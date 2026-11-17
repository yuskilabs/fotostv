package com.yuskilabs.fotostv;

import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Piezas de pantalla del explorador: botón pequeño, cabecera con logo, icono con rótulo y textos. */
final class BrowserUi {
    private BrowserUi() {}

    static android.widget.Button sbtn(final android.content.Context ctx, String label, int icon, View.OnClickListener l) {
        android.widget.Button b = Ui.button(ctx, label, l);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (icon != 0) Ui.icon(b, icon);
        b.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 6), Ui.dp(ctx, 9), Ui.dp(ctx, 6));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, Ui.dp(ctx, 4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    static ImageView logoView(final android.content.Context ctx, int hdp) {
        ImageView v = new ImageView(ctx);
        v.setImageResource(R.drawable.logo_full);
        v.setAdjustViewBounds(true);
        v.setLayoutParams(new LinearLayout.LayoutParams(-2, Ui.dp(ctx, hdp)));
        return v;
    }

    /** Cabecera común de las dos pantallas (misma altura, logo y separador gris). */
    static LinearLayout header(final android.content.Context ctx) {
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(logoView(ctx, 44));
        View sep = new View(ctx);
        sep.setBackgroundColor(0xFF6B7C90);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(ctx, 2), Ui.dp(ctx, 32));
        sp.setMargins(Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16), 0);
        head.addView(sep, sp);
        return head;
    }

    /** Icono con su rótulo debajo (como «Mapa» y «Fecha»), para la esquina de arriba a la derecha. */
    static View iconBox(final android.content.Context ctx, int icon, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setFocusable(true);
        box.setClickable(true);
        int pd = Ui.dp(ctx, 4);
        box.setPadding(Ui.dp(ctx, 6), pd, Ui.dp(ctx, 6), pd);
        box.setMinimumWidth(Ui.dp(ctx, 68));
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFF3D8BFD);
        on.setCornerRadius(Ui.dp(ctx, 8));
        sl.addState(new int[]{android.R.attr.state_focused}, on);
        box.setBackground(sl);
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(icon);
        box.addView(ic, new LinearLayout.LayoutParams(Ui.dp(ctx, 26), Ui.dp(ctx, 26)));
        TextView lb = text(ctx, label, 12, 0xFF9FB3C8);          // gris, como el texto de los botones
        lb.setPadding(Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5), 0);     // holgura a los lados: la última letra no se recorta
        lb.setGravity(Gravity.CENTER);
        lb.setSingleLine(true);
        lb.setEllipsize(null);
        lb.setIncludeFontPadding(false);
        box.addView(lb, new LinearLayout.LayoutParams(-2, -2));
        box.setOnClickListener(click);
        return box;
    }

    static TextView text(final android.content.Context ctx, String s, int sp, int color) {
        TextView t = new TextView(ctx);
        t.setTypeface(Ui.REG);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
        return t;
    }
}
