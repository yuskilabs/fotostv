package com.yuskilabs.fotostv;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** «Buscar» (función extra): fotos por lugar, nombre del álbum, mes o año. */
public class SearchActivity extends SettingsBase {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final List<String[]> rows = new ArrayList<String[]>();      // {texto, búsqueda, nº}
    private final List<String[]> shown = new ArrayList<String[]>();     // {título, detalle} que se ven
    private volatile List<Item> all;
    private volatile HashMap<String, String> places;
    private EditText input;
    private ListView list;
    private TextView info;
    private int gen;
    private boolean destroyed;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = screen("Buscar", "Escribe un lugar, el nombre de un álbum, un mes o un año («Zahara», «agosto 2025», «Lara 2024»).", R.drawable.ic_search);
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF7C93AD);
        input.setHint("Lugar, álbum, mes o año…");
        input.setTypeface(Ui.REG);
        input.setTextSize(20);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFF22364F);
        on.setCornerRadius(dp(6));
        on.setStroke(dp(3), 0xFF3D8BFD);
        GradientDrawable off = new GradientDrawable();
        off.setColor(0xFF22364F);
        off.setCornerRadius(dp(6));
        off.setStroke(dp(1), 0xFF3A506B);
        StateListDrawable bgd = new StateListDrawable();
        bgd.addState(new int[]{android.R.attr.state_focused}, on);
        bgd.addState(new int[]{}, off);
        input.setBackground(bgd);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));
        info = text("Cargando tus fotos…", 13, 0xFF9FB3C8);
        info.setPadding(0, dp(8), 0, dp(8));
        root.addView(info);
        list = rowList(shown);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { open(pos); }
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { }
            public void afterTextChanged(Editable e) { h.removeCallbacks(update); h.postDelayed(update, 350); }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int action, KeyEvent ev) {
                if (!rows.isEmpty()) { list.requestFocus(); list.setSelection(0); }
                return true;
            }
        });
        bg.submit(new Runnable() {
            public void run() {
                final List<Item> a = Search.allVisible(SearchActivity.this);
                final HashMap<String, String> p = Search.places(SearchActivity.this);
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        all = a;
                        places = p;
                        info.setText(a.size() + " fotos en tus álbumes (" + p.size() + " con lugar conocido).");
                        update.run();
                    }
                });
            }
        });
        input.requestFocus();
    }

    private final Runnable update = new Runnable() {
        public void run() {
            final List<Item> a = all;
            final HashMap<String, String> p = places;
            if (a == null || p == null) return;
            final String q = input.getText().toString();
            final int g = ++gen;
            bg.submit(new Runnable() {
                public void run() {
                    final List<String[]> s = Search.suggest(a, p, q);
                    h.post(new Runnable() {
                        public void run() {
                            if (destroyed || g != gen) return;
                            rows.clear();
                            rows.addAll(s);
                            shown.clear();
                            for (String[] r : rows) {
                                int i = r[0].lastIndexOf("  ·  ");
                                shown.add(i > 0 ? new String[]{r[0].substring(0, i), r[0].substring(i + 5)} : new String[]{r[0], ""});
                            }
                            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                            if (q.trim().length() > 0 && rows.isEmpty()) info.setText("No encuentro fotos con «" + q.trim() + "».");
                            else if (q.trim().length() > 0) info.setText("Baja con el mando para elegir.");
                        }
                    });
                }
            });
        }
    };

    private void open(int pos) {
        if (pos < 0 || pos >= rows.size() || all == null) return;
        final String[] r = rows.get(pos);
        final String query = r[1];
        String label = r[0];
        int cut = label.lastIndexOf("  ·  ");
        final String title = query.length() > 0 && query.charAt(0) > 2 ? query : (cut > 0 ? label.substring(0, cut) : label);
        final List<Item> a = all;
        final HashMap<String, String> p = places;
        bg.submit(new Runnable() {
            public void run() {
                final List<Item> res = Search.runSuggestion(a, p, query);
                h.post(new Runnable() {
                    public void run() { if (!destroyed) openMosaic(title, res, query); }
                });
            }
        });
    }

    @Override protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        super.onDestroy();
    }
}
