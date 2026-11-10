package com.yuskilabs.fotostv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** «Viajes» (función extra): las fotos agrupadas solas por fechas seguidas, con su lugar. */
public class TripsActivity extends SettingsBase {
    static final long GAP_MS = 30L * 60 * 60 * 1000;    // más de 30 horas sin fotos: otra salida
    static final int MIN_PHOTOS = 15;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<String[]> shown = new ArrayList<String[]>();
    private final List<FxLogic.Trip> trips = new ArrayList<FxLogic.Trip>();
    private ListView list;
    private TextView info;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = screen("Viajes y salidas", "Fotos hechas en días seguidos, agrupadas solas, con el sitio donde se hicieron la mayoría.", R.drawable.ic_flight);
        info = text("Agrupando tus fotos…", 13, 0xFF9FB3C8);
        info.setPadding(0, 0, 0, dp(8));
        root.addView(info);
        list = rowList(shown);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < 0 || pos >= trips.size()) return;
                FxLogic.Trip t = trips.get(pos);
                List<Item> l = new ArrayList<Item>(t.items);
                java.util.Collections.reverse(l);           // las más recientes primero, como en los álbumes
                openMosaic(t.title + " · " + range(t), l, null);
            }
        });
        new Thread(new Runnable() {
            public void run() {
                final List<FxLogic.Trip> res = FxLogic.trips(Search.allVisible(TripsActivity.this), Search.places(TripsActivity.this), GAP_MS, MIN_PHOTOS);
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        trips.clear();
                        trips.addAll(res);
                        shown.clear();
                        for (FxLogic.Trip t : trips) shown.add(new String[]{t.title, range(t) + "  ·  " + t.items.size() + " fotos"});
                        ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                        info.setText(trips.isEmpty() ? "No encuentro grupos de al menos " + MIN_PHOTOS + " fotos seguidas."
                                : trips.size() + " salidas encontradas. Los lugares salen a medida que la app los conoce.");
                        list.requestFocus();
                    }
                });
            }
        }, "trips").start();
    }

    /** «12–18 ago 2026», «28 dic 2025 – 3 ene 2026» o «5 jun 2026». */
    static String range(FxLogic.Trip t) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.setTimeInMillis(t.start);
        b.setTimeInMillis(t.end);
        SimpleDateFormat full = new SimpleDateFormat("d MMM yyyy", MetaFetcher.ES);
        if (a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR))
            return full.format(new Date(t.start));
        if (a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH))
            return a.get(Calendar.DAY_OF_MONTH) + "–" + full.format(new Date(t.end));
        if (a.get(Calendar.YEAR) == b.get(Calendar.YEAR))
            return new SimpleDateFormat("d MMM", MetaFetcher.ES).format(new Date(t.start)) + " – " + full.format(new Date(t.end));
        return full.format(new Date(t.start)) + " – " + full.format(new Date(t.end));
    }

    @Override protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
