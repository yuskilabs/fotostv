package com.yuskilabs.fotostv;

import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Acciones del explorador: menús de álbum y foto, selección, mover, renombrar, borrar, repetidas y recargar. */
final class BrowserActions {
    final BrowserActivity host;

    BrowserActions(BrowserActivity host) { this.host = host; }



    void exitSel() {
        host.selMode = false;
        host.sel.clear();
        if (host.level == 0) host.showAlbums();
    }

    List<Entry> selected() {
        List<Entry> l = new ArrayList<Entry>();
        for (Entry e : host.entries) if (host.sel.contains(e)) l.add(e);
        return l;
    }

    void startMove(Entry e) {
        if (!"manual".equals(Prefs.albumSort(host))) {
            Prefs.sp(host).edit().putString("albumSort", "manual").commit();
            host.showToast("El orden de los álbumes pasa a manual.");
        }
        host.moveEntry = e;
        host.moveBackup = new ArrayList<Entry>(host.real);
        host.showToast("Mueve el álbum con las flechas. OK: fijar la posición. Atrás: cancelar.");
        if (host.albumGrid != null) ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
    }

    void applyRealOrder(List<Entry> order) {
        int off = host.real.isEmpty() ? host.entries.size() : Math.max(0, host.entries.indexOf(host.real.get(0)));
        host.entries.removeAll(host.real);
        host.real = new ArrayList<Entry>(order);
        host.entries.addAll(Math.min(off, host.entries.size()), host.real);
    }

    void moveBy(int delta) {
        int i = host.real.indexOf(host.moveEntry);
        if (i < 0) return;
        int n = Math.max(0, Math.min(host.real.size() - 1, i + delta));
        if (n == i) return;
        List<Entry> l = new ArrayList<Entry>(host.real);
        l.remove(i);
        l.add(n, host.moveEntry);
        applyRealOrder(l);
        if (host.albumGrid != null) {
            ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
            host.albumGrid.setSelection(Math.max(0, host.entries.indexOf(host.moveEntry)));
        }
    }

    void endMove(boolean keep) {
        if (keep) {
            List<Prefs.Album> old = Prefs.albums(host), nw = new ArrayList<Prefs.Album>();
            for (Entry x : host.real) for (Prefs.Album al : old) if (al.url.equals(x.url)) { nw.add(al); break; }
            for (Prefs.Album al : old) if (!nw.contains(al)) nw.add(al);
            Prefs.saveAlbums(host, nw);
            host.showToast("Posición fijada.");
        } else if (host.moveBackup != null) applyRealOrder(host.moveBackup);
        host.moveEntry = null;
        host.moveBackup = null;
        if (host.albumGrid != null) ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
    }

    /** Cambia el nombre del álbum solo dentro de FotosTV (no toca Google Fotos). */
    void renameAlbum(final Entry e) {
        final android.widget.EditText in = new android.widget.EditText(host);
        in.setSingleLine(true);
        in.setTextColor(Color.WHITE);
        in.setText(e.title);
        in.setSelection(in.getText().length());
        new android.app.AlertDialog.Builder(host, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Nombre del álbum")
            .setMessage("Solo cambia aquí, en FotosTV. Déjalo vacío para volver al nombre de Google.")
            .setView(in)
            .setPositiveButton("Guardar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String nm = in.getText().toString().trim();
                    List<Prefs.Album> l = Prefs.albums(host);
                    for (Prefs.Album al : l) if (al.url.equals(e.url)) al.name = nm;
                    Prefs.saveAlbums(host, l);
                    String t = nm;
                    if (t.length() == 0) {
                        AlbumFetcher.AlbumData cd = Prefs.albumCache(host, e.url);
                        t = cd != null && cd.title.length() > 0 ? cd.title : "Álbum";
                    }
                    e.title = t;
                    for (Item it : e.src) it.album = t;
                    if (host.albumGrid != null) ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Pulsación larga en un álbum virtual: pase, nombre o borrarlo (las fotos no se tocan). */
    void virtualMenu(final Entry e) {
        final List<Entry> one = new ArrayList<Entry>();
        one.add(e);
        Dialogs.iconMenu(host, e.title, new String[]{"Pase", "Cambiar nombre", "Borrar álbum virtual"},
                new int[]{R.drawable.ic_play, R.drawable.ic_tune, R.drawable.ic_delete},
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) startPase(one, false);
                        else if (w == 1) Dialogs.askName(host, "Nombre del álbum virtual", e.title, new Dialogs.NameDone() {
                            public void done(String name) { renameVirtual(e, name); }
                        });
                        else {
                            List<String[]> l = Extras.virtualAlbums(host);
                            for (int i = l.size() - 1; i >= 0; i--) if (l.get(i)[1].equals(e.query)) l.remove(i);
                            Extras.saveVirtualAlbums(host, l);
                            host.virt.remove(e);
                            if (host.current == e) host.current = null;
                            host.refreshAfterChange(0);
                            host.showToast("Álbum virtual borrado (las fotos siguen en sus álbumes).");
                        }
                    }
                });
    }

