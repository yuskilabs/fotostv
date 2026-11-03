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
    static final int ALBUM = 1, ALBUM_PASE = 2, PLACE_TOWN = 3, PLACE_REGION = 4;
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
