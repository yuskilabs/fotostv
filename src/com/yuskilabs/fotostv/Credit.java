package com.yuskilabs.fotostv;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Marca de agua «© Nombre»: quién hizo cada foto, según las reglas del usuario (web de la tele) y los datos de la foto. */
final class Credit {
    private Credit() { }

    private static String rulesNow;
    private static int rulesHash;

    /**
     * Reglas en uso. Si han cambiado desde la última vez, se olvida lo ya calculado para que se recalcule con las nuevas.
     */
    private static synchronized String rulesFor(Context ctx, android.content.SharedPreferences m) {
        String r = Extras.str(ctx, Extras.CREDIT_RULES, "");
        int hash = r.hashCode();
        if (rulesNow == null || hash != rulesHash) {
            if (rulesNow != null || m.getInt("creditRulesHash", 0) != hash) {
                android.content.SharedPreferences.Editor ed = m.edit();
                for (String k : new ArrayList<String>(m.getAll().keySet())) if (k.startsWith("cr2_")) ed.remove(k);
                ed.putInt("creditRulesHash", hash).apply();
            }
            rulesNow = r;
            rulesHash = hash;
        }
        return rulesNow;
    }

    /**
     * Quién hizo esa foto: "" si nadie encaja; null si todavía no se sabe (y no se puede mirar ahora: net = false).
     * Se recuerda solo cuando la respuesta es segura.
     */
    static String of(Context ctx, Item it, boolean net) {
        try {
            android.content.SharedPreferences m = Prefs.meta(ctx);
            String rules = rulesFor(ctx, m);
            String k = "cr2_" + it.key();
            String c = m.getString(k, null);
            if (c != null) return c;
            Boolean v = MetaFetcher.cachedVideo(ctx, it);
            if (v != null && v) return "";
            String cam = MetaFetcher.cachedCam(ctx, it);
            if (cam == null || cam.length() == 0) {
                try { cam = GeoIndex.get(ctx).camOf(it.key()); } catch (Throwable ignored) { cam = null; }
            }
            boolean camKnown = cam != null && cam.length() > 0;
            String name = camKnown ? FxLogic.credit(cam, null, rules) : "";
            if (name.length() > 0) { m.edit().putString(k, name).apply(); return name; }
            if (!net) return null;
            if (!camKnown) {
                String got = MetaFetcher.fetchCam(it);       // null = no se pudo leer
                if (got != null && got.length() > 0) {
                    camKnown = true;
                    try { GeoIndex.get(ctx).putCam(it.key(), got); } catch (Throwable ignored) { }
                    name = FxLogic.credit(got, null, rules);
                    if (name.length() > 0) { m.edit().putString(k, name).apply(); return name; }
                }
            }
            boolean fileOk = false;
            if (it.id.length() > 0 && it.akey.length() > 0) {
                try {
                    String fn = AlbumFetcher.fileName(it.id, it.akey);
                    if (fn.length() > 0) { fileOk = true; name = FxLogic.credit(null, fn, rules); }
                } catch (Throwable ignored) { }
            }
            if (name.length() > 0 || camKnown || fileOk) m.edit().putString(k, name).apply();
            return name;
        } catch (Throwable t) { Diag.e("autor", t); return ""; }
    }

    /** Autores de las fotos de esta diapositiva (si es un mosaico, los distintos). Con net = false solo usa lo ya guardado. */
    static void fill(Context ctx, Slideshow.Slide p, boolean net) {
        if (!Extras.on(ctx, Extras.CREDIT) || p == null || p.item == null) return;
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
        List<Item> all = new ArrayList<Item>();
        all.add(p.item);
        if (p.item2 != null) all.add(p.item2);
        if (p.others != null) all.addAll(p.others);
        boolean pending = false;
        for (Item o : all) {
            String c = of(ctx, o, net);
            if (c == null) pending = true; else if (c.length() > 0) names.add(c);
        }
        StringBuilder sb = new StringBuilder();
        for (String n : names) { if (sb.length() > 0) sb.append(" \u00B7 "); sb.append(n); }
        p.credit = sb.toString();
        p.creditPending = pending;
    }
}
