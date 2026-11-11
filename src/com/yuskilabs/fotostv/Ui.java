package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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

    private static final java.util.regex.Pattern PAREN = java.util.regex.Pattern.compile("\\s*[\\(\\[][^\\)\\]]*\\d[^\\)\\]]*[\\)\\]]");
    /** Edades entre paréntesis («0-1 año», «3 meses»): no son fechas y se conservan. */
    private static final java.util.regex.Pattern AGE = java.util.regex.Pattern.compile(
            "(?<!\\p{L})(años?|anos?|mes(es)?|semanas?|years?|months?|weeks?)(?!\\p{L})",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /** Nombre del álbum sin fechas al final («Tenerife 2026» → «Tenerife», «Roma (3-10 mayo)» → «Roma»); las edades como «(0-1 año)» o «1 año» se mantienen. */
    static String albumName(String name) {
        if (name == null) return null;
        String s = name.trim();
        java.util.regex.Matcher pm = PAREN.matcher(s);
        StringBuffer pb = new StringBuffer();
        while (pm.find()) {
            String g = pm.group();
            pm.appendReplacement(pb, AGE.matcher(g).find() ? java.util.regex.Matcher.quoteReplacement(g) : "");
        }
        pm.appendTail(pb);
        String t = pb.toString().trim();
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
        return button(c, new FitButton(c), label, l);
    }

    static Button button(Context c, Button btn, String label, View.OnClickListener l) {
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
    /** Iconos que se pueden elegir para un perfil (el orden no debe cambiar: se guarda la posición). */
    static final int[] PROFILE_ICONS = {R.drawable.ic_family, R.drawable.ic_home, R.drawable.ic_heart, R.drawable.ic_star,
        R.drawable.ic_flight, R.drawable.ic_albums, R.drawable.ic_music, R.drawable.ic_photo, R.drawable.ic_camera,
        R.drawable.ic_tv, R.drawable.ic_place, R.drawable.ic_sparkle, R.drawable.ic_moon, R.drawable.ic_cloud,
        R.drawable.ic_terrain, R.drawable.ic_video, R.drawable.ic_single};

    /** Icono de un perfil: el elegido o, si no, la familia para «Familia» y una sola persona para los demás. */
    static int profileIcon(Context c, int id) {
        int idx = Prefs.profileIconIdx(c, id);
        if (idx >= 0 && idx < PROFILE_ICONS.length) return PROFILE_ICONS[idx];
        int pos = Prefs.profileIndex(c, id);
        return pos <= 0 ? PROFILE_ICONS[0] : R.drawable.ic_single;      // Familia, la familia; los nuevos, una persona
    }

    /** Pone en la vista el icono (o la foto) del perfil. */
    static void showProfileIcon(ImageView v, int id) {
        Context c = v.getContext();
        java.io.File f = Prefs.profileImageFile(c, id);
        android.graphics.Bitmap b = f == null ? null : android.graphics.BitmapFactory.decodeFile(f.getPath());
        if (b != null) { v.setPadding(0, 0, 0, 0); v.setImageBitmap(b); }
        else { int p = dp(c, 10); v.setPadding(p, p, p, p); v.setImageResource(profileIcon(c, id)); }
    }

    /** Botón que reduce un poco la letra (hasta 10 sp) si su texto no cabe, antes de recortarlo con «…». */
    static class FitButton extends Button {
        private float basePx;
        private boolean fitting;
        FitButton(Context c) { super(c); }
        @Override public void setText(CharSequence t, BufferType type) {
            super.setText(t, type);
            if (basePx > 0) post(fit);
        }
        @Override public void setTextSize(int unit, float size) {
            super.setTextSize(unit, size);
            if (!fitting) basePx = getTextSize();
        }
        private final Runnable fit = new Runnable() { public void run() { fitNow(); } };
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (basePx <= 0) basePx = getTextSize();
            post(fit);
        }
        /** Elige la letra más grande (entre la normal y 10 sp) con la que el texto cabe, y rehace la maquetación. */
        private void fitNow() {
            int w = getWidth();
            if (w <= 0 || basePx <= 0 || getText() == null) return;
            int avail = w - getPaddingLeft() - getPaddingRight();
            android.graphics.drawable.Drawable[] cd = getCompoundDrawables();
            if (cd[0] != null) avail -= cd[0].getBounds().width() + getCompoundDrawablePadding();
            float sd = getResources().getDisplayMetrics().scaledDensity, min = 10f * sd, px = basePx;
            String s = getText().toString();
            android.text.TextPaint tp = new android.text.TextPaint(getPaint());
            tp.setTextSize(px);
            while (px > min && tp.measureText(s) > avail) { px -= 0.5f * sd; tp.setTextSize(px); }
            if (Math.abs(px - getTextSize()) < 0.25f) return;
            fitting = true;
            setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
            fitting = false;
        }
    }

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
