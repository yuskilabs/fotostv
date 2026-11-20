package com.yuskilabs.fotostv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mosaicos del pase (funciones extra): fotos hechas casi a la vez que salen juntas en una sola imagen, sin bordes.
 * Dos verticales, tres verticales o cuatro horizontales; nunca con vídeos y siempre del mismo álbum.
 */
final class Mosaic {
    /** Lo que el mosaico necesita del pase. */
    interface Host {
        List<Item> items();
        int width();
        int height();
        boolean manual();
        byte[] photoBytes(Item it) throws Exception;
    }

    private final Context ctx;
    private final Host host;

    Mosaic(Context ctx, Host host) { this.ctx = ctx; this.host = host; }

    static boolean portrait(Bitmap b) { return b != null && b.getHeight() > b.getWidth() * 1.05f; }

    /**
     * Al pasar a una foto vertical, si la que va justo detrás en la cola ya está lista y también es una foto vertical,
     * salen juntas (en el hilo de pantalla y en el orden de la lista: nunca se salta ni se desordena nada).
     */
    static boolean sameAlbum(Item x, Item y) {
        if (x == null || y == null) return false;
        if (x.akey != null && y.akey != null && x.akey.length() > 0 && y.akey.length() > 0) return x.akey.equals(y.akey);
        return x.album != null && x.album.equals(y.album);
    }

    private static final long GROUP_WINDOW_MS = 15L * 60 * 1000;
    private static final int GRID_ODDS = 14;                // tres/cuatro juntas: una de cada 14 fotos, más o menos
    private static final int PAIR_ODDS = 6;                 // dos verticales: una de cada 6 fotos verticales, más o menos
    private final java.util.Random grpRnd = new java.util.Random();

    private String level = "off";
    private boolean mosaicsOn() { return !host.manual() && !"off".equals(level); }

    private static class G {
        Item it;
        Bitmap b;
        G(Item it, Bitmap b) { this.it = it; this.b = b; }
    }

    // para que al volver atrás con «Anterior» siga saliendo el mismo mosaico: foto principal -> las que iban con ella
    private final java.util.Map<String, List<Item>> groups = new java.util.concurrent.ConcurrentHashMap<String, List<Item>>();

    /** Fotos del mismo álbum hechas con menos de 15 minutos de diferencia, las más cercanas primero. */
    private List<Item> nearItems(final Item it) {
        List<Item> out = new ArrayList<Item>();
        if (it.ts <= 0) return out;
        List<Item> snap = host.items();
        java.util.Set<String> hid = Prefs.hiddenAll(ctx);
        try {
            for (int i = 0; i < snap.size(); i++) {
                Item q = snap.get(i);
                if (q == it || q.ts <= 0 || Math.abs(q.ts - it.ts) > GROUP_WINDOW_MS || !sameAlbum(it, q)) continue;
                if (q.key().equals(it.key()) || hid.contains(q.key())) continue;
                out.add(q);
            }
        } catch (Throwable ignored) { }
        Collections.sort(out, new java.util.Comparator<Item>() {
            public int compare(Item x, Item y) { return Long.compare(Math.abs(x.ts - it.ts), Math.abs(y.ts - it.ts)); }
        });
        // nunca vídeos en un mosaico: se comprueba uno a uno (lo ya sabido es instantáneo; si no se puede saber, se descarta)
        List<Item> ok = new ArrayList<Item>();
        for (Item q : out) {
            if (ok.size() >= 8) break;
            try { if (!MetaFetcher.isVideoStrict(ctx, q)) ok.add(q); } catch (Throwable ignored) { }
        }
        return ok;
    }

