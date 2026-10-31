package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Álbumes temporales de invitados. Viven aparte de los álbumes de la casa (no salen en el salvapantallas,
 * ni en Projectivy, ni en la copia de seguridad) y desaparecen solos a las 2 horas, sin dejar rastro.
 */
class Guest {
    static final long DURATION = 2L * 60 * 60 * 1000;
    static final long TOKEN_MS = 15L * 60 * 1000;
    private static final SecureRandom RND = new SecureRandom();

    static class G {
        String id, url, name;
        long added, expires;
        String title() { return name.length() > 0 ? name : "Álbum invitado"; }
        long left() { return expires - System.currentTimeMillis(); }
    }

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("guest", Context.MODE_PRIVATE);
    }

    private static String rnd(int bytes) {
        byte[] b = new byte[bytes];
        RND.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** Clave de la caché de un temporal: distinta de la de un álbum de la casa, aunque sea el mismo enlace. */
    private static String ck(String url) { return "guest|" + url; }

    // ---------------------------------------------------------------- enlace (QR) de corta duración
    static synchronized String newToken(Context c) {
        String t = rnd(8);
        sp(c).edit().putString("token", t).putLong("tokenExp", System.currentTimeMillis() + TOKEN_MS).commit();
        return t;
    }

    static boolean tokenOk(Context c, String t) {
        if (t == null || t.length() == 0) return false;
        SharedPreferences s = sp(c);
        return t.equals(s.getString("token", "")) && System.currentTimeMillis() < s.getLong("tokenExp", 0);
    }

    // ---------------------------------------------------------------- lista
    private static List<G> readAll(Context c) {
        List<G> l = new ArrayList<G>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                G g = new G();
                g.id = o.getString("id");
                g.url = o.getString("url");
                g.name = o.optString("name", "");
                g.added = o.optLong("added", 0);
                g.expires = o.optLong("expires", 0);
                l.add(g);
            }
        } catch (Exception ignored) { }
        return l;
    }

    private static void writeAll(Context c, List<G> l) {
        JSONArray a = new JSONArray();
        try {
            for (G g : l) a.put(new JSONObject().put("id", g.id).put("url", g.url).put("name", g.name)
                    .put("added", g.added).put("expires", g.expires));
        } catch (Exception ignored) { }
        sp(c).edit().putString("list", a.toString()).commit();
    }

    /** Los temporales que siguen vigentes (primero el que caduca antes). Antes borra los caducados. */
    static synchronized List<G> list(Context c) {
        purgeExpired(c);
        List<G> l = readAll(c);
        java.util.Collections.sort(l, new java.util.Comparator<G>() {
            public int compare(G a, G b) { return a.expires < b.expires ? -1 : (a.expires > b.expires ? 1 : 0); }
        });
        return l;
    }

    static synchronized G find(Context c, String id) {
        for (G g : readAll(c)) if (g.id.equals(id)) return g;
        return null;
    }

    static boolean any(Context c) { return !list(c).isEmpty(); }

    /** Guarda un álbum de invitado ya descargado. Si ya estaba, renueva sus 2 horas. */
    static synchronized G add(Context c, String url, String name, AlbumFetcher.AlbumData d) {
        Prefs.saveAlbumCache(c, ck(url), d);
        List<G> l = readAll(c);
        G g = null;
        for (G x : l) if (x.url.equals(url)) g = x;
        if (g == null) {
            g = new G();
            g.id = rnd(6);
            g.url = url;
            l.add(g);
        }
        g.name = name.length() > 0 ? name : d.title;
        g.added = System.currentTimeMillis();
        g.expires = g.added + DURATION;
        writeAll(c, l);
        map = null;
        return g;
    }

    /** Álbum completo (de la caché) de un temporal. */
    static AlbumFetcher.AlbumData data(Context c, G g) {
        return Prefs.albumCache(c, ck(g.url));
    }

    // ---------------------------------------------------------------- borrado total
    static synchronized void remove(Context c, String id) {
        List<G> l = readAll(c), keep = new ArrayList<G>();
        for (G g : l) {
            if (g.id.equals(id)) wipe(c, g); else keep.add(g);
        }
        writeAll(c, keep);
        map = null;
    }

    /** Quita todo lo caducado. Se llama al abrir la app, al volver a ella y al mirar la lista. */
    static synchronized void purgeExpired(Context c) {
        List<G> l = readAll(c), keep = new ArrayList<G>();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (G g : l) {
            if (g.expires <= now) { wipe(c, g); changed = true; } else keep.add(g);
        }
        if (changed) { writeAll(c, keep); map = null; }
        // restos sueltos (por ejemplo, tras un cierre a mitad de borrado)
        File[] gd = guestThumbRoot(c).listFiles();
        if (gd != null) {
            java.util.Set<String> ids = new java.util.HashSet<String>();
            for (G g : keep) ids.add(g.id);
            for (File f : gd) if (!ids.contains(f.getName())) deleteTree(f);
        }
        if (keep.isEmpty()) sp(c).edit().remove("token").remove("tokenExp").commit();
    }

    private static void wipe(Context c, G g) {
        AlbumFetcher.AlbumData d = null;
        try { d = Prefs.albumCache(c, ck(g.url)); } catch (Throwable ignored) { }
        // miniaturas (y fotos descargadas)
        deleteTree(new File(guestThumbRoot(c), g.id));
        try { Thumbs.get(c).dropMemory(); } catch (Throwable ignored) { }
        if (d != null) {
            // vídeos descargados de ese álbum + datos guardados de cada elemento (vídeo, GPS, detalles, lugar)
            File[] vs = new File(c.getCacheDir(), "videos").listFiles();
            SharedPreferences m = Prefs.meta(c);
            SharedPreferences.Editor ed = m.edit();
            java.util.Set<String> seen = Prefs.seen(c);
            boolean seenChanged = false;
            java.util.Set<String> keys = new java.util.HashSet<String>();
            for (Item it : d.items) {
                String k = it.key();
                keys.add(k);
                String x = m.getString("x_" + k, null);
                if (x != null) {
                    try {
                        String[] p = x.split(";");
                        ed.remove(String.format(java.util.Locale.US, "p_%.2f,%.2f", Double.parseDouble(p[0]), Double.parseDouble(p[1])));
                    } catch (Exception ignored) { }
                }
                try {
                    String dj = m.getString("d1_" + k, null);
                    if (dj != null) {
                        JSONObject o = new JSONObject(dj);
                        if (o.has("lat")) ed.remove(String.format(java.util.Locale.US, "p_%.2f,%.2f", o.getDouble("lat"), o.getDouble("lon")));
                    }
                } catch (Exception ignored) { }
                ed.remove("v2_" + k).remove("x_" + k).remove("d1_" + k);
                if (seen.remove(k)) seenChanged = true;
            }
            if (seenChanged) ed.putStringSet("seen", seen);
            if (m.getString("last", "").length() > 0 && keys.contains(m.getString("last", ""))) ed.remove("last");
            ed.commit();
            if (vs != null) for (File f : vs) {
                String n = f.getName();
                int dot = n.indexOf('.');
                if (keys.contains(dot > 0 ? n.substring(0, dot) : n)) f.delete();
            }
            if (Session.lastKey != null && keys.contains(Session.lastKey)) Session.lastKey = null;
        }
        Prefs.deleteAlbumCache(c, ck(g.url));
    }

    static void deleteTree(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File x : k) deleteTree(x);
        f.delete();
    }

    // ---------------------------------------------------------------- miniaturas aparte
    static File guestThumbRoot(Context c) {
        File d = new File(c.getCacheDir(), "thumbs_guest");
        d.mkdirs();
        return d;
    }

    private static volatile Map<String, String> map;   // dirección de la foto -> id del temporal

    /** id del temporal al que pertenece esa foto (null = es de un álbum normal). */
    static String idOfBase(Context c, String base) {
        Map<String, String> m = map;
        if (m == null) {
            synchronized (Guest.class) {
                m = map;
                if (m == null) {
                    m = new ConcurrentHashMap<String, String>();
                    for (G g : readAll(c)) {
                        AlbumFetcher.AlbumData d = Prefs.albumCache(c, ck(g.url));
                        if (d != null) for (Item it : d.items) m.put(it.url, g.id);
                    }
                    map = m;
                }
            }
        }
        return m.get(base);
    }

    /** Texto «1 h 45 min», «12 min»… */
    static String left(long ms) {
        long min = Math.max(1, (ms + 59999) / 60000);
        if (min >= 60) return (min / 60) + " h " + (min % 60 < 10 ? "0" : "") + (min % 60) + " min";
        return min + " min";
    }

    /** Marca a los elementos de un temporal para que la app los trate como «de invitado». */
    static boolean isGuestItem(Context c, Item it) {
        return idOfBase(c, it.url) != null;
    }
}