    void renameVirtual(Entry e, String name) {
        if (name.length() == 0) return;
        List<String[]> l = Extras.virtualAlbums(host);
        for (String[] x : l) if (x[1].equals(e.query)) x[0] = name;
        Extras.saveVirtualAlbums(host, l);
        e.title = name;
        if (host.albumGrid != null) ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
    }


    /** Guarda el mosaico de una búsqueda como álbum virtual (sale en la lista de álbumes y se actualiza solo). */
    void saveVirtual() {
        if (host.current == null || host.current.query == null) return;
        Dialogs.askName(host, "Guardar como álbum virtual", host.current.title, new Dialogs.NameDone() {
            public void done(String name) {
                if (name.length() == 0) return;
                List<String[]> l = Extras.virtualAlbums(host);
                for (int i = l.size() - 1; i >= 0; i--) if (l.get(i)[1].equals(host.current.query)) l.remove(i);
                l.add(new String[]{name, host.current.query});
                Extras.saveVirtualAlbums(host, l);
                Extras.sp(host).edit().putBoolean(Extras.VIRTUAL, true).commit();   // al guardar la primera, la función se activa
                host.showToast("Guardado: «" + name + "» sale en la lista de álbumes.");
            }
        });
    }

    /** Busca fotos casi iguales hechas seguidas (a menos de 4 s) y las deja marcadas en el mosaico para ocultarlas. */
    void findDupes(final Entry e) {
        if (host.dupBusy) { host.showToast("Ya estoy buscando repetidas…"); return; }
        host.dupBusy = true;
        host.busyStart("Buscando repetidas…");
        final List<Item> base = new ArrayList<Item>(e.items);
        host.act.submit(new Runnable() {
            public void run() {
                final DupeScan.Result extra = DupeScan.scan(host, base, new DupeScan.Cancel() {
                    public boolean cancelled() { return host.destroyed; }
                });
                host.h.post(new Runnable() {
                    public void run() {
                        host.dupBusy = false;
                        host.busyEnd("Buscando repetidas…");
                        if (host.destroyed) return;
                        if (extra.dups.isEmpty()) { host.showToastLong("No he encontrado fotos repetidas en «" + e.title + "»."); return; }
                        int pos = host.entries.indexOf(e);
                        if (pos < 0) return;
                        if (host.level != 0 || host.selMode || host.moveEntry != null) {   // mientras, has abierto otra cosa: no se cambia de pantalla
                            host.pendingDupes = e;
                            host.pendingDupeKeys = extra;
                            host.showToastLong(extra.dups.size() + (extra.dups.size() == 1 ? " foto repetida" : " fotos repetidas") + " en «" + e.title + "»: ábrelo para verlas marcadas.");
                            return;
                        }
                        host.openEntry(pos);
                        markDupes(extra);
                        host.showToastLong(extra.dups.size() + (extra.dups.size() == 1 ? " foto repetida marcada" : " fotos repetidas marcadas")
                                + " (se queda la primera de cada grupo, que sale sin marcar justo antes de las suyas). Pulsa «Ocultar» para quitarlas o desmarca las que quieras.");
                    }
                });
            }
        });
    }

