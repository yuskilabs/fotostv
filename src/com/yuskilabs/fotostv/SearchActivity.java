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
import android.widget.Button;
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
    private volatile int gen;
    private boolean destroyed;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = screen("Buscar", "Escribe un lugar, el nombre de un álbum, un mes o un año («Zahara», «agosto 2025», «Lara 2024»).", R.drawable.ic_search);
        input = new EditText(this) {
            // Atrás llega aquí antes que al teclado: así se sale aunque el teclado (el de Fire TV, por ejemplo) no lo suelte
            @Override public boolean onKeyPreIme(int code, KeyEvent ev) {
                if (code == KeyEvent.KEYCODE_BACK) { if (ev.getAction() == KeyEvent.ACTION_UP) back(); return true; }
                return super.onKeyPreIme(code, ev);
            }
        };
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
        goBtn = btn("Buscar", R.drawable.ic_search, new View.OnClickListener() {
            public void onClick(View v) { go(); }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = dp(8);
        root.addView(goBtn, glp);
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
            public boolean onEditorAction(TextView v, int action, KeyEvent ev) { return go(); }
        });
        // Intro del teclado (si no manda «Buscar») y ↓ del mando: dos maneras de salir del campo sin quedarse atrapado
        input.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent ev) {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                // Intro, «Siguiente» (teclados que lo mandan como tecla) y «Buscar»: lanzan la búsqueda
                if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_TAB
                        || code == 261 /* NAVIGATE_NEXT */ || code == KeyEvent.KEYCODE_SEARCH) return go();
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) { View nx = input.focusSearch(View.FOCUS_DOWN); if (nx != null) { nx.requestFocus(); return true; } }
                return false;
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
                        if (openFirst) go(); else update.run();
                    }
                });
            }
        });
        String q0 = getIntent().getStringExtra("q");
        if (q0 != null && q0.length() > 0) input.setText(q0);
        input.requestFocus();
        input.setNextFocusDownId(View.NO_ID);
        if (getIntent().getBooleanExtra("kbd", false)) {
            h.postDelayed(new Runnable() {
                public void run() {
                    if (destroyed) return;
                    try {
                        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input, 0);
                    } catch (Throwable ignored) { }
                }
            }, 350);
        }
    }

    private boolean openFirst;
    private Button goBtn;
    private long lastGo;

    /** «Buscar», Intro o «Siguiente»: busca lo escrito y abre el resultado; si no hay nada, lo dice. */
    private boolean go() {
        final String text = input.getText().toString().trim();
        if (text.length() == 0) return true;
        long now = System.currentTimeMillis();
        if (now - lastGo < 800) return true;       // Intro y «Buscar» pueden llegar los dos: una sola vez
        lastGo = now;
        final List<Item> a = all;
        final HashMap<String, String> p = places;
        if (a == null || p == null) {          // las fotos aún se están cargando: se busca en cuanto estén
            openFirst = true;
            info.setText("Cargando tus fotos… en cuanto estén, busco «" + text + "».");
            return true;
        }
        openFirst = false;
        gen++;                                   // las sugerencias pendientes ya no hacen falta
        h.removeCallbacks(update);
        info.setText("Buscando «" + text + "»…");
        bg.submit(new Runnable() {
            public void run() {
                List<Item> r0 = null;
                String err0 = null;
                try { r0 = Search.filter(a, p, text); } catch (Throwable t) { err0 = String.valueOf(t); }
                final List<Item> res = r0 == null ? new ArrayList<Item>() : r0;
                final String err = err0;
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        if (err != null) { info.setText("No se ha podido buscar: " + err); return; }
                        if (res.isEmpty()) {
                            info.setText("No encuentro fotos con «" + text + "».");
                            android.widget.Toast.makeText(SearchActivity.this, "No encuentro fotos con «" + text + "».", android.widget.Toast.LENGTH_SHORT).show();
                        } else {
                            info.setText(res.size() + " fotos con «" + text + "».");
                            openMosaic(text, res, text);
                        }
                    }
                });
            }
        });
        return true;
    }

    /** Atrás: la primera vez cierra el teclado y deja el foco en el botón «Buscar»; la segunda sale. */
    private void back() {
        if (input.hasFocus()) {
            try {
                ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(input.getWindowToken(), 0);
            } catch (Throwable ignored) { }
            goBtn.requestFocus();
            return;
        }
        leave();
    }

    private void leave() {
        try {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Throwable ignored) { }
        finish();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent ev) {
        int c = ev.getKeyCode();
        if (ev.getAction() == KeyEvent.ACTION_UP && (c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_ESCAPE
                || c == KeyEvent.KEYCODE_MEDIA_PREVIOUS)) { back(); return true; }
        if (ev.getAction() == KeyEvent.ACTION_DOWN && (c == KeyEvent.KEYCODE_BACK || c == KeyEvent.KEYCODE_ESCAPE
                || c == KeyEvent.KEYCODE_MEDIA_PREVIOUS)) return true;
        return super.dispatchKeyEvent(ev);
    }

    private String lastQ = "";

    private final Runnable update = new Runnable() {
        public void run() {
            final List<Item> a = all;
            final HashMap<String, String> p = places;
            if (a == null || p == null) return;
            final String q = input.getText().toString();
            final int g = ++gen;
            bg.submit(new Runnable() {
                public void run() {
                    if (g != gen) return;          // ya se ha escrito otra cosa: no se pierde tiempo con ésta
                    final List<String[]> s = Search.suggest(a, p, q);
                    h.post(new Runnable() {
                        public void run() {
                            if (destroyed || g != gen) return;
                            lastQ = q;
                            rows.clear();
                            rows.addAll(s);
                            shown.clear();
                            for (String[] r : rows) {
                                int i = r[0].lastIndexOf("  ·  ");
                                shown.add(i > 0 ? new String[]{r[0].substring(0, i), r[0].substring(i + 5)} : new String[]{r[0], ""});
                            }
                            ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
                            if (openFirst && q.equals(input.getText().toString())) {
                                openFirst = false;
                                if (!rows.isEmpty()) { open(0); return; }
                            }
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
