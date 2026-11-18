package com.yuskilabs.fotostv;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * «Filtrar» (función extra): se eligen uno o varios valores de año, mes, día, región, localidad, álbum, cámara y autor.
 * Dentro de una categoría los valores suman (2024 o 2025); entre categorías se piden todas a la vez. Se enseñan las fotos que cumplen.
 */
public class FilterActivity extends SettingsBase {
    private static final String[] CATS = {"Año", "Mes", "Día", "Región", "Localidad", "Álbum", "Cámara", "Autor"};
    private static final int[] ICONS = {R.drawable.ic_date_range, R.drawable.ic_event, R.drawable.ic_schedule, R.drawable.ic_map,
        R.drawable.ic_place, R.drawable.ic_albums, R.drawable.ic_camera, R.drawable.ic_person};
    private static final int N = 8;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final List<Set<String>> sel = new ArrayList<Set<String>>();
    private final Button[] catBtn = new Button[N];
    private TextView info;
    private Button viewBtn, clearBtn;
    private volatile List<Item> all;
    private volatile String[][] at;
    private volatile int gen;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = screen("Filtrar", "Elige uno o varios valores en cada categoría. Se enseñan las fotos que cumplan todo a la vez.", R.drawable.ic_tune);
        info = text("Cargando tus fotos…", 14, Color.WHITE);
        info.setPadding(0, 0, 0, dp(10));
        root.addView(info);
        for (int i = 0; i < N; i++) sel.add(new HashSet<String>());
        for (int i = 0; i < N; i++) {
            final int cat = i;
            catBtn[i] = btn(label(i), ICONS[i], new View.OnClickListener() {
                public void onClick(View v) { choose(cat); }
            });
            catBtn[i].setEnabled(false);
        }
        for (int i = 0; i < N; i += 2) {
            LinearLayout r = row(catBtn[i], catBtn[i + 1]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            root.addView(r, lp);
        }
        viewBtn = btn("Ver fotos", R.drawable.ic_photo, new View.OnClickListener() {
            public void onClick(View v) { showPhotos(); }
        });
        viewBtn.setEnabled(false);
        clearBtn = btn("Quitar filtros", R.drawable.ic_close, new View.OnClickListener() {
            public void onClick(View v) {
                for (Set<String> s : sel) s.clear();
                refresh();
            }
        });
        root.addView(row(viewBtn, clearBtn));
        TextView hint = text("Dentro de una categoría los valores suman (2024 y 2025); entre categorías se piden todas (2025 y Zahara). "
            + "Cámara y autor solo salen en las fotos con la cámara ya leída (Ajustes › Ayuda y mantenimiento › Leer cámaras).", 12, 0xFF9FB3C8);
        hint.setPadding(dp(4), dp(10), dp(4), 0);
        root.addView(hint);
        load();
    }

    private static String nodata(int cat) { return cat <= 2 ? "Sin fecha" : "Sin dato"; }

