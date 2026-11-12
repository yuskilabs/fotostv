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
        int kind;                 // 0 álbum normal, 1 Todas, 2 Favoritos, 3 Ocultas, 4 temporal de invitado, 5 lugar o búsqueda, 6 álbum virtual
        String gid = "";          // (temporal) identificador
        long expires;             // (temporal) cuándo caduca
        List<Item> src = new ArrayList<Item>();     // todo lo del álbum
        List<Item> items = new ArrayList<Item>();   // lo que se ve (sin las ocultas, salvo en «Ocultas»)
        List<Item> view;          // lista ordenada (null = tal cual la da Google)
        String order = "desc";   // random | asc | desc: por defecto, recientes primero
        boolean orderSet;        // el usuario eligió el orden (si no, el pase del álbum va aleatorio)
        Item cover;
        boolean coverPinned;     // portada fijada por el usuario (si no, al azar)
        List<String> coverKeys = new ArrayList<String>();   // portadas fijadas (varias con la función extra)
        String query;            // búsqueda que genera el álbum (virtual, o mosaico que viene de «Buscar»)
        Set<String> newKeys = new HashSet<String>();      // fotos nuevas desde la última visita (función extra)

        boolean onlyVideos;       // filtro «Vídeos»
        List<Item> vids = new ArrayList<Item>();

        int dayLo = -1, dayHi = -1;   // filtro «Ir a la fecha»: de un día, un mes o un año (claves de día; -1 = todas)
        private List<Item> dayOf, dayList;
        private int dayN = -1, dayCLo = -2, dayCHi = -2;

        List<Item> base() { return onlyVideos ? vids : (view != null ? view : items); }

        List<Item> shown() {
            List<Item> b = base();
            if (dayLo < 0) return b;
            if (dayOf != b || dayN != b.size() || dayList == null || dayCLo != dayLo || dayCHi != dayHi) {
                java.util.TimeZone tz = java.util.TimeZone.getDefault();
                List<Item> r = new ArrayList<Item>();
                for (Item it : b) { int k = FxLogic.dayKey(it.ts, tz); if (k >= dayLo && k <= dayHi) r.add(it); }
                dayOf = b;
                dayN = b.size();
                dayCLo = dayLo;
                dayCHi = dayHi;
                dayList = r;
            }
            return dayList;
        }
    }

    /** Casilla con proporción fija (alto = ancho × ratio) para las cuadrículas. */
    private static class Tile extends FrameLayout {
        final float ratio;
        ImageView img;
        TextView name, count, badge, fresh;
        ImageView fav, pin;
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
    private boolean placeMode;
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
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
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

    /** Icono con su rótulo debajo (como «Mapa» y «Fecha»), para la esquina de arriba a la derecha. */
    private View iconBox(int icon, String label, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setFocusable(true);
        box.setClickable(true);
        int pd = Ui.dp(this, 4);
        box.setPadding(Ui.dp(this, 6), pd, Ui.dp(this, 6), pd);
        box.setMinimumWidth(Ui.dp(this, 68));
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFF3D8BFD);
        on.setCornerRadius(Ui.dp(this, 8));
        sl.addState(new int[]{android.R.attr.state_focused}, on);
        box.setBackground(sl);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        box.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        TextView lb = text(label, 12, 0xFF9FB3C8);          // gris, como el texto de los botones
        lb.setPadding(Ui.dp(this, 5), 0, Ui.dp(this, 5), 0);     // holgura a los lados: la última letra no se recorta
        lb.setGravity(Gravity.CENTER);
        lb.setSingleLine(true);
        lb.setEllipsize(null);
        lb.setIncludeFontPadding(false);
        box.addView(lb, new LinearLayout.LayoutParams(-2, -2));
        box.setOnClickListener(click);
        return box;
    }

    /** Arriba a la derecha: «Fecha» (si está activada) y «Mapa» (lugares y mapa de calor de las fotos de este álbum). */
    private View mapButton() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2), lp2 = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(Ui.dp(this, 4), 0, 0, 0);                // los iconos, juntos
        lp2.setMargins(Ui.dp(this, 4), 0, 0, 0);
        if (Extras.on(this, Extras.DATE_JUMP)) {
            wrap.addView(iconBox(R.drawable.ic_event, "Fecha", new View.OnClickListener() {
                public void onClick(View v) { dateJump(); }
            }), lp);
        }
        wrap.addView(iconBox(R.drawable.ic_map, "Mapa", new View.OnClickListener() {
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
            e.items = visible(e.src, Prefs.hidden(this));
            applyOrder(e);
            pickCover(e);
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
                current.items = visible(current.src, Prefs.hidden(this));
                reapply(current);
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
        if (selMode && level == 0) { exitSel(); return; }
        if (pSel && level == 1) { exitPSel(); return; }
        if (level == 1) {
            // primer «atrás»: sube a los botones; segundo: vuelve a los álbumes
            if (photoGrid != null && photoGrid.hasFocus() && playBtn != null) playBtn.requestFocus();
            else showAlbums();
        } else super.onBackPressed();
    }

    // ------------------------------------------------------------ datos
    /** «Ir al álbum» desde la ficha del pase: abre ese álbum y se coloca en la foto. */
    private void applyGoto() {
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
                final List<Entry> vl = buildVirtual(list);
                h.post(new Runnable() {
                    public void run() {
                        if (destroyed) return;
                        real = new ArrayList<Entry>(fin);
                        virt = vl;
                        for (Entry e : real) computeNew(e);
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
    private List<Entry> virt = new ArrayList<Entry>();

    /** Álbumes virtuales: búsquedas guardadas, calculadas con las fotos de los álbumes reales. Fuera del hilo de pantalla. */
    private List<Entry> buildVirtual(List<Entry> reals) {
        List<Entry> out = new ArrayList<Entry>();
        List<String[]> defs = Extras.virtualAlbums(this);
        if (defs.isEmpty()) return out;
        List<Item> all = new ArrayList<Item>();
        Set<String> seen = new HashSet<String>();
        for (Entry e : reals) for (Item it : e.src) if (seen.add(it.key())) all.add(it);
        java.util.HashMap<String, String> places = Search.places(this);
        for (String[] d : defs) {
            Entry v = new Entry();
            v.kind = 6;
            v.title = d[0];
            v.query = d[1];
            v.url = "virtual:" + d[1];
            v.src = Search.runSuggestion(all, places, d[1]);
            v.items = v.src;
            out.add(v);
        }
        return out;
    }

    /** Fotos del álbum que no estaban la última vez que se abrió. La primera vez solo se apunta lo que hay. */
    private void computeNew(Entry e) {
        e.newKeys = new HashSet<String>();
        if (e.kind != 0 || !Extras.on(this, Extras.NEW_BADGE)) return;
        Set<String> known = Prefs.meta(this).getStringSet("kn_" + e.url, null);
        if (known == null) { markKnown(e); return; }
        for (Item it : e.src) if (!known.contains(it.key())) e.newKeys.add(it.key());
    }

    private void markKnown(Entry e) {
        Set<String> ks = new HashSet<String>();
        for (Item it : e.src) ks.add(it.key());
        Prefs.meta(this).edit().putStringSet("kn_" + e.url, ks).apply();
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

    /**
     * Portada fijada por el usuario si existe y la foto se ve (con varias, una de ellas al azar, que se mantiene
     * mientras dure la pantalla); si no, una al azar que se mantiene mientras sirva.
     */
    private void applyCover(Entry e, java.util.Set<String> hid) {
        e.coverKeys = Extras.covers(this, e.url);
        e.coverPinned = false;
        if (!e.coverKeys.isEmpty()) {
            List<Item> pinned = new ArrayList<Item>();
            for (Item it : e.items) if (e.coverKeys.contains(it.key())) pinned.add(it);
            if (!pinned.isEmpty()) {
                if (e.cover == null || !pinned.contains(e.cover)) e.cover = pinned.get(new java.util.Random().nextInt(pinned.size()));
                e.coverPinned = true;
                return;
            }
        }
        boolean in = false;
        if (e.cover != null && !hid.contains(e.cover.key())) for (Item it : e.items) if (it == e.cover) { in = true; break; }
        if (!in) pickCover(e);
    }

    /** Rehace las listas que se ven: quita las ocultas, y arma Todas, Favoritos y Ocultas. */
    private final java.util.Map<String, Entry> guestMap = new java.util.HashMap<String, Entry>();

    private void compose() {
        java.util.Set<String> hid = Prefs.hidden(this);
        favSet = Prefs.favs(this);
        for (Entry e : real) {
            e.items = visible(e.src, hid);
            reapply(e);
            applyCover(e, hid);
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
                applyOrder(ge);
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
        if (Extras.on(this, Extras.VIRTUAL)) {
            for (Entry v : virt) {
                v.items = visible(v.src, hid);
                reapply(v);
                if (v.cover == null || hid.contains(v.cover.key()) || !v.items.contains(v.cover)) pickCover(v);
                entries.add(v);
            }
        }
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
                try {
                for (Prefs.Album al : Prefs.albums(BrowserActivity.this)) {
                    if (destroyed) return;
                    try {
                        AlbumFetcher.AlbumData d = AlbumFetcher.fetchAlbum(al.url);
                        Prefs.saveAlbumCache(BrowserActivity.this, al.url, d);
                        if (d.partial) continue;   // descarga recortada: se queda la lista que ya hay
                        fresh.add(makeEntry(al, d));
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

    private void applyFresh(List<Entry> fresh) {
        if (destroyed) return;
        boolean changed = false;
        for (Entry f : fresh) {
            for (Entry e : real) {
                if (e.url.equals(f.url) && e.src.size() != f.src.size()) {
                    e.src = f.src;            // se cambia la lista entera, no se modifica la anterior
                    if (e != current || level != 1) computeNew(e);
                    changed = true;
                }
            }
        }
        if (!changed) return;
        refreshAfterChange(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
    }


    // ------------------------------------------------------------ tareas en segundo plano (texto de colores junto al título)
    private final java.util.LinkedHashMap<String, Integer> busy = new java.util.LinkedHashMap<String, Integer>();
    private TextView busyView;

    private void busyStart(String label) {
        Integer n = busy.get(label);
        busy.put(label, n == null ? 1 : n + 1);
        busyUpdate();
    }

    private void busyEnd(String label) {
        Integer n = busy.get(label);
        if (n == null) return;
        if (n <= 1) busy.remove(label); else busy.put(label, n - 1);
        busyUpdate();
    }

    /** Aviso fijo abajo en el centro (donde salen los avisos), que no se quita mientras algo sigue en marcha. */
    private void ensureBusyView() {
        if (busyView != null) return;
        busyView = text("", 16, Color.WHITE);
        busyView.setTypeface(Ui.REG);
        busyView.setSingleLine(true);
        busyView.setGravity(Gravity.CENTER);
        busyView.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(0xE6323232);
        bgd.setCornerRadius(Ui.dp(this, 24));
        busyView.setBackground(bgd);
        busyView.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(this, 56);
        addContentView(busyView, lp);
    }

    private void busyUpdate() {
        ensureBusyView();
        if (busy.isEmpty()) { busyView.setVisibility(View.GONE); return; }
        StringBuilder sb = new StringBuilder();
        for (String k : busy.keySet()) { if (sb.length() > 0) sb.append("  ·  "); sb.append(k); }
        busyView.setText(sb.toString());
        busyView.setVisibility(View.VISIBLE);
        busyView.bringToFront();
    }

    /** «Revisando ubicaciones…» hasta que las fotos revisadas vuelven a tener lugar (o pasan 10 minutos). */
    private void watchGeo(final Set<String> keys) {
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
        if (placeMode) { finish(); return; }   // desde un lugar, «atrás» vuelve a Lugares
        if (level == 1 && current != null && current.kind == 0 && !current.newKeys.isEmpty()) {
            markKnown(current);                // ya vistas: dejan de ser nuevas
            current.newKeys.clear();
        }
        pSel = false;
        pKeys.clear();
        level = 0;
        photoGrid = null;
        computeLayout();
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = header();
        TextView title = text(selMode ? sel.size() + (sel.size() == 1 ? " álbum seleccionado" : " álbumes seleccionados") : "Álbumes", selMode ? 20 : 26, Color.WHITE);
        title.setTypeface(Ui.BLACK);
        title.setPadding(0, 0, 0, 0);
        selTitle = selMode ? title : null;
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        if (selMode) {
            head.addView(sbtn("Pase", R.drawable.ic_play, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) startPase(selected(), false); }
            }));
            head.addView(sbtn("Con música", R.drawable.ic_play, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) startPase(selected(), true); }
            }));
            head.addView(sbtn("Ocultar", R.drawable.ic_visibility_off, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) hideEntries(selected(), false); }
            }));
            head.addView(sbtn("Borrar", R.drawable.ic_delete, new View.OnClickListener() {
                public void onClick(View v) { if (!sel.isEmpty()) confirmDelete(selected()); }
            }));
            head.addView(sbtn("Cancelar", R.drawable.ic_close, new View.OnClickListener() {
                public void onClick(View v) { exitSel(); }
            }));
        } else head.addView(colsControl("album", ALBUM_COLS_RANGE, albumCols, "albumCols", true));
        col.addView(head, headParams());

        albumGrid = makeGrid(albumCols);
        albumGrid.setAdapter(new AlbumAdapter());
        albumGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (selMode) { if (pos >= 0 && pos < entries.size()) toggleSel(entries.get(pos)); } else openEntry(pos);
            }
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

    private boolean selMode;
    private boolean pSel;
    private final java.util.HashSet<String> pKeys = new java.util.HashSet<String>();
    private final java.util.HashSet<Entry> sel = new java.util.HashSet<Entry>();
    private java.util.Set<String> offUrls = new java.util.HashSet<String>();

    private void refreshOff() {
        java.util.Set<String> o = new java.util.HashSet<String>();
        for (Prefs.Album al : Prefs.albums(this)) if (!al.show) o.add(al.url);
        offUrls = o;
    }

    private void exitSel() {
        selMode = false;
        sel.clear();
        if (level == 0) showAlbums();
    }

    private List<Entry> selected() {
        List<Entry> l = new ArrayList<Entry>();
        for (Entry e : entries) if (sel.contains(e)) l.add(e);
        return l;
    }

    /** Pulsación larga en un álbum: pase, pase con música, recargar, ocultar, borrar o empezar a seleccionar. */
    /** Menú de la pulsación larga con iconos (el mismo estilo para álbumes y fotos). */
    private void iconMenu(String title, final String[] labels, final int[] icons, android.content.DialogInterface.OnClickListener l) {
        android.widget.ListAdapter ad = new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int i, View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getView(i, cv, parent);
                tv.setTypeface(Ui.REG);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);     // menús más compactos: caben sin desplazar
                tv.setMinHeight(Ui.dp(BrowserActivity.this, 36));
                tv.setMinimumHeight(Ui.dp(BrowserActivity.this, 36));
                tv.setPadding(tv.getPaddingLeft(), Ui.dp(BrowserActivity.this, 6), tv.getPaddingRight(), Ui.dp(BrowserActivity.this, 6));
                android.graphics.drawable.Drawable d = getResources().getDrawable(icons[i]).mutate();
                int sz = Ui.dp(BrowserActivity.this, 18);
                d.setBounds(0, 0, sz, sz);
                tv.setCompoundDrawables(d, null, null, null);
                tv.setCompoundDrawablePadding(Ui.dp(BrowserActivity.this, 14));
                tv.setGravity(Gravity.CENTER_VERTICAL);
                return tv;
            }
        };
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        if (title != null && title.length() > 0) b.setTitle(title);
        b.setAdapter(ad, l).show();
    }

    // ---- reordenar álbumes (con el mando)
    private Entry moveEntry;
    private List<Entry> moveBackup;

    private void startMove(Entry e) {
        if (!"manual".equals(Prefs.albumSort(this))) {
            Prefs.sp(this).edit().putString("albumSort", "manual").commit();
            showToast("El orden de los álbumes pasa a manual.");
        }
        moveEntry = e;
        moveBackup = new ArrayList<Entry>(real);
        showToast("Mueve el álbum con las flechas. OK: fijar la posición. Atrás: cancelar.");
        if (albumGrid != null) ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
    }

    private void applyRealOrder(List<Entry> order) {
        int off = real.isEmpty() ? entries.size() : Math.max(0, entries.indexOf(real.get(0)));
        entries.removeAll(real);
        real = new ArrayList<Entry>(order);
        entries.addAll(Math.min(off, entries.size()), real);
    }

    private void moveBy(int delta) {
        int i = real.indexOf(moveEntry);
        if (i < 0) return;
        int n = Math.max(0, Math.min(real.size() - 1, i + delta));
        if (n == i) return;
        List<Entry> l = new ArrayList<Entry>(real);
        l.remove(i);
        l.add(n, moveEntry);
        applyRealOrder(l);
        if (albumGrid != null) {
            ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
            albumGrid.setSelection(Math.max(0, entries.indexOf(moveEntry)));
        }
    }

    private void endMove(boolean keep) {
        if (keep) {
            List<Prefs.Album> old = Prefs.albums(this), nw = new ArrayList<Prefs.Album>();
            for (Entry x : real) for (Prefs.Album al : old) if (al.url.equals(x.url)) { nw.add(al); break; }
            for (Prefs.Album al : old) if (!nw.contains(al)) nw.add(al);
            Prefs.saveAlbums(this, nw);
            showToast("Posición fijada.");
        } else if (moveBackup != null) applyRealOrder(moveBackup);
        moveEntry = null;
        moveBackup = null;
        if (albumGrid != null) ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
    }

    /** Cambia el nombre del álbum solo dentro de FotosTV (no toca Google Fotos). */
    private void renameAlbum(final Entry e) {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setTextColor(Color.WHITE);
        in.setText(e.title);
        in.setSelection(in.getText().length());
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Nombre del álbum")
            .setMessage("Solo cambia aquí, en FotosTV. Déjalo vacío para volver al nombre de Google.")
            .setView(in)
            .setPositiveButton("Guardar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String nm = in.getText().toString().trim();
                    List<Prefs.Album> l = Prefs.albums(BrowserActivity.this);
                    for (Prefs.Album al : l) if (al.url.equals(e.url)) al.name = nm;
                    Prefs.saveAlbums(BrowserActivity.this, l);
                    String t = nm;
                    if (t.length() == 0) {
                        AlbumFetcher.AlbumData cd = Prefs.albumCache(BrowserActivity.this, e.url);
                        t = cd != null && cd.title.length() > 0 ? cd.title : "Álbum";
                    }
                    e.title = t;
                    for (Item it : e.src) it.album = t;
                    if (albumGrid != null) ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Pulsación larga en un álbum virtual: pase, nombre o borrarlo (las fotos no se tocan). */
    private void virtualMenu(final Entry e) {
        final List<Entry> one = new ArrayList<Entry>();
        one.add(e);
        iconMenu(e.title, new String[]{"Pase", "Cambiar nombre", "Borrar álbum virtual"},
                new int[]{R.drawable.ic_play, R.drawable.ic_tune, R.drawable.ic_delete},
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) startPase(one, false);
                        else if (w == 1) askName("Nombre del álbum virtual", e.title, new NameDone() {
                            public void done(String name) { renameVirtual(e, name); }
                        });
                        else {
                            List<String[]> l = Extras.virtualAlbums(BrowserActivity.this);
                            for (int i = l.size() - 1; i >= 0; i--) if (l.get(i)[1].equals(e.query)) l.remove(i);
                            Extras.saveVirtualAlbums(BrowserActivity.this, l);
                            virt.remove(e);
                            if (current == e) current = null;
                            refreshAfterChange(0);
                            showToast("Álbum virtual borrado (las fotos siguen en sus álbumes).");
                        }
                    }
                });
    }

    private void renameVirtual(Entry e, String name) {
        if (name.length() == 0) return;
        List<String[]> l = Extras.virtualAlbums(this);
        for (String[] x : l) if (x[1].equals(e.query)) x[0] = name;
        Extras.saveVirtualAlbums(this, l);
        e.title = name;
        if (albumGrid != null) ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
    }

    interface NameDone { void done(String name); }

    private void askName(String title, String current, final NameDone done) {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setTextColor(Color.WHITE);
        in.setText(current);
        in.setSelection(in.getText().length());
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(title)
            .setView(in)
            .setPositiveButton("Guardar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { done.done(in.getText().toString().trim()); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Guarda el mosaico de una búsqueda como álbum virtual (sale en la lista de álbumes y se actualiza solo). */
    private void saveVirtual() {
        if (current == null || current.query == null) return;
        askName("Guardar como álbum virtual", current.title, new NameDone() {
            public void done(String name) {
                if (name.length() == 0) return;
                List<String[]> l = Extras.virtualAlbums(BrowserActivity.this);
                for (int i = l.size() - 1; i >= 0; i--) if (l.get(i)[1].equals(current.query)) l.remove(i);
                l.add(new String[]{name, current.query});
                Extras.saveVirtualAlbums(BrowserActivity.this, l);
                showToast("Guardado: «" + name + "» sale en la lista de álbumes.");
            }
        });
    }

    /** Emisora propia del álbum (suena en el pase de ese álbum). */
    private void pickAlbumMusic(final Entry e) {
        final List<String[]> rows = new ArrayList<String[]>();
        rows.add(new String[]{"La de Ajustes (sin emisora propia)", ""});
        String own = Prefs.musicRadio(this);
        boolean ownIsPreset = false;
        for (String[] pr : Music.PRESETS) if (pr[1] != null && pr[1].equals(own)) ownIsPreset = true;
        if (own.length() > 0 && !ownIsPreset) rows.add(new String[]{"Tu emisora (la de la web)", own});
        for (String[] pr : Music.PRESETS) if (pr[1] != null) rows.add(new String[]{Music.shortName(pr[0]), pr[1]});
        String cur = Prefs.sp(this).getString("amusic_" + e.url, "");
        int sel = 0;
        String[] labels = new String[rows.size()];
        for (int i = 0; i < labels.length; i++) { labels[i] = rows.get(i)[0]; if (rows.get(i)[1].equals(cur)) sel = i; }
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Música del álbum «" + e.title + "»")
            .setSingleChoiceItems(labels, sel, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Extras.setAlbumMusic(BrowserActivity.this, e.url, rows.get(w)[1]);
                    d.dismiss();
                    showToast(w == 0 ? "El pase de este álbum usará la música de Ajustes." : "El pase de este álbum sonará con «" + rows.get(w)[0] + "».");
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    // ------------------------------------------------------------ fotos repetidas (función extra)
    private volatile boolean dupBusy;

    /** Busca fotos casi iguales hechas seguidas (a menos de 4 s) y las deja marcadas en el mosaico para ocultarlas. */
    private void findDupes(final Entry e) {
        if (dupBusy) { showToast("Ya estoy buscando repetidas…"); return; }
        dupBusy = true;
        busyStart("Buscando repetidas…");
        final List<Item> base = new ArrayList<Item>(e.items);
        bg.submit(new Runnable() {
            public void run() {
                final Set<String> extra = new HashSet<String>();
                try {
                    for (List<Item> burst : FxLogic.bursts(base, 4000)) {
                        if (destroyed) return;
                        List<Item> photos = new ArrayList<Item>();
                        for (Item it : burst) {
                            Boolean v = MetaFetcher.cachedVideo(BrowserActivity.this, it);
                            if (v == null || !v.booleanValue()) photos.add(it);
                        }
                        if (photos.size() < 2) continue;
                        long[] hs = new long[photos.size()];
                        boolean ok = true;
                        for (int i = 0; i < hs.length && ok; i++) {
                            Long hv = dhashOf(photos.get(i));
                            if (hv == null) ok = false; else hs[i] = hv;
                        }
                        if (!ok) continue;
                        for (int i : FxLogic.similarToEarlier(hs, 10)) extra.add(photos.get(i).key());
                    }
                } catch (Throwable ignored) { }
                h.post(new Runnable() {
                    public void run() {
                        dupBusy = false;
                        busyEnd("Buscando repetidas…");
                        if (destroyed) return;
                        if (extra.isEmpty()) { showToast("No he encontrado fotos repetidas en «" + e.title + "»."); return; }
                        int pos = entries.indexOf(e);
                        if (pos < 0) return;
                        if (level != 0 || selMode || moveEntry != null) {   // mientras, has abierto otra cosa: no se cambia de pantalla
                            pendingDupes = e;
                            pendingDupeKeys = extra;
                            showToast(extra.size() + (extra.size() == 1 ? " foto repetida" : " fotos repetidas") + " en «" + e.title + "»: ábrelo para verlas marcadas.");
                            return;
                        }
                        openEntry(pos);
                        markDupes(extra);
                        showToast(extra.size() + (extra.size() == 1 ? " foto repetida marcada" : " fotos repetidas marcadas")
                                + " (se queda la primera de cada grupo). Pulsa «Ocultar» para quitarlas o desmarca las que quieras.");
                    }
                });
            }
        });
    }

    private Entry pendingDupes;
    private Set<String> pendingDupeKeys;

    /** Deja marcadas las repetidas en el mosaico abierto (modo de selección, listas para «Ocultar»). */
    private void markDupes(Set<String> keys) {
        pSel = true;
        pKeys.clear();
        pKeys.addAll(keys);
        showPhotos(0);
    }

    /** Huella dHash de la miniatura (9×8 en grises); null si no se pudo bajar. */
    private Long dhashOf(Item it) {
        try {
            byte[] d = AlbumFetcher.get(it.url + "=w96-h96");
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(d, 0, d.length);
            if (b == null) return null;
            android.graphics.Bitmap s9 = android.graphics.Bitmap.createScaledBitmap(b, 9, 8, true);
            int[] g = new int[72];
            for (int y = 0; y < 8; y++) for (int x = 0; x < 9; x++) {
                int c = s9.getPixel(x, y);
                g[y * 9 + x] = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
            }
            if (s9 != b) s9.recycle();
            b.recycle();
            return FxLogic.dhash(g);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean albumMenu(int pos) {
        if (pos < 0 || pos >= entries.size()) return false;
        final Entry e = entries.get(pos);
        if (selMode) { toggleSel(e); return true; }
        if (e.kind == 0) {
            final boolean off = offUrls.contains(e.url);
            final List<Entry> one = new ArrayList<Entry>();
            one.add(e);
            final boolean pinned = e.coverPinned;
            final List<String> lb = new ArrayList<String>(java.util.Arrays.asList("Seleccionar", "Pase", "Pase con música", "Recargar álbum",
                    off ? "Mostrar álbum" : "Ocultar álbum", "Revisar ubicaciones"));
            final List<Integer> ic = new ArrayList<Integer>(java.util.Arrays.asList(R.drawable.ic_check, R.drawable.ic_play, R.drawable.ic_tune,
                    R.drawable.ic_refresh, off ? R.drawable.ic_visibility : R.drawable.ic_visibility_off, R.drawable.ic_place));
            if (Extras.on(this, Extras.ALBUM_MUSIC)) { lb.add("Música del álbum"); ic.add(R.drawable.ic_music); }
            if (Extras.on(this, Extras.DUPES)) { lb.add("Buscar repetidas"); ic.add(R.drawable.ic_copy); }
            if (pinned) { lb.add(e.coverKeys.size() > 1 ? "Quitar portadas" : "Quitar portada"); ic.add(R.drawable.ic_photo); }
            lb.add("Reordenar álbum"); ic.add(R.drawable.ic_move);
            lb.add("Cambiar nombre"); ic.add(R.drawable.ic_tune);
            lb.add("Borrar álbum"); ic.add(R.drawable.ic_delete);
            int[] ia = new int[ic.size()];
            for (int i = 0; i < ia.length; i++) ia[i] = ic.get(i);
            iconMenu(e.title, lb.toArray(new String[0]), ia,
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String a = lb.get(w);
                        if (a.equals("Seleccionar")) { selMode = true; sel.clear(); sel.add(e); albumPos = Math.max(0, entries.indexOf(e)); showAlbums(); }
                        else if (a.equals("Pase")) startPase(one, false);
                        else if (a.equals("Pase con música")) startPase(one, true);
                        else if (a.equals("Recargar álbum")) reloadAlbum(e);
                        else if (a.equals("Revisar ubicaciones")) recheckPlaces(one);
                        else if (a.equals("Música del álbum")) pickAlbumMusic(e);
                        else if (a.equals("Buscar repetidas")) findDupes(e);
                        else if (a.equals("Quitar portada") || a.equals("Quitar portadas")) {
                            Extras.setCovers(BrowserActivity.this, e.url, null);   // vuelve a ser al azar (como siempre)
                            e.coverPinned = false;
                            e.coverKeys = new ArrayList<String>();
                            pickCover(e);
                            if (albumGrid != null) ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
                            showToast("Portada quitada: vuelve a ser aleatoria.");
                        }
                        else if (a.equals("Reordenar álbum")) startMove(e);
                        else if (a.equals("Cambiar nombre")) renameAlbum(e);
                        else if (a.equals("Borrar álbum")) confirmDelete(one);
                        else hideEntries(one, off);
                    }
                });
            return true;
        }
        if (e.kind == 6) { virtualMenu(e); return true; }
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

    private void toggleSel(Entry e) {
        if (e.kind != 0) return;
        if (!sel.remove(e)) sel.add(e);
        if (albumGrid != null) {
            int pos = entries.indexOf(e);
            ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
            if (pos >= 0) albumGrid.setSelection(pos);
        }
        if (selTitle != null) selTitle.setText(sel.size() + (sel.size() == 1 ? " álbum seleccionado" : " álbumes seleccionados"));
    }

    private android.widget.TextView selTitle;

    /** Abre un pase con las fotos de los álbumes dados (mezclados), con música o según los ajustes. */
    private void startPase(List<Entry> es, boolean music) {
        List<Item> all = new ArrayList<Item>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (Entry x : es) for (Item it : x.items) if (seen.add(it.key())) all.add(it);
        if (all.isEmpty()) { showToast("No hay nada que mostrar en esos álbumes."); return; }
        if (music && Prefs.musicRadio(this).length() == 0 && Prefs.musicFolder(this).length() == 0) {
            showToast("Elige antes una emisora o una carpeta de música.");
            music = false;
        }
        Session.items = all;
        Session.order = "random";
        Session.manual = false;
        Session.guest = false;
        Session.guestId = null;
        Session.startKey = null;
        Session.lastKey = null;
        Session.music = music ? 1 : 0;
        Session.musicUrl = es.size() == 1 && es.get(0).kind == 0 ? nullIfEmpty(Extras.albumMusic(this, es.get(0).url)) : null;
        selMode = false;
        sel.clear();
        startActivity(new Intent(this, PlayerActivity.class));
    }

    /** Oculta (o vuelve a mostrar) los álbumes en el salvapantallas. */
    private void hideEntries(List<Entry> es, boolean show) {
        List<Prefs.Album> l = Prefs.albums(this);
        for (Prefs.Album al : l) for (Entry x : es) if (al.url.equals(x.url)) al.show = show;
        Prefs.saveAlbums(this, l);
        if (!show && "all".equals(Prefs.albumMode(this))) Prefs.sp(this).edit().putString("albumMode", "selected").apply();
        refreshOff();
        showToast(show ? "Se mostrará en el salvapantallas." : "Oculto del salvapantallas (sigue en esta lista).");
        exitSel();
        if (level == 0 && albumGrid != null) ((BaseAdapter) albumGrid.getAdapter()).notifyDataSetChanged();
    }

    private void confirmDelete(final List<Entry> es) {
        String m = es.size() == 1 ? "Se quitará «" + es.get(0).title + "» de la lista."
                : "Se quitarán " + es.size() + " álbumes de la lista.";
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Borrar álbum")
            .setMessage(m + "\n\nLas fotos NO se borran de Google Fotos; si lo quieres de vuelta tendrás que añadir el enlace otra vez.")
            .setPositiveButton("Borrar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { deleteEntries(es); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void deleteEntries(List<Entry> es) {
        java.util.HashSet<String> urls = new java.util.HashSet<String>();
        for (Entry x : es) urls.add(x.url);
        List<Prefs.Album> keep = new ArrayList<Prefs.Album>();
        for (Prefs.Album al : Prefs.albums(this)) {
            if (urls.contains(al.url)) Prefs.deleteAlbumCache(this, al.url); else keep.add(al);
        }
        Prefs.saveAlbums(this, keep);
        List<Entry> nr = new ArrayList<Entry>();
        for (Entry x : real) if (!urls.contains(x.url)) nr.add(x);
        real = nr;
        selMode = false;
        sel.clear();
        refreshOff();
        compose();
        if (entries.isEmpty()) showMessage("No hay álbumes.\nAñádelos desde la web de ajustes de FotosTV.");
        else showAlbums();
    }

    private boolean reloading;
    private android.widget.Button reloadBtn;
    private Spin reloadSpin;
    private android.animation.ValueAnimator spinAnim;

    /** Icono que gira mientras se recarga. */
    private static class Spin extends android.graphics.drawable.Drawable {
        final android.graphics.drawable.Drawable d;
        float angle;
        Spin(android.graphics.drawable.Drawable d) { this.d = d; }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            c.save();
            c.rotate(angle, b.exactCenterX(), b.exactCenterY());
            d.setBounds(b);
            d.draw(c);
            c.restore();
        }
        @Override public void setAlpha(int a) { d.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { d.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void startSpin() {
        if (reloadBtn == null || reloadSpin == null) return;
        if (spinAnim != null) spinAnim.cancel();
        spinAnim = android.animation.ValueAnimator.ofFloat(0f, 360f);
        spinAnim.setDuration(900);
        spinAnim.setInterpolator(new android.view.animation.LinearInterpolator());
        spinAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        spinAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                reloadSpin.angle = ((Float) a.getAnimatedValue()).floatValue();
                reloadSpin.invalidateSelf();
                if (reloadBtn != null) reloadBtn.invalidate();
            }
        });
        spinAnim.start();
    }

    private void stopSpin() {
        if (spinAnim != null) { spinAnim.cancel(); spinAnim = null; }
        if (reloadSpin != null) { reloadSpin.angle = 0; reloadSpin.invalidateSelf(); }
        if (reloadBtn != null) reloadBtn.invalidate();
    }

    /** Vuelve a leer un solo álbum: fotos nuevas, título y ubicaciones que faltaban. */
    private void reloadAlbum(final Entry e) {
        if (e == null || e.kind != 0 || reloading) return;
        reloading = true;
        startSpin();
        busyStart("Recargando álbum…");
        final List<Item> old = new ArrayList<Item>(e.src);
        bg.submit(new Runnable() {
            public void run() {
                String msg;
                AlbumFetcher.AlbumData d = null;
                int regps = 0;
                try {
                    d = AlbumFetcher.fetchAlbum(e.url);
                    if (d.partial) { d = null; msg = "No se ha podido leer el álbum completo. Inténtalo de nuevo."; }
                    else {
                        Prefs.saveAlbumCache(BrowserActivity.this, e.url, d);
                        regps = MetaFetcher.forgetMissingGps(BrowserActivity.this, d.items);
                        msg = "\u2714 «" + e.title + "» recargado: " + d.items.size() + " elementos"
                                + (regps > 0 ? ". Se vuelve a buscar la ubicación de " + regps + "." : ".");
                    }
                } catch (Exception ex) {
                    msg = "No se ha podido recargar (¿hay conexión?).";
                }
                final AlbumFetcher.AlbumData fd = d;
                final String fm = msg;
                h.post(new Runnable() {
                    public void run() {
                        reloading = false;
                        stopSpin();
                        busyEnd("Recargando álbum…");
                        if (destroyed) return;
                        if (fd != null) {
                            for (Item it : fd.items) {
                                it.album = e.title;
                                if (it.akey.length() == 0) it.akey = fd.key;
                            }
                            e.src = fd.items;
                            refreshAfterChange(photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0);
                        }
                        showToast(fm);
                    }
                });
            }
        });
    }

    private void showToast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
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
        pSel = false;
        pKeys.clear();
        albumPos = pos;
        current = entries.get(pos);
        current.onlyVideos = false;
        current.dayLo = -1;
        current.dayHi = -1;
        scanGen++;
        if (pendingDupes == current && pendingDupeKeys != null) {   // repetidas que se encontraron mientras estabas en otro sitio
            Set<String> k = pendingDupeKeys;
            pendingDupes = null;
            pendingDupeKeys = null;
            markDupes(k);
            return;
        }
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
        nm.setMaxWidth(getResources().getDisplayMetrics().widthPixels * 38 / 100);
        head.addView(nm, new LinearLayout.LayoutParams(-2, -2));
        countView = text("", 20, 0xFFD0DBE8);
        countView.setPadding(0, 0, 0, 0);
        countView.setSingleLine(true);
        head.addView(countView);
        updateCount();
        if (current.kind == 0 && !placeMode) head.addView(mapButton());
        else if (Extras.on(this, Extras.DATE_JUMP)) {
            LinearLayout w2 = new LinearLayout(this);
            w2.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            w2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            w2.addView(iconBox(R.drawable.ic_event, "Fecha", new View.OnClickListener() {
                public void onClick(View v) { dateJump(); }
            }));
            head.addView(w2);
        }
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
        if (current.kind == 5 && current.query != null && Extras.on(this, Extras.VIRTUAL)) {
            row.addView(sbtn("Guardar", R.drawable.ic_albums, new View.OnClickListener() {
                public void onClick(View v) { saveVirtual(); }
            }));
        }
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
        if (pSel) {
            row.removeAllViews();
            row.addView(sbtn("Todas", R.drawable.ic_check, new View.OnClickListener() {
                public void onClick(View v) {
                    for (Item it : current.shown()) pKeys.add(it.key());
                    refreshPhotoSel();
                }
            }));
            row.addView(sbtn(current.kind == 3 ? "Mostrar" : "Ocultar", current.kind == 3 ? R.drawable.ic_visibility : R.drawable.ic_visibility_off,
                    new View.OnClickListener() {
                public void onClick(View v) { applyPhotoSel(0); }
            }));
            row.addView(sbtn("A favoritos", R.drawable.ic_star, new View.OnClickListener() {
                public void onClick(View v) { applyPhotoSel(1); }
            }));
            row.addView(sbtn("Quitar de favoritos", R.drawable.ic_star_border, new View.OnClickListener() {
                public void onClick(View v) { applyPhotoSel(2); }
            }));
            row.addView(sbtn("Revisar ubicación", R.drawable.ic_place, new View.OnClickListener() {
                public void onClick(View v) {
                    if (pKeys.isEmpty()) { showToast("Marca primero alguna foto o vídeo."); return; }
                    List<Item> l = new ArrayList<Item>();
                    for (Item it : current.shown()) if (pKeys.contains(it.key())) l.add(it);
                    exitPSel();
                    recheckItems(l);
                }
            }));
            row.addView(sbtn("Cancelar", R.drawable.ic_close, new View.OnClickListener() {
                public void onClick(View v) { exitPSel(); }
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

        photoGrid = makeGrid(photoCols);
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
                if (pSel) togglePhoto(pos); else openViewer(pos);
            }
        });
        photoGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pSel) togglePhoto(pos); else itemMenu(pos);
                return true;
            }
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
        if (pSel) photoGrid.requestFocus();
        else if ("minus".equals(refocus)) colsMinus.requestFocus();
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
        final boolean canCover = current.kind == 0;
        final boolean multi = Extras.on(this, Extras.MULTI_COVER);
        final boolean isCover = current.coverKeys.contains(it.key());
        final List<String> lb = new ArrayList<String>();
        final List<Integer> ic = new ArrayList<Integer>();
        lb.add("Seleccionar"); ic.add(R.drawable.ic_check);
        lb.add(isHid ? "Mostrar" : "Ocultar"); ic.add(isHid ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        lb.add("Ver información"); ic.add(R.drawable.ic_info);
        lb.add(isFav ? "Quitar de favoritos" : "Añadir a favoritos"); ic.add(isFav ? R.drawable.ic_star_border : R.drawable.ic_star);
        lb.add("Revisar ubicación"); ic.add(R.drawable.ic_place);
        if (canCover) {
            lb.add(multi ? (isCover ? "Quitar de portadas" : "Añadir a portadas") : "Fijar como portada");
            ic.add(R.drawable.ic_photo);
        }
        if (Extras.on(this, Extras.CAPTIONS)) { lb.add("Pie de foto"); ic.add(R.drawable.ic_notes); }
        final String[] labels = lb.toArray(new String[0]);
        final int[] icons = new int[ic.size()];
        for (int i = 0; i < icons.length; i++) icons[i] = ic.get(i);
        android.widget.ListAdapter ad = new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int i, View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getView(i, cv, parent);
                tv.setTypeface(Ui.REG);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);     // menús más compactos: caben sin desplazar
                tv.setMinHeight(Ui.dp(BrowserActivity.this, 36));
                tv.setMinimumHeight(Ui.dp(BrowserActivity.this, 36));
                tv.setPadding(tv.getPaddingLeft(), Ui.dp(BrowserActivity.this, 6), tv.getPaddingRight(), Ui.dp(BrowserActivity.this, 6));
                android.graphics.drawable.Drawable d = getResources().getDrawable(icons[i]).mutate();
                int sz = Ui.dp(BrowserActivity.this, 18);
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
                    String a = labels[which];
                    if (a.equals("Seleccionar")) {
                        pSel = true;
                        pKeys.clear();
                        pKeys.add(it.key());
                        showPhotos(pos);
                    } else if (a.equals("Mostrar") || a.equals("Ocultar")) {
                        if (isHid) Prefs.unhide(BrowserActivity.this, it.key()); else Prefs.hide(BrowserActivity.this, it.key());
                        refreshAfterChange(pos);
                    } else if (a.equals("Ver información")) {
                        showInfo(it);
                    } else if (a.equals("Revisar ubicación")) {
                        List<Item> l = new ArrayList<Item>();
                        l.add(it);
                        recheckItems(l);
                    } else if (a.equals("Fijar como portada") || a.equals("Añadir a portadas") || a.equals("Quitar de portadas")) {
                        // sin «Varias portadas» solo se cambia la primera: las demás se guardan por si se vuelve a activar
                        List<String> keys = Extras.allCovers(BrowserActivity.this, current.url);
                        if (a.equals("Quitar de portadas")) keys.remove(it.key());
                        else if (multi) { if (!keys.contains(it.key())) keys.add(it.key()); }
                        else if (keys.isEmpty()) keys.add(it.key());
                        else { keys.remove(it.key()); if (keys.isEmpty()) keys.add(it.key()); else keys.set(0, it.key()); }
                        Extras.setCovers(BrowserActivity.this, current.url, keys);
                        current.coverKeys = Extras.covers(BrowserActivity.this, current.url);
                        current.coverPinned = !current.coverKeys.isEmpty();
                        if (a.equals("Quitar de portadas")) { if (current.cover == it) current.cover = null; }
                        else current.cover = it;
                        if (current.cover == null || !current.coverPinned) applyCover(current, Prefs.hidden(BrowserActivity.this));
                        if (photoGrid != null) ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
                        showToast(a.equals("Quitar de portadas") ? "Quitada de las portadas del álbum."
                                : multi ? "Añadida a las portadas del álbum (" + current.coverKeys.size() + ")." : "Portada del álbum fijada.");
                    } else if (a.equals("Pie de foto")) {
                        askName("Pie de foto (vacío para quitarlo)", Extras.rawCaption(BrowserActivity.this, it.key()), new NameDone() {
                            public void done(String text) {
                                Extras.setCaption(BrowserActivity.this, it.key(), text);
                                showToast(text.length() == 0 ? "Pie de foto quitado." : "Pie de foto guardado: sale en el pase y en la ficha.");
                            }
                        });
                    } else {
                        Prefs.toggleFav(BrowserActivity.this, it.key());
                        refreshAfterChange(pos);
                    }
                }
            }).show();
    }

    /** «Fecha» (función extra): lista de meses del álbum; al elegir uno, el mosaico va a su primera foto. */
    private void dateJump() {
        if (current == null) return;
        final Entry e = current;
        final List<Item> l = e.base();
        final java.util.TreeMap<Integer, Integer> days = new java.util.TreeMap<Integer, Integer>();
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        for (Item it : l) {
            int k = FxLogic.dayKey(it.ts, tz);
            if (k < 0) continue;
            Integer n = days.get(k);
            days.put(k, n == null ? 1 : n + 1);
        }
        if (days.isEmpty()) { showToast("Este álbum no tiene fotos con fecha."); return; }
        int start = e.dayLo;
        if (start < 0 && photoGrid != null) {
            int p = photoGrid.getSelectedItemPosition();
            List<Item> sh = e.shown();
            if (p >= 0 && p < sh.size()) start = FxLogic.dayKey(sh.get(p).ts, tz);
        }
        new CalendarDialog(this, days, start, e.dayLo >= 0, new CalendarDialog.Listener() {
            public void onRange(int lo, int hi) { e.dayLo = lo; e.dayHi = hi; showPhotos(0); }
            public void onAll() { e.dayLo = -1; e.dayHi = -1; showPhotos(0); }
        }).show();
    }

    private void togglePhoto(int pos) {
        if (current == null || pos < 0 || pos >= current.shown().size()) return;
        String k = current.shown().get(pos).key();
        if (!pKeys.remove(k)) pKeys.add(k);
        if (photoGrid != null) {
            ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
            photoGrid.setSelection(pos);
        }
        updateCount();
    }

    private void refreshPhotoSel() {
        if (photoGrid != null) {
            int p = photoGrid.getSelectedItemPosition();
            ((BaseAdapter) photoGrid.getAdapter()).notifyDataSetChanged();
            if (p >= 0) photoGrid.setSelection(p);
        }
        updateCount();
    }

    private void exitPSel() {
        int fp = photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0;
        pSel = false;
        pKeys.clear();
        showPhotos(fp);
    }

    /** Vuelve a leer la ubicación y el nombre del lugar de estas fotos (en segundo plano). */
    private void recheckItems(final List<Item> l) {
        final Set<String> ks = new HashSet<String>();
        for (Item it : l) ks.add(it.key());
        new Thread(new Runnable() {
            public void run() { try { GeoIndex.recheck(BrowserActivity.this, l); } catch (Throwable ignored) { } }
        }).start();
        watchGeo(ks);
    }

    private void recheckPlaces(List<Entry> es) {
        List<Item> l = new ArrayList<Item>();
        for (Entry e : es) l.addAll(e.src);
        recheckItems(l);
    }

    /** 0 = ocultar o mostrar, 1 = añadir a favoritas, 2 = quitar de favoritas, para todas las seleccionadas. */
    private void applyPhotoSel(int what) {
        if (pKeys.isEmpty()) { showToast("Marca primero alguna foto o vídeo."); return; }
        List<String> ks = new ArrayList<String>(pKeys);
        int fp = photoGrid != null ? photoGrid.getFirstVisiblePosition() : 0;
        if (what == 0) Prefs.hideAll(this, ks, current.kind != 3);
        else Prefs.favAll(this, ks, what == 1);
        showToast(ks.size() + (ks.size() == 1 ? " elemento cambiado." : " elementos cambiados."));
        pSel = false;
        pKeys.clear();
        refreshAfterChange(fp);
        if (level == 1 && current != null && entries.contains(current)) showPhotos(fp);
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
        if (moveEntry != null && level == 0) {        // reordenando un álbum
            int k = e.getKeyCode();
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (k == android.view.KeyEvent.KEYCODE_DPAD_LEFT) moveBy(-1);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) moveBy(1);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_UP) moveBy(-albumCols);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_DOWN) moveBy(albumCols);
                else if (k == android.view.KeyEvent.KEYCODE_DPAD_CENTER || k == android.view.KeyEvent.KEYCODE_ENTER) endMove(true);
                else if (k == android.view.KeyEvent.KEYCODE_BACK || k == android.view.KeyEvent.KEYCODE_ESCAPE) endMove(false);
            }
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
        current.orderSet = true;
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

    private static String nullIfEmpty(String s) { return s == null || s.length() == 0 ? null : s; }

    private View.OnClickListener play() { return play(false); }

    /** Con «music», el pase suena con la emisora o carpeta de música aunque en Ajustes esté apagada. */
    private View.OnClickListener play(final boolean music) {
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
                Session.musicUrl = current.kind == 0 ? nullIfEmpty(Extras.albumMusic(BrowserActivity.this, current.url)) : null;
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
        Session.music = 0;
        Session.musicUrl = null;
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
        t.pin = new ImageView(this);
        t.pin.setImageResource(R.drawable.ic_move);
        t.pin.setColorFilter(Color.WHITE);
        GradientDrawable mbg = new GradientDrawable();
        mbg.setShape(GradientDrawable.OVAL);
        mbg.setColor(0xCC3D8BFD);
        t.pin.setBackground(mbg);
        int mp = Ui.dp(this, 8);
        t.pin.setPadding(mp, mp, mp, mp);
        t.addView(t.pin, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.CENTER));
        t.pin.setVisibility(View.GONE);
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
    private void prefetchAhead(int pos) {
        List<Item> l = current.shown();
        int to = Math.min(l.size() - 1, pos + AHEAD);
        for (int i = Math.max(prefetched + 1, pos + 1); i <= to; i++)
            thumbs.prefetch(l.get(i).url, r32(photoTile), r32(photoTileH), !"full".equals(photoShape));
        if (to > prefetched) prefetched = to;
    }

    private android.graphics.drawable.Drawable selFrameD;

    private android.graphics.drawable.Drawable selFrame() {
        if (selFrameD == null) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x334DA3FF);
            g.setStroke(Ui.dp(this, 4), 0xFF4DA3FF);
            selFrameD = g;
        }
        return selFrameD;
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
        t.pin = new ImageView(this);
        t.pin.setImageResource(R.drawable.ic_photo);
        t.pin.setColorFilter(Color.WHITE);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setShape(GradientDrawable.OVAL);
        pbg.setColor(0xB3000000);
        t.pin.setBackground(pbg);
        int pp = Ui.dp(this, 5);
        t.pin.setPadding(pp, pp, pp, pp);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28), Gravity.TOP | Gravity.START);
        pl.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), 0, 0);
        t.addView(t.pin, pl);
        t.pin.setVisibility(View.GONE);
        t.fresh = new TextView(this);                 // «NUEVA» (función extra)
        t.fresh.setTypeface(Ui.BLACK);
        t.fresh.setText("NUEVA");
        t.fresh.setTextColor(0xFF0A1424);
        t.fresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.fresh.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        GradientDrawable nb = new GradientDrawable();
        nb.setColor(0xFF7CE08A);
        nb.setCornerRadius(Ui.dp(this, 9));
        t.fresh.setBackground(nb);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        nl.setMargins(0, 0, Ui.dp(this, 6), Ui.dp(this, 6));
        t.addView(t.fresh, nl);
        t.fresh.setVisibility(View.GONE);
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
