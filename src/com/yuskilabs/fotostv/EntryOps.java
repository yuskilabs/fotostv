package com.yuskilabs.fotostv;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Operaciones sobre los álbumes del explorador: crear, ordenar, elegir portada, fotos nuevas y álbumes virtuales. */
final class EntryOps {
    private EntryOps() {}

    /** Álbumes virtuales: búsquedas guardadas, calculadas con las fotos de los álbumes reales. Fuera del hilo de pantalla. */
    static List<Entry> buildVirtual(Context c, List<Entry> reals) {
        List<Entry> out = new ArrayList<Entry>();
        List<String[]> defs = Extras.virtualAlbums(c);
        if (defs.isEmpty()) return out;
        List<Item> all = new ArrayList<Item>();
        Set<String> seen = new HashSet<String>();
        for (Entry e : reals) for (Item it : e.src) if (seen.add(it.key())) all.add(it);
        java.util.HashMap<String, String> places = Search.places(c);
        for (String[] d : defs) {
            Entry v = new Entry();
            v.kind = 6;
            v.title = d[0];
            v.query = d[1];
            v.url = "virtual:" + d[1];
            v.src = Search.runSuggestion(c, all, places, d[1]);
            v.items = v.src;
            out.add(v);
        }
        return out;
    }
    /** Fotos del álbum que no estaban la última vez que se abrió. La primera vez solo se apunta lo que hay. */
    static void computeNew(Context c, Entry e) {
        e.newKeys = new HashSet<String>();
        if (e.kind != 0 || !Extras.on(c, Extras.NEW_BADGE)) return;
        Set<String> known = Prefs.meta(c).getStringSet("kn_" + e.url, null);
        if (known == null) { markKnown(c, e); return; }
        for (Item it : e.src) if (!known.contains(it.key())) e.newKeys.add(it.key());
    }
    static void markKnown(Context c, Entry e) {
        Set<String> ks = new HashSet<String>();
        for (Item it : e.src) ks.add(it.key());
        Prefs.meta(c).edit().putStringSet("kn_" + e.url, ks).apply();
    }
    static Entry makeEntry(Prefs.Album al, AlbumFetcher.AlbumData d) {
        Entry e = new Entry();
        e.url = al.url;
        String base = al.name.length() > 0 ? al.name : (d.title.length() > 0 ? d.title : "Álbum");
        e.owner = al.owner >= 0 ? al.ownerName : "";
        e.title = e.owner.length() > 0 ? base + " · " + e.owner : base;
        for (Item it : d.items) {
            it.album = base;
            if (it.akey.length() == 0) it.akey = d.key;
        }
        e.src = d.items;
        e.items = d.items;
        return e;
    }
    /** Portada del álbum: una foto al azar (la portada elegida en Google no viaja en el enlace). */
    static void pickCover(Context c, Entry e) {
        e.cover = null;
        if (e.items.isEmpty()) return;
        java.util.Random r = new java.util.Random();
        for (int t = 0; t < 8; t++) {
            Item it = e.items.get(r.nextInt(e.items.size()));
            Boolean v = MetaFetcher.cachedVideo(c, it);
            e.cover = it;
            if (v == null || !v.booleanValue()) return;
        }
    }
    static void applyOrder(Entry e) {
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
    /** Aplica el orden elegido: el de la lista (manual) o por la fecha de la última foto de cada álbum. */
    static List<Entry> sortEntries(Context c, List<Entry> list) {
        final String mode = Prefs.albumSort(c);
        if (!"newest".equals(mode) && !"oldest".equals(mode)) return list;
        final boolean newest = "newest".equals(mode);
        final java.util.Map<Entry, Long> ts = new java.util.IdentityHashMap<Entry, Long>();
        for (Entry e : list) ts.put(e, BrowseUtil.lastTs(e));
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
    static void reapply(Entry e) {
        if ("random".equals(e.order) && e.view != null) {
            java.util.Set<String> now = new HashSet<String>();
            for (Item x : e.items) now.add(x.key());
            java.util.Set<String> have = new HashSet<String>();
            List<Item> nv = new ArrayList<Item>();
            for (Item x : e.view) if (now.contains(x.key()) && have.add(x.key())) nv.add(x);
            for (Item x : e.items) if (have.add(x.key())) nv.add(x);
            e.view = nv;
        } else EntryOps.applyOrder(e);
    }
    /**
     * Portada fijada por el usuario si existe y la foto se ve (con varias, una de ellas al azar, que se mantiene
     * mientras dure la pantalla); si no, una al azar que se mantiene mientras sirva.
     */
    static void applyCover(Context c, Entry e, java.util.Set<String> hid) {
        e.coverKeys = Extras.covers(c, e.url);
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
        if (!in) pickCover(c, e);
    }
}
