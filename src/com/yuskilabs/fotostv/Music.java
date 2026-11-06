package com.yuskilabs.fotostv;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Música de fondo del pase: una emisora de internet o las canciones de una carpeta. Todo se llama desde el hilo principal. */
class Music {
    /** Nombre de emisora sin la cadena delante («Antenne Bayern - Relax (…)» → «Relax (…)»). */
    static String shortName(String n) {
        String[] pre = {"Antenne Bayern - ", "80s80s - ", "90s90s - ", "90s00s - "};
        for (String p : pre) if (n.startsWith(p)) return n.substring(p.length());
        return n;
    }

    /** {nombre, url}; si la url es null, es el título de una sección. */
    static final String[][] PRESETS = {
        {"RELAX, COFFEE, LOUNGE, CHILLOUT & BALADAS", null},
        {"Coffee Music", "https://stream.antenne.de/coffee/stream/mp3"},
        {"Relax Pop", "https://stream.antenne.de/relax/stream/mp3"},
        {"Lounge", "https://stream.antenne.de/lounge/stream/mp3"},
        {"Chillout Ambient", "https://stream.antenne.de/chillout/stream/mp3"},
        {"Chillout Melódico", "https://stream.antenne.de/chillout-antenne-bayern/stream/mp3"},
        {"Sobremesa 80s", "https://streams.80s80s.de/dinnerparty/mp3-192/streams.80s80s.de/"},
        {"Sobremesa 90s", "https://streams.90s90s.de/dinnerparty/mp3-192/streams.90s90s.de/"},
        {"Beach Club 90s", "https://streams.90s90s.de/chillout/mp3-192/streams.90s90s.de/"},
        {"Yacht Rock 80s", "https://streams.80s80s.de/yachtrock/mp3-192/streams.80s80s.de/"},
        {"Lovesongs Pop", "https://stream.antenne.de/lovesongs/stream/mp3"},
        {"Baladas Pop 80s", "https://streams.80s80s.de/love/mp3-192/streams.80s80s.de/"},
        {"Baladas Pop 90s", "https://streams.90s90s.de/lovesongs/mp3-192/streams.90s90s.de/"},
        {"Soul Ballads 80s", "https://streams.80s80s.de/soulballads/mp3-192/streams.80s80s.de/"},
        {"R&B Ballads 90s", "https://streams.90s90s.de/rnbballads/mp3-192/streams.90s90s.de/"},
        {"Power Ballads 80s", "https://streams.80s80s.de/romanticrock/mp3-192/streams.80s80s.de/"},
        {"POP COMERCIAL ACTUAL, TOP 40 & NOVEDADES", null},
        {"Top 40", "https://stream.antenne.de/top-40/stream/mp3"},
        {"Novedades Pop", "https://stream.antenne.de/fresh/stream/mp3"},
        {"Pop XXL", "https://stream.antenne.de/pop-xxl/stream/mp3"},
        {"Solo Música", "https://stream.antenne.de/nur-die-musik/stream/mp3"},
        {"Happy Hits", "https://stream.antenne.de/happy-hits/stream/mp3"},
        {"Top 1000", "https://stream.antenne.de/top-1000/stream/mp3"},
        {"ÉXITOS GENERALES & DÉCADAS 2000 Y 2010", null},
        {"Greatest Hits", "https://stream.antenne.de/greatest-hits/stream/mp3"},
        {"Oldies 70-80-90", "https://stream.antenne.de/oldie-antenne/stream/mp3"},
        {"Hits 2010s", "https://stream.antenne.de/2010er-hits/stream/mp3"},
        {"Hits 2000s", "https://stream.antenne.de/2000er-hits/stream/mp3"},
        {"Millennium 1999-2000", "https://streams.90s90s.de/90s00s/mp3-192/play.mp3"},
        {"AÑOS 90: POP, EURODANCE & ÉXITOS URBANOS", null},
        {"Pop & Hits 90s", "https://streams.90s90s.de/pop/mp3-192/play.mp3"},
        {"Números uno 90s", "https://stream.antenne.de/90er-hits/stream/mp3"},
        {"Boybands 90s", "https://streams.90s90s.de/boygroup/mp3-192/streams.90s90s.de/"},
        {"Girl Power 90s", "https://streams.90s90s.de/girlpower/mp3-192/streams.90s90s.de/"},
        {"Eurodance Clásico 90s", "https://stream.antenne.de/antenne-bayern-90er-eurodance/stream/mp3"},
        {"Eurodance Hits 90s", "https://streams.90s90s.de/eurodance/mp3-192/streams.90s90s.de/"},
        {"Soul & R&B 90s", "https://streams.90s90s.de/rnb/mp3-192/streams.90s90s.de/"},
        {"Hip-Hop & Rap 90s", "https://streams.90s90s.de/hiphop/mp3-192/streams.90s90s.de/"},
        {"Reggae Pop 90s", "https://streams.90s90s.de/reggae/mp3-192/streams.90s90s.de/"},
        {"AÑOS 90: DANCE, CLUB, HOUSE, TECHNO & RAVE", null},
        {"Ibiza 90s", "https://streams.90s90s.de/ibiza/mp3-192/streams.90s90s.de/"},
        {"House 90s", "https://streams.90s90s.de/house/mp3-192/streams.90s90s.de/"},
        {"Dance 90s", "https://streams.90s90s.de/danceradio/mp3-192/play.mp3"},
        {"Club Hits 90s", "https://streams.90s90s.de/club/mp3-192/streams.90s90s.de/"},
        {"Sesiones DJ 90s", "https://streams.90s90s.de/inthemix/mp3-192/streams.90s90s.de/"},
        {"Fiesta 90s", "https://stream.antenne.de/antenne-bayern-90er-party/stream/mp3"},
        {"Techno 90s", "https://streams.90s90s.de/techno/mp3-192/streams.90s90s.de/"},
        {"Techno Essentials Tom Wax", "https://streams.90s90s.de/tomwax/mp3-192/streams.90s90s.de/"},
        {"Loveparade", "https://streams.90s90s.de/loveparade/mp3-192/streams.90s90s.de/"},
        {"Mayday", "https://streams.90s90s.de/mayday/mp3-192/streams.90s90s.de/"},
        {"Rave 90s", "https://streams.90s90s.de/RAVE/mp3-192/streams.90s90s.de/"},
        {"Trance 90s", "https://streams.90s90s.de/trance/mp3-192/streams.90s90s.de/"},
        {"AÑOS 80: POP, DISCO, FUNK & ARTISTAS", null},
        {"Radio 80s", "https://streams.80s80s.de/web/mp3-192/streams.80s80s.de/"},
        {"Clásicos 80s", "https://stream.antenne.de/80er-kulthits/stream/mp3"},
        {"Pop 80s", "https://streams.80s80s.de/pop/mp3-192/streams.80s80s.de/"},
        {"Michael Jackson", "https://streams.80s80s.de/mj/mp3-192/streams.80s80s.de/"},
        {"Prince", "https://streams.80s80s.de/100/mp3-192/streams.80s80s.de/"},
        {"Queen", "https://streams.80s80s.de/queen/mp3-192/streams.80s80s.de/"},
        {"David Bowie", "https://streams.80s80s.de/bowie/mp3-192/streams.80s80s.de/"},
        {"Disco Hits 80s", "https://stream.antenne.de/antenne-bayern-80er-disco-hits/stream/mp3"},
        {"Disco Pop 80s", "https://streams.80s80s.de/disco/mp3-192/streams.80s80s.de/"},
        {"Italo Disco", "https://streams.80s80s.de/italohits/mp3-192/streams.80s80s.de/"},
        {"Italo Disco DJ", "https://streams.80s80s.de/italodiscomix/mp3-192/streams.80s80s.de/"},
        {"Funk & Soul 80s", "https://streams.80s80s.de/soul/mp3-192/streams.80s80s.de/"},
        {"Freestyle Miami 80s", "https://streams.80s80s.de/freestyle/mp3-192/streams.80s80s.de/"},
        {"Dance 80s", "https://streams.80s80s.de/dance/mp3-192/streams.80s80s.de/"},
        {"Breakdance 80s", "https://streams.80s80s.de/breakdance/mp3-192/streams.80s80s.de/"},
        {"Hip-Hop Old School 80s", "https://streams.80s80s.de/hiphop/mp3-192/streams.80s80s.de/"},
        {"Maxi-singles 80s", "https://streams.80s80s.de/maxis/mp3-192/streams.80s80s.de/"},
        {"Fiesta Hits 80s", "https://stream.antenne.de/antenne-bayern-80er-party/stream/mp3"},
        {"Party Mix 80s", "https://streams.80s80s.de/party/mp3-192/streams.80s80s.de/"},
        {"Sesiones DJ 80s", "https://streams.80s80s.de/inthemix/mp3-192/streams.80s80s.de/"},
        {"Neo 80s", "https://streams.80s80s.de/neo/mp3-192/streams.80s80s.de/"},
        {"Reggae Pop 80s", "https://streams.80s80s.de/reggae/mp3-192/streams.80s80s.de/"},
        {"AÑOS 80: SYNTH, NEW WAVE & ALTERNATIVO", null},
        {"Synthpop 80s", "https://streams.80s80s.de/synth/mp3-192/streams.80s80s.de/"},
        {"New Wave 80s", "https://streams.80s80s.de/wave/mp3-192/streams.80s80s.de/"},
        {"Dark Wave 80s", "https://streams.80s80s.de/darkwave/mp3-192/streams.80s80s.de/"},
        {"EBM e Industrial 80s", "https://streams.80s80s.de/ebm/mp3-192/streams.80s80s.de/"},
        {"Alternativo 80s", "https://streams.80s80s.de/alternative/mp3-192/streams.80s80s.de/"},
        {"Conciertos 80s", "https://streams.80s80s.de/live/mp3-192/streams.80s80s.de/"},
        {"Acid House y Techno 80s", "https://streams.80s80s.de/techno/mp3-192/streams.80s80s.de/"},
        {"Videojuegos 80s", "https://streams.80s80s.de/games/mp3-192/streams.80s80s.de/"},
        {"Festival WGT", "https://streams.80s80s.de/wgt/mp3-192/streams.80s80s.de/"},
        {"ROCK, GUITARRAS & AÑOS 70", null},
        {"Números uno 70s", "https://stream.antenne.de/70er-hits/stream/mp3"},
        {"Rock 70s", "https://stream.antenne.de/antenne-bayern-70er-rock/stream/mp3"},
        {"Classic Rock", "https://stream.antenne.de/classic-rock-live/stream/mp3"},
        {"Rock Moderno", "https://stream.antenne.de/rockantenne/stream/mp3"},
        {"Rock Alternativo", "https://stream.antenne.de/rockantenne-bayern/stream/mp3"},
        {"Himnos Rock 80s", "https://stream.antenne.de/antenne-bayern-80er-rock/stream/mp3"},
        {"Rock Clásico 80s", "https://streams.80s80s.de/rock/mp3-192/streams.80s80s.de/"},
        {"Britpop 90s", "https://streams.90s90s.de/britpop/mp3-192/streams.90s90s.de/"},
        {"Grunge 90s", "https://streams.90s90s.de/grunge/mp3-192/streams.90s90s.de/"},
        {"Rock Internacional 90s", "https://stream.antenne.de/antenne-bayern-90er-rock/stream/mp3"},
        {"Himnos Rock 90s", "https://streams.90s90s.de/rock/mp3-192/streams.90s90s.de/"},
        {"OTROS GÉNEROS MUSICALES INTERNACIONALES", null},
        {"Country", "https://stream.antenne.de/country-antenne/stream/mp3"},
        {"Black Music", "https://stream.antenne.de/black-beatz/stream/mp3"},
        {"Dance XXL", "https://stream.antenne.de/dance-xxl/stream/mp3"},
        {"Sesiones Pop y Dance", "https://stream.antenne.de/in-the-mix/stream/mp3"},
        {"Pop Italiano", "https://stream.antenne.de/antenne-bayern-italo-hits/stream/mp3"},
        {"Entreno", "https://stream.antenne.de/workout-hits/stream/mp3"},
        {"ESTACIONALES, VERANO & NAVIDAD INTERNACIONAL", null},
        {"Fiesta Pop", "https://stream.antenne.de/party-hits/stream/mp3"},
        {"Verano Hits", "https://stream.antenne.de/sommer-hits/stream/mp3"},
        {"Verano 80s", "https://streams.80s80s.de/summer/mp3-192/streams.80s80s.de/"},
        {"Verano 90s", "https://streams.90s90s.de/90s90s-sommerhits/mp3-192/streams.90s90s.de/"},
        {"Primavera Hits", "https://stream.antenne.de/fruehlings-hits/stream/mp3"},
        {"Otoño Hits", "https://stream.antenne.de/herbst-hits/stream/mp3"},
        {"Barbacoa", "https://stream.antenne.de/event/stream/mp3"},
        {"Navidad Pop", "https://stream.antenne.de/weihnachts-hits/stream/mp3"},
        {"Navidad Clásicos", "https://stream.antenne.de/oldie-antenne-event/stream/mp3"},
        {"Navidad 80s", "https://streams.80s80s.de/xmas/mp3-192/streams.80s80s.de/"},
        {"Navidad 90s", "https://streams.90s90s.de/christmas/mp3-192/streams.90s90s.de/"},
        {"Navidad Rock", "https://stream.antenne.de/xmas-rock/stream/mp3"},
    };

