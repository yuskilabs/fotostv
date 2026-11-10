package com.yuskilabs.fotostv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Saltos desde la ficha de una foto en el pase: ir a su álbum, pase solo de su álbum o de su lugar. */
class Nav {
    static final int ALBUM = 1, ALBUM_PASE = 2, PLACE_TOWN = 3, PLACE_REGION = 4, DAY = 5, ZONE = 6;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    /** Parte «provincia, isla o país» de un nombre de lugar («Adeje, Tenerife» → «Tenerife»); null si no la hay. */
    static String regionOf(String place) {
        if (place == null) return null;
        int i = place.lastIndexOf(", ");
        return i > 0 ? place.substring(i + 2) : null;
    }

    static String townOf(String place) {
        if (place == null) return null;
        int i = place.lastIndexOf(", ");
        return i > 0 ? place.substring(0, i) : place;
    }

    private static Prefs.Album albumOf(Context c, Item it, AlbumFetcher.AlbumData[] out) {
        for (Prefs.Album al : Prefs.albums(c)) {
            AlbumFetcher.AlbumData d = Prefs.albumCache(c, al.url);
            if (d == null) continue;
            boolean hit = it.akey.length() > 0 && it.akey.equals(d.key);
            if (!hit) for (Item x : d.items) if (x.key().equals(it.key())) { hit = true; break; }
            if (hit) { out[0] = d; return al; }
        }
        return null;
    }

    private static List<Item> placeItems(Context c, String place, boolean region) {
        String want = region ? regionOf(place) : place;
        if (want == null) return new ArrayList<Item>();
        return GeoIndex.items(c, want, region);
    }

    /** Ejecuta la acción en segundo plano y luego abre lo que toque. «place» es el nombre de lugar de la foto. */
    static void run(final Context ctx, final int action, final Item it, final String place) {
        new Thread(new Runnable() {
            public void run() {
                final AlbumFetcher.AlbumData[] dd = new AlbumFetcher.AlbumData[1];
                final Prefs.Album al = action == PLACE_TOWN || action == PLACE_REGION ? null : albumOf(ctx, it, dd);
                final List<Item> list;
                if (action == ALBUM_PASE && dd[0] != null) {
                    String title = al.name.length() > 0 ? al.name : dd[0].title;
                    for (Item x : dd[0].items) { x.album = title; if (x.akey.length() == 0) x.akey = dd[0].key; }
                    list = dd[0].items;
                } else if (action == PLACE_TOWN || action == PLACE_REGION) {
                    list = placeItems(ctx, place, action == PLACE_REGION);
                } else list = null;
                ui.post(new Runnable() {
                    public void run() { go(ctx, action, it, al, list); }
                });
            }
        }).start();
    }

