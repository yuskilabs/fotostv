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
        java.util.concurrent.Future<java.io.File> hdFut;   // descarga a la caché mientras se ve en directo
        VideoProxy.Prog prog;
        String streamUrl;     // vídeo en directo (versión ligera) mientras baja la buena
        int startMs;          // empezar el vídeo por aquí (al pasar de baja a alta calidad)
        int state;            // 0 preparando, 1 descargando vídeo, 2 lista, 3 fallida
    }

    private final Context ctx;
    private final FrameLayout root;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ExecutorService ex, vex, bex, hdEx;
    private java.util.concurrent.Future<?> browseLoad, upLoad;
    private volatile String browseKey;
    private volatile boolean detecting;
    private Item aheadItem;
    private final Runnable aheadRun = new Runnable() {
        public void run() { if (running && aheadItem != null) browseAhead(aheadItem); }
    };
    private int session;
    private String loadingKey;
    private final List<Slide> queue = new ArrayList<Slide>();
    private static final int AHEAD = 3;
    private ImageView a, b;
    private TextureView videoView;
    private FrameLayout videoHolder;
    private boolean revealed, frameRendered;
    private int greenTries;
    private long videoStart;
    private int vidW, vidH;
    private Surface surface;
    private MediaPlayer player;
    private Slide videoWaitingSurface;
    private View shade;
    private LinearLayout info;
    private TextView clock, status, tvAlbum, tvDate, tvPlace;
    private boolean showingA = true, running, waiting, playingVideo, paused;
    private InfoPanel panel;
    private boolean resumeAfterInfo;
    private Slide cur;
    private final List<Item> hist = new ArrayList<Item>();
    private int histPos = -1, gen;
    private TextView pauseBadge, toast, tvPos, tvRem;
    private LinearLayout progRow;
    private FrameLayout progTrack;
    private View progFill;
    private GradientDrawable progFillBg;
    private boolean ctlMode;
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

    private static boolean hintShown;
    private static final String DEFAULT_HINT =
            "\u25C0 \u25B6 cambiar foto    OK pausa    \u25B2 ocultar foto    \u25BC información";

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
        hdEx = Executors.newSingleThreadExecutor();
        pex = Executors.newFixedThreadPool(4, new java.util.concurrent.ThreadFactory() {
            public Thread newThread(final Runnable r) {
                return new Thread(new Runnable() {
                    public void run() {
                        try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); } catch (Throwable ignored) { }
                        r.run();
                    }
                });
            }
        });
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
        if (!hintShown && (showHint || (cfg != null && cfg.hint != null))) {
            hintShown = true;   // el letrero de arriba solo sale la primera vez
            flash(cfg != null && cfg.hint != null ? cfg.hint : DEFAULT_HINT, 5000);
        }
    }

    void stop() {
        running = false;
        if (panel != null) { panel.detach(); panel = null; }
        h.removeCallbacksAndMessages(null);
        session++;
        if (ex != null) ex.shutdownNow();
        if (vex != null) vex.shutdownNow();
        if (bex != null) bex.shutdownNow();
        if (hdEx != null) hdEx.shutdownNow();
        if (pex != null) pex.shutdownNow();
        synchronized (bcache) { bcache.clear(); }
        synchronized (bytesCache) { bytesCache.clear(); bytesTotal = 0; }
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
        tvDate = text(24, Gravity.START);
        tvPlace = text(21, Gravity.START);
        // una sola línea cada una: así una fecha larga nunca se parte ni se corta («18 de diciembre de»)
        TextView[] lines = {tvAlbum, tvDate, tvPlace};
        for (TextView t : lines) {
            t.setSingleLine(true);
            t.setEllipsize(t == tvDate ? null : android.text.TextUtils.TruncateAt.END);   // la fecha nunca se corta con «…»
            t.setHorizontallyScrolling(false);
        }
        info.addView(tvAlbum);
        info.addView(tvDate);
        info.addView(tvPlace);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        ip.setMargins(dp(48), 0, dp(220), dp(32));   // ancho fijo (hasta la hora): el texto no se recorta
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

        progRow = new LinearLayout(ctx);
        progRow.setOrientation(LinearLayout.HORIZONTAL);
        progRow.setGravity(Gravity.CENTER_VERTICAL);
        progRow.setVisibility(View.GONE);
        tvPos = text(16, Gravity.CENTER);
        tvRem = text(16, Gravity.CENTER);
        tvPos.setText("0:00");
        tvRem.setText("-0:00");
        progTrack = new FrameLayout(ctx);
        GradientDrawable tr = new GradientDrawable();
        tr.setColor(0x33FFFFFF);
        tr.setCornerRadius(dp(3));
        progTrack.setBackground(tr);
        progFill = new View(ctx);
        GradientDrawable fl = new GradientDrawable();
        fl.setColor(Color.WHITE);
        progFillBg = fl;
        fl.setCornerRadius(dp(3));
        progFill.setBackground(fl);
        progTrack.addView(progFill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tkp = new LinearLayout.LayoutParams(0, dp(6), 1f);
        tkp.setMargins(dp(12), 0, dp(12), 0);
        progRow.addView(tvPos);
        progRow.addView(progTrack, tkp);
        progRow.addView(tvRem);
        FrameLayout.LayoutParams prp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        prp.setMargins(dp(48), 0, dp(48), dp(12));
        root.addView(progRow, prp);

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
        progRow.bringToFront();
    }

    private static String mmss(int ms) {
        int t = Math.max(0, ms / 1000);
        return (t / 60) + ":" + (t % 60 < 10 ? "0" : "") + (t % 60);
    }

    private final Runnable progTick = new Runnable() {
        public void run() {
            if (!running || !playingVideo || player == null) { progRow.setVisibility(View.GONE); return; }
            try {
                int d = player.getDuration(), c = player.getCurrentPosition();
                if (d > 0) {
                    progRow.setVisibility(View.VISIBLE);
                    tvPos.setText(mmss(c));
                    tvRem.setText("-" + mmss(d - c));
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progFill.getLayoutParams();
                    lp.width = (int) (progTrack.getWidth() * Math.min(1f, c / (float) d));
                    progFill.setLayoutParams(lp);
                }
            } catch (Exception ignored) { }
            h.postDelayed(this, 500);
        }
    };

    private GradientDrawable pill() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x99000000);
        g.setCornerRadius(dp(14));
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
                setPlace(Prefs.showPlace(ctx) ? s.place : null);
                info.setVisibility(View.VISIBLE);
                info.requestLayout();
                info.setAlpha(0f);
                info.animate().alpha(1f).setDuration(600).start();
            }
        };
        if (info.getVisibility() == View.VISIBLE && info.getAlpha() > 0.05f)
            info.animate().alpha(0f).setDuration(300).withEndAction(apply).start();
        else apply.run();
    }

    /** Lugar con un pin blanco delante (dibujo propio, no depende de la fuente del dispositivo). */
    private void setPlace(String place) {
        if (place == null || place.length() == 0) { tvPlace.setVisibility(View.GONE); return; }
        tvPlace.setText(place);
        try {
            android.graphics.drawable.Drawable d = ctx.getResources().getDrawable(R.drawable.pin_white);
            int hh = Math.round(tvPlace.getTextSize() * 1.05f);
            int ww = Math.round(hh * d.getIntrinsicWidth() / (float) d.getIntrinsicHeight());
            d.setBounds(0, 0, ww, hh);
            tvPlace.setCompoundDrawables(d, null, null, null);
            tvPlace.setCompoundDrawablePadding(Math.round(hh * 0.35f));
        } catch (Throwable ignored) { }
        tvPlace.setVisibility(View.VISIBLE);
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

    /** Mantiene la caché de vídeos: se conservan los más usados hasta el tope de espacio y se borra solo lo más antiguo. */
    private void cleanVideos() {
        java.io.File[] fs = videoDir().listFiles();
        if (fs == null) return;
        java.util.Set<String> keep = new java.util.HashSet<String>();
        if (cur != null && cur.item != null) keep.add(cur.item.key());
        for (Slide q : queue) keep.add(q.item.key());
        if (loadingKey != null) keep.add(loadingKey);
        List<java.io.File> old = new ArrayList<java.io.File>();
        long now = System.currentTimeMillis();
        for (java.io.File f : fs) {
            String n = f.getName();
            if (n.endsWith(".part")) {                       // descargas a medias: fuera las de hace rato
                if (now - f.lastModified() > 15 * 60 * 1000L) f.delete();
                continue;
            }
            int d = n.indexOf('.');
            String k = d > 0 ? n.substring(0, d) : n;
            if (!keep.contains(k)) old.add(f);
        }
        java.util.Collections.sort(old, new java.util.Comparator<java.io.File>() {
            public int compare(java.io.File x, java.io.File y) {
                return x.lastModified() < y.lastModified() ? 1 : (x.lastModified() > y.lastModified() ? -1 : 0);
            }
        });
        long total = 0;
        for (java.io.File f : fs) {
            String n = f.getName();
            int d = n.indexOf('.');
            if (!n.endsWith(".part") && keep.contains(d > 0 ? n.substring(0, d) : n)) total += f.length();
        }
        long cap = Prefs.videoMB(ctx) * 1024L * 1024L;
        for (java.io.File f : old) {
            total += f.length();
            if (total > cap) f.delete();
        }
    }

    private java.io.File hdFile(Item it) { return new java.io.File(videoDir(), it.key() + ".mp4"); }
    private java.io.File lqFile(Item it) { return new java.io.File(videoDir(), it.key() + ".lq.mp4"); }

    private static boolean ready(java.io.File f) {
        if (!f.exists() || f.length() <= 10000) return false;
        f.setLastModified(System.currentTimeMillis());      // usado hace poco: se borra el último
        return true;
    }

    /** Alta calidad (1080p, si no 720p). readMs: cuánto esperar a que Google prepare el vídeo. */
    private java.io.File loadHd(Item it, int readMs) { return loadHd(it, readMs, null); }

    private java.io.File loadHd(Item it, int readMs, VideoProxy.Prog prog) {
        java.io.File f = hdFile(it);
        if (ready(f)) { if (prog != null) { prog.len = f.length(); prog.written = f.length(); prog.done = true; } return f; }
        java.io.File part = new java.io.File(videoDir(), it.key() + ".mp4.part");
        for (String v : Prefs.videoSd(ctx) ? new String[]{"=m22", "=m37"} : new String[]{"=m37", "=m22"}) {
            if (Thread.currentThread().isInterrupted()) break;
            if (AlbumFetcher.downloadVideo(it.url + v, part, MAX_VIDEO, readMs, prog) && part.renameTo(f)) { if (prog != null) prog.done = true; return f; }
        }
        part.delete();
        if (prog != null) prog.failed = true;
        return null;
    }

    /** Dirección para ver el vídeo mientras se descarga (con todo lo bajado como búfer). */
    private String streamFor(Item it, VideoProxy.Prog pr) {
        String u = VideoProxy.get().register(new java.io.File(videoDir(), it.key() + ".mp4.part"), hdFile(it), pr);
        return u != null ? u : it.url + "=m37";
    }

    private volatile String lqNote = "";

    /** Versión ligera (360p, si no 720p): pesa poco y llega antes. Se guarda aparte para no confundirla con la buena. */
    private java.io.File loadLq(Item it, int readMs) {
        java.io.File f = lqFile(it);
        if (ready(f)) return f;
        java.io.File part = new java.io.File(videoDir(), it.key() + ".lq.part");
        StringBuilder why = new StringBuilder();
        for (String v : new String[]{"=m18", "=m22"}) {
            if (Thread.currentThread().isInterrupted()) break;
            if (AlbumFetcher.downloadVideo(it.url + v, part, MAX_VIDEO, readMs) && part.renameTo(f)) return f;
            why.append(v).append(": ").append(AlbumFetcher.lastVideoErr).append("  ");
        }
        part.delete();
        lqNote = why.toString().trim();
        return null;
    }

    /** Pase y vídeo suelto: la mejor calidad disponible; si no hay otra, la baja; y por último el original. */
    private java.io.File loadVideo(Item it) { return loadVideo(it, null); }

    private java.io.File loadVideo(Item it, VideoProxy.Prog prog) {
        java.io.File f = loadHd(it, 60000, prog);
        if (f != null) return f;
        f = loadLq(it, 30000);
        if (f != null) return f;
        java.io.File hd = hdFile(it);
        java.io.File part = new java.io.File(videoDir(), it.key() + ".mp4.part");
        if (!Thread.currentThread().isInterrupted()
                && AlbumFetcher.downloadVideo(it.url + "=dv", part, MAX_VIDEO, 30000) && part.renameTo(hd)) return hd;
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

    // fotos ya descargadas (comprimidas) para el visor: se guardan muchas porque ocupan poco
    private final java.util.LinkedHashMap<String, byte[]> bytesCache = new java.util.LinkedHashMap<String, byte[]>();
    private long bytesTotal;
    // un cuarto de la memoria de la app, entre 40 y 100 MB
    private static final long BYTES_MAX = Math.max(40L << 20, Math.min(100L << 20, Runtime.getRuntime().maxMemory() / 4));

    private byte[] photoBytes(Item it) throws Exception {
        String k = it.key();
        synchronized (bytesCache) {
            byte[] d = bytesCache.remove(k);
            if (d != null) { bytesCache.put(k, d); return d; }
        }
        byte[] d = AlbumFetcher.get(it.url + "=w" + w + "-h" + hgt);
        synchronized (bytesCache) {
            byte[] old = bytesCache.put(k, d);
            if (old != null) bytesTotal -= old.length;
            bytesTotal += d.length;
            java.util.Iterator<java.util.Map.Entry<String, byte[]>> e = bytesCache.entrySet().iterator();
            while (bytesTotal > BYTES_MAX && e.hasNext()) {
                java.util.Map.Entry<String, byte[]> x = e.next();
                if (x.getKey().equals(k)) continue;
                bytesTotal -= x.getValue().length;
                e.remove();
            }
        }
        return d;
    }

    private boolean hasBytes(String k) {
        synchronized (bytesCache) { return bytesCache.containsKey(k); }
    }

    private Slide buildPhoto(Item it) {
        try {
            Slide t = new Slide();
            t.item = it;
            t.album = it.album;
            long date = it.ts;
            byte[] data = photoBytes(it);
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
                java.util.concurrent.Future<java.io.File> fut = null;
                final VideoProxy.Prog pr = new VideoProxy.Prog();
                t.prog = pr;
                try {
                    if (ready(hdFile(t.item))) f = hdFile(t.item);
                    else fut = hdEx.submit(new java.util.concurrent.Callable<java.io.File>() {
                        public java.io.File call() { return loadVideo(t.item, pr); }
                    });
                } catch (Exception ignored) { }
                // el vídeo solo entra en el pase cuando ya hay unos segundos descargados: mientras, siguen las fotos
                if (f == null && fut != null) {
                    long t0 = System.currentTimeMillis();
                    while (!fut.isDone() && sess == session && running && System.currentTimeMillis() - t0 < 180000) {
                        if (pr.len > 0 && pr.written >= Math.min(pr.len, 6L * 1024 * 1024)) break;
                        try { Thread.sleep(100); } catch (InterruptedException ie) { return; }
                    }
                    if (fut.isDone()) {
                        try { f = fut.get(); } catch (Exception ignored) { f = null; }
                        fut = null;
                    }
                }
                final java.io.File got = f;
                final java.util.concurrent.Future<java.io.File> hf = fut;
                h.post(new Runnable() {
                    public void run() {
                        if (sess != session || !running) return;
                        t.date = MetaFetcher.formatDate(t.item.ts);
                        if (got != null) t.videoFile = got;
                        else if (hf != null) { t.streamUrl = streamFor(t.item, t.prog); t.hdFut = hf; }
                        else { t.video = false; t.state = 3; onBuilt(t); return; }
                        t.state = 2;
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

    // caché pequeña de fotos ya preparadas para pasar al instante con ◀ ▶ en el visor
    private final java.util.LinkedHashMap<String, Slide> bcache = new java.util.LinkedHashMap<String, Slide>();
    private java.util.concurrent.ExecutorService pex;
    private static final int BCACHE_MAX = 6;

    private Slide bcacheGet(String k) {
        synchronized (bcache) {
            Slide v = bcache.remove(k);
            if (v != null) bcache.put(k, v);
            return v;
        }
    }

    private void bcachePut(String k, Slide v) {
        synchronized (bcache) {
            bcache.remove(k);
            bcache.put(k, v);
            while (bcache.size() > BCACHE_MAX) {
                java.util.Iterator<String> it = bcache.keySet().iterator();
                it.next();
                it.remove();
            }
        }
    }

    /** En el visor, baja por detrás varias fotos por delante y alguna por detrás; las más cercanas ya decodificadas. */
    private void browseAhead(Item from) {
        if (pex == null) return;
        final int ci = indexOfKey(from.key());
        if (ci < 0) return;
        final int sess = session;
        final String centre = from.key();
        int[] offs = new int[30];
        offs[0] = 1; offs[1] = -1; offs[2] = 2;
        for (int n = 3; n < 30; n++) offs[n] = n <= 5 ? n : (n == 6 ? -2 : (n == 7 ? -3 : n - 2));
        // 1) averiguar de golpe cuáles de las próximas son vídeos (si no, cada una pide su dato al pasar)
        final java.util.List<Item> unknown = new java.util.ArrayList<Item>();
        for (int o : offs) {
            int i = ci + o;
            if (i < 0 || i >= items.size()) continue;
            Item it = items.get(i);
            if (MetaFetcher.cachedVideo(ctx, it) == null) unknown.add(it);
        }
        if (!unknown.isEmpty() && !detecting) {
            detecting = true;
            pex.submit(new Runnable() {
                public void run() {
                    try {
                        for (int a = 0; a < unknown.size(); a += 20)
                            MetaFetcher.detectVideos(ctx, unknown.subList(a, Math.min(unknown.size(), a + 20)));
                    } catch (Throwable ignored) { }
                    detecting = false;
                }
            });
        }
        for (final int o : offs) {
            int i = ci + o;
            if (i < 0 || i >= items.size()) continue;
            final Item it = items.get(i);
            final boolean decode = o == 1 || o == -1 || o == 2;
            if (decode ? bcacheGet(it.key()) != null : hasBytes(it.key())) continue;
            pex.submit(new Runnable() {
                public void run() {
                    if (sess != session || !running) return;
                    // si ya te has movido lejos, esta tarea no sirve
                    String now = browseKey;
                    if (now != null && !now.equals(centre)) {
                        int ni = indexOfKey(now);
                        if (ni < 0 || Math.abs(ni - ci) > 2) return;
                    }
                    try {
                        if (decode) {
                            if (bcacheGet(it.key()) != null) return;
                            Slide t = buildPhoto(it);
                            if (t != null && sess == session) {
                                try { t.bmp.prepareToDraw(); } catch (Throwable ignored) { }   // sube la imagen a la GPU ya
                                bcachePut(it.key(), t);
                            }
                        } else if (!hasBytes(it.key())) {
                            photoBytes(it);
                        }
                    } catch (Throwable ignored) { }
                }
            });
        }
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
        if (upLoad != null) { upLoad.cancel(true); upLoad = null; }
        if (loadingKey != null) {   // se salta un vídeo que aún estaba cargando: fuera el letrero
            loadingKey = null;
            h.removeCallbacks(hideToast);
            hideToast.run();
        }
        ex.submit(new Runnable() {
            public void run() {
                boolean vid = false;
                try { vid = Prefs.video(ctx) && MetaFetcher.isVideo(ctx, it); } catch (Exception ignored) { }
                final boolean isVid = vid;
                Slide cached = bcacheGet(it.key());
                if (cached == null) {
                    cached = buildPhoto(it);   // la foto, o la imagen fija del vídeo
                    if (cached != null) bcachePut(it.key(), cached);
                }
                final Slide p = cached;
                h.post(new Runnable() {
                    public void run() {
                        if (!running || sess != session || myGen != gen) return;
                        if (p != null) display(p, !isVid);
                        browseAhead(it);
                        if (!isVid) {
                            if (p == null) flash("No se pudo cargar esta foto", 2500);
                            return;
                        }
                        loadingKey = it.key();
                        flash("Cargando vídeo…", 600000);
                        browseLoad = bex.submit(new Runnable() {
                            public void run() {
                                java.io.File f = null;
                                boolean low = false;
                                java.util.concurrent.Future<java.io.File> hdF = null;
                                final VideoProxy.Prog pr = new VideoProxy.Prog();
                                try {
                                    f = ready(hdFile(it)) ? hdFile(it) : null;
                                    if (f == null) {
                                        // la alta calidad se baja aparte; si no llega enseguida, se empieza con la baja
                                        hdF = hdEx.submit(new java.util.concurrent.Callable<java.io.File>() {
                                            public java.io.File call() { return loadHd(it, 120000, pr); }
                                        });
                                        // se empieza ya en directo con la versión ligera y la buena baja aparte
                                        low = true;
                                    }
                                } catch (RuntimeException ie) {
                                    return;
                                }
                                final java.io.File got = f;
                                final boolean isLow = low;
                                final java.util.concurrent.Future<java.io.File> hdFut = hdF;
                                h.post(new Runnable() {
                                    public void run() {
                                        if (!running || sess != session || myGen != gen) {
                                            if (hdFut != null) hdFut.cancel(true);
                                            return;
                                        }
                                        h.removeCallbacks(hideToast);
                                        hideToast.run();
                                        if (got == null && !isLow) { flash("No se pudo cargar el vídeo", 3000); return; }
                                        Slide v = new Slide();
                                        v.item = it;
                                        v.album = it.album;
                                        v.video = true;
                                        v.videoFile = got;
                                        if (got == null && isLow) { v.prog = pr; v.streamUrl = streamFor(it, pr); }
                                        v.state = 2;
                                        v.date = p != null && p.date != null ? p.date : MetaFetcher.formatDate(it.ts);
                                        v.place = p != null ? p.place : null;
                                        display(v, true);
                                        if (isLow && hdFut != null) {
                                            // se ve en directo (con búfer) y a la vez se guarda en la caché; si el directo falla, se usa el guardado
                                            v.hdFut = hdFut;
                                        }
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    /** Espera a la alta calidad y, cuando llega, sigue el vídeo por donde iba. */
    private void upgradeVideo(final Item it, final java.util.concurrent.Future<java.io.File> hdF,
                              final int sess, final int myGen, final Slide meta) {
        upLoad = vex.submit(new Runnable() {
            public void run() {
                java.io.File f = null;
                try { f = hdF.get(); }
                catch (InterruptedException ie) { hdF.cancel(true); return; }
                catch (Exception ignored) { }
                final java.io.File hd = f;
                h.post(new Runnable() {
                    public void run() {
                        if (!running || sess != session || myGen != gen) return;
                        if (cur == null || cur.item == null || !cur.item.key().equals(it.key())) return;
                        h.removeCallbacks(hideToast);
                        hideToast.run();
                        if (hd == null) { flash("No se pudo cargar el vídeo", 3000); if (!manual()) endVideo(); return; }
                        int pos = 0;
                        try { if (player != null) pos = player.getCurrentPosition(); } catch (Exception ignored) { }
                        Slide v = new Slide();
                        v.item = it;
                        v.album = it.album;
                        v.video = true;
                        v.videoFile = hd;
                        v.state = 2;
                        v.startMs = pos;
                        v.date = meta != null && meta.date != null ? meta.date : MetaFetcher.formatDate(it.ts);
                        v.place = meta != null ? meta.place : null;
                        display(v, false);
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
        boolean vid = playingVideo && player != null && revealed && cur != null && cur.video && panel == null;
        if (vid) {
            if (ctlMode) {
                switch (keyCode) {
                    case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
                    case android.view.KeyEvent.KEYCODE_ENTER:
                    case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:
                    case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE: toggleVideoPause(); return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: seekBy(5000); return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_LEFT: seekBy(-5000); return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_UP: setCtl(false); return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_DOWN: openInfo(); return true;
                    default: break;
                }
            } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                setCtl(true);
                return true;
            }
        }
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!manual()) setPaused(false, false);
                goNext();
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                if (!manual()) setPaused(false, false);
                goPrev();
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:
            case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:
                setPaused(!paused, true);       // OK: pausa / continúa
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_UP:
                hideCurrent();                  // ▲ oculta la foto
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                openInfo();                     // ▼ pausa y muestra la información
                return true;
            default:
                return false;
        }
    }

    boolean infoOpen() { return panel != null; }

    private void openInfo() {
        if (cur == null || cur.item == null || panel != null) return;
        resumeAfterInfo = !paused;
        setPaused(true, false);
        panel = new InfoPanel(ctx, cur.item, cur.album != null && cur.album.length() > 0 ? cur.album : cur.item.album);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        panel.bringToFront();
    }

    /** Cierra la información y sigue por donde estaba (si estaba en marcha). */
    void closeInfo() {
        if (panel == null) return;
        panel.detach();
        panel = null;
        if (resumeAfterInfo) setPaused(false, true);
        resumeAfterInfo = false;
    }

    static boolean isNavKey(int keyCode) {
        return keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;
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
        if (cur == null) return;
        Item it = cur.item;
        int hideAt = manual() ? indexOfKey(it.key()) : -1;
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
        flash("Foto ocultada. La encuentras en el álbum «Ocultas».", 2500);
        if (manual()) {
            // visor: se queda como estaba (parado o en pase) y pasa a la siguiente
            if (items.isEmpty()) {
                h.removeCallbacks(tick);
                releasePlayer();
                playingVideo = false;
                cur = null;
                status.setText("Ya no quedan fotos que ver.\nAtrás para volver al mosaico.");
                return;
            }
            if (browse()) {
                browseTo(items.get(Math.max(0, Math.min(hideAt, items.size() - 1))));
            } else {
                cur = null;
                goNext();
            }
            return;
        }
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
        if (browse()) {
            // paso manual por el álbum: cambio inmediato, sin fundido
            back.setAlpha(1f);
            front.setImageDrawable(null);
            front.setAlpha(0f);
            if (hideVideo) {
                releasePlayer();
                videoHolder.setAlpha(0f);
                videoHolder.setVisibility(View.INVISIBLE);
            }
            info.animate().cancel();
            set(tvAlbum, Prefs.showAlbum(ctx) ? s.album : null);
            set(tvDate, Prefs.showDate(ctx) ? s.date : null);
            setPlace(Prefs.showPlace(ctx) ? s.place : null);
            info.setVisibility(View.VISIBLE);
            info.requestLayout();
            info.setAlpha(1f);
            showingA = !showingA;
            return;
        }
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
    /** true si la vista de vídeo enseña un fotograma verde uniforme (el decodificador aún no ha pintado nada real). */
    private boolean looksGreen() {
        try {
            Bitmap b = videoView.getBitmap(32, 18);
            if (b == null) return false;
            int green = 0, n = 0;
            for (int y = 0; y < b.getHeight(); y++) {
                for (int x = 0; x < b.getWidth(); x++) {
                    int c = b.getPixel(x, y);
                    int r = Color.red(c), g = Color.green(c), bl = Color.blue(c);
                    n++;
                    if (g > r + 40 && g > bl + 40) green++;
                }
            }
            b.recycle();
            return n > 0 && green * 100 / n >= 90;
        } catch (Throwable t) {
            return false;
        }
    }

    private void revealVideo() {
        if (revealed || !running || !playingVideo || cur == null || !cur.video) return;
        long waited = System.currentTimeMillis() - videoStart;
        // no se enseña hasta que el reproductor ha pintado un fotograma real (y no el verde inicial)
        if (!frameRendered && waited < 6000) { h.postDelayed(revealLater, 250); return; }
        if (looksGreen() && greenTries++ < 25) { h.postDelayed(revealLater, 150); return; }
        revealed = true;
        fitVideo(vidW, vidH);
        videoHolder.bringToFront();
        overlaysToFront();
        videoUi(true);
        videoHolder.animate().alpha(1f).setDuration(1000)
            .setInterpolator(new AccelerateDecelerateInterpolator()).withLayer().start();
    }

    private void playVideo(final Slide s) {
        playingVideo = true;
        revealed = false;
        frameRendered = false;
        greenTries = 0;
        videoStart = System.currentTimeMillis();
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
            if (s.videoFile != null) mp.setDataSource(s.videoFile.getAbsolutePath());
            else {
                java.util.Map<String, String> hd = new java.util.HashMap<String, String>();
                hd.put("User-Agent", AlbumFetcher.UA);
                mp.setDataSource(ctx, android.net.Uri.parse(s.streamUrl), hd);
            }
            mp.setSurface(surface);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer p) {
                    if (p != player) return;
                    h.removeCallbacks(videoTimeout);
                    if (!Prefs.sound(ctx)) p.setVolume(0f, 0f);
                    if (s.startMs > 0) { try { p.seekTo(s.startMs); } catch (Exception ignored) { } }
                    h.removeCallbacks(progTick);
                    h.post(progTick);
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
                        frameRendered = true;
                        h.removeCallbacks(revealLater);
                        h.postDelayed(revealLater, 250);   // da tiempo a que la vista pinte ese fotograma
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
                    if (p == player && s.streamUrl != null) {
                        if (s.hdFut != null) {
                            java.util.concurrent.Future<java.io.File> f0 = s.hdFut;
                            s.hdFut = null;
                            upgradeVideo(s.item, f0, session, gen, s);
                        } else endVideo();
                        return true;
                    }
                    if (p == player) {
                        flash("No se pudo reproducir este vídeo (error " + what + "/" + extra + ")", 4000);
                        endVideo();
                    }
                    return true;
                }
            });
            h.postDelayed(videoTimeout, s.streamUrl != null ? 90000 : 30000);
            mp.prepareAsync();
        } catch (Exception e) {
            endVideo();
        }
    }

    private void fitVideo(int vw, int vh) {
        if (player != null) {
            try {
                int pw = player.getVideoWidth(), ph = player.getVideoHeight();
                if (pw > 0 && ph > 0) { vw = pw; vh = ph; }
            } catch (Throwable ignored) {}
        }
        if (vw <= 0 || vh <= 0) return;
        float holderW = videoHolder.getWidth(), holderH = videoHolder.getHeight();
        if (holderW <= 0 || holderH <= 0) return;
        float scale = Math.min(holderW / vw, holderH / vh);
        int w = Math.max(1, Math.round(vw * scale)), hh = Math.max(1, Math.round(vh * scale));
        // se ajusta el tamaño de la propia vista (centrada) en vez de usar una matriz: el resto queda negro
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, hh, Gravity.CENTER);
        videoView.setTransform(null);
        videoView.setLayoutParams(lp);
    }

    private void endVideo() {
        h.removeCallbacks(videoTimeout);
        videoWaitingSurface = null;
        if (!playingVideo) return;
        if (paused) return;   // en pausa se queda en el vídeo
        goNext();
    }

    /** Con un vídeo en marcha desaparecen hora, fecha, álbum, etc. */
    private void videoUi(boolean on) {
        if (on) {
            info.animate().cancel();
            info.setVisibility(View.INVISIBLE);
            clock.setVisibility(View.INVISIBLE);
            shade.setVisibility(View.INVISIBLE);
        } else {
            clock.setVisibility(Prefs.clock(ctx) ? View.VISIBLE : View.GONE);
            boolean anyText = Prefs.clock(ctx) || Prefs.showAlbum(ctx) || Prefs.showDate(ctx) || Prefs.showPlace(ctx);
            shade.setVisibility(anyText ? View.VISIBLE : View.GONE);
        }
    }

    private void setCtl(boolean on) {
        ctlMode = on;
        if (progFillBg != null) progFillBg.setColor(on ? 0xFF3D8BFD : Color.WHITE);
        if (progFill != null) progFill.invalidate();
    }

    private boolean vidPaused;

    /** Pausa o reanuda solo el vídeo (el pase no se toca). */
    private void toggleVideoPause() {
        if (player == null) return;
        try {
            if (vidPaused) {
                vidPaused = false;
                player.start();
                int left = Math.max(5000, player.getDuration() - player.getCurrentPosition());
                h.removeCallbacks(videoTimeout);
                h.postDelayed(videoTimeout, left + 15000L);
            } else {
                vidPaused = true;
                player.pause();
                h.removeCallbacks(videoTimeout);
            }
        } catch (Exception ignored) { }
    }

    private void seekBy(int ms) {
        try {
            int d = player.getDuration();
            int c = player.getCurrentPosition();
            int max = d > 0 ? d - 500 : Integer.MAX_VALUE;
            VideoProxy.Prog pg = cur != null ? cur.prog : null;
            if (pg != null && !pg.done && pg.len > 0 && d > 0)   // solo hasta donde ya está bajado
                max = Math.min(max, (int) (d * (pg.written / (double) pg.len)) - 4000);
            int target = Math.max(0, Math.min(max, c + ms));
            if (ms > 0 && target <= c) return;
            player.seekTo(target);
        } catch (Exception ignored) { }
        h.removeCallbacks(progTick);
        h.postDelayed(progTick, 150);
    }

    private void releasePlayer() {
        vidPaused = false;
        if (ctlMode) setCtl(false);
        if (revealed) videoUi(false);
        h.removeCallbacks(progTick);
        if (progRow != null) progRow.setVisibility(View.GONE);
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
