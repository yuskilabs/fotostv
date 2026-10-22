package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

class Prefs {
    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }
    static SharedPreferences meta(Context c) {
        return c.getSharedPreferences("meta", Context.MODE_PRIVATE);
    }

    /** En dispositivos con poco almacenamiento (p. ej. Fire Stick de 8 GB) el límite por defecto es menor. */
    private static boolean smallStorage(Context c) {
        try {
            android.os.StatFs st = new android.os.StatFs(c.getCacheDir().getPath());
            return st.getTotalBytes() < 12L * 1024 * 1024 * 1024;
        } catch (Throwable t) {
            return false;
        }
    }
    static int thumbMB(Context c) { return sp(c).getInt("thumbMB", smallStorage(c) ? 150 : 500); }
    static int videoMB(Context c) { return sp(c).getInt("videoMB", smallStorage(c) ? 500 : 1500); }

    /** Minutos sin usar el mando antes de abrir el pase solo (0 = desactivado). Solo para equipos sin salvapantallas estándar. */
    static int idleMin(Context c) { return sp(c).getInt("idleMin", 0); }

    static int interval(Context c) { return sp(c).getInt("interval", 60); }
    static String fit(Context c) { return sp(c).getString("fit", "auto"); }
    /** random | norepeat | asc | desc */
    static String order(Context c) { return sp(c).getString("order", "random"); }
    /** all | selected */
    static String albumMode(Context c) { return sp(c).getString("albumMode", "all"); }
    static boolean resume(Context c) { return sp(c).getBoolean("resume", false); }
    static boolean video(Context c) { return sp(c).getBoolean("video", true); }
    static boolean sound(Context c) { return sp(c).getBoolean("sound", false); }
    static boolean clock(Context c) { return sp(c).getBoolean("clock", true); }
    static boolean showAlbum(Context c) { return sp(c).getBoolean("showAlbum", true); }
    static boolean showDate(Context c) { return sp(c).getBoolean("showDate", true); }
    static boolean showPlace(Context c) { return sp(c).getBoolean("showPlace", true); }

    static class Album {
        String url, name;
        boolean show;
        Album(String url, String name, boolean show) { this.url = url; this.name = name; this.show = show; }
    }

    static List<Album> albums(Context c) {
        List<Album> l = new ArrayList<Album>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("albums", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                l.add(new Album(o.getString("url"), o.optString("name", ""), o.optBoolean("show", true)));
            }
        } catch (Exception ignored) { }
        String old = sp(c).getString("url", "");   // versión 1
        if (l.isEmpty() && old.length() > 0) l.add(new Album(old, "", true));
        return l;
    }

    static void saveAlbums(Context c, List<Album> l) {
        JSONArray a = new JSONArray();
        try {
            for (Album x : l) a.put(new JSONObject().put("url", x.url).put("name", x.name).put("show", x.show));
        } catch (Exception ignored) { }
        sp(c).edit().putString("albums", a.toString()).remove("url").apply();
    }

    /** Álbumes que hay que mostrar según el modo elegido. */
    static List<Album> activeAlbums(Context c) {
        boolean all = "all".equals(albumMode(c));
        List<Album> out = new ArrayList<Album>();
        for (Album a : albums(c)) if (all || a.show) out.add(a);
        return out;
    }

    // ---- fotos ocultas ----
    static java.util.Set<String> hidden(Context c) {
        return new java.util.HashSet<String>(sp(c).getStringSet("hidden", new java.util.HashSet<String>()));
    }
    static void hide(Context c, String key) {
        java.util.Set<String> h = hidden(c);
        h.add(key);
        sp(c).edit().putStringSet("hidden", h).apply();
    }
    static void clearHidden(Context c) { sp(c).edit().remove("hidden").apply(); }

    // ---- fotos ya vistas en la vuelta actual (modo aleatorio) ----
    static java.util.Set<String> seen(Context c) {
        return new java.util.HashSet<String>(meta(c).getStringSet("seen", new java.util.HashSet<String>()));
    }
    static void saveSeen(Context c, java.util.Set<String> s) {
        meta(c).edit().putStringSet("seen", new java.util.HashSet<String>(s)).apply();
    }
    static String lastShown(Context c) { return meta(c).getString("last", ""); }
    static void setLastShown(Context c, String key) { meta(c).edit().putString("last", key).apply(); }

    // ---- caché de contenido de cada álbum ----
    static void saveAlbumCache(Context c, String url, AlbumFetcher.AlbumData d) {
        try {
            JSONObject all = new JSONObject(sp(c).getString("cache", "{}"));
            JSONArray items = new JSONArray();
            for (Item it : d.items) items.put(new JSONArray().put(it.url).put(it.ts).put(it.id));
            all.put(url, new JSONObject().put("title", d.title).put("key", d.key).put("items", items));
            sp(c).edit().putString("cache", all.toString()).apply();
        } catch (Exception ignored) { }
    }

    static AlbumFetcher.AlbumData albumCache(Context c, String url) {
        try {
            JSONObject o = new JSONObject(sp(c).getString("cache", "{}")).optJSONObject(url);
            if (o == null) return null;
            AlbumFetcher.AlbumData d = new AlbumFetcher.AlbumData();
            d.title = o.optString("title", "");
            d.key = o.optString("key", "");
            JSONArray items = o.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONArray it = items.getJSONArray(i);
                Item x = new Item(it.getString(0), it.optLong(1, 0));
                x.id = it.optString(2, "");
                x.akey = d.key;
                d.items.add(x);
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }
}