    private Bitmap lightBitmap(Item q) {
        try {
            byte[] d = host.photoBytes(q);
            return BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (Throwable t) { return null; }
    }

    /**
     * En segundo plano: si esta foto puede ir con otras hechas casi a la vez, las baja y las junta en una sola imagen, sin
     * bordes: dos verticales, tres verticales (de vez en cuando) o cuatro horizontales en cuadrícula (de vez en cuando).
     * Si no se consigue, la foto sale sola.
     */
    void groupUp(Item it, Slideshow.Slide p) {
        if (host.width() < 1 || host.height() < 1 || p.bmp == null || p.item2 != null) return;
        try { if (MetaFetcher.isVideoStrict(ctx, it)) return; } catch (Throwable t) { return; }
        level = Extras.mosaics(ctx);
        if (!mosaicsOn()) return;
        boolean port = portrait(p.bmp);
        if (!port) {
            if (grpRnd.nextInt(FxLogic.mosaicOdds(level, GRID_ODDS)) == 0) tryGroup(it, p, 3, false);
            return;
        }
        if (grpRnd.nextInt(FxLogic.mosaicOdds(level, GRID_ODDS)) == 0 && tryGroup(it, p, 2, true)) return;
        if (grpRnd.nextInt(FxLogic.mosaicOdds(level, PAIR_ODDS)) == 0) tryGroup(it, p, 1, true);
    }

    private boolean tryGroup(Item it, Slideshow.Slide p, int need, boolean wantPortrait) {
        List<G> got = new ArrayList<G>();
        got.add(new G(it, p.bmp));
        for (Item q : nearItems(it)) {
            if (got.size() > need) break;
            Bitmap b = lightBitmap(q);
            if (b == null) continue;
            if (wantPortrait ? !portrait(b) : b.getWidth() <= b.getHeight()) { b.recycle(); continue; }
            got.add(new G(q, b));
        }
        if (got.size() < need + 1) {
            for (int i = 1; i < got.size(); i++) got.get(i).b.recycle();
            return false;
        }
        if (!compose(p, got)) return false;
        List<Item> others = new ArrayList<Item>();
        for (G g : got) if (g.it != it) others.add(g.it);
        groups.put(it.key(), others);
        return true;
    }

    /** Al volver a una foto que salió en mosaico: vuelve a montarlo igual. */
    void regroup(Item it, Slideshow.Slide p) {
        List<Item> others = groups.get(it.key());
        if (others == null || p.bmp == null || p.item2 != null || host.width() < 1 || host.height() < 1) return;
        List<G> got = new ArrayList<G>();
        got.add(new G(it, p.bmp));
        for (Item q : others) {
            Bitmap b = lightBitmap(q);
            if (b == null) {
                for (int i = 1; i < got.size(); i++) got.get(i).b.recycle();
                return;
            }
            got.add(new G(q, b));
        }
        compose(p, got);
    }

    /** Junta 2, 3 o 4 fotos (por orden de hora) en una sola imagen a pantalla completa, sin bordes ni huecos. */
    private boolean compose(Slideshow.Slide p, List<G> got) {
        final int w = host.width(), hgt = host.height();
        Collections.sort(got, new java.util.Comparator<G>() {
            public int compare(G x, G y) { return Long.compare(x.it.ts, y.it.ts); }
        });
        Bitmap out;
        try {
            out = Bitmap.createBitmap(w, hgt, Bitmap.Config.ARGB_8888);
        } catch (Throwable oom) {
            Diag.e("mosaico", oom);
            for (G g : got) if (g.b != p.bmp) g.b.recycle();
            return false;
        }
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        int n = got.size();
        if (n == 4) {
            int x1 = w / 2, y1 = hgt / 2;
            drawCover(c, got.get(0).b, 0, 0, x1, y1, pt);
            drawCover(c, got.get(1).b, x1, 0, w - x1, y1, pt);
            drawCover(c, got.get(2).b, 0, y1, x1, hgt - y1, pt);
            drawCover(c, got.get(3).b, x1, y1, w - x1, hgt - y1, pt);
        } else {
            for (int i = 0; i < n; i++) {
                int x0 = w * i / n, x1 = w * (i + 1) / n;
                drawCover(c, got.get(i).b, x0, 0, x1 - x0, hgt, pt);
            }
        }
        Item anchor = p.item;
        for (G g : got) if (g.b != p.bmp) g.b.recycle();
        p.bmp = out;
        p.item2 = null;
        p.others = null;
        for (G g : got) {
            if (g.it == anchor || (anchor != null && g.it.key().equals(anchor.key()))) continue;
            if (p.item2 == null) p.item2 = g.it;
            else { if (p.others == null) p.others = new ArrayList<Item>(); p.others.add(g.it); }
        }
        if (p.date != null) p.date = p.date.replaceFirst(" - \\d{1,2}:\\d{2}", "");   // un solo pie de foto y sin hora
        return true;
    }

    /** La foto llena el hueco entero; lo que sobra se recorta por el centro. */
    private void drawCover(android.graphics.Canvas c, Bitmap b, int x0, int y0, int bw, int bh, android.graphics.Paint pt) {
        float sc = Math.max(bw / (float) b.getWidth(), bh / (float) b.getHeight());
        float sw = bw / sc, sh = bh / sc;
        int sx = Math.max(0, Math.round((b.getWidth() - sw) / 2f)), sy = Math.max(0, Math.round((b.getHeight() - sh) / 2f));
        c.drawBitmap(b, new android.graphics.Rect(sx, sy, Math.min(b.getWidth(), Math.round(sx + sw)), Math.min(b.getHeight(), Math.round(sy + sh))),
                new android.graphics.Rect(x0, y0, x0 + bw, y0 + bh), pt);
    }
}
