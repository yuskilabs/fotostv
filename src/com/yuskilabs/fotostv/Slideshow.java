package com.yuskilabs.fotostv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pase de fotos y vídeos; lo usan el salvapantallas y la vista previa. */
class Slideshow {
    private static final long REFRESH_MS = 60L * 60L * 1000L;

    static class Slide {
        Item item;
        Bitmap bmp;
        boolean video;
        String album, date, place;
        java.io.File videoFile;
        int state;            // 0 preparando, 1 descargando vídeo, 2 lista, 3 fallida
    }

    private final Context ctx;
    private final FrameLayout root;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ExecutorService ex, vex, bex;
    private java.util.concurrent.Future<?> browseLoad;
    private String browseKey;
    private int session;
    private String loadingKey;
    private final List<Slide> queue = new ArrayList<Slide>();
    private static final int AHEAD = 3;
    private ImageView a, b;
    private TextureView videoView;
    private FrameLayout videoHolder;
    private boolean revealed;
    private int vidW, vidH;
    private Surface surface;
    private MediaPlayer player;
    private Slide videoWaitingSurface;
    private View shade;
    private LinearLayout info;
    private TextView clock, status, tvAlbum, tvDate, tvPlace;
    private boolean showingA = true, running, waiting, playingVideo, paused;
    private Slide cur;
    private final List<Item> hist = new ArrayList<Item>();
    private int histPos = -1, gen;
    private TextView pauseBadge, toast;
    private List<Item> items = new ArrayList<Item>();
    private int idx = -1, w, hgt, fails;
    private String lastKey;
    private List<Item> all = new ArrayList<Item>();
    private java.util.Set<String> seen;

    private final boolean showHint;
    private final Config cfg;

    /** Reproducción de una lista concreta (explorador de álbumes) en vez de los ajustes generales. */
    static class Config {
        List<Item> items;
        String order = "random";   // random | asc | desc | keep (tal cual está la lista)
        boolean manual;            // visor: empieza parado y las flechas no lo ponen en marcha
        String startKey;           // foto por la que empezar
        String hint;               // texto de ayuda al empezar
    }

    private static final String DEFAULT_HINT =
            "\u25C0 \u25B6 cambiar foto    \u25B2 pausa    \u25BC ocultar foto";

    Slideshow(Context ctx, FrameLayout root, boolean showHint) {
        this(ctx, root, showHint, null);
    }

    Slideshow(Context ctx, FrameLayout root, boolean showHint, Config cfg) {
        this.ctx = ctx;
        this.root = root;
        this.showHint = showHint;
        this.cfg = cfg;
    }

    /** Visor del explorador sin pase automático: se comporta como un álbum normal. */
    private boolean browse() { return manual() && paused; }

    private boolean manual() { return cfg != null && cfg.manual; }

    private String order() { return cfg != null ? cfg.order : Prefs.order(ctx); }

    /** Clave de la foto que se está viendo (o por la que se empezó). */
    String currentKey() {
        if (cur != null && cur.item != null) return cur.item.key();
        return cfg != null ? cfg.startKey : null;
    }

    // ------------------------------------------------------------ ciclo de vida
    void start() {
        if (running) return;
        running = true;
        // estado limpio en cada arranque: una carga a medias de la vez anterior no debe bloquear el pase
        session++;
        queue.clear();
        loadingKey = null;
        cur = null;
        playingVideo = false;
        revealed = false;
        fails = 0;
        gen++;
        hist.clear();
        histPos = -1;
        ex = Executors.newFixedThreadPool(2);
        vex = Executors.newSingleThreadExecutor();
        bex = Executors.newSingleThreadExecutor();
        browseLoad = null;
        browseKey = null;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        w = Math.max(dm.widthPixels, dm.heightPixels);
        hgt = Math.min(dm.widthPixels, dm.heightPixels);
        buildViews();
        h.post(clockTick);
        cleanVideos();

        if (cfg != null) {
            paused = cfg.manual;
            rebuild(cfg.items);
        } else {
            rebuild(loadFromCache());
        }
        waiting = !browse();
        if (all.isEmpty()) status.setText(cfg != null ? "No hay fotos para mostrar." : "Cargando fotos…");
        else if (browse()) {
            if (idx + 1 >= 0 && idx + 1 < items.size()) browseTo(items.get(idx + 1));
        } else prefetch();
        if (cfg == null) refresh();
        if (showHint || (cfg != null && cfg.hint != null))
            flash(cfg != null && cfg.hint != null ? cfg.hint : DEFAULT_HINT, 5000);
    }

