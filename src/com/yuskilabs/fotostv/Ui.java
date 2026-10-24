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
    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    /** Botón para mando: el seleccionado se ve azul intenso con borde blanco. */
    static Button button(Context c, String label, View.OnClickListener l) {
        Button btn = new Button(c);
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
