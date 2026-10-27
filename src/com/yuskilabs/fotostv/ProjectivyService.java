package com.yuskilabs.fotostv;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Proveedor de fondos de pantalla para Projectivy Launcher.
 * Habla con el launcher por Binder con el mismo formato que su interfaz
 * (tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService) sin necesitar su librería.
 */
public class ProjectivyService extends Service {
    static final String DESC = "tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService";
    static final String UUID = "de627aad-b6d8-4a17-a922-f809c81275b9";
    static final int MAX_WALLPAPERS = 30;

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    private final Binder binder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws android.os.RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESC); return true; }
            if (code == FIRST_CALL_TRANSACTION) {            // getWallpapers(in Event)
                data.enforceInterface(DESC);
                List<String[]> w;
                try { w = build(ProjectivyService.this); } catch (Throwable t) { w = new ArrayList<String[]>(); }
                reply.writeNoException();
                reply.writeInt(w.size());
                for (String[] x : w) {
                    reply.writeInt(1);
                    reply.writeString(x[0]);               // uri
                    reply.writeInt(Integer.parseInt(x[1])); // type
                    reply.writeInt(Integer.parseInt(x[2])); // displayMode
                    reply.writeString(x[3]);               // title
                    reply.writeString(null);               // source
                    reply.writeString(null);               // author
                    reply.writeString(null);               // actionUri
                }
                return true;
            }
            if (code == FIRST_CALL_TRANSACTION + 1) {        // getPreferences()
                data.enforceInterface(DESC);
                reply.writeNoException();
                reply.writeString("{}");
                return true;
            }
            if (code == FIRST_CALL_TRANSACTION + 2) {        // setPreferences(String)
                data.enforceInterface(DESC);
                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    // ---- ajustes del fondo ----
    static SharedPreferences sp(Context c) { return Prefs.sp(c); }

    /** Álbumes elegidos (ninguno hasta que se marquen). */
    static boolean albumOn(Context c, String url) {
        Set<String> s = sp(c).getStringSet("pv_albums", null);
        return s != null && s.contains(url);
    }

    /** Horizontales: auto/fill = rellenar; fit = original; blur = original con fondo difuminado. */
    static String fit(Context c) { return sp(c).getString("pv_fit", "auto"); }

    /** Verticales y cuadradas: no = fuera; blur = enteras sobre fondo difuminado; fill = rellenar (recorta). */
    static String vert(Context c) { return sp(c).getString("pv_vert", "no"); }

    /** «Todas»: usa los álbumes de golpe, sin tener que marcarlos uno a uno. */
    static boolean allOn(Context c) { return sp(c).getBoolean("pv_all", false); }

    static boolean videos(Context c) { return sp(c).getBoolean("pv_videos", false); }

    /** Avisa a Projectivy de que hay que pedir otra vez las fotos. */
    static void notifyChanged(Context c) {
        try {
            Intent i = new Intent("tv.projectivy.plugin.action.WALLPAPER_PROVIDER_UPDATED");
            i.setPackage("com.spocky.projengmenu");
            i.putExtra("tv.projectivy.plugin.extra.PROVIDER_ID", UUID);
            i.putExtra("tv.projectivy.plugin.extra.UPDATE_REASON", 1);
            c.sendBroadcast(i);
        } catch (Throwable ignored) { }
    }

    private static class Cand {
        Item it;
        boolean video;
        float ratio;
    }

    /** Devuelve {uri, tipo, modo, título}. Solo fotos (o vídeos) horizontales: el fondo tiene que encajar. */
    private static List<String[]> build(final Context c) {
        List<Item> pool = new ArrayList<Item>();
        boolean anyVert = !"no".equals(vert(c));
        Set<String> hidden = Prefs.hidden(c);
        for (Prefs.Album a : Prefs.albums(c)) {
            if (!allOn(c) && !albumOn(c, a.url)) continue;
            AlbumFetcher.AlbumData d = Prefs.albumCache(c, a.url);
            if (d == null) continue;
            for (Item x : d.items) {
                x.album = d.title;
                if (!hidden.contains(x.key())) pool.add(x);
            }
        }
        Collections.shuffle(pool);
        final boolean withVideos = videos(c);
        ExecutorService ex = Executors.newFixedThreadPool(8);
        List<Future<Cand>> fs = new ArrayList<Future<Cand>>();
        int n = Math.min(pool.size(), 90);
        for (int i = 0; i < n; i++) {
            final Item it = pool.get(i);
            Boolean v = MetaFetcher.cachedVideo(c, it);
            final boolean isVid = v != null && v.booleanValue();
            if (isVid && !withVideos) continue;
            fs.add(ex.submit(new Callable<Cand>() {
                public Cand call() {
                    Cand k = new Cand();
                    k.it = it;
                    k.video = isVid;
                    k.ratio = ratio(c, it);
                    return k;
                }
            }));
        }
        List<String[]> out = new ArrayList<String[]>();
        long end = System.currentTimeMillis() + 12000;
        int vids = 0;
        for (Future<Cand> f : fs) {
            if (out.size() >= MAX_WALLPAPERS) break;
            try {
                Cand k = f.get(Math.max(1, end - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                boolean horiz = k.ratio >= 1.2f;
                if (k.ratio <= 0f) continue;
                if (!horiz && "no".equals(vert(c))) continue;
                if (k.video) {
                    if (vids >= MAX_WALLPAPERS / 4) continue;
                    vids++;
                    out.add(new String[]{k.it.url + "=m37", "4", String.valueOf(mode(c, horiz)), k.it.album});
                } else {
                    out.add(new String[]{k.it.url + "=w1920-h1080", "0", String.valueOf(mode(c, horiz)), k.it.album});
                }
            } catch (Exception e) {
                break;
            }
        }
        ex.shutdownNow();
        return out;
    }

    private static int mode(Context c, boolean horiz) {
        if (!horiz) return "blur".equals(vert(c)) ? 5 : 1;
        String f = fit(c);
        return "fit".equals(f) ? 4 : "blur".equals(f) ? 5 : 1;
    }

    /** Proporción ancho/alto (se mira una miniatura minúscula y se recuerda). */
    private static float ratio(Context c, Item it) {
        SharedPreferences m = Prefs.meta(c);
        String k = "pr_" + it.key();
        float r = m.getFloat(k, 0f);
        if (r > 0f) return r;
        try {
            byte[] d = AlbumFetcher.get(it.url + "=w96", 200000);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(d, 0, d.length, o);
            if (o.outWidth > 0 && o.outHeight > 0) {
                r = o.outWidth / (float) o.outHeight;
                m.edit().putFloat(k, r).apply();
                return r;
            }
        } catch (Exception ignored) { }
        return 0f;
    }
}
