package com.yuskilabs.fotostv;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

/**
 * Lógica pura de las funciones extra (sin nada de Android): se puede probar en un ordenador.
 * Modo noche, recuerdos de hoy, orden inteligente con favoritas, búsqueda, viajes y fotos parecidas.
 */
final class FxLogic {
    private FxLogic() { }

    // ------------------------------------------------------------ modo noche
    /** ¿Está «hour» (0-23) dentro de la franja «22-8» (de las 22 a las 8)? «off» o algo raro: no. */
    static boolean inWindow(String spec, int hour) {
        int[] w = window(spec);
        if (w == null) return false;
        int a = w[0], b = w[1];
        if (a == b) return false;
        return a < b ? (hour >= a && hour < b) : (hour >= a || hour < b);
    }

    /** {inicio, fin} de «22-8»; null si no es una franja válida. */
    static int[] window(String spec) {
        if (spec == null) return null;
        int i = spec.indexOf('-');
        if (i <= 0) return null;
        try {
            int a = Integer.parseInt(spec.substring(0, i).trim()), b = Integer.parseInt(spec.substring(i + 1).trim());
            if (a < 0 || a > 23 || b < 0 || b > 24) return null;
            return new int[]{a, b % 24};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ tiempo (Open-Meteo, códigos WMO)
    /** Símbolos antiguos (Unicode 1.1), que están en las fuentes de cualquier Android TV. */
    static String weatherSymbol(int code) {
        if (code == 0 || code == 1) return "\u2600";                                        // sol
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "\u2602";   // paraguas
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "\u2744";     // copo
        if (code >= 95) return "\u2608";                                                  // tormenta
        return "\u2601";                                                                  // nube
    }

    static String weatherText(int code) {
        if (code == 0) return "Despejado";
        if (code == 1 || code == 2) return "Poco nuboso";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Lluvia";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Nieve";
        if (code >= 95) return "Tormenta";
        return "";
    }

    // ------------------------------------------------------------ movimiento suave (Ken Burns)
    static final float KB_ZOOM = 0.14f;   // la foto, como mucho, un 14 % más grande

    /**
     * Recorrido de una foto: {escala inicial, final, desplazamiento X inicial, final, Y inicial, final}; los desplazamientos,
     * en fracción del ancho/alto de la pantalla. Estilos: in (acercar), out (alejar), pan (de lado, arriba/abajo o en
     * diagonal) y mix (cualquiera de ellos). Nunca asoma un borde: con la escala 1+Z, cabe un desplazamiento de Z/2.
     */
    static float[] kenBurnsPath(String style, Random r) { return kenBurnsPath(style, r, false); }

    /**
     * Con showEdges la foto empieza entera (escala 1, sin ampliar): acercar un poco, alejar (se encoge y deja ver el relleno)
     * o desplazar unos pocos puntos; así se ven las zonas que la foto no llena.
     */
    static float[] kenBurnsPath(String style, Random r, boolean showEdges) {
        if (showEdges) {
            int k;
            if ("in".equals(style)) k = 0;
            else if ("out".equals(style)) k = 1;
            else if ("pan".equals(style)) k = 2 + r.nextInt(3);
            else if ("zoom".equals(style)) k = r.nextInt(2);
            else k = r.nextInt(5);
            float ex = r.nextBoolean() ? 1f : -1f, ey = r.nextBoolean() ? 1f : -1f, p = 0.035f;
            switch (k) {
                case 0: return new float[]{1f, 1.07f, 0f, 0f, 0f, 0f};
                case 1: return new float[]{1f + KB_ZOOM, 1f, 0f, 0f, 0f, 0f};   // alejar: empieza con zoom y acaba con la foto entera
                case 2: return new float[]{1f, 1f, -ex * p, ex * p, 0f, 0f};
                case 3: return new float[]{1f, 1f, 0f, 0f, -ey * p, ey * p};
                default: return new float[]{1f, 1f, -ex * p, ex * p, -ey * p, ey * p};
            }
        }
        float z = KB_ZOOM, big = 1f + z, m = z / 2f * 0.9f;
        int kind;
        if ("in".equals(style)) kind = 0;
        else if ("out".equals(style)) kind = 1;
        else if ("pan".equals(style)) kind = 2 + r.nextInt(3);
        else if ("zoom".equals(style)) kind = r.nextInt(2);
        else kind = r.nextInt(5);
        float sx = r.nextBoolean() ? 1f : -1f, sy = r.nextBoolean() ? 1f : -1f;
        switch (kind) {
            case 0: return new float[]{1f, big, 0f, sx * m * 0.3f, 0f, sy * m * 0.3f};          // acercar (y un poco de lado)
            case 1: return new float[]{big, 1f, sx * m * 0.3f, 0f, sy * m * 0.3f, 0f};          // alejar
            case 2: return new float[]{big, big, -sx * m, sx * m, 0f, 0f};                      // de lado
            case 3: return new float[]{big, big, 0f, 0f, -sy * m, sy * m};                      // arriba o abajo
            default: return new float[]{big, big, -sx * m, sx * m, -sy * m, sy * m};            // en diagonal
        }
    }

    /** Desenfoque de caja en «passes» pasadas (con 3 se parece mucho a uno gaussiano), sobre píxeles ARGB. Bordes repetidos. */
    static void blur(int[] px, int w, int h, int radius, int passes) {
        int[] tmp = new int[px.length];
        for (int p = 0; p < passes; p++) {
            boxLine(px, tmp, w, h, radius, true);
            boxLine(tmp, px, w, h, radius, false);
        }
    }

    private static void boxLine(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h, div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = src[idx(l, Math.max(0, Math.min(len - 1, i)), w, horizontal)];
                sa += c >>> 24; sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                dst[idx(l, i, w, horizontal)] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int out = src[idx(l, Math.max(0, i - r), w, horizontal)];
                int in = src[idx(l, Math.min(len - 1, i + r + 1), w, horizontal)];
                sa += (in >>> 24) - (out >>> 24);
                sr += ((in >> 16) & 255) - ((out >> 16) & 255);
                sg += ((in >> 8) & 255) - ((out >> 8) & 255);
                sb += (in & 255) - (out & 255);
            }
        }
    }

