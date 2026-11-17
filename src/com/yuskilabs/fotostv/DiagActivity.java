package com.yuskilabs.fotostv;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** «Diagnóstico»: datos del aparato y los últimos errores de la app, para poder mandarlos en una captura. */
public class DiagActivity extends SettingsBase {
    private TextView head, body;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = screen("Diagnóstico", "Los últimos fallos de la app. Si algo va mal, haz una captura de esta pantalla.", R.drawable.ic_info);
        head = text("", 14, 0xFFBBD0E6);
        head.setPadding(0, 0, 0, dp(8));
        root.addView(head);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = btn("Actualizar", R.drawable.ic_refresh, new View.OnClickListener() {
            public void onClick(View v) { fill(); }
        });
        Button clear = btn("Borrar registro", R.drawable.ic_delete, new View.OnClickListener() {
            public void onClick(View v) { Diag.clear(); fill(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(0, 0, dp(8), 0);
        row.addView(refresh, lp);
        row.addView(clear, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        body = text("", 12, Color.WHITE);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(4), dp(10), dp(4), dp(10));
        ScrollView sv = new ScrollView(this);
        sv.setFocusable(true);                    // con el mando, arriba y abajo desplazan el texto
        sv.setBackground(focusBg());
        sv.addView(body);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1f);
        sp.topMargin = dp(10);
        root.addView(sv, sp);
        fill();
    }

    private void fill() {
        head.setText(Diag.summary(this));
        String log = Diag.read();
        body.setText(log.length() == 0 ? "Sin errores registrados. Todo bien." : log);
    }
}
