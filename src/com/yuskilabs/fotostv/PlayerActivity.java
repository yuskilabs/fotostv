package com.yuskilabs.fotostv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Pantalla completa desde el explorador: visor manual o pase automático de un álbum. */
public class PlayerActivity extends Activity {
    private Slideshow show;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Session.items == null || Session.items.isEmpty()) { finish(); return; }
        Slideshow.Config cfg = new Slideshow.Config();
        cfg.items = Session.items;
        cfg.order = Session.order;
        cfg.manual = Session.manual;
        cfg.startKey = Session.startKey;
        cfg.hint = cfg.manual
                ? "\u25C0 \u25B6 cambiar foto    \u25B2 u OK pase automático    Atrás: volver al mosaico"
                : "\u25C0 \u25B6 cambiar foto    \u25B2 pausa    \u25BC ocultar foto    Atrás: volver al mosaico";
        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        show = new Slideshow(this, root, true, cfg);
    }

    @Override protected void onResume() {
        super.onResume();
        if (show != null) show.start();
    }

    @Override protected void onPause() {
        if (show != null) {
            String k = show.currentKey();
            if (k != null) Session.lastKey = k;
            show.stop();
        }
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (show == null) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0;
        if (Slideshow.isNavKey(k)) {
            if (down) show.onKey(k);
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY
                || k == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (down) show.onKey(KeyEvent.KEYCODE_DPAD_UP);   // OK = pausa / pase automático
            return true;
        }
        return super.dispatchKeyEvent(e);                     // Atrás vuelve al mosaico
    }
}
