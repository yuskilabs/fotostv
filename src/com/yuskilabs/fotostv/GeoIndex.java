package com.yuskilabs.fotostv;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Índice de ubicaciones de todas las fotos: foto → coordenadas → nombre del lugar. Se rellena en segundo plano. */
class GeoIndex extends SQLiteOpenHelper {
    static final int NO_GPS = 2, GPS = 1;
    private static GeoIndex inst;

    static synchronized GeoIndex get(Context c) {
        if (inst == null) inst = new GeoIndex(c.getApplicationContext());
        return inst;
    }

    private GeoIndex(Context c) { super(c, "geo.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE photos(k TEXT PRIMARY KEY, lat REAL, lon REAL, st INTEGER, ts INTEGER, cell TEXT, place TEXT)");
        db.execSQL("CREATE INDEX photos_place ON photos(place)");
        db.execSQL("CREATE INDEX photos_cell ON photos(cell)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) { }

    /**
     * Tabla de cámaras (7.0, para las estadísticas; «» = se miró y no la tiene). Se crea al abrir, sin cambiar
     * la versión de la base: así una versión anterior de la app la sigue abriendo sin problemas.
     */
    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) db.execSQL("CREATE TABLE IF NOT EXISTS cams(k TEXT PRIMARY KEY, cam TEXT)");
    }

    /** «Apple iPhone 13» a partir de la marca y el modelo del EXIF (sin repetir la marca). */
    static String camName(String make, String model) {
        String mk = make == null ? "" : make.trim(), md = model == null ? "" : model.trim();
        if (md.length() == 0) return mk;
        if (mk.length() == 0 || md.toLowerCase(Locale.ROOT).startsWith(mk.toLowerCase(Locale.ROOT).split(" ")[0])) return md;
        return mk + " " + md;
    }