    private static final Handler h = new Handler(Looper.getMainLooper());
    private static Context app;
    private static MediaPlayer mp;
    private static boolean active, ducked;
    private static int retries, gen;
    private static List<File> files;
    private static int idx;

    private static final String[] EXT = {".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".flac", ".wav"};

    /** ¿Hay música configurada para este uso (dream = salvapantallas / vista previa)? */
    /** Fuente real: si la música está desactivada pero se pidió «Pase con música», usa la emisora o la carpeta que haya. */
    static String mode(Context c) {
        String m = Prefs.musicMode(c);
        if ("off".equals(m) && Session.music == 1 && Session.playerOn) {
            if (Prefs.musicRadio(c).length() > 0) return "radio";
            if (Prefs.musicFolder(c).length() > 0) return "folder";
        }
        return m;
    }

    static boolean enabled(Context c, boolean dream) {
        String m = mode(c);
        if ("off".equals(m)) return false;
        if (dream && !Prefs.musicDream(c)) return false;
        if ("radio".equals(m)) return Prefs.musicRadio(c).length() > 0;
        return Prefs.musicFolder(c).length() > 0;
    }

    static void start(Context c) {
        if (active) return;
        app = c.getApplicationContext();
        active = true;
        retries = 0;
        gen++;
        if ("folder".equals(mode(app))) scanThenPlay(); else next();
    }

    static void stop() {
        active = false;
        gen++;
        h.removeCallbacksAndMessages(null);
        release();
        files = null;
    }

    /** Silencia la música mientras suena el audio de un vídeo. */
    static void duck(boolean on) {
        ducked = on;
        applyVol();
    }

    private static void applyVol() {
        MediaPlayer m = mp;
        if (m == null || app == null) return;
        float v = ducked ? 0f : (float) Math.pow(Prefs.musicVol(app) / 100.0, 2);   // curva suave: a medio control suena bajito
        try { m.setVolume(v, v); } catch (Exception ignored) { }
    }

    private static void release() {
        MediaPlayer m = mp;
        mp = null;
        if (m != null) {
            try { m.stop(); } catch (Exception ignored) { }
            try { m.release(); } catch (Exception ignored) { }
        }
    }

    private static void scanThenPlay() {
        final int g = gen;
        final String path = Prefs.musicFolder(app);
        new Thread(new Runnable() {
            public void run() {
                final List<File> found = new ArrayList<File>();
                scan(new File(path), 0, found);
                Collections.shuffle(found);
                h.post(new Runnable() {
                    public void run() {
                        if (g != gen || !active) return;
                        if (found.isEmpty()) { active = false; return; }   // sin canciones o sin permiso: silencio
                        files = found;
                        idx = 0;
                        next();
                    }
                });
            }
        }, "music-scan").start();
    }

    private static void scan(File d, int depth, List<File> out) {
        if (depth > 6 || out.size() >= 3000) return;
        File[] l = d.listFiles();
        if (l == null) return;
        for (File f : l) {
            if (out.size() >= 3000) return;
            if (f.isDirectory()) { if (!f.getName().startsWith(".")) scan(f, depth + 1, out); }
            else {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.startsWith(".")) continue;
                for (String e : EXT) if (n.endsWith(e)) { out.add(f); break; }
            }
        }
    }

