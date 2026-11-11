package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

class Prefs {
    /** Ajustes del perfil en uso: «Familia» (id 0) usa los de siempre; cada perfil que se añade tiene los suyos, vacíos al principio. */
    static SharedPreferences sp(Context c) {
        int p = profile(c);
        return c.getSharedPreferences(p == 0 ? "cfg" : "cfgx_" + p, Context.MODE_PRIVATE);
    }

    /** Ajustes del aparato (los de «Familia»). */
    static SharedPreferences base(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    // ---- perfiles (función extra): la lista y el perfil en uso se guardan en «meta», fuera de los propios perfiles ----
    static boolean profilesOn(Context c) { return meta(c).getBoolean("fx_profiles", false); }

    private static String profKey;
    private static List<String[]> profCache = new ArrayList<String[]>();

    /** Perfiles: {id, nombre}. El primero, «Familia» (id 0), siempre existe. */
    static synchronized List<String[]> profiles(Context c) {
        String j = meta(c).getString("prof_list", "[]");
        if (!j.equals(profKey)) {
            List<String[]> l = new ArrayList<String[]>();
            l.add(new String[]{"0", "Familia"});
            try {
                JSONArray a = new JSONArray(j);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    l.add(new String[]{String.valueOf(o.getInt("id")), o.getString("name")});
                }
            } catch (Exception ignored) { }
            profCache = l;
            profKey = j;
        }
        return profCache;
    }

    private static void saveProfiles(Context c, List<String[]> l) {
        JSONArray a = new JSONArray();
        try {
            for (int i = 1; i < l.size(); i++) a.put(new JSONObject().put("id", Integer.parseInt(l.get(i)[0])).put("name", l.get(i)[1]));
        } catch (Exception ignored) { }
        meta(c).edit().putString("prof_list", a.toString()).commit();
    }

    private static final ThreadLocal<Integer> forced = new ThreadLocal<Integer>();

    /** Solo para este hilo: trabaja con los datos de otro perfil (copia de seguridad) sin cambiar el que se está usando. */
    static void forceProfile(Integer id) { if (id == null) forced.remove(); else forced.set(id); }

    /** Id del perfil en uso: 0 = Familia (siempre, si los perfiles están desactivados). */
    static int profile(Context c) {
        Integer f = forced.get();
        if (f != null) return f;
        SharedPreferences m = meta(c);
        if (!m.getBoolean("fx_profiles", false)) return 0;
        int id = m.getInt("profileId", 0);
        if (id == 0) return 0;
        for (String[] p : profiles(c)) if (p[0].equals(String.valueOf(id))) return id;
        return 0;
    }

    /** Posición del perfil en la lista (para elegir su icono). */
    static int profileIndex(Context c, int id) {
        List<String[]> l = profiles(c);
        for (int i = 0; i < l.size(); i++) if (l.get(i)[0].equals(String.valueOf(id))) return i;
        return 0;
    }

    static String profileName(Context c, int id) { return profiles(c).get(profileIndex(c, id))[1]; }

    static void setProfile(Context c, int id) { meta(c).edit().putInt("profileId", id).commit(); }

    /** Perfil nuevo y vacío: sin álbumes y con sus ajustes de fábrica. Devuelve su id. */
    static int addProfile(Context c, String name) {
        List<String[]> l = new ArrayList<String[]>(profiles(c));
        int id = meta(c).getInt("prof_next", 1);
        meta(c).edit().putInt("prof_next", id + 1).commit();
        l.add(new String[]{String.valueOf(id), name});
        saveProfiles(c, l);
        return id;
    }

    static void renameProfile(Context c, int id, String name) {
        if (id == 0) return;
        List<String[]> l = new ArrayList<String[]>(profiles(c));
        for (String[] p : l) if (p[0].equals(String.valueOf(id))) p[1] = name;
        saveProfiles(c, l);
    }

