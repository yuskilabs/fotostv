package com.yuskilabs.fotostv;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import android.widget.TextView;

/** Diálogos pequeños del explorador: menú con iconos y pregunta de un nombre. */
final class Dialogs {
    private Dialogs() {}

    /** Pulsación larga en un álbum: pase, pase con música, recargar, ocultar, borrar o empezar a seleccionar. */
    /** Menú de la pulsación larga con iconos (el mismo estilo para álbumes y fotos). */
    static void iconMenu(final android.content.Context ctx, String title, final String[] labels, final int[] icons, android.content.DialogInterface.OnClickListener l) {
        android.widget.ListAdapter ad = new android.widget.ArrayAdapter<String>(ctx,
                android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int i, View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getView(i, cv, parent);
                tv.setTypeface(Ui.REG);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);     // menús más compactos: caben sin desplazar
                tv.setMinHeight(Ui.dp(ctx, 36));
                tv.setMinimumHeight(Ui.dp(ctx, 36));
                tv.setPadding(tv.getPaddingLeft(), Ui.dp(ctx, 6), tv.getPaddingRight(), Ui.dp(ctx, 6));
                android.graphics.drawable.Drawable d = ctx.getResources().getDrawable(icons[i]).mutate();
                int sz = Ui.dp(ctx, 18);
                d.setBounds(0, 0, sz, sz);
                tv.setCompoundDrawables(d, null, null, null);
                tv.setCompoundDrawablePadding(Ui.dp(ctx, 14));
                tv.setGravity(Gravity.CENTER_VERTICAL);
                return tv;
            }
        };
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        if (title != null && title.length() > 0) b.setTitle(title);
        b.setAdapter(ad, l).show();
    }

    /**
     * Para diálogos con una lista y botones abajo: con izquierda o derecha en la lista, el foco salta al botón de abajo (Aceptar si lo hay);
     * y con izquierda o derecha más allá del último botón por ese lado, vuelve a la fila de la lista donde estaba.
     */
    static android.app.AlertDialog edgeKeys(final android.app.AlertDialog dlg) {
        try {
            final android.widget.ListView lv = dlg.getListView();
            if (lv == null) return dlg;
            final android.widget.Button[] bs = new android.widget.Button[3];
            int[] ids = {android.app.AlertDialog.BUTTON_NEUTRAL, android.app.AlertDialog.BUTTON_NEGATIVE, android.app.AlertDialog.BUTTON_POSITIVE};
            final java.util.List<android.widget.Button> vis = new java.util.ArrayList<android.widget.Button>();
            for (int i = 0; i < 3; i++) {
                bs[i] = dlg.getButton(ids[i]);
                if (bs[i] != null && bs[i].getVisibility() == android.view.View.VISIBLE) vis.add(bs[i]);
            }
            if (vis.isEmpty()) return dlg;
            final android.widget.Button target = bs[2] != null && bs[2].getVisibility() == android.view.View.VISIBLE ? bs[2] : vis.get(vis.size() - 1);
            final int[] back = {0};
            lv.setOnKeyListener(new android.view.View.OnKeyListener() {
                public boolean onKey(android.view.View v, int code, android.view.KeyEvent ev) {
                    if (code != android.view.KeyEvent.KEYCODE_DPAD_LEFT && code != android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                    if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        back[0] = Math.max(0, lv.getSelectedItemPosition());
                        target.requestFocus();
                    }
                    return true;
                }
            });
            final android.widget.Button first = vis.get(0), last = vis.get(vis.size() - 1);
            android.view.View.OnKeyListener bk = new android.view.View.OnKeyListener() {
                public boolean onKey(android.view.View v, int code, android.view.KeyEvent ev) {
                    boolean out = (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT && v == first)
                        || (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && v == last);
                    if (!out) return false;
                    if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        lv.requestFocus();
                        lv.setSelection(Math.min(back[0], Math.max(0, lv.getCount() - 1)));
                    }
                    return true;
                }
            };
            for (android.widget.Button b : vis) b.setOnKeyListener(bk);
        } catch (Throwable t) { /* sin el atajo, el diálogo se maneja como siempre */ }
        return dlg;
    }

    interface NameDone { void done(String name); }

    static void askName(android.content.Context ctx, String title, String current, final NameDone done) {
        final android.widget.EditText in = new android.widget.EditText(ctx);
        in.setSingleLine(true);
        in.setTextColor(Color.WHITE);
        in.setText(current);
        in.setSelection(in.getText().length());
        new android.app.AlertDialog.Builder(ctx, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(title)
            .setView(in)
            .setPositiveButton("Guardar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { done.done(in.getText().toString().trim()); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Emisora propia del álbum (suena en el pase de ese álbum). */
    static void albumMusic(final android.content.Context ctx, final Entry e) {
        final List<String[]> rows = new ArrayList<String[]>();
        rows.add(new String[]{"La de Ajustes (sin emisora propia)", ""});
        String own = Prefs.musicRadio(ctx);
        boolean ownIsPreset = false;
        for (String[] pr : Music.PRESETS) if (pr[1] != null && pr[1].equals(own)) ownIsPreset = true;
        if (own.length() > 0 && !ownIsPreset) rows.add(new String[]{"Tu emisora (la de la web)", own});
        for (String[] pr : Music.PRESETS) if (pr[1] != null) rows.add(new String[]{Music.shortName(pr[0]), pr[1]});
        String cur = Prefs.sp(ctx).getString("amusic_" + e.url, "");
        int sel = 0;
        String[] labels = new String[rows.size()];
        for (int i = 0; i < labels.length; i++) { labels[i] = rows.get(i)[0]; if (rows.get(i)[1].equals(cur)) sel = i; }
        new android.app.AlertDialog.Builder(ctx, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Música del álbum «" + e.title + "»")
            .setSingleChoiceItems(labels, sel, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Extras.setAlbumMusic(ctx, e.url, rows.get(w)[1]);
                    d.dismiss();
                    android.widget.Toast.makeText(ctx, w == 0 ? "El pase de este álbum usará la música de Ajustes." : "El pase de este álbum sonará con «" + rows.get(w)[0] + "».", android.widget.Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }
}
