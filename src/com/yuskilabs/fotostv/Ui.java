package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/** Elementos de interfaz compartidos. */
class Ui {
    private static final java.util.regex.Pattern DATE_TOKEN = java.util.regex.Pattern.compile("^[\\d.,/\\-\u2013\u2014:\u00ba\u00aa]+$");
    private static final java.util.regex.Pattern MONTHS = java.util.regex.Pattern.compile(
            "^(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|"
            + "ene|feb|mar|abr|may|jun|jul|ago|sep|sept|oct|nov|dic|january|february|march|april|june|july|august|"
            + "september|october|november|december|jan|apr|aug|dec)$");
    private static final java.util.regex.Pattern LINKS = java.util.regex.Pattern.compile(
            "^(de|del|al|a|y|e|desde|hasta|entre|el|los|-|\u2013|\u2014|\u00b7|\\||/)$");

    /** Nombre del \u00e1lbum sin fechas al final («Tenerife 2026» \u2192 «Tenerife», «Roma (3-10 mayo)» \u2192 «Roma»). */
    static String albumName(String name) {
        if (name == null) return null;
        String s = name.trim();
        String t = s.replaceAll("\\s*[\\(\\[][^\\)\\]]*\\d[^\\)\\]]*[\\)\\]]", "").trim();
        String[] w = t.split("\\s+");
        int end = w.length;
        while (end > 1) {
            String k = w[end - 1].toLowerCase(java.util.Locale.ROOT).replaceAll("[,;.:]+$", "");
            if (k.length() == 0 || DATE_TOKEN.matcher(k).matches() || MONTHS.matcher(k).matches() || LINKS.matcher(k).matches()) end--;
            else break;
        }
        int start = 0;
        while (start < end - 1 && w[start].matches("^\\d{4}([\\-/.]\\d{1,2}){0,2}[:,.\\-]?$")) start++;
        StringBuilder b = new StringBuilder();
        for (int i = start; i < end; i++) { if (b.length() > 0) b.append(' '); b.append(w[i]); }
        String r = b.toString().replaceAll("[\\s:,;.\\-\u2013\u2014\u00b7|/]+$", "").trim();
        return r.length() == 0 ? s : r;
    }

    /** Fuente de títulos (Figtree Bold; la carga App al arrancar), de botones (Regular) y de textos normales (Regular). */
    static android.graphics.Typeface BLACK = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL);
    static android.graphics.Typeface BTN = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL);
    /** Letra manuscrita (Caveat) para el lugar de la foto. */
    static android.graphics.Typeface HAND = android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL);
    static android.graphics.Typeface REG = android.graphics.Typeface.DEFAULT;

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    /** Botón para mando: el seleccionado se ve azul intenso con borde blanco. */
    static Button button(Context c, String label, View.OnClickListener l) {
        Button btn = new Button(c);
        btn.setTypeface(Ui.BTN);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btn.setOnClickListener(l);
        btn.setFocusable(true);
        btn.setPadding(dp(c, 24), dp(c, 12), dp(c, 24), dp(c, 12));

        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFF3D8BFD);
        on.setCornerRadius(dp(c, 6));
        on.setStroke(dp(c, 3), Color.WHITE);
        GradientDrawable off = new GradientDrawable();
        off.setColor(0xFF22364F);
        off.setCornerRadius(dp(c, 6));
        off.setStroke(dp(c, 1), 0xFF3A506B);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{android.R.attr.state_pressed}, on);
        bg.addState(new int[]{}, off);
        btn.setBackground(bg);
        btn.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{Color.WHITE, 0xFF9FB3C8}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(c, 24), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    /** Pone un icono blanco a la izquierda del texto del botón. */
    static Button icon(Button b, int res) {
        Context c = b.getContext();
        android.graphics.drawable.Drawable d = c.getResources().getDrawable(res);
        int sz = (int) (b.getTextSize() * 1.2f);
        d.setBounds(0, 0, sz, sz);
        b.setCompoundDrawables(d, null, null, null);
        b.setCompoundDrawablePadding(dp(c, 10));
        return b;
    }
}
