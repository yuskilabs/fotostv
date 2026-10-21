package com.yuskilabs.fotostv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lee la página pública de un álbum de Google Fotos compartido por enlace. */
class AlbumFetcher {
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    // Elemento con fecha: ["AF1Qip…",["https://lh3…/pw/…",ancho,alto,…],fecha_ms
    static final Pattern ITEM = Pattern.compile(
            "\\[\"(AF1Qip[A-Za-z0-9_\\-]+)\",\\[\"(https://lh3\\.googleusercontent\\.com/pw/[A-Za-z0-9_\\-]+)\",\\d+,\\d+(?:[^\\[\\]]|\\[[^\\[\\]]*\\])*\\],(\\d{11,14})");
    // Respaldo sin fecha
    static final Pattern PHOTO = Pattern.compile(
            "\\[\"(https://lh3\\.googleusercontent\\.com/pw/[A-Za-z0-9_\\-]+)\",\\d+,\\d+");
    static final Pattern TITLE = Pattern.compile("<title>([^<]*)</title>");

    static class AlbumData {
        String title = "";
        String key = "";
        List<Item> items = new ArrayList<Item>();
    }

    static final String RPC = "snAcKc";
    static final String BATCH = "https://photos.google.com/_/PhotosUi/data/batchexecute";
    static final Pattern CANON = Pattern.compile("<link\\s+rel=\"canonical\"\\s+href=\"([^\"]+)\"");
    static final Pattern SHARE = Pattern.compile("/share/([A-Za-z0-9_\\-]+)");
    static final Pattern KEY = Pattern.compile("[?&]key=([A-Za-z0-9_\\-]+)");

    static AlbumData fetchAlbum(String albumUrl) throws IOException {
        if (albumUrl == null || albumUrl.trim().length() == 0)
            throw new IOException("No hay ningún álbum configurado");
        String[] finalUrl = new String[1];
        String html = new String(get(albumUrl.trim(), Integer.MAX_VALUE, finalUrl), "UTF-8");
        AlbumData d = new AlbumData();
        Matcher t = TITLE.matcher(html);
        if (t.find()) d.title = cleanTitle(t.group(1));

        // Todas las páginas mediante la petición interna de la web (de 300 en 300)
        try {
            String ref = finalUrl[0];
            Matcher c = CANON.matcher(html);
            if (c.find()) ref = c.group(1).replace("&amp;", "&");
            Matcher mi = SHARE.matcher(ref), mk = KEY.matcher(ref);
            if (!mk.find()) { mk = KEY.matcher(finalUrl[0]); mk.find(); }
            if (mi.find()) {
                String id = mi.group(1), key = mk.group(1);
                Set<String> seen = new HashSet<String>();
                List<Item> all = new ArrayList<Item>();
                String token = null;
                for (int page = 0; page < 200; page++) {
                    JSONArray r = batchPage(id, key, token);
                    JSONArray items = r.optJSONArray(1);
                    if (items != null) for (int i = 0; i < items.length(); i++) {
                        JSONArray it = items.optJSONArray(i);
                        if (it == null) continue;
                        JSONArray det = it.optJSONArray(1);
                        if (det == null) continue;
                        String u = det.optString(0, "");
                        if (!u.startsWith("https://") || !seen.add(u)) continue;
                        Item nit = new Item(u, it.optLong(2, 0));
                        nit.id = it.optString(0, "");
                        nit.akey = key;
                        all.add(nit);
                    }
                    if (page == 0 && d.title.length() == 0) {
                        JSONArray info = r.optJSONArray(3);
                        if (info != null) d.title = cleanTitle(info.optString(1, ""));
                    }
                    token = r.isNull(2) ? null : r.optString(2, null);
                    if (token == null || token.length() == 0) break;
                }
                if (!all.isEmpty()) { d.items = all; d.key = key; return d; }
            }
        } catch (Exception ignored) { }

        // Respaldo: lo que viene en la propia página (máximo unas 300)
        Set<String> seen = new HashSet<String>();
        Matcher m = ITEM.matcher(html);
        while (m.find()) {
            if (seen.add(m.group(2))) {
                Item nit = new Item(m.group(2), Long.parseLong(m.group(3)));
                nit.id = m.group(1);
                d.items.add(nit);
            }
        }
        m = PHOTO.matcher(html);
        while (m.find()) {
            if (seen.add(m.group(1))) d.items.add(new Item(m.group(1), 0));
        }
        if (d.items.isEmpty())
            throw new IOException("No se encontraron fotos. ¿Es un álbum compartido con enlace?");
        return d;
    }

    static JSONArray batchPage(String id, String key, String token) throws Exception {
        JSONArray args = new JSONArray().put(id).put(token == null ? JSONObject.NULL : token)
                .put(JSONObject.NULL).put(key);
        return rpc(RPC, args);
    }

    /** Nombre del archivo original de un elemento (para saber si es vídeo). */
    static String fileName(String id, String key) throws Exception {
        JSONArray r = rpc("fDcn4b", new JSONArray().put(id).put(JSONObject.NULL).put(key));
        JSONArray info = r.getJSONArray(0);
        return info.isNull(2) ? "" : info.getString(2);
    }

