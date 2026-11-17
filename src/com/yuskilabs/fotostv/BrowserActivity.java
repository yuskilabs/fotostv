package com.yuskilabs.fotostv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explorador: mosaico de álbumes → mosaico de fotos → pantalla completa. */
public class BrowserActivity extends Activity {
    final BrowserActions actions = new BrowserActions(this);
    final Handler h = new Handler(Looper.getMainLooper());
    final ExecutorService bg = Executors.newSingleThreadExecutor();
    final ExecutorService act = Executors.newSingleThreadExecutor();   // acciones que pide el usuario (no esperan detrás de la carga de álbumes)
    boolean placeMode;
    final List<Entry> entries = new ArrayList<Entry>();
    FrameLayout root;
    List<Entry> real = new ArrayList<Entry>();
    Entry allE, favE, hidE;
    java.util.Set<String> favSet = new HashSet<String>();
    InfoPanel infoPanel;
    GridView albumGrid, photoGrid;
    Entry current;
    Thumbs thumbs;
    int level;            // 0 = álbumes, 1 = fotos de un álbum
    int albumPos;
    int W, margin, gap, albumTileW, albumTileH, photoTile;
    boolean destroyed, loaded;
    int prefetched = -1;
    static final int AHEAD = 150;
    android.widget.Button orderBtn, fitBtn, kindBtn;
    TextView countView;
    int scanGen, scanDone, scanTotal;
    final ExecutorService scanExec = Executors.newSingleThreadExecutor();
    android.widget.Button playBtn;

    int albumCols, photoCols, photoTileH;
    String photoShape = "square";
    float photoRatio = 1f;
    android.widget.Button colsMinus, colsPlus, shapeBtn;
    String refocus;
    android.widget.Button sortBtn;
    static final int[] ALBUM_COLS_RANGE = {2, 10};
    static final int[] PHOTO_COLS_RANGE = {2, 14};
    static final int TITLE_COLOR = 0xFFFFFFFF;

    void computeLayout() {
        albumCols = Prefs.albumCols(this);
        photoCols = Prefs.photoCols(this);
        photoShape = Prefs.photoShape(this);
        photoRatio = "wide".equals(photoShape) ? 0.6667f : ("full".equals(photoShape) ? 0.75f : 1f);
        albumTileW = (W - 2 * margin - (albumCols - 1) * gap) / albumCols;
        albumTileH = (int) (albumTileW * 0.66f);
        photoTile = (W - 2 * margin - (photoCols - 1) * gap) / photoCols;
        photoTileH = (int) (photoTile * photoRatio);
    }


    /** Control «−  Columnas: N  +» con dos botones y la etiqueta en medio (sin foco). */
    LinearLayout colsControl(final String key, final int[] range, final int cur, final String prefKey,
                                     final boolean albums) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Button minus = BrowserUi.sbtn(BrowserActivity.this, "", R.drawable.ic_remove, null);
        android.widget.Button plus = BrowserUi.sbtn(BrowserActivity.this, "", R.drawable.ic_add, null);
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
        TextView lab = BrowserUi.text(BrowserActivity.this, "Columnas: " + cur, 13, 0xFFD0DBE8);
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