    private static int idx(int line, int i, int w, boolean horizontal) { return horizontal ? line * w + i : i * w + line; }

    // ------------------------------------------------------------ recuerdos de hoy
    /**
     * «Recuerdos de hoy» mezclados: las fotos de hoy (de años anteriores) salen alternadas con las demás, una sí y una no,
     * hasta que se acaban; luego sigue el resto. Conserva el orden relativo de cada grupo.
     */
    static List<Item> mixToday(List<Item> order, java.util.Set<String> todayKeys) {
        if (todayKeys == null || todayKeys.isEmpty()) return order;
        List<Item> t = new ArrayList<Item>(), o = new ArrayList<Item>();
        for (Item it : order) (todayKeys.contains(it.key()) ? t : o).add(it);
        List<Item> out = new ArrayList<Item>(order.size());
        int i = 0, j = 0;
        while (i < t.size() || j < o.size()) {
            if (i < t.size()) out.add(t.get(i++));
            if (j < o.size()) out.add(o.get(j++));
        }
        return out;
    }

    /** Fotos hechas un día como hoy (mismo día y mes) de años anteriores. */
    static List<Item> onThisDay(List<Item> all, long now, TimeZone tz) {
        Calendar today = Calendar.getInstance(tz), c = Calendar.getInstance(tz);
        today.setTimeInMillis(now);
        int d = today.get(Calendar.DAY_OF_MONTH), m = today.get(Calendar.MONTH), y = today.get(Calendar.YEAR);
        List<Item> out = new ArrayList<Item>();
        for (Item it : all) {
            if (it.ts <= 0) continue;
            c.setTimeInMillis(it.ts);
            if (c.get(Calendar.DAY_OF_MONTH) == d && c.get(Calendar.MONTH) == m && c.get(Calendar.YEAR) < y) out.add(it);
        }
        return out;
    }

    /** Años completos entre la foto y hoy (por el año del calendario). */
    static int yearsAgo(long ts, long now, TimeZone tz) {
        Calendar a = Calendar.getInstance(tz), b = Calendar.getInstance(tz);
        a.setTimeInMillis(ts);
        b.setTimeInMillis(now);
        return b.get(Calendar.YEAR) - a.get(Calendar.YEAR);
    }

