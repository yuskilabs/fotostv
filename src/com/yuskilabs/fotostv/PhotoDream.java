package com.yuskilabs.fotostv;

import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.widget.FrameLayout;

public class PhotoDream extends DreamService {
    private Slideshow show;
    private final android.os.Handler hh = new android.os.Handler();
    private final Runnable stopper = new Runnable() { public void run() { finish(); } };
    private final Runnable timerEnd = new Runnable() { public void run() { finish(); } };   // temporizador (función extra)

    private void armStop() {
        hh.removeCallbacks(stopper);
        int m = Prefs.autoStopMin(this);
        if (m > 0) hh.postDelayed(stopper, m * 60000L);
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);          // las flechas controlan el pase
        setFullscreen(true);
        setScreenBright(true);
        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        show = new Slideshow(this, root, false);
        show.setKeyListener(new Runnable() { public void run() { armStop(); } });   // el mando del móvil también cuenta
    }

    private boolean eatBackUp;     // Atrás cerró la ficha: al soltarla no debe salir del salvapantallas

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int kc0 = e.getKeyCode();
        if (eatBackUp && (kc0 == KeyEvent.KEYCODE_BACK || kc0 == KeyEvent.KEYCODE_ESCAPE)) {
            if (e.getAction() == KeyEvent.ACTION_UP) eatBackUp = false;
            return true;
        }
        if (show != null && show.infoOpen()) {                                      // ficha abierta: Atrás vuelve al pase; las flechas y OK mueven y pulsan sus botones
            int kc = e.getKeyCode();
            if (kc == KeyEvent.KEYCODE_BACK || kc == KeyEvent.KEYCODE_ESCAPE) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) { show.closeInfo(); eatBackUp = true; }
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (show != null && Slideshow.isNavKey(e.getKeyCode())) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) { show.onKey(e.getKeyCode()); armStop(); }
            return true;
        }
        finish();                      // cualquier otro botón: salir
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        finish();
        return true;
    }

    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        finish();
        return true;
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (show != null) show.start();
        armStop();
        hh.removeCallbacks(timerEnd);
        if (Extras.num(this, Extras.TIMER) > 0) hh.postDelayed(timerEnd, Extras.num(this, Extras.TIMER) * 60000L);
        if (Music.enabled(this, true)) Music.start(this);
    }

    @Override public void onDreamingStopped() {
        hh.removeCallbacks(stopper);
        hh.removeCallbacks(timerEnd);
        Music.stop();
        if (show != null) show.stop();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (show != null) show.stop();
        super.onDetachedFromWindow();
    }
}