    /** Borra el perfil con todos sus ajustes y álbumes. Si estaba en uso, se vuelve a «Familia». */
    static void deleteProfile(Context c, int id) {
        if (id == 0) return;
        List<String[]> l = new ArrayList<String[]>(profiles(c));
        for (int i = 1; i < l.size(); i++) if (l.get(i)[0].equals(String.valueOf(id))) l.remove(i--);
        saveProfiles(c, l);
        c.getSharedPreferences("cfgx_" + id, Context.MODE_PRIVATE).edit().clear().commit();
        clearProfileLook(c, id);
        if (meta(c).getInt("profileId", 0) == id) setProfile(c, 0);
    }

    // ---- icono del perfil: uno de la lista (posición) o una foto propia (archivo pimg_<id>.png) ----
    /** Posición del icono elegido en la lista de iconos, o -1 si no se ha elegido (se usa el de por defecto). */
    static int profileIconIdx(Context c, int id) { return meta(c).getInt("picon_" + id, -1); }

    static java.io.File profileImageFile(Context c, int id) {
        java.io.File f = new java.io.File(c.getFilesDir(), "pimg_" + id + ".png");
        return f.exists() ? f : null;
    }

    static void setProfileIcon(Context c, int id, int idx) {
        meta(c).edit().putInt("picon_" + id, idx).commit();
        java.io.File f = profileImageFile(c, id);
        if (f != null) f.delete();
    }

    static void clearProfileLook(Context c, int id) {
        meta(c).edit().remove("picon_" + id).commit();
        java.io.File f = profileImageFile(c, id);
        if (f != null) f.delete();
    }

