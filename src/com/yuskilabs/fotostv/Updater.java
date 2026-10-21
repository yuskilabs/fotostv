package com.yuskilabs.fotostv;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/** Busca la última versión publicada en GitHub (Releases) y la descarga/instala. */
class Updater {
    static final String REPO = "yuskilabs/fotostv";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

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

    /** Último release con APK, o null si no hay ninguno publicado. */
    static Info latest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "FotosTV");
        c.setRequestProperty("Accept", "application/vnd.github+json");
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
            i.version = o.optString("tag_name", "").replaceFirst("^[vV]", "");
            i.notes = o.optString("body", "");
            JSONArray as = o.optJSONArray("assets");
            if (as != null) for (int k = 0; k < as.length(); k++) {
                JSONObject a = as.getJSONObject(k);
                if (a.optString("name", "").toLowerCase().endsWith(".apk")) {
                    i.apkUrl = a.optString("browser_download_url", "");
                    i.size = a.optLong("size", 0);
                    break;
                }
            }
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
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getCacheDir();
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
            out.setReadable(true, false);
            return out;
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            try { if (fo != null) fo.close(); } catch (Exception ignored) { }
            h.disconnect();
        }
    }

    static void install(Context c, File apk) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
