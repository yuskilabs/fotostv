package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Funciones extra (versión 7.0). Todas vienen apagadas: con todo apagado la app se comporta como antes.
 * Se guardan en los ajustes del perfil (claves «fx_…») y viajan en la copia de seguridad.
 */
final class Extras {
    private Extras() { }

    // pase y salvapantallas
    static final String BIG_CLOCK = "fx_bigClock";     // reloj grande con la fecha
    static final String WEATHER = "fx_weather";        // tiempo junto al reloj
    static final String CITY = "fx_city";              // ciudad del tiempo (texto)
    static final String TODAY = "fx_today";            // recuerdos de hoy en el salvapantallas
    static final String NIGHT = "fx_night";            // franja de noche: off | 22-8 | 23-7 | 0-7
    static final String NIGHT_MODE = "fx_nightMode";   // dim (tenue) | black (pantalla negra)
    static final String PLACE = "fx_place";            // salvapantallas de un solo lugar ("" = todos)
    static final String PLACE_REGION = "fx_placeRegion";   // el lugar es una provincia/isla/país entero
    static final String TRIO3 = "fx_trio3";            // tres verticales juntas, de vez en cuando
    static final String CREDIT = "fx_credit";          // marca de agua con quién hizo la foto
    static final String GRID4 = "fx_grid4";            // cuatro fotos cercanas en el tiempo, en cuadrícula
    static final String COLLAGE = "fx_collage";        // dos fotos verticales juntas
    static final String KEN_BURNS = "fx_kenBurns";     // movimiento suave
    static final String KB_STYLE = "fx_kbStyle";       // mix (variado) | in (acercar) | out (alejar) | pan (desplazar)
    static final String KB_SPEED = "fx_kbSpeed";       // slow (lenta) | normal
    static final String DATE_FMT = "fx_dateFmt";       // formato de fecha: long (por defecto) o short
    static final String DATE_FULL = "fx_dateFull";     // fecha del pase con día de la semana y hora
    static final String TODAY_MIX = "fx_todayMix";     // recuerdos de hoy mezclados con el resto
    static final String KB_EDGE = "fx_kbEdge";         // bordes: hide (la foto va ampliada) | show (empieza entera y se ve el relleno)
    static final String BACKGROUND = "fx_bg";          // relleno de lo que la foto no llena: black | color | blur
    static final String CAPTIONS = "fx_captions";      // pies de foto
    static final String FAV_BOOST = "fx_favBoost";     // favoritas más a menudo (orden inteligente)
    static final String ALBUM_MUSIC = "fx_albumMusic"; // música propia de cada álbum
    static final String SOFT_DUCK = "fx_softDuck";     // con vídeo con sonido, la música baja en vez de callarse
    static final String TIMER = "fx_timer";            // temporizador del pase (minutos, 0 = no)
    // explorador
    static final String SEARCH = "fx_search";          // botón «Buscar»
    static final String VIRTUAL = "fx_virtual";        // álbumes virtuales (búsquedas guardadas)
    static final String DATE_JUMP = "fx_dateJump";     // botón «Fecha» en los mosaicos
    static final String TRIPS = "fx_trips";            // botón «Viajes»
    static final String MULTI_COVER = "fx_multiCover"; // varias portadas por álbum
    static final String NEW_BADGE = "fx_new";          // marcar fotos nuevas
    static final String DUPES = "fx_dupes";            // buscar fotos repetidas
    static final String STATS = "fx_stats";            // botón «Estadísticas» (con filtro por cámara)
    static final String REMOTE = "fx_remote";          // mando desde el móvil
    static final String RELOAD = "fx_reload";          // recarga automática de álbumes (horas, 0 = no)

    static SharedPreferences sp(Context c) { return Prefs.sp(c); }

    static boolean on(Context c, String key) { return sp(c).getBoolean(key, false); }

    static String str(Context c, String key, String def) { return sp(c).getString(key, def); }

