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

/** Lugares donde se han hecho tus fotos: primero países / provincias / islas, luego localidades. Al elegir uno, pase con sus fotos. */
public class PlacesActivity extends Activity {
    private TextView title, sub;
    private ListView list;
    private final List<String[]> rows = new ArrayList<String[]>();   // {texto, clave}
    private HashMap<String, Integer> counts = new HashMap<String, Integer>();
    private String region;   // null = nivel de regiones

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0x553D8BFD));
        list.setItemsCanFocus(false);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) { pick(rows.get(pos)[1]); }
        });
        new Thread(new Runnable() {
            public void run() {
                final HashMap<String, Integer> c = GeoIndex.get(PlacesActivity.this).placeCounts();
                runOnUiThread(new Runnable() { public void run() { counts = c; show(null); } });
            }
        }).start();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static String num(int n) { return String.format(java.util.Locale.GERMAN, "%,d", n); }

    private void pick(String key) {
        if (key.startsWith("R:")) show(key.substring(2));
        else if (key.startsWith("A:")) Nav.openPlace(this, key.substring(2), true);    // todo el sitio mayor
        else if (key.startsWith("T:")) Nav.openPlace(this, key.substring(2), false);
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
        if (reg == null) {
            title.setText("Lugares");
            sub.setText(counts.isEmpty() ? "Aún no hay lugares: la app los va descubriendo en segundo plano (mira el contador en Ajustes)."
                    : tot.size() + " sitios · " + num(all) + " fotos con lugar. Elige uno para ver sus fotos.");
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
                t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding(dp(14), dp(12), dp(14), dp(12));
                t.setText(getItem(i)[0]);
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
        if (region != null) show(null); else super.onBackPressed();
    }
}
