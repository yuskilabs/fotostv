package com.yuskilabs.fotostv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** Busca la última versión publicada en el repositorio de GitHub y la descarga/instala. */
class Updater {
    static final String REPO = "yuskilabs/fotostv";
    /** Fichero del repositorio con la última versión publicada (lo genera tools/publish.py). */
    private static final String INFO = "https://raw.githubusercontent.com/" + REPO + "/main/update.json";

    static class Info {
        String version = "", notes = "", apkUrl = "";
        long size;
    }

    interface Progress { void onPercent(int pct); }

    static String installed(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** Última versión publicada, o null si todavía no hay ninguna. */
    static Info latest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(INFO + "?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "FotosTV");
        try {
            if (c.getResponseCode() == 404) return null;
            if (c.getResponseCode() != 200) throw new Exception("GitHub respondió " + c.getResponseCode());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            InputStream in = c.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            Info i = new Info();
            i.version = o.optString("version", "");
            i.notes = o.optString("notes", "");
            i.apkUrl = o.optString("apk", "");
            i.size = o.optLong("size", 0);
            return i.apkUrl.length() == 0 || i.version.length() == 0 ? null : i;
        } finally {
            c.disconnect();
        }
    }

    /** true si a es una versión posterior a b (compara 5.10 > 5.9 número a número). */
    static boolean newer(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    static File download(Context c, Info info, Progress pr) throws Exception {
        File dir = new File(c.getCacheDir(), "update");
        dir.mkdirs();
        File out = new File(dir, "FotosTV-update.apk");
        out.delete();
        HttpURLConnection h = (HttpURLConnection) new URL(info.apkUrl).openConnection();
        h.setConnectTimeout(20000);
        h.setReadTimeout(30000);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", "FotosTV");
        FileOutputStream fo = null;
        try {
            long len = h.getContentLength() > 0 ? h.getContentLength() : info.size;
            InputStream in = h.getInputStream();
            fo = new FileOutputStream(out);
            byte[] buf = new byte[65536];
            long total = 0;
            int n, last = -1;
            while ((n = in.read(buf)) > 0) {
                fo.write(buf, 0, n);
                total += n;
                if (len > 0 && pr != null) {
                    int p = (int) (total * 100 / len);
                    if (p != last) { last = p; pr.onPercent(p); }
                }
            }
            fo.close();
            fo = null;
            if (total < 50000) throw new Exception("descarga incompleta");
            return out;
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            try { if (fo != null) fo.close(); } catch (Exception ignored) { }
            h.disconnect();
        }
    }

    /** Instala con PackageInstaller: no hace falta que el sistema (ni un gestor de archivos) abra el fichero. */
    static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(p);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            OutputStream os = s.openWrite("FotosTV.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            in.close();
            s.fsync(os);
            os.close();
            Intent it = new Intent(c, InstallReceiver.class);
            it.setAction("com.yuskilabs.fotostv.INSTALL_DONE");
            PendingIntent pend = PendingIntent.getBroadcast(c, id, it, PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pend.getIntentSender());
        } catch (Exception e) {
            s.abandon();
            throw e;
        } finally {
            s.close();
        }
    }

    /** ¿Puede esta app instalar paquetes? (Android 8+: permiso «instalar apps desconocidas» por app). */
    static boolean canInstall(Context c) {
        if (Build.VERSION.SDK_INT < 26) return true;
        try {
            Object r = PackageManager.class.getMethod("canRequestPackageInstalls").invoke(c.getPackageManager());
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return true;
        }
    }

    /** Abre directamente el ajuste de Android donde se concede ese permiso a FotosTV. */
    static void openInstallSettings(Context c) {
        try {
            Intent i = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception e2) {
                Intent i = new Intent(android.provider.Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            }
        }
    }
}
