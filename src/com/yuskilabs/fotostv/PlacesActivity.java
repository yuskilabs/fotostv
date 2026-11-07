package com.yuskilabs.fotostv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lugares y mapa en una sola pantalla: a la izquierda la lista (países, provincias o islas y luego localidades) y a la derecha el mapa
 * con el mapa de calor. Al moverte por la lista, el mapa te lleva allí; al elegir un sitio arranca un pase con sus fotos.
 */
public class PlacesActivity extends Activity {
    private TextView title, sub;
    private ListView list;
    private MapView map;
    private final List<String[]> rows = new ArrayList<String[]>();   // {texto, clave}
    private HashMap<String, Integer> counts = new HashMap<String, Integer>();
    private int pending;      // fotos con ubicación pero aún sin nombre de lugar
    private String region;    // null = nivel de regiones
    private java.util.HashSet<String> scope;   // si no es null: solo las fotos de un álbum
    private String scopeTitle;
    private List<String[]> srows;              // fotos del álbum con ubicación (modo álbum)
    private boolean single;                    // modo álbum con un solo sitio: la lista sale ya desglosada

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        scope = Session.mapScope;
        scopeTitle = Session.mapTitle;
        Session.mapScope = null;
        Session.mapTitle = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1622);
        int p = dp(28);
        root.setPadding(p, dp(20), p, dp(16));
        title = new TextView(this);
        title.setTypeface(Ui.BLACK);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        root.addView(title);
        sub = new TextView(this);
        sub.setTypeface(Ui.REG);
        sub.setTextColor(0xFF9FB3C8);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0x553D8BFD));
        list.setItemsCanFocus(false);
        body.addView(list, new LinearLayout.LayoutParams(0, -1, 0.30f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        map = new MapView(this);
        map.setPick(new MapView.Pick() {
            public void box(double a, double b2, double c, double d) {
                fromMap = true;
                Nav.openMosaic(PlacesActivity.this, "Zona del mapa", null, false, new double[] { a, b2, c, d }, scope);
            }
        });
        right.addView(map, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = new TextView(this);
        hint.setTypeface(Ui.REG);
        hint.setTextColor(0xFF9FB3C8);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(0, dp(6), 0, 0);
        hint.setText("Lista: derecha entra en el mapa.  Mapa: flechas mover · Play acercar · Pausa alejar · OK ver las fotos de la mira · Atrás volver a la lista");
        right.addView(hint);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 0.70f);
        rp.setMargins(dp(16), 0, 0, 0);
        body.addView(right, rp);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) { pick(rows.get(pos)[1]); }
        });
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) { flyTo(pos); }
            public void onNothingSelected(AdapterView<?> a) { }
        });
        list.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    map.requestFocus();
                    return true;
                }
                return false;
            }
        });
        new Thread(new Runnable() {
            public void run() {
                GeoIndex g = GeoIndex.get(PlacesActivity.this);
                final HashMap<String, Integer> c;
                final int pend;
                final List<double[]> pts;
                if (scope == null) {
                    c = g.placeCounts();
                    pend = g.count(" WHERE st=1 AND place IS NULL");
                    pts = g.heat();
                } else {
                    srows = g.rowsOf(scope);
                    c = new HashMap<String, Integer>();
                    int pn = 0;
                    HashMap<String, double[]> cells = new HashMap<String, double[]>();
                    for (String[] r : srows) {
                        if (r[3] != null) { Integer o = c.get(r[3]); c.put(r[3], o == null ? 1 : o + 1); } else pn++;
                        double[] a = cells.get(r[4]);
                        if (a == null) { a = new double[3]; cells.put(r[4], a); }
                        a[0] += Double.parseDouble(r[1]); a[1] += Double.parseDouble(r[2]); a[2]++;
                    }
                    pend = pn;
                    pts = new ArrayList<double[]>();
                    for (double[] a : cells.values()) pts.add(new double[]{a[0] / a[2], a[1] / a[2], a[2]});
                }
                runOnUiThread(new Runnable() {
                    public void run() { counts = c; pending = pend; map.setPoints(pts); show(null); }
                });
            }
        }).start();
    }

    private boolean fromMap;

    /** Al volver del mosaico, el foco vuelve a donde estabas: mapa si vienes del mapa, lista si vienes de la lista. */
    @Override protected void onResume() {
        super.onResume();
        if (map == null || list == null) return;
        if (fromMap) map.requestFocus(); else list.requestFocus();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static String num(int n) { return String.format(java.util.Locale.GERMAN, "%,d", n); }

    private void pick(String key) {
        if (key.startsWith("R:")) show(key.substring(2));
        else if (key.startsWith("A:")) { fromMap = false; Nav.openMosaic(this, key.substring(2), key.substring(2), true, null, scope); }    // todo el sitio mayor
        else if (key.startsWith("T:")) { fromMap = false; Nav.openMosaic(this, key.substring(2), key.substring(2), false, null, scope); }
    }

    /** Al moverte por la lista, el mapa se coloca sobre ese sitio. */
    private void flyTo(int pos) {
        if (pos < 0 || pos >= rows.size()) return;
        String k = rows.get(pos)[1];
        final String want = k.substring(2);
        final boolean reg = !k.startsWith("T:");
        new Thread(new Runnable() {
            public void run() {
                final double[] b = scope == null ? GeoIndex.get(PlacesActivity.this).bbox(want, reg) : scopedBox(want, reg);
                if (b != null) runOnUiThread(new Runnable() { public void run() { map.fitBox(b); } });
            }
        }).start();
    }

    /** Rectángulo {minLat, maxLat, minLon, maxLon} de un sitio dentro del álbum. */
    private double[] scopedBox(String want, boolean reg) {
        double[] b = null;
        List<String[]> l = srows;
        if (l == null) return null;
        for (String[] r : l) {
            if (r[3] == null) continue;
            if (!(r[3].equals(want) || (reg && r[3].endsWith(", " + want)))) continue;
            double la = Double.parseDouble(r[1]), lo = Double.parseDouble(r[2]);
            if (b == null) b = new double[]{la, la, lo, lo};
            else { b[0] = Math.min(b[0], la); b[1] = Math.max(b[1], la); b[2] = Math.min(b[2], lo); b[3] = Math.max(b[3], lo); }
        }
        return b;
    }

    private void show(String reg) {
        region = reg;
        rows.clear();
        HashMap<String, Integer> tot = new HashMap<String, Integer>();
        HashMap<String, Integer> towns = new HashMap<String, Integer>();
        int all = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String r = Nav.regionOf(e.getKey());
            if (r == null) r = e.getKey();
            Integer t = tot.get(r);
            tot.put(r, (t == null ? 0 : t) + e.getValue());
            all += e.getValue();
            if (reg != null && reg.equals(r)) towns.put(e.getKey(), e.getValue());
        }
        if (scope != null && reg == null && tot.size() == 1) {   // un solo sitio: la lista sale ya desglosada
            single = true;
            show(tot.keySet().iterator().next());
            return;
        }
        String wait = pending > 0 ? " · " + num(pending) + " fotos aún sin nombre de lugar (la app los va poniendo; salen en el mapa)" : "";
        if (reg == null) {
            title.setText(scopeTitle != null ? "Mapa · " + scopeTitle : "Lugares");
            sub.setText(counts.isEmpty() ? "Aún no hay nombres de lugar: la app los va descubriendo en segundo plano (mira el contador en Ajustes)." + wait
                    : tot.size() + " sitios · " + num(all) + " fotos con lugar" + wait);
            List<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(tot.entrySet());
            Collections.sort(l, BY_COUNT);
            for (Map.Entry<String, Integer> e : l) rows.add(new String[]{e.getKey() + "   ·   " + num(e.getValue()), "R:" + e.getKey()});
        } else {
            title.setText(reg);
            sub.setText(num(tot.get(reg) == null ? 0 : tot.get(reg)) + " fotos. Atrás: volver a la lista.");
            rows.add(new String[]{"Todas las fotos de " + reg, "A:" + reg});
            List<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(towns.entrySet());
            Collections.sort(l, BY_COUNT);
            for (Map.Entry<String, Integer> e : l) rows.add(new String[]{e.getKey() + "   ·   " + num(e.getValue()), "T:" + e.getKey()});
        }
        ArrayAdapter<String[]> ad = new ArrayAdapter<String[]>(this, android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int i, View cv, ViewGroup par) {
                TextView t = new TextView(PlacesActivity.this);
                t.setTypeface(Ui.REG);
                t.setTextColor(0xFFFFB300);                 // nombre en ámbar
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setIncludeFontPadding(false);
                t.setPadding(dp(12), dp(6), dp(12), dp(6));
                String full = getItem(i)[0];
                int cut = full.indexOf("   \u00B7   ");
                android.text.SpannableString sp = new android.text.SpannableString(full);
                if (cut > 0) sp.setSpan(new android.text.style.ForegroundColorSpan(0xFFB8C7D9), cut, full.length(), 0);
                t.setText(sp);
                return t;
            }
        };
        list.setAdapter(ad);
        list.requestFocus();
        if (!rows.isEmpty()) list.setSelection(0);
    }

    private static final Comparator<Map.Entry<String, Integer>> BY_COUNT = new Comparator<Map.Entry<String, Integer>>() {
        public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
            int d = b.getValue() - a.getValue();
            return d != 0 ? d : a.getKey().compareTo(b.getKey());
        }
    };

    @Override public void onBackPressed() {
        if (map != null && map.hasFocus()) { list.requestFocus(); return; }
        if (region != null && !single) show(null); else super.onBackPressed();
    }
}
