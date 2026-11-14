package com.yuskilabs.fotostv;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Búsqueda en todas las fotos por lugar, nombre del álbum y fecha. Todo se llama fuera del hilo de pantalla. */
final class Search {
    private Search() { }

    /** Todas las fotos de los álbumes, sin repetir y sin las ocultas, con el nombre de su álbum. */
    static List<Item> allVisible(Context c) {
        Set<String> hid = Prefs.hidden(c);
        Set<String> seen = new HashSet<String>();
        List<Item> out = new ArrayList<Item>();
        for (Prefs.Album al : Prefs.albums(c)) {
            AlbumFetcher.AlbumData d = Prefs.albumCache(c, al.url);
            if (d == null) continue;
            String title = al.name.length() > 0 ? al.name : d.title;
            for (Item x : d.items) {
                String k = x.key();
                if (hid.contains(k) || !seen.add(k)) continue;
                x.album = title;
                if (x.akey.length() == 0) x.akey = d.key;
                out.add(x);
            }
        }
        return out;
    }

    static HashMap<String, String> places(Context c) {
        try { return GeoIndex.get(c).placeMap(); } catch (Throwable t) { return new HashMap<String, String>(); }
    }

    /** Fotos que encajan con el texto («Zahara», «agosto 2025», «Lara 2024»). */
    static List<Item> run(Context c, String text) {
        return filter(allVisible(c), places(c), text);
    }

    // textos ya normalizados (sin tildes, en minúsculas): hay pocos lugares y álbumes distintos, y normalizar en cada foto
    // hacía que buscar entre miles tardase minutos en una tele
    private static final java.util.concurrent.ConcurrentHashMap<String, String> NORM = new java.util.concurrent.ConcurrentHashMap<String, String>();

    private static String n(String s) {
        if (s == null || s.length() == 0) return "";
        String r = NORM.get(s);
        if (r == null) { r = FxLogic.norm(s); if (NORM.size() < 20000) NORM.put(s, r); }
        return r;
    }

    static List<Item> filter(List<Item> all, Map<String, String> placeOf, String text) {
        FxLogic.Query q = FxLogic.parse(text);
        List<Item> out = new ArrayList<Item>();
        if (q.isEmpty()) return out;
        TimeZone tz = TimeZone.getDefault();
        for (Item it : all) {
            if (!FxLogic.dateMatches(q, it.ts, tz)) continue;
            if (!q.words.isEmpty() && !FxLogic.wordsIn(q, n(placeOf.get(it.key())) + " " + n(it.album))) continue;
            out.add(it);
        }
        return out;
    }

    /** Sugerencias para la lista: {texto que se ve, búsqueda que lanza, nº de fotos}. Primero «todo», luego lugares y álbumes. */
    static List<String[]> suggest(List<Item> all, Map<String, String> placeOf, String text) {
        List<String[]> out = new ArrayList<String[]>();
        FxLogic.Query q = FxLogic.parse(text);
        if (q.isEmpty()) return out;
        TimeZone tz = TimeZone.getDefault();
        LinkedHashMap<String, Integer> byPlace = new LinkedHashMap<String, Integer>();
        LinkedHashMap<String, Integer> byAlbum = new LinkedHashMap<String, Integer>();
        int total = 0;
        for (Item it : all) {
            if (!FxLogic.dateMatches(q, it.ts, tz)) continue;
            String p = placeOf.get(it.key());
            if (q.words.isEmpty()) { total++; continue; }
            String np = n(p), na = n(it.album);
            if (!FxLogic.wordsIn(q, np + " " + na)) continue;
            total++;
            if (p != null && FxLogic.wordsIn(q, np)) inc(byPlace, p);
            if (FxLogic.wordsIn(q, na)) inc(byAlbum, it.album);
        }
        if (total == 0) return out;
        out.add(new String[]{"Todas: «" + text.trim() + "»  ·  " + total + " fotos", text.trim(), String.valueOf(total)});
        String date = dateText(q);
        for (Map.Entry<String, Integer> e : top(byPlace, 12))
            out.add(new String[]{e.getKey() + (date.length() > 0 ? " " + date : "") + "  ·  " + e.getValue() + " fotos",
                    "\u0001" + e.getKey() + "\u0001" + date, String.valueOf(e.getValue())});
        for (Map.Entry<String, Integer> e : top(byAlbum, 8))
            out.add(new String[]{"Álbum «" + e.getKey() + "»" + (date.length() > 0 ? " " + date : "") + "  ·  " + e.getValue() + " fotos",
                    "\u0002" + e.getKey() + "\u0002" + date, String.valueOf(e.getValue())});
        return out;
    }

    /** Lanza una sugerencia: búsqueda normal, o un lugar / álbum exacto (con la fecha si la había). */
    static List<Item> runSuggestion(List<Item> all, Map<String, String> placeOf, String s) {
        if (s.length() > 0 && (s.charAt(0) == '\u0001' || s.charAt(0) == '\u0002')) {
            boolean place = s.charAt(0) == '\u0001';
            String[] p = s.substring(1).split(place ? "\u0001" : "\u0002", -1);
            String name = p[0];
            FxLogic.Query q = FxLogic.parse(p.length > 1 ? p[1] : "");
            TimeZone tz = TimeZone.getDefault();
            List<Item> out = new ArrayList<Item>();
            for (Item it : all) {
                if (!FxLogic.dateMatches(q, it.ts, tz)) continue;
                if (place ? name.equals(placeOf.get(it.key())) : name.equals(it.album)) out.add(it);
            }
            return out;
        }
        return filter(all, placeOf, s);
    }

    private static String dateText(FxLogic.Query q) {
        String m = q.month >= 0 ? FxLogic.MONTHS[q.month] : "";
        String y = q.year >= 0 ? String.valueOf(q.year) : "";
        return (m + " " + y).trim();
    }

    private static void inc(Map<String, Integer> m, String k) {
        Integer o = m.get(k);
        m.put(k, o == null ? 1 : o + 1);
    }

    private static List<Map.Entry<String, Integer>> top(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<Map.Entry<String, Integer>>(m.entrySet());
        java.util.Collections.sort(l, new java.util.Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); }
        });
        return l.size() > n ? l.subList(0, n) : l;
    }
}
