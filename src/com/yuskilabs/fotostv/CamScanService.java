package com.yuskilabs.fotostv;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;

/** Lee las cámaras de las fotos aunque se salga de la app o se apague la pantalla (mantiene la CPU y el wifi despiertos mientras trabaja). */
public class CamScanService extends Service {
    private PowerManager.WakeLock wl;
    private WifiManager.WifiLock wf;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        try {
            Notification n = new Notification.Builder(this).setSmallIcon(R.drawable.icon)
                    .setContentTitle("FotosTV").setContentText("Leyendo la cámara de las fotos").build();
            startForeground(7101, n);
        } catch (Throwable t) { Diag.e("servicio cámaras", t); }
        try {
            if (wl == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fotostv:camscan");
                wl.setReferenceCounted(false);
                wl.acquire(10 * 3600 * 1000L);
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) { wf = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "fotostv:camscan"); wf.setReferenceCounted(false); wf.acquire(); }
            }
        } catch (Throwable t) { Diag.e("servicio cámaras", t); }
        CamScan.begin(this, new Runnable() { public void run() { stopSelf(); } });
        if (!CamScan.running) stopSelf();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) { }
        try { if (wf != null && wf.isHeld()) wf.release(); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
