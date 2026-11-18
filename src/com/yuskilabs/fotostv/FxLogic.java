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

    /** Fotos de esta semana (hasta 3 días antes o después de hoy) de años anteriores. */
    static List<Item> onThisWeek(List<Item> all, long now, TimeZone tz) {
        Calendar today = Calendar.getInstance(tz), c = Calendar.getInstance(tz);
        today.setTimeInMillis(now);
        int y = today.get(Calendar.YEAR);
        List<Item> out = new ArrayList<Item>();
        for (Item it : all) {
            if (it.ts <= 0) continue;
            c.setTimeInMillis(it.ts);
            int cy = c.get(Calendar.YEAR);
            if (cy >= y) continue;
            // misma fecha de calendario en el año de la foto (el 29 de febrero cae el 28 si ese año no es bisiesto)
            Calendar t = Calendar.getInstance(tz);
            t.clear();
            t.set(cy, today.get(Calendar.MONTH), 1);
            t.set(Calendar.DAY_OF_MONTH, Math.min(today.get(Calendar.DAY_OF_MONTH), t.getActualMaximum(Calendar.DAY_OF_MONTH)));
            long diff = Math.abs(c.getTimeInMillis() - t.getTimeInMillis()) / 86400000L;
            if (diff <= 3 || diff >= 362) out.add(it);   // 362: por si la semana cruza el fin de año
        }
        return out;
    }

    /** Probabilidad del mosaico según el nivel: «poco» la mitad, «normal» como siempre, «mucho» el doble. Devuelve el divisor (una de cada N). */
    static int mosaicOdds(String level, int base) {
        if ("few".equals(level)) return base * 2;
        if ("many".equals(level)) return Math.max(1, base / 2);
        return base;
    }


    // ------------------------------------------------------------ preajustes del pase
    static final String[] PRESET_IDS = {"calm", "party", "memories"};
    static final String[] PRESET_NAMES = {"Tranquilo", "Fiesta", "Solo recuerdos"};

    /** Los ajustes que fija cada preajuste (Boolean, Integer o String, según cómo se guarde cada uno). */
    static Map<String, Object> presetValues(String id) {
        Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        if ("calm".equals(id)) {
            m.put("order", "smart"); m.put("interval", 30);
            m.put("fx_kenBurns", true); m.put("fx_kbStyle", "in"); m.put("fx_kbSpeed", "slow");
            m.put("fx_mosaics", "off"); m.put("fx_transition", "fade");
            m.put("fx_today", false); m.put("fx_todayMix", false); m.put("fx_todayWeek", false);
            m.put("showAlbum", false); m.put("showPlace", true); m.put("showDate", true);
            m.put("musicMode", "radio"); m.put("musicRadio", "https://stream.antenne.de/relax/stream/mp3");   // Relax Pop
        } else if ("party".equals(id)) {
            m.put("order", "random"); m.put("interval", 10);
            m.put("fx_kenBurns", true); m.put("fx_kbStyle", "mix"); m.put("fx_kbSpeed", "normal");
            m.put("fx_mosaics", "many"); m.put("fx_transition", "wipe");
            m.put("fx_today", false); m.put("fx_todayMix", false); m.put("fx_todayWeek", false);
            m.put("showAlbum", true); m.put("showPlace", true); m.put("showDate", true);
            m.put("musicMode", "radio"); m.put("musicRadio", "https://stream.antenne.de/party-hits/stream/mp3");   // Fiesta Pop
        } else if ("memories".equals(id)) {
            m.put("order", "smart"); m.put("interval", 30);
            m.put("fx_kenBurns", true); m.put("fx_kbStyle", "mix"); m.put("fx_kbSpeed", "slow");
            m.put("fx_mosaics", "few"); m.put("fx_transition", "fade");
            m.put("fx_today", true); m.put("fx_todayMix", false); m.put("fx_todayWeek", true);
            m.put("showAlbum", true); m.put("showPlace", true); m.put("showDate", true);
            m.put("musicMode", "radio"); m.put("musicRadio", "https://stream.antenne.de/oldie-antenne/stream/mp3");   // Oldies 70-80-90
        }
        return m;
    }

    static String presetName(String id) {
        for (int i = 0; i < PRESET_IDS.length; i++) if (PRESET_IDS[i].equals(id)) return PRESET_NAMES[i];
        return "Personalizado";
    }

    /** Siguiente preajuste del ciclo (de «personalizado» al primero). */
    static String nextPreset(String id) {
        for (int i = 0; i < PRESET_IDS.length; i++) if (PRESET_IDS[i].equals(id)) return PRESET_IDS[(i + 1) % PRESET_IDS.length];
        return PRESET_IDS[0];
    }

    /** ¿Los ajustes de ahora son justo los del preajuste? (lo que falta cuenta como distinto). */
    static boolean presetMatches(Map<String, ?> now, String id) {
        Map<String, Object> want = presetValues(id);
        if (want.isEmpty()) return false;
        for (Map.Entry<String, Object> e : want.entrySet()) {
            Object v = now.get(e.getKey());
            if (v == null || !v.equals(e.getValue())) return false;
        }
        return true;
    }

    /** Guarda como texto los valores de ahora de esas claves (para poder volver a ellos): clave, tipo (b/i/s o - si no existía) y valor. */
    static String encodeBase(Map<String, ?> now, Set<String> keys) {
        StringBuilder b = new StringBuilder();
        for (String k : keys) {
            Object v = now.get(k);
            String t = v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof String ? "s" : "-";
            b.append(k).append('\t').append(t).append('\t').append(v == null ? "" : String.valueOf(v).replace('\n', ' ').replace('\t', ' ')).append('\n');
        }
        return b.toString();
    }

    /** Lo contrario: clave -> valor (null = no existía y hay que quitarla). */
    static Map<String, Object> decodeBase(String text) {
        Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        if (text == null) return m;
        for (String line : text.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length < 3 || p[0].length() == 0) continue;
            try {
                if ("b".equals(p[1])) m.put(p[0], Boolean.valueOf(p[2]));
                else if ("i".equals(p[1])) m.put(p[0], Integer.valueOf(p[2]));
                else if ("s".equals(p[1])) m.put(p[0], p[2]);
                else m.put(p[0], null);
            } catch (NumberFormatException ignored) { }
        }
        return m;
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
        return MARKS.matcher(n).replaceAll("");
    }

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");

    /** Como wordsMatch, pero con el texto ya normalizado (para no repetir ese trabajo en cada foto). */
    static boolean wordsIn(Query q, String normHay) {
        for (String w : q.words) if (!normHay.contains(w)) return false;
        return true;
    }

    /** Un tramo de fechas, con o sin año. Meses de 0 a 11. Si el final es anterior al inicio (Navidad, invierno), cruza el fin de año. */
    static final class Spec {
        int y0 = -1, y1 = -1;      // años en que empieza el tramo (-1: cualquiera)
        int sm, sd, em, ed;        // desde mes/día hasta mes/día
        boolean easter;            // Semana Santa: del Domingo de Ramos al de Pascua
    }

    static final class Query {
        final List<Spec> dates = new ArrayList<Spec>();     // si hay varias, basta con que encaje una
        String dateText = "";                               // lo escrito que era fecha, para volver a leerlo igual
        final List<String> words = new ArrayList<String>();
        boolean isEmpty() { return dates.isEmpty() && words.isEmpty(); }
        boolean hasDate() { return !dates.isEmpty(); }
    }

    private static final String YR = "((?:19|20)\\d{2})";
    private static final String YOPT = "(?:\\s+(?:de\\s+|del\\s+)?" + YR + ")?";
    private static final String MON = "(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre"
            + "|sept|sep|ene|feb|abr|jun|jul|oct|nov|dic)\\b";
    private static final String DAY = "(0?[1-9]|[12]\\d|3[01])";
    private static final String TO = "(?:\\s*-\\s*|\\s+(?:a|al|hasta)\\s+)";
    /** Frases de fecha, de más concreta a más general; entre dos que empiezan en el mismo sitio gana la primera. */
    private static final java.util.regex.Pattern[] DATE_PATS = {
        java.util.regex.Pattern.compile("\\b(?:del\\s+)?" + DAY + TO + DAY + "\\s+(?:de\\s+)?" + MON + YOPT),          // 0 del 10 al 20 de agosto 2025
        java.util.regex.Pattern.compile("\\b" + DAY + "[/.-](0?[1-9]|1[0-2])[/.-]" + YR + "\\b"),                          // 1 15/08/2025
        java.util.regex.Pattern.compile("\\b" + YR + "-(0?[1-9]|1[0-2])-" + DAY + "\\b"),                                  // 2 2025-08-15
        java.util.regex.Pattern.compile("\\b(?:del?\\s+)?" + DAY + "\\s+(?:de\\s+)?" + MON + YOPT),                     // 3 15 de agosto 2025
        java.util.regex.Pattern.compile("\\b" + MON + TO + MON + YOPT),                                                      // 4 junio-agosto 2026
        java.util.regex.Pattern.compile("\\b" + YR + TO + YR + "\\b"),                                                      // 5 2023-2025
        java.util.regex.Pattern.compile("\\b(verano|primavera|otono|invierno|navidades|navidad|semana\\s+santa)\\b" + YOPT), // 6 verano 2026
        java.util.regex.Pattern.compile("\\b" + MON + "(?:(?:\\s*,\\s*|\\s+y\\s+|\\s+)" + MON + ")+" + YOPT),           // 7 junio julio agosto 2026
        java.util.regex.Pattern.compile("\\b" + MON + YOPT),                                                                 // 8 agosto 2025
        java.util.regex.Pattern.compile("\\b" + YR + "\\b")                                                                  // 9 2025
    };
    private static final java.util.regex.Pattern MON_FIND = java.util.regex.Pattern.compile("\\b" + MON);

    /** «agosto 2025», «junio-agosto 2026», «verano», «15/08/2025», «Málaga 2024», «Zahara» → fechas y palabras. */
    static Query parse(String text) {
        Query q = new Query();
        StringBuilder s = new StringBuilder(norm(text));
        StringBuilder dt = new StringBuilder();
        while (true) {
            java.util.regex.Matcher best = null;
            int bi = -1;
            for (int i = 0; i < DATE_PATS.length; i++) {
                java.util.regex.Matcher m = DATE_PATS[i].matcher(s);
                if (m.find() && (best == null || m.start() < best.start())) { best = m; bi = i; }
            }
            if (best == null) break;
            addDates(q, bi, best);
            if (dt.length() > 0) dt.append(' ');
            dt.append(s.substring(best.start(), best.end()));
            for (int i = best.start(); i < best.end(); i++) s.setCharAt(i, ' ');
        }
        q.dateText = dt.toString();
        for (String t : s.toString().split("[^\\p{L}\\p{N}]+")) {
            if (t.length() == 0) continue;
            boolean stop = false;
            for (String w : STOP) if (w.equals(t)) { stop = true; break; }
            if (!stop) q.words.add(t);
        }
        return q;
    }

    private static int num(String g) { return g == null ? -1 : Integer.parseInt(g); }

    private static Spec spec(int y0, int y1, int sm, int sd, int em, int ed) {
        Spec p = new Spec();
        p.y0 = y0; p.y1 = y1; p.sm = sm; p.sd = sd; p.em = em; p.ed = ed;
        return p;
    }

    private static void addDates(Query q, int kind, java.util.regex.Matcher m) {
        int y = (kind == 1 || kind == 2 || kind == 5 || kind == 9) ? -1 : num(m.group(m.groupCount()));
        switch (kind) {
            case 0: { int mo = monthOf(m.group(3)); q.dates.add(spec(y, y, mo, num(m.group(1)), mo, num(m.group(2)))); break; }
            case 1: { int mo = num(m.group(2)) - 1; q.dates.add(spec(num(m.group(3)), num(m.group(3)), mo, num(m.group(1)), mo, num(m.group(1)))); break; }
            case 2: { int mo = num(m.group(2)) - 1; q.dates.add(spec(num(m.group(1)), num(m.group(1)), mo, num(m.group(3)), mo, num(m.group(3)))); break; }
            case 3: { int mo = monthOf(m.group(2)); q.dates.add(spec(y, y, mo, num(m.group(1)), mo, num(m.group(1)))); break; }
            case 4: q.dates.add(spec(y, y, monthOf(m.group(1)), 1, monthOf(m.group(2)), 31)); break;
            case 5: {
                int a = num(m.group(1)), b = num(m.group(2));
                q.dates.add(spec(Math.min(a, b), Math.max(a, b), 0, 1, 11, 31));
                break;
            }
            case 6: {
                String n = m.group(1);
                Spec p;
                if (n.startsWith("verano")) p = spec(y, y, 5, 21, 8, 22);
                else if (n.startsWith("primavera")) p = spec(y, y, 2, 20, 5, 20);
                else if (n.startsWith("otono")) p = spec(y, y, 8, 23, 11, 20);
                else if (n.startsWith("invierno")) p = spec(y, y, 11, 21, 2, 19);
                else if (n.startsWith("navid")) p = spec(y, y, 11, 20, 0, 6);
                else { p = spec(y, y, 0, 1, 11, 31); p.easter = true; }
                q.dates.add(p);
                break;
            }
            case 7: {
                java.util.regex.Matcher f = MON_FIND.matcher(m.group(0));
                while (f.find()) { int mo = monthOf(f.group(1)); q.dates.add(spec(y, y, mo, 1, mo, 31)); }
                break;
            }
            case 8: { int mo = monthOf(m.group(1)); q.dates.add(spec(y, y, mo, 1, mo, 31)); break; }
            default: q.dates.add(spec(num(m.group(1)), num(m.group(1)), 0, 1, 11, 31)); break;
        }
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
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
        for (Spec s : q.dates) if (specMatches(s, y, m, d)) return true;
        return false;
    }

    private static boolean yearIn(Spec s, int y) { return s.y0 < 0 || (y >= s.y0 && y <= s.y1); }

    /** ¿Cae el día (año, mes 0-11, día) dentro del tramo? */
    static boolean specMatches(Spec s, int y, int m, int d) {
        if (s.easter) {
            if (!yearIn(s, y)) return false;
            int e = easterOrdinal(y);
            int o = new java.util.GregorianCalendar(y, m, d).get(Calendar.DAY_OF_YEAR);
            return o >= e - 7 && o <= e;
        }
        int key = m * 100 + d, a = s.sm * 100 + s.sd, b = s.em * 100 + s.ed;
        if (a <= b) return key >= a && key <= b && yearIn(s, y);
        if (key >= a) return yearIn(s, y);          // la parte de antes del fin de año
        if (key <= b) return yearIn(s, y - 1);      // la parte de después: el tramo empezó el año anterior
        return false;
    }

    private static final java.util.concurrent.ConcurrentHashMap<Integer, Integer> EASTER = new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();

    /** Día del año (1-366) del Domingo de Pascua. */
    static int easterOrdinal(int y) {
        Integer c = EASTER.get(y);
        if (c != null) return c;
        int a = y % 19, b = y / 100, cc = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = cc / 4, k = cc % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int mm = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * mm + 114) / 31, day = ((h + l - 7 * mm + 114) % 31) + 1;
        int o = new java.util.GregorianCalendar(y, month - 1, day).get(Calendar.DAY_OF_YEAR);
        EASTER.put(y, o);
        return o;
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
    /** Reglas de partida (una por línea). Se pueden cambiar desde la web de la tele. */
    static final String DEFAULT_CREDIT_RULES =
            "# Una regla por línea. Gana la primera que encaje.\n"
          + "# cámara: lo que lleva el nombre de la cámara (varios términos separados por comas; con «-» delante, lo que NO debe llevar)\n"
          + "# archivo: con lo que empieza el nombre del archivo\n"
          + "cámara: iphone 12 = Lola\n"
          + "cámara: iphone 13 = Lola\n"
          + "cámara: iphone 14 pro = Alex\n"
          + "cámara: leica = Pepe\n"
          + "cámara: nikon = Pepe\n"
          + "cámara: canon = Pepe\n"
          + "cámara: sony, -xperia = Pepe\n"
          + "cámara: fujifilm = Pepe\n"
          + "cámara: panasonic = Pepe\n"
          + "cámara: olympus = Pepe\n"
          + "cámara: om digital = Pepe\n"
          + "cámara: pentax = Pepe\n"
          + "cámara: ricoh = Pepe\n"
          + "cámara: sigma = Pepe\n"
          + "cámara: hasselblad = Pepe\n"
          + "archivo: PIC = Lara\n";

    private static final java.util.regex.Pattern CREDIT_LINE =
            java.util.regex.Pattern.compile("^(c[aá]mara|camera|cam|archivo|file)\\s*:\\s*(.+?)\\s*=\\s*(.+?)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Quién hizo la foto según las reglas. cam: nombre de la cámara (o null); fileName: nombre del archivo (o null).
     * Solo se aplican las reglas del dato que se da. "" si no encaja en ninguna.
     */
    static String credit(String cam, String fileName, String rules) {
        String c = cam == null ? "" : cam.toLowerCase(Locale.ROOT);
        String f = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (c.length() == 0 && f.length() == 0) return "";
        for (String line : (rules == null || rules.trim().length() == 0 ? DEFAULT_CREDIT_RULES : rules).split("\n")) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            java.util.regex.Matcher m = CREDIT_LINE.matcher(line);
            if (!m.matches()) continue;
            boolean isFile = m.group(1).toLowerCase(Locale.ROOT).startsWith("a") || m.group(1).equalsIgnoreCase("file");
            String name = m.group(3);
            if (isFile) {
                if (f.length() > 0 && f.startsWith(m.group(2).toLowerCase(Locale.ROOT))) return name;
            } else if (c.length() > 0) {
                boolean ok = true;
                for (String t : m.group(2).toLowerCase(Locale.ROOT).split(",")) {
                    t = t.trim();
                    if (t.length() == 0) continue;
                    if (t.startsWith("-")) { if (c.contains(t.substring(1).trim())) ok = false; }
                    else if (!c.contains(t)) ok = false;
                }
                if (ok) return name;
            }
        }
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
