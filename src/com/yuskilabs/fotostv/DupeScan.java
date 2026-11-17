package com.yuskilabs.fotostv;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Busca fotos casi iguales hechas seguidas (a menos de 4 s) comparando una huella de cada miniatura. */
final class DupeScan {
    private DupeScan() {}

    interface Cancel { boolean cancelled(); }

    /** Lo encontrado: las repetidas y, por cada foto que se queda, las que se le parecen (la que se queda va la primera). */
    static final class Result {
        final Set<String> dups = new HashSet<String>();
        final List<List<Item>> groups = new ArrayList<List<Item>>();
    }

    /** Pesado: no llamar desde el hilo principal. De cada grupo de parecidas se queda la primera. */
    static Result scan(Context c, List<Item> base, Cancel stop) {
        Result res = new Result();
        try {
            for (List<Item> burst : FxLogic.bursts(base, 4000)) {
                if (stop.cancelled()) return res;
                List<Item> photos = new ArrayList<Item>();
                for (Item it : burst) {
                    Boolean v = MetaFetcher.cachedVideo(c, it);
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
                List<Integer> kept = new ArrayList<Integer>();
                java.util.HashMap<Integer, List<Item>> grp = new java.util.HashMap<Integer, List<Item>>();
                for (int i = 0; i < hs.length; i++) {
                    int leader = -1;
                    for (int k : kept) if (FxLogic.hamming(hs[i], hs[k]) <= 10) { leader = k; break; }
                    if (leader < 0) { kept.add(i); continue; }
                    List<Item> g = grp.get(leader);
                    if (g == null) { g = new ArrayList<Item>(); g.add(photos.get(leader)); grp.put(leader, g); }
                    g.add(photos.get(i));
                    res.dups.add(photos.get(i).key());
                }
                for (int k : kept) if (grp.containsKey(k)) res.groups.add(grp.get(k));
            }
        } catch (Throwable t) { Diag.e("repetidas", t); }
        return res;
    }

    /** Huella dHash de la miniatura (9×8 en grises); null si no se pudo bajar. */
    static Long dhashOf(Item it) {
        try {
            byte[] d = AlbumFetcher.get(it.url + "=w96-h96");
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(d, 0, d.length);
            if (b == null) return null;
            android.graphics.Bitmap s9 = android.graphics.Bitmap.createScaledBitmap(b, 9, 8, true);
            int[] g = new int[72];
            for (int y = 0; y < 8; y++) for (int x = 0; x < 9; x++) {
                int c = s9.getPixel(x, y);
                g[y * 9 + x] = (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000;
            }
            if (s9 != b) s9.recycle();
            b.recycle();
            return FxLogic.dhash(g);
        } catch (Throwable t) {
            return null;
        }
    }
}
