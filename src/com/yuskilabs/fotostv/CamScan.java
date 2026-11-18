package com.yuskilabs.fotostv;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Lee en segundo plano la cámara de las fotos que aún no la tienen (para «Estadísticas»). Solo cuando se pide. */
final class CamScan {
    private CamScan() { }
    static volatile boolean running;
    static volatile int done, total;
    static volatile long finishedAt;
    static volatile int lastTotal;      // cuántas fotos tocaba leer en la última pasada (0 = no faltaba ninguna)

    /** Pide la lectura: la hace un servicio (con la pantalla apagada o fuera de la app) y se reanuda sola si el sistema la corta. */
    static void start(Context ctx) { start(ctx, false); }

    /** retry: vuelve a leer también las fotos que quedaron apuntadas como «sin cámara» (por si fue un fallo de lectura). */
    static void start(Context ctx, boolean retry) {
        if (running) return;
        Prefs.sp(ctx).edit().putBoolean("camScanOn", true).putBoolean("camRetry", retry).commit();
        try { ctx.getApplicationContext().startService(new android.content.Intent(ctx.getApplicationContext(), CamScanService.class)); }
        catch (Throwable t) { Diag.e("leer cámaras", t); begin(ctx, null); }
    }

    /** Al arrancar la app: si la lectura estaba en marcha y el sistema mató el proceso, sigue. */
    static void resumeIfWanted(Context ctx) {
        if (!running && Prefs.sp(ctx).getBoolean("camScanOn", false)) start(ctx);
    }

    static synchronized void begin(Context ctx, final Runnable onEnd) {
        if (running) return;
        running = true;
        done = 0;
        total = 0;
        final Context c = ctx.getApplicationContext();
        final boolean retry = Prefs.sp(c).getBoolean("camRetry", false);
        new Thread(new Runnable() {
            public void run() {
                try {
                    GeoIndex g = GeoIndex.get(c);
                    HashMap<String, String> known = g.camMap();
                    List<Item> todo = new ArrayList<Item>();
                    for (Item it : Search.allVisible(c)) {
                        String k = known.get(it.key());
                        if (k != null && !(retry && k.length() == 0)) continue;
                        Boolean v = MetaFetcher.cachedVideo(c, it);
                        if (v != null && v.booleanValue()) continue;          // los vídeos no tienen EXIF
                        todo.add(it);
                    }
                    total = todo.size();
                    lastTotal = total;
                    int fails = 0;
                    for (Item it : todo) {
                        if (!running) break;
                        byte[] head;
                        try {
                            head = AlbumFetcher.get(it.url + "=d", 160 * 1024);
                        } catch (java.io.IOException net) {           // sin red: no se apunta nada y se espera un poco
                            if (++fails > 5) { Thread.sleep(30000); fails = 0; }
                            done++;
                            continue;
                        }
                        fails = 0;
                        g.putCam(it.key(), MetaFetcher.camFromHead(head));
                        done++;
                    }
                } catch (Throwable t) {
                    Diag.e("leer cámaras", t);
                } finally {
                    boolean wanted = running;        // false = la paró el usuario
                    running = false;
                    finishedAt = System.currentTimeMillis();
                    if (wanted) Prefs.sp(c).edit().putBoolean("camScanOn", false).putBoolean("camRetry", false).commit();
                    if (onEnd != null) onEnd.run();
                }
            }
        }, "cam-scan").start();
    }

    static void stop() { running = false; }
    static void stop(Context c) { running = false; Prefs.sp(c).edit().putBoolean("camScanOn", false).putBoolean("camRetry", false).commit(); }
}
