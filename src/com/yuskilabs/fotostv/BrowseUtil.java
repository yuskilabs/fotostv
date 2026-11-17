package com.yuskilabs.fotostv;

import java.util.ArrayList;
import java.util.List;

/** Funciones sueltas del explorador (orden, ajuste de pantalla, fotos visibles). */
final class BrowseUtil {
    private BrowseUtil() {}

    /** Fecha de captura de la foto más reciente del álbum (0 si no se conoce). */
    static long lastTs(Entry e) {
        long m = 0;
        for (Item it : e.items) if (it.ts > m) m = it.ts;
        return m;
    }
    static List<Item> visible(List<Item> src, java.util.Set<String> hid) {
        List<Item> out = new ArrayList<Item>(src.size());
        for (Item it : src) if (!hid.contains(it.key())) out.add(it);
        return out;
    }
    static String shapeLabel(String sh) {
        return "wide".equals(sh) ? "Miniaturas: apaisadas" : ("full".equals(sh) ? "Miniaturas: completas" : "Miniaturas: cuadradas");
    }
    /** Aleatorio → Antiguas primero → Recientes primero → Aleatorio… (el orden de partida equivale a «Recientes»). */
    static String nextOrder(String o) {
        if ("random".equals(o)) return "asc";
        if ("asc".equals(o)) return "desc";
        return "random";
    }
    static String nextFit(String f) {
        return "auto".equals(f) ? "crop" : ("crop".equals(f) ? "fit" : "auto");
    }
    static String fitLabel(String f) {
        return "crop".equals(f) ? "Pantalla: rellenar" : ("fit".equals(f) ? "Pantalla: original" : "Pantalla: automática");
    }
    static String nullIfEmpty(String s) { return s == null || s.length() == 0 ? null : s; }
}
