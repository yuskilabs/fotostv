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

    static synchronized void start(Context ctx) {
        if (running) return;
        running = true;
        done = 0;
        total = 0;
        final Context c = ctx.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                try {
                    GeoIndex g = GeoIndex.get(c);
                    HashMap<String, String> known = g.camMap();
                    List<Item> todo = new ArrayList<Item>();
                    for (Item it : Search.allVisible(c)) {
                        if (known.containsKey(it.key())) continue;
                        Boolean v = MetaFetcher.cachedVideo(c, it);
                        if (v != null && v.booleanValue()) continue;          // los vídeos no tienen EXIF
                        todo.add(it);
                    }
                    total = todo.size();
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
                } catch (Throwable ignored) {
                } finally {
                    running = false;
                    finishedAt = System.currentTimeMillis();
                }
            }
        }, "cam-scan").start();
    }

    static void stop() { running = false; }
}
