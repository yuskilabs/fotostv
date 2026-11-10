package com.yuskilabs.fotostv;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Servicio en primer plano que va leyendo en segundo plano dónde se hizo cada foto y poniéndole nombre al lugar. */
public class GeoService extends Service {
    private static volatile boolean alive;
    private PowerManager.WakeLock wl;
    private WifiManager.WifiLock wf;

    static volatile boolean nudge;   // «reintentar nombres»: no esperar los 2 minutos entre pasadas

    static void start(Context c) {
        nudge = true;
        if (alive || GeoIndex.paused(c)) return;
        try { c.getApplicationContext().startService(new Intent(c.getApplicationContext(), GeoService.class)); }
        catch (Throwable ignored) { }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (alive) return START_STICKY;
        alive = true;
        try {
            Notification n = new Notification.Builder(this).setSmallIcon(R.drawable.icon)
                    .setContentTitle("FotosTV").setContentText("Buscando dónde se hizo cada foto").setOngoing(true).build();
            startForeground(4711, n);
        } catch (Throwable ignored) { }
        try {
            wl = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fotostv:geo");
            wl.setReferenceCounted(false);
            wl.acquire(10L * 60 * 60 * 1000);
            wf = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "fotostv:geo");
            wf.setReferenceCounted(false);
            wf.acquire();
        } catch (Throwable ignored) { }
        new Thread(new Runnable() {
            public void run() {
                try { work(); } catch (Throwable ignored) { }
                finish();
            }
        }, "geo-index").start();
        return START_STICKY;
    }

    private void finish() {
        alive = false;
        GeoIndex.running = 0;
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) { }
        try { if (wf != null && wf.isHeld()) wf.release(); } catch (Throwable ignored) { }
        try { stopForeground(true); } catch (Throwable ignored) { }
        stopSelf();
    }

    private boolean stop() { return GeoIndex.paused(this); }

    private void work() throws Exception {
        final Context c = this;
        final GeoIndex g = GeoIndex.get(c);
        int idle = 0;
        HashMap<String, Integer> fails = new HashMap<String, Integer>();
        while (!stop()) {
            nudge = false;
            List<Item> all = new ArrayList<Item>();
            GeoIndex.allItems(c, all);
            GeoIndex.total = all.size();
            HashSet<String> known = g.known();
            final List<Item> todo = new ArrayList<Item>();
            for (Item x : all) if (!known.contains(x.key())) todo.add(x);

            // por tandas de 400 fotos: primero las coordenadas (3 hilos) y luego el nombre de los sitios nuevos,
            // así «Lugares» se va llenando desde el principio
            HashSet<String> skip = new HashSet<String>();
            for (int from = 0; from < todo.size() && !stop(); from += 400) {
                readGps(c, g, todo.subList(from, Math.min(todo.size(), from + 400)));
                nameCells(c, g, skip);
                idle = 0;
            }
            if (todo.isEmpty()) nameCells(c, g, skip);
            if (stop()) break;
            // zonas que tras varios intentos siguen sin nombre (mar, descampado…): se agrupan como «Sin nombre»
            // para que no queden pendientes eternamente. Solo si son pocas (si son muchas, lo normal es que no haya red).
            for (String cell : skip) fails.put(cell, fails.containsKey(cell) ? fails.get(cell) + 1 : 1);
            if (skip.size() <= 100) {
                for (String cell : new ArrayList<String>(skip)) {
                    if (fails.get(cell) >= 3) { g.setPlace(cell, "Sin nombre"); skip.remove(cell); fails.remove(cell); }
                }
            }
            // todo al día: se vuelve a mirar cada hora por si hay fotos nuevas; tras unas pasadas sin novedades, se acaba
            if (!todo.isEmpty() || !skip.isEmpty()) {
                for (int w = skip.isEmpty() ? 1 : 120; w > 0 && !nudge && !stop(); w--) Thread.sleep(1000);
                continue;
            }
            if (++idle >= 2) break;
            Thread.sleep(1000);
        }
    }

    private void readGps(final Context c, final GeoIndex g, final List<Item> todo) throws InterruptedException {
        final AtomicInteger next = new AtomicInteger(), fails = new AtomicInteger();
        Thread[] ts = new Thread[3];
        for (int t = 0; t < ts.length; t++) {
            ts[t] = new Thread(new Runnable() {
                public void run() {
                    int i;
                    while (!stop() && (i = next.getAndIncrement()) < todo.size()) {
                        Item it = todo.get(i);
                        try {
                            double[] r = MetaFetcher.cachedGps(c, it);
                            String[] cam = new String[1];
                            if (r == null) r = MetaFetcher.quickGps(it, Extras.on(c, Extras.STATS) ? cam : null);
                            if (cam[0] != null) g.putCam(it.key(), cam[0]);   // de paso, la cámara (para las estadísticas)
                            if (r == null || Double.isNaN(r[0])) {
                                double[] f = MetaFetcher.exif(c, it);     // lectura completa (vídeos, Live Photos…)
                                r = Double.isNaN(f[0]) ? new double[]{Double.NaN, Double.NaN} : new double[]{f[0], f[1]};
                            }
                            g.put(it.key(), r[0], r[1], !Double.isNaN(r[0]));
                            if (!Double.isNaN(r[0]) && MetaFetcher.cachedGps(c, it) == null) MetaFetcher.storeGps(c, it, r[0], r[1], 0);
                            fails.set(0);
                        } catch (Throwable e) {
                            if (fails.incrementAndGet() > 5) {      // sin red: espera un poco
                                try { Thread.sleep(60000); } catch (InterruptedException ignored) { }
                                fails.set(0);
                            }
                        }
                    }
                }
            }, "geo-gps-" + t);
            ts[t].start();
        }
        for (Thread t : ts) t.join();
    }

    /** Pone nombre a las casillas de 0,01° que aún no lo tienen; las que fallan se anotan en skip. */
    private void nameCells(Context c, GeoIndex g, HashSet<String> skip) throws InterruptedException {
        String[] cell;
        while (!stop() && (cell = g.nextCell(skip)) != null) {
            double lat = Double.parseDouble(cell[1]), lon = Double.parseDouble(cell[2]);
            boolean cached = MetaFetcher.placeCached(c, lat, lon) != null;
            String p = MetaFetcher.place(c, lat, lon);
            if (p != null && p.length() > 0) g.setPlace(cell[0], p); else skip.add(cell[0]);
            if (!cached) Thread.sleep(1100);        // educación con el servidor de nombres
        }
    }

    @Override public void onDestroy() { alive = false; super.onDestroy(); }
}
