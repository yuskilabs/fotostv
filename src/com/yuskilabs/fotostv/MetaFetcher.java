package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Vídeo sí/no, fecha y GPS (EXIF) y nombre del lugar, con caché. */
class MetaFetcher {
    static final Locale ES = new Locale("es", "ES");

    static final String[] VIDEO_EXT = {".mp4", ".mov", ".m4v", ".3gp", ".avi", ".mkv", ".webm", ".mts", ".m2ts", ".wmv", ".mpg", ".mpeg"};

    static boolean isVideoName(String name) {
        name = name.toLowerCase(Locale.US);
        for (String e : VIDEO_EXT) if (name.endsWith(e)) return true;
        return false;
    }

    /** true/false si ya se sabe si es vídeo; null si todavía no. */
    static Boolean cachedVideo(Context c, Item it) {
        SharedPreferences m = Prefs.meta(c);
        String k = "v2_" + it.key();
        return m.contains(k) ? Boolean.valueOf(m.getBoolean(k, false)) : null;
    }

    /** Averigua por lotes (una petición por cada 20 elementos) cuáles son vídeos y lo guarda. */
    static void detectVideos(Context c, List<Item> items) throws Exception {
        java.util.Map<String, List<Item>> byKey = new java.util.LinkedHashMap<String, List<Item>>();
        for (Item it : items) {
            if (it.id.length() == 0 || it.akey.length() == 0 || cachedVideo(c, it) != null) continue;
            List<Item> l = byKey.get(it.akey);
            if (l == null) { l = new java.util.ArrayList<Item>(); byKey.put(it.akey, l); }
            l.add(it);
        }
        SharedPreferences.Editor ed = Prefs.meta(c).edit();
        boolean any = false;
        for (java.util.Map.Entry<String, List<Item>> g : byKey.entrySet()) {
            List<Item> list = g.getValue();
            for (int i = 0; i < list.size(); i += 20) {
                List<Item> part = list.subList(i, Math.min(list.size(), i + 20));
                List<String> ids = new java.util.ArrayList<String>();
                for (Item it : part) ids.add(it.id);
                java.util.Map<String, String> names = AlbumFetcher.fileNames(g.getKey(), ids);
                for (Item it : part) {
                    String n = names.get(it.id);
                    if (n == null || n.length() == 0) continue;
                    ed.putBoolean("v2_" + it.key(), isVideoName(n));
                    any = true;
                }
            }
        }
        if (any) ed.apply();
    }

    static boolean isVideo(Context c, Item it) {
        SharedPreferences m = Prefs.meta(c);
        String k = "v2_" + it.key();
        if (m.contains(k)) return m.getBoolean(k, false);
        // 1) Por el nombre del archivo original
        if (it.id.length() > 0 && it.akey.length() > 0) {
            try {
                String name = AlbumFetcher.fileName(it.id, it.akey).toLowerCase(Locale.US);
                if (name.length() > 0) {
                    boolean v = isVideoName(name);
                    m.edit().putBoolean(k, v).apply();
                    return v;
                }
            } catch (Exception ignored) { }
        }
        // 2) Respaldo: por el tipo de contenido de la descarga de vídeo
        try {
            String t = AlbumFetcher.contentType(it.url + "=dv");
            boolean v = t.startsWith("video/");
            if (v || t.startsWith("image/")) m.edit().putBoolean(k, v).apply();
            return v;
        } catch (Exception e) {
            return false;
        }
    }

    /** Olvida las fotos guardadas como «sin GPS»: se vuelven a mirar la próxima vez que se muestren. Devuelve cuántas eran. */
    static int forgetMissingGps(Context c) {
        SharedPreferences m = Prefs.meta(c);
        SharedPreferences.Editor e = m.edit();
        int n = 0;
        for (java.util.Map.Entry<String, ?> en : m.getAll().entrySet()) {
            if (!(en.getValue() instanceof String)) continue;
            String k = en.getKey(), v = (String) en.getValue();
            if ((k.startsWith("x_") && v.startsWith("NaN;")) || (k.startsWith("d1_") && !v.contains("\"lat\""))) {
                e.remove(k);
                n++;
            }
        }
        e.apply();
        return n;
    }

