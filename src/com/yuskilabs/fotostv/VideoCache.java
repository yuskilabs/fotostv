package com.yuskilabs.fotostv;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Caché temporal de vídeos del pase: dónde se guardan, cómo se descargan (alta y baja calidad) y cómo se limpia. */
final class VideoCache {
    private static final long MAX_VIDEO = 600L * 1024 * 1024;
    private final Context ctx;

    VideoCache(Context ctx) { this.ctx = ctx; }


    private java.io.File videoDir() {
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "videos");
        dir.mkdirs();
        return dir;
    }

    /** Mantiene la caché de vídeos: se conservan los más usados hasta el tope de espacio y se borra solo lo más antiguo. */
    void clean(java.util.Set<String> keep) {
        java.io.File[] fs = videoDir().listFiles();
        if (fs == null) return;
        List<java.io.File> old = new ArrayList<java.io.File>();
        long now = System.currentTimeMillis();
        for (java.io.File f : fs) {
            String n = f.getName();
            if (n.endsWith(".part")) {                       // descargas a medias: fuera las de hace rato
                if (now - f.lastModified() > 15 * 60 * 1000L) f.delete();
                continue;
            }
            int d = n.indexOf('.');
            String k = d > 0 ? n.substring(0, d) : n;
            if (!keep.contains(k)) old.add(f);
        }
        java.util.Collections.sort(old, new java.util.Comparator<java.io.File>() {
            public int compare(java.io.File x, java.io.File y) {
                return x.lastModified() < y.lastModified() ? 1 : (x.lastModified() > y.lastModified() ? -1 : 0);
            }
        });
        long total = 0;
        for (java.io.File f : fs) {
            String n = f.getName();
            int d = n.indexOf('.');
            if (!n.endsWith(".part") && keep.contains(d > 0 ? n.substring(0, d) : n)) total += f.length();
        }
        long cap = Prefs.videoMB(ctx) * 1024L * 1024L;
        for (java.io.File f : old) {
            total += f.length();
            if (total > cap) f.delete();
        }
    }

    java.io.File hdFile(Item it) { return new java.io.File(videoDir(), it.key() + ".mp4"); }
    java.io.File lqFile(Item it) { return new java.io.File(videoDir(), it.key() + ".lq.mp4"); }

    static boolean ready(java.io.File f) {
        if (!f.exists() || f.length() <= 10000) return false;
        f.setLastModified(System.currentTimeMillis());      // usado hace poco: se borra el último
        return true;
    }

    /** Alta calidad (1080p, si no 720p). readMs: cuánto esperar a que Google prepare el vídeo. */
    java.io.File loadHd(Item it, int readMs, VideoProxy.Prog prog) {
        java.io.File f = hdFile(it);
        if (ready(f)) { if (prog != null) { prog.len = f.length(); prog.written = f.length(); prog.done = true; } return f; }
        java.io.File part = new java.io.File(videoDir(), it.key() + ".mp4.part");
        for (String v : Prefs.videoSd(ctx) ? new String[]{"=m22", "=m37"} : new String[]{"=m37", "=m22"}) {
            if (Thread.currentThread().isInterrupted()) break;
            if (AlbumFetcher.downloadVideo(it.url + v, part, MAX_VIDEO, readMs, prog) && part.renameTo(f)) { if (prog != null) prog.done = true; return f; }
        }
        part.delete();
        if (prog != null) prog.failed = true;
        return null;
    }

    /** Dirección para ver el vídeo mientras se descarga (con todo lo bajado como búfer). */
    String streamFor(Item it, VideoProxy.Prog pr) {
        String u = VideoProxy.get().register(new java.io.File(videoDir(), it.key() + ".mp4.part"), hdFile(it), pr);
        return u != null ? u : it.url + "=m37";
    }

    /** Versión ligera (360p, si no 720p): pesa poco y llega antes. Se guarda aparte para no confundirla con la buena. */
    java.io.File loadLq(Item it, int readMs) {
        java.io.File f = lqFile(it);
        if (ready(f)) return f;
        java.io.File part = new java.io.File(videoDir(), it.key() + ".lq.part");
        for (String v : new String[]{"=m18", "=m22"}) {
            if (Thread.currentThread().isInterrupted()) break;
            if (AlbumFetcher.downloadVideo(it.url + v, part, MAX_VIDEO, readMs) && part.renameTo(f)) return f;
        }
        part.delete();
        return null;
    }

    /** Pase y vídeo suelto: la mejor calidad disponible; si no hay otra, la baja; y por último el original. */
    java.io.File loadVideo(Item it) { return loadVideo(it, null); }

    java.io.File loadVideo(Item it, VideoProxy.Prog prog) {
        java.io.File f = loadHd(it, 60000, prog);
        if (f != null) return f;
        f = loadLq(it, 30000);
        if (f != null) return f;
        java.io.File hd = hdFile(it);
        java.io.File part = new java.io.File(videoDir(), it.key() + ".mp4.part");
        if (!Thread.currentThread().isInterrupted()
                && AlbumFetcher.downloadVideo(it.url + "=dv", part, MAX_VIDEO, 30000) && part.renameTo(hd)) return hd;
        part.delete();
        if (!Thread.currentThread().isInterrupted()) Diag.i("vídeo", "no se pudo bajar " + it.key() + " (" + (AlbumFetcher.lastVideoErr == null || AlbumFetcher.lastVideoErr.length() == 0 ? "sin respuesta del servidor" : AlbumFetcher.lastVideoErr) + ")");
        return null;
    }
}
