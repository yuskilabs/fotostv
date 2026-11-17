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

    /** Claves de las fotos repetidas (se queda la primera de cada grupo). Pesado: no llamar desde el hilo principal. */
    static Set<String> scan(Context c, List<Item> base, Cancel stop) {
        Set<String> extra = new HashSet<String>();
        try {
            for (List<Item> burst : FxLogic.bursts(base, 4000)) {
                if (stop.cancelled()) return extra;
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
                for (int i : FxLogic.similarToEarlier(hs, 10)) extra.add(photos.get(i).key());
            }
        } catch (Throwable ignored) { }
        return extra;
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
