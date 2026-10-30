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
    private static final long CHECK_MS = 5000;
    private final Handler h = new Handler();
    private static volatile long last;
    private static volatile boolean connected;
    private volatile boolean showing;
    /** Qué está pasando, para enseñarlo al pulsar «Inicio automático» (ayuda a saber por qué no salta). */
    static volatile String diag = "servicio no arrancado";
    static volatile long events, keys;
    static volatile String lastBlock = "ninguno todavía";

    private final Runnable check = new Runnable() {
        public void run() {
            try { tick(); } catch (Throwable ignored) { }
            h.postDelayed(this, CHECK_MS);
        }
    };

    @Override protected void onServiceConnected() {
        last = System.currentTimeMillis();
        showing = false;
        diag = "servicio activo";
        connected = true;
        h.removeCallbacks(check);
        h.postDelayed(check, CHECK_MS);
    }

    @Override public boolean onKeyEvent(KeyEvent e) {
        last = System.currentTimeMillis();
        keys++;
        showing = false;   // si se pulsa algo, el pase (si estaba) se cierra; podrá volver a salir tras otro rato
        return false;      // la tecla sigue su camino normal
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        events++;
        last = System.currentTimeMillis();
    }

    @Override public void onInterrupt() { }

    /** Estado al momento (con el ajuste que se acaba de elegir), para enseñarlo en pantalla. */
    static String describe(Context c) {
        int min = Prefs.idleMin(c);
        if (min <= 0) return "desactivado";
        if (!connected) return "servicio no arrancado";
        long idle = (System.currentTimeMillis() - last) / 1000;
        return "sin tocar hace " + idle + " s de " + min * 60 + " s (teclas " + keys + ", eventos " + events + ")";
    }

    @Override public boolean onUnbind(Intent i) {
        connected = false;
        h.removeCallbacks(check);
        return super.onUnbind(i);
    }

    private void tick() {
        int min = Prefs.idleMin(this);
        if (min <= 0) { diag = "desactivado"; return; }
        if (showing) { diag = "el pase ya está abierto"; return; }
        long idle = System.currentTimeMillis() - last;
        diag = "sin tocar hace " + idle / 1000 + " s de " + min * 60 + " s (teclas " + keys + ", eventos " + events + ")";
        if (idle < min * 60000L) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) { diag = lastBlock = "pantalla apagada o en reposo"; return; }               // pantalla apagada o en reposo
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null && am.isMusicActive()) { diag = lastBlock = "suena audio (se reinicia la espera)"; last = System.currentTimeMillis(); return; }   // se está viendo algo
        if (Prefs.activeAlbums(this).isEmpty()) { diag = lastBlock = "no hay álbumes que mostrar"; return; }
        diag = lastBlock = "abrió el pase a las " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        showing = true;
        last = System.currentTimeMillis();
        try {
            Intent i = new Intent(this, PreviewActivity.class);
            i.putExtra("auto", true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Throwable t) {
            diag = lastBlock = "no se pudo abrir: " + t;
            showing = false;
        }
    }
}