    void stop() {
        running = false;
        h.removeCallbacksAndMessages(null);
        session++;
        if (ex != null) ex.shutdownNow();
        if (vex != null) vex.shutdownNow();
        if (bex != null) bex.shutdownNow();
        releasePlayer();
        queue.clear();
        cur = null;
        cleanVideos();
        if (a != null) a.setImageDrawable(null);
        if (b != null) b.setImageDrawable(null);
    }

    // ------------------------------------------------------------ vistas
    private void buildViews() {
        root.removeAllViews();
        root.setBackgroundColor(Color.BLACK);
        a = newImage();
        b = newImage();
        root.addView(a);
        root.addView(b);
        b.setAlpha(0f);

        videoHolder = new FrameLayout(ctx);
        videoHolder.setBackgroundColor(Color.BLACK);
        videoHolder.setAlpha(0f);
        videoHolder.setVisibility(View.INVISIBLE);
        videoView = new TextureView(ctx);
        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                surface = new Surface(st);
                if (videoWaitingSurface != null) {
                    Slide s = videoWaitingSurface;
                    videoWaitingSurface = null;
                    playVideo(s);
                }
            }
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) { }
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                releasePlayer();
                if (surface != null) surface.release();
                surface = null;
                return true;
            }
            public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
        videoHolder.addView(videoView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(videoHolder, new FrameLayout.LayoutParams(-1, -1));

        shade = new View(ctx);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x99000000, 0x00000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, (int) (hgt * 0.32f), Gravity.BOTTOM));

        info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        tvAlbum = text(18, Gravity.START);
        tvAlbum.setAlpha(0.8f);
        tvDate = text(28, Gravity.START);
        tvPlace = text(21, Gravity.START);
        info.addView(tvAlbum);
        info.addView(tvDate);
        info.addView(tvPlace);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        ip.setMargins(dp(48), 0, 0, dp(32));
        root.addView(info, ip);

        clock = text(46, Gravity.END);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        cp.setMargins(0, 0, dp(48), dp(28));
        root.addView(clock, cp);
        clock.setVisibility(Prefs.clock(ctx) ? View.VISIBLE : View.GONE);

        status = text(22, Gravity.CENTER);
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        pauseBadge = text(22, Gravity.CENTER);
        pauseBadge.setText("\u275A\u275A  En pausa");
        pauseBadge.setPadding(dp(18), dp(8), dp(18), dp(8));
        pauseBadge.setBackground(pill());
        pauseBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pp.setMargins(0, dp(28), dp(40), 0);
        root.addView(pauseBadge, pp);

        toast = text(20, Gravity.CENTER);
        toast.setPadding(dp(22), dp(10), dp(22), dp(10));
        toast.setBackground(pill());
        toast.setVisibility(View.GONE);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.setMargins(0, dp(28), 0, 0);
        root.addView(toast, tp);

        boolean anyText = Prefs.clock(ctx) || Prefs.showAlbum(ctx) || Prefs.showDate(ctx) || Prefs.showPlace(ctx);
        shade.setVisibility(anyText ? View.VISIBLE : View.GONE);
        info.setVisibility(View.INVISIBLE);
    }

    private void overlaysToFront() {
        shade.bringToFront();
        info.bringToFront();
        clock.bringToFront();
        status.bringToFront();
        pauseBadge.bringToFront();
        toast.bringToFront();
    }

    private GradientDrawable pill() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x99000000);
        g.setCornerRadius(dp(24));
        return g;
    }

    private final Runnable hideToast = new Runnable() {
        public void run() {
            toast.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
                public void run() { toast.setVisibility(View.GONE); }
            }).start();
        }
    };

    private void flash(String msg, long ms) {
        h.removeCallbacks(hideToast);
        toast.animate().cancel();
        toast.setText(msg);
        toast.setAlpha(1f);
        toast.setVisibility(View.VISIBLE);
        toast.bringToFront();
        h.postDelayed(hideToast, ms);
    }

    private ImageView newImage() {
        ImageView v = new ImageView(ctx);
        v.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        v.setBackgroundColor(Color.BLACK);   // sin franjas transparentes en fotos verticales
        return v;
    }

    private TextView text(int sp, int gravity) {
        TextView t = new TextView(ctx);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(gravity);
        t.setShadowLayer(8f, 0f, 2f, 0xCC000000);
        return t;
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    private void setInfo(final Slide s) {
        info.animate().cancel();
        final Runnable apply = new Runnable() {
            public void run() {
                set(tvAlbum, Prefs.showAlbum(ctx) ? s.album : null);
                set(tvDate, Prefs.showDate(ctx) ? s.date : null);
                set(tvPlace, Prefs.showPlace(ctx) && s.place != null ? "\u2316 " + s.place : null);
                info.setVisibility(View.VISIBLE);
                info.setAlpha(0f);
                info.animate().alpha(1f).setDuration(600).start();
            }
        };
        if (info.getVisibility() == View.VISIBLE && info.getAlpha() > 0.05f)
            info.animate().alpha(0f).setDuration(300).withEndAction(apply).start();
        else apply.run();
    }

    private static void set(TextView t, String s) {
        if (s == null || s.length() == 0) t.setVisibility(View.GONE);
        else { t.setText(s); t.setVisibility(View.VISIBLE); }
    }

    // ------------------------------------------------------------ lista de reproducción
    private List<Item> loadFromCache() {
        List<Item> all = new ArrayList<Item>();
        for (Prefs.Album al : Prefs.activeAlbums(ctx)) {
            AlbumFetcher.AlbumData d = Prefs.albumCache(ctx, al.url);
            if (d == null) continue;
            String name = al.name.length() > 0 ? al.name : d.title;
            for (Item it : d.items) { it.album = name; if (it.akey.length() == 0) it.akey = d.key; all.add(it); }
        }
        return all;
    }

    /**
     * Aleatorio sin repeticiones: cada foto sale una sola vez por vuelta. Las ya vistas se
     * recuerdan aunque se apague el salvapantallas; al acabar la vuelta se baraja de nuevo.
     * Por fecha: continúa desde la última foto mostrada.
     */
    private synchronized void rebuild(List<Item> list) {
        boolean keep = "keep".equals(order());
        java.util.Set<String> hidden = keep ? new java.util.HashSet<String>() : Prefs.hidden(ctx);
        all = new ArrayList<Item>();
        java.util.Set<String> keys = new java.util.HashSet<String>();
        for (Item it : list) if (!hidden.contains(it.key()) && keys.add(it.key())) all.add(it);
        if (seen == null) seen = cfg != null ? new java.util.HashSet<String>() : Prefs.seen(ctx);
        if (keep) {
            items = new ArrayList<Item>(all);
            idx = -1;
            if (cfg != null && cfg.startKey != null)
                for (int i = 0; i < items.size(); i++)
                    if (items.get(i).key().equals(cfg.startKey)) { idx = i - 1; break; }
            return;
        }
        if (isRandom()) {
            items = new ArrayList<Item>();
            if (noRepeat()) {
                for (Item it : all) if (!seen.contains(it.key())) items.add(it);
            } else {
                items.addAll(all);
            }
            if (items.isEmpty()) newRound();
            else Collections.shuffle(items);
            idx = -1;
            return;
        }
        final boolean asc = "asc".equals(order());
        items = new ArrayList<Item>(all);
        Collections.sort(items, new Comparator<Item>() {
            public int compare(Item x, Item y) {
                if (x.ts == 0 && y.ts == 0) return 0;
                if (x.ts == 0) return 1;
                if (y.ts == 0) return -1;
                return asc ? Long.compare(x.ts, y.ts) : Long.compare(y.ts, x.ts);
            }
        });
        idx = -1;
        String last = lastKey != null ? lastKey : (cfg == null && Prefs.resume(ctx) ? Prefs.lastShown(ctx) : null);
        if (last != null) for (int i = 0; i < items.size(); i++)
            if (items.get(i).key().equals(last)) { idx = i; break; }
    }

    private boolean isRandom() {
        String o = order();
        return "random".equals(o) || "norepeat".equals(o);
    }

    private boolean noRepeat() { return "norepeat".equals(order()); }

    /** Empieza una vuelta nueva con todas las fotos barajadas. */
    private void newRound() {
        if (noRepeat()) {
            seen.clear();
            Prefs.saveSeen(ctx, seen);
        }
        items = new ArrayList<Item>(all);
        Collections.shuffle(items);
        // que la primera de la vuelta nueva no sea la que se acaba de ver
        if (items.size() > 1 && cur != null && items.get(0).key().equals(cur.item.key()))
            Collections.swap(items, 0, items.size() - 1);
    }

    private synchronized Item nextItem() {
        if (all.isEmpty()) return null;
        idx++;
        if (idx >= items.size()) {
            if (isRandom()) newRound();
            idx = 0;
        }
        if (items.isEmpty()) return null;
        Item it = items.get(idx);
        lastKey = it.key();
        return it;
    }

    private void markShown(Item it) {
        if (cfg != null) {                       // explorador: no toca lo guardado del salvapantallas
            if (cfg.manual) cfg.startKey = it.key();
            return;
        }
        if (seen == null) seen = Prefs.seen(ctx);
        if (noRepeat() && seen.add(it.key())) Prefs.saveSeen(ctx, seen);
        Prefs.setLastShown(ctx, it.key());
    }

    private void refresh() {
        final List<Prefs.Album> albums = Prefs.activeAlbums(ctx);
        ex.submit(new Runnable() {
            public void run() {
                final List<Item> all = new ArrayList<Item>();
                String error = albums.isEmpty() ? "No hay álbumes seleccionados." : null;
                for (Prefs.Album al : albums) {
                    AlbumFetcher.AlbumData d;
                    try {
                        d = AlbumFetcher.fetchAlbum(al.url);
                        Prefs.saveAlbumCache(ctx, al.url, d);
                    } catch (Exception e) {
                        error = e.getMessage();
                        d = Prefs.albumCache(ctx, al.url);
                    }
                    if (d == null) continue;
                    String name = al.name.length() > 0 ? al.name : d.title;
                    for (Item it : d.items) { it.album = name; if (it.akey.length() == 0) it.akey = d.key; all.add(it); }
                }
                final String err = error;
                h.post(new Runnable() {
                    public void run() {
                        if (!running) return;
                        if (all.isEmpty()) {
                            status.setText(err + "\nAbre la app «FotosTV» para configurarlo.");
                            return;
                        }
                        rebuild(all);
                        if (Slideshow.this.all.isEmpty()) {
                            status.setText("Todas las fotos están ocultas.\nPuedes recuperarlas desde la web de ajustes.");
                            return;
                        }
                        prefetch();
                    }
                });
                h.postDelayed(new Runnable() {
                    public void run() { if (running) refresh(); }
                }, REFRESH_MS);
            }
        });
    }

    // ------------------------------------------------------------ caché temporal de vídeos
    private static final long MAX_VIDEO = 600L * 1024 * 1024;

    private java.io.File videoDir() {
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "videos");
        dir.mkdirs();
        return dir;
    }

    /** Borra los vídeos temporales salvo el actual, los de la cola de precarga y el que se está cargando. */
    private void cleanVideos() {
        java.io.File[] fs = videoDir().listFiles();
        if (fs == null) return;
        java.util.Set<String> keep = new java.util.HashSet<String>();
        if (cur != null && cur.item != null) keep.add(cur.item.key());
        for (Slide q : queue) keep.add(q.item.key());
        if (loadingKey != null) keep.add(loadingKey);
        // los de las últimas 10 fotos vistas se guardan por si se vuelve atrás (hasta un tope de espacio)
        java.util.Set<String> recent = new java.util.HashSet<String>();
        for (int i = Math.max(0, hist.size() - 10); i < hist.size(); i++) recent.add(hist.get(i).key());
        List<java.io.File> old = new ArrayList<java.io.File>();
        for (java.io.File f : fs) {
            String n = f.getName();
            int d = n.indexOf('.');
            String k = d > 0 ? n.substring(0, d) : n;
            if (keep.contains(k)) continue;
            if (recent.contains(k)) old.add(f); else f.delete();
        }
        java.util.Collections.sort(old, new java.util.Comparator<java.io.File>() {
            public int compare(java.io.File x, java.io.File y) {
                return x.lastModified() < y.lastModified() ? 1 : (x.lastModified() > y.lastModified() ? -1 : 0);
            }
        });
        long total = 0;
        for (java.io.File f : old) {
            total += f.length();
            if (total > Prefs.videoMB(ctx) * 1024L * 1024L) f.delete();
        }
    }

    /** Descarga el vídeo (la mejor calidad disponible (1080p); si no, otras) a un archivo temporal. */
    private java.io.File loadVideo(Item it) {
        java.io.File f = new java.io.File(videoDir(), it.key() + ".mp4");
        if (f.exists() && f.length() > 10000) return f;
        java.io.File part = new java.io.File(videoDir(), it.key() + ".mp4.part");
        String[] variants = {"=m37", "=m22", "=m18", "=dv"};
        for (String v : variants) {
            if (Thread.currentThread().isInterrupted()) break;
            if (AlbumFetcher.downloadVideo(it.url + v, part, MAX_VIDEO) && part.renameTo(f)) return f;
        }
        part.delete();
        return null;
    }

    // ------------------------------------------------------------ carga de una diapositiva
    /** Versión síncrona (navegación hacia atrás): vídeo o foto. */
    private Slide buildSlide(Item it) {
        try {
            if (Prefs.video(ctx) && MetaFetcher.isVideo(ctx, it)) {
                loadingKey = it.key();
                java.io.File f = loadVideo(it);
                if (f != null) {
                    Slide t = new Slide();
                    t.item = it;
                    t.album = it.album;
                    t.video = true;
                    t.videoFile = f;
                    t.date = MetaFetcher.formatDate(it.ts);
                    return t;
                }
            }
            return buildPhoto(it);
        } catch (Exception e) {
            return null;
        }
    }

    private Slide buildPhoto(Item it) {
        try {
            Slide t = new Slide();
            t.item = it;
            t.album = it.album;
            long date = it.ts;
            byte[] data = AlbumFetcher.get(it.url + "=w" + w + "-h" + hgt);
            t.bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (t.bmp == null) return null;
            boolean wantPlace = Prefs.showPlace(ctx);
            if (wantPlace || Prefs.showDate(ctx)) {
                double[] e = MetaFetcher.exif(ctx, it);
                if (e[2] > 0) date = (long) e[2];
                if (wantPlace) t.place = MetaFetcher.place(ctx, e[0], e[1]);
            }
            t.date = MetaFetcher.formatDate(date);
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ precarga de la siguiente
    private int count(int state) {
        int n = 0;
        for (Slide q : queue) if (q.state == state) n++;
        return n;
    }

    /** Mantiene varias diapositivas preparadas por delante. Los vídeos largos se bajan aparte y no frenan las fotos. */
    private void prefetch() {
        if (!running || browse()) return;
        int guard = 0;
        while (running && guard++ < 50 && count(2) < AHEAD && count(0) < 2 && count(1) < 2 && queue.size() < 8) {
            Item it = nextItem();
            if (it == null) break;
            if (Prefs.hidden(ctx).contains(it.key())) continue;
            startBuild(it);
        }
    }

    private void startBuild(final Item it) {
        final Slide t = new Slide();
        t.item = it;
        t.album = it.album;
        queue.add(t);
        final int sess = session;
        ex.submit(new Runnable() {
            public void run() {
                boolean isVid = false;
                try { isVid = Prefs.video(ctx) && MetaFetcher.isVideo(ctx, it); } catch (Exception ignored) { }
                if (isVid) {
                    h.post(new Runnable() {
                        public void run() {
                            if (sess != session || !running) return;
                            t.video = true;
                            t.state = 1;
                            startVideo(t, sess);
                            prefetch();             // la foto siguiente no espera al vídeo
                        }
                    });
                    return;
                }
                final Slide p = buildPhoto(it);
                h.post(new Runnable() {
                    public void run() {
                        if (sess != session || !running) return;
                        if (p == null) { t.state = 3; } else {
                            t.bmp = p.bmp; t.date = p.date; t.place = p.place; t.state = 2;
                        }
                        onBuilt(t);
                    }
                });
            }
        });
    }

    private void startVideo(final Slide t, final int sess) {
        vex.submit(new Runnable() {
            public void run() {
                java.io.File f = null;
                try { f = loadVideo(t.item); } catch (Exception ignored) { }
                final java.io.File got = f;
                Slide fallback = null;
                if (got == null) fallback = buildPhoto(t.item);   // sin vídeo: al menos la imagen
                final Slide fb = fallback;
                h.post(new Runnable() {
                    public void run() {
                        if (sess != session || !running) return;
                        if (got != null) {
                            t.videoFile = got;
                            t.date = MetaFetcher.formatDate(t.item.ts);
                            t.state = 2;
                        } else if (fb != null) {
                            t.video = false;
                            t.bmp = fb.bmp; t.date = fb.date; t.place = fb.place; t.state = 2;
                        } else {
                            t.state = 3;
                        }
                        onBuilt(t);
                    }
                });
            }
        });
    }

    private void onBuilt(Slide t) {
        if (!queue.contains(t)) return;
        if (t.state == 3) {
            queue.remove(t);
            fails++;
            if (cur == null && count(2) == 0 && count(0) == 0 && count(1) == 0)
                status.setText("Sin conexión con Google Fotos. Reintentando…");
            h.postDelayed(new Runnable() {
                public void run() { prefetch(); }
            }, fails <= 3 ? 500L * fails : Math.min(fails, 10) * 15000L);
            return;
        }
        fails = 0;
        if (Prefs.hidden(ctx).contains(t.item.key())) {
            queue.remove(t);
            prefetch();
            return;
        }
        if (waiting) { waiting = false; goNext(); }
        else prefetch();
    }

    /** Primera diapositiva lista de la cola (si el vídeo de turno aún se baja, pasa la siguiente y él entra cuando esté). */
    private Slide takeReady() {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).state == 2) return queue.remove(i);
        }
        return null;
    }

    private final Runnable tick = new Runnable() {
        public void run() { if (!paused) goNext(); }
    };

    // ------------------------------------------------------------ navegación
    // ------------------------------------------------------------ visor tipo álbum
    private int indexOfKey(String k) {
        if (k == null) return -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(k)) return i;
        return -1;
    }

    private void syncAuto() {
        queue.clear();
        int i = indexOfKey(browseKey != null ? browseKey : (cur != null ? cur.item.key() : null));
        if (i >= 0) idx = i;
        waiting = false;
        prefetch();
    }

    private void browseStep(int dir) {
        if (items.isEmpty()) return;
        String k = browseKey != null ? browseKey : (cur != null ? cur.item.key() : cfg.startKey);
        int ci = indexOfKey(k);
        int ni = ci < 0 ? 0 : ci + dir;
        if (ni < 0) { flash("Es la primera", 1500); return; }
        if (ni >= items.size()) { flash("Es la última", 1500); return; }
        browseTo(items.get(ni));
    }

    /** Va a una foto o vídeo concreto: responde al momento (miniatura + «Cargando vídeo…»). */
    private void browseTo(final Item it) {
        final int myGen = ++gen;
        final int sess = session;
        browseKey = it.key();
        h.removeCallbacks(tick);
        if (browseLoad != null) { browseLoad.cancel(true); browseLoad = null; }
        ex.submit(new Runnable() {
            public void run() {
                boolean vid = false;
                try { vid = Prefs.video(ctx) && MetaFetcher.isVideo(ctx, it); } catch (Exception ignored) { }
                final boolean isVid = vid;
                final Slide p = buildPhoto(it);   // la foto, o la imagen fija del vídeo
                h.post(new Runnable() {
                    public void run() {
                        if (!running || sess != session || myGen != gen) return;
                        if (p != null) display(p, !isVid);
                        if (!isVid) {
                            if (p == null) flash("No se pudo cargar esta foto", 2500);
                            return;
                        }
                        loadingKey = it.key();
                        flash("Cargando vídeo…", 600000);
                        browseLoad = bex.submit(new Runnable() {
                            public void run() {
                                java.io.File f = null;
                                try { f = loadVideo(it); } catch (Exception ignored) { }
                                final java.io.File got = f;
                                h.post(new Runnable() {
                                    public void run() {
                                        if (!running || sess != session || myGen != gen) return;
                                        h.removeCallbacks(hideToast);
                                        hideToast.run();
                                        if (got == null) { flash("No se pudo cargar el vídeo", 3000); return; }
                                        Slide v = new Slide();
                                        v.item = it;
                                        v.album = it.album;
                                        v.video = true;
                                        v.videoFile = got;
                                        v.state = 2;
                                        v.date = p != null && p.date != null ? p.date : MetaFetcher.formatDate(it.ts);
                                        v.place = p != null ? p.place : null;
                                        display(v, true);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private void goNext() {
        if (!running) return;
        if (browse()) { browseStep(1); return; }
        if (histPos < hist.size() - 1) {
            navigateTo(hist.get(histPos + 1), histPos + 1);
        } else {
            Slide s = takeReady();
            if (s != null) {
                gen++;
                display(s, true);
                prefetch();
            } else {
                waiting = true;
                prefetch();
            }
        }
    }

    private void goPrev() {
        if (browse()) { browseStep(-1); return; }
        if (histPos > 0) {
            navigateTo(hist.get(histPos - 1), histPos - 1);
        } else if (manual() && cur != null) {
            // visor: la anterior en la lista, aunque todavía no haya pasado por ella
            int ci = -1;
            for (int i = 0; i < items.size(); i++)
                if (items.get(i).key().equals(cur.item.key())) { ci = i; break; }
            if (ci > 0) {
                Item prev = items.get(ci - 1);
                hist.add(0, prev);
                histPos++;
                navigateTo(prev, 0);
            } else {
                flash("Es la primera", 1500);
            }
        } else {
            flash("No hay fotos anteriores", 1500);
        }
    }

    private void navigateTo(final Item it, final int pos) {
        final int myGen = ++gen;
        h.removeCallbacks(tick);
        final int sess = session;
        ex.submit(new Runnable() {
            public void run() {
                final Slide s = buildSlide(it);
                h.post(new Runnable() {
                    public void run() {
                        if (!running || sess != session || myGen != gen) return;
                        int p = hist.indexOf(it);
                        if (p < 0) { goNext(); return; }
                        if (s == null) {          // no se pudo cargar: se salta
                            hist.remove(p);
                            if (p <= histPos) histPos--;
                            goNext();
                            return;
                        }
                        histPos = p;
                        display(s, false);
                    }
                });
            }
        });
    }

    private void display(Slide s, boolean append) {
        markShown(s.item);
        if (append) {
            hist.add(s.item);
            while (hist.size() > 200) hist.remove(0);
            histPos = hist.size() - 1;
        }
        h.removeCallbacks(tick);
        h.removeCallbacks(videoTimeout);
        status.setText("");
        cur = s;
        loadingKey = null;
        cleanVideos();
        if (s.video) {
            playVideo(s);
        } else {
            boolean wasVideo = playingVideo;
            playingVideo = false;
            showImage(s, wasVideo);
            if (!paused) h.postDelayed(tick, Prefs.interval(ctx) * 1000L);
        }
    }

    // ------------------------------------------------------------ mando
    /** Devuelve true si la tecla es de navegación (se consume); false = salir. */
    boolean onKey(int keyCode) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!manual()) setPaused(false, false);
                goNext();
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                if (!manual()) setPaused(false, false);
                goPrev();
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_UP:
                setPaused(!paused, true);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                hideCurrent();
                return true;
            default:
                return false;
        }
    }

    static boolean isNavKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private void setPaused(boolean p, boolean announce) {
        if (p == paused) return;
        boolean wasBrowse = browse();
        paused = p;
        if (p && manual()) {
            queue.clear();
            browseKey = cur != null && cur.item != null ? cur.item.key() : browseKey;
        } else if (wasBrowse) {
            syncAuto();
        }
        pauseBadge.setVisibility(p && !manual() ? View.VISIBLE : View.GONE);
        if (p) {
            if (announce && manual()) flash("\u275A\u275A  Pase en pausa", 1500);
            h.removeCallbacks(tick);
            if (playingVideo && player != null) try { player.pause(); } catch (Exception ignored) { }
        } else {
            if (playingVideo && player != null) {
                try { player.start(); } catch (Exception ignored) { }
            } else if (announce && cur != null) {
                h.removeCallbacks(tick);
                h.postDelayed(tick, Prefs.interval(ctx) * 1000L);
            }
            if (announce) flash(manual() ? "\u25B6  Pase automático" : "\u25B6  Continúa el pase", 1500);
        }
    }

    private void hideCurrent() {
        if (cur == null || manual()) return;   // en el visor ▼ no hace nada (ocultar es del salvapantallas)
        Item it = cur.item;
        Prefs.hide(ctx, it.key());
        synchronized (this) {
            for (int i = all.size() - 1; i >= 0; i--)
                if (all.get(i).key().equals(it.key())) all.remove(i);
            for (int i = items.size() - 1; i >= 0; i--) {
                if (items.get(i).key().equals(it.key())) {
                    items.remove(i);
                    if (i <= idx) idx--;
                }
            }
        }
        for (int i = hist.size() - 1; i >= 0; i--) {
            if (hist.get(i).key().equals(it.key())) {
                hist.remove(i);
                if (i <= histPos) histPos--;
            }
        }
        for (int i = queue.size() - 1; i >= 0; i--)
            if (queue.get(i).item.key().equals(it.key())) queue.remove(i);
        flash("Foto ocultada. No volverá a salir.", 2500);
        setPaused(false, false);
        cur = null;
        if (all.isEmpty()) {
            h.removeCallbacks(tick);
            releasePlayer();
            playingVideo = false;
            status.setText("Todas las fotos están ocultas.\nPuedes recuperarlas desde la web de ajustes.");
            return;
        }
        goNext();
    }

    private void showImage(Slide s, final boolean hideVideo) {
        final ImageView front = showingA ? a : b;
        final ImageView back = showingA ? b : a;
        h.removeCallbacks(revealLater);
        front.animate().cancel();
        back.animate().cancel();
        videoHolder.animate().cancel();
        front.setAlpha(1f);                  // si se salta rápido, lo que estaba entrando queda completo
        back.setScaleType(scaleFor(s.bmp));
        back.setImageBitmap(s.bmp);
        back.setAlpha(0f);
        back.bringToFront();
        overlaysToFront();
        setInfo(s);
        if (hideVideo) releasePlayer();
        back.animate().alpha(1f).setDuration(hideVideo ? 800 : 1200)
            .setInterpolator(new AccelerateDecelerateInterpolator()).withLayer()
            .withEndAction(new Runnable() {
                public void run() {
                    front.setImageDrawable(null);
                    front.setAlpha(0f);
                    if (hideVideo) {
                        videoHolder.setAlpha(0f);
                        videoHolder.setVisibility(View.INVISIBLE);
                    }
                }
            }).start();
        showingA = !showingA;
    }

    // ------------------------------------------------------------ vídeo
    private final Runnable videoTimeout = new Runnable() {
        public void run() {
            if (player != null && !player.isPlaying() && !paused) flash("El vídeo no termina de cargar", 4000);
            endVideo();
        }
    };

    private final Runnable revealLater = new Runnable() {
        public void run() { revealVideo(); }
    };

    /** Congela el último fotograma del vídeo en una imagen para poder fundir desde él. */
    private void freezeVideoFrame() {
        try {
            Bitmap bm = videoView.getBitmap();
            if (bm == null) return;
            ImageView old = showingA ? a : b;
            ImageView snap = showingA ? b : a;
            old.animate().cancel();
            snap.animate().cancel();
            snap.setScaleType(ImageView.ScaleType.FIT_CENTER);
            snap.setImageBitmap(bm);
            snap.setAlpha(1f);
            snap.bringToFront();
            overlaysToFront();
            old.setImageDrawable(null);
            old.setAlpha(0f);
            showingA = !showingA;
        } catch (Throwable ignored) { }
    }

    /** Muestra el vídeo con fundido, solo cuando ya se ha pintado su primer fotograma. */
    private void revealVideo() {
        if (revealed || !running || !playingVideo || cur == null || !cur.video) return;
        revealed = true;
        fitVideo(vidW, vidH);
        videoHolder.bringToFront();
        overlaysToFront();
        setInfo(cur);
        videoHolder.animate().alpha(1f).setDuration(1000)
            .setInterpolator(new AccelerateDecelerateInterpolator()).withLayer().start();
    }

    private void playVideo(final Slide s) {
        playingVideo = true;
        revealed = false;
        h.removeCallbacks(revealLater);
        videoHolder.animate().cancel();
        if (videoHolder.getVisibility() == View.VISIBLE && videoHolder.getAlpha() > 0.5f) freezeVideoFrame();
        releasePlayer();
        videoHolder.setVisibility(View.VISIBLE);
        videoHolder.setAlpha(0.01f);   // casi invisible, pero la vista sigue pintándose por detrás
        if (surface == null) { videoWaitingSurface = s; h.postDelayed(videoTimeout, 30000); return; }
        try {
            final MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setDataSource(s.videoFile.getAbsolutePath());
            mp.setSurface(surface);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer p) {
                    if (p != player) return;
                    h.removeCallbacks(videoTimeout);
                    if (!Prefs.sound(ctx)) p.setVolume(0f, 0f);
                    p.start();   // también en pausa: hace falta para pintar el primer fotograma
                    int dur = p.getDuration();
                    if (!paused) h.postDelayed(videoTimeout, (dur > 0 ? dur : 5 * 60 * 1000) + 15000L);
                    h.postDelayed(revealLater, 2500);   // por si el reproductor no avisa del primer fotograma
                }
            });
            mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                public boolean onInfo(MediaPlayer p, int what, int extra) {
                    if (p == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        if (paused && !manual()) { try { p.pause(); } catch (Exception ignored) { } }
                        h.postDelayed(revealLater, 150);   // da tiempo a que la vista pinte ese fotograma
                    }
                    return false;
                }
            });
            mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer p, int vw, int vh) {
                    vidW = vw;
                    vidH = vh;
                    fitVideo(vw, vh);
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer p) { if (p == player) endVideo(); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer p, int what, int extra) {
                    if (p == player) {
                        flash("No se pudo reproducir este vídeo (error " + what + "/" + extra + ")", 4000);
                        endVideo();
                    }
                    return true;
                }
            });
            h.postDelayed(videoTimeout, 30000);
            mp.prepareAsync();
        } catch (Exception e) {
            endVideo();
        }
    }

    private void fitVideo(int vw, int vh) {
        if (vw <= 0 || vh <= 0) return;
        float viewW = videoView.getWidth(), viewH = videoView.getHeight();
        if (viewW <= 0 || viewH <= 0) return;
        float scale = Math.min(viewW / vw, viewH / vh);
        Matrix m = new Matrix();
        m.setScale(vw * scale / viewW, vh * scale / viewH, viewW / 2f, viewH / 2f);
        videoView.setTransform(m);
    }

    private void endVideo() {
        h.removeCallbacks(videoTimeout);
        videoWaitingSurface = null;
        if (!playingVideo) return;
        if (paused) return;   // en pausa se queda en el vídeo
        goNext();
    }

    private void releasePlayer() {
        if (player != null) {
            MediaPlayer p = player;
            player = null;
            try { p.stop(); } catch (Exception ignored) { }
            p.release();
        }
    }

    // ------------------------------------------------------------ utilidades
    private ImageView.ScaleType scaleFor(Bitmap bm) {
        String fit = Prefs.fit(ctx);
        if ("crop".equals(fit)) return ImageView.ScaleType.CENTER_CROP;
        if ("fit".equals(fit)) return ImageView.ScaleType.FIT_CENTER;
        float img = bm.getWidth() / (float) bm.getHeight();
        float scr = w / (float) hgt;
        return Math.abs(img - scr) / scr < 0.25f
                ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER;
    }

    private final Runnable clockTick = new Runnable() {
        public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            h.postDelayed(this, 10000);
        }
    };
}
