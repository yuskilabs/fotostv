package com.yuskilabs.fotostv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** «Estadísticas» (función extra): fotos por año, lugar, álbum y cámara; cada fila abre sus fotos. */
public class StatsActivity extends SettingsBase {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<List<Item>> groups = new ArrayList<List<Item>>();     // fotos de cada fila, por columna
    private final List<List<String[]>> shown = new ArrayList<List<String[]>>();
    private final List<List<List<Item>>> rowItems = new ArrayList<List<List<Item>>>();
    private final List<ListView> lists = new ArrayList<ListView>();
    private TextView info, camInfo;
    private Button camBtn;
    private volatile boolean destroyed;
    private static final String[] TITLES = {"Por año", "Lugares", "Álbumes", "Cámaras"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = screen("Estadísticas", "Elige una fila para ver esas fotos.", R.drawable.ic_stats);
        info = text("Contando…", 14, Color.WHITE);
        info.setPadding(0, 0, 0, dp(10));
        root.addView(info);
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TITLES.length; i++) {
            final int col = i;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            TextView t = text(TITLES[i], 18, Color.WHITE);
            t.setTypeface(Ui.BLACK);
            t.setPadding(0, 0, 0, dp(6));
            c.addView(t);
            List<String[]> rows = new ArrayList<String[]>();
            shown.add(rows);
            rowItems.add(new ArrayList<List<Item>>());
            ListView lv = rowList(rows);
            lists.add(lv);
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                    List<List<Item>> ri = rowItems.get(col);
                    if (pos < 0 || pos >= ri.size()) return;
                    openMosaic(shown.get(col).get(pos)[0], ri.get(pos), null);
                }
            });
            c.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
            if (i == 3) {
                camInfo = text("", 12, 0xFF9FB3C8);
                camInfo.setPadding(0, dp(6), 0, dp(4));
                c.addView(camInfo);
                camBtn = btn("Leer cámaras", R.drawable.ic_camera, new View.OnClickListener() {
                    public void onClick(View v) { camAction(); }
                });
                c.addView(camBtn, new LinearLayout.LayoutParams(-1, dp(46)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(i == 0 ? 0 : dp(8), 0, i == TITLES.length - 1 ? 0 : dp(8), 0);
            cols.addView(c, lp);
        }
        root.addView(cols, new LinearLayout.LayoutParams(-1, 0, 1f));
        load();
        h.post(camTick);
    }

    private void load() {
        new Thread(new Runnable() {
            public void run() {
                final Data d = compute(StatsActivity.this);
                h.post(new Runnable() {
                    public void run() { if (!destroyed) show(d); }
                });
            }
        }, "stats").start();
    }

    private static class Data {
        int total, located, dated, camKnown, camRead;
        long first;
        final List<LinkedHashMap<String, List<Item>>> cols = new ArrayList<LinkedHashMap<String, List<Item>>>();
    }

    private static Data compute(Context c) {
        Data d = new Data();
        List<Item> all = Search.allVisible(c);
        HashMap<String, String> places = Search.places(c);
        HashMap<String, String> cams;
        try { cams = GeoIndex.get(c).camMap(); } catch (Throwable t) { cams = new HashMap<String, String>(); }
        d.total = all.size();
        Map<String, List<Item>> years = new HashMap<String, List<Item>>(), regs = new HashMap<String, List<Item>>(),
                albs = new HashMap<String, List<Item>>(), cms = new HashMap<String, List<Item>>();
        Calendar cal = Calendar.getInstance();
        d.first = Long.MAX_VALUE;
        for (Item it : all) {
            if (it.ts > 0) {
                cal.setTimeInMillis(it.ts);
                add(years, String.valueOf(cal.get(Calendar.YEAR)), it);
                d.dated++;
                d.first = Math.min(d.first, it.ts);
            } else add(years, "Sin fecha", it);
            String p = places.get(it.key());
            if (p != null && !"Sin nombre".equals(p)) { d.located++; add(regs, FxLogic.regionOf(p), it); }
            add(albs, it.album, it);
            String cm = cams.get(it.key());
            if (cm != null) {
                d.camRead++;
                if (cm.length() > 0) { d.camKnown++; add(cms, cm, it); }
            }
        }
        d.cols.add(sorted(years, true));
        d.cols.add(sorted(regs, false));
        d.cols.add(sorted(albs, false));
        d.cols.add(sorted(cms, false));
        return d;
    }

    private static void add(Map<String, List<Item>> m, String k, Item it) {
        List<Item> l = m.get(k);
        if (l == null) { l = new ArrayList<Item>(); m.put(k, l); }
        l.add(it);
    }

    /** Por número de fotos (o por año, del más reciente al más antiguo). */
    private static LinkedHashMap<String, List<Item>> sorted(Map<String, List<Item>> m, final boolean byKey) {
        List<Map.Entry<String, List<Item>>> l = new ArrayList<Map.Entry<String, List<Item>>>(m.entrySet());
        Collections.sort(l, new Comparator<Map.Entry<String, List<Item>>>() {
            public int compare(Map.Entry<String, List<Item>> a, Map.Entry<String, List<Item>> b) {
                if (byKey) return b.getKey().compareTo(a.getKey());   // «Sin fecha» queda al principio por orden: se corrige abajo
                return b.getValue().size() - a.getValue().size();
            }
        });
        LinkedHashMap<String, List<Item>> out = new LinkedHashMap<String, List<Item>>();
        Map.Entry<String, List<Item>> nodate = null;
        for (Map.Entry<String, List<Item>> e : l) {
            if (byKey && "Sin fecha".equals(e.getKey())) { nodate = e; continue; }
            out.put(e.getKey(), e.getValue());
        }
        if (nodate != null) out.put(nodate.getKey(), nodate.getValue());
        return out;
    }

    private void show(Data d) {
        String since = d.first < Long.MAX_VALUE ? " · desde " + new java.text.SimpleDateFormat("yyyy", MetaFetcher.ES).format(new java.util.Date(d.first)) : "";
        info.setText(n(d.total) + " fotos y vídeos" + since + " · " + n(d.located) + " con lugar · " + n(d.dated) + " con fecha");
        for (int i = 0; i < d.cols.size(); i++) {
            List<String[]> rows = shown.get(i);
            List<List<Item>> ri = rowItems.get(i);
            rows.clear();
            ri.clear();
            for (Map.Entry<String, List<Item>> e : d.cols.get(i).entrySet()) {
                rows.add(new String[]{e.getKey(), n(e.getValue().size()) + (e.getValue().size() == 1 ? " foto" : " fotos")});
                List<Item> l = new ArrayList<Item>(e.getValue());
                Collections.sort(l, new Comparator<Item>() {
                    public int compare(Item a, Item b) { return Long.compare(b.ts, a.ts); }
                });
                ri.add(l);
            }
            ((BaseAdapter) lists.get(i).getAdapter()).notifyDataSetChanged();
        }
        camInfo.setText(d.camRead == 0 ? "La cámara se apunta al revisar las ubicaciones o al abrir la ficha de una foto."
                : "Cámara conocida en " + n(d.camKnown) + " de " + n(d.total) + " fotos.");
        // el foco, a la primera lista con datos (no a «Leer cámaras», que era lo único que se podía elegir mientras cargaba)
        View f = getCurrentFocus();
        if (f == null || f == camBtn) for (ListView lv : lists) if (lv.getCount() > 0) { lv.requestFocus(); break; }
    }

    private static String n(int v) { return String.format(MetaFetcher.ES, "%,d", v); }

    // ---- lectura de cámaras en segundo plano
    private final Runnable camTick = new Runnable() {
        public void run() {
            if (destroyed) return;
            if (CamScan.running) {
                camBtn.setText("Parar (" + CamScan.done + " de " + CamScan.total + ")");
                h.postDelayed(this, 1500);
            } else {
                camBtn.setText("Leer cámaras");
                if (CamScan.finishedAt > shownAt) { shownAt = CamScan.finishedAt; load(); }
                h.postDelayed(this, 3000);
            }
        }
    };
    private long shownAt = System.currentTimeMillis();

    private void camAction() {
        if (CamScan.running) { CamScan.stop(); return; }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Leer la cámara de cada foto")
            .setMessage("Se lee el principio de cada foto (unos 160 KB por foto) para saber con qué cámara se hizo. Con muchas fotos puede tardar y gastar bastantes datos. Puedes pararlo cuando quieras; sigue mientras la app esté abierta.")
            .setPositiveButton("Empezar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int w) { CamScan.start(StatsActivity.this); h.removeCallbacks(camTick); h.post(camTick); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
