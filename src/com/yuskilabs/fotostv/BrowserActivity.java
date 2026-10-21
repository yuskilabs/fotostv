package com.yuskilabs.fotostv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explorador: mosaico de álbumes → mosaico de fotos → pantalla completa. */
public class BrowserActivity extends Activity {
    private static class Entry {
        String title = "";
        String url = "";
        boolean isAll;
        List<Item> items = new ArrayList<Item>();
        List<Item> view;          // lista ordenada (null = tal cual la da Google)
        String order = "";       // random | asc | desc | vacío
        Item cover;

        List<Item> shown() { return view != null ? view : items; }
    }

    /** Casilla con proporción fija (alto = ancho × ratio) para las cuadrículas. */
    private static class Tile extends FrameLayout {
        final float ratio;
        ImageView img;
        TextView name, count, badge;
        View shade;

        Tile(android.content.Context c, float ratio) {
            super(c);
            this.ratio = ratio;
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) (w * ratio), MeasureSpec.EXACTLY));
        }
    }

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final ExecutorService flagExec = Executors.newSingleThreadExecutor();
    private final List<Entry> entries = new ArrayList<Entry>();
    private final LinkedHashSet<Item> flagPending = new LinkedHashSet<Item>();
    private final Set<String> flagTried = new HashSet<String>();
    private FrameLayout root;
    private GridView albumGrid, photoGrid;
    private Entry current;
    private Thumbs thumbs;
    private int level;            // 0 = álbumes, 1 = fotos de un álbum
    private int albumPos;
    private int W, margin, gap, albumTileW, albumTileH, photoTile;
    private boolean destroyed, flagBusy, loaded;
    private int prefetched = -1;
    private static final int AHEAD = 150;
    private android.widget.Button[] ordBtn = new android.widget.Button[3];
    private android.widget.Button playBtn;
    private static final String[] ORD_KEY = {"random", "asc", "desc"};
    private static final String[] ORD_LABEL = {"Aleatorio", "Más antiguas primero", "Más recientes primero"};

    private static final int ALBUM_COLS = 4, PHOTO_COLS = 6;

    // ------------------------------------------------------------ ciclo de vida
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = Math.max(dm.widthPixels, dm.heightPixels);
        margin = (int) (W * 0.035f);
        gap = Ui.dp(this, 10);
        albumTileW = (W - 2 * margin - (ALBUM_COLS - 1) * gap) / ALBUM_COLS;
        albumTileH = (int) (albumTileW * 0.66f);
        photoTile = (W - 2 * margin - (PHOTO_COLS - 1) * gap) / PHOTO_COLS;
        thumbs = Thumbs.get(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F1D2E);
        setContentView(root);
        showMessage("Cargando álbumes…");
        loadEntries();
    }

    @Override protected void onResume() {
        super.onResume();
        // al volver de la pantalla completa, el mosaico se coloca en la última foto vista
        if (level == 1 && photoGrid != null && current != null && Session.lastKey != null) {
            for (int i = 0; i < current.shown().size(); i++) {
                if (current.shown().get(i).key().equals(Session.lastKey)) {
                    photoGrid.setSelection(i);
                    break;
                }
            }
        }
        Session.lastKey = null;
    }

    @Override protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        flagExec.shutdownNow();
        super.onDestroy();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        thumbs.trimMemory();
    }

    @Override public void onTrimMemory(int lvl) {
        super.onTrimMemory(lvl);
        if (lvl >= TRIM_MEMORY_RUNNING_LOW) thumbs.trimMemory();
    }

    @Override public void onBackPressed() {
        if (level == 1) {
            // primer «atrás»: sube a los botones; segundo: vuelve a los álbumes
            if (photoGrid != null && photoGrid.hasFocus() && ordBtn[0] != null) ordBtn[0].requestFocus();
            else showAlbums();
        } else super.onBackPressed();
    }

    // ------------------------------------------------------------ datos
    private void loadEntries() {
        bg.submit(new Runnable() {
            public void run() {
                final List<Entry> list = new ArrayList<Entry>();
                for (Prefs.Album al : Prefs.albums(BrowserActivity.this)) {
                    if (destroyed) return;
                    AlbumFetcher.AlbumData d = Prefs.albumCache(BrowserActivity.this, al.url);
                    if (d == null) {
                        try {
                            d = AlbumFetcher.fetchAlbum(al.url);
                            Prefs.saveAlbumCache(BrowserActivity.this, al.url, d);
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    list.add(makeEntry(al, d));
                }
                final List<Entry> fin = withAll(list);
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        entries.clear();
                        entries.addAll(fin);
                        loaded = true;
                        if (entries.isEmpty()) {
                            showMessage("No hay álbumes.\nAñádelos desde la web de ajustes de FotosTV.");
                        } else {
                            showAlbums();
                            refreshInBackground();
                        }
                    }
                });
            }
        });
    }

    private Entry makeEntry(Prefs.Album al, AlbumFetcher.AlbumData d) {
        Entry e = new Entry();
        e.url = al.url;
        e.title = al.name.length() > 0 ? al.name : (d.title.length() > 0 ? d.title : "Álbum");
        for (Item it : d.items) {
            it.album = e.title;
            if (it.akey.length() == 0) it.akey = d.key;
        }
        e.items = d.items;
        pickCover(e);
        return e;
    }

    /** Portada del álbum: una foto al azar (la portada elegida en Google no viaja en el enlace). */
    private void pickCover(Entry e) {
        e.cover = null;
        if (e.items.isEmpty()) return;
        java.util.Random r = new java.util.Random();
        for (int t = 0; t < 8; t++) {
            Item it = e.items.get(r.nextInt(e.items.size()));
            Boolean v = MetaFetcher.cachedVideo(this, it);
            e.cover = it;
            if (v == null || !v.booleanValue()) return;
        }
    }

    private void applyOrder(Entry e) {
        if (e.order.length() == 0) { e.view = null; return; }
        List<Item> l = new ArrayList<Item>(e.items);
        if ("random".equals(e.order)) {
            Collections.shuffle(l);
        } else {
            final boolean asc = "asc".equals(e.order);
            Collections.sort(l, new Comparator<Item>() {
                public int compare(Item x, Item y) {
                    if (x.ts == 0 && y.ts == 0) return 0;
                    if (x.ts == 0) return 1;
                    if (y.ts == 0) return -1;
                    return asc ? Long.compare(x.ts, y.ts) : Long.compare(y.ts, x.ts);
                }
            });
        }
        e.view = l;
    }

    /** Añade delante la entrada «Todos» (todas las fotos de todos los álbumes). */
    private List<Entry> withAll(List<Entry> list) {
        List<Entry> out = new ArrayList<Entry>();
        if (list.size() > 1) {
            Entry all = new Entry();
            all.isAll = true;
            all.title = "Todos los álbumes";
            for (Entry e : list) all.items.addAll(e.items);
            pickCover(all);
            out.add(all);
        }
        out.addAll(list);
        return out;
    }

    /** Actualiza los álbumes desde Google en segundo plano, sin bloquear el mosaico. */
    private void refreshInBackground() {
        bg.submit(new Runnable() {
            public void run() {
                final List<Entry> fresh = new ArrayList<Entry>();
                for (Prefs.Album al : Prefs.albums(BrowserActivity.this)) {
                    if (destroyed) return;
                    try {
                        AlbumFetcher.AlbumData d = AlbumFetcher.fetchAlbum(al.url);
                        Prefs.saveAlbumCache(BrowserActivity.this, al.url, d);
                        fresh.add(makeEntry(al, d));
                    } catch (Exception ignored) { }
                }
                if (fresh.isEmpty()) return;
                h.post(new Runnable() {
                    public void run() { applyFresh(fresh); }
                });
            }
        });
    }

    private void applyFresh(List<Entry> fresh) {
        if (destroyed) return;
        boolean changed = false;
        for (Entry f : fresh) {
            for (Entry e : entries) {
                if (!e.isAll && e.url.equals(f.url) && e.items.size() != f.items.size()) {
                    e.items = f.items;        // se cambia la lista entera, no se modifica la anterior
                    applyOrder(e);
                    if (e.cover == null) pickCover(e);
                    changed = true;
                }
            }
        }
        if (!changed) return;
        for (Entry e : entries) {
            if (e.isAll) {
                List<Item> all = new ArrayList<Item>();
                for (Entry x : entries) if (!x.isAll) all.addAll(x.items);
                e.items = all;
                applyOrder(e);
                if (e.cover == null) pickCover(e);
            }
        }
        if (level == 0 && albumGrid != null) ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
        if (level == 1 && photoGrid != null) ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
    }

    // ------------------------------------------------------------ pantallas
    private void showMessage(String msg) {
        level = 0;
        albumGrid = null;
        photoGrid = null;
        root.removeAllViews();
        TextView t = text(msg, 26, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        root.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    private void showAlbums() {
        level = 0;
        photoGrid = null;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(margin, Ui.dp(this, 22), margin, 0);
        TextView title = text("FotosTV  ·  Álbumes", 30, Color.WHITE);
        col.addView(title, hp);
        TextView sub = text("OK para abrir  ·  Atrás para salir", 16, 0xFF9FB3C8);
        col.addView(sub, hp);

        albumGrid = makeGrid(ALBUM_COLS);
        albumGrid.setAdapter(new AlbumAdapter());
        albumGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { openEntry(pos); }
        });
        col.addView(albumGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        albumGrid.setSelection(Math.min(albumPos, Math.max(0, entries.size() - 1)));
        albumGrid.requestFocus();
    }

    private void openEntry(int pos) {
        if (pos < 0 || pos >= entries.size()) return;
        albumPos = pos;
        current = entries.get(pos);
        showPhotos();
    }

    private void showPhotos() {
        level = 1;
        albumGrid = null;
        synchronized (flagPending) { flagPending.clear(); }
        flagTried.clear();
        prefetched = -1;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(margin, Ui.dp(this, 22), margin, 0);
        col.addView(text(current.title + "  ·  " + current.items.size() + " elementos", 28, Color.WHITE), hp);
        TextView sub = text("Ordenar con los 3 primeros botones  ·  OK en una foto para verla  ·  Atrás: botones, y otra vez: álbumes", 15, 0xFF9FB3C8);
        col.addView(sub, hp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        for (int i = 0; i < 3; i++) {
            final String key = ORD_KEY[i];
            ordBtn[i] = Ui.button(this, ORD_LABEL[i], new View.OnClickListener() {
                public void onClick(View v) { setOrder(key); }
            });
            ordBtn[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.addView(ordBtn[i]);
        }
        playBtn = Ui.button(this, "▶  Pase de diapositivas", play());
        playBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(playBtn);
        refreshOrderButtons();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(margin, 0, margin, 0);
        col.addView(row, rp);

        photoGrid = makeGrid(PHOTO_COLS);
        photoGrid.setAdapter(new PhotoAdapter());
        photoGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { openViewer(pos); }
        });
        photoGrid.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            public void onScrollStateChanged(android.widget.AbsListView v, int st) { }
            public void onScroll(android.widget.AbsListView v, int first, int visible, int total) {
                // cada vez que se mueve la vista se revisa qué miniaturas aún no tienen marca de vídeo
                scheduleFlags();
            }
        });
        col.addView(photoGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (current.items.isEmpty()) {
            TextView t = text("Este álbum no tiene fotos.", 22, Color.WHITE);
            root.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        }
        photoGrid.requestFocus();
    }

    private void setOrder(String key) {
        if (current == null) return;
        current.order = key;
        applyOrder(current);
        prefetched = -1;
        refreshOrderButtons();
        if (photoGrid != null) {
            ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
            photoGrid.setSelection(0);
        }
    }

    private void refreshOrderButtons() {
        for (int i = 0; i < 3; i++)
            if (ordBtn[i] != null)
                ordBtn[i].setText((current != null && ORD_KEY[i].equals(current.order) ? "\u2713  " : "") + ORD_LABEL[i]);
    }

    private View.OnClickListener play() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                if (current == null || current.items.isEmpty()) return;
                Session.items = current.shown();
                Session.order = current.order.length() == 0 ? "random" : "keep";   // sin ordenar: aleatorio
                Session.manual = false;
                Session.startKey = null;
                Session.lastKey = null;
                startActivity(new Intent(BrowserActivity.this, PlayerActivity.class));
            }
        };
    }

    private void openViewer(int pos) {
        if (current == null || pos < 0 || pos >= current.shown().size()) return;
        Session.items = current.shown();
        Session.order = "keep";
        Session.manual = true;
        Session.startKey = current.shown().get(pos).key();
        Session.lastKey = null;
        startActivity(new Intent(this, PlayerActivity.class));
    }

    // ------------------------------------------------------------ cuadrícula
    private GridView makeGrid(int cols) {
        GridView g = new GridView(this);
        g.setNumColumns(cols);
        g.setHorizontalSpacing(gap);
        g.setVerticalSpacing(gap);
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setPadding(margin, gap, margin, margin);
        g.setClipToPadding(false);
        g.setVerticalScrollBarEnabled(false);
        g.setCacheColorHint(Color.TRANSPARENT);
        g.setFocusable(true);
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x22FFFFFF);
        sel.setStroke(Ui.dp(this, 4), Color.WHITE);
        sel.setCornerRadius(Ui.dp(this, 6));
        g.setSelector(sel);
        g.setDrawSelectorOnTop(true);
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        return t;
    }

    private static int r32(int v) { return ((v + 31) / 32) * 32; }

    private class AlbumAdapter extends BaseAdapter {
        public int getCount() { return entries.size(); }
        public Object getItem(int i) { return entries.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Tile t = cv instanceof Tile ? (Tile) cv : newAlbumTile();
            Entry e = entries.get(pos);
            t.name.setText(e.isAll ? "★  " + e.title : e.title);
            t.count.setText(e.items.size() + " elementos");
            if (e.items.isEmpty()) thumbs.clearView(t.img);
            else thumbs.load(t.img, (e.cover != null ? e.cover : e.items.get(0)).url, r32(albumTileW), r32(albumTileH));
            return t;
        }
    }

    private Tile newAlbumTile() {
        Tile t = new Tile(this, 0.66f);
        t.setBackgroundColor(0xFF1B2D44);
        t.img = new ImageView(this);
        t.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(t.img, new FrameLayout.LayoutParams(-1, -1));
        t.shade = new View(this);
        t.shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xCC000000, 0x00000000}));
        t.addView(t.shade, new FrameLayout.LayoutParams(-1, albumTileH / 2, Gravity.BOTTOM));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        t.name = text("", 20, Color.WHITE);
        t.name.setSingleLine(true);
        t.name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.name.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        t.count = text("", 14, 0xFFD0DBE8);
        t.count.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        box.addView(t.name);
        box.addView(t.count);
        t.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return t;
    }

    private class PhotoAdapter extends BaseAdapter {
        public int getCount() { return current == null ? 0 : current.shown().size(); }
        public Object getItem(int i) { return current.shown().get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Tile t = cv instanceof Tile ? (Tile) cv : newPhotoTile();
            Item it = current.shown().get(pos);
            thumbs.load(t.img, it.url, r32(photoTile), r32(photoTile));
            prefetchAhead(pos);
            return t;
        }
    }

    /** Va bajando al disco las miniaturas que vienen detrás de la que se está viendo. */
    private void prefetchAhead(int pos) {
        List<Item> l = current.shown();
        int to = Math.min(l.size() - 1, pos + AHEAD);
        for (int i = Math.max(prefetched + 1, pos + 1); i <= to; i++)
            thumbs.prefetch(l.get(i).url, r32(photoTile), r32(photoTile));
        if (to > prefetched) prefetched = to;
    }

    private Tile newPhotoTile() {
        Tile t = new Tile(this, 1f);
        t.setBackgroundColor(0xFF1B2D44);
        t.img = new ImageView(this);
        t.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(t.img, new FrameLayout.LayoutParams(-1, -1));
        // marca de vídeo: círculo oscuro con ▶ abajo a la izquierda
        t.badge = new TextView(this);
        t.badge.setText("▶");
        t.badge.setTextColor(Color.WHITE);
        t.badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.badge.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xB3000000);
        circle.setStroke(Ui.dp(this, 2), Color.WHITE);
        t.badge.setBackground(circle);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36),
                Gravity.BOTTOM | Gravity.START);
        bp.setMargins(Ui.dp(this, 8), 0, 0, Ui.dp(this, 8));
        t.addView(t.badge, bp);
        t.badge.setVisibility(View.GONE);
        return t;
    }

    // ------------------------------------------------------------ marcas de vídeo (por lotes)
    private final Runnable flagRun = new Runnable() {
        public void run() { runFlags(); }
    };

    private void wantFlag(Item it) {
        if (it.id.length() == 0 || it.akey.length() == 0) return;
        if (!flagTried.add(it.key())) return;
        synchronized (flagPending) { flagPending.add(it); }
        scheduleFlags();
    }

    private void scheduleFlags() {
        h.removeCallbacks(flagRun);
        h.postDelayed(flagRun, 400);
    }

    private void runFlags() {
        if (destroyed || photoGrid == null || current == null) return;
        if (flagBusy) { h.postDelayed(flagRun, 400); return; }
        // solo los que están a la vista (con un margen); los demás se pedirán al verlos
        int first = Math.max(0, photoGrid.getFirstVisiblePosition() - PHOTO_COLS * 2);
        int last = Math.min(current.items.size() - 1, photoGrid.getLastVisiblePosition() + PHOTO_COLS * 2);
        Set<String> vis = new HashSet<String>();
        for (int i = first; i <= last; i++) vis.add(current.items.get(i).key());
        final List<Item> batch = new ArrayList<Item>();
        List<Item> keep = new ArrayList<Item>();
        synchronized (flagPending) {
            for (Item it : flagPending) {
                if (!vis.contains(it.key())) flagTried.remove(it.key());
                else if (batch.size() < 20) batch.add(it);
                else keep.add(it);
            }
            flagPending.clear();
            flagPending.addAll(keep);
        }
        if (!keep.isEmpty()) h.postDelayed(flagRun, 600);
        if (batch.isEmpty()) return;
        flagBusy = true;
        flagExec.submit(new Runnable() {
            public void run() {
                try { MetaFetcher.detectVideos(BrowserActivity.this, batch); } catch (Throwable ignored) { }
                h.post(new Runnable() {
                    public void run() {
                        flagBusy = false;
                        if (!destroyed) updateBadges();
                    }
                });
            }
        });
    }

    /** Pone o quita la marca de vídeo de las casillas visibles sin recargar las miniaturas. */
    private void updateBadges() {
        if (photoGrid == null || current == null) return;
        int first = photoGrid.getFirstVisiblePosition();
        for (int i = 0; i < photoGrid.getChildCount(); i++) {
            View v = photoGrid.getChildAt(i);
            int pos = first + i;
            if (!(v instanceof Tile) || pos >= current.items.size()) continue;
            Boolean vid = MetaFetcher.cachedVideo(this, current.items.get(pos));
            ((Tile) v).badge.setVisibility(vid != null && vid.booleanValue() ? View.VISIBLE : View.GONE);
        }
    }
}