    void putCam(String k, String cam) {
        ContentValues v = new ContentValues();
        v.put("k", k);
        v.put("cam", cam == null ? "" : cam);
        getWritableDatabase().insertWithOnConflict("cams", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Clave → cámara de las fotos en las que ya se ha mirado («» = sin cámara en el EXIF). */
    HashMap<String, String> camMap() {
        HashMap<String, String> m = new HashMap<String, String>();
        Cursor q = getReadableDatabase().rawQuery("SELECT k, cam FROM cams", null);
        try { while (q.moveToNext()) m.put(q.getString(0), q.getString(1)); } finally { q.close(); }
        return m;
    }

    /** Clave → nombre de lugar de todas las fotos que ya lo tienen. */
    HashMap<String, String> placeMap() {
        HashMap<String, String> m = new HashMap<String, String>();
        Cursor q = getReadableDatabase().rawQuery("SELECT k, place FROM photos WHERE place IS NOT NULL", null);
        try { while (q.moveToNext()) m.put(q.getString(0), q.getString(1)); } finally { q.close(); }
        return m;
    }

    static String cell(double lat, double lon) { return String.format(Locale.US, "%.2f,%.2f", lat, lon); }

    /** Claves ya registradas (con GPS definitivo, o sin GPS mirado hace menos de una semana). */
    HashSet<String> known() {
        HashSet<String> s = new HashSet<String>();
        Cursor q = getReadableDatabase().rawQuery("SELECT k FROM photos WHERE st=? OR ts>?",
                new String[]{"" + GPS, "" + (System.currentTimeMillis() - 7L * 24 * 3600 * 1000)});
        try { while (q.moveToNext()) s.add(q.getString(0)); } finally { q.close(); }
        return s;
    }

    void put(String k, double lat, double lon, boolean gps) {
        ContentValues v = new ContentValues();
        v.put("k", k);
        v.put("st", gps ? GPS : NO_GPS);
        v.put("ts", System.currentTimeMillis());
        if (gps) { v.put("lat", lat); v.put("lon", lon); v.put("cell", cell(lat, lon)); }
        getWritableDatabase().insertWithOnConflict("photos", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Casilla (0,01°) pendiente de ponerle nombre: {cell, lat, lon}. */
    String[] nextCell(HashSet<String> skip) {
        Cursor q = getReadableDatabase().rawQuery(
                "SELECT cell, lat, lon FROM photos WHERE st=1 AND place IS NULL GROUP BY cell ORDER BY COUNT(*) DESC LIMIT 50", null);
        try {
            while (q.moveToNext()) if (!skip.contains(q.getString(0)))
                return new String[]{q.getString(0), q.getString(1), q.getString(2)};
        } finally { q.close(); }
        return null;
    }

    void setPlace(String cell, String place) {
        ContentValues v = new ContentValues();
        v.put("place", place);
        getWritableDatabase().update("photos", v, "cell=?", new String[]{cell});
    }

    int count(String where) {
        Cursor q = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM photos" + where, null);
        try { return q.moveToFirst() ? q.getInt(0) : 0; } finally { q.close(); }
    }

    /** Claves de las fotos de un lugar («Adeje, Tenerife») o, si region, de todo el sitio mayor («Tenerife»). */
    HashSet<String> keysAt(String want, boolean region) {
        HashSet<String> s = new HashSet<String>();
        Cursor q = region
                ? getReadableDatabase().rawQuery("SELECT k FROM photos WHERE place=? OR place LIKE ?", new String[]{want, "%, " + want})
                : getReadableDatabase().rawQuery("SELECT k FROM photos WHERE place=?", new String[]{want});
        try { while (q.moveToNext()) s.add(q.getString(0)); } finally { q.close(); }
        return s;
    }

    /** Todas las fotos de los álbumes (clave → foto), sin repetidas. */
    static HashMap<String, Item> allItems(Context c, List<Item> order) {
        HashMap<String, Item> m = new HashMap<String, Item>();
        for (Prefs.Album al : Prefs.albums(c)) {
            AlbumFetcher.AlbumData d = Prefs.albumCache(c, al.url);
            if (d == null) continue;
            for (Item x : d.items) {
                String k = x.key();
                if (!m.containsKey(k)) { m.put(k, x); if (order != null) order.add(x); }
            }
        }
        return m;
    }

    // ---- estado para la pantalla de ajustes ----
    static volatile int total, running;
    static boolean paused(Context c) { return Prefs.meta(c).getBoolean("geoPause", false); }
    static void setPaused(Context c, boolean p) {
        Prefs.meta(c).edit().putBoolean("geoPause", p).apply();
        if (!p) GeoService.start(c);
    }

    /** Solo lo pendiente: fotos sin ubicación (se vuelven a mirar) y zonas sin nombre. No toca lo que ya está bien. */
    static void recheckPending(Context c) {
        try { MetaFetcher.forgetMissingGps(c); } catch (Throwable ignored) { }
        get(c).getWritableDatabase().delete("photos", "st=?", new String[]{"" + NO_GPS});
        Prefs.meta(c).edit().putBoolean("geoPause", false).apply();
        GeoService.start(c);
    }

    /** Rehace todos los nombres de lugar (cuando cambian las reglas de nombres). */
    static void rebuildNames(Context c) {
        MetaFetcher.forgetPlaceNames(c);
        get(c).getWritableDatabase().execSQL("UPDATE photos SET place=NULL");
        Prefs.meta(c).edit().putBoolean("geoPause", false).apply();
        GeoService.start(c);
    }

    /** Vuelve a leer la ubicación y el nombre del lugar de estas fotos (y su zona). Llamar fuera del hilo de pantalla. */
    static void recheck(Context c, List<Item> items) {
        SQLiteDatabase db = get(c).getWritableDatabase();
        SharedPreferences.Editor e = Prefs.meta(c).edit();
        try { MetaFetcher.forgetMissingGps(c, items); } catch (Throwable ignored) { }
        for (Item it : items) {
            double[] g = MetaFetcher.cachedGps(c, it);
            if (g != null) {
                e.remove(String.format(Locale.US, "p8_%.2f,%.2f", g[0], g[1]));
                e.remove(String.format(Locale.US, "p8_%.2f,%.2f_t", g[0], g[1]));
                db.execSQL("UPDATE photos SET place=NULL WHERE cell=?", new Object[]{cell(g[0], g[1])});
            }
            e.remove("d1_" + it.key());
            db.delete("photos", "k=?", new String[]{it.key()});
        }
        e.commit();
        Prefs.meta(c).edit().putBoolean("geoPause", false).apply();
        GeoService.start(c);
    }

    /** Coordenadas ya leídas en formato compacto: «clave,lat,lon;clave,lat,lon;…» (sin nombres de lugar). */
    String exportGeo() {
        StringBuilder sb = new StringBuilder();
        Cursor q = getReadableDatabase().rawQuery("SELECT k, lat, lon FROM photos WHERE st=1", null);
        try {
            while (q.moveToNext()) {
                if (sb.length() > 0) sb.append(';');
                sb.append(q.getString(0)).append(',')
                  .append(String.format(Locale.US, "%.5f", q.getDouble(1))).append(',')
                  .append(String.format(Locale.US, "%.5f", q.getDouble(2)));
            }
        } finally { q.close(); }
        return sb.toString();
    }

    /** Nombres de lugar ya calculados, uno por celda: [[celda, nombre], ...]. */
    org.json.JSONArray exportPlaces() {
        org.json.JSONArray a = new org.json.JSONArray();
        Cursor q = getReadableDatabase().rawQuery("SELECT cell, place FROM photos WHERE place IS NOT NULL GROUP BY cell", null);
        try {
            while (q.moveToNext()) a.put(new org.json.JSONArray().put(q.getString(0)).put(q.getString(1)));
        } finally { q.close(); }
        return a;
    }

    /** Pone los nombres exportados a las fotos que aún no tienen. */
    void importPlaces(org.json.JSONArray a) {
        if (a == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONArray p = a.optJSONArray(i);
                if (p == null || p.length() < 2) continue;
                db.execSQL("UPDATE photos SET place=? WHERE cell=? AND place IS NULL", new Object[]{p.optString(1), p.optString(0)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /** Carga coordenadas exportadas; devuelve cuántas fotos nuevas se añadieron. */
    int importGeo(String data) {
        if (data == null || data.length() == 0) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int n = 0;
        db.beginTransaction();
        try {
            for (String e : data.split(";")) {
                String[] p = e.split(",");
                if (p.length != 3) continue;
                try {
                    double la = Double.parseDouble(p[1]), lo = Double.parseDouble(p[2]);
                    ContentValues v = new ContentValues();
                    v.put("k", p[0]);
                    v.put("st", GPS);
                    v.put("ts", System.currentTimeMillis());
                    v.put("lat", la);
                    v.put("lon", lo);
                    v.put("cell", cell(la, lo));
                    if (db.insertWithOnConflict("photos", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1) n++;
                } catch (NumberFormatException ignored) { }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return n;
    }

    /** Lugares con fotos: nombre → nº de fotos. */
    HashMap<String, Integer> placeCounts() {
        HashMap<String, Integer> m = new HashMap<String, Integer>();
        Cursor q = getReadableDatabase().rawQuery("SELECT place, COUNT(*) FROM photos WHERE place IS NOT NULL GROUP BY place", null);
        try { while (q.moveToNext()) m.put(q.getString(0), q.getInt(1)); } finally { q.close(); }
        return m;
    }

    static String status(Context c) {
        GeoIndex g = get(c);
        int done = g.count("");
        int tot = Math.max(total, done);
        int named = g.count(" WHERE place IS NOT NULL");
        int noName = g.count(" WHERE st=1 AND place IS NULL");
        if (paused(c)) return "Ubicaciones en pausa: " + done + " de " + tot + " fotos revisadas";
        if (done >= tot && noName == 0) return "Ubicaciones al día: " + named + " fotos con lugar";
        if (done >= tot) return "Ubicaciones: " + named + " fotos con lugar · " + noName + " sin nombre todavía";
        return "Ubicaciones: " + done + " de " + tot + " fotos revisadas" + (noName > 0 ? " · " + noName + " pendientes de nombre" : "");
    }

    /** Qué hace OK sobre el estado: «reanudar», «pausar» (solo si está trabajando de verdad), «reintentar nombres» o nada. */
    static String action(Context c) {
        if (paused(c)) return "reanudar";
        int done = get(c).count("");
        if (done < Math.max(total, done)) return "pausar";
        return get(c).count(" WHERE st=1 AND place IS NULL") > 0 ? "reintentar nombres" : "";
    }

    /** OK sobre el estado. */
    static void toggle(Context c) {
        String a = action(c);
        if ("reanudar".equals(a)) setPaused(c, false);
        else if ("pausar".equals(a)) setPaused(c, true);
        else if (a.startsWith("reintentar")) retryNames(c);
    }

    /** Vuelve a pedir el nombre de las zonas que se quedaron sin él (borra los intentos fallidos y despierta el servicio). */
    static void retryNames(Context c) {
        SharedPreferences.Editor e = Prefs.meta(c).edit();
        SharedPreferences m = Prefs.meta(c);
        for (String k : new ArrayList<String>(m.getAll().keySet())) if (k.startsWith("p8_") && k.endsWith("_t")) e.remove(k.substring(0, k.length() - 2)).remove(k);
        e.putBoolean("geoPause", false).commit();
        GeoService.start(c);
    }

    /** {lat, lon} de una foto ya leída; null si no se conoce. */
    double[] gpsOf(String k) {
        Cursor q = getReadableDatabase().rawQuery("SELECT lat, lon FROM photos WHERE k=? AND st=1", new String[]{k});
        try { return q.moveToFirst() ? new double[]{q.getDouble(0), q.getDouble(1)} : null; } finally { q.close(); }
    }

    /** Rectángulo {minLat, maxLat, minLon, maxLon} que cubre las fotos de un lugar (o región); null si no hay. */
    double[] bbox(String want, boolean region) {
        Cursor q = region
                ? getReadableDatabase().rawQuery("SELECT MIN(lat), MAX(lat), MIN(lon), MAX(lon), COUNT(*) FROM photos WHERE place=? OR place LIKE ?", new String[]{want, "%, " + want})
                : getReadableDatabase().rawQuery("SELECT MIN(lat), MAX(lat), MIN(lon), MAX(lon), COUNT(*) FROM photos WHERE place=?", new String[]{want});
        try {
            if (q.moveToFirst() && q.getInt(4) > 0) return new double[]{q.getDouble(0), q.getDouble(1), q.getDouble(2), q.getDouble(3)};
            return null;
        } finally { q.close(); }
    }

    /** Fotos con ubicación de un conjunto de claves: {k, lat, lon, place (o null), cell}. */
    List<String[]> rowsOf(java.util.Set<String> scope) {
        List<String[]> l = new ArrayList<String[]>();
        Cursor q = getReadableDatabase().rawQuery("SELECT k, lat, lon, place, cell FROM photos WHERE st=1", null);
        try {
            while (q.moveToNext()) if (scope.contains(q.getString(0)))
                l.add(new String[]{q.getString(0), q.getString(1), q.getString(2), q.getString(3), q.getString(4)});
        } finally { q.close(); }
        return l;
    }

    /** Puntos del mapa de calor: {lat, lon, nº de fotos} por casilla de 0,01°. */
    List<double[]> heat() {
        List<double[]> l = new ArrayList<double[]>();
        Cursor q = getReadableDatabase().rawQuery("SELECT AVG(lat), AVG(lon), COUNT(*) FROM photos WHERE st=1 GROUP BY cell", null);
        try { while (q.moveToNext()) l.add(new double[]{q.getDouble(0), q.getDouble(1), q.getInt(2)}); } finally { q.close(); }
        return l;
    }

    /** Claves de las fotos dentro de un rectángulo de coordenadas. */
    HashSet<String> keysIn(double minLat, double maxLat, double minLon, double maxLon) {
        HashSet<String> s = new HashSet<String>();
        Cursor q = getReadableDatabase().rawQuery("SELECT k FROM photos WHERE st=1 AND lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?",
                new String[]{"" + minLat, "" + maxLat, "" + minLon, "" + maxLon});
        try { while (q.moveToNext()) s.add(q.getString(0)); } finally { q.close(); }
        return s;
    }

    static List<Item> items(Context c, String want, boolean region) {
        return itemsOf(c, get(c).keysAt(want, region));
    }

    static List<Item> itemsOf(Context c, HashSet<String> ks) {
        List<Item> res = new ArrayList<Item>();
        if (ks.isEmpty()) return res;
        for (Prefs.Album al : Prefs.albums(c)) {
            AlbumFetcher.AlbumData d = Prefs.albumCache(c, al.url);
            if (d == null) continue;
            String title = al.name.length() > 0 ? al.name : d.title;
            for (Item x : d.items) if (ks.remove(x.key())) {
                x.album = title;
                if (x.akey.length() == 0) x.akey = d.key;
                res.add(x);
            }
        }
        return res;
    }
}