    // ------------------------------------------------------------ orden inteligente
    /**
     * Barajado con pesos: las ya vistas pesan 0,05 y las nuevas 1 (Efraimidis-Spirakis: clave = azar^(1/peso), de mayor a menor).
     * Con «favBoost», cada favorita sale dos veces más en la vuelta (tres en total), en sitios al azar donde no quede
     * junto a sí misma: así sale tres veces más a menudo y nunca dos veces seguidas.
     */
    static List<Item> weightedOrder(List<Item> all, Set<String> seen, Set<String> favs, boolean favBoost, Random r) {
        final Map<Item, Double> k = new IdentityHashMap<Item, Double>();
        for (Item it : all) k.put(it, Math.pow(r.nextDouble(), 1.0 / (seen.contains(it.key()) ? 0.05 : 1.0)));
        List<Item> l = new ArrayList<Item>(all);
        Collections.sort(l, new Comparator<Item>() {
            public int compare(Item a, Item b) { return Double.compare(k.get(b), k.get(a)); }
        });
        if (!favBoost || favs == null || favs.isEmpty()) return l;
        List<Item> fl = new ArrayList<Item>();
        for (Item it : all) if (favs.contains(it.key())) fl.add(it);
        for (Item f : fl) {
            for (int copy = 0; copy < 2; copy++) {
                int n = l.size(), at = -1;
                for (int t = 0; t < 40 && at < 0; t++) {        // primero al azar…
                    int p = r.nextInt(n + 1);
                    if (fits(l, p, f)) at = p;
                }
                for (int p = 0; p <= n && at < 0; p++) if (fits(l, p, f)) at = p;   // …y si no, el primer hueco que sirva
                if (at >= 0) l.add(at, f);
            }
        }
        return l;
    }

    /** ¿Se puede poner «f» en la posición p sin que quede junto a sí misma? */
    private static boolean fits(List<Item> l, int p, Item f) {
        return (p == 0 || l.get(p - 1) != f) && (p >= l.size() || l.get(p) != f);
    }

    // ------------------------------------------------------------ búsqueda
    static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre"};
    private static final String[] STOP = {"de", "del", "en", "la", "el", "los", "las", "y", "a", "al", "fotos", "foto"};

    /** Texto sin tildes y en minúsculas («Málaga» → «malaga»). */
    static String norm(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }

    static final class Query {
        int year = -1, month = -1;              // month: 0-11
        final List<String> words = new ArrayList<String>();
        boolean isEmpty() { return year < 0 && month < 0 && words.isEmpty(); }
        boolean hasDate() { return year >= 0 || month >= 0; }
    }

