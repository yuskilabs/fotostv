package com.yuskilabs.fotostv;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pequeña web local para configurar el álbum desde el móvil. */
class WebConfigServer extends Thread {
    interface Listener { void onSaved(); }

    private final Context ctx;
    private final Listener listener;
    private ServerSocket server;
    int port;

    WebConfigServer(Context ctx, Listener l) {
        this.ctx = ctx.getApplicationContext();
        this.listener = l;
        setDaemon(true);
    }

    /** Intenta siempre el 8080 (la dirección no debe cambiar); solo si sigue ocupado tras varios intentos usa otro. */
    boolean open() {
        for (int t = 0; t < 8; t++) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new java.net.InetSocketAddress(8080));
                server = ss;
                port = 8080;
                return true;
            } catch (IOException ignored) { }
            try { Thread.sleep(300); } catch (InterruptedException e) { break; }
        }
        for (int p = 8081; p < 8090; p++) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new java.net.InetSocketAddress(p));
                server = ss;
                port = p;
                return true;
            } catch (IOException ignored) { }
        }
        return false;
    }

    void close() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
    }

    private final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);

    @Override public void run() {
        while (server != null && !server.isClosed()) {
            try {
                final Socket s = server.accept();
                // cada conexión en su propio hilo: los navegadores abren conexiones «de reserva» vacías
                // que, atendidas de una en una, dejaban la página colgada hasta agotar su tiempo
                pool.submit(new Runnable() {
                    public void run() {
                        try {
                            s.setSoTimeout(10000);
                            handle(s);
                        } catch (IOException ignored) {
                        } finally {
                            try { s.close(); } catch (IOException ignored) { }
                        }
                    }
                });
            } catch (IOException ignored) {
            }
        }
        pool.shutdownNow();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1 && c != '\n') if (c != '\r') b.write(c);
        return b.toString("UTF-8");
    }

    private void handle(Socket s) throws IOException {
        InputStream in = new BufferedInputStream(s.getInputStream());
        String req = readLine(in);
        if (req.length() == 0) return;
        int len = 0;
        String line;
        while ((line = readLine(in)).length() > 0) {
            if (line.toLowerCase().startsWith("content-length:"))
                len = Integer.parseInt(line.substring(15).trim());
        }
        String[] parts = req.split(" ");
        String method = parts[0], path = parts.length > 1 ? parts[1] : "/";
        String body;
        String ctype = "text/html; charset=utf-8";
        if (path.startsWith("/img/")) {
            int id = path.startsWith("/img/yuski") ? R.drawable.yuski_logo : R.drawable.logo_full;
            java.io.InputStream ri = ctx.getResources().openRawResource(id);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int rn;
            while ((rn = ri.read(tmp)) > 0) bo.write(tmp, 0, rn);
            ri.close();
            OutputStream o2 = s.getOutputStream();
            o2.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: max-age=86400\r\nContent-Length: "
                    + bo.size() + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            o2.write(bo.toByteArray());
            o2.flush();
            return;
        }
        if (path.startsWith("/export")) {
            body = exportJson();
            ctype = "text/plain; charset=utf-8";
        } else if ("POST".equals(method) && path.startsWith("/import")) {
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) { int n = in.read(buf, off, len - off); if (n < 0) break; off += n; }
            body = importJson(parseForm(new String(buf, 0, off, "UTF-8")));
        } else if ("POST".equals(method) && path.startsWith("/addmany")) {
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) { int n = in.read(buf, off, len - off); if (n < 0) break; off += n; }
            body = addMany(parseForm(new String(buf, 0, off, "UTF-8")));
        } else if (path.startsWith("/progress")) {
            body = progress();
        } else if ("POST".equals(method) && path.startsWith("/add")) {
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) { int n = in.read(buf, off, len - off); if (n < 0) break; off += n; }
            body = add(parseForm(new String(buf, 0, off, "UTF-8")));
        } else if ("POST".equals(method) && path.startsWith("/save")) {
            byte[] buf = new byte[len];
            int off = 0;
            while (off < len) { int n = in.read(buf, off, len - off); if (n < 0) break; off += n; }
            save(parseForm(new String(buf, 0, off, "UTF-8")));
            if (listener != null) listener.onSaved();
            body = page("<p class='ok'>\u2714 Guardado. En la tele se comprueban los álbumes.</p>"
                    + "<p><a href='/'>Volver a los ajustes</a></p>");
        } else {
            body = form();
        }
        byte[] out = body.getBytes("UTF-8");
        OutputStream o = s.getOutputStream();
        o.write(("HTTP/1.1 200 OK\r\nContent-Type: " + ctype + "\r\nContent-Length: "
                + out.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        o.write(out);
        o.flush();
    }

    private static Map<String, String> parseForm(String s) throws IOException {
        Map<String, String> m = new HashMap<String, String>();
        for (String kv : s.split("&")) {
            int i = kv.indexOf('=');
            if (i <= 0) continue;
            m.put(URLDecoder.decode(kv.substring(0, i), "UTF-8"), URLDecoder.decode(kv.substring(i + 1), "UTF-8"));
        }
        return m;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }

    private String opt(String value, String label, String current) {
        return "<option value='" + value + "'" + (value.equals(current) ? " selected" : "") + ">" + label + "</option>";
    }

    private void save(Map<String, String> f) {
        List<Prefs.Album> list = new ArrayList<Prefs.Album>();
        int n = 0;
        try { n = Integer.parseInt(f.get("n")); } catch (Exception ignored) { }
        for (int i = 0; i < n; i++) {
            String url = val(f, "url" + i);
            if (url.length() == 0 || f.containsKey("del" + i)) continue;
            list.add(new Prefs.Album(url, val(f, "name" + i), f.containsKey("show" + i)));
        }
        String nu = val(f, "urlnew");
        if (nu.length() > 0) list.add(new Prefs.Album(nu, val(f, "namenew"), true));
        Prefs.saveAlbums(ctx, list);
        if (f.containsKey("unhide")) Prefs.clearHidden(ctx);
        int interval = 60;
        try { interval = Integer.parseInt(f.get("interval")); } catch (Exception ignored) { }
        Prefs.sp(ctx).edit()
            .putInt("interval", interval)
            .putInt("thumbMB", intOf(f.get("thumbMB"), Prefs.thumbMB(ctx)))
            .putInt("videoMB", intOf(f.get("videoMB"), Prefs.videoMB(ctx)))
            .putString("fit", f.containsKey("fit") ? f.get("fit") : "auto")
            .putString("order", f.containsKey("order") ? f.get("order") : "random")
            .putString("albumMode", f.containsKey("albumMode") ? f.get("albumMode") : "all")
            .putString("albumSort", f.containsKey("albumSort") ? f.get("albumSort") : "manual")
            .putBoolean("resume", f.containsKey("resume"))
            .putBoolean("video", f.containsKey("video"))
            .putBoolean("sound", f.containsKey("sound"))
            .putBoolean("clock", f.containsKey("clock"))
            .putBoolean("showAlbum", f.containsKey("showAlbum"))
            .putBoolean("showDate", f.containsKey("showDate"))
            .putBoolean("showPlace", f.containsKey("showPlace"))
            .apply();
    }

    private static int intOf(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // ---- copia de seguridad (exportar / importar)
    private String exportJson() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("fotostv", 1);
            org.json.JSONArray al = new org.json.JSONArray();
            for (Prefs.Album a : Prefs.albums(ctx))
                al.put(new org.json.JSONObject().put("url", a.url).put("name", a.name).put("show", a.show));
            o.put("albums", al);
            org.json.JSONObject s = new org.json.JSONObject();
            s.put("interval", Prefs.interval(ctx)).put("fit", Prefs.fit(ctx)).put("order", Prefs.order(ctx))
             .put("albumMode", Prefs.albumMode(ctx)).put("resume", Prefs.resume(ctx))
             .put("video", Prefs.video(ctx)).put("sound", Prefs.sound(ctx)).put("clock", Prefs.clock(ctx))
             .put("showAlbum", Prefs.showAlbum(ctx)).put("showDate", Prefs.showDate(ctx))
             .put("showPlace", Prefs.showPlace(ctx)).put("thumbMB", Prefs.thumbMB(ctx))
             .put("videoMB", Prefs.videoMB(ctx));
            o.put("settings", s);
            org.json.JSONArray hid = new org.json.JSONArray();
            for (String k : Prefs.hidden(ctx)) hid.put(k);
            o.put("hidden", hid);
            return o.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String importJson(Map<String, String> f) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(val(f, "data"));
            if (!o.has("fotostv")) return "ER\nEse texto no parece una copia de FotosTV.";
            List<Prefs.Album> list = new ArrayList<Prefs.Album>();
            org.json.JSONArray al = o.optJSONArray("albums");
            if (al != null) for (int i = 0; i < al.length(); i++) {
                org.json.JSONObject a = al.getJSONObject(i);
                String u = a.optString("url", "").trim();
                if (u.length() > 0) list.add(new Prefs.Album(u, a.optString("name", ""), a.optBoolean("show", true)));
            }
            Prefs.saveAlbums(ctx, list);
            org.json.JSONObject s = o.optJSONObject("settings");
            if (s != null) {
                android.content.SharedPreferences.Editor e = Prefs.sp(ctx).edit();
                String[] ints = {"interval", "thumbMB", "videoMB"};
                String[] strs = {"fit", "order", "albumMode"};
                String[] bools = {"resume", "video", "sound", "clock", "showAlbum", "showDate", "showPlace"};
                for (String k : ints) if (s.has(k)) e.putInt(k, s.optInt(k));
                for (String k : strs) if (s.has(k)) e.putString(k, s.optString(k));
                for (String k : bools) if (s.has(k)) e.putBoolean(k, s.optBoolean(k));
                e.apply();
            }
            org.json.JSONArray hid = o.optJSONArray("hidden");
            if (hid != null) {
                java.util.Set<String> hs = new java.util.HashSet<String>();
                for (int i = 0; i < hid.length(); i++) hs.add(hid.getString(i));
                Prefs.sp(ctx).edit().putStringSet("hidden", hs).apply();
            }
            if (listener != null) listener.onSaved();
            return "OK\n\u2714 Importado: " + list.size() + " álbumes. La tele los está comprobando.";
        } catch (Exception e) {
            return "ER\nNo se pudo leer ese texto: " + esc(String.valueOf(e.getMessage()));
        }
    }

    private static String val(Map<String, String> f, String k) {
        String v = f.get(k);
        return v == null ? "" : v.trim();
    }

    private String chk(String name, String label, boolean on) {
        return "<label class='chk'><input type='checkbox' name='" + name + "'" + (on ? " checked" : "")
                + "> " + label + "</label>";
    }

    /** Añade un álbum al momento (sin pulsar Guardar) y devuelve la lista actualizada. */
    private String add(Map<String, String> f) {
        String url = val(f, "url"), name = val(f, "name");
        String msg;
        boolean ok = false;
        if (url.length() == 0) {
            msg = "Pega primero el enlace del álbum.";
        } else {
            boolean dup = false;
            for (Prefs.Album al : Prefs.albums(ctx)) if (al.url.equals(url)) dup = true;
            if (dup) {
                msg = "Ese álbum ya estaba añadido.";
            } else {
                try {
                    AlbumFetcher.AlbumData d = AlbumFetcher.fetchAlbum(url);
                    Prefs.saveAlbumCache(ctx, url, d);
                    List<Prefs.Album> l = Prefs.albums(ctx);
                    l.add(new Prefs.Album(url, name, true));
                    Prefs.saveAlbums(ctx, l);
                    String t = name.length() > 0 ? name : (d.title.length() > 0 ? d.title : "Álbum");
                    msg = "✔ «" + esc(t) + "» añadido (" + d.items.size() + " elementos).";
                    ok = true;
                    if (listener != null) listener.onSaved();
                } catch (Exception e) {
                    msg = "✖ No se pudo añadir: " + esc(String.valueOf(e.getMessage()));
                }
            }
        }
        return (ok ? "OK" : "ER") + "\n" + msg + "\n" + albumsHtml();
    }

    // ---- importación en lote (en segundo plano, con progreso)
    private final StringBuilder batchLog = new StringBuilder();
    private volatile boolean batchRunning;

    private synchronized void logLine(String l) { batchLog.append(l).append("<br>"); }

    private String addMany(Map<String, String> f) {
        if (batchRunning) return "ER\nYa hay una importación en marcha.\n";
        String raw = val(f, "urls");
        final List<String> urls = new ArrayList<String>();
        for (String tok : raw.split("[\\s,;]+")) {
            String u = tok.trim();
            if (u.startsWith("http") && !urls.contains(u)) urls.add(u);
        }
        if (urls.isEmpty()) return "ER\nNo veo ningún enlace. Pega uno por línea.\n";
        if (urls.size() > 100) return "ER\nMáximo 100 enlaces cada vez (has pegado " + urls.size() + ").\n";
        synchronized (this) { batchLog.setLength(0); }
        batchRunning = true;
        new Thread(new Runnable() {
            public void run() {
                int added = 0, dup = 0, bad = 0, i = 0;
                try {
                    for (String url : urls) {
                        i++;
                        String tag = i + "/" + urls.size() + " ";
                        boolean exists = false;
                        for (Prefs.Album al : Prefs.albums(ctx)) if (al.url.equals(url)) exists = true;
                        if (exists) { dup++; logLine(tag + "\u21BA ya estaba añadido"); continue; }
                        try {
                            AlbumFetcher.AlbumData d = AlbumFetcher.fetchAlbum(url);
                            Prefs.saveAlbumCache(ctx, url, d);
                            synchronized (WebConfigServer.this) {
                                List<Prefs.Album> l = Prefs.albums(ctx);
                                l.add(new Prefs.Album(url, "", true));
                                Prefs.saveAlbums(ctx, l);
                            }
                            added++;
                            logLine(tag + "\u2714 «" + esc(d.title.length() > 0 ? d.title : "Álbum")
                                    + "» (" + d.items.size() + " elementos)");
                        } catch (Exception e) {
                            bad++;
                            logLine(tag + "\u2716 error: " + esc(String.valueOf(e.getMessage())));
                        }
                    }
                } finally {
                    logLine("<b>Terminado: " + added + " añadidos, " + dup + " repetidos, " + bad + " con error.</b>");
                    batchRunning = false;
                    if (listener != null) listener.onSaved();
                }
            }
        }).start();
        return "OK\nImportando " + urls.size() + " enlaces…\n";
    }

    private synchronized String progress() {
        return (batchRunning ? "RUN" : "END") + "\n" + batchLog.toString().replace("\n", " ") + "\n" + albumsHtml();
    }

    private String albumsHtml() {
        List<Prefs.Album> albums = Prefs.albums(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("<input type='hidden' name='n' value='").append(albums.size()).append("'>");
        if (albums.isEmpty()) sb.append("<p class='muted'>Todavía no hay álbumes.</p>");
        for (int i = 0; i < albums.size(); i++) {
            Prefs.Album al = albums.get(i);
            String[] info = Prefs.albumInfo(ctx, al.url);
            String title = info != null && info[0].length() > 0 ? info[0] : "Nombre (opcional)";
            int count = info != null ? Integer.parseInt(info[2]) : 0;
            sb.append("<div class='card'>")
              .append("<input name='name").append(i).append("' placeholder='").append(esc(title))
              .append("' value='").append(esc(al.name)).append("'>")
              .append("<input name='url").append(i).append("' type='url' value='").append(esc(al.url)).append("'>")
              .append("<div class='row'>")
              .append(chk("show" + i, "Mostrar", al.show))
              .append("<span class='muted'>").append(count > 0 ? count + " elementos" : "sin comprobar")
              .append(!al.show && "selected".equals(Prefs.albumMode(ctx)) ? " \u00B7 oculto" : "").append("</span>")
              .append(chk("del" + i, "Quitar", false))
              .append("</div><div class='row2'>")
              .append("<button type='button' class='sec' onclick='mv(this,-1)'>\u2191 Subir</button>")
              .append("<button type='button' class='sec' onclick='mv(this,1)'>\u2193 Bajar</button>")
              .append("</div></div>");
        }
        return sb.toString();
    }

    private String form() {
        String iv = String.valueOf(Prefs.interval(ctx));
        String fit = Prefs.fit(ctx), order = Prefs.order(ctx), mode = Prefs.albumMode(ctx);
        StringBuilder sb = new StringBuilder("<form method='post' action='/save'>");
        sb.append("<h2>Presentación</h2><label>Orden</label><select name='order'>")
          .append(opt("random", "Aleatorio (se baraja cada vez que empieza)", order))
          .append(opt("norepeat", "Aleatorio sin repetir hasta verlas todas", order))
          .append(opt("asc", "Por fecha, de más antigua a más reciente", order))
          .append(opt("desc", "Por fecha, de más reciente a más antigua", order))
          .append("</select>")
          .append(chk("resume", "Por fecha: seguir donde se quedó la última vez", Prefs.resume(ctx)))
          .append("<label>Cambiar de foto cada</label><select name='interval'>")
          .append(opt("5", "5 segundos", iv)).append(opt("10", "10 segundos", iv)).append(opt("30", "30 segundos", iv))
          .append(opt("60", "1 minuto", iv)).append(opt("180", "3 minutos", iv))
          .append(opt("300", "5 minutos", iv)).append(opt("600", "10 minutos", iv))
          .append("</select><label>Encuadre</label><select name='fit'>")
          .append(opt("auto", "Automático (recorta solo si encaja)", fit))
          .append(opt("crop", "Llenar pantalla (recorta)", fit))
          .append(opt("fit", "Foto completa (bandas negras)", fit))
          .append("</select>");
        sb.append("<h2>Vídeos</h2>")
          .append(chk("video", "Reproducir vídeos", Prefs.video(ctx)))
          .append(chk("sound", "Con sonido", Prefs.sound(ctx)));
        String tm = String.valueOf(Prefs.thumbMB(ctx)), vm = String.valueOf(Prefs.videoMB(ctx));
        sb.append("<h2>Caché</h2><label>Máximo para miniaturas</label><select name='thumbMB'>")
          .append(opt("100", "100 MB", tm)).append(opt("150", "150 MB", tm)).append(opt("250", "250 MB", tm))
          .append(opt("500", "500 MB", tm)).append(opt("1000", "1 GB", tm)).append(opt("2000", "2 GB", tm))
          .append("</select><label>Máximo para vídeos temporales</label><select name='videoMB'>")
          .append(opt("300", "300 MB", vm)).append(opt("500", "500 MB", vm)).append(opt("1000", "1 GB", vm))
          .append(opt("1500", "1,5 GB", vm)).append(opt("3000", "3 GB", vm))
          .append("</select><p class='muted'>Al llenarse, se borra lo menos usado. En dispositivos con poco espacio (Fire Stick) conviene dejarlo bajo.</p>");
        sb.append("<h2>Información en pantalla</h2>")
          .append(chk("showAlbum", "Nombre del álbum", Prefs.showAlbum(ctx)))
          .append(chk("showDate", "Fecha de captura", Prefs.showDate(ctx)))
          .append(chk("showPlace", "Lugar (si la foto tiene GPS)", Prefs.showPlace(ctx)))
          .append(chk("clock", "Hora", Prefs.clock(ctx)));
        int nh = Prefs.hidden(ctx).size();
        sb.append("<h2>Fotos ocultas</h2>");
        if (nh == 0) sb.append("<p class='muted'>No has ocultado ninguna foto. Durante el salvapantallas, pulsa \u25BC en el mando para ocultar la que se está viendo.</p>");
        else sb.append(chk("unhide", "Volver a mostrar las " + nh + " fotos ocultas", false));
        sb.append("<h2>Copia de seguridad</h2><p class='muted'>Para pasar álbumes y ajustes a otro dispositivo: pulsa «Exportar», copia el texto, y en el otro pégalo aquí y pulsa «Importar» (sustituye lo que haya).</p>")
          .append("<textarea id='bk' rows='5' placeholder='Aquí aparece la copia, o pega la que quieras importar'></textarea>")
          .append("<div class='row2'><button type='button' class='sec' onclick='expBk()'>📤 Exportar</button>")
          .append("<button type='button' class='sec' onclick='impBk()'>📥 Importar</button></div>")
          .append("<p id='bkm' class='muted'></p>");
        sb.append("<h2>Mando</h2><p class='muted'>\u25C0 \u25B6 cambiar de foto \u00B7 \u25B2 pausar / continuar \u00B7 \u25BC ocultar esa foto \u00B7 cualquier otro botón sale del salvapantallas.</p>");
        sb.append("<button type='submit'>💾 Guardar</button>");
        StringBuilder alb = new StringBuilder();
        alb.append("<h2>Álbumes</h2>");
        alb.append("<div class='card new'><b>Añadir álbum</b>")
          .append("<input id='nu' type='url' placeholder='https://photos.app.goo.gl/…'>")
          .append("<input id='nn' placeholder='Nombre (opcional; si no, el del álbum)'>")
          .append("<button type='button' id='addb' class='sec' onclick='addAlbum()'>➕ Añadir este álbum</button>")
          .append("<p id='msg' class='muted'></p></div>");
        alb.append("<div class='card new'><b>Añadir varios álbumes a la vez</b>")
          .append("<p class='muted'>Pega los enlaces, uno debajo de otro.</p>")
          .append("<textarea id='nm' rows='6' placeholder='https://photos.app.goo.gl/…&#10;https://photos.app.goo.gl/…'></textarea>")
          .append("<button type='button' id='addm' class='sec' onclick='addMany()'>➕ Importar todos</button>")
          .append("<p id='bmsg' class='muted'></p></div>");
        String asort = Prefs.albumSort(ctx);
        alb.append("<label>Orden de los álbumes en el explorador de la tele</label><select name='albumSort'>")
          .append(opt("manual", "Manual (el de esta lista)", asort))
          .append(opt("newest", "Por la última foto: el más reciente primero", asort))
          .append(opt("oldest", "Por la última foto: el más antiguo primero", asort))
          .append("</select><p class='muted'>La fecha es la de captura de la foto más reciente de cada álbum.</p>");
        alb.append("<label>¿Qué álbumes mostrar?</label><select name='albumMode'>")
          .append(opt("all", "Todos", mode)).append(opt("selected", "Solo los marcados con «Mostrar»", mode))
          .append("</select><p class='muted'>Con «Todos» la casilla «Mostrar» no se tiene en cuenta. "
                + "«Quitar» borra el álbum de la lista al guardar.</p>");
        alb.append("<div class='row2'><button type='button' class='sec' onclick='markAll(true)'>✅ Marcar todos</button>")
          .append("<button type='button' class='sec' onclick='markAll(false)'>⬜ Desmarcar todos</button></div>")
          .append("<button type='button' class='sec danger' onclick='delAll()'>🗑 Quitar todos los álbumes…</button>")
          .append("<p class='muted'>Para ordenarlos a mano usa «Subir» y «Bajar» en cada álbum y pulsa «Guardar» abajo.</p>")
          .append("<div id='albums'>").append(albumsHtml()).append("</div>");
        sb.append(alb);
        sb.append("<button type='submit'>💾 Guardar</button></form>");
        return page(sb.toString());
    }

    private static final String SCRIPT = "<script>"
        + "function delAll(){var c=document.querySelectorAll('#albums input[type=checkbox][name^=del]');"
        + "if(!c.length){alert('No hay álbumes que quitar.');return;}"
        + "if(!confirm('\u26A0 Se quitarán los '+c.length+' álbumes de la lista.\\n\\n'"
        + "+'Las fotos NO se borran de Google Fotos, pero tendrás que volver a añadir los enlaces uno a uno.\\n\\n'"
        + "+'¿Quitar todos?'))return;"
        + "for(var i=0;i<c.length;i++)c[i].checked=true;document.querySelector('form').submit();}"
        + "function renum(){var cs=document.querySelectorAll('#albums .card');"
        + "for(var i=0;i<cs.length;i++){var ins=cs[i].querySelectorAll('input');"
        + "for(var j=0;j<ins.length;j++){ins[j].name=ins[j].name.replace(/[0-9]+$/,i);}}}"
        + "function mv(b,d){var c=b;while(c&&!(c.className||'').match(/(^| )card( |$)/))c=c.parentNode;if(!c)return;"
        + "var p=c.parentNode;if(d<0){var s=c.previousElementSibling;if(s&&(s.className||'').indexOf('card')>=0)p.insertBefore(c,s);}"
        + "else{var s2=c.nextElementSibling;if(s2&&(s2.className||'').indexOf('card')>=0)p.insertBefore(s2,c);}"
        + "renum();c.scrollIntoView({block:'center'});}"
        + "function markAll(v){var c=document.querySelectorAll('#albums input[type=checkbox][name^=show]');"
        + "for(var i=0;i<c.length;i++)c[i].checked=v;}"
        + "function addAlbum(){var u=document.getElementById('nu'),n=document.getElementById('nn'),"
        + "m=document.getElementById('msg'),b=document.getElementById('addb');"
        + "if(!u.value.trim()){m.textContent='Pega primero el enlace del álbum.';return;}"
        + "b.disabled=true;m.textContent='Comprobando álbum…';"
        + "var x=new XMLHttpRequest();x.open('POST','/add');"
        + "x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');"
        + "x.onload=function(){var t=x.responseText,i=t.indexOf('\\n'),j=t.indexOf('\\n',i+1);"
        + "var ok=t.substring(0,i)=='OK';m.innerHTML=t.substring(i+1,j);"
        + "document.getElementById('albums').innerHTML=t.substring(j+1);"
        + "if(ok){u.value='';n.value='';u.focus();}b.disabled=false;};"
        + "x.onerror=function(){m.textContent='No hay conexión con la Shield.';b.disabled=false;};"
        + "x.send('url='+encodeURIComponent(u.value.trim())+'&name='+encodeURIComponent(n.value.trim()));}"
        + "function expBk(){var x=new XMLHttpRequest();x.open('GET','/export');"
        + "x.onload=function(){var a=document.getElementById('bk');a.value=x.responseText;a.focus();a.select();"
        + "document.getElementById('bkm').textContent='Copia este texto y guárdalo, o pégalo en el otro dispositivo.';};x.send();}"
        + "function impBk(){var a=document.getElementById('bk'),m=document.getElementById('bkm');"
        + "if(!a.value.trim()){m.textContent='Pega primero el texto de la copia.';return;}"
        + "if(!confirm('Se sustituirán los álbumes y ajustes actuales. ¿Importar?'))return;"
        + "var x=new XMLHttpRequest();x.open('POST','/import');"
        + "x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');"
        + "x.onload=function(){var t=x.responseText,i=t.indexOf('\\n');m.textContent=t.substring(i+1);"
        + "if(t.substring(0,i)=='OK')setTimeout(function(){location.reload();},1800);};"
        + "x.onerror=function(){m.textContent='No hay conexión con la Shield.';};"
        + "x.send('data='+encodeURIComponent(a.value));}"
        + "function poll(){var x=new XMLHttpRequest();x.open('GET','/progress');"
        + "x.onload=function(){var t=x.responseText,i=t.indexOf('\\n'),j=t.indexOf('\\n',i+1);"
        + "document.getElementById('bmsg').innerHTML=t.substring(i+1,j);"
        + "document.getElementById('albums').innerHTML=t.substring(j+1);"
        + "if(t.substring(0,i)=='RUN')setTimeout(poll,1500);else document.getElementById('addm').disabled=false;};"
        + "x.onerror=function(){setTimeout(poll,3000);};x.send();}"
        + "function addMany(){var a=document.getElementById('nm'),m=document.getElementById('bmsg'),b=document.getElementById('addm');"
        + "if(!a.value.trim()){m.textContent='Pega primero los enlaces.';return;}"
        + "b.disabled=true;m.textContent='Empezando…';"
        + "var x=new XMLHttpRequest();x.open('POST','/addmany');"
        + "x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');"
        + "x.onload=function(){var t=x.responseText,i=t.indexOf('\\n'),j=t.indexOf('\\n',i+1);"
        + "if(t.substring(0,i)=='OK'){a.value='';poll();}else{m.textContent=t.substring(i+1,j);b.disabled=false;}};"
        + "x.onerror=function(){m.textContent='No hay conexión con la Shield.';b.disabled=false;};"
        + "x.send('urls='+encodeURIComponent(a.value));}"
        + "document.addEventListener('keydown',function(e){if(e.key=='Enter'&&(e.target.id=='nu'||e.target.id=='nn')){e.preventDefault();addAlbum();}});"
        + "</script>";

    private static String page(String content) {
        return "<!doctype html><html lang='es'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>FotosTV</title><style>"
            + "body{font-family:-apple-system,system-ui,sans-serif;background:#0f1d2e;color:#eef;margin:0;padding:24px 16px}"
            + "img.logo{height:72px;max-width:100%;display:block;margin:0 0 4px}.by{margin:44px 0 8px;text-align:center;font-size:15px}.by a{color:#89a;text-decoration:none}.by img{height:24px;vertical-align:middle;margin-right:8px}h2{font-size:17px;margin:28px 0 4px;color:#8cf}"
            + ".card{background:#162a40;border-radius:12px;padding:10px 12px;margin:10px 0}.card input{margin:4px 0}"
            + ".row{display:flex;justify-content:space-between;align-items:center;gap:8px}.row .chk{margin:6px 0}"
            + ".muted{color:#89a;font-size:14px}.new{border:1px dashed #456}"
            + "label{display:block;margin:18px 0 6px;font-size:15px;color:#bcd}"
            + "input,select,textarea{width:100%;box-sizing:border-box;font-size:17px;padding:12px;border-radius:10px;border:1px solid #456;background:#1b2d44;color:#fff}"
            + ".chk{display:flex;gap:10px;align-items:center;font-size:17px;color:#eef}.chk{margin:12px 0}.chk input{width:22px;height:22px;margin:0}"
            + "button.sec{margin-top:10px;background:#2a4a6e;font-size:16px;padding:12px}button:disabled{opacity:.5}"
            + "button.danger{background:#7a2b36}.row2{display:flex;gap:10px}.row2 button{width:auto;flex:1}"
            + "button{margin-top:26px;width:100%;font-size:18px;padding:14px;border:0;border-radius:12px;background:#3d8bfd;color:#fff;font-weight:600}"
            + ".ok{font-size:19px}a{color:#8cf}</style></head><body><img class='logo' src='/img/logo.png' alt='FotosTV'>"
            + content + "<div class='by'><a href='https://github.com/yuskilabs/fotostv#readme' target='_blank'><img src='/img/yuski.png' alt=''>yuskilabs</a></div>" + SCRIPT + "</body></html>";
    }
}
