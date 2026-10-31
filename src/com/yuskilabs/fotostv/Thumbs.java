package com.yuskilabs.fotostv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Carga de miniaturas con dos cachés de tamaño limitado:
 *  - memoria: como mucho 1/8 de la memoria de la app (entre 8 y 32 MB)
 *  - disco: como mucho el límite elegido en los ajustes; al pasarse borra las más antiguas hasta el 80 %
 * Las peticiones van en una cola donde manda la última (lo que se ve ahora) y las de
 * vistas que ya han cambiado de foto se descartan sin descargar nada.
 */
class Thumbs {
    private long maxDisk() { return Prefs.thumbMB(app) * 1024L * 1024L; }
    private static final int WORKERS = 4;
    private static final int MAX_QUEUE = 500;
    private static final int MAX_BYTES = 3 * 1024 * 1024;

    private static Thumbs inst;

    static synchronized Thumbs get(Context c) {
        if (inst == null) inst = new Thumbs(c.getApplicationContext());
        return inst;
    }

    private static class Task {
        ImageView v;
        String url;
        String tag;
        boolean blur;
    }

    private final Context app;
    private final File dir;
    private final LruCache<String, Bitmap> mem;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Task> queue = new ArrayDeque<Task>();
    private final java.util.HashSet<String> asked = new java.util.HashSet<String>();
    private final Object diskLock = new Object();
    private long diskBytes = -1;

