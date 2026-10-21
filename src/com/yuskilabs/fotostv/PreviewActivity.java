package com.yuskilabs.fotostv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class PreviewActivity extends Activity {
    private Slideshow show;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        show = new Slideshow(this, root, true);
    }

    @Override protected void onResume() { super.onResume(); show.start(); }
    @Override protected void onPause() { show.stop(); super.onPause(); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (Slideshow.isNavKey(e.getKeyCode())) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) show.onKey(e.getKeyCode());
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }
}