    /** «agosto 2025», «Málaga 2024», «Zahara» → año, mes y palabras. */
    static Query parse(String text) {
        Query q = new Query();
        for (String t : norm(text).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() == 0) continue;
            if (t.matches("\\d{4}")) {
                int y = Integer.parseInt(t);
                if (y >= 1900 && y <= 2100) { q.year = y; continue; }
            }
            int m = monthOf(t);
            if (m >= 0) { q.month = m; continue; }
            boolean stop = false;
            for (String s : STOP) if (s.equals(t)) { stop = true; break; }
            if (!stop) q.words.add(t);
        }
        return q;
    }

    /** Abreviaturas que no se confunden con palabras normales («mar» o «may» no cuentan como mes). */
    private static final String[][] ABBR = {{"ene", "0"}, {"feb", "1"}, {"abr", "3"}, {"jun", "5"}, {"jul", "6"},
            {"sep", "8"}, {"sept", "8"}, {"setiembre", "8"}, {"oct", "9"}, {"nov", "10"}, {"dic", "11"}};

    static int monthOf(String t) {
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(t)) return i;
        for (String[] a : ABBR) if (a[0].equals(t)) return Integer.parseInt(a[1]);
        return -1;
    }

    static boolean dateMatches(Query q, long ts, TimeZone tz) {
        if (!q.hasDate()) return true;
        if (ts <= 0) return false;
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ts);
        if (q.year >= 0 && c.get(Calendar.YEAR) != q.year) return false;
        if (q.month >= 0 && c.get(Calendar.MONTH) != q.month) return false;
        return true;
    }

    /** Todas las palabras aparecen en el lugar o en el nombre del álbum. */
    static boolean wordsMatch(Query q, String place, String album) {
        if (q.words.isEmpty()) return true;
        String hay = norm((place == null ? "" : place) + " " + (album == null ? "" : album));
        for (String w : q.words) if (!hay.contains(w)) return false;
        return true;
    }

    // ------------------------------------------------------------ viajes y salidas
    static final class Trip {
        final List<Item> items = new ArrayList<Item>();
        long start, end;
        String title = "";
    }

    static String regionOf(String place) {
        if (place == null) return null;
        int i = place.lastIndexOf(", ");
        return i > 0 ? place.substring(i + 2) : place;
    }

    static String townOf(String place) {
        if (place == null) return null;
        int i = place.lastIndexOf(", ");
        return i > 0 ? place.substring(0, i) : place;
    }

    /**
     * Agrupa fotos por cercanía en el tiempo: se corta cuando pasan más de «gapMs» sin fotos.
     * Solo se quedan los grupos con al menos «minPhotos». Título: la localidad más repetida
     * (o la provincia/isla/país si están repartidas), o «Sin ubicación». Del más reciente al más antiguo.
     */
    static List<Trip> trips(List<Item> items, Map<String, String> placeOf, long gapMs, int minPhotos) {
        List<Item> l = new ArrayList<Item>();
        for (Item it : items) if (it.ts > 0) l.add(it);
        Collections.sort(l, new Comparator<Item>() {
            public int compare(Item a, Item b) { return Long.compare(a.ts, b.ts); }
        });
        List<Trip> out = new ArrayList<Trip>();
        Trip cur = null;
        for (Item it : l) {
            if (cur == null || it.ts - cur.end > gapMs) {
                if (cur != null && cur.items.size() >= minPhotos) out.add(cur);
                cur = new Trip();
                cur.start = it.ts;
            }
            cur.items.add(it);
            cur.end = it.ts;
        }
        if (cur != null && cur.items.size() >= minPhotos) out.add(cur);
        for (Trip t : out) t.title = tripTitle(t, placeOf);
        Collections.reverse(out);
        return out;
    }

    private static String tripTitle(Trip t, Map<String, String> placeOf) {
        Map<String, Integer> towns = new HashMap<String, Integer>(), regions = new HashMap<String, Integer>();
        int located = 0;
        for (Item it : t.items) {
            String p = placeOf == null ? null : placeOf.get(it.key());
            if (p == null || p.length() == 0 || "Sin nombre".equals(p)) continue;
            located++;
            inc(towns, p);
            inc(regions, regionOf(p));
        }
        if (located == 0) return "Sin ubicación";
        String[] bt = best(towns), br = best(regions);
        // una localidad clara (al menos la mitad de las fotos con lugar): esa; si no, la provincia, isla o país
        if (Integer.parseInt(bt[1]) * 2 >= located) return bt[0];
        return br[0];
    }

    private static void inc(Map<String, Integer> m, String k) {
        Integer o = m.get(k);
        m.put(k, o == null ? 1 : o + 1);
    }

    private static String[] best(Map<String, Integer> m) {
        String bk = null;
        int bv = -1;
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (e.getValue() > bv || (e.getValue() == bv && e.getKey().compareTo(bk) < 0)) { bk = e.getKey(); bv = e.getValue(); }
        }
        return new String[]{bk, String.valueOf(bv)};
    }

    // ------------------------------------------------------------ fotos parecidas
    /** Grupos de fotos hechas muy seguidas (a menos de «windowMs» entre una y la siguiente). Solo grupos de 2 o más. */
    static List<List<Item>> bursts(List<Item> items, long windowMs) {
        List<Item> l = new ArrayList<Item>();
        for (Item it : items) if (it.ts > 0) l.add(it);
        Collections.sort(l, new Comparator<Item>() {
            public int compare(Item a, Item b) { return Long.compare(a.ts, b.ts); }
        });
        List<List<Item>> out = new ArrayList<List<Item>>();
        List<Item> cur = null;
        long last = 0;
        for (Item it : l) {
            if (cur == null || it.ts - last > windowMs) {
                if (cur != null && cur.size() > 1) out.add(cur);
                cur = new ArrayList<Item>();
            }
            cur.add(it);
            last = it.ts;
        }
        if (cur != null && cur.size() > 1) out.add(cur);
        return out;
    }

    /** Huella de diferencias (dHash) de una imagen en grises de 9×8: 64 bits. */
    static long dhash(int[] gray9x8) {
        long h = 0;
        int bit = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (gray9x8[y * 9 + x] > gray9x8[y * 9 + x + 1]) h |= 1L << bit;
                bit++;
            }
        }
        return h;
    }

    static int hamming(long a, long b) { return Long.bitCount(a ^ b); }

    /**
     * Dentro de un grupo de fotos seguidas, las que se parecen a una anterior que se queda (distancia ≤ max).
     * Devuelve los índices que sobran (la primera de cada parecido se conserva).
     */
    static List<Integer> similarToEarlier(long[] hashes, int max) {
        List<Integer> extra = new ArrayList<Integer>();
        List<Integer> kept = new ArrayList<Integer>();
        for (int i = 0; i < hashes.length; i++) {
            boolean dup = false;
            for (int k : kept) if (hamming(hashes[i], hashes[k]) <= max) { dup = true; break; }
            if (dup) extra.add(i); else kept.add(i);
        }
        return extra;
    }

    // ------------------------------------------------------------ quién hizo la foto
    private static final String[] BIG_CAMS = {"leica", "nikon", "canon", "fujifilm", "panasonic", "olympus", "om digital",
            "pentax", "ricoh", "sigma", "hasselblad"};

    /**
     * Quién hizo la foto: «Lola» (iPhone 12 o 13), «Alex» (iPhone 14 Pro), «Pepe» (cámara que no es un móvil: Leica, Nikon…),
     * «Lara» (el nombre del archivo empieza por PIC). "" si no encaja en nada.
     */
    static String credit(String cam, String fileName) {
        String c = cam == null ? "" : cam.toLowerCase(java.util.Locale.ROOT);
        if (c.contains("iphone 12") || c.contains("iphone 13")) return "Lola";
        if (c.contains("iphone 14 pro")) return "Alex";
        for (String b : BIG_CAMS) if (c.contains(b)) return "Pepe";
        if (c.contains("sony") && !c.contains("xperia")) return "Pepe";
        if (fileName != null && fileName.toUpperCase(java.util.Locale.ROOT).startsWith("PIC")) return "Lara";
        return "";
    }

    // ------------------------------------------------------------ calendario («Ir a la fecha»)
    /** Clave de día año*10000+mes(0-11)*100+día en la zona dada; -1 si no hay fecha. */
    static int dayKey(long ts, java.util.TimeZone tz) {
        if (ts <= 0) return -1;
        java.util.Calendar c = java.util.Calendar.getInstance(tz);
        c.setTimeInMillis(ts);
        return c.get(java.util.Calendar.YEAR) * 10000 + c.get(java.util.Calendar.MONTH) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    /** Mes (año*12+mes) siguiente o anterior que tenga fotos; el mismo si no hay más en esa dirección. */
    static int stepMonth(java.util.TreeSet<Integer> months, int cur, int dir) {
        Integer n = dir > 0 ? months.higher(cur) : months.lower(cur);
        return n == null ? cur : n;
    }

    /** Año siguiente o anterior que tenga fotos; el mes se queda en el mismo si allí hay fotos y, si no, en el más cercano. */
    static int stepYear(java.util.TreeSet<Integer> months, int cur, int dir) {
        int y = cur / 12, m = cur % 12, ny = -1;
        for (int k : months) {
            int ky = k / 12;
            if (dir > 0 && ky > y) { ny = ky; break; }
            if (dir < 0 && ky < y) ny = ky;      // el último año anterior
        }
        if (ny < 0) return cur;
        int best = -1;
        for (int k : months) if (k / 12 == ny && (best < 0 || Math.abs(k % 12 - m) < Math.abs(best % 12 - m))) best = k;
        return best;
    }
}
