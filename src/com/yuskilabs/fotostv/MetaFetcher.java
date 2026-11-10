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
            if (!Double.isNaN(r[0])) return r;
            double[] kg = knownGps(c, it);          // el índice o la ficha ya la tienen aunque aquí se guardase «sin ubicación»
            if (kg != null) { r[0] = kg[0]; r[1] = kg[1]; storeGps(c, it, kg[0], kg[1], r[2]); return r; }
            if (System.currentTimeMillis() - checked < RECHECK_MS) return r;
        } else {
            double[] kg = knownGps(c, it);
            if (kg != null) { r[0] = kg[0]; r[1] = kg[1]; storeGps(c, it, kg[0], kg[1], 0); return r; }
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

    /** Coordenadas ya conocidas por la ficha (d1_) o por el índice de ubicaciones; null si no hay. */
    static double[] knownGps(Context c, Item it) {
        double[] g = cachedGps(c, it);
        if (g == null) { try { g = GeoIndex.get(c).gpsOf(it.key()); } catch (Throwable ignored) { } }
        return g;
    }

    /** Guarda las coordenadas de una foto en la caché rápida (la que usa el rótulo del pase). */
    static void storeGps(Context c, Item it, double lat, double lon, double date) {
        Prefs.meta(c).edit().putString("x_" + it.key(), lat + ";" + lon + ";" + date + ";" + System.currentTimeMillis()).apply();
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
            try { if (ex != null && Extras.on(c, Extras.STATS)) GeoIndex.get(c).putCam(it.key(), GeoIndex.camName(d.make, d.model)); } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
        return d;
    }

    /** Lectura rápida del GPS (solo ~160 KB de la foto). null = no se pudo decidir; {NaN,NaN} = sin GPS en el EXIF. */
    static double[] quickGps(Item it) { return quickGps(it, null); }

    /** Igual, y de paso apunta la cámara («Apple iPhone 13») en cam[0] si el EXIF la trae. */
    static double[] quickGps(Item it, String[] cam) {
        try {
            byte[] head = AlbumFetcher.get(it.url + "=d", 160 * 1024);
            if (head.length < 4 || (head[0] & 0xFF) != 0xFF || (head[1] & 0xFF) != 0xD8) return null;
            ExifInterface ex = (ExifInterface) ExifInterface.class
                    .getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(head));
            if (cam != null) cam[0] = GeoIndex.camName(tag(ex, "Make"), tag(ex, "Model"));
            float[] ll = new float[2];
            if (ex.getLatLong(ll) && !(ll[0] == 0f && ll[1] == 0f)) return new double[]{ll[0], ll[1]};
            return new double[]{Double.NaN, Double.NaN};
        } catch (Throwable t) { return null; }
    }

    /** Cámara («Apple iPhone 13») según el EXIF del principio de una foto; "" si no es JPEG o no la lleva. */
    static String camFromHead(byte[] head) {
        try {
            if (head.length < 4 || (head[0] & 0xFF) != 0xFF || (head[1] & 0xFF) != 0xD8) return "";
            ExifInterface ex = (ExifInterface) ExifInterface.class
                    .getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(head));
            return GeoIndex.camName(tag(ex, "Make"), tag(ex, "Model"));
        } catch (Throwable t) { return ""; }
    }

    /** {lat, lon} solo si ya se leyeron antes (sin red); null si no se sabe o no tiene GPS. */
    static double[] cachedGps(Context c, Item it) {
        SharedPreferences m = Prefs.meta(c);
        try {
            String x = m.getString("x_" + it.key(), null);
            if (x != null) {
                String[] p = x.split(";");
                double la = Double.parseDouble(p[0]), lo = Double.parseDouble(p[1]);
                if (!Double.isNaN(la) && !Double.isNaN(lo)) return new double[]{la, lo};
            }
            String d = m.getString("d1_" + it.key(), null);
            if (d != null) {
                JSONObject o = new JSONObject(d);
                double la = o.optDouble("lat", Double.NaN), lo = o.optDouble("lon", Double.NaN);
                if (!Double.isNaN(la) && !Double.isNaN(lo)) return new double[]{la, lo};
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Olvida todos los nombres de lugar guardados (se vuelven a pedir con las reglas actuales). */
    static void forgetPlaceNames(Context c) {
        SharedPreferences m = Prefs.meta(c);
        SharedPreferences.Editor e = m.edit();
        for (String k : new java.util.ArrayList<String>(m.getAll().keySet())) if (k.startsWith("p8_") || k.startsWith("p7_")) e.remove(k);
        e.commit();
    }

    /** Nombre del lugar solo si ya estaba guardado (sin red). */
    static String placeCached(Context c, double lat, double lon) {
        SharedPreferences m = Prefs.meta(c);
        String k = String.format(Locale.US, "p8_%.2f,%.2f", lat, lon);
        return m.contains(k) ? m.getString(k, null) : null;
    }

    /**
     * Nombre del lugar. En España: «Localidad, Provincia» (o solo la ciudad si es capital de su provincia).
     * Fuera de España: «Localidad, País» (en español y con letras latinas), sin condados ni comarcas; siempre con localidad.
     */
    static String place(Context c, double lat, double lon) {
        if (Double.isNaN(lat)) return null;
        SharedPreferences m = Prefs.meta(c);
        String k = String.format(Locale.US, "p8_%.2f,%.2f", lat, lon);
        if (m.contains(k)) {
            long t = m.getLong(k + "_t", 0);
            if (t == 0 || System.currentTimeMillis() - t < 24L * 60 * 60 * 1000) return m.getString(k, null);
        }
        String cc = null, town = null, region = null, rgEs = null, loc = null, sub = null, subAdm = null;
        try {
            if (Geocoder.isPresent()) {
                List<Address> l = new Geocoder(c, ES).getFromLocation(lat, lon, 5);
                if (l != null && !l.isEmpty()) {
                    for (Address x : l) {
                        if (cc == null && x.getCountryCode() != null && x.getCountryCode().length() > 0) cc = x.getCountryCode();
                        // nunca featureName: en la costa o el campo suele ser solo un número de portal o de carretera
                        if (loc == null) { String q = firstLatin(x.getLocality()); if (q != null && !distrito(q)) loc = q; }
                        if (sub == null) { String q = firstLatin(x.getSubLocality()); if (q != null && !distrito(q)) sub = q; }
                        if (subAdm == null) { String q = firstLatin(x.getSubAdminArea()); if (q != null && !distrito(q)) subAdm = q; }
                        if (region == null) region = firstLatin(x.getAdminArea());
                    }
                    Address a0 = l.get(0);
                    rgEs = first(a0.getSubAdminArea(), a0.getAdminArea());
                }
            }
        } catch (Exception ignored) { }
        boolean es = cc != null && "ES".equalsIgnoreCase(cc);
        // OpenStreetMap (detalle de localidad): país, provincia y localidad aunque el equipo no tenga Geocoder (Fire TV)
        String q14 = null, hamlet = null;
        {
            JSONObject a = osm(lat, lon, 14);
            if (a != null) {
                String ncc = a.optString("country_code", "");
                if (ncc.length() > 0) { cc = ncc; es = "es".equalsIgnoreCase(ncc); }
                q14 = firstLatin(a.optString("city", null), a.optString("town", null),
                        a.optString("village", null), a.optString("municipality", null));
                if (q14 != null && distrito(q14)) q14 = null;
                hamlet = firstLatin(a.optString("hamlet", null));
                if (es && rgEs == null) rgEs = first(a.optString("province", null), a.optString("state", null));
                if (region == null) region = firstLatin(a.optString("state", null));
            }
        }
        // la localidad (la que dice OpenStreetMap, no la del Geocoder: ese da el municipio, p. ej. Barbate para Zahara),
        // nunca la aldea: si solo hay aldea, el municipio al que pertenece
        town = q14;
        if (!es) {
            // fuera de España: la ciudad (no el barrio ni el distrito), con el nombre en castellano si OpenStreetMap lo tiene
            town = loc != null ? loc : q14;
            try { Thread.sleep(1100); } catch (InterruptedException ignored) { }
            JSONObject a10 = osm(lat, lon, 10);
            if (a10 != null) {
                String q = firstLatin(a10.optString("city", null), a10.optString("town", null), a10.optString("village", null),
                        a10.optString("municipality", null));
                if (q != null && !distrito(q)) town = q;
            }
        } else if (town == null) {
            try { Thread.sleep(1100); } catch (InterruptedException ignored) { }
            JSONObject a10 = osm(lat, lon, 10);
            if (a10 != null) {
                String q = firstLatin(a10.optString("city", null), a10.optString("town", null), a10.optString("village", null),
                        a10.optString("municipality", null));
                if (q != null && !distrito(q)) town = q;
            }
            if (town == null && loc != null && (hamlet == null || !hamlet.equalsIgnoreCase(loc))) town = loc;
        }
        // Zahara de los Atunes y Atlanterra (pedanía de Barbate): OpenStreetMap las da como Barbate
        if (town != null && town.equalsIgnoreCase("Barbate") && lat > 36.08 && lat < 36.165 && lon > -5.885 && lon < -5.78) town = "Zahara de los Atunes";
        if (town == null) town = nearestPlace(lat, lon);   // mar abierto, campo: el núcleo habitado más cercano
        if (town == null) town = sub != null ? sub : subAdm;
        boolean complete = town != null;
        String name;
        if (es) {
            String isla = isla(lat, lon);   // Baleares y Canarias: la isla, no la provincia
            name = join(town, isla != null ? isla : (rgEs != null ? rgEs : region));
        } else {
            String country = countryName(cc);
            name = join(castellano(town != null ? town : region), country);
        }
        if (name != null && name.length() == 0) name = null;
        if (name != null) {
            SharedPreferences.Editor ed = m.edit().putString(k, name);
            if (complete) ed.remove(k + "_t"); else ed.putLong(k + "_t", System.currentTimeMillis());
            ed.apply();
        }
        return name;
    }

    /** Isla de Baleares o Canarias (recuadros aproximados) o null. {sur, norte, oeste, este}. */
    private static final Object[][] ISLAS = {
        {"Mallorca", 39.25, 39.97, 2.30, 3.50}, {"Menorca", 39.80, 40.10, 3.78, 4.35},
        {"Ibiza", 38.82, 39.15, 1.15, 1.65}, {"Formentera", 38.62, 38.82, 1.30, 1.65},
        {"Tenerife", 28.00, 28.60, -16.95, -16.10}, {"Gran Canaria", 27.70, 28.20, -15.85, -15.35},
        {"Lanzarote", 28.80, 29.25, -13.95, -13.40}, {"Fuerteventura", 28.05, 28.75, -14.55, -13.80},
        {"La Palma", 28.40, 28.90, -18.05, -17.70}, {"La Gomera", 27.95, 28.25, -17.40, -17.10},
        {"El Hierro", 27.60, 27.90, -18.20, -17.85},
    };

    private static String isla(double lat, double lon) {
        for (Object[] i : ISLAS) {
            if (lat >= (Double) i[1] && lat <= (Double) i[2] && lon >= (Double) i[3] && lon <= (Double) i[4]) return (String) i[0];
        }
        return null;
    }

    /** Respuesta «address» de Nominatim (nivel de detalle zoom) en castellano o inglés; null si falla. */
    private static JSONObject osm(double lat, double lon, int zoom) {
        try {
            String url = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=json&zoom=%d&lat=%.5f&lon=%.5f&accept-language=", zoom, lat, lon)
                    + URLEncoder.encode("es,en", "UTF-8");
            return new JSONObject(new String(AlbumFetcher.get(url), "UTF-8")).getJSONObject("address");
        } catch (Exception e) { return null; }
    }

    /** Nombre de distrito, barrio o municipio-contenedor que no sirve como «ciudad». */
    private static boolean distrito(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.startsWith("city of ") || l.startsWith("borough of ") || l.startsWith("london borough") || l.startsWith("royal borough")
            || l.startsWith("kecamatan") || l.startsWith("kelurahan") || l.startsWith("distrito ") || l.startsWith("district ")
            || l.startsWith("arrondissement") || l.startsWith("quartier ") || l.startsWith("barrio ") || l.startsWith("ward ")
            || l.endsWith(" mh.") || l.endsWith(" mahallesi") || l.endsWith(" district") || l.endsWith(" borough")
            || l.endsWith(" ward") || l.endsWith(" county") || l.endsWith(" ilçesi") || l.endsWith(" ilcesi");
    }

    private static final String[][] EXONIMOS = {
        {"denizli", "Pamukkale"}, {"london", "Londres"}, {"greater london", "Londres"}, {"gran londres", "Londres"}, {"city of london", "Londres"}, {"city of westminster", "Londres"}, {"istanbul", "Estambul"}, {"jakarta", "Yakarta"}, {"kobenhavn", "Copenhague"}, {"copenhagen", "Copenhague"},
        {"roma", "Roma"}, {"wien", "Viena"}, {"vienna", "Viena"}, {"praha", "Praga"}, {"prague", "Praga"}, {"firenze", "Florencia"},
        {"florence", "Florencia"}, {"venezia", "Venecia"}, {"venice", "Venecia"}, {"napoli", "Nápoles"}, {"naples", "Nápoles"},
        {"milano", "Milán"}, {"milan", "Milán"}, {"torino", "Turín"}, {"turin", "Turín"}, {"genova", "Génova"}, {"genoa", "Génova"},
        {"munchen", "Múnich"}, {"munich", "Múnich"}, {"koln", "Colonia"}, {"cologne", "Colonia"}, {"nurnberg", "Núremberg"},
        {"nuremberg", "Núremberg"}, {"bruxelles", "Bruselas"}, {"brussel", "Bruselas"}, {"brussels", "Bruselas"}, {"antwerpen", "Amberes"},
        {"antwerp", "Amberes"}, {"athina", "Atenas"}, {"athens", "Atenas"}, {"moskva", "Moscú"}, {"moscow", "Moscú"},
        {"warszawa", "Varsovia"}, {"warsaw", "Varsovia"}, {"krakow", "Cracovia"}, {"beograd", "Belgrado"}, {"belgrade", "Belgrado"},
        {"bucuresti", "Bucarest"}, {"bucharest", "Bucarest"}, {"zurich", "Zúrich"}, {"geneve", "Ginebra"}, {"geneva", "Ginebra"},
        {"bern", "Berna"}, {"edinburgh", "Edimburgo"}, {"kyoto", "Kioto"}, {"tokyo", "Tokio"}, {"beijing", "Pekín"}, {"new york", "Nueva York"},
        {"new york city", "Nueva York"}, {"rio de janeiro", "Río de Janeiro"}, {"cairo", "El Cairo"}, {"al qahirah", "El Cairo"},
        {"marrakesh", "Marrakech"}, {"singapore", "Singapur"}, {"seoul", "Seúl"}, {"phnom penh", "Nom Pen"}, {"havana", "La Habana"},
        {"lisbon", "Lisboa"}, {"gothenburg", "Gotemburgo"}, {"goteborg", "Gotemburgo"}, {"brugge", "Brujas"}, {"bruges", "Brujas"},
        {"den haag", "La Haya"}, {"the hague", "La Haya"}, {"sankt peterburg", "San Petersburgo"}, {"saint petersburg", "San Petersburgo"},
        {"kyiv", "Kiev"}, {"kiev", "Kiev"}, {"tbilisi", "Tiflis"}, {"yerevan", "Ereván"}, {"baku", "Bakú"}, {"dubai", "Dubái"},
        {"abu dhabi", "Abu Dabi"}, {"doha", "Doha"}, {"muscat", "Mascate"}, {"kolkata", "Calcuta"}, {"mumbai", "Bombay"},
        {"ho chi minh city", "Ciudad Ho Chi Minh"}, {"yangon", "Rangún"}, {"colombo", "Colombo"}, {"kathmandu", "Katmandú"},
        {"hanoi", "Hanói"}, {"nairobi", "Nairobi"}, {"cape town", "Ciudad del Cabo"}, {"mexico city", "Ciudad de México"},
        {"naha", "Naha"}, {"osaka", "Osaka"}, {"vilnius", "Vilna"}, {"tallinn", "Tallín"}, {"riga", "Riga"}, {"helsingfors", "Helsinki"},
        {"stockholm", "Estocolmo"}, {"oslo", "Oslo"}, {"reykjavik", "Reikiavik"}, {"dublin", "Dublín"}, {"baile atha cliath", "Dublín"},
        {"valletta", "La Valeta"}, {"la valletta", "La Valeta"}, {"sevastopol", "Sebastopol"}, {"thessaloniki", "Salónica"}
    };

    private static String sinTildes(String n) {
        String d = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD);
        return d.replaceAll("\\p{M}", "").replace('ı', 'i').replace('ø', 'o').replace('ł', 'l').toLowerCase(Locale.ROOT).trim();
    }

    /** Palabras genéricas de lugar en inglés a castellano («Malcapuya Island» → «Isla Malcapuya»). */
    private static String castellano(String n) {
        if (n == null) return null;
        String sn = sinTildes(n);
        for (String[] x : EXONIMOS) if (x[0].equals(sn)) return x[1];
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
