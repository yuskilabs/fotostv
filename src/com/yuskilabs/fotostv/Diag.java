package com.yuskilabs.fotostv;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Registro de errores de la app (Ajustes → Diagnóstico). Guarda los últimos fallos en un archivo pequeño, también los
 * cierres inesperados, para poder ver qué ha pasado sin tener la tele conectada a un ordenador.
 */
final class Diag {
    private Diag() { }

    private static final long MAX_BYTES = 48 * 1024;
    private static Context app;
    private static String lastKey = "";
    private static long lastAt;

    static void init(Context c) {
        app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try { write("CIERRE INESPERADO (" + t.getName() + ")", e, null); } catch (Throwable ignored) { }
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    /** Un fallo capturado: dónde ocurrió y qué fue. */
    static void e(String where, Throwable t) { write(where, t, null); }

    /** Un aviso sin excepción (algo raro pero controlado). */
    static void i(String where, String msg) { write(where, null, msg); }

    private static File file() { return app == null ? null : new File(app.getFilesDir(), "diag.log"); }

    private static synchronized void write(String where, Throwable t, String msg) {
        File f = file();
        if (f == null) return;
        String what = msg != null ? msg : (t instanceof OutOfMemoryError ? "falta de memoria" : String.valueOf(t));
        String key = where + "|" + what;
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastAt < 30000) return;     // el mismo fallo en bucle no llena el registro
        lastKey = key;
        lastAt = now;
        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(new Date(now))).append("  ").append(where)
          .append(": ").append(what).append('\n');
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String[] ls = sw.toString().split("\n");
            int n = 0;
            for (String l : ls) {
                if (l.contains("com.yuskilabs.fotostv")) { sb.append("    ").append(l.trim()).append('\n'); if (++n >= 4) break; }
            }
        }
        try {
            if (f.length() > MAX_BYTES) {          // se queda con la mitad más reciente
                byte[] all = readAll(f);
                int from = all.length / 2;
                while (from < all.length && all[from] != '\n') from++;
                FileOutputStream o = new FileOutputStream(f, false);
                o.write(all, Math.min(all.length, from + 1), Math.max(0, all.length - from - 1));
                o.close();
            }
            FileOutputStream o = new FileOutputStream(f, true);
            o.write(sb.toString().getBytes("UTF-8"));
            o.close();
        } catch (Throwable ignored) { }
    }

    private static byte[] readAll(File f) throws java.io.IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally { in.close(); }
    }

    /** Todo el registro, lo más reciente arriba (cada entrada es un bloque que empieza por la fecha). */
    static synchronized String read() {
        File f = file();
        if (f == null || !f.exists()) return "";
        try {
            String all = new String(readAll(f), "UTF-8");
            java.util.List<String> blocks = new java.util.ArrayList<String>();
            StringBuilder cur = null;
            for (String l : all.split("\n")) {
                if (l.length() > 14 && Character.isDigit(l.charAt(0)) && l.charAt(2) == '/') {
                    if (cur != null) blocks.add(cur.toString());
                    cur = new StringBuilder();
                }
                if (cur != null) cur.append(l).append('\n');
            }
            if (cur != null) blocks.add(cur.toString());
            StringBuilder out = new StringBuilder();
            for (int i = blocks.size() - 1; i >= 0; i--) out.append(blocks.get(i));
            return out.toString();
        } catch (Throwable t) { return ""; }
    }

    static synchronized void clear() {
        File f = file();
        if (f != null) f.delete();
        lastKey = "";
    }

    /** Datos del aparato y de la app para la cabecera de la pantalla. */
    static String summary(Context c) {
        Runtime r = Runtime.getRuntime();
        long max = r.maxMemory() >> 20, used = (r.totalMemory() - r.freeMemory()) >> 20;
        return "FotosTV " + Updater.installed(c) + "  ·  Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "  ·  " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\nMemoria de la app: " + used + " MB en uso de " + max + " MB";
    }
}
