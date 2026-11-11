package com.yuskilabs.fotostv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Calendario mensual para «Ir a la fecha». Con el mes seleccionado, izquierda/derecha cambia de mes; con el año,
 * cambia de año (solo los que tienen fotos). Abajo, los días con fotos: al elegir uno se ven solo las de ese día.
 */
class CalendarDialog {
    interface Listener {
        void onDay(int dayKey);
        void onAll();
    }

    private static final String[] WD = {"L", "M", "X", "J", "V", "S", "D"};

    private final Context c;
    private final TreeMap<Integer, Integer> days;        // clave de día -> cuántas fotos
    private final TreeSet<Integer> months = new TreeSet<Integer>();
    private final Listener listener;
    private final boolean filtered;
    private int cur;                                       // año*12+mes que se ve
    private TextView monthV, yearV;
    private LinearLayout grid;
    private AlertDialog dlg;

    CalendarDialog(Context c, TreeMap<Integer, Integer> days, int startDay, boolean filtered, Listener l) {
        this.c = c;
        this.days = days;
        this.listener = l;
        this.filtered = filtered;
        for (int k : days.keySet()) months.add(k / 10000 * 12 + (k / 100) % 100);
        int sk = days.containsKey(startDay) ? startDay : days.lastKey();
        cur = sk / 10000 * 12 + (sk / 100) % 100;
    }

    private int dp(int v) { return Ui.dp(c, v); }

    private TextView selector() {
        final TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        t.setTypeface(Ui.BTN);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        t.setBackground(bg(0xFF22364F, 0xFF3D8BFD, 0xFF3A506B, Color.WHITE));
        t.setTextColor(Color.WHITE);
        return t;
    }

    private static StateListDrawable bg(int off, int on, int offStroke, int onStroke) {
        GradientDrawable a = new GradientDrawable();
        a.setColor(on);
        a.setCornerRadius(18);
        a.setStroke(5, onStroke);
        GradientDrawable b = new GradientDrawable();
        b.setColor(off);
        b.setCornerRadius(18);
        b.setStroke(2, offStroke);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, a);
        s.addState(new int[]{android.R.attr.state_pressed}, a);
        s.addState(new int[]{}, b);
        return s;
    }

    void show() {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(6), dp(16), dp(4));
        box.setMinimumWidth(dp(440));

        monthV = selector();
        yearV = selector();
        monthV.setId(View.generateViewId());
        LinearLayout.LayoutParams ly = new LinearLayout.LayoutParams(-1, -2), lm = new LinearLayout.LayoutParams(-1, -2);
        lm.setMargins(0, dp(6), 0, 0);
        box.addView(yearV, ly);
        box.addView(monthV, lm);

        LinearLayout wd = new LinearLayout(c);
        wd.setOrientation(LinearLayout.HORIZONTAL);
        wd.setPadding(0, dp(8), 0, dp(2));
        for (String w : WD) wd.addView(label(w, 0xFF9FB3C8, 13), new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(wd);

        grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        box.addView(grid);

        monthV.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { cur = FxLogic.stepMonth(months, cur, -1); refresh(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { cur = FxLogic.stepMonth(months, cur, 1); refresh(); return true; }
                return false;
            }
        });
        yearV.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) { cur = FxLogic.stepYear(months, cur, -1); refresh(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { cur = FxLogic.stepYear(months, cur, 1); refresh(); return true; }
                return false;
            }
        });
        View.OnClickListener toGrid = new View.OnClickListener() {
            public void onClick(View v) { View f = grid.findFocus(); if (f == null) focusFirstDay(); }
        };
        monthV.setOnClickListener(toGrid);
        yearV.setOnClickListener(toGrid);

        AlertDialog.Builder b = new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setView(box)
            .setNegativeButton("Cancelar", null);
        if (filtered) b.setNeutralButton("Ver todo", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { listener.onAll(); }
        });
        dlg = b.create();
        refresh();
        dlg.show();
        monthV.requestFocus();
    }

    private TextView label(String t, int col, int sp) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(col);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void focusFirstDay() {
        for (int r = 0; r < grid.getChildCount(); r++) {
            LinearLayout row = (LinearLayout) grid.getChildAt(r);
            for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i).isFocusable()) { row.getChildAt(i).requestFocus(); return; }
        }
    }

    private void refresh() {
        int y = cur / 12, m = cur % 12;
        String mn = FxLogic.MONTHS[m];
        monthV.setText("‹  " + Character.toUpperCase(mn.charAt(0)) + mn.substring(1) + "  ›");
        yearV.setText("‹  " + y + "  ›");
        grid.removeAllViews();
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m, 1);
        int first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;          // lunes = 0
        int n = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int d = 1 - first;
        View firstDay = null;
        while (d <= n) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 7; i++, d++) {
                View cell;
                if (d < 1 || d > n) {
                    cell = new View(c);
                } else {
                    final int key = y * 10000 + m * 100 + d;
                    Integer cnt = days.get(key);
                    TextView t = new TextView(c);
                    t.setGravity(Gravity.CENTER);
                    t.setIncludeFontPadding(false);
                    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                    if (cnt != null) {
                        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(String.valueOf(d));
                        int st = sb.length();
                        sb.append("\n(").append(String.valueOf(cnt)).append(")");
                        sb.setSpan(new android.text.style.RelativeSizeSpan(0.55f), st, sb.length(), 0);
                        sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF7FB2FF), st, sb.length(), 0);
                        t.setText(sb);
                        t.setTextColor(Color.WHITE);
                        t.setFocusable(true);
                        t.setBackground(bg(0xFF22364F, 0xFF3D8BFD, 0xFF3A506B, Color.WHITE));
                        t.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) { dlg.dismiss(); listener.onDay(key); }
                        });
                    } else {
                        t.setText(String.valueOf(d));
                        t.setTextColor(0xFF4A5A70);
                    }
                    cell = t;
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                row.addView(cell, lp);
                if (firstDay == null && cell.isFocusable()) firstDay = cell;
            }
            grid.addView(row);
        }
        for (int r = 0; r < 1 && r < grid.getChildCount(); r++) {
            LinearLayout row = (LinearLayout) grid.getChildAt(r);
            for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setNextFocusUpId(monthV.getId());
        }
        if (firstDay != null) {
            firstDay.setId(View.generateViewId());
            monthV.setNextFocusDownId(firstDay.getId());
            yearV.setNextFocusDownId(firstDay.getId());
        }
        if (grid.findFocus() == null && dlg != null && dlg.getCurrentFocus() != monthV && dlg.getCurrentFocus() != yearV) monthV.requestFocus();
    }
}