    void stepCols(int[] range, int cur, int d, String prefKey, boolean albums, String focusKey) {
        int n = Math.max(range[0], Math.min(range[1], cur + d));
        Prefs.sp(this).edit().putInt(prefKey, n).commit();
        refocus = focusKey;
        if (albums) showAlbums(); else showPhotos(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
    }




    /** Arriba a la derecha: «Fecha» (si está activada) y «Mapa» (lugares y mapa de calor de las fotos de este álbum). */
    View mapButton() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2), lp2 = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(this, 4), 0, 0, 0);                // los iconos, juntos
        lp2.setMargins(Ui.dp(this, 4), 0, 0, 0);
        if (Extras.on(this, Extras.DATE_JUMP)) {
            wrap.addView(BrowserUi.iconBox(BrowserActivity.this, R.drawable.ic_event, "Fecha", new View.OnClickListener() {
                public void onClick(View v) { actions.dateJump(); }
            }), lp);
        }
        wrap.addView(BrowserUi.iconBox(BrowserActivity.this, R.drawable.ic_map, "Mapa", new View.OnClickListener() {
            public void onClick(View v) {
                if (current == null) return;
                java.util.HashSet<String> ks = new java.util.HashSet<String>();
                for (Item it : current.items) ks.add(it.key());
                Session.mapScope = ks;
                Session.mapTitle = current.title;
                startActivity(new Intent(BrowserActivity.this, PlacesActivity.class));
            }
        }), lp2);
        return wrap;
    }

    LinearLayout.LayoutParams headParams() {
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        hp.setMargins(margin, Ui.dp(this, 10), margin, 0);
        return hp;
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
        if (Session.placeItems != null) {
            // mosaico de un lugar (viene de Lugares o del mapa): como un álbum, pero sin salir de la lista de lugares
            placeMode = true;
            Entry e = new Entry();
            e.kind = 5;
            e.title = Session.placeTitle != null ? Session.placeTitle : "Lugar";
            e.src = new ArrayList<Item>(Session.placeItems);
            e.query = Session.searchQuery;
            Session.placeItems = null;
            Session.placeTitle = null;
            Session.searchQuery = null;
            favSet = Prefs.favs(this);
            e.items = BrowseUtil.visible(e.src, Prefs.hidden(this));
            EntryOps.applyOrder(e);
            EntryOps.pickCover(BrowserActivity.this, e);
            entries.add(e);
            loaded = true;
            current = e;
            showPhotos(0);
            return;
        }
        showMessage("Cargando álbumes…");
        loadEntries();
    }

    @Override protected void onResume() {
        super.onResume();
        h.removeCallbacks(guestTick);
        h.postDelayed(guestTick, 30000);
        Guest.purgeExpired(this);
        if (placeMode && Session.gotoUrl != null) {   // «Ir al álbum» desde un pase de lugar: abre el explorador normal
            startActivity(new Intent(this, BrowserActivity.class));
            finish();
            return;
        }
        if (placeMode) {
            if (current != null && photoGrid != null) {
                favSet = Prefs.favs(this);
                current.items = BrowseUtil.visible(current.src, Prefs.hidden(this));
                EntryOps.reapply(current);
                ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
                updateCount();
            }
        } else if (loaded) {
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
        applyGoto();
    }

    @Override protected void onPause() {
        h.removeCallbacks(guestTick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        act.shutdownNow();
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
        if (selMode && level == 0) { actions.exitSel(); return; }
        if (pSel && level == 1) { actions.exitPSel(); return; }
        if (level == 1) {
            // primer «atrás»: sube a los botones; segundo: vuelve a los álbumes
            if (photoGrid != null && photoGrid.hasFocus() && playBtn != null) playBtn.requestFocus();
            else showAlbums();
        } else super.onBackPressed();
    }

    // ------------------------------------------------------------ datos
    /** «Ir al álbum» desde la ficha del pase: abre ese álbum y se coloca en la foto. */
    void applyGoto() {
        if (Session.gotoUrl == null || !loaded) return;
        String u = Session.gotoUrl, k = Session.gotoKey;
        Session.gotoUrl = null;
        Session.gotoKey = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.kind == 0 && u.equals(e.url)) {
                openEntry(i);
                if (k != null && photoGrid != null) {
                    List<Item> l = current.shown();
                    for (int j = 0; j < l.size(); j++) if (l.get(j).key().equals(k)) { photoGrid.setSelection(j); break; }
                }
                return;
            }
        }
    }

    void loadEntries() {
        act.submit(new Runnable() {
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
                    list.add(EntryOps.makeEntry(al, d));
                }
                final List<Entry> fin = EntryOps.sortEntries(BrowserActivity.this, list);
                final List<Entry> vl = EntryOps.buildVirtual(BrowserActivity.this, list);
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        real = new ArrayList<Entry>(fin);
                        virt = vl;
                        for (Entry e : real) EntryOps.computeNew(BrowserActivity.this, e);
                        refreshOff();
                        compose();
                        loaded = true;
                        if (entries.isEmpty()) {
                            showMessage("No hay álbumes.\nAñádelos desde la web de ajustes de FotosTV.");
                        } else {
                            showAlbums();
                            refreshInBackground();
                            applyGoto();
                        }
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------ álbumes virtuales y fotos nuevas (funciones extra)
    List<Entry> virt = new ArrayList<Entry>();











    /** Rehace las listas que se ven: quita las ocultas, y arma Todas, Favoritos y Ocultas. */
    final java.util.Map<String, Entry> guestMap = new java.util.HashMap<String, Entry>();

    void compose() {
        java.util.Set<String> hid = Prefs.hidden(this);
        favSet = Prefs.favs(this);
        for (Entry e : real) {
            e.items = BrowseUtil.visible(e.src, hid);
            EntryOps.reapply(e);
            EntryOps.applyCover(BrowserActivity.this, e, hid);
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
        EntryOps.reapply(allE);
        if (allE.cover == null || hid.contains(allE.cover.key())) EntryOps.pickCover(BrowserActivity.this, allE);
        List<Item> favs = new ArrayList<Item>();
        for (Item it : vis.values()) if (favSet.contains(it.key())) favs.add(it);
        if (favs.isEmpty()) favE = null;
        else {
            if (favE == null) { favE = new Entry(); favE.kind = 2; favE.title = "Favoritos"; }
            favE.items = favs;
            EntryOps.reapply(favE);
            if (favE.cover == null || !favSet.contains(favE.cover.key())) EntryOps.pickCover(BrowserActivity.this, favE);
        }
        if (hidden.isEmpty()) hidE = null;
        else {
            if (hidE == null) { hidE = new Entry(); hidE.kind = 3; hidE.title = "Ocultas"; }
            hidE.items = new ArrayList<Item>(hidden.values());
            EntryOps.reapply(hidE);
            if (hidE.cover == null || !hid.contains(hidE.cover.key())) EntryOps.pickCover(BrowserActivity.this, hidE);
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
                EntryOps.applyOrder(ge);
                EntryOps.pickCover(BrowserActivity.this, ge);
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
        if (Extras.on(this, Extras.VIRTUAL)) {
            for (Entry v : virt) {
                v.items = BrowseUtil.visible(v.src, hid);
                EntryOps.reapply(v);
                if (v.cover == null || hid.contains(v.cover.key()) || !v.items.contains(v.cover)) EntryOps.pickCover(BrowserActivity.this, v);
                entries.add(v);
            }
        }
        if (hidE != null) entries.add(hidE);
    }

    /** Vuelve a pintar la pantalla actual tras ocultar, mostrar o marcar una favorita. */
    void refreshAfterChange(int keepPos) {
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
    void refreshInBackground() {
        bg.submit(new Runnable() {
            public void run() {
                final List<Entry> fresh = new ArrayList<Entry>();
                try {
                for (Prefs.Album al : Prefs.albums(BrowserActivity.this)) {
                    if (destroyed) return;
                    try {
                        AlbumFetcher.AlbumData d = AlbumFetcher.fetchAlbum(al.url);
                        Prefs.saveAlbumCache(BrowserActivity.this, al.url, d);
                        if (d.partial) continue;   // descarga recortada: se queda la lista que ya hay
                        fresh.add(EntryOps.makeEntry(al, d));
                    } catch (Exception ignored) { }
                }
                } finally { }
                if (fresh.isEmpty()) return;
                h.post(new Runnable() {
                    public void run() { applyFresh(fresh); }
                });
            }
        });
    }

    void applyFresh(List<Entry> fresh) {
        if (destroyed) return;
        boolean changed = false;
        for (Entry f : fresh) {
            for (Entry e : real) {
                if (e.url.equals(f.url) && e.src.size() != f.src.size()) {
                    e.src = f.src;            // se cambia la lista entera, no se modifica la anterior
                    if (e != current || level != 1) EntryOps.computeNew(BrowserActivity.this, e);
                    changed = true;
                }
            }
        }
        if (!changed) return;
        refreshAfterChange(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
    }


    // ------------------------------------------------------------ tareas en segundo plano (texto de colores junto al título)
    final BusyBar busyBar = new BusyBar(this);

    void busyStart(String label) { busyBar.start(label); }

    void busyEnd(String label) { busyBar.end(label); }

    /** «Revisando ubicaciones…» hasta que las fotos revisadas vuelven a tener lugar (o pasan 10 minutos). */
    void watchGeo(final Set<String> keys) {
        busyStart("Revisando ubicaciones…");
        final long t0 = System.currentTimeMillis();
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (!destroyed && System.currentTimeMillis() - t0 < 600000L) {
                        Thread.sleep(3000);
                        if (GeoIndex.get(BrowserActivity.this).known().containsAll(keys)) break;
                    }
                } catch (Throwable ignored) { }
                h.post(new Runnable() { public void run() { busyEnd("Revisando ubicaciones…"); } });
            }
        }).start();
    }

    // ------------------------------------------------------------ pantallas
    void showMessage(String msg) {
        level = 0;
        albumGrid = null;
        photoGrid = null;
        root.removeAllViews();
        TextView t = BrowserUi.text(BrowserActivity.this, msg, 26, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        root.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    void showAlbums() {
        if (placeMode) { finish(); return; }   // desde un lugar, «atrás» vuelve a Lugares
        if (level == 1 && current != null && current.kind == 0 && !current.newKeys.isEmpty()) {
            EntryOps.markKnown(BrowserActivity.this, current);                // ya vistas: dejan de ser nuevas
            current.newKeys.clear();
        }
        pSel = false;
        pKeys.clear();
        actions.endDupeView();
        level = 0;
        photoGrid = null;
        computeLayout();
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = BrowserUi.header(BrowserActivity.this);
        TextView title = BrowserUi.text(BrowserActivity.this, selMode ? sel.size() + (sel.size() == 1 ? " álbum seleccionado" : " álbumes seleccionados") : "Álbumes", selMode ? 20 : 26, Color.WHITE);
        title.setTypeface(Ui.BLACK);
        title.setPadding(0, 0, 0, 0);
        selTitle = selMode ? title : null;
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        if (selMode) {
            head.addView(BrowserUi.sbtn(BrowserActivity.this, "Pase", R.drawable.ic_play, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) actions.startPase(actions.selected(), false); }
            }));
            head.addView(BrowserUi.sbtn(BrowserActivity.this, "Con música", R.drawable.ic_music, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) actions.startPase(actions.selected(), true); }
            }));
            head.addView(BrowserUi.sbtn(BrowserActivity.this, "Ocultar", R.drawable.ic_visibility_off, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) actions.hideEntries(actions.selected(), false); }
            }));
            head.addView(BrowserUi.sbtn(BrowserActivity.this, "Borrar", R.drawable.ic_delete, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) actions.confirmDelete(actions.selected()); }
            }));
            head.addView(BrowserUi.sbtn(BrowserActivity.this, "Cancelar", R.drawable.ic_close, new View.OnClickListener() {
                public void onClick(View v) { actions.exitSel(); }
            }));
        } else {
            if (!placeMode) head.addView(sortBtn = BrowserUi.sbtn(BrowserActivity.this, albumSortLabel(), R.drawable.ic_down, new View.OnClickListener() {
                public void onClick(View v) {
                    String m = Prefs.albumSort(BrowserActivity.this);
                    String n = "manual".equals(m) ? "newest" : "newest".equals(m) ? "oldest" : "manual";
                    Prefs.sp(BrowserActivity.this).edit().putString("albumSort", n).commit();
                    refocus = "sort";
                    loadEntries();
                }
            }));
            head.addView(colsControl("album", ALBUM_COLS_RANGE, albumCols, "albumCols", true));
        }
        col.addView(head, headParams());

        albumGrid = Tiles.makeGrid(BrowserActivity.this, gap, margin, albumCols);
        albumGrid.setAdapter(new AlbumAdapter());
        albumGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (selMode) { if (pos >= 0 && pos < entries.size()) actions.toggleSel(entries.get(pos)); } else openEntry(pos);
            }
        });
        albumGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { return actions.albumMenu(pos); }
        });
        col.addView(albumGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        albumGrid.setSelection(Math.min(albumPos, Math.max(0, entries.size() - 1)));
        if ("minus".equals(refocus)) colsMinus.requestFocus();
        else if ("plus".equals(refocus)) colsPlus.requestFocus();
        else if ("sort".equals(refocus) && sortBtn != null) sortBtn.requestFocus();
        else albumGrid.requestFocus();
        refocus = null;
    }

    String albumSortLabel() {
        String m = Prefs.albumSort(this);
        return "Álbumes: " + ("newest".equals(m) ? "recientes primero" : "oldest".equals(m) ? "antiguos primero" : "orden manual");
    }

    boolean selMode;
    boolean pSel;
    final java.util.HashSet<String> pKeys = new java.util.HashSet<String>();
    final java.util.HashSet<Entry> sel = new java.util.HashSet<Entry>();
    java.util.Set<String> offUrls = new java.util.HashSet<String>();

    void refreshOff() {
        java.util.Set<String> o = new java.util.HashSet<String>();
        for (Prefs.Album al : Prefs.albums(this)) if (!al.show) o.add(al.url);
        offUrls = o;
    }


    // ---- reordenar álbumes (con el mando)
    Entry moveEntry;
    List<Entry> moveBackup;


    // ------------------------------------------------------------ fotos repetidas (función extra)
    volatile boolean dupBusy;

    Entry pendingDupes;
    DupeScan.Result pendingDupeKeys;

    android.widget.TextView selTitle;

    boolean reloading;

    /** Aviso que se queda unos segundos más que el normal (el texto largo da tiempo a leerlo). */
    void showToastLong(final String s) {
        final android.widget.Toast t1 = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG);
        t1.show();
        h.postDelayed(new Runnable() {
            public void run() {
                if (destroyed) return;
                t1.cancel();
                android.widget.Toast.makeText(BrowserActivity.this, s, android.widget.Toast.LENGTH_LONG).show();
            }
        }, 3000);
    }

    void showToast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
    }

    /** Cada medio minuto: retira los temporales caducados y refresca los tiempos que quedan. */
    final Runnable guestTick = new Runnable() {
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

    void openEntry(int pos) {
        if (pos < 0 || pos >= entries.size()) return;
        pSel = false;
        pKeys.clear();
        albumPos = pos;
        current = entries.get(pos);
        current.onlyVideos = false;
        current.dayLo = -1;
        current.dayHi = -1;
        scanGen++;
        if (pendingDupes == current && pendingDupeKeys != null) {   // repetidas que se encontraron mientras estabas en otro sitio
            DupeScan.Result k = pendingDupeKeys;
            pendingDupes = null;
            pendingDupeKeys = null;
            actions.markDupes(k);
            return;
        }
        showPhotos(0);
    }

    void showPhotos(int sel) {
        level = 1;
        albumGrid = null;
        computeLayout();
        prefetched = -1;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = BrowserUi.header(BrowserActivity.this);
        TextView nm = BrowserUi.text(BrowserActivity.this, current.title, 26, TITLE_COLOR);
        nm.setTypeface(Ui.BLACK);
        nm.setPadding(0, 0, 0, 0);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nm.setMaxWidth(getResources().getDisplayMetrics().widthPixels * 38 / 100);
        head.addView(nm, new LinearLayout.LayoutParams(-2, -2));
        countView = BrowserUi.text(BrowserActivity.this, "", 20, 0xFFD0DBE8);
        countView.setPadding(0, 0, 0, 0);
        countView.setSingleLine(true);
        head.addView(countView);
        updateCount();
        if (current.kind == 0 && !placeMode) head.addView(mapButton());
        else if (Extras.on(this, Extras.DATE_JUMP)) {
            LinearLayout w2 = new LinearLayout(this);
            w2.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            w2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            w2.addView(BrowserUi.iconBox(BrowserActivity.this, R.drawable.ic_event, "Fecha", new View.OnClickListener() {
                public void onClick(View v) { actions.dateJump(); }
            }));
            head.addView(w2);
        }
        col.addView(head, headParams());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        playBtn = BrowserUi.sbtn(BrowserActivity.this, "Pase", R.drawable.ic_play, play());
        row.addView(playBtn);
        kindBtn = BrowserUi.sbtn(BrowserActivity.this, current.onlyVideos ? "Vídeos" : "Todo", current.onlyVideos ? R.drawable.ic_video : R.drawable.ic_photo,
                new View.OnClickListener() {
            public void onClick(View v) { toggleKind(); }
        });
        row.addView(kindBtn);
        orderBtn = BrowserUi.sbtn(BrowserActivity.this, "", R.drawable.ic_down, new View.OnClickListener() {
            public void onClick(View v) {
                if (current != null) setOrder(BrowseUtil.nextOrder(current.order));
            }
        });
        row.addView(orderBtn);
        if (current.kind == 5 && current.query != null) {
            row.addView(BrowserUi.sbtn(BrowserActivity.this, "Guardar", R.drawable.ic_albums, new View.OnClickListener() {
                public void onClick(View v) { actions.saveVirtual(); }
            }));
        }
        shapeBtn = BrowserUi.sbtn(BrowserActivity.this, BrowseUtil.shapeLabel(photoShape), R.drawable.ic_format, new View.OnClickListener() {
            public void onClick(View v) {
                String n = "square".equals(photoShape) ? "wide" : ("wide".equals(photoShape) ? "full" : "square");
                Prefs.sp(BrowserActivity.this).edit().putString("photoShape", n).commit();
                refocus = "shape";
                showPhotos(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
            }
        });
        row.addView(shapeBtn);
        fitBtn = BrowserUi.sbtn(BrowserActivity.this, BrowseUtil.fitLabel(Prefs.fit(this)), R.drawable.ic_fullscreen, new View.OnClickListener() {
            public void onClick(View v) {
                String n = BrowseUtil.nextFit(Prefs.fit(BrowserActivity.this));
                Prefs.sp(BrowserActivity.this).edit().putString("fit", n).commit();
                fitBtn.setText(BrowseUtil.fitLabel(n));
            }
        });
        row.addView(fitBtn);
        row.addView(colsControl("photo", PHOTO_COLS_RANGE, photoCols, "photoCols", false));
        refreshOrderButtons();
        if (pSel) {
            row.removeAllViews();
            row.addView(BrowserUi.sbtn(BrowserActivity.this, "Todas", R.drawable.ic_check, new View.OnClickListener() {
                public void onClick(View v) {
                    for (Item it : current.shown()) pKeys.add(it.key());
                    actions.refreshPhotoSel();
                }
            }));
            row.addView(BrowserUi.sbtn(BrowserActivity.this, current.kind == 3 ? "Mostrar" : "Ocultar", current.kind == 3 ? R.drawable.ic_visibility : R.drawable.ic_visibility_off,
                    new View.OnClickListener() {
                public void onClick(View v) { actions.applyPhotoSel(0); }
            }));
            row.addView(BrowserUi.sbtn(BrowserActivity.this, "A favoritos", R.drawable.ic_star, new View.OnClickListener() {
                public void onClick(View v) { actions.applyPhotoSel(1); }
            }));
            row.addView(BrowserUi.sbtn(BrowserActivity.this, "Quitar de favoritos", R.drawable.ic_star_border, new View.OnClickListener() {
                public void onClick(View v) { actions.applyPhotoSel(2); }
            }));
            row.addView(BrowserUi.sbtn(BrowserActivity.this, "Revisar ubicación", R.drawable.ic_place, new View.OnClickListener() {
                public void onClick(View v) {
                    if (pKeys.isEmpty()) { showToast("Marca primero alguna foto o vídeo."); return; }
                    List<Item> l = new ArrayList<Item>();
                    for (Item it : current.shown()) if (pKeys.contains(it.key())) l.add(it);
                    actions.exitPSel();
                    actions.recheckItems(l);
                }
            }));
            row.addView(BrowserUi.sbtn(BrowserActivity.this, "Cancelar", R.drawable.ic_close, new View.OnClickListener() {
                public void onClick(View v) { actions.exitPSel(); }
            }));
        }
        // los botones se reparten todo el ancho del mosaico, ni más ni menos (hasta donde acaba la última foto de la primera fila)
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof android.widget.Button) {
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2, 1f);
                bl.setMargins(0, 0, Ui.dp(this, 4), 0);
                v.setLayoutParams(bl);
                ((android.widget.Button) v).setGravity(Gravity.CENTER);
                ((android.widget.Button) v).setSingleLine(true);
            }
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(margin, 0, margin, 0);
        col.addView(row, rp);

        photoGrid = Tiles.makeGrid(BrowserActivity.this, gap, margin, photoCols);
        photoGrid.setAdapter(new PhotoAdapter());
        final LinearLayout frow = row;
        photoGrid.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, android.view.KeyEvent ev) {
                // arriba desde la primera foto: al primer botón (no al más cercano, que era el segundo)
                if (code == android.view.KeyEvent.KEYCODE_DPAD_UP && ev.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && photoGrid != null && photoGrid.getSelectedItemPosition() == 0 && frow.getChildCount() > 0) {
                    frow.getChildAt(0).requestFocus();
                    return true;
                }
                return false;
            }
        });
        photoGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pSel) actions.togglePhoto(pos); else openViewer(pos);
            }
        });
        photoGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pSel) actions.togglePhoto(pos); else actions.itemMenu(pos);
                return true;
            }
        });
        col.addView(photoGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (current.items.isEmpty()) {
            TextView t = BrowserUi.text(BrowserActivity.this, "Este álbum no tiene fotos.", 22, Color.WHITE);
            root.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        }
        if (sel > 0) photoGrid.setSelection(sel);
        if (pSel) photoGrid.requestFocus();
        else if ("minus".equals(refocus)) colsMinus.requestFocus();
        else if ("plus".equals(refocus)) colsPlus.requestFocus();
        else if ("shape".equals(refocus)) shapeBtn.requestFocus();
        else photoGrid.requestFocus();
        refocus = null;
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (infoPanel != null) {                      // cualquier botón cierra la información
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) { infoPanel.detach(); infoPanel = null; }
            return true;
        }
        if (moveEntry != null && level == 0) {        // reordenando un álbum
            int k = e.getKeyCode();
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (k == android.view.KeyEvent.KEYCODE_DPAD_LEFT) actions.moveBy(-1);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) actions.moveBy(1);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_UP) actions.moveBy(-albumCols);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_DOWN) actions.moveBy(albumCols);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_CENTER || k == android.view.KeyEvent.KEYCODE_ENTER) actions.endMove(true);
                else if (k == android.view.KeyEvent.KEYCODE_BACK || k == android.view.KeyEvent.KEYCODE_ESCAPE) actions.endMove(false);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    void updateCount() {
        if (countView == null || current == null) return;
        String t;
        if (current.onlyVideos) {
            t = "  \u00B7  " + current.vids.size() + (current.vids.size() == 1 ? " vídeo" : " vídeos");
            if (scanTotal > 0 && scanDone < scanTotal) t += "  \u00B7  buscando " + scanDone + "/" + scanTotal + "…";
        } else {
            t = "  \u00B7  " + current.items.size() + " elementos";
        }
        if (current.dayLo >= 0) {
            int lo = current.dayLo, hi = current.dayHi;
            String lab;
            if (lo == hi) lab = (lo % 100) + " " + FxLogic.MONTHS[(lo / 100) % 100] + " " + (lo / 10000);
            else if (lo / 100 == hi / 100) lab = FxLogic.MONTHS[(lo / 100) % 100] + " " + (lo / 10000);
            else lab = String.valueOf(lo / 10000);
            t += "  \u00B7  " + lab + ": " + current.shown().size();
        }
        if (pSel) t += "  \u00B7  " + pKeys.size() + " seleccionados";
        countView.setText(t);
    }

    /** Los vídeos ya conocidos de la lista actual, en su orden. */
    void rebuildVids() {
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
    void toggleKind() {
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

    void startScan() {
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

    void setOrder(String key) {
        if (current == null) return;
        current.order = key;
        current.orderSet = true;
        EntryOps.applyOrder(current);
        if (current.onlyVideos) rebuildVids();
        prefetched = -1;
        refreshOrderButtons();
        if (photoGrid != null) {
            ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
            photoGrid.setSelection(0);
        }
    }

    void refreshOrderButtons() {
        if (orderBtn == null) return;
        String o = current != null ? current.order : "";
        if ("random".equals(o)) { orderBtn.setText("Aleatorio"); Ui.icon(orderBtn, R.drawable.ic_shuffle); }
        else if ("asc".equals(o)) { orderBtn.setText("Antiguas primero"); Ui.icon(orderBtn, R.drawable.ic_up); }
        else { orderBtn.setText("Recientes primero"); Ui.icon(orderBtn, R.drawable.ic_down); }
    }





    View.OnClickListener play() { return play(false); }

    /** Con «music», el pase suena con la emisora o carpeta de música aunque en Ajustes esté apagada. */
    View.OnClickListener play(final boolean music) {
        return new View.OnClickListener() {
            public void onClick(View v) {
                if (current == null || current.items.isEmpty()) return;
                if (music && Prefs.musicRadio(BrowserActivity.this).length() == 0 && Prefs.musicFolder(BrowserActivity.this).length() == 0) {
                    showToast("Elige antes una emisora o una carpeta de música en Ajustes.");
                    return;
                }
                Session.items = current.shown();
                Session.order = !current.orderSet && current.kind != 3 ? "random" : "keep";   // sin elegir orden: aleatorio
                Session.manual = false;
                Session.guest = current.kind == 4;
                Session.guestId = current.kind == 4 ? current.gid : null;
                Session.startKey = null;
                Session.lastKey = null;
                Session.music = music ? 1 : 0;
                Session.musicUrl = current.kind == 0 ? BrowseUtil.nullIfEmpty(Extras.albumMusic(BrowserActivity.this, current.url)) : null;
                startActivity(new Intent(BrowserActivity.this, PlayerActivity.class));
            }
        };
    }

    void openViewer(int pos) {
        if (current == null || pos < 0 || pos >= current.shown().size()) return;
        Session.items = current.shown();
        Session.order = "keep";
        Session.manual = true;
        Session.guest = current.kind == 4;
        Session.guestId = current.kind == 4 ? current.gid : null;
        Session.startKey = current.shown().get(pos).key();
        Session.lastKey = null;
        Session.music = 0;
        Session.musicUrl = null;
        startActivity(new Intent(this, PlayerActivity.class));
    }



    static int r32(int v) { return ((v + 31) / 32) * 32; }

    class AlbumAdapter extends BaseAdapter {
        public int getCount() { return entries.size(); }
        public Object getItem(int i) { return entries.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Tile t = cv instanceof Tile ? (Tile) cv : Tiles.albumTile(BrowserActivity.this, albumTileH);
            Entry e = entries.get(pos);
            boolean isSel = selMode && sel.contains(e);
            t.name.setText(isSel ? "\u2713 " + e.title : e.title);
            t.setAlpha(selMode && !isSel ? 0.5f : 1f);
            int ic = e.kind == 1 ? R.drawable.ic_albums : e.kind == 2 ? R.drawable.ic_star
                    : e.kind == 3 ? R.drawable.ic_visibility_off : e.kind == 4 ? R.drawable.ic_schedule
                    : e.kind == 6 ? R.drawable.ic_search : 0;
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
                boolean off = e.kind == 0 && offUrls.contains(e.url);
                int nn = 0;
                if (!e.newKeys.isEmpty()) for (Item x : e.items) if (e.newKeys.contains(x.key())) nn++;   // sin las ocultas
                t.count.setText(e.items.size() + " elementos" + (nn > 0 ? " \u00B7 " + nn + (nn == 1 ? " nueva" : " nuevas") : "")
                        + (off ? " \u00B7 oculto en el salvapantallas" : ""));
                t.count.setTextColor(off ? 0xFFFFB454 : nn > 0 ? 0xFF7CE08A : 0xFFD0DBE8);
            }
            t.pin.setVisibility(e == moveEntry ? View.VISIBLE : View.GONE);
            t.setForeground(e == moveEntry ? selFrame() : null);
            if (e.items.isEmpty()) thumbs.clearView(t.img);
            else if (e.kind == 3) thumbs.loadBlur(t.img, (e.cover != null ? e.cover : e.items.get(0)).url, r32(albumTileW), r32(albumTileH));
            else thumbs.load(t.img, (e.cover != null ? e.cover : e.items.get(0)).url, r32(albumTileW), r32(albumTileH));
            return t;
        }
    }


    class PhotoAdapter extends BaseAdapter {
        public int getCount() { return current == null ? 0 : current.shown().size(); }
        public Object getItem(int i) { return current.shown().get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Tile t = cv instanceof Tile ? (Tile) cv : Tiles.photoTile(BrowserActivity.this, photoRatio);
            Item it = current.shown().get(pos);
            boolean crop = !"full".equals(photoShape);
            t.img.setScaleType(crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            thumbs.load(t.img, it.url, r32(photoTile), r32(photoTileH), crop);
            t.fav.setVisibility(favSet.contains(it.key()) ? View.VISIBLE : View.GONE);
            t.pin.setVisibility(current.kind == 0 && current.coverKeys.contains(it.key()) ? View.VISIBLE : View.GONE);
            t.fresh.setVisibility(current.newKeys.contains(it.key()) ? View.VISIBLE : View.GONE);
            boolean on = pSel && pKeys.contains(it.key());
            t.setAlpha(pSel && !on ? 0.5f : 1f);
            t.setForeground(on ? selFrame() : null);
            prefetchAhead(pos);
            return t;
        }
    }

    /** Va bajando al disco las miniaturas que vienen detrás de la que se está viendo. */
    void prefetchAhead(int pos) {
        List<Item> l = current.shown();
        int to = Math.min(l.size() - 1, pos + AHEAD);
        for (int i = Math.max(prefetched + 1, pos + 1); i <= to; i++)
            thumbs.prefetch(l.get(i).url, r32(photoTile), r32(photoTileH), !"full".equals(photoShape));
        if (to > prefetched) prefetched = to;
    }

    android.graphics.drawable.Drawable selFrameD;

    android.graphics.drawable.Drawable selFrame() {
        if (selFrameD == null) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x334DA3FF);
            g.setStroke(Ui.dp(this, 4), 0xFF4DA3FF);
            selFrameD = g;
        }
        return selFrameD;
    }


    // ------------------------------------------------------------ marcas de vídeo (por lotes)
}
