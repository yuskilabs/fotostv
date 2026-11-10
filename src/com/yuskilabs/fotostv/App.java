package com.yuskilabs.fotostv;

import android.app.Application;
import android.graphics.Typeface;

/** Carga las tipografías de la app (Figtree) una vez, al arrancar el proceso. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            Ui.BLACK = Typeface.createFromAsset(getAssets(), "fonts/Figtree-Bold.ttf");
            Ui.BTN = Typeface.createFromAsset(getAssets(), "fonts/Figtree-Regular.ttf");
            Ui.HAND = Typeface.createFromAsset(getAssets(), "fonts/Caveat-SemiBold.ttf");
            Ui.REG = Typeface.createFromAsset(getAssets(), "fonts/Figtree-Regular.ttf");
        } catch (RuntimeException e) { /* si falla, se queda la fuente de Android */ }
        sweepGuests();
        autoReload();
        try { Prefs.renewWebPin(this); } catch (Throwable ignored) { }
        new Thread(new Runnable() {
            public void run() {
                try {
                    // al actualizar la app se vuelven a mirar las fotos guardadas como «sin ubicación» (cambia la forma de leerla)
                    String v = Updater.installed(App.this);
                    android.content.SharedPreferences m = Prefs.meta(App.this);
                    if (!v.equals(m.getString("gpsReset", ""))) {
                        MetaFetcher.forgetMissingGps(App.this);
                        m.edit().putString("gpsReset", v).apply();
                    }
                    if (!"5.31.1".equals(m.getString("geoNames", ""))) {   // nombres de lugar con las reglas nuevas
                        try { GeoIndex.get(App.this).getWritableDatabase().execSQL("UPDATE photos SET place=NULL"); } catch (Throwable ignored) { }
                        m.edit().putString("geoNames", "5.31.1").apply();
                    }
                    if (!"1".equals(m.getString("geoPamukkale", ""))) {   // «Denizli» pasa a llamarse «Pamukkale»
                        android.content.SharedPreferences.Editor ed = m.edit();
                        for (java.util.Map.Entry<String, ?> en : m.getAll().entrySet()) {
                            if (en.getKey().startsWith("p8_") && en.getValue() instanceof String && ((String) en.getValue()).contains("Denizli")) {
                                ed.remove(en.getKey());
                                ed.remove(en.getKey() + "_t");
                            }
                        }
                        ed.putString("geoPamukkale", "1").apply();
                        try { GeoIndex.get(App.this).getWritableDatabase().execSQL("UPDATE photos SET place=NULL WHERE place LIKE '%Denizli%'"); } catch (Throwable ignored) { }
                    }
                    if (!"1".equals(m.getString("geoLocalidad3", ""))) {   // nombres por municipio (Estepona y no Voladilla)
                        android.content.SharedPreferences.Editor ed = m.edit();
                        for (String key : new java.util.ArrayList<String>(m.getAll().keySet())) if (key.startsWith("p8_")) ed.remove(key);
                        ed.putString("geoLocalidad3", "1").apply();
                        try { GeoIndex.get(App.this).getWritableDatabase().execSQL("UPDATE photos SET place=NULL"); } catch (Throwable ignored) { }
                    }
                    GeoService.start(App.this);
                } catch (Throwable ignored) { }
            }
        }, "gps-reset").start();
    }

    /** Recarga automática de álbumes (función extra): mientras viva la app, cada media hora mira si toca. */
    private void autoReload() {
        final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable r = new Runnable() {
            public void run() {
                hh.postDelayed(this, 30 * 60 * 1000L);
                final int hours = Extras.num(App.this, Extras.RELOAD);
                if (hours <= 0) return;
                final android.content.SharedPreferences m = Prefs.meta(App.this);
                if (System.currentTimeMillis() - m.getLong("autoReload", 0) < hours * 3600000L) return;
                m.edit().putLong("autoReload", System.currentTimeMillis()).apply();
                new Thread(new Runnable() {
                    public void run() {
                        for (Prefs.Album al : Prefs.albums(App.this)) {
                            try { Prefs.saveAlbumCache(App.this, al.url, AlbumFetcher.fetchAlbum(al.url)); } catch (Throwable ignored) { }
                        }
                    }
                }, "auto-reload").start();
            }
        };
        hh.postDelayed(r, 60 * 1000L);
    }

    /** Álbumes temporales de invitados: al arrancar y cada 5 minutos mientras viva la app, se borra lo caducado. */
    private void sweepGuests() {
        final android.os.Handler hh = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable r = new Runnable() {
            public void run() {
                new Thread(new Runnable() {
                    public void run() { try { Guest.purgeExpired(App.this); } catch (Throwable ignored) { } }
                }, "guest-sweep").start();
                hh.postDelayed(this, 5 * 60 * 1000L);
            }
        };
        hh.post(r);
    }
}
