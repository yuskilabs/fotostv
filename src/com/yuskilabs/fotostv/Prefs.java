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
    static int albumCols(Context c) { return sp(c).getInt("albumCols", 4); }
    static int photoCols(Context c) { return sp(c).getInt("photoCols", 6); }
    /** square | wide | full */
    static String photoShape(Context c) { return sp(c).getString("photoShape", "square"); }
    static int videoMB(Context c) { return sp(c).getInt("videoMB", smallStorage(c) ? 500 : 1500); }

    /** Minutos sin usar el mando antes de abrir el pase solo (0 = desactivado). Solo para equipos sin salvapantallas estándar. */
    static int idleMin(Context c) { return sp(c).getInt("idleMin", 0); }

    /** Orden de los álbumes en el explorador de la tele: manual | newest | oldest (por la fecha de captura de su última foto). */
    static String albumSort(Context c) { return sp(c).getString("albumSort", "manual"); }

    /** Columnas del mosaico de álbumes y de fotos del explorador, y formato de las miniaturas de fotos: square | wide | full. */
    static String photoFmt(Context c) { return sp(c).getString("photoFmt", "square"); }

    static int interval(Context c) { return sp(c).getInt("interval", 60); }
    static String fit(Context c) { return sp(c).getString("fit", "auto"); }
    /** random | norepeat | asc | desc */
    static String order(Context c) { return sp(c).getString("order", "random"); }
    /** all | selected */
    static String albumMode(Context c) { return sp(c).getString("albumMode", "all"); }
    static boolean resume(Context c) { return sp(c).getBoolean("resume", false); }
    /** Calidad de los vídeos: auto (media en Fire TV, que suele ir con wifi y menos potencia), alta o media. */
    static String vq(Context c) { return sp(c).getString("vq", "auto"); }
    static boolean videoSd(Context c) {
        String q = vq(c);
        if ("sd".equals(q)) return true;
        if ("hd".equals(q)) return false;
        return "Amazon".equalsIgnoreCase(android.os.Build.MANUFACTURER);
    }
    /** Minutos sin tocar el mando tras los que se cierra el pase automático (0 = nunca). */
    static int autoStopMin(Context c) { return sp(c).getInt("autoStopMin", 10); }
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
    static void unhide(Context c, String key) {
        java.util.Set<String> h = hidden(c);
        h.remove(key);
        sp(c).edit().putStringSet("hidden", h).apply();
    }

    // ---- favoritas ----
    static java.util.Set<String> favs(Context c) {
        return new java.util.HashSet<String>(sp(c).getStringSet("favs", new java.util.HashSet<String>()));
    }
    static boolean toggleFav(Context c, String key) {
        java.util.Set<String> f = favs(c);
        boolean now = f.add(key);
        if (!now) f.remove(key);
        sp(c).edit().putStringSet("favs", f).apply();
        return now;
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
    // ---- caché de álbumes: un archivo por álbum (antes, todo en un único texto gigante dentro de los ajustes,
    // que con decenas de álbumes y miles de fotos se releía entero por cada álbum y dejaba la app colgada)
    private static boolean migrated;

    private static java.io.File cacheDir(Context c) {
        java.io.File d = new java.io.File(c.getFilesDir(), "albumcache");
        d.mkdirs();
        return d;
    }

    private static java.io.File cacheFile(Context c, String url) {
        String h;
        try {
            byte[] b = java.security.MessageDigest.getInstance("SHA-1").digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            h = sb.toString();
        } catch (Exception e) {
            h = Integer.toHexString(url.hashCode());
        }
        return new java.io.File(cacheDir(c), h + ".txt");
    }

    private static void writeCache(Context c, String url, String title, String key, List<Item> items) throws java.io.IOException {
        java.io.File f = cacheFile(c, url);
        java.io.File tmp = new java.io.File(f.getPath() + ".tmp");
        java.io.BufferedWriter w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp), "UTF-8"), 1 << 16);
        try {
            try {
                w.write(new JSONObject().put("title", title).put("key", key).put("count", items.size()).toString());
            } catch (Exception e) { throw new java.io.IOException(e.getMessage()); }
            w.write("\n");
            for (Item it : items) {
                w.write(it.url); w.write('\t');
                w.write(Long.toString(it.ts)); w.write('\t');
                w.write(it.id == null ? "" : it.id); w.write('\n');
            }
        } finally {
            w.close();
        }
        f.delete();
        tmp.renameTo(f);
    }

    /** Pasa la caché antigua (un solo texto en los ajustes) al formato nuevo, una sola vez. */
    private static synchronized void migrate(Context c) {
        if (migrated) return;
        SharedPreferences s = sp(c);
        String old = s.getString("cache", null);
        if (old != null) {
            try {
                JSONObject all = new JSONObject(old);
                java.util.Iterator<String> keys = all.keys();
                while (keys.hasNext()) {
                    String url = keys.next();
                    JSONObject o = all.optJSONObject(url);
                    if (o == null) continue;
                    List<Item> list = new ArrayList<Item>();
                    String key = o.optString("key", "");
                    JSONArray items = o.optJSONArray("items");
                    if (items != null) for (int i = 0; i < items.length(); i++) {
                        JSONArray it = items.getJSONArray(i);
                        Item x = new Item(it.getString(0), it.optLong(1, 0));
                        x.id = it.optString(2, "");
                        list.add(x);
                    }
                    writeCache(c, url, o.optString("title", ""), key, list);
                }
            } catch (Throwable ignored) { }
            s.edit().remove("cache").commit();
        }
        migrated = true;
    }

    static void deleteAlbumCache(Context c, String url) {
        synchronized (Prefs.class) {
            java.io.File f = cacheFile(c, url);
            f.delete();
            new java.io.File(f.getPath() + ".tmp").delete();
        }
    }

    static void saveAlbumCache(Context c, String url, AlbumFetcher.AlbumData d) {
        try {
            migrate(c);
            if (d.partial) {
                // una descarga recortada (~300) nunca sustituye a una lista más completa ya guardada
                AlbumFetcher.AlbumData old = albumCache(c, url);
                if (old != null && old.items.size() > d.items.size()) return;
            }
            synchronized (Prefs.class) { writeCache(c, url, d.title, d.key, d.items); }
        } catch (Exception ignored) { }
    }

    /** Título, clave y número de elementos de un álbum, sin cargar sus fotos (lectura casi instantánea). */
    static String[] albumInfo(Context c, String url) {
        try {
            migrate(c);
            java.io.File f = cacheFile(c, url);
            if (!f.exists()) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            try {
                String line = r.readLine();
                if (line == null) return null;
                JSONObject o = new JSONObject(line);
                return new String[]{o.optString("title", ""), o.optString("key", ""), String.valueOf(o.optInt("count", 0))};
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static AlbumFetcher.AlbumData albumCache(Context c, String url) {
        try {
            migrate(c);
            java.io.File f = cacheFile(c, url);
            if (!f.exists()) return null;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"), 1 << 16);
            try {
                String line = r.readLine();
                if (line == null) return null;
                JSONObject o = new JSONObject(line);
                AlbumFetcher.AlbumData d = new AlbumFetcher.AlbumData();
                d.title = o.optString("title", "");
                d.key = o.optString("key", "");
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t", 3);
                    if (p.length < 2) continue;
                    long ts = 0;
                    try { ts = Long.parseLong(p[1]); } catch (Exception ignored) { }
                    Item x = new Item(p[0], ts);
                    x.id = p.length > 2 ? p[2] : "";
                    x.akey = d.key;
                    d.items.add(x);
                }
                return d;
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
