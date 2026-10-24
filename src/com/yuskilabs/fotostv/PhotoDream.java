package com.yuskilabs.fotostv;

import android.service.dreams.DreamService;
import android.view.KeyEvent;
import android.widget.FrameLayout;

public class PhotoDream extends DreamService {
    private Slideshow show;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);          // las flechas controlan el pase
        setFullscreen(true);
        setScreenBright(true);
        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        show = new Slideshow(this, root, false);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (show != null && show.infoOpen()) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) show.closeInfo();
            return true;
        }
        if (show != null && Slideshow.isNavKey(e.getKeyCode())) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) show.onKey(e.getKeyCode());
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
    }

    @Override public void onDreamingStopped() {
        if (show != null) show.stop();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (show != null) show.stop();
        super.onDetachedFromWindow();
    }
}