    /** Guarda una foto como icono del perfil: cuadrada, de 160 px y con las esquinas redondeadas. */
    static boolean setProfileImage(Context c, int id, android.graphics.Bitmap src) {
        try {
            int s = Math.min(src.getWidth(), src.getHeight());
            android.graphics.Bitmap sq = android.graphics.Bitmap.createBitmap(src, (src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
            android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(160, 160, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(out);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            cv.drawRoundRect(new android.graphics.RectF(0, 0, 160, 160), 24, 24, p);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            cv.drawBitmap(sq, null, new android.graphics.Rect(0, 0, 160, 160), p);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(new java.io.File(c.getFilesDir(), "pimg_" + id + ".png"));
            try { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fo); } finally { fo.close(); }
            meta(c).edit().putInt("picon_" + id, -1).commit();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Para la copia de seguridad: la lista de perfiles con su nombre e icono, y los iconos propios en base64. */
    static org.json.JSONObject exportProfiles(Context c) throws org.json.JSONException {
        org.json.JSONObject pr = new org.json.JSONObject();
        pr.put("on", true).put("next", meta(c).getInt("prof_next", 1));
        JSONArray l = new JSONArray();
        for (String[] p : profiles(c)) {
            int id = Integer.parseInt(p[0]);
            JSONObject o = new JSONObject().put("id", id).put("name", p[1]).put("icon", profileIconIdx(c, id));
            java.io.File f = profileImageFile(c, id);
            if (f != null) {
                try {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    java.io.FileInputStream fi = new java.io.FileInputStream(f);
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = fi.read(b)) > 0) bo.write(b, 0, n);
                    fi.close();
                    o.put("img", android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP));
                } catch (Exception ignored) { }
            }
            l.put(o);
        }
        pr.put("list", l);
        return pr;
    }

    /** Pone la lista de perfiles de la copia (activa los perfiles). Los perfiles que ya no están se borran con sus datos. */
    static void importProfiles(Context c, org.json.JSONObject pr) {
        List<String[]> keep = new ArrayList<String[]>();
        keep.add(new String[]{"0", "Familia"});
        JSONArray l = pr.optJSONArray("list");
        java.util.Set<String> ids = new java.util.HashSet<String>();
        ids.add("0");
        if (l != null) for (int i = 0; i < l.length(); i++) {
            JSONObject o = l.optJSONObject(i);
            if (o == null) continue;
            int id = o.optInt("id", 0);
            if (id != 0) { keep.add(new String[]{String.valueOf(id), o.optString("name", "Perfil")}); ids.add(String.valueOf(id)); }
            int ic = o.optInt("icon", -1);
            String img = o.optString("img", "");
            if (img.length() > 0) {
                try {
                    byte[] b = android.util.Base64.decode(img, android.util.Base64.DEFAULT);
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(new java.io.File(c.getFilesDir(), "pimg_" + id + ".png"));
                    try { fo.write(b); } finally { fo.close(); }
                    meta(c).edit().putInt("picon_" + id, -1).commit();
                } catch (Exception ignored) { }
            } else {
                java.io.File f = profileImageFile(c, id);
                if (f != null) f.delete();
                if (ic >= 0) meta(c).edit().putInt("picon_" + id, ic).commit(); else meta(c).edit().remove("picon_" + id).commit();
            }
        }
        for (String[] p : new ArrayList<String[]>(profiles(c))) {
            if (!ids.contains(p[0])) {
                int id = Integer.parseInt(p[0]);
                c.getSharedPreferences("cfgx_" + id, Context.MODE_PRIVATE).edit().clear().commit();
                clearProfileLook(c, id);
            }
        }
        saveProfiles(c, keep);
        meta(c).edit().putBoolean("fx_profiles", true).putInt("prof_next", Math.max(meta(c).getInt("prof_next", 1), pr.optInt("next", 1))).commit();
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
    /** Música del pase: off | radio | folder. */
    static String musicMode(Context c) { return sp(c).getString("musicMode", "off"); }
    static String musicRadio(Context c) { return sp(c).getString("musicRadio", ""); }
    static String musicFolder(Context c) { return sp(c).getString("musicFolder", ""); }
    static int musicVol(Context c) { return sp(c).getInt("musicVol", 40); }
    static boolean musicDream(Context c) { return sp(c).getBoolean("musicDream", false); }
    static boolean clock(Context c) { return sp(c).getBoolean("clock", true); }
    static boolean showAlbum(Context c) { return sp(c).getBoolean("showAlbum", true); }
    static boolean showDate(Context c) { return sp(c).getBoolean("showDate", true); }
    static boolean showPlace(Context c) { return sp(c).getBoolean("showPlace", true); }

    /** PIN de 4 cifras de la web de ajustes (se crea la primera vez). */
    /** PIN nuevo: se cambia cada vez que arranca la app (los móviles que entraron antes vuelven a pedirlo). */
    static void renewWebPin(Context c) {
        for (String[] p : profiles(c))
            c.getSharedPreferences(p[0].equals("0") ? "cfg" : "cfgx_" + p[0], Context.MODE_PRIVATE).edit()
                .putString("webPin", String.format("%04d", new java.security.SecureRandom().nextInt(10000))).commit();
    }

    /** PIN de la web del perfil en uso (cada perfil tiene el suyo). */
    static String webPin(Context c) {
        String p = sp(c).getString("webPin", "");
        if (p.length() != 4) {
            p = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
            sp(c).edit().putString("webPin", p).commit();
        }
        return p;
    }

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

    /** Oculta o muestra de golpe varias fotos. */
    static void hideAll(Context c, java.util.Collection<String> keys, boolean hide) {
        java.util.Set<String> h = hidden(c);
        if (hide) h.addAll(keys); else h.removeAll(keys);
        sp(c).edit().putStringSet("hidden", h).apply();
    }

    /** Añade o quita de favoritas varias fotos de golpe. */
    static void favAll(Context c, java.util.Collection<String> keys, boolean add) {
        java.util.Set<String> f = favs(c);
        if (add) f.addAll(keys); else f.removeAll(keys);
        sp(c).edit().putStringSet("favs", f).apply();
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
                w.write(it.id == null ? "" : it.id); w.write('\t');
                w.write(it.tz == Item.NO_TZ ? "" : Long.toString(it.tz)); w.write('\n');
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
                    String[] p = line.split("\t", 4);
                    if (p.length < 2) continue;
                    long ts = 0;
                    try { ts = Long.parseLong(p[1]); } catch (Exception ignored) { }
                    Item x = new Item(p[0], ts);
                    x.id = p.length > 2 ? p[2] : "";
                    if (p.length > 3 && p[3].length() > 0) { try { x.tz = Long.parseLong(p[3]); } catch (Exception ignored) { } }
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