    private static String cap(String s) { return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Datos de cada foto por categoría, calculados una vez. */
    private void load() {
        bg.submit(new Runnable() {
            public void run() {
                List<Item> a;
                String[][] t;
                try {
                    a = Search.allVisible(FilterActivity.this);
                    HashMap<String, String> places = Search.places(FilterActivity.this);   // también deja listos los autores
                    HashMap<String, String> cams;
                    try { cams = GeoIndex.get(FilterActivity.this).camMap(); } catch (Throwable e) { cams = new HashMap<String, String>(); }
                    int n = a.size();
                    t = new String[N][n];
                    Calendar cal = Calendar.getInstance();
                    for (int i = 0; i < n; i++) {
                        Item it = a.get(i);
                        if (it.ts > 0) {
                            cal.setTimeInMillis(it.ts);
                            t[0][i] = String.valueOf(cal.get(Calendar.YEAR));
                            t[1][i] = cap(FxLogic.MONTHS[cal.get(Calendar.MONTH)]);
                            t[2][i] = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
                        } else { t[0][i] = nodata(0); t[1][i] = nodata(1); t[2][i] = nodata(2); }
                        String p = places.get(it.key());
                        if (p == null || p.length() == 0 || "Sin nombre".equals(p)) { t[3][i] = nodata(3); t[4][i] = nodata(4); }
                        else {
                            String rg = FxLogic.regionOf(p), tw = FxLogic.townOf(p);
                            t[3][i] = rg == null ? nodata(3) : rg;
                            t[4][i] = tw == null ? nodata(4) : tw;
                        }
                        t[5][i] = it.album == null || it.album.length() == 0 ? nodata(5) : it.album;
                        String cm = cams.get(it.key());
                        t[6][i] = cm == null || cm.length() == 0 ? nodata(6) : cm;
                        String au = Search.authorOf(it.key());
                        t[7][i] = au == null || au.length() == 0 ? nodata(7) : au;
                    }
                } catch (Throwable e) {
                    Diag.e("filtrar", e);
                    a = new ArrayList<Item>();
                    t = new String[N][0];
                }
                final List<Item> fa = a;
                final String[][] ft = t;
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        all = fa;
                        at = ft;
                        for (Button b : catBtn) b.setEnabled(true);
                        refresh();
                        catBtn[0].requestFocus();
                    }
                });
            }
        });
    }

    private List<Set<String>> snapshot() {
        List<Set<String>> s = new ArrayList<Set<String>>();
        for (Set<String> x : sel) s.add(new HashSet<String>(x));
        return s;
    }

    /** ¿La foto i cumple todas las categorías menos «skip»? (-1: todas) */
    private static boolean matches(String[][] t, List<Set<String>> s, int i, int skip) {
        for (int k = 0; k < N; k++) {
            if (k == skip) continue;
            Set<String> x = s.get(k);
            if (!x.isEmpty() && !x.contains(t[k][i])) return false;
        }
        return true;
    }

    /** Pone los textos de los botones y cuenta las fotos que quedan. */
    private void refresh() {
        for (int i = 0; i < N; i++) catBtn[i].setText(label(i));
        final String[][] t = at;
        final List<Item> a = all;
        if (t == null || a == null) return;
        final List<Set<String>> s = snapshot();
        final int g = ++gen;
        bg.submit(new Runnable() {
            public void run() {
                int c = 0;
                for (int i = 0; i < a.size(); i++) if (matches(t, s, i, -1)) c++;
                final int count = c;
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed || g != gen) return;
                        boolean any = false;
                        for (Set<String> x : sel) if (!x.isEmpty()) any = true;
                        info.setText(n(count) + (count == 1 ? " foto" : " fotos") + (any ? " con este filtro" : " en total (sin filtros)")
                            + " · de " + n(a.size()));
                        viewBtn.setText("Ver fotos (" + n(count) + ")");
                        viewBtn.setEnabled(count > 0);
                        clearBtn.setEnabled(any);
                    }
                });
            }
        });
    }

    private static String n(int v) { return String.format(MetaFetcher.ES, "%,d", v); }

    private String label(int cat) {
        Set<String> s = sel.get(cat);
        if (s.isEmpty()) return CATS[cat] + ": todos";
        List<String> v = ordered(cat, s);
        String j = join(v);
        return CATS[cat] + ": " + (s.size() <= 2 && j.length() <= 22 ? j : s.size() + " elegidos");
    }

    private static String join(List<String> v) {
        StringBuilder sb = new StringBuilder();
        for (String x : v) { if (sb.length() > 0) sb.append(", "); sb.append(x); }
        return sb.toString();
    }

    /** Valores en el orden natural de la categoría: años del más reciente, meses y días en orden, el resto por nombre. */
    private List<String> ordered(final int cat, Set<String> vals) {
        List<String> l = new ArrayList<String>(vals);
        Collections.sort(l, new Comparator<String>() {
            public int compare(String a, String b) {
                boolean na = a.equals(nodata(cat)), nb = b.equals(nodata(cat));
                if (na != nb) return na ? 1 : -1;
                if (cat == 0) return b.compareTo(a);
                if (cat == 1) return monthIdx(a) - monthIdx(b);
                if (cat == 2) return parse(a) - parse(b);
                return a.compareToIgnoreCase(b);
            }
        });
        return l;
    }

    private static int parse(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }

    private static int monthIdx(String s) {
        for (int i = 0; i < FxLogic.MONTHS.length; i++) if (cap(FxLogic.MONTHS[i]).equals(s)) return i;
        return 99;
    }

    /** Abre la lista de valores de una categoría, con cuántas fotos hay de cada uno según lo ya elegido en las demás. */
    private void choose(final int cat) {
        final String[][] t = at;
        final List<Item> a = all;
        if (t == null || a == null) return;
        final List<Set<String>> s = snapshot();
        bg.submit(new Runnable() {
            public void run() {
                final Map<String, Integer> cnt = new HashMap<String, Integer>();
                for (int i = 0; i < a.size(); i++) {
                    if (!matches(t, s, i, cat)) continue;
                    String v = t[cat][i];
                    Integer o = cnt.get(v);
                    cnt.put(v, o == null ? 1 : o + 1);
                }
                h.post(new Runnable() {
                    public void run() { if (!destroyed) showChoices(cat, cnt); }
                });
            }
        });
    }

    private void showChoices(final int cat, final Map<String, Integer> cnt) {
        Set<String> keys = new HashSet<String>(cnt.keySet());
        keys.addAll(sel.get(cat));
        final List<String> vals = ordered(cat, keys);
        if (cat >= 3) {    // lugares, álbumes, cámaras y autores: los más frecuentes primero
            Collections.sort(vals, new Comparator<String>() {
                public int compare(String x, String y) {
                    boolean nx = x.equals(nodata(cat)), ny = y.equals(nodata(cat));
                    if (nx != ny) return nx ? 1 : -1;
                    int cx = cnt.get(x) == null ? 0 : cnt.get(x), cy = cnt.get(y) == null ? 0 : cnt.get(y);
                    return cx != cy ? cy - cx : x.compareToIgnoreCase(y);
                }
            });
        }
        if (vals.isEmpty()) {
            Toast.makeText(this, "No hay valores para elegir en «" + CATS[cat] + "».", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labs = new String[vals.size()];
        final boolean[] on = new boolean[vals.size()];
        for (int i = 0; i < labs.length; i++) {
            Integer c = cnt.get(vals.get(i));
            labs[i] = vals.get(i) + "   (" + n(c == null ? 0 : c) + ")";
            on[i] = sel.get(cat).contains(vals.get(i));
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(CATS[cat])
            .setMultiChoiceItems(labs, on, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int which, boolean checked) { on[which] = checked; }
            })
            .setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    Set<String> x = sel.get(cat);
                    x.clear();
                    for (int i = 0; i < on.length; i++) if (on[i]) x.add(vals.get(i));
                    refresh();
                }
            })
            .setNeutralButton("Ninguno", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { sel.get(cat).clear(); refresh(); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Abre el mosaico con las fotos que cumplen el filtro, de la más reciente a la más antigua. */
    private void showPhotos() {
        final String[][] t = at;
        final List<Item> a = all;
        if (t == null || a == null) return;
        final List<Set<String>> s = snapshot();
        final StringBuilder title = new StringBuilder();
        for (int i = 0; i < N; i++) {
            if (sel.get(i).isEmpty()) continue;
            if (title.length() > 0) title.append(" · ");
            title.append(join(ordered(i, sel.get(i))));
        }
        bg.submit(new Runnable() {
            public void run() {
                final List<Item> out = new ArrayList<Item>();
                for (int i = 0; i < a.size(); i++) if (matches(t, s, i, -1)) out.add(a.get(i));
                Collections.sort(out, new Comparator<Item>() {
                    public int compare(Item x, Item y) { return Long.compare(y.ts, x.ts); }
                });
                h.post(new Runnable() {
                    public void run() {
                        if (!destroyed) openMosaic(title.length() > 0 ? title.toString() : "Todas las fotos", out, null);
                    }
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