    /** Deja marcadas las repetidas en el mosaico abierto (modo de selección, listas para «Ocultar»). */
    void markDupes(DupeScan.Result r) {
        host.pSel = true;
        host.pKeys.clear();
        host.pKeys.addAll(r.dups);
        // para comparar: cada foto que se queda, con sus repetidas al lado, al principio; el resto sigue en su orden
        if (host.current != null) {
            host.current.dupFront = null;
            List<Item> base = host.current.base();
            java.util.HashSet<String> there = new java.util.HashSet<String>();
            for (Item it : base) there.add(it.key());
            java.util.HashSet<String> used = new java.util.HashSet<String>();
            List<Item> front = new ArrayList<Item>(base.size());
            for (List<Item> g : r.groups) {
                for (Item it : g) if (there.contains(it.key()) && used.add(it.key())) front.add(it);
            }
            for (Item it : base) if (!used.contains(it.key())) front.add(it);
            host.current.dupFront = front;
        }
        host.showPhotos(0);
    }

    /** Vuelve al orden de siempre tras comparar repetidas. */
    void endDupeView() {
        if (host.current != null) host.current.dupFront = null;
    }

    boolean albumMenu(int pos) {
        if (pos < 0 || pos >= host.entries.size()) return false;
        final Entry e = host.entries.get(pos);
        if (host.selMode) { toggleSel(e); return true; }
        if (e.kind == 0) {
            final boolean off = host.offUrls.contains(e.url);
            final List<Entry> one = new ArrayList<Entry>();
            one.add(e);
            final boolean pinned = e.coverPinned;
            final List<String> lb = new ArrayList<String>(java.util.Arrays.asList("Seleccionar", "Pase", "Pase con música", "Recargar álbum",
                    off ? "Mostrar álbum" : "Ocultar álbum", "Revisar ubicaciones"));
            final List<Integer> ic = new ArrayList<Integer>(java.util.Arrays.asList(R.drawable.ic_check, R.drawable.ic_play, R.drawable.ic_tune,
                    R.drawable.ic_refresh, off ? R.drawable.ic_visibility : R.drawable.ic_visibility_off, R.drawable.ic_place));
            if (Extras.on(host, Extras.ALBUM_MUSIC)) { lb.add("Música del álbum"); ic.add(R.drawable.ic_music); }
            if (Extras.on(host, Extras.DUPES)) { lb.add("Buscar repetidas"); ic.add(R.drawable.ic_copy); }
            if (pinned) { lb.add(e.coverKeys.size() > 1 ? "Quitar portadas" : "Quitar portada"); ic.add(R.drawable.ic_photo); }
            lb.add("Reordenar álbum"); ic.add(R.drawable.ic_move);
            lb.add("Cambiar nombre"); ic.add(R.drawable.ic_tune);
            lb.add("Borrar álbum"); ic.add(R.drawable.ic_delete);
            int[] ia = new int[ic.size()];
            for (int i = 0; i < ia.length; i++) ia[i] = ic.get(i);
            Dialogs.iconMenu(host, e.title, lb.toArray(new String[0]), ia,
                new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        String a = lb.get(w);
                        if (a.equals("Seleccionar")) { host.selMode = true; host.sel.clear(); host.sel.add(e); host.albumPos = Math.max(0, host.entries.indexOf(e)); host.showAlbums(); }
                        else if (a.equals("Pase")) startPase(one, false);
                        else if (a.equals("Pase con música")) startPase(one, true);
                        else if (a.equals("Recargar álbum")) reloadAlbum(e);
                        else if (a.equals("Revisar ubicaciones")) recheckPlaces(one);
                        else if (a.equals("Música del álbum")) Dialogs.albumMusic(host, e);
                        else if (a.equals("Buscar repetidas")) findDupes(e);
                        else if (a.equals("Quitar portada") || a.equals("Quitar portadas")) {
                            Extras.setCovers(host, e.url, null);   // vuelve a ser al azar (como siempre)
                            e.coverPinned = false;
                            e.coverKeys = new ArrayList<String>();
                            EntryOps.pickCover(host, e);
                            if (host.albumGrid != null) ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
                            host.showToast("Portada quitada: vuelve a ser aleatoria.");
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
        new android.app.AlertDialog.Builder(host, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(e.title)
            .setMessage("Se borrará ahora mismo del televisor, con todas sus miniaturas y vídeos descargados.")
            .setPositiveButton("Quitar ahora", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Guest.remove(host, e.gid);
                    if (host.current == e) host.current = null;
                    host.refreshAfterChange(0);
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
        return true;
    }

    void toggleSel(Entry e) {
        if (e.kind != 0) return;
        if (!host.sel.remove(e)) host.sel.add(e);
        if (host.albumGrid != null) {
            int pos = host.entries.indexOf(e);
            ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
            if (pos >= 0) host.albumGrid.setSelection(pos);
        }
        if (host.selTitle != null) host.selTitle.setText(host.sel.size() + (host.sel.size() == 1 ? " álbum seleccionado" : " álbumes seleccionados"));
    }

    /** Abre un pase con las fotos de los álbumes dados (mezclados), con música o según los ajustes. */
    void startPase(List<Entry> es, boolean music) {
        List<Item> all = new ArrayList<Item>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (Entry x : es) for (Item it : x.items) if (seen.add(it.key())) all.add(it);
        if (all.isEmpty()) { host.showToast("No hay nada que mostrar en esos álbumes."); return; }
        if (music && Prefs.musicRadio(host).length() == 0 && Prefs.musicFolder(host).length() == 0) {
            host.showToast("Elige antes una emisora o una carpeta de música.");
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
        Session.musicUrl = es.size() == 1 && es.get(0).kind == 0 ? BrowseUtil.nullIfEmpty(Extras.albumMusic(host, es.get(0).url)) : null;
        host.selMode = false;
        host.sel.clear();
        host.startActivity(new Intent(host, PlayerActivity.class));
    }

    /** Oculta (o vuelve a mostrar) los álbumes en el salvapantallas. */
    void hideEntries(List<Entry> es, boolean show) {
        List<Prefs.Album> l = Prefs.albums(host);
        for (Prefs.Album al : l) for (Entry x : es) if (al.url.equals(x.url)) al.show = show;
        Prefs.saveAlbums(host, l);
        if (!show && "all".equals(Prefs.albumMode(host))) Prefs.sp(host).edit().putString("albumMode", "selected").apply();
        host.refreshOff();
        host.showToast(show ? "Se mostrará en el salvapantallas." : "Oculto del salvapantallas (sigue en esta lista).");
        exitSel();
        if (host.level == 0 && host.albumGrid != null) ((BaseAdapter) host.albumGrid.getAdapter()).notifyDataSetChanged();
    }

    void confirmDelete(final List<Entry> es) {
        String m = es.size() == 1 ? "Se quitará «" + es.get(0).title + "» de la lista."
                : "Se quitarán " + es.size() + " álbumes de la lista.";
        new android.app.AlertDialog.Builder(host, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Borrar álbum")
            .setMessage(m + "\n\nLas fotos NO se borran de Google Fotos; si lo quieres de vuelta tendrás que añadir el enlace otra vez.")
            .setPositiveButton("Borrar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { deleteEntries(es); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    void deleteEntries(List<Entry> es) {
        java.util.HashSet<String> urls = new java.util.HashSet<String>();
        for (Entry x : es) urls.add(x.url);
        List<Prefs.Album> keep = new ArrayList<Prefs.Album>();
        for (Prefs.Album al : Prefs.albums(host)) {
            if (urls.contains(al.url)) Prefs.deleteAlbumCache(host, al.url); else keep.add(al);
        }
        Prefs.saveAlbums(host, keep);
        List<Entry> nr = new ArrayList<Entry>();
        for (Entry x : host.real) if (!urls.contains(x.url)) nr.add(x);
        host.real = nr;
        host.selMode = false;
        host.sel.clear();
        host.refreshOff();
        host.compose();
        if (host.entries.isEmpty()) host.showMessage("No hay álbumes.\nAñádelos desde la web de ajustes de FotosTV.");
        else host.showAlbums();
    }

    /** Vuelve a leer un solo álbum: fotos nuevas, título y ubicaciones que faltaban. */
    void reloadAlbum(final Entry e) {
        if (e == null || e.kind != 0 || host.reloading) return;
        host.reloading = true;
        host.busyStart("Recargando álbum…");
        final List<Item> old = new ArrayList<Item>(e.src);
        host.act.submit(new Runnable() {
            public void run() {
                String msg;
                AlbumFetcher.AlbumData d = null;
                int regps = 0;
                try {
                    d = AlbumFetcher.fetchAlbum(e.url);
                    if (d.partial) { d = null; msg = "No se ha podido leer el álbum completo. Inténtalo de nuevo."; }
                    else {
                        Prefs.saveAlbumCache(host, e.url, d);
                        regps = MetaFetcher.forgetMissingGps(host, d.items);
                        msg = "\u2714 «" + e.title + "» recargado: " + d.items.size() + " elementos"
                                + (regps > 0 ? ". Se vuelve a buscar la ubicación de " + regps + "." : ".");
                    }
                } catch (Throwable ex) {
                    Diag.e("recargar álbum", ex);
                    msg = "No se ha podido recargar (¿hay conexión?).";
                }
                final AlbumFetcher.AlbumData fd = d;
                final String fm = msg;
                host.h.post(new Runnable() {
                    public void run() {
                        host.reloading = false;
                        host.busyEnd("Recargando álbum…");
                        if (host.destroyed) return;
                        if (fd != null) {
                            for (Item it : fd.items) {
                                it.album = e.title;
                                if (it.akey.length() == 0) it.akey = fd.key;
                            }
                            e.src = fd.items;
                            host.refreshAfterChange(host.photoGrid != null ? host.photoGrid.getFirstVisiblePosition() : 0);
                        }
                        host.showToast(fm);
                    }
                });
            }
        });
    }

    void itemMenu(final int pos) {
        if (host.current == null || pos < 0 || pos >= host.current.shown().size()) return;
        final Item it = host.current.shown().get(pos);
        if (host.current.kind == 4) { showInfo(it); return; }       // los temporales no se ocultan ni se marcan
        final boolean isHid = Prefs.hidden(host).contains(it.key());
        final boolean isFav = Prefs.favs(host).contains(it.key());
        final boolean canCover = host.current.kind == 0;
        final boolean multi = Extras.on(host, Extras.MULTI_COVER);
        final boolean isCover = host.current.coverKeys.contains(it.key());
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
        if (Extras.on(host, Extras.CAPTIONS)) { lb.add("Pie de foto"); ic.add(R.drawable.ic_notes); }
        final String[] labels = lb.toArray(new String[0]);
        final int[] icons = new int[ic.size()];
        for (int i = 0; i < icons.length; i++) icons[i] = ic.get(i);
        android.widget.ListAdapter ad = new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int i, View cv, ViewGroup parent) {
                TextView tv = (TextView) super.getView(i, cv, parent);
                tv.setTypeface(Ui.REG);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);     // menús más compactos: caben sin desplazar
                tv.setMinHeight(Ui.dp(host, 36));
                tv.setMinimumHeight(Ui.dp(host, 36));
                tv.setPadding(tv.getPaddingLeft(), Ui.dp(host, 6), tv.getPaddingRight(), Ui.dp(host, 6));
                android.graphics.drawable.Drawable d = host.getResources().getDrawable(icons[i]).mutate();
                int sz = Ui.dp(host, 18);
                d.setBounds(0, 0, sz, sz);
                tv.setCompoundDrawables(d, null, null, null);
                tv.setCompoundDrawablePadding(Ui.dp(host, 14));
                tv.setGravity(Gravity.CENTER_VERTICAL);
                return tv;
            }
        };
        new android.app.AlertDialog.Builder(host, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setAdapter(ad, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int which) {
                    String a = labels[which];
                    if (a.equals("Seleccionar")) {
                        host.pSel = true;
                        host.pKeys.clear();
                        host.pKeys.add(it.key());
                        host.showPhotos(pos);
                    } else if (a.equals("Mostrar") || a.equals("Ocultar")) {
                        if (isHid) Prefs.unhide(host, it.key()); else Prefs.hide(host, it.key());
                        host.refreshAfterChange(pos);
                    } else if (a.equals("Ver información")) {
                        showInfo(it);
                    } else if (a.equals("Revisar ubicación")) {
                        List<Item> l = new ArrayList<Item>();
                        l.add(it);
                        recheckItems(l);
                    } else if (a.equals("Fijar como portada") || a.equals("Añadir a portadas") || a.equals("Quitar de portadas")) {
                        // sin «Varias portadas» solo se cambia la primera: las demás se guardan por si se vuelve a activar
                        List<String> keys = Extras.allCovers(host, host.current.url);
                        if (a.equals("Quitar de portadas")) keys.remove(it.key());
                        else if (multi) { if (!keys.contains(it.key())) keys.add(it.key()); }
                        else if (keys.isEmpty()) keys.add(it.key());
                        else { keys.remove(it.key()); if (keys.isEmpty()) keys.add(it.key()); else keys.set(0, it.key()); }
                        Extras.setCovers(host, host.current.url, keys);
                        host.current.coverKeys = Extras.covers(host, host.current.url);
                        host.current.coverPinned = !host.current.coverKeys.isEmpty();
                        if (a.equals("Quitar de portadas")) { if (host.current.cover == it) host.current.cover = null; }
                        else host.current.cover = it;
                        if (host.current.cover == null || !host.current.coverPinned) EntryOps.applyCover(host, host.current, Prefs.hidden(host));
                        if (host.photoGrid != null) ((BaseAdapter) host.photoGrid.getAdapter()).notifyDataSetChanged();
                        host.showToast(a.equals("Quitar de portadas") ? "Quitada de las portadas del álbum."
                                : multi ? "Añadida a las portadas del álbum (" + host.current.coverKeys.size() + ")." : "Portada del álbum fijada.");
                    } else if (a.equals("Pie de foto")) {
                        Dialogs.askName(host, "Pie de foto (vacío para quitarlo)", Extras.rawCaption(host, it.key()), new Dialogs.NameDone() {
                            public void done(String text) {
                                Extras.setCaption(host, it.key(), text);
                                host.showToast(text.length() == 0 ? "Pie de foto quitado." : "Pie de foto guardado: sale en el pase y en la ficha.");
                            }
                        });
                    } else {
                        Prefs.toggleFav(host, it.key());
                        host.refreshAfterChange(pos);
                    }
                }
            }).show();
    }

    /** «Fecha» (función extra): lista de meses del álbum; al elegir uno, el mosaico va a su primera foto. */
    void dateJump() {
        if (host.current == null) return;
        final Entry e = host.current;
        final List<Item> l = e.base();
        final java.util.TreeMap<Integer, Integer> days = new java.util.TreeMap<Integer, Integer>();
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        for (Item it : l) {
            int k = FxLogic.dayKey(it.ts, tz);
            if (k < 0) continue;
            Integer n = days.get(k);
            days.put(k, n == null ? 1 : n + 1);
        }
        if (days.isEmpty()) { host.showToast("Este álbum no tiene fotos con fecha."); return; }
        int start = e.dayLo;
        if (start < 0 && host.photoGrid != null) {
            int p = host.photoGrid.getSelectedItemPosition();
            List<Item> sh = e.shown();
            if (p >= 0 && p < sh.size()) start = FxLogic.dayKey(sh.get(p).ts, tz);
        }
        new CalendarDialog(host, days, start, e.dayLo >= 0, new CalendarDialog.Listener() {
            public void onRange(int lo, int hi) { e.dayLo = lo; e.dayHi = hi; host.showPhotos(0); }
            public void onAll() { e.dayLo = -1; e.dayHi = -1; host.showPhotos(0); }
        }).show();
    }

    void togglePhoto(int pos) {
        if (host.current == null || pos < 0 || pos >= host.current.shown().size()) return;
        String k = host.current.shown().get(pos).key();
        if (!host.pKeys.remove(k)) host.pKeys.add(k);
        if (host.photoGrid != null) {
            ((BaseAdapter) host.photoGrid.getAdapter()).notifyDataSetChanged();
            host.photoGrid.setSelection(pos);
        }
        host.updateCount();
    }

    void refreshPhotoSel() {
        if (host.photoGrid != null) {
            int p = host.photoGrid.getSelectedItemPosition();
            ((BaseAdapter) host.photoGrid.getAdapter()).notifyDataSetChanged();
            if (p >= 0) host.photoGrid.setSelection(p);
        }
        host.updateCount();
    }

    void exitPSel() {
        int fp = host.current != null && host.current.dupFront != null ? 0 : (host.photoGrid != null ? host.photoGrid.getFirstVisiblePosition() : 0);
        host.pSel = false;
        host.pKeys.clear();
        endDupeView();
        host.showPhotos(fp);
    }

    /** Vuelve a leer la ubicación y el nombre del lugar de estas fotos (en segundo plano). */
    void recheckItems(final List<Item> l) {
        final Set<String> ks = new HashSet<String>();
        for (Item it : l) ks.add(it.key());
        new Thread(new Runnable() {
            public void run() { try { GeoIndex.recheck(host, l); } catch (Throwable ignored) { } }
        }).start();
        host.watchGeo(ks);
    }

    void recheckPlaces(List<Entry> es) {
        List<Item> l = new ArrayList<Item>();
        for (Entry e : es) l.addAll(e.src);
        recheckItems(l);
    }

    /** 0 = ocultar o mostrar, 1 = añadir a favoritas, 2 = quitar de favoritas, para todas las seleccionadas. */
    void applyPhotoSel(int what) {
        if (host.pKeys.isEmpty()) { host.showToast("Marca primero alguna foto o vídeo."); return; }
        List<String> ks = new ArrayList<String>(host.pKeys);
        int fp = host.current != null && host.current.dupFront != null ? 0 : (host.photoGrid != null ? host.photoGrid.getFirstVisiblePosition() : 0);
        if (what == 0) Prefs.hideAll(host, ks, host.current.kind != 3);
        else Prefs.favAll(host, ks, what == 1);
        host.showToast(ks.size() + (ks.size() == 1 ? " elemento cambiado." : " elementos cambiados."));
        host.pSel = false;
        host.pKeys.clear();
        endDupeView();
        host.refreshAfterChange(fp);
        if (host.level == 1 && host.current != null && host.entries.contains(host.current)) host.showPhotos(fp);
    }

    void showInfo(Item it) {
        if (host.infoPanel != null) return;
        host.infoPanel = new InfoPanel(host, it, it.album);
        host.root.addView(host.infoPanel, new FrameLayout.LayoutParams(-1, -1));
        host.infoPanel.bringToFront();
    }}
