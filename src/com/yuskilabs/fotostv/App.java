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
                } catch (Throwable ignored) { }
            }
        }, "gps-reset").start();
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
