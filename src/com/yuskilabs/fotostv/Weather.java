package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/** Tiempo actual de la ciudad elegida (Open-Meteo, sin clave). Todo se llama fuera del hilo de pantalla. */
final class Weather {
    private Weather() { }
    private static final long FRESH_MS = 30L * 60 * 1000;

    /** Olvida la ciudad y el último dato (al cambiar de ciudad). */
    static void forget(Context c) {
        Prefs.meta(c).edit().remove("wx_city").remove("wx_lat").remove("wx_lon").remove("wx_name")
                .remove("wx_txt").remove("wx_t").apply();
    }

    /** Busca la ciudad y guarda sus coordenadas. Devuelve «Málaga, Andalucía» o null si no la encuentra. */
    static String locate(Context c, String city) {
        try {
            String u = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&format=json&name="
                    + java.net.URLEncoder.encode(city, "UTF-8");
            org.json.JSONObject o = new org.json.JSONObject(new String(AlbumFetcher.get(u, 256 * 1024), "UTF-8"));
            org.json.JSONArray r = o.optJSONArray("results");
            if (r == null || r.length() == 0) return null;
            org.json.JSONObject p = r.getJSONObject(0);
            String name = p.optString("name", city);
            String adm = p.optString("admin1", "");
            if (adm.length() > 0 && !adm.equals(name)) name = name + ", " + adm;
            Prefs.meta(c).edit().putString("wx_city", city).putFloat("wx_lat", (float) p.getDouble("latitude"))
                    .putFloat("wx_lon", (float) p.getDouble("longitude")).putString("wx_name", name)
                    .remove("wx_txt").remove("wx_t").apply();
            return name;
        } catch (Throwable t) {
            return null;
        }
    }

    /** «☀ 18°» de la ciudad elegida; usa el último dato si es de hace menos de media hora. "" si no hay. */
    static String now(Context c) {
        String city = Extras.str(c, Extras.CITY, "");
        if (city.length() == 0) return "";
        SharedPreferences m = Prefs.meta(c);
        if (city.equals(m.getString("wx_city", "")) && System.currentTimeMillis() - m.getLong("wx_t", 0) < FRESH_MS)
            return m.getString("wx_txt", "");
        if (!city.equals(m.getString("wx_city", "")) && locate(c, city) == null) return m.getString("wx_txt", "");
        try {
            String u = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                    m.getFloat("wx_lat", 0f), m.getFloat("wx_lon", 0f));
            org.json.JSONObject o = new org.json.JSONObject(new String(AlbumFetcher.get(u, 256 * 1024), "UTF-8"));
            org.json.JSONObject cur = o.getJSONObject("current");
            long t = Math.round(cur.getDouble("temperature_2m"));
            int code = cur.optInt("weather_code", -1);
            String txt = FxLogic.weatherSymbol(code) + "  " + t + "°";
            m.edit().putString("wx_txt", txt).putLong("wx_t", System.currentTimeMillis()).apply();
            return txt;
        } catch (Throwable e) {
            return m.getString("wx_txt", "");   // sin conexión: el último dato que hubiera
        }
    }
}
