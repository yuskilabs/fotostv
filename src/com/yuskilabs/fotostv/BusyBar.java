package com.yuskilabs.fotostv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;

/** Aviso fijo abajo en el centro («Buscando repetidas…»), que no se quita mientras algo sigue en marcha. Solo desde el hilo principal. */
final class BusyBar {
    private final Activity act;
    private final LinkedHashMap<String, Integer> busy = new LinkedHashMap<String, Integer>();
    private TextView view;

    BusyBar(Activity act) { this.act = act; }

    void start(String label) {
        Integer n = busy.get(label);
        busy.put(label, n == null ? 1 : n + 1);
        update();
    }

    void end(String label) {
        Integer n = busy.get(label);
        if (n == null) return;
        if (n <= 1) busy.remove(label); else busy.put(label, n - 1);
        update();
    }

    private void ensure() {
        if (view != null) return;
        view = new TextView(act);
        view.setTextSize(16);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Ui.REG);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(Ui.dp(act, 22), Ui.dp(act, 10), Ui.dp(act, 22), Ui.dp(act, 10));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(0xE6323232);
        bgd.setCornerRadius(Ui.dp(act, 24));
        view.setBackground(bgd);
        view.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(act, 56);
        act.addContentView(view, lp);
    }

    private void update() {
        ensure();
        if (busy.isEmpty()) { view.setVisibility(View.GONE); return; }
        StringBuilder sb = new StringBuilder();
        for (String k : busy.keySet()) { if (sb.length() > 0) sb.append("  ·  "); sb.append(k); }
        view.setText(sb.toString());
        view.setVisibility(View.VISIBLE);
        view.bringToFront();
    }
}