    /** Nombres de archivo de varios elementos del mismo álbum con una sola petición. */
    static java.util.Map<String, String> fileNames(String key, List<String> ids) throws Exception {
        java.util.Map<String, String> out = new java.util.HashMap<String, String>();
        if (ids.isEmpty()) return out;
        JSONArray calls = new JSONArray();
        for (String id : ids)
            calls.put(new JSONArray().put("fDcn4b")
                    .put(new JSONArray().put(id).put(JSONObject.NULL).put(key).toString()));
        String body = "f.req=" + URLEncoder.encode(new JSONArray().put(calls).toString(), "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(BATCH).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        c.setRequestProperty("Cookie", "CONSENT=YES+cb; SOCS=CAI");
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        if (c.getResponseCode() != 200) { c.disconnect(); throw new IOException("HTTP " + c.getResponseCode()); }
        String txt = new String(readAll(c.getInputStream()), "UTF-8");
        c.disconnect();
        JSONArray outer = new JSONArray(txt.substring(txt.indexOf('[')));
        for (int i = 0; i < outer.length(); i++) {
            JSONArray e = outer.optJSONArray(i);
            if (e == null || !"fDcn4b".equals(e.optString(1)) || e.isNull(2)) continue;
            try {
                JSONArray photo = new JSONArray(e.getString(2)).getJSONArray(0);
                out.put(photo.getString(0), photo.isNull(2) ? "" : photo.getString(2));
            } catch (Exception ignored) { }
        }
        return out;
    }

    /** Sigue las redirecciones y devuelve la dirección final del vídeo. */
    static String resolve(String url) throws IOException {
        String[] fin = new String[1];
        HttpURLConnection c = open(url, fin);
        String type = c.getContentType();
        c.disconnect();
        if (type != null && type.startsWith("text/")) throw new IOException("No es un vídeo (" + type + ")");
        return fin[0];
    }

    /**
     * Descarga un vídeo a un archivo temporal. Devuelve false si esa dirección no da un vídeo.
     * Se aborta si supera maxBytes.
     */
    static boolean downloadVideo(String url, java.io.File out, long maxBytes) {
        HttpURLConnection c = null;
        java.io.FileOutputStream fo = null;
        boolean ok = false;
        try {
            c = open(url);
            String type = c.getContentType();
            if (type == null || !(type.startsWith("video/") || type.startsWith("application/octet-stream"))) return false;
            long len = c.getContentLength();
            if (len > maxBytes) return false;
            InputStream in = c.getInputStream();
            fo = new java.io.FileOutputStream(out);
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes || Thread.currentThread().isInterrupted()) return false;
                fo.write(buf, 0, n);
            }
            in.close();
            ok = total > 10000;
            return ok;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fo != null) fo.close(); } catch (IOException ignored) { }
            if (c != null) c.disconnect();
            if (!ok) out.delete();
        }
    }

    static JSONArray rpc(String rpcId, JSONArray args) throws Exception {
        JSONArray req = new JSONArray().put(new JSONArray().put(new JSONArray().put(rpcId).put(args.toString())));
        String body = "f.req=" + URLEncoder.encode(req.toString(), "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(BATCH).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        c.setRequestProperty("Cookie", "CONSENT=YES+cb; SOCS=CAI");
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();
        if (c.getResponseCode() != 200) { c.disconnect(); throw new IOException("HTTP " + c.getResponseCode()); }
        String txt = new String(readAll(c.getInputStream()), "UTF-8");
        c.disconnect();
        int start = txt.indexOf('[');
        JSONArray outer = new JSONArray(txt.substring(start));
        for (int i = 0; i < outer.length(); i++) {
            JSONArray e = outer.optJSONArray(i);
            if (e != null && rpcId.equals(e.optString(1)) && !e.isNull(2)) return new JSONArray(e.getString(2));
        }
        throw new IOException("Respuesta inesperada");
    }

    static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    static String cleanTitle(String s) {
        s = s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").trim();
        s = s.replaceAll("\\s*[-–·|]\\s*Google (Fotos|Photos)\\s*$", "");
        if (s.equalsIgnoreCase("Google Fotos") || s.equalsIgnoreCase("Google Photos")) s = "";
        return s.trim();
    }

    static HttpURLConnection open(String url) throws IOException {
        return open(url, null);
    }

    static HttpURLConnection open(String url, String[] finalUrl) throws IOException {
        String u = url;
        for (int hop = 0; hop < 8; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
            c.setRequestProperty("Cookie", "CONSENT=YES+cb; SOCS=CAI");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirección sin destino");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code != 200) { c.disconnect(); throw new IOException("Error HTTP " + code); }
            if (finalUrl != null) finalUrl[0] = u;
            return c;
        }
        throw new IOException("Demasiadas redirecciones");
    }

    static byte[] get(String url) throws IOException {
        return get(url, Integer.MAX_VALUE);
    }

    /** Descarga como mucho maxBytes (sirve para leer solo la cabecera EXIF). */
    static byte[] get(String url, int maxBytes) throws IOException {
        return get(url, maxBytes, null);
    }

    static byte[] get(String url, int maxBytes, String[] finalUrl) throws IOException {
        HttpURLConnection c = open(url, finalUrl);
        InputStream in = c.getInputStream();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while (out.size() < maxBytes && (n = in.read(buf, 0, Math.min(buf.length, maxBytes - out.size()))) > 0)
                out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            try { in.close(); } catch (IOException ignored) { }
            c.disconnect();
        }
    }

    /** Tipo de contenido sin descargar el cuerpo. */
    static String contentType(String url) throws IOException {
        HttpURLConnection c = open(url);
        String t = c.getContentType();
        c.disconnect();
        return t == null ? "" : t;
    }
}