    static int num(Context c, String key) { return sp(c).getInt(key, 0); }

    static String night(Context c) { return str(c, NIGHT, "off"); }

    static boolean nightBlack(Context c) { return "black".equals(str(c, NIGHT_MODE, "dim")); }

    static String background(Context c) { return str(c, BACKGROUND, "black"); }

    // ---- pies de foto (se guardan aunque la función se apague; solo dejan de verse)
    static String caption(Context c, String key) {
        if (!on(c, CAPTIONS)) return "";
        return sp(c).getString("cap_" + key, "");
    }

    static String rawCaption(Context c, String key) { return sp(c).getString("cap_" + key, ""); }

    static void setCaption(Context c, String key, String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() == 0) sp(c).edit().remove("cap_" + key).apply();
        else sp(c).edit().putString("cap_" + key, t).apply();
    }

    // ---- música propia de un álbum («» = la de Ajustes)
    static String albumMusic(Context c, String albumUrl) {
        if (albumUrl == null || !on(c, ALBUM_MUSIC)) return "";
        return sp(c).getString("amusic_" + albumUrl, "");
    }

    static void setAlbumMusic(Context c, String albumUrl, String radioUrl) {
        if (radioUrl == null || radioUrl.length() == 0) sp(c).edit().remove("amusic_" + albumUrl).apply();
        else sp(c).edit().putString("amusic_" + albumUrl, radioUrl).apply();
    }

    // ---- portadas: una o varias claves separadas por comas en «cover_<url>»
    static java.util.List<String> covers(Context c, String albumUrl) {
        java.util.List<String> l = new java.util.ArrayList<String>();
        String v = sp(c).getString("cover_" + albumUrl, null);
        if (v == null) return l;
        for (String k : v.split(",")) if (k.trim().length() > 0) l.add(k.trim());
        if (!on(c, MULTI_COVER) && l.size() > 1) l = l.subList(0, 1);   // sin la función, solo cuenta la primera
        return l;
    }

    /** Todas las portadas guardadas, también las que no cuentan con «Varias portadas» apagada. */
    static java.util.List<String> allCovers(Context c, String albumUrl) {
        java.util.List<String> l = new java.util.ArrayList<String>();
        String v = sp(c).getString("cover_" + albumUrl, null);
        if (v != null) for (String k : v.split(",")) if (k.trim().length() > 0) l.add(k.trim());
        return l;
    }

    static void setCovers(Context c, String albumUrl, java.util.List<String> keys) {
        if (keys == null || keys.isEmpty()) { sp(c).edit().remove("cover_" + albumUrl).commit(); return; }
        StringBuilder b = new StringBuilder();
        for (String k : keys) { if (b.length() > 0) b.append(','); b.append(k); }
        sp(c).edit().putString("cover_" + albumUrl, b.toString()).commit();
    }

    // ---- álbumes virtuales: [{"name":…, "q":…}]
    static java.util.List<String[]> virtualAlbums(Context c) {
        java.util.List<String[]> l = new java.util.ArrayList<String[]>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(sp(c).getString("valbums", "[]"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                String q = o.optString("q", "");
                if (q.length() > 0) l.add(new String[]{o.optString("name", q), q});
            }
        } catch (Exception ignored) { }
        return l;
    }

    static void saveVirtualAlbums(Context c, java.util.List<String[]> l) {
        org.json.JSONArray a = new org.json.JSONArray();
        try {
            for (String[] x : l) a.put(new org.json.JSONObject().put("name", x[0]).put("q", x[1]));
        } catch (Exception ignored) { }
        sp(c).edit().putString("valbums", a.toString()).commit();
    }

    /** ¿Es ahora de noche según la franja elegida? */
    static boolean isNight(Context c) {
        String w = night(c);
        if ("off".equals(w)) return false;
        return FxLogic.inWindow(w, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
    }
}
