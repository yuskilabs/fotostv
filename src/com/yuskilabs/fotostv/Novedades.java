package com.yuskilabs.fotostv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * «Novedades» de cada versión: el historial de cambios viaja dentro de la app (assets/novedades.txt, sacado de
 * CHANGELOG.md al compilar) y se enseña una sola vez al abrir la app tras actualizar.
 */
final class Novedades {
    private Novedades() { }

    private static final String SEEN = "novSeen";

    /** Secciones del historial: {versión, texto} de la más nueva a la más antigua. */
    static List<String[]> parse(String md) {
        List<String[]> out = new ArrayList<String[]>();
        String ver = null;
        StringBuilder b = new StringBuilder();
        for (String line : md.split("\n")) {
            if (line.startsWith("## ")) {
                if (ver != null) out.add(new String[]{ver, b.toString().trim()});
                ver = line.substring(3).trim();
                b.setLength(0);
            } else if (ver != null) {
                String t = line.trim();
                if (t.startsWith("- ")) b.append("• ").append(t.substring(2)).append("\n\n");
                else if (t.length() > 0) b.append(t).append("\n\n");
            }
        }
        if (ver != null) out.add(new String[]{ver, b.toString().trim()});
        return out;
    }

    /** Lo nuevo desde la versión «seen» (sin incluirla), con un máximo de secciones; sin «seen», solo la más reciente. */
    static List<String[]> since(List<String[]> all, String seen, int max) {
        List<String[]> out = new ArrayList<String[]>();
        for (String[] s : all) {
            if (s[0].equals(seen) || out.size() >= max) break;
            out.add(s);
        }
        if (out.isEmpty() && !all.isEmpty()) out.add(all.get(0));
        return out;
    }

    private static String load(Context c) {
        try {
            InputStream in = c.getAssets().open("novedades.txt");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Throwable e) { return ""; }
    }

    private static String version(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Throwable e) { return ""; }
    }

    private static CharSequence format(List<String[]> secs) {
        StringBuilder b = new StringBuilder();
        for (String[] s : secs) {
            if (b.length() > 0) b.append("\n\n");
            b.append("Versión ").append(s[0]).append("\n\n").append(s[1]);
        }
        return b.length() == 0 ? "No hay novedades guardadas." : b;
    }

    private static void dialog(Activity a, List<String[]> secs) {
        try {
            new android.app.AlertDialog.Builder(a, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Novedades")
                .setMessage(format(secs))
                .setPositiveButton("Entendido", null)
                .show();
        } catch (Throwable ignored) { }
    }

    /** Pantalla principal: si la app se ha actualizado desde la última vez, enseña lo nuevo (una sola vez). La primera instalación no enseña nada. */
    static void showIfNew(Activity a) {
        SharedPreferences m = Prefs.meta(a);
        String now = version(a), seen = m.getString(SEEN, "");
        if (now.length() == 0 || now.equals(seen)) return;
        m.edit().putString(SEEN, now).commit();
        if (seen.length() == 0) return;
        List<String[]> secs = since(parse(load(a)), seen, 4);
        if (!secs.isEmpty() && !secs.get(0)[0].equals(seen)) dialog(a, secs);
    }

    /** Botón «Novedades»: las últimas versiones, cuando se quiera. */
    static void showLatest(Activity a) { dialog(a, since(parse(load(a)), "", 3)); }
}
