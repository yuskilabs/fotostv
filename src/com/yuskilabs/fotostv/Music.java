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
    /** {nombre, url}; si la url es null, es el título de una sección. */
    static final String[][] PRESETS = {
        {"RELAX, COFFEE, LOUNGE, CHILLOUT & BALADAS", null},
        {"Antenne Bayern - Coffee Music (Música suave de cafetería)", "https://stream.antenne.de/coffee/stream/mp3"},
        {"Antenne Bayern - Relax (Pop suave y tempos lentos)", "https://stream.antenne.de/relax/stream/mp3"},
        {"Antenne Bayern - Lounge (Sonido lounge y downtempo)", "https://stream.antenne.de/lounge/stream/mp3"},
        {"Antenne Bayern - Chillout Antenne (Ambient y relax continuo)", "https://stream.antenne.de/chillout/stream/mp3"},
        {"Antenne Bayern - Chillout Antenne Bayern (Ambient chillout melódico)", "https://stream.antenne.de/chillout-antenne-bayern/stream/mp3"},
        {"80s80s - Dinnerparty (Música relajada y sobremesa)", "https://streams.80s80s.de/dinnerparty/mp3-192/play.mp3"},
        {"90s90s - Dinnerparty (Música relajante y sobremesa)", "https://streams.90s90s.de/dinnerparty/mp3-192/play.mp3"},
        {"90s90s - Chill Out (Ambient, Lounge y Beach Club)", "https://streams.90s90s.de/chillout/mp3-192/play.mp3"},
        {"80s80s - Yacht Rock (West Coast y Soft Pop californiano)", "https://streams.80s80s.de/yachtrock/mp3-192/play.mp3"},
        {"Antenne Bayern - Lovesongs (Grandes baladas románticas)", "https://stream.antenne.de/lovesongs/stream/mp3"},
        {"80s80s - Love (Baladas Pop románticas de los 80)", "https://streams.80s80s.de/love/mp3-192/play.mp3"},
        {"90s90s - Lovesongs (Baladas Pop y Boygroups de los 90)", "https://streams.90s90s.de/lovesongs/mp3-192/play.mp3"},
        {"80s80s - Soul Ballads (Baladas Soul y R&B clásico)", "https://streams.80s80s.de/soulballads/mp3-192/play.mp3"},
        {"90s90s - RnB Ballads (Slow Jams y baladas R&B)", "https://streams.90s90s.de/rnbballads/mp3-192/play.mp3"},
        {"80s80s - Romantic Rock (Power Ballads y baladas Rock)", "https://streams.80s80s.de/romanticrock/mp3-192/play.mp3"},
        {"POP COMERCIAL ACTUAL, TOP 40 & NOVEDADES", null},
        {"Antenne Bayern - Top 40 (Éxitos comerciales actuales)", "https://stream.antenne.de/top-40/stream/mp3"},
        {"Antenne Bayern - Fresh (Novedades de pop internacional)", "https://stream.antenne.de/fresh/stream/mp3"},
        {"Antenne Bayern - Pop XXL (Grandes éxitos pop)", "https://stream.antenne.de/pop-xxl/stream/mp3"},
        {"Antenne Bayern - Nur die Musik (Solo música sin interrupciones)", "https://stream.antenne.de/nur-die-musik/stream/mp3"},
        {"Antenne Bayern - Happy Hits (Pop alegre y feel-good)", "https://stream.antenne.de/happy-hits/stream/mp3"},
        {"Antenne Bayern - Top 1000 (Las 1000 mejores canciones pop)", "https://stream.antenne.de/top-1000/stream/mp3"},
        {"Antenne Bayern - Live (Emisión general en directo)", "https://stream.antenne.de/antenne/stream/mp3"},
        {"ÉXITOS GENERALES & DÉCADAS 2000 Y 2010", null},
        {"Antenne Bayern - Greatest Hits (Clásicos pop internacionales)", "https://stream.antenne.de/greatest-hits/stream/mp3"},
        {"Antenne Bayern - Oldie Antenne Live (Clásicos 70s, 80s y 90s)", "https://stream.antenne.de/oldie-antenne/stream/mp3"},
        {"Antenne Bayern - 2010er Hits (Éxitos pop de la década 2010)", "https://stream.antenne.de/2010er-hits/stream/mp3"},
        {"Antenne Bayern - 2000er Hits (Pop comercial de los años 2000)", "https://stream.antenne.de/2000er-hits/stream/mp3"},
        {"90s00s - Millennium Radio (Sonido cambio de milenio 1999-2000)", "https://streams.90s90s.de/millennium/mp3-192/play.mp3"},
        {"AÑOS 90: POP, EURODANCE & ÉXITOS URBANOS", null},
        {"90s90s - Radio (Grandes éxitos de los 90)", "https://streams.90s90s.de/90s90s/mp3-192/play.mp3"},
        {"Antenne Bayern - 90er Hits (Grandes números uno de los 90)", "https://stream.antenne.de/90er-hits/stream/mp3"},
        {"90s90s - Pop (Pop comercial internacional noventero)", "https://streams.90s90s.de/pop/mp3-192/play.mp3"},
        {"90s90s - Boygroups (Backstreet Boys, Take That, Boyzone)", "https://streams.90s90s.de/boygroups/mp3-192/play.mp3"},
        {"90s90s - Girlpower (Spice Girls, TLC, Destiny's Child)", "https://streams.90s90s.de/girlpower/mp3-192/play.mp3"},
        {"Antenne Bayern - 90er Eurodance (Eurodance clásico de los 90)", "https://stream.antenne.de/antenne-bayern-90er-eurodance/stream/mp3"},
        {"90s90s - Eurodance (Snap!, 2 Unlimited, Corona, Masterboy)", "https://streams.90s90s.de/eurodance/mp3-192/play.mp3"},
        {"90s90s - Soul & RnB (Hip-Hop Soul y R&B de los 90)", "https://streams.90s90s.de/rnb/mp3-192/play.mp3"},
        {"90s90s - HipHop & Rap (Éxitos de la era dorada del rap)", "https://streams.90s90s.de/hiphop/mp3-192/play.mp3"},
        {"90s90s - Reggae (Reggae Pop noventero soleado)", "https://streams.90s90s.de/reggae/mp3-192/play.mp3"},
        {"AÑOS 90: DANCE, CLUB, HOUSE, TECHNO & RAVE", null},
        {"90s90s - Ibiza (House, Trance y clásicos de discoteca)", "https://streams.90s90s.de/ibiza/mp3-192/play.mp3"},
        {"90s90s - House (House noventero vocal y underground)", "https://streams.90s90s.de/house/mp3-192/play.mp3"},
        {"90s90s - Dance Radio (Dance internacional continuo)", "https://streams.90s90s.de/dance/mp3-192/play.mp3"},
        {"90s90s - Clubhits (Temas de club noventeros)", "https://streams.90s90s.de/club/mp3-192/play.mp3"},
        {"90s90s - In the Mix (Sesiones DJ de grandes éxitos de los 90)", "https://streams.90s90s.de/inthemix/mp3-192/play.mp3"},
        {"Antenne Bayern - 90er Party (Fiesta y temas bailables de los 90)", "https://stream.antenne.de/antenne-bayern-90er-party/stream/mp3"},
        {"90s90s - Techno (El sonido original de los 90)", "https://streams.90s90s.de/techno/mp3-192/play.mp3"},
        {"90s90s - Techno Essentials by Tom Wax (Selección techno de autor)", "https://streams.90s90s.de/tomwax/mp3-192/play.mp3"},
        {"90s90s - Loveparade (Himnos de la mítica Loveparade)", "https://streams.90s90s.de/loveparade/mp3-192/play.mp3"},
        {"90s90s - Mayday (Sonido de los legendarios festivales Mayday)", "https://streams.90s90s.de/mayday/mp3-192/play.mp3"},
        {"90s90s - Rave (Rave clásico y ritmos rápidos)", "https://streams.90s90s.de/rave/mp3-192/play.mp3"},
        {"90s90s - Trance (Clásicos del sonido Trance de los 90)", "https://streams.90s90s.de/trance/mp3-192/play.mp3"},
        {"AÑOS 80: POP, DISCO, FUNK & ARTISTAS", null},
        {"80s80s - Radio (Grandes himnos pop de los 80)", "https://streams.80s80s.de/80s80s/mp3-192/play.mp3"},
        {"Antenne Bayern - 80er Hits (Grandes clásicos ochenteros)", "https://stream.antenne.de/80er-kulthits/stream/mp3"},
        {"80s80s - Pop (Pop comercial de los 80)", "https://streams.80s80s.de/pop/mp3-192/play.mp3"},
        {"80s80s - Michael Jackson (Canal monográfico)", "https://streams.80s80s.de/michaeljackson/mp3-192/play.mp3"},
        {"80s80s - Prince (Canal monográfico)", "https://streams.80s80s.de/prince/mp3-192/play.mp3"},
        {"80s80s - Queen (Canal monográfico)", "https://streams.80s80s.de/queen/mp3-192/play.mp3"},
        {"80s80s - David Bowie (Canal monográfico)", "https://streams.80s80s.de/bowie/mp3-192/play.mp3"},
        {"80s80s - Depeche Mode (Canal monográfico)", "https://streams.80s80s.de/depechemode/mp3-192/play.mp3"},
        {"Antenne Bayern - 80er Disco Hits (Música disco ochentera)", "https://stream.antenne.de/antenne-bayern-80er-disco-hits/stream/mp3"},
        {"80s80s - Disco (Música disco comercial de los 80)", "https://streams.80s80s.de/disco/mp3-192/play.mp3"},
        {"80s80s - Italo Disco (Clásicos de sintetizador italianos)", "https://streams.80s80s.de/italodisco/mp3-192/play.mp3"},
        {"80s80s - Italo Disco In The Mix (Sesión DJ continua)", "https://streams.80s80s.de/italodiscointhemix/mp3-192/play.mp3"},
        {"80s80s - Funk & Soul (Soul, Funk y R&B ochentero)", "https://streams.80s80s.de/soul/mp3-192/play.mp3"},
        {"80s80s - Freestyle (Sonido latino de Miami y Dance 80s)", "https://streams.80s80s.de/freestyle/mp3-192/play.mp3"},
        {"80s80s - Dance (Éxitos de baile de la década de los 80)", "https://streams.80s80s.de/dance/mp3-192/play.mp3"},
        {"80s80s - Breakdance (Clásicos del electro-funk y breakdance)", "https://streams.80s80s.de/breakdance/mp3-192/play.mp3"},
        {"80s80s - HipHop (Pioneros del Rap y Hip-Hop de los 80)", "https://streams.80s80s.de/hiphop/mp3-192/play.mp3"},
        {"80s80s - Maxis (Maxi-Singles y versiones extendidas de 12 pulgadas)", "https://streams.80s80s.de/maxis/mp3-192/play.mp3"},
        {"Antenne Bayern - 80er Party (Temas para fiesta de los 80)", "https://stream.antenne.de/antenne-bayern-80er-party/stream/mp3"},
        {"80s80s - Party (Éxitos bailables de fiesta de los 80)", "https://streams.80s80s.de/party/mp3-192/play.mp3"},
        {"80s80s - In the Mix (Sesiones DJ mezcladas de los 80)", "https://streams.80s80s.de/inthemix/mp3-192/play.mp3"},
        {"80s80s - Neo (Nuevos temas pop con producción estilo 80s)", "https://streams.80s80s.de/neo/mp3-192/play.mp3"},
        {"80s80s - Reggae (Reggae pop y temas veraniegos de los 80)", "https://streams.80s80s.de/reggae/mp3-192/play.mp3"},
        {"AÑOS 80: SYNTH, NEW WAVE & ALTERNATIVO", null},
        {"80s80s - Synthiepop (Sintetizadores, cajas de ritmo y melodías)", "https://streams.80s80s.de/synth/mp3-192/play.mp3"},
        {"80s80s - Wave (New Wave: New Order, The Cure, Ultravox)", "https://streams.80s80s.de/wave/mp3-192/play.mp3"},
        {"80s80s - Dark Wave (Post-punk y vertiente oscura)", "https://streams.80s80s.de/darkwave/mp3-192/play.mp3"},
        {"80s80s - EBM (Electronic Body Music e Industrial)", "https://streams.80s80s.de/ebm/mp3-192/play.mp3"},
        {"80s80s - Alternative (Pop/Rock independiente fuera del mainstream)", "https://streams.80s80s.de/alternative/mp3-192/play.mp3"},
        {"80s80s - Live (Grandes conciertos en directo de los 80)", "https://streams.80s80s.de/live/mp3-192/play.mp3"},
        {"80s80s - Techno (Inicios del Acid House y techno de Detroit)", "https://streams.80s80s.de/techno/mp3-192/play.mp3"},
        {"80s80s - Games (Bandas sonoras y chiptune de videojuegos ochenteros)", "https://streams.80s80s.de/games/mp3-192/play.mp3"},
        {"80s80s - WGT (Emisora oficial del festival Wave-Gotik-Treffen)", "https://streams.80s80s.de/wgt/mp3-192/play.mp3"},
        {"ROCK, GUITARRAS & AÑOS 70", null},
        {"Antenne Bayern - 70er Hits (Grandes números uno de los 70)", "https://stream.antenne.de/70er-hits/stream/mp3"},
        {"Antenne Bayern - 70er Rock (Rock clásico de la década de los 70)", "https://stream.antenne.de/antenne-bayern-70er-rock/stream/mp3"},
        {"Antenne Bayern - Classic Rock (Rock clásico internacional de estadios)", "https://stream.antenne.de/classic-rock-live/stream/mp3"},
        {"Antenne Bayern - ROCK ANTENNE (Rock moderno y contemporáneo)", "https://stream.antenne.de/rockantenne/stream/mp3"},
        {"Antenne Bayern - ROCK ANTENNE Bayern (Rock y rock alternativo)", "https://stream.antenne.de/rockantenne-bayern/stream/mp3"},
        {"Antenne Bayern - 80er Rock (Himnos del rock de los 80)", "https://stream.antenne.de/antenne-bayern-80er-rock/stream/mp3"},
        {"80s80s - Rock (Rock clásico ochentero)", "https://streams.80s80s.de/rock/mp3-192/play.mp3"},
        {"90s90s - Britpop (Oasis, Blur, Pulp, Supergrass)", "https://streams.90s90s.de/britpop/mp3-192/play.mp3"},
        {"90s90s - Grunge (Nirvana, Pearl Jam, Soundgarden, Alice in Chains)", "https://streams.90s90s.de/grunge/mp3-192/play.mp3"},
        {"Antenne Bayern - 90er Rock (Rock internacional de los 90)", "https://stream.antenne.de/antenne-bayern-90er-rock/stream/mp3"},
        {"90s90s - Rock (Himnos del rock noventero)", "https://streams.90s90s.de/rock/mp3-192/play.mp3"},
        {"OTROS GÉNEROS MUSICALES INTERNACIONALES", null},
        {"Antenne Bayern - Country Antenne (Música country americana actual y clásica)", "https://stream.antenne.de/country-antenne/stream/mp3"},
        {"Antenne Bayern - Black Music (Hip-Hop, R&B moderno y Soul)", "https://stream.antenne.de/black-beatz/stream/mp3"},
        {"Antenne Bayern - Dance XXL (Éxitos Dance comercial y electrónica)", "https://stream.antenne.de/dance-xxl/stream/mp3"},
        {"Antenne Bayern - In the Mix (Sesiones pop y dance mezcladas)", "https://stream.antenne.de/in-the-mix/stream/mp3"},
        {"Antenne Bayern - Italo Hits (Grandes clásicos del pop italiano)", "https://stream.antenne.de/antenne-bayern-italo-hits/stream/mp3"},
        {"Antenne Bayern - Workout Mix (Música rítmica para entrenar y hacer deporte)", "https://stream.antenne.de/workout-hits/stream/mp3"},
        {"ESTACIONALES, VERANO & NAVIDAD INTERNACIONAL", null},
        {"Antenne Bayern - Party Hits (Temas de fiesta pop comercial)", "https://stream.antenne.de/party-hits/stream/mp3"},
        {"Antenne Bayern - Sommer Hits (Canciones pop y bailables de verano)", "https://stream.antenne.de/sommer-hits/stream/mp3"},
        {"80s80s - Summer (Banda sonora veraniega de los 80)", "https://streams.80s80s.de/summer/mp3-192/play.mp3"},
        {"90s90s - Sommerhits (Éxitos veraniegos de los 90)", "https://streams.90s90s.de/sommerhits/mp3-192/play.mp3"},
        {"Antenne Bayern - Frühlings Hits (Canciones pop frescas de primavera)", "https://stream.antenne.de/fruehlings-hits/stream/mp3"},
        {"Antenne Bayern - Herbst Hits (Pop melódico y tranquilo de otoño)", "https://stream.antenne.de/herbst-hits/stream/mp3"},
        {"Antenne Bayern - Grill Hits (Rock y pop rítmico para barbacoas)", "https://stream.antenne.de/event/stream/mp3"},
        {"Antenne Bayern - Weihnachtshits (Éxitos pop navideños anglosajones)", "https://stream.antenne.de/weihnachts-hits/stream/mp3"},
        {"Antenne Bayern - Weihnachts-Classics (Clásicos pop y swing de Navidad)", "https://stream.antenne.de/oldie-antenne-event/stream/mp3"},
        {"80s80s - Xmas (Clásicos del pop navideño de los 80)", "https://streams.80s80s.de/xmas/mp3-192/play.mp3"},
        {"Antenne Bayern - Xmas Rock (Clásicos del rock adaptados a Navidad)", "https://stream.antenne.de/xmas-rock/stream/mp3"},
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
