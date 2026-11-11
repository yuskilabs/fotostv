package com.yuskilabs.fotostv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Piezas comunes de las pantallas de ajustes (tarjetas, botones de sí/no y de valores que rotan). */
public abstract class SettingsBase extends Activity {
    protected int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    protected TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(Ui.REG);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    protected LinearLayout card(String title, int icon) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(10), dp(12), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99162A40);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), 0xFF2C4766);
        c.setBackground(bg);
        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(0xFF7FB2FF);
        hd.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = text(title, 18, Color.WHITE);
        t.setTypeface(Ui.BLACK);
        t.setPadding(dp(10), 0, 0, 0);
        hd.addView(t);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, 0, 0, dp(8));
        c.addView(hd, hp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(14));
        c.setLayoutParams(cp);
        return c;
    }

    protected Button btn(String label, int icon, View.OnClickListener l) {
        Button cb = new Ui.FitButton(this) {   // tras los dos puntos, la primera letra en mayúscula («Música: Apagada»)
            @Override public void setText(CharSequence t, BufferType type) {
                if (t != null) {
                    String s = t.toString();
                    int i = s.indexOf(": ");
                    if (i >= 0 && i + 2 < s.length() && Character.isLowerCase(s.charAt(i + 2)))
                        s = s.substring(0, i + 2) + Character.toUpperCase(s.charAt(i + 2)) + s.substring(i + 3);
                    t = s;
                }
                super.setText(t, type);
            }
        };
        Button b = Ui.icon(Ui.button(this, cb, label, l), icon);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setPadding(dp(8), dp(4), dp(6), dp(4));
        return b;
    }

    /** Fila de uno o dos botones de igual tamaño. */
    protected LinearLayout row(Button a, Button b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, dp(46), 1f);
        if (b != null) la.setMargins(0, 0, dp(5), 0);
        r.addView(a, la);
        if (b != null) {
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lb.setMargins(dp(5), 0, 0, 0);
            r.addView(b, lb);
        }
        LinearLayout.LayoutParams lr = new LinearLayout.LayoutParams(-1, -2);
        lr.setMargins(0, 0, 0, dp(8));
        r.setLayoutParams(lr);
        return r;
    }

    protected android.graphics.drawable.Drawable focusBg() {
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x00000000);
        on.setCornerRadius(dp(5));
        on.setStroke(dp(3), 0xFF3D8BFD);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0x00000000));
        return bg;
    }

    interface Cur { String get(); }
    interface Bool { boolean get(); }

    /** Botón que rota entre valores guardados en los ajustes. */
    protected Button cyc(final String prefix, final String key, final boolean isInt, final String[] vals, final String[] labs, int icon, final Cur cur) {
        final Button[] self = new Button[1];
        final Runnable show = new Runnable() {
            public void run() {
                String c = cur.get();
                String l = c;
                for (int i = 0; i < vals.length; i++) if (vals[i].equals(c)) l = labs[i];
                self[0].setText(prefix + ": " + l);
            }
        };
        self[0] = btn(prefix, icon, new View.OnClickListener() {
            public void onClick(View v) {
                String c = cur.get();
                int idx = -1;
                for (int i = 0; i < vals.length; i++) if (vals[i].equals(c)) idx = i;
                String nx = vals[(idx + 1) % vals.length];
                android.content.SharedPreferences.Editor e = Prefs.sp(SettingsBase.this).edit();
                if (isInt) e.putInt(key, Integer.parseInt(nx)); else e.putString(key, nx);
                e.commit();
                show.run();
            }
        });
        show.run();
        return self[0];
    }

    /** Botón de sí / no. */
    protected Button tog(final String label, final String key, int icon, final Bool cur) {
        final Button[] self = new Button[1];
        self[0] = btn(label, icon, new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.sp(SettingsBase.this).edit().putBoolean(key, !cur.get()).commit();
                self[0].setText(label + ": " + (cur.get() ? "sí" : "no"));
            }
        });
        self[0].setText(label + ": " + (cur.get() ? "sí" : "no"));
        return self[0];
    }


    // ---- pantallas de listas (Buscar, Viajes, Estadísticas)
    /** Fondo, cabecera con icono, título y frase, y separador. Devuelve la columna donde añadir el resto. */
    protected LinearLayout screen(String title, String sub, int icon) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(44), dp(24), dp(44), dp(14));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0A1424, 0xFF14305A}));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(0xFF7FB2FF);
        head.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(14), 0, 0, 0);
        TextView t = text(title, 30, Color.WHITE);
        t.setTypeface(Ui.BLACK);
        tt.addView(t);
        if (sub != null) tt.addView(text(sub, 14, 0xFF9FB3C8));
        head.addView(tt);
        root.addView(head);
        View sep = new View(this);
        sep.setBackgroundColor(0xFF2C4766);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(1));
        sp.setMargins(0, dp(12), 0, dp(12));
        root.addView(sep, sp);
        setContentView(root);
        return root;
    }

    /** Lista para el mando: filas de {título, detalle} con el mismo aspecto que los botones. */
    protected android.widget.ListView rowList(final java.util.List<String[]> rows) {
        android.widget.ListView lv = new android.widget.ListView(this);
        lv.setDivider(new android.graphics.drawable.ColorDrawable(0x00000000));
        lv.setDividerHeight(dp(6));
        lv.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        lv.setItemsCanFocus(false);
        lv.setVerticalScrollBarEnabled(false);
        lv.setAdapter(new android.widget.BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int i) { return rows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, android.view.ViewGroup parent) {
                LinearLayout box = cv instanceof LinearLayout ? (LinearLayout) cv : rowBox();
                String[] r = rows.get(i);
                ((TextView) box.getChildAt(0)).setText(r[0]);
                TextView d = (TextView) box.getChildAt(1);
                d.setText(r.length > 1 && r[1] != null ? r[1] : "");
                d.setVisibility(r.length > 1 && r[1] != null && r[1].length() > 0 ? View.VISIBLE : View.GONE);
                return box;
            }
        });
        return lv;
    }

    private LinearLayout rowBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFF3D8BFD);
        on.setCornerRadius(dp(6));
        on.setStroke(dp(3), Color.WHITE);
        GradientDrawable off = new GradientDrawable();
        off.setColor(0xFF22364F);
        off.setCornerRadius(dp(6));
        off.setStroke(dp(1), 0xFF3A506B);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_selected}, on);
        bg.addState(new int[]{android.R.attr.state_pressed}, on);
        bg.addState(new int[]{}, off);
        box.setBackground(bg);
        TextView a = text("", 16, Color.WHITE);
        a.setTypeface(Ui.BLACK);
        a.setSingleLine(true);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView b = text("", 13, 0xFFBBD0E6);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(a);
        box.addView(b);
        return box;
    }

    /** Abre el mosaico (como un álbum) con estas fotos. «query»: búsqueda que lo ha generado (para guardarlo como álbum virtual). */
    protected void openMosaic(String title, java.util.List<Item> items, String query) {
        if (items == null || items.isEmpty()) {
            android.widget.Toast.makeText(this, "No hay fotos que enseñar.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Session.placeItems = items;
        Session.placeTitle = title;
        Session.searchQuery = query;
        startActivity(new android.content.Intent(this, BrowserActivity.class));
    }
}
