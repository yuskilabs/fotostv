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
    /** Emisoras tranquilas y sin publicidad: {nombre, dirección}. */
    static final String[][] PRESETS = {
        {"SomaFM · Drone Zone (ambiental, muy tranquila)", "https://ice1.somafm.com/dronezone-128-mp3"},
        {"SomaFM · Deep Space One (ambiental profunda)", "https://ice1.somafm.com/deepspaceone-128-mp3"},
        {"SomaFM · Space Station (ambiental suave)", "https://ice1.somafm.com/spacestation-128-mp3"},
        {"SomaFM · Groove Salad (suave, con ritmo tranquilo)", "https://ice1.somafm.com/groovesalad-128-mp3"},
        {"Radio Paradise · Mellow Mix (suave)", "https://stream.radioparadise.com/mellow-192"},
        {"Radio Swiss Classic (clásica)", "https://stream.srg-ssr.ch/m/rsc_de/mp3_128"},
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
    static boolean enabled(Context c, boolean dream) {
        String m = Prefs.musicMode(c);
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
        if ("folder".equals(Prefs.musicMode(app))) scanThenPlay(); else next();
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
        boolean radio = "radio".equals(Prefs.musicMode(app));
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
                    retry(!"radio".equals(Prefs.musicMode(app)));   // en carpeta, la siguiente canción; en radio, reconectar
                }
            });
            m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer p, int what, int extra) {
                    if (g != gen || p != mp) return true;
                    retry("folder".equals(Prefs.musicMode(app)));   // canción que no se lee: se salta; emisora caída: se reintenta
                    return true;
                }
            });
            m.prepareAsync();
        } catch (Exception e) {
            retry("folder".equals(Prefs.musicMode(app)));
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