    /** Pase con las fotos hechas el mismo día que «it» (de todos los álbumes). */
    static void runDay(final Context ctx, final Item it) {
        new Thread(new Runnable() {
            public void run() {
                final List<Item> list = new ArrayList<Item>();
                if (it.ts > 0) {
                    java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
                    a.setTimeInMillis(it.ts);
                    HashSet<String> keys = new HashSet<String>();
                    for (Item x : GeoIndex.allItems(ctx, null).values()) {
                        if (x.ts <= 0) continue;
                        b.setTimeInMillis(x.ts);
                        if (a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)) keys.add(x.key());
                    }
                    list.addAll(GeoIndex.itemsOf(ctx, keys));
                }
                ui.post(new Runnable() { public void run() { go(ctx, DAY, it, null, list); } });
            }
        }).start();
    }

    /** Pase con las fotos a menos de «km» kilómetros de un punto. */
    static void runZone(final Context ctx, final Item it, final double lat, final double lon, final double km) {
        new Thread(new Runnable() {
            public void run() {
                double dLat = km / 111.0;
                double dLon = km / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(lat))));
                final List<Item> list = GeoIndex.itemsOf(ctx, GeoIndex.get(ctx).keysIn(lat - dLat, lat + dLat, lon - dLon, lon + dLon));
                ui.post(new Runnable() { public void run() { go(ctx, ZONE, it, null, list); } });
            }
        }).start();
    }

    /** Abre un pase con las fotos de un lugar (o de una región entera). */
    static void openPlace(final Context ctx, final String want, final boolean region) {
        new Thread(new Runnable() {
            public void run() {
                final List<Item> list = GeoIndex.items(ctx, want, region);
                ui.post(new Runnable() { public void run() { go(ctx, PLACE_TOWN, null, null, list); } });
            }
        }).start();
    }

    /** Abre un pase con las fotos de un rectángulo del mapa. */
    static void openBox(final Context ctx, final double minLat, final double maxLat, final double minLon, final double maxLon) {
        new Thread(new Runnable() {
            public void run() {
                final List<Item> list = GeoIndex.itemsOf(ctx, GeoIndex.get(ctx).keysIn(minLat, maxLat, minLon, maxLon));
                ui.post(new Runnable() { public void run() { go(ctx, PLACE_TOWN, null, null, list); } });
            }
        }).start();
    }

    /** Abre el mosaico (como un álbum) con las fotos de un lugar, o de un rectángulo del mapa si box != null. */
    static void openMosaic(Context ctx, String title, String want, boolean region, double[] box) {
        openMosaic(ctx, title, want, region, box, null);
    }

    /** Con «scope», solo las fotos de ese conjunto de claves (el «Mapa» de un álbum). */
    static void openMosaic(final Context ctx, final String title, final String want, final boolean region, final double[] box,
                           final java.util.Set<String> scope) {
        new Thread(new Runnable() {
            public void run() {
                java.util.HashSet<String> ks = box != null
                        ? GeoIndex.get(ctx).keysIn(box[0], box[1], box[2], box[3])
                        : GeoIndex.get(ctx).keysAt(want, region);
                if (scope != null) ks.retainAll(scope);
                final List<Item> list = GeoIndex.itemsOf(ctx, ks);
                ui.post(new Runnable() {
                    public void run() {
                        if (list == null || list.isEmpty()) {
                            Toast.makeText(ctx, "Aún no conozco fotos de este sitio (la app las va revisando en segundo plano).", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Session.placeItems = list;
                        Session.placeTitle = title;
                        ctx.startActivity(new Intent(ctx, BrowserActivity.class));
                    }
                });
            }
        }).start();
    }

    private static void go(Context ctx, int action, Item it, Prefs.Album al, List<Item> list) {
        if (action == ALBUM) {
            if (al == null) { Toast.makeText(ctx, "No encuentro el álbum de esta foto.", Toast.LENGTH_LONG).show(); return; }
            Session.gotoUrl = al.url;
            Session.gotoKey = it.key();
            if (!(ctx instanceof PlayerActivity)) {
                Intent i = new Intent(ctx, BrowserActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ctx.startActivity(i);
            }
            finish(ctx);
            return;
        }
        if (list == null || list.isEmpty()) {
            Toast.makeText(ctx, action == ALBUM_PASE ? "No encuentro el álbum de esta foto."
                    : action == DAY ? "No encuentro más fotos de este día."
                    : "Aún no conozco más fotos de este sitio (la app las va revisando en segundo plano; mira el contador en Ajustes).",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Session.items = list;
        Session.order = "random";
        Session.manual = false;
        Session.guest = false;
        Session.guestId = null;
        Session.startKey = null;
        Session.lastKey = null;
        Session.music = 0;
        String am = action == ALBUM_PASE && al != null ? Extras.albumMusic(ctx, al.url) : "";
        Session.musicUrl = am.length() > 0 ? am : null;      // «Pase de este álbum» con su música propia
        Intent i = new Intent(ctx, PlayerActivity.class);
        if (!(ctx instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        finish(ctx);
    }

    private static void finish(Context ctx) {
        if (ctx instanceof Activity) ((Activity) ctx).finish();
        else if (ctx instanceof android.service.dreams.DreamService) ((android.service.dreams.DreamService) ctx).finish();
    }
}