    private Thumbs(Context c) {
        app = c;
        dir = new File(c.getCacheDir(), "thumbs");
        dir.mkdirs();
        long max = Runtime.getRuntime().maxMemory() / 8;
        int memMax = (int) Math.min(32L * 1024 * 1024, Math.max(8L * 1024 * 1024, max));
        mem = new LruCache<String, Bitmap>(memMax) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };
        for (int i = 0; i < WORKERS; i++) {
            Thread t = new Thread(new Runnable() { public void run() { loop(); } }, "thumbs-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        }
        Thread init = new Thread(new Runnable() {
            public void run() { synchronized (diskLock) { diskBytes = scan(); trimLocked(); } }
        }, "thumbs-init");
        init.setDaemon(true);
        init.start();
    }

    /** Pide la miniatura (recortada al tamaño dado) de baseUrl y la pone en la vista. */
    void load(ImageView v, String baseUrl, int w, int h) { load(v, baseUrl, w, h, true); }

    /** crop=false: la foto entera, ajustada dentro de w×h sin recortar. */
    void load(ImageView v, String baseUrl, int w, int h, boolean crop) { load(v, baseUrl, w, h, crop, false); }

    /** Igual, pero la miniatura sale con un desenfoque suave (álbum «Ocultas»). */
    void loadBlur(ImageView v, String baseUrl, int w, int h) { load(v, baseUrl, w, h, true, true); }

    private void load(ImageView v, String baseUrl, int w, int h, boolean crop, boolean blur) {
        final String url = baseUrl + "=w" + w + "-h" + h + (crop ? "-c" : "");
        final String tag = blur ? url + "#b" : url;
        v.animate().cancel();
        v.setAlpha(1f);
        v.setTag(tag);
        Bitmap b = mem.get(tag);
        if (b != null && !b.isRecycled()) {
            v.setImageBitmap(b);
            return;
        }
        v.setImageDrawable(null);
        Task t = new Task();
        t.v = v;
        t.url = url;
        t.tag = tag;
        t.blur = blur;
        synchronized (queue) {
            queue.addFirst(t);
            while (queue.size() > MAX_QUEUE) dropLast();
            queue.notify();
        }
    }

    private void dropLast() {
        Task x = queue.pollLast();
        if (x != null && x.v == null) asked.remove(x.url);
    }

    /** Descarga la miniatura al disco (sin vista), con menos prioridad que lo que se está viendo. */
    void prefetch(String baseUrl, int w, int h) { prefetch(baseUrl, w, h, true); }

    void prefetch(String baseUrl, int w, int h, boolean crop) {
        String url = baseUrl + "=w" + w + "-h" + h + (crop ? "-c" : "");
        Task t = new Task();
        t.url = url;
        synchronized (queue) {
            if (!asked.add(url)) return;
            if (asked.size() > 20000) asked.clear();
            queue.addLast(t);
            while (queue.size() > MAX_QUEUE) dropLast();
            queue.notify();
        }
    }

    void clearView(ImageView v) {
        v.animate().cancel();
        v.setTag(null);
        v.setImageDrawable(null);
    }

    void trimMemory() { mem.evictAll(); }

    /** Vacía las dos cachés y devuelve los bytes de disco liberados. */
    long clearAll() {
        mem.evictAll();
        long freed = 0;
        synchronized (diskLock) {
            File[] fs = dir.listFiles();
            if (fs != null) for (File f : fs) { freed += f.length(); f.delete(); }
            diskBytes = 0;
        }
        return freed;
    }

    long diskUsage() {
        synchronized (diskLock) {
            if (diskBytes < 0) diskBytes = scan();
            return diskBytes;
        }
    }

    // ------------------------------------------------------------ interno
    private void loop() {
        while (true) {
            Task t;
            synchronized (queue) {
                while (queue.isEmpty()) {
                    try { queue.wait(); } catch (InterruptedException e) { return; }
                }
                t = queue.pollFirst();
            }
            try { run(t); } catch (Throwable ignored) { }
        }
    }

    private void run(final Task t) {
        if (t.v == null) {                            // precarga: solo al disco
            File pf = fileFor(t.url);
            if (pf.exists()) return;
            try {
                byte[] data = AlbumFetcher.get(t.url, MAX_BYTES);
                if (data.length > 0) store(pf, data);
            } catch (Exception ignored) { }
            return;
        }
        if (!t.tag.equals(t.v.getTag())) return;     // la vista ya enseña otra cosa
        Bitmap bm = mem.get(t.tag);
        if (bm == null) {
            bm = mem.get(t.url);
            File f = fileFor(t.url);
            if (f.exists()) {
                bm = decode(f);
                if (bm != null) f.setLastModified(System.currentTimeMillis());
                else f.delete();
            }
            if (bm == null) {
                try {
                    byte[] data = AlbumFetcher.get(t.url, MAX_BYTES);
                    if (data.length > 0) {
                        store(f, data);
                        bm = decode(data);
                    }
                } catch (Exception ignored) { }
            }
            if (bm != null) mem.put(t.url, bm);
            if (bm != null && t.blur) { Bitmap bl = blurred(bm); if (bl != null) { bm = bl; mem.put(t.tag, bm); } }
        }
        final Bitmap out = bm;
        if (out == null) return;
        main.post(new Runnable() {
            public void run() {
                if (!t.tag.equals(t.v.getTag())) return;
                t.v.setImageBitmap(out);
                t.v.setAlpha(0f);
                t.v.animate().alpha(1f).setDuration(180).start();
            }
        });
    }

    /** Desenfoque suave: se reduce mucho la imagen y se le pasan varios promedios; al ampliarla queda difuminada. */
    private static Bitmap blurred(Bitmap src) {
        try {
            int w = 48, h = Math.max(1, Math.round(48f * src.getHeight() / src.getWidth()));
            Bitmap s = Bitmap.createScaledBitmap(src, w, h, true);
            int[] a = new int[w * h], b = new int[w * h];
            s.getPixels(a, 0, w, 0, 0, w, h);
            int r = 3;
            for (int pass = 0; pass < 3; pass++) {
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {          // horizontal
                    int ar = 0, ag = 0, ab = 0, n = 0;
                    for (int k = -r; k <= r; k++) {
                        int xx = Math.max(0, Math.min(w - 1, x + k));
                        int c = a[y * w + xx];
                        ar += (c >> 16) & 255; ag += (c >> 8) & 255; ab += c & 255; n++;
                    }
                    b[y * w + x] = 0xFF000000 | ((ar / n) << 16) | ((ag / n) << 8) | (ab / n);
                }
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {          // vertical
                    int ar = 0, ag = 0, ab = 0, n = 0;
                    for (int k = -r; k <= r; k++) {
                        int yy = Math.max(0, Math.min(h - 1, y + k));
                        int c = b[yy * w + x];
                        ar += (c >> 16) & 255; ag += (c >> 8) & 255; ab += c & 255; n++;
                    }
                    a[y * w + x] = 0xFF000000 | ((ar / n) << 16) | ((ag / n) << 8) | (ab / n);
                }
            }
            Bitmap o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            o.setPixels(a, 0, w, 0, 0, w, h);
            return o;
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap decode(File f) {
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (OutOfMemoryError e) {
            mem.evictAll();
            return null;
        }
    }

    private Bitmap decode(byte[] d) {
        try {
            return BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (OutOfMemoryError e) {
            mem.evictAll();
            return null;
        }
    }

    private void store(File f, byte[] data) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try { o.write(data); } finally { o.close(); }
            if (!tmp.renameTo(f)) { tmp.delete(); return; }
            synchronized (diskLock) {
                if (diskBytes >= 0) diskBytes += data.length;
                if (diskBytes > maxDisk()) trimLocked();
            }
        } catch (Exception e) {
            tmp.delete();
        }
    }

    private long scan() {
        long total = 0;
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) total += f.length();
        return total;
    }

    /** Borra las miniaturas menos usadas hasta quedar por debajo del 80 % del límite. */
    private void trimLocked() {
        if (diskBytes < 0) diskBytes = scan();
        if (diskBytes <= maxDisk()) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs, new Comparator<File>() {
            public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        long target = (long) (maxDisk() * 0.8);
        long total = 0;
        for (File f : fs) total += f.length();
        for (File f : fs) {
            if (total <= target) break;
            long len = f.length();
            if (f.delete()) total -= len;
        }
        diskBytes = total;
    }

    private File fileFor(String url) {
        int i = url.lastIndexOf("=w");
        String gid = i > 0 ? Guest.idOfBase(app, url.substring(0, i)) : null;
        if (gid != null) {                       // fotos de un álbum de invitado: carpeta aparte, que se borra entera
            File gd = new File(Guest.guestThumbRoot(app), gid);
            gd.mkdirs();
            return new File(gd, sha1(url) + ".img");
        }
        return new File(dir, sha1(url) + ".img");
    }

    /** Vacía la memoria de miniaturas (al borrar un álbum de invitado). */
    void dropMemory() { mem.evictAll(); }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            char[] hex = "0123456789abcdef".toCharArray();
            char[] out = new char[d.length * 2];
            for (int i = 0; i < d.length; i++) {
                out[i * 2] = hex[(d[i] >> 4) & 15];
                out[i * 2 + 1] = hex[d[i] & 15];
            }
            return new String(out);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode()) + "_" + s.length();
        }
    }
}
