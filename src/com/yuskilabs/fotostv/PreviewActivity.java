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
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
        FrameLayout root = new FrameLayout(this);
        setContentView(root);
        if (getIntent().getBooleanExtra("auto", false)) {
            // entrada suave cuando se abre solo por inactividad
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(1800).start();
        }
        show = new Slideshow(this, root, true);
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
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
