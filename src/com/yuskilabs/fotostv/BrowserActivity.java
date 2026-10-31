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
        int kind;                 // 0 álbum normal, 1 Todas, 2 Favoritos, 3 Ocultas, 4 temporal de invitado
        String gid = "";          // (temporal) identificador
        long expires;             // (temporal) cuándo caduca
        List<Item> src = new ArrayList<Item>();     // todo lo del álbum
        List<Item> items = new ArrayList<Item>();   // lo que se ve (sin las ocultas, salvo en «Ocultas»)
        List<Item> view;          // lista ordenada (null = tal cual la da Google)
        String order = "";       // random | asc | desc | vacío
        Item cover;

        boolean onlyVideos;       // filtro «Vídeos»
        List<Item> vids = new ArrayList<Item>();

        List<Item> shown() { return onlyVideos ? vids : (view != null ? view : items); }
    }

    /** Casilla con proporción fija (alto = ancho × ratio) para las cuadrículas. */
    private static class Tile extends FrameLayout {
        final float ratio;
        ImageView img;
        TextView name, count, badge;
        ImageView fav;
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
    private List<Entry> real = new ArrayList<Entry>();
    private Entry allE, favE, hidE;
    private java.util.Set<String> favSet = new HashSet<String>();
    private InfoPanel infoPanel;
    private GridView albumGrid, photoGrid;
    private Entry current;
    private Thumbs thumbs;
    private int level;            // 0 = álbumes, 1 = fotos de un álbum
    private int albumPos;
    private int W, margin, gap, albumTileW, albumTileH, photoTile;
    private boolean destroyed, flagBusy, loaded;
    private int prefetched = -1;
    private static final int AHEAD = 150;
    private android.widget.Button orderBtn, fitBtn, kindBtn;
    private TextView countView;
    private int scanGen, scanDone, scanTotal;
    private final ExecutorService scanExec = Executors.newSingleThreadExecutor();
    private android.widget.Button playBtn;

    private int albumCols, photoCols, photoTileH;
    private String photoShape = "square";
    private float photoRatio = 1f;
    private android.widget.Button colsMinus, colsPlus, shapeBtn;
    private String refocus;
    private static final int[] ALBUM_COLS_RANGE = {2, 10};
    private static final int[] PHOTO_COLS_RANGE = {2, 14};
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private void computeLayout() {
        albumCols = Prefs.albumCols(this);
        photoCols = Prefs.photoCols(this);
        photoShape = Prefs.photoShape(this);
        photoRatio = "wide".equals(photoShape) ? 0.6667f : ("full".equals(photoShape) ? 0.75f : 1f);
        albumTileW = (W - 2 * margin - (albumCols - 1) * gap) / albumCols;
        albumTileH = (int) (albumTileW * 0.66f);
        photoTile = (W - 2 * margin - (photoCols - 1) * gap) / photoCols;
        photoTileH = (int) (photoTile * photoRatio);
    }

    private android.widget.Button sbtn(String label, int icon, View.OnClickListener l) {
        android.widget.Button b = Ui.button(this, label, l);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (icon != 0) Ui.icon(b, icon);
        b.setPadding(Ui.dp(this, 9), Ui.dp(this, 6), Ui.dp(this, 9), Ui.dp(this, 6));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, Ui.dp(this, 4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    /** Control «−  Columnas: N  +» con dos botones y la etiqueta en medio (sin foco). */
    private LinearLayout colsControl(final String key, final int[] range, final int cur, final String prefKey,
                                     final boolean albums) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Button minus = sbtn("", R.drawable.ic_remove, null);
        android.widget.Button plus = sbtn("", R.drawable.ic_add, null);
        minus.setCompoundDrawablePadding(0);
        plus.setCompoundDrawablePadding(0);
        minus.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        plus.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        LinearLayout.LayoutParams z = new LinearLayout.LayoutParams(-2, -2);
        z.setMargins(0, 0, 0, 0);
        minus.setLayoutParams(z);
        LinearLayout.LayoutParams z2 = new LinearLayout.LayoutParams(-2, -2);
        z2.setMargins(0, 0, 0, 0);
        plus.setLayoutParams(z2);
        TextView lab = text("Columnas: " + cur, 13, 0xFFD0DBE8);
        lab.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        lab.setFocusable(false);
        minus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { stepCols(range, cur, -1, prefKey, albums, "minus"); }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { stepCols(range, cur, 1, prefKey, albums, "plus"); }
        });
        box.addView(minus);
        box.addView(lab);
        box.addView(plus);
        if (key.equals("album")) { colsMinus = minus; colsPlus = plus; }
        else { colsMinus = minus; colsPlus = plus; }
        return box;
    }

    private void stepCols(int[] range, int cur, int d, String prefKey, boolean albums, String focusKey) {
        int n = Math.max(range[0], Math.min(range[1], cur + d));
        Prefs.sp(this).edit().putInt(prefKey, n).commit();
        refocus = focusKey;
        if (albums) showAlbums(); else showPhotos(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
    }

    private ImageView logoView(int hdp) {
        ImageView v = new ImageView(this);
        v.setImageResource(R.drawable.logo_full);
        v.setAdjustViewBounds(true);
        v.setLayoutParams(new LinearLayout.LayoutParams(-2, Ui.dp(this, hdp)));
        return v;
    }

    /** Cabecera común de las dos pantallas (misma altura, logo y separador gris). */
    private LinearLayout header() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(logoView(44));
        View sep = new View(this);
        sep.setBackgroundColor(0xFF6B7C90);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(this, 2), Ui.dp(this, 32));
        sp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        head.addView(sep, sp);
        return head;
    }

    private LinearLayout.LayoutParams headParams() {
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        hp.setMargins(margin, Ui.dp(this, 10), margin, 0);
        return hp;
    }

    private static String shapeLabel(String sh) {
        return "wide".equals(sh) ? "Miniaturas: apaisadas" : ("full".equals(sh) ? "Miniaturas: completas" : "Miniaturas: cuadradas");
    }

    // ------------------------------------------------------------ ciclo de vida
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = Math.max(dm.widthPixels, dm.heightPixels);
        margin = (int) (W * 0.035f);
        gap = Ui.dp(this, 10);
        computeLayout();
        thumbs = Thumbs.get(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F1D2E);
        setContentView(root);
        showMessage("Cargando álbumes…");
        loadEntries();
    }

    @Override protected void onResume() {
        super.onResume();
        h.removeCallbacks(guestTick);
        h.postDelayed(guestTick, 30000);
        Guest.purgeExpired(this);
        if (loaded) {
            int fp = photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0;
            refreshAfterChange(fp);       // por si se ha ocultado o marcado algo en el visor
        }
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

    @Override protected void onPause() {
        h.removeCallbacks(guestTick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        flagExec.shutdownNow();
        scanExec.shutdownNow();
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
            if (photoGrid != null && photoGrid.hasFocus() && playBtn != null) playBtn.requestFocus();
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
                final List<Entry> fin = sortEntries(list);
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        real = new ArrayList<Entry>(fin);
                        compose();
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
        e.src = d.items;
        e.items = d.items;
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
    /** Fecha de captura de la foto más reciente del álbum (0 si no se conoce). */
    private static long lastTs(Entry e) {
        long m = 0;
        for (Item it : e.items) if (it.ts > m) m = it.ts;
        return m;
    }

    /** Aplica el orden elegido: el de la lista (manual) o por la fecha de la última foto de cada álbum. */
    private List<Entry> sortEntries(List<Entry> list) {
        final String mode = Prefs.albumSort(this);
        if (!"newest".equals(mode) && !"oldest".equals(mode)) return list;
        final boolean newest = "newest".equals(mode);
        final java.util.Map<Entry, Long> ts = new java.util.IdentityHashMap<Entry, Long>();
        for (Entry e : list) ts.put(e, lastTs(e));
        List<Entry> l = new ArrayList<Entry>(list);
        Collections.sort(l, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                long x = ts.get(a), y = ts.get(b);
                if (x == 0 && y == 0) return 0;
                if (x == 0) return 1;      // sin fecha, al final
                if (y == 0) return -1;
                return newest ? Long.compare(y, x) : Long.compare(x, y);
            }
        });
        return l;
    }

    /** Reordena sin barajar de nuevo lo que ya estaba barajado. */
    private void reapply(Entry e) {
        if ("random".equals(e.order) && e.view != null) {
            java.util.Set<String> now = new HashSet<String>();
            for (Item x : e.items) now.add(x.key());
            java.util.Set<String> have = new HashSet<String>();
            List<Item> nv = new ArrayList<Item>();
            for (Item x : e.view) if (now.contains(x.key()) && have.add(x.key())) nv.add(x);
            for (Item x : e.items) if (have.add(x.key())) nv.add(x);
            e.view = nv;
        } else applyOrder(e);
    }

    private static List<Item> visible(List<Item> src, java.util.Set<String> hid) {
        List<Item> out = new ArrayList<Item>(src.size());
        for (Item it : src) if (!hid.contains(it.key())) out.add(it);
        return out;
    }

    /** Rehace las listas que se ven: quita las ocultas, y arma Todas, Favoritos y Ocultas. */
    private final java.util.Map<String, Entry> guestMap = new java.util.HashMap<String, Entry>();

    private void compose() {
        java.util.Set<String> hid = Prefs.hidden(this);
        favSet = Prefs.favs(this);
        for (Entry e : real) {
            e.items = visible(e.src, hid);
            reapply(e);
            if (e.cover == null || hid.contains(e.cover.key())) pickCover(e);
        }
        java.util.LinkedHashMap<String, Item> vis = new java.util.LinkedHashMap<String, Item>();
        java.util.LinkedHashMap<String, Item> hidden = new java.util.LinkedHashMap<String, Item>();
        for (Entry e : real) {
            for (Item it : e.src) {
                if (hid.contains(it.key())) { if (!hidden.containsKey(it.key())) hidden.put(it.key(), it); }
                else if (!vis.containsKey(it.key())) vis.put(it.key(), it);
            }
        }
        if (allE == null) { allE = new Entry(); allE.kind = 1; allE.title = "Todas"; }
        allE.items = new ArrayList<Item>(vis.values());
        reapply(allE);
        if (allE.cover == null || hid.contains(allE.cover.key())) pickCover(allE);
        List<Item> favs = new ArrayList<Item>();
        for (Item it : vis.values()) if (favSet.contains(it.key())) favs.add(it);
        if (favs.isEmpty()) favE = null;
        else {
            if (favE == null) { favE = new Entry(); favE.kind = 2; favE.title = "Favoritos"; }
            favE.items = favs;
            reapply(favE);
            if (favE.cover == null || !favSet.contains(favE.cover.key())) pickCover(favE);
        }
        if (hidden.isEmpty()) hidE = null;
        else {
            if (hidE == null) { hidE = new Entry(); hidE.kind = 3; hidE.title = "Ocultas"; }
            hidE.items = new ArrayList<Item>(hidden.values());
            reapply(hidE);
            if (hidE.cover == null || !hid.contains(hidE.cover.key())) pickCover(hidE);
        }
        entries.clear();
        // temporales de invitado: siempre al principio y aparte de todo lo demás
        java.util.Set<String> live = new HashSet<String>();
        for (Guest.G g : Guest.list(this)) {
            live.add(g.id);
            Entry ge = guestMap.get(g.id);
            if (ge == null) {
                AlbumFetcher.AlbumData d = Guest.data(this, g);
                if (d == null) continue;
                ge = new Entry();
                ge.kind = 4;
                ge.gid = g.id;
                ge.url = g.url;
                for (Item it : d.items) { if (it.akey.length() == 0) it.akey = d.key; }
                ge.src = d.items;
                ge.items = d.items;
                pickCover(ge);
                guestMap.put(g.id, ge);
            }
            ge.title = g.title();
            ge.expires = g.expires;
            for (Item it : ge.src) it.album = ge.title;
            entries.add(ge);
        }
        guestMap.keySet().retainAll(live);
        if (real.size() > 1) entries.add(allE);
        if (favE != null) entries.add(favE);
        entries.addAll(real);
        if (hidE != null) entries.add(hidE);
    }

    /** Vuelve a pintar la pantalla actual tras ocultar, mostrar o marcar una favorita. */
    private void refreshAfterChange(int keepPos) {
        compose();
        if (current != null && current.onlyVideos) { rebuildVids(); updateCount(); }
        if (level == 0 && albumGrid != null) {
            ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
        } else if (level == 1 && current != null) {
            if (!entries.contains(current)) { showAlbums(); return; }
            if (photoGrid != null) {
                ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
                int n = current.shown().size();
                if (n > 0) photoGrid.setSelection(Math.max(0, Math.min(keepPos, n - 1)));
            }
        }
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
                        if (d.partial) continue;   // descarga recortada: se queda la lista que ya hay
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
            for (Entry e : real) {
                if (e.url.equals(f.url) && e.src.size() != f.src.size()) {
                    e.src = f.src;            // se cambia la lista entera, no se modifica la anterior
                    changed = true;
                }
            }
        }
        if (!changed) return;
        refreshAfterChange(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
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
        computeLayout();
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = header();
        TextView title = text("Álbumes", 26, Color.WHITE);
        title.setTypeface(Ui.BLACK);
        title.setPadding(0, 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(colsControl("album", ALBUM_COLS_RANGE, albumCols, "albumCols", true));
        col.addView(head, headParams());

        albumGrid = makeGrid(albumCols);
        albumGrid.setAdapter(new AlbumAdapter());
        albumGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { openEntry(pos); }
        });
        albumGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { return albumMenu(pos); }
        });
        col.addView(albumGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        albumGrid.setSelection(Math.min(albumPos, Math.max(0, entries.size() - 1)));
        if ("minus".equals(refocus)) colsMinus.requestFocus();
        else if ("plus".equals(refocus)) colsPlus.requestFocus();
        else albumGrid.requestFocus();
        refocus = null;
    }

    /** Pulsación larga en un álbum temporal: quitarlo ahora mismo. */
    private boolean albumMenu(int pos) {
        if (pos < 0 || pos >= entries.size()) return false;
        final Entry e = entries.get(pos);
        if (e.kind != 4) return false;
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(e.title)
            .setMessage("Se borrará ahora mismo del televisor, con todas sus miniaturas y vídeos descargados.")
            .setPositiveButton("Quitar ahora", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Guest.remove(BrowserActivity.this, e.gid);
                    if (current == e) current = null;
                    refreshAfterChange(0);
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
        return true;
    }

    /** Cada medio minuto: retira los temporales caducados y refresca los tiempos que quedan. */
    private final Runnable guestTick = new Runnable() {
        public void run() {
            if (destroyed) return;
            boolean had = !guestMap.isEmpty();
            if (had && loaded) {
                int before = guestMap.size();
                Guest.purgeExpired(BrowserActivity.this);
                int fp = photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0;
                refreshAfterChange(fp);
                if (guestMap.size() < before && level == 1 && current != null && !entries.contains(current)) showAlbums();
            }
            h.postDelayed(this, 30000);
        }
    };

    private void openEntry(int pos) {
        if (pos < 0 || pos >= entries.size()) return;
        albumPos = pos;
        current = entries.get(pos);
        current.onlyVideos = false;
        scanGen++;
        showPhotos(0);
    }

    private void showPhotos(int sel) {
        level = 1;
        albumGrid = null;
        computeLayout();
        synchronized (flagPending) { flagPending.clear(); }
        flagTried.clear();
        prefetched = -1;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = header();
        TextView nm = text(current.title, 26, TITLE_COLOR);
        nm.setTypeface(Ui.BLACK);
        nm.setPadding(0, 0, 0, 0);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(nm, new LinearLayout.LayoutParams(-2, -2));
        countView = text("", 20, 0xFFD0DBE8);
        countView.setPadding(0, 0, 0, 0);
        countView.setSingleLine(true);
        head.addView(countView);
        updateCount();
        col.addView(head, headParams());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        playBtn = sbtn("Pase", R.drawable.ic_play, play());
        row.addView(playBtn);
        kindBtn = sbtn(current.onlyVideos ? "Vídeos" : "Todo", current.onlyVideos ? R.drawable.ic_video : R.drawable.ic_photo,
                new View.OnClickListener() {
            public void onClick(View v) { toggleKind(); }
        });
        row.addView(kindBtn);
        orderBtn = sbtn("", R.drawable.ic_down, new View.OnClickListener() {
            public void onClick(View v) {
                if (current != null) setOrder(nextOrder(current.order));
            }
        });
        row.addView(orderBtn);
        shapeBtn = sbtn(shapeLabel(photoShape), R.drawable.ic_format, new View.OnClickListener() {
            public void onClick(View v) {
                String n = "square".equals(photoShape) ? "wide" : ("wide".equals(photoShape) ? "full" : "square");
                Prefs.sp(BrowserActivity.this).edit().putString("photoShape", n).commit();
                refocus = "shape";
                showPhotos(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
            }
        });
        row.addView(shapeBtn);
        fitBtn = sbtn(fitLabel(Prefs.fit(this)), R.drawable.ic_fullscreen, new View.OnClickListener() {
            public void onClick(View v) {
                String n = nextFit(Prefs.fit(BrowserActivity.this));
                Prefs.sp(BrowserActivity.this).edit().putString("fit", n).commit();
                fitBtn.setText(fitLabel(n));
            }
        });
        row.addView(fitBtn);
        row.addView(colsControl("photo", PHOTO_COLS_RANGE, photoCols, "photoCols", false));
        refreshOrderButtons();
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.addView(row);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(margin, 0, margin, 0);
        col.addView(hs, rp);

        photoGrid = makeGrid(photoCols);
        photoGrid.setAdapter(new PhotoAdapter());
        photoGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { openViewer(pos); }
        });
        photoGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { itemMenu(pos); return true; }
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
        if (sel > 0) photoGrid.setSelection(sel);
        if ("minus".equals(refocus)) colsMinus.requestFocus();
        else if ("plus".equals(refocus)) colsPlus.requestFocus();
        else if ("shape".equals(refocus)) shapeBtn.requestFocus();
        else photoGrid.requestFocus();
        refocus = null;
    }

    private void itemMenu(final int pos) {
        if (current == null || pos < 0 || pos >= current.shown().size()) return;
        final Item it = current.shown().get(pos);
        if (current.kind == 4) { showInfo(it); return; }       // los temporales no se ocultan ni se marcan
        final boolean isHid = Prefs.hidden(this).contains(it.key());
        final boolean isFav = Prefs.favs(this).contains(it.key());
        final String[] labels = {isHid ? "Mostrar" : "Ocultar", "Ver información",
                isFav ? "Quitar de favoritos" : "Añadir a favoritos"};
        final int[] icons = {isHid ? R.drawable.ic_visibility : R.drawable.ic_visibility_off, R.drawable.ic_info,
                isFav ? R.drawable.ic_star_border : R.drawable.ic_star};
        android.widget.ListAdapter ad = new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int i, View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getView(i, cv, parent);
                tv.setTypeface(Ui.REG);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                android.graphics.drawable.Drawable d = getResources().getDrawable(icons[i]).mutate();
                int sz = Ui.dp(BrowserActivity.this, 22);
                d.setBounds(0, 0, sz, sz);
                tv.setCompoundDrawables(d, null, null, null);
                tv.setCompoundDrawablePadding(Ui.dp(BrowserActivity.this, 14));
                tv.setGravity(Gravity.CENTER_VERTICAL);
                return tv;
            }
        };
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setAdapter(ad, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int which) {
                    if (which == 0) {
                        if (isHid) Prefs.unhide(BrowserActivity.this, it.key()); else Prefs.hide(BrowserActivity.this, it.key());
                        refreshAfterChange(pos);
                    } else if (which == 1) {
                        showInfo(it);
                    } else {
                        Prefs.toggleFav(BrowserActivity.this, it.key());
                        refreshAfterChange(pos);
                    }
                }
            }).show();
    }

    private void showInfo(Item it) {
        if (infoPanel != null) return;
        infoPanel = new InfoPanel(this, it, it.album);
        root.addView(infoPanel, new FrameLayout.LayoutParams(-1, -1));
        infoPanel.bringToFront();
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (infoPanel != null) {                      // cualquier botón cierra la información
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) { infoPanel.detach(); infoPanel = null; }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void updateCount() {
        if (countView == null || current == null) return;
        String t;
        if (current.onlyVideos) {
            t = "  \u00B7  " + current.vids.size() + (current.vids.size() == 1 ? " vídeo" : " vídeos");
            if (scanTotal > 0 && scanDone < scanTotal) t += "  \u00B7  buscando " + scanDone + "/" + scanTotal + "…";
        } else {
            t = "  \u00B7  " + current.items.size() + " elementos";
        }
        countView.setText(t);
    }

    /** Los vídeos ya conocidos de la lista actual, en su orden. */
    private void rebuildVids() {
        if (current == null) return;
        List<Item> base = current.view != null ? current.view : current.items;
        List<Item> out = new ArrayList<Item>();
        for (Item it : base) {
            Boolean v = MetaFetcher.cachedVideo(this, it);
            if (v != null && v.booleanValue()) out.add(it);
        }
        current.vids = out;
    }

    /** Alterna entre «Todo» y «Vídeos». Los vídeos se buscan por lotes y se van mostrando según aparecen. */
    private void toggleKind() {
        if (current == null) return;
        current.onlyVideos = !current.onlyVideos;
        scanGen++;
        scanDone = 0;
        scanTotal = 0;
        if (current.onlyVideos) {
            rebuildVids();
            startScan();
        }
        if (kindBtn != null) {
            kindBtn.setText(current.onlyVideos ? "Vídeos" : "Todo");
            Ui.icon(kindBtn, current.onlyVideos ? R.drawable.ic_video : R.drawable.ic_photo);
        }
        updateCount();
        if (photoGrid != null) {
            ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
            photoGrid.setSelection(0);
        }
    }

    private void startScan() {
        final int gen = scanGen;
        final Entry e = current;
        final List<Item> base = new ArrayList<Item>(e.view != null ? e.view : e.items);
        scanTotal = base.size();
        scanDone = 0;
        scanExec.submit(new Runnable() {
            public void run() {
                int total = base.size();
                for (int i = 0; i < total; i += 40) {
                    if (destroyed || gen != scanGen) return;
                    try { MetaFetcher.detectVideos(BrowserActivity.this, new ArrayList<Item>(base.subList(i, Math.min(total, i + 40)))); }
                    catch (Throwable ignored) { }
                    final int done = Math.min(total, i + 40);
                    h.post(new Runnable() {
                        public void run() {
                            if (destroyed || gen != scanGen || current != e) return;
                            scanDone = done;
                            rebuildVids();
                            updateCount();
                            if (photoGrid != null) ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }

    private void setOrder(String key) {
        if (current == null) return;
        current.order = key;
        applyOrder(current);
        if (current.onlyVideos) rebuildVids();
        prefetched = -1;
        refreshOrderButtons();
        if (photoGrid != null) {
            ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
            photoGrid.setSelection(0);
        }
    }

    private void refreshOrderButtons() {
        if (orderBtn == null) return;
        String o = current != null ? current.order : "";
        if ("random".equals(o)) { orderBtn.setText("Aleatorio"); Ui.icon(orderBtn, R.drawable.ic_shuffle); }
        else if ("asc".equals(o)) { orderBtn.setText("Antiguas primero"); Ui.icon(orderBtn, R.drawable.ic_up); }
        else { orderBtn.setText("Recientes primero"); Ui.icon(orderBtn, R.drawable.ic_down); }
    }

    /** Aleatorio → Antiguas primero → Recientes primero → Aleatorio… (el orden de partida equivale a «Recientes»). */
    private static String nextOrder(String o) {
        if ("random".equals(o)) return "asc";
        if ("asc".equals(o)) return "desc";
        return "random";
    }

    private static String nextFit(String f) {
        return "auto".equals(f) ? "crop" : ("crop".equals(f) ? "fit" : "auto");
    }

    private static String fitLabel(String f) {
        return "crop".equals(f) ? "Pantalla: rellenar" : ("fit".equals(f) ? "Pantalla: original" : "Pantalla: automática");
    }

    private View.OnClickListener play() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                if (current == null || current.items.isEmpty()) return;
                Session.items = current.shown();
                Session.order = current.order.length() == 0 && current.kind != 3 ? "random" : "keep";   // sin ordenar: aleatorio
                Session.manual = false;
                Session.guest = current.kind == 4;
                Session.guestId = current.kind == 4 ? current.gid : null;
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
        Session.guest = current.kind == 4;
        Session.guestId = current.kind == 4 ? current.gid : null;
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
        sel.setCornerRadius(0);   // miniaturas cuadradas: recuadro con esquinas rectas
        final GradientDrawable selOn = sel;
        final android.graphics.drawable.ColorDrawable selOff = new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
        g.setSelector(sel);
        g.setDrawSelectorOnTop(true);
        // al subir a los botones, el recuadro blanco de la foto desaparece para que se vea dónde está el foco
        g.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                ((GridView) v).setSelector(has ? selOn : selOff);
                v.invalidate();
            }
        });
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(Ui.REG);
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
            t.name.setText(e.title);
            int ic = e.kind == 1 ? R.drawable.ic_albums : e.kind == 2 ? R.drawable.ic_star
                    : e.kind == 3 ? R.drawable.ic_visibility_off : e.kind == 4 ? R.drawable.ic_schedule : 0;
            if (ic != 0) {
                android.graphics.drawable.Drawable d = getResources().getDrawable(ic).mutate();
                if (e.kind == 2) d.setColorFilter(0xFFFFC83D, android.graphics.PorterDuff.Mode.SRC_IN);
                if (e.kind == 4) d.setColorFilter(0xFFFFB454, android.graphics.PorterDuff.Mode.SRC_IN);
                int sz = Ui.dp(BrowserActivity.this, 17);
                d.setBounds(0, 0, sz, sz);
                t.name.setCompoundDrawables(d, null, null, null);
                t.name.setCompoundDrawablePadding(Ui.dp(BrowserActivity.this, 7));
            } else t.name.setCompoundDrawables(null, null, null, null);
            if (e.kind == 4) {
                t.count.setText("Temporal \u00B7 quedan " + Guest.left(e.expires - System.currentTimeMillis()));
                t.count.setTextColor(0xFFFFB454);
            } else {
                t.count.setText(e.items.size() + " elementos");
                t.count.setTextColor(0xFFD0DBE8);
            }
            if (e.items.isEmpty()) thumbs.clearView(t.img);
            else if (e.kind == 3) thumbs.loadBlur(t.img, (e.cover != null ? e.cover : e.items.get(0)).url, r32(albumTileW), r32(albumTileH));
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
                new int[]{0xD9000000, 0xA6000000, 0x66000000, 0x2B000000, 0x00000000}));
        t.addView(t.shade, new FrameLayout.LayoutParams(-1, albumTileH * 6 / 10, Gravity.BOTTOM));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 4));
        t.name = text("", 16, Color.WHITE);
        t.name.setPadding(0, 0, 0, 0);
        t.name.setSingleLine(true);
        t.name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.name.setTypeface(Ui.BLACK);
        t.name.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        t.count = text("", 13, 0xFFD0DBE8);
        t.count.setPadding(0, 0, 0, 0);
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
            boolean crop = !"full".equals(photoShape);
            t.img.setScaleType(crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            thumbs.load(t.img, it.url, r32(photoTile), r32(photoTileH), crop);
            t.fav.setVisibility(favSet.contains(it.key()) ? View.VISIBLE : View.GONE);
            prefetchAhead(pos);
            return t;
        }
    }

    /** Va bajando al disco las miniaturas que vienen detrás de la que se está viendo. */
    private void prefetchAhead(int pos) {
        List<Item> l = current.shown();
        int to = Math.min(l.size() - 1, pos + AHEAD);
        for (int i = Math.max(prefetched + 1, pos + 1); i <= to; i++)
            thumbs.prefetch(l.get(i).url, r32(photoTile), r32(photoTileH), !"full".equals(photoShape));
        if (to > prefetched) prefetched = to;
    }

    private Tile newPhotoTile() {
        Tile t = new Tile(this, photoRatio);
        t.setBackgroundColor(0xFF1B2D44);
        t.img = new ImageView(this);
        t.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(t.img, new FrameLayout.LayoutParams(-1, -1));
        // marca de vídeo: círculo oscuro con ▶ abajo a la izquierda
        t.badge = new TextView(this);
        t.badge.setTypeface(Ui.BLACK);
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
        t.fav = new ImageView(this);
        t.fav.setImageResource(R.drawable.ic_star);
        t.fav.setColorFilter(0xFFFFC83D);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.TOP | Gravity.END);
        fp.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), 0);
        t.addView(t.fav, fp);
        t.fav.setVisibility(View.GONE);
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
        int first = Math.max(0, photoGrid.getFirstVisiblePosition() - photoCols * 2);
        int last = Math.min(current.items.size() - 1, photoGrid.getLastVisiblePosition() + photoCols * 2);
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
