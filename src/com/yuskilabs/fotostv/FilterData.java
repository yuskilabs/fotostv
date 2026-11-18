package com.yuskilabs.fotostv;

import android.content.Context;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Datos y reglas de «Filtrar»: valores de cada foto por categoría y el texto con el que un filtro se guarda como álbum virtual. */
final class FilterData {
    static final int N = 8;
    static final char PREFIX = '\u0004';

    private FilterData() {}

    static String nodata(int cat) { return cat <= 2 ? "Sin fecha" : "Sin dato"; }

    static String cap(String s) { return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Valores de cada foto por categoría (año, mes, día, región, localidad, álbum, cámara, autor). Es lento: fuera del hilo de pantalla. */
    static String[][] attrs(Context c, List<Item> a, Map<String, String> places) {
        Map<String, String> cams;
        try { cams = GeoIndex.get(c).camMap(); } catch (Throwable e) { cams = new HashMap<String, String>(); }
        int n = a.size();
        String[][] t = new String[N][n];
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
        return t;
    }

    /** Texto del filtro: PREFIX + categorías separadas por \u0005, y dentro de cada una los valores separados por \u0006. */
    static String encode(List<Set<String>> sel) {
        StringBuilder sb = new StringBuilder();
        sb.append(PREFIX);
        for (int i = 0; i < sel.size(); i++) {
            if (i > 0) sb.append('\u0005');
            boolean first = true;
            for (String v : sel.get(i)) { if (!first) sb.append('\u0006'); sb.append(v); first = false; }
        }
        return sb.toString();
    }

    static List<Set<String>> decode(String q) {
        List<Set<String>> sel = new ArrayList<Set<String>>();
        String[] cats = q.substring(1).split("\u0005", -1);
        for (int i = 0; i < N; i++) {
            Set<String> s = new HashSet<String>();
            if (i < cats.length && cats[i].length() > 0) for (String v : cats[i].split("\u0006", -1)) s.add(v);
            sel.add(s);
        }
        return sel;
    }

    /** ¿La foto i cumple todas las categorías menos «skip»? (-1: todas) */
    static boolean matches(String[][] t, List<Set<String>> s, int i, int skip) {
        for (int k = 0; k < N; k++) {
            if (k == skip) continue;
            Set<String> x = s.get(k);
            if (!x.isEmpty() && !x.contains(t[k][i])) return false;
        }
        return true;
    }

    /** Fotos que cumplen un filtro guardado (álbum virtual). */
    static List<Item> run(Context c, List<Item> all, Map<String, String> places, String q) {
        List<Set<String>> s = decode(q);
        String[][] t = attrs(c, all, places);
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; i < all.size(); i++) if (matches(t, s, i, -1)) out.add(all.get(i));
        return out;
    }
}
