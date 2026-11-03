package com.yuskilabs.fotostv;

import android.content.ContentValues;
import android.content.Context;
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
                "SELECT cell, lat, lon FROM photos WHERE st=1 AND place IS NULL GROUP BY cell LIMIT 50", null);
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

    /** Vuelve a mirar las fotos sin ubicación y arranca el servicio. */
    static void forceRecheck(Context c) {
        try { MetaFetcher.forgetMissingGps(c); } catch (Throwable ignored) { }
        get(c).getWritableDatabase().delete("photos", "st=?", new String[]{"" + NO_GPS});
        Prefs.meta(c).edit().putBoolean("geoPause", false).apply();
        GeoService.start(c);
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
        return "Ubicaciones: " + done + " de " + tot + " fotos revisadas" + (noName > 0 ? " · " + noName + " pendientes de nombre" : "");
    }

    static List<Item> items(Context c, String want, boolean region) {
        List<Item> res = new ArrayList<Item>();
        HashSet<String> ks = get(c).keysAt(want, region);
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
