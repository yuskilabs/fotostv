package com.yuskilabs.fotostv;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor local mínimo: el reproductor lee de aquí un vídeo que aún se está descargando.
 * Así el búfer es todo lo ya bajado (y la descarga va a la máxima velocidad por delante),
 * en vez del búfer pequeño del reproductor.
 */
final class VideoProxy {
    /** Progreso de una descarga. */
    static final class Prog {
        volatile long len = -1;
        volatile long written;
        volatile boolean done, failed;
    }

    private static final class Entry {
        File part, fin;
        Prog p;
    }

    private static VideoProxy inst;
    private ServerSocket ss;
    private final Map<String, Entry> map = new ConcurrentHashMap<String, Entry>();
    private int seq;

    static synchronized VideoProxy get() {
        if (inst == null) inst = new VideoProxy();
        return inst;
    }

    private VideoProxy() {
        try {
            ss = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            Thread t = new Thread(new Runnable() {
                public void run() { loop(); }
            }, "videoproxy");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            ss = null;
        }
    }

    /** Dirección local para ver ese vídeo; null si el servidor no arrancó. */
    synchronized String register(File part, File fin, Prog p) {
        if (ss == null) return null;
        Entry e = new Entry();
        e.part = part;
        e.fin = fin;
        e.p = p;
        String k = "v" + (++seq);
        map.put(k, e);
        if (map.size() > 40) map.remove("v" + (seq - 40));
        return "http://127.0.0.1:" + ss.getLocalPort() + "/" + k + ".mp4";
    }

    private void loop() {
        while (true) {
            try {
                final Socket s = ss.accept();
                Thread t = new Thread(new Runnable() {
                    public void run() { handle(s); }
                }, "videoproxy-c");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.toString();
    }

    private void handle(Socket s) {
        RandomAccessFile raf = null;
        try {
            s.setSoTimeout(0);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String req = readLine(in);
            String line;
            long from = 0, to = -1;
            boolean ranged = false;
            while ((line = readLine(in)).length() > 0) {
                String l = line.toLowerCase();
                if (l.startsWith("range:") && l.contains("bytes=")) {
                    String r = l.substring(l.indexOf("bytes=") + 6).trim();
                    int d = r.indexOf('-');
                    if (d > 0) from = Long.parseLong(r.substring(0, d).trim());
                    if (d >= 0 && d + 1 < r.length() && r.substring(d + 1).trim().length() > 0)
                        to = Long.parseLong(r.substring(d + 1).trim());
                    ranged = true;
                }
            }
            String[] parts = req.split(" ");
            String k = parts.length > 1 ? parts[1].replace("/", "").replace(".mp4", "") : "";
            Entry e = map.get(k);
            if (e == null) { out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes()); return; }
            Prog p = e.p;
            long t0 = System.currentTimeMillis();
            while (p.len <= 0 && !p.failed && !p.done && System.currentTimeMillis() - t0 < 90000) Thread.sleep(50);
            long total = p.len > 0 ? p.len : (e.fin.exists() ? e.fin.length() : -1);
            if (total <= 0) { out.write("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n".getBytes()); return; }
            if (from >= total) { out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + total + "\r\nContent-Length: 0\r\n\r\n").getBytes()); return; }
            if (to < 0 || to >= total) to = total - 1;
            // antes de empezar se espera a tener unos segundos por delante (wifi lento, dispositivos justos)
            long need = Math.min(total, from + 4L * 1024 * 1024);
            t0 = System.currentTimeMillis();
            while (!p.done && !p.failed && p.written < need && System.currentTimeMillis() - t0 < 20000) Thread.sleep(50);
            long n = to - from + 1;
            String head = (ranged ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n")
                    + "Content-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: " + n + "\r\n"
                    + (ranged ? "Content-Range: bytes " + from + "-" + to + "/" + total + "\r\n" : "")
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes());
            long pos = from;
            byte[] buf = new byte[65536];
            while (pos <= to) {
                long avail = (p.done ? total : p.written) - pos;
                if (avail <= 0) {
                    if (p.failed) break;
                    Thread.sleep(40);
                    continue;
                }
                if (raf == null) {
                    File f = e.part.exists() ? e.part : e.fin;
                    raf = new RandomAccessFile(f, "r");
                }
                raf.seek(pos);
                int len = (int) Math.min(Math.min(avail, buf.length), to - pos + 1);
                int r = raf.read(buf, 0, len);
                if (r <= 0) {
                    // el archivo se acaba de renombrar: se reabre con el nombre final
                    raf.close();
                    raf = null;
                    if (!e.fin.exists() && !e.part.exists()) break;
                    Thread.sleep(40);
                    continue;
                }
                out.write(buf, 0, r);
                pos += r;
            }
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try { if (raf != null) raf.close(); } catch (IOException ignored) { }
            try { s.close(); } catch (IOException ignored) { }
        }
    }
}
