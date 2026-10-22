package com.yuskilabs.fotostv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Para dispositivos cuyo sistema no deja elegir salvapantallas (Fire TV con FireOS 8):
 * si no se toca el mando durante unos minutos y no hay nada sonando, abre el pase de fotos.
 * Solo observa que se pulsa una tecla; no lee el contenido de la pantalla ni guarda nada.
 */
public class IdleService extends AccessibilityService {
    private static final long CHECK_MS = 15000;
    private final Handler h = new Handler();
    private volatile long last;
    private volatile boolean showing;

    private final Runnable check = new Runnable() {
        public void run() {
            try { tick(); } catch (Throwable ignored) { }
            h.postDelayed(this, CHECK_MS);
        }
    };

    @Override protected void onServiceConnected() {
        last = System.currentTimeMillis();
        showing = false;
        h.removeCallbacks(check);
        h.postDelayed(check, CHECK_MS);
    }

    @Override public boolean onKeyEvent(KeyEvent e) {
        last = System.currentTimeMillis();
        showing = false;   // si se pulsa algo, el pase (si estaba) se cierra; podrá volver a salir tras otro rato
        return false;      // la tecla sigue su camino normal
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        last = System.currentTimeMillis();
    }

    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent i) {
        h.removeCallbacks(check);
        return super.onUnbind(i);
    }

    private void tick() {
        int min = Prefs.idleMin(this);
        if (min <= 0 || showing) return;
        long idle = System.currentTimeMillis() - last;
        if (idle < min * 60000L) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;               // pantalla apagada o en reposo
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null && am.isMusicActive()) { last = System.currentTimeMillis(); return; }   // se está viendo algo
        if (Prefs.activeAlbums(this).isEmpty()) return;
        showing = true;
        last = System.currentTimeMillis();
        try {
            Intent i = new Intent(this, PreviewActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Throwable t) {
            showing = false;
        }
    }
}
