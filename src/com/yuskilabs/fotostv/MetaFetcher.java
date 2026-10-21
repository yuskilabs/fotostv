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

    /** Devuelve {lat, lon, fecha_ms}; lat/lon NaN si no hay GPS, fecha 0 si no hay. */
    static double[] exif(Context c, Item it) {
        SharedPreferences m = Prefs.meta(c);
        String k = "x_" + it.key();
        String cached = m.getString(k, null);
        if (cached != null) {
            String[] p = cached.split(";");
            return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])};
        }
        double[] r = new double[]{Double.NaN, Double.NaN, 0};
        try {
            byte[] head = AlbumFetcher.get(it.url + "=d", 512 * 1024);
            ExifInterface ex = (ExifInterface) ExifInterface.class
                    .getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(head));
            float[] ll = new float[2];
            if (ex.getLatLong(ll) && !(ll[0] == 0f && ll[1] == 0f)) { r[0] = ll[0]; r[1] = ll[1]; }
            String dt = ex.getAttribute("DateTimeOriginal");
            if (dt == null) dt = ex.getAttribute("DateTime");
            if (dt != null) {
                try { r[2] = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(dt).getTime(); }
                catch (Exception ignored) { }
            }
            m.edit().putString(k, r[0] + ";" + r[1] + ";" + r[2]).apply();
        } catch (Throwable ignored) { }
        return r;
    }

    static String place(Context c, double lat, double lon) {
        if (Double.isNaN(lat)) return null;
        SharedPreferences m = Prefs.meta(c);
        String k = String.format(Locale.US, "p_%.2f,%.2f", lat, lon);
        if (m.contains(k)) return m.getString(k, null);
        String name = null;
        try {
            if (Geocoder.isPresent()) {
                List<Address> l = new Geocoder(c, ES).getFromLocation(lat, lon, 1);
                if (l != null && !l.isEmpty()) {
                    Address a = l.get(0);
                    String town = first(a.getLocality(), a.getSubLocality(), a.getFeatureName());
                    String region = first(a.getSubAdminArea(), a.getAdminArea(), a.getCountryName());
                    name = join(town, region);
                }
            }
        } catch (Exception ignored) { }
        if (name == null) {
            try {
                String url = String.format(Locale.US,
                        "https://nominatim.openstreetmap.org/reverse?format=json&zoom=12&lat=%.5f&lon=%.5f&accept-language=",
                        lat, lon) + URLEncoder.encode("es", "UTF-8");
                JSONObject a = new JSONObject(new String(AlbumFetcher.get(url), "UTF-8")).getJSONObject("address");
                String town = first(a.optString("city", null), a.optString("town", null),
                        a.optString("village", null), a.optString("municipality", null));
                String region = first(a.optString("province", null), a.optString("county", null),
                        a.optString("state", null), a.optString("country", null));
                name = join(town, region);
            } catch (Exception ignored) { }
        }
        if (name != null) m.edit().putString(k, name).apply();
        return name;
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