    private static final Runnable nextRun = new Runnable() { public void run() { next(); } };

    private static void retry(boolean quick) {
        if (!active) return;
        h.removeCallbacks(nextRun);
        h.postDelayed(nextRun, quick ? 300 : Math.min(30000, 3000L * (++retries)));
    }

    private static void next() {
        if (!active || app == null) return;
        release();
        final int g = gen;
        boolean radio = "radio".equals(mode(app));
        try {
            final MediaPlayer m = new MediaPlayer();
            mp = m;
            m.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if (radio) {
                String url = Prefs.musicRadio(app);
                if (url.length() == 0) { active = false; return; }
                m.setDataSource(url);
            } else {
                if (files == null || files.isEmpty()) { active = false; return; }
                if (idx >= files.size()) { idx = 0; Collections.shuffle(files); }
                m.setDataSource(files.get(idx++).getPath());
            }
            m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer p) {
                    if (g != gen || p != mp) return;
                    retries = 0;
                    applyVol();
                    p.start();
                }
            });
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer p) {
                    if (g != gen || p != mp) return;
                    retry(!"radio".equals(mode(app)));   // en carpeta, la siguiente canción; en radio, reconectar
                }
            });
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer p, int what, int extra) {
                    if (g != gen || p != mp) return true;
                    retry("folder".equals(mode(app)));   // canción que no se lee: se salta; emisora caída: se reintenta
                    return true;
                }
            });
            m.prepareAsync();
        } catch (Exception e) {
            retry("folder".equals(mode(app)));
        }
    }

    /** Carpetas donde probablemente haya música (para sugerirlas en la web de ajustes). */
    static List<String> suggestFolders() {
        List<String> out = new ArrayList<String>();
        List<File> roots = new ArrayList<File>();
        roots.add(android.os.Environment.getExternalStorageDirectory());
        File[] st = new File("/storage").listFiles();
        if (st != null) for (File f : st) if (!f.getName().equals("self") && !f.getName().equals("emulated")) roots.add(f);
        for (File r : roots) {
            if (r == null || !r.exists()) continue;
            String[] sub = {"Music", "Musica", "Música", "music"};
            boolean any = false;
            for (String s : sub) {
                File d = new File(r, s);
                if (d.isDirectory()) { out.add(d.getPath()); any = true; }
            }
            if (!any && r.isDirectory()) out.add(r.getPath());
        }
        return out;
    }
}