    /** Ubicación según la ficha de Google (la que muestra Google Fotos, aunque el archivo no la lleve). */
    private static double[] googleGps(Item it, StringBuilder dump) {
        if (it.id.length() == 0 || it.akey.length() == 0) return null;
        try {
            org.json.JSONArray info = AlbumFetcher.photoInfo(it.id, it.akey);
            if (dump != null) { abbrev(info, dump, 0); }
            return findGeo(info, 0);
        } catch (Throwable t) {
            if (dump != null) dump.append("sin ficha de Google");
            return null;
        }
    }

    /** Busca una pareja [lat, lon] en grados por 10 millones (o en decimales) dentro de la ficha. */
    private static double[] findGeo(Object o, int depth) {
        if (depth > 8 || !(o instanceof org.json.JSONArray)) return null;
        org.json.JSONArray a = (org.json.JSONArray) o;
        if (a.length() == 2 && a.opt(0) instanceof Number && a.opt(1) instanceof Number) {
            double x = ((Number) a.opt(0)).doubleValue(), y = ((Number) a.opt(1)).doubleValue();
            boolean e7 = Math.abs(x) >= 1e6 && Math.abs(x) <= 9e8 && Math.abs(y) >= 1e6 && Math.abs(y) <= 1.8e9
                    && x == Math.rint(x) && y == Math.rint(y);
            if (e7) return new double[]{x / 1e7, y / 1e7};
            if (x != Math.rint(x) && y != Math.rint(y) && Math.abs(x) <= 90 && Math.abs(y) <= 180
                    && Math.abs(x) > 0.01 && Math.abs(y) > 0.01) return new double[]{x, y};
        }
        for (int i = 0; i < a.length(); i++) {
            double[] r = findGeo(a.opt(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    /** Resumen corto de la ficha (cadenas largas recortadas) para el diagnóstico. */
    private static void abbrev(Object o, StringBuilder b, int depth) {
        if (b.length() > 900) return;
        if (o instanceof org.json.JSONArray) {
            org.json.JSONArray a = (org.json.JSONArray) o;
            b.append('[');
            for (int i = 0; i < a.length() && b.length() <= 900; i++) {
                if (i > 0) b.append(',');
                abbrev(a.opt(i), b, depth + 1);
            }
            b.append(']');
        } else if (o instanceof String) {
            String s = (String) o;
            b.append('"').append(s.length() > 14 ? s.substring(0, 12) + "…" : s).append('"');
        } else b.append(o == null || o == JSONObject.NULL ? "n" : o.toString());
    }

    /** Cuántos bytes del principio del archivo original se leen para el EXIF. */
    private static final int HEAD_BYTES = 1024 * 1024;

    /** Si la descarga es un ZIP (Live Photo empaquetada), devuelve la imagen que lleva dentro; si no, los mismos bytes. */
    private static byte[] imageBytes(byte[] head) {
        byte[][] z = unzip(head);
        return z[0] != null ? z[0] : head;
    }

    /** {imagen, vídeo} dentro de un ZIP (alguno puede ser null); si no es ZIP, ambos null. */
    private static byte[][] unzip(byte[] b) {
        byte[][] r = new byte[2][];
        if (b == null || b.length < 4 || b[0] != 'P' || b[1] != 'K') return r;
        java.util.zip.ZipInputStream zi = new java.util.zip.ZipInputStream(new ByteArrayInputStream(b));
        try {
            java.util.zip.ZipEntry en;
            while ((en = zi.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                try { while ((n = zi.read(buf)) > 0 && out.size() < 4 * 1024 * 1024) out.write(buf, 0, n); }
                catch (Exception ignored) { }
                int i = isVideoName(en.getName()) ? 1 : 0;
                if (r[i] == null && out.size() > 0) r[i] = out.toByteArray();
            }
        } catch (Exception ignored) {
        } finally {
            try { zi.close(); } catch (Exception ignored) { }
        }
        return r;
    }

    private static final java.util.regex.Pattern ISO6709 =
            java.util.regex.Pattern.compile("([+-]\\d{1,2}\\.\\d{2,})([+-]\\d{1,3}\\.\\d{2,})(?=[+/\\-])");

    /** Busca una ubicación «+23.1346-082.3588+048.000/» (formato ISO 6709 de los vídeos de iPhone). */
    private static double[] scanIso6709(byte[] b) {
        if (b == null || b.length < 10) return null;
        try {
            java.util.regex.Matcher m = ISO6709.matcher(new String(b, "ISO-8859-1"));
            while (m.find()) {
                double la = Double.parseDouble(m.group(1)), lo = Double.parseDouble(m.group(2));
                if (Math.abs(la) <= 90 && Math.abs(lo) <= 180 && !(la == 0 && lo == 0)) return new double[]{la, lo};
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Ubicación de la parte de vídeo: dentro de lo ya leído, dentro del ZIP o en el final de «=dv» (donde el iPhone guarda los metadatos). */
    private static double[] videoGps(Item it, byte[] head) {
        try {
            byte[][] z = unzip(head);
            double[] g = scanIso6709(z[1] != null ? z[1] : null);
            if (g != null) return g;
            if (z[0] == null && z[1] == null) {
                g = scanIso6709(head);          // el propio archivo es un vídeo con los metadatos al principio
                if (g != null) return g;
            }
        } catch (Throwable ignored) { }
        try {
            java.net.HttpURLConnection c = AlbumFetcher.open(it.url + "=dv", null, 15000, "bytes=-400000");
            InputStream in = c.getInputStream();
            try {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while (out.size() < 2 * 1024 * 1024 && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                byte[] all = out.toByteArray();
                double[] g = scanIso6709(all);
                if (g == null && c.getResponseCode() == 200) {
                    // el servidor ignoró el rango: solo tenemos el principio; no hay más que hacer
                    g = null;
                }
                return g;
            } finally {
                try { in.close(); } catch (Exception ignored) { }
                c.disconnect();
            }
        } catch (Throwable ignored) { return null; }
    }

    /** Igual, pero solo para las fotos de la lista (un álbum). */
    static int forgetMissingGps(Context c, List<Item> items) {
        SharedPreferences m = Prefs.meta(c);
        SharedPreferences.Editor e = m.edit();
        int n = 0;
        for (Item it : items) {
            String xk = "x_" + it.key(), dk = "d1_" + it.key();
            String xv = m.getString(xk, null), dv = m.getString(dk, null);
            if (xv != null && xv.startsWith("NaN;")) { e.remove(xk); n++; }
            if (dv != null && !dv.contains("\"lat\"")) e.remove(dk);
        }
        e.apply();
        return n;
    }

    /** Tiempo tras el cual se vuelve a buscar el GPS de una foto que no lo tenía. */
    private static final long RECHECK_MS = 6L * 60 * 60 * 1000;

    /** Devuelve {lat, lon, fecha_ms}; lat/lon NaN si no hay GPS, fecha 0 si no hay. */
    static double[] exif(Context c, Item it) {
        SharedPreferences m = Prefs.meta(c);
        String k = "x_" + it.key();
        String cached = m.getString(k, null);
        double[] r = new double[]{Double.NaN, Double.NaN, 0};
        if (cached != null) {
            String[] p = cached.split(";");
            r = new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])};
            long checked = p.length > 3 ? Long.parseLong(p[3]) : 0;
            // con GPS guardado, vale siempre; sin GPS solo si se miró hace poco (puede que después se activase la ubicación al compartir)
            if (!Double.isNaN(r[0]) || System.currentTimeMillis() - checked < RECHECK_MS) return r;
        }
        try {
            byte[] head = AlbumFetcher.get(it.url + "=d", HEAD_BYTES);
            ExifInterface ex = null;       // en un vídeo no hay EXIF: se sigue igualmente con la búsqueda en sus metadatos
            try {
                ex = (ExifInterface) ExifInterface.class
                        .getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(imageBytes(head)));
            } catch (Throwable ignored) { }
            float[] ll = new float[2];
            if (ex != null && ex.getLatLong(ll) && !(ll[0] == 0f && ll[1] == 0f)) { r[0] = ll[0]; r[1] = ll[1]; }
            else {
                double[] g = videoGps(it, head);       // Live Photo / vídeo: la ubicación va en la parte de vídeo
                if (g == null) g = googleGps(it, null);  // la que tiene Google en su ficha
                if (g != null) { r[0] = g[0]; r[1] = g[1]; }
            }
            String dt = ex == null ? null : ex.getAttribute("DateTimeOriginal");
            if (dt == null && ex != null) dt = ex.getAttribute("DateTime");
            if (dt != null) {
                try { r[2] = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(dt).getTime(); }
                catch (Exception ignored) { }
            }
            m.edit().putString(k, r[0] + ";" + r[1] + ";" + r[2] + ";" + System.currentTimeMillis()).apply();
        } catch (Throwable ignored) { }
        return r;
    }

    /** Datos de la foto para el panel de información. */
    static class Details {
        double lat = Double.NaN, lon = Double.NaN, alt = Double.NaN;
        long date;                 // fecha y hora de captura (ms, 0 = desconocida)
        boolean hasTime;           // la hora viene del EXIF
        String make = "", model = "", exposure = "", fnum = "", iso = "", focal = "";
        int w, h;
        String file = "";
        String dump = "";          // ficha de Google resumida (solo si no se encontró ubicación)
        String note = "";          // cómo se ha leído (para diagnóstico cuando no hay ubicación)
    }

    private static String tag(Object ex, String name) {
        try {
            String v = ((ExifInterface) ex).getAttribute(name);
            return v == null ? "" : v.trim();
        } catch (Throwable t) { return ""; }
    }

    private static double num(String s) {
        try {
            int i = s.indexOf('/');
            if (i > 0) return Double.parseDouble(s.substring(0, i)) / Double.parseDouble(s.substring(i + 1));
            return Double.parseDouble(s);
        } catch (Throwable t) { return Double.NaN; }
    }

    static Details details(Context c, Item it) {
        Details d = new Details();
        d.date = it.ts;
        SharedPreferences m = Prefs.meta(c);
        String k = "d1_" + it.key();
        try {
            String cached = m.getString(k, null);
            JSONObject o = cached == null ? null : new JSONObject(cached);
            // sin GPS guardado hace tiempo se vuelve a mirar
            if (o != null && o.has("lat")) {
                d.lat = o.optDouble("lat", Double.NaN); d.lon = o.optDouble("lon", Double.NaN);
                d.alt = o.optDouble("alt", Double.NaN);
                if (o.optLong("date", 0) > 0) { d.date = o.optLong("date"); d.hasTime = true; }
                d.make = o.optString("make", ""); d.model = o.optString("model", "");
                d.exposure = o.optString("exp", ""); d.fnum = o.optString("f", "");
                d.iso = o.optString("iso", ""); d.focal = o.optString("foc", "");
                d.w = o.optInt("w", 0); d.h = o.optInt("h", 0);
                return d;
            }
        } catch (Throwable ignored) { }
        try {
            byte[] head = AlbumFetcher.get(it.url + "=d", HEAD_BYTES);
            d.note = (head.length > 1 && head[0] == 'P' && head[1] == 'K' ? "ZIP, " : "") + (head.length / 1024) + " KB leídos";
            ExifInterface ex = null;       // en un vídeo no hay EXIF: se sigue igualmente con la búsqueda en sus metadatos
            try {
                ex = (ExifInterface) ExifInterface.class
                        .getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(imageBytes(head)));
            } catch (Throwable ignored) { }
            float[] ll = new float[2];
            if (ex != null && ex.getLatLong(ll) && !(ll[0] == 0f && ll[1] == 0f)) { d.lat = ll[0]; d.lon = ll[1]; }
            else {
                double[] g = videoGps(it, head);
                if (g != null) { d.lat = g[0]; d.lon = g[1]; d.note += ", ubicación del vídeo"; }
                else {
                    StringBuilder dump = new StringBuilder();
                    g = googleGps(it, dump);
                    if (g != null) { d.lat = g[0]; d.lon = g[1]; d.note += ", ubicación de la ficha de Google"; }
                    else d.dump = dump.toString();
                }
            }
            try { d.alt = ex.getAltitude(Double.NaN); } catch (Throwable ignored) { }
            String dt = tag(ex, "DateTimeOriginal");
            if (dt.length() == 0) dt = tag(ex, "DateTime");
            if (dt.length() > 0) {
                try { d.date = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(dt).getTime(); d.hasTime = true; }
                catch (Exception ignored) { }
            }
            d.make = tag(ex, "Make"); d.model = tag(ex, "Model");
            double et = num(tag(ex, "ExposureTime"));
            if (!Double.isNaN(et) && et > 0) d.exposure = et >= 1 ? String.format(Locale.US, "%.1f s", et)
                    : "1/" + Math.round(1.0 / et) + " s";
            double fn = num(tag(ex, "FNumber"));
            if (!Double.isNaN(fn) && fn > 0) d.fnum = "f/" + String.format(Locale.US, "%.1f", fn);
            d.iso = tag(ex, "ISOSpeedRatings");
            double fl = num(tag(ex, "FocalLength"));
            if (!Double.isNaN(fl) && fl > 0) d.focal = String.format(Locale.US, "%.1f mm", fl).replace(".0 mm", " mm");
            try { d.w = Integer.parseInt(tag(ex, "ImageWidth")); d.h = Integer.parseInt(tag(ex, "ImageLength")); }
            catch (Exception ignored) { }
            JSONObject o = new JSONObject();
            o.put("chk", System.currentTimeMillis());
            if (!Double.isNaN(d.lat)) { o.put("lat", d.lat); o.put("lon", d.lon); }
            if (!Double.isNaN(d.alt)) o.put("alt", d.alt);
            o.put("date", d.hasTime ? d.date : 0);
            o.put("make", d.make); o.put("model", d.model); o.put("exp", d.exposure); o.put("f", d.fnum);
            o.put("iso", d.iso); o.put("foc", d.focal); o.put("w", d.w); o.put("h", d.h);
            m.edit().putString(k, o.toString()).apply();
        } catch (Throwable ignored) { }
        return d;
    }

    /**
     * Nombre del lugar. En España: «Localidad, Provincia» (o solo la ciudad si es capital de su provincia).
     * Fuera de España: «Localidad, País» (en español y con letras latinas), sin condados ni comarcas; siempre con localidad.
     */
    static String place(Context c, double lat, double lon) {
        if (Double.isNaN(lat)) return null;
        SharedPreferences m = Prefs.meta(c);
        String k = String.format(Locale.US, "p3_%.2f,%.2f", lat, lon);
        if (m.contains(k)) {
            long t = m.getLong(k + "_t", 0);
            if (t == 0 || System.currentTimeMillis() - t < 24L * 60 * 60 * 1000) return m.getString(k, null);
        }
        String name = null;
        String cc = null, country = null, town = null, region = null;
        try {
            if (Geocoder.isPresent()) {
                List<Address> l = new Geocoder(c, ES).getFromLocation(lat, lon, 5);
                if (l != null && !l.isEmpty()) {
                    Address a = l.get(0);
                    for (Address x : l) if (cc == null && x.getCountryCode() != null && x.getCountryCode().length() > 0) cc = x.getCountryCode();
                    if (cc != null && !"ES".equalsIgnoreCase(cc)) {
                        for (Address x : l) {
                            if (town == null) town = firstLatin(x.getLocality(), x.getSubLocality());
                            if (region == null) region = firstLatin(x.getAdminArea());
                        }
                    } else {
                        String tn = first(a.getLocality(), a.getSubLocality(), a.getFeatureName());
                        String rg = first(a.getSubAdminArea(), a.getAdminArea(), a.getCountryName());
                        name = join(tn, rg);
                    }
                }
            }
        } catch (Exception ignored) { }
        if (name == null && (cc == null || !"ES".equalsIgnoreCase(cc))) {
            if (town == null) {
                try {
                    String url = String.format(Locale.US,
                            "https://nominatim.openstreetmap.org/reverse?format=json&zoom=14&lat=%.5f&lon=%.5f&accept-language=",
                            lat, lon) + URLEncoder.encode("es,en", "UTF-8");
                    JSONObject a = new JSONObject(new String(AlbumFetcher.get(url), "UTF-8")).getJSONObject("address");
                    String ncc = a.optString("country_code", "");
                    if (ncc.length() > 0) cc = ncc;
                    if ("es".equalsIgnoreCase(cc)) {
                        String tn = first(a.optString("city", null), a.optString("town", null),
                                a.optString("village", null), a.optString("municipality", null));
                        String rg = first(a.optString("province", null), a.optString("county", null),
                                a.optString("state", null), a.optString("country", null));
                        name = join(tn, rg);
                    } else {
                        town = firstLatin(a.optString("city", null), a.optString("town", null),
                                a.optString("village", null), a.optString("municipality", null),
                                a.optString("hamlet", null), a.optString("suburb", null),
                                a.optString("city_district", null), a.optString("neighbourhood", null));
                        if (region == null) region = firstLatin(a.optString("state", null));
                    }
                } catch (Exception ignored) { }
            }
            if (name == null && town == null) town = nearestPlace(lat, lon);   // mar abierto, campo: el núcleo más cercano
            if (name == null) {
                country = countryName(cc);
                if (country == null) country = "";
                boolean complete = town != null;
                String t2 = castellano(town != null ? town : region);
                name = join(t2, country.length() > 0 ? country : null);
                if (name != null && name.length() == 0) name = null;
                if (name != null) {
                    SharedPreferences.Editor ed = m.edit().putString(k, name);
                    if (complete) ed.remove(k + "_t"); else ed.putLong(k + "_t", System.currentTimeMillis());
                    ed.apply();
                }
                return name;
            }
        }
        if (name != null) m.edit().putString(k, name).remove(k + "_t").apply();
        return name;
    }

    /** Palabras genéricas de lugar en inglés a castellano («Malcapuya Island» → «Isla Malcapuya»). */
    private static String castellano(String n) {
        if (n == null) return null;
        String[][] sufijos = {{" Islands", "Islas"}, {" Island", "Isla"}, {" Beach", "Playa"}, {" Bay", "Bahía"},
                {" Lake", "Lago"}, {" Mountain", "Monte"}, {" Falls", "Cascada"}, {" Peninsula", "Península"}};
        for (String[] x : sufijos) {
            if (n.endsWith(x[0]) && n.length() > x[0].length()) return x[1] + " " + n.substring(0, n.length() - x[0].length());
        }
        String[][] prefijos = {{"Mount ", "Monte "}, {"Lake ", "Lago "}, {"Isle of ", "Isla de "}, {"Port ", "Puerto "}, {"Saint ", "San "}};
        for (String[] x : prefijos) if (n.startsWith(x[0])) return x[1] + n.substring(x[0].length());
        return n;
    }

    /** País en español a partir de su código («CU» → «Cuba»). */
    private static String countryName(String cc) {
        if (cc == null || cc.length() != 2) return null;
        String n = new Locale("", cc.toUpperCase(Locale.US)).getDisplayCountry(ES);
        return n == null || n.length() == 0 || n.equalsIgnoreCase(cc) ? null : n;
    }

    /** Núcleo habitado más cercano (OpenStreetMap, Overpass) con nombre en letras latinas; null si no hay o no hay red. */
    private static String nearestPlace(double lat, double lon) {
        int[] radii = {20000, 60000};
        for (int r : radii) {
            try {
                String q = String.format(Locale.US,
                        "[out:json][timeout:8];node(around:%d,%.5f,%.5f)[place~\"^(city|town|village|hamlet|suburb)$\"];out 150;", r, lat, lon);
                String url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8");
                java.net.HttpURLConnection con = AlbumFetcher.open(url, null, 9000);
                InputStream in = con.getInputStream();
                String body;
                try {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    while (out.size() < 1024 * 1024 && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                    body = new String(out.toByteArray(), "UTF-8");
                } finally {
                    try { in.close(); } catch (Exception ignored) { }
                    con.disconnect();
                }
                org.json.JSONArray els = new JSONObject(body).getJSONArray("elements");
                String best = null;
                double bd = Double.MAX_VALUE, cl = Math.cos(Math.toRadians(lat));
                for (int i = 0; i < els.length(); i++) {
                    JSONObject e = els.getJSONObject(i);
                    JSONObject t = e.optJSONObject("tags");
                    if (t == null) continue;
                    String nm = firstLatin(t.optString("name:es", null), t.optString("name", null), t.optString("name:en", null));
                    if (nm == null) continue;
                    double dy = e.optDouble("lat") - lat, dx = (e.optDouble("lon") - lon) * cl;
                    double dd = dx * dx + dy * dy;
                    if (dd < bd) { bd = dd; best = nm; }
                }
                if (best != null) return best;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** true si el texto solo lleva letras latinas (sin japonés, árabe, cirílico…). */
    private static boolean latin(String s) {
        if (s == null || s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!Character.isLetter(ch)) continue;
            Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
            if (b != Character.UnicodeBlock.BASIC_LATIN && b != Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                    && b != Character.UnicodeBlock.LATIN_EXTENDED_A && b != Character.UnicodeBlock.LATIN_EXTENDED_B
                    && b != Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL) return false;
        }
        return true;
    }

    private static String firstLatin(String... s) {
        for (String x : s) if (x != null && x.length() > 0 && latin(x)) return x;
        return null;
    }

    static String formatDate(long ms) {
        if (ms <= 0) return null;
        return new SimpleDateFormat("d 'de' MMMM 'de' yyyy", ES).format(new java.util.Date(ms));
    }

    private static String first(String... s) {
        for (String x : s) if (x != null && x.length() > 0) return x;
        return null;
    }

    private static String join(String a, String b) {
        if (a == null) return b;
        if (b == null || b.equals(a)) return a;
        return a + ", " + b;
    }
}
