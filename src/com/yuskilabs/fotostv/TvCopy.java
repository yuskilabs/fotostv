package com.yuskilabs.fotostv;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Copiar los ajustes de otra tele por la red, sin archivos: se le pide a la web de configuración de la otra tele
 * su copia de seguridad (con su PIN) y se importa aquí, igual que si se hubiera restaurado a mano.
 */
final class TvCopy {
    private TvCopy() { }

    private static final Pattern ADDR = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?$");

    /** «192.168.1.20», «192.168.1.20:8080» o con «http://»: devuelve «ip:puerto», o null si no parece una dirección. */
    static String cleanAddr(String raw) {
        if (raw == null) return null;
        String a = raw.trim();
        if (a.startsWith("http://")) a = a.substring(7);
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        if (!ADDR.matcher(a).matches()) return null;
        if (a.indexOf(':') < 0) a += ":8080";
        return a;
    }

    /** Fuera del hilo de pantalla. Devuelve el mensaje para el usuario (empieza por «OK» o por «ER» y salto de línea). */
    static String pull(Context c, String rawAddr, String pin) {
        String addr = cleanAddr(rawAddr);
        if (addr == null) return "ER\nEscribe la dirección de la otra tele tal como sale en su pantalla principal, por ejemplo 192.168.1.20";
        if (pin == null || pin.trim().length() == 0) return "ER\nEscribe el PIN de la otra tele (sale debajo de su dirección).";
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("http://" + addr + "/export").openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(30000);
            con.setRequestProperty("Cookie", "fp=" + pin.trim());
            int code = con.getResponseCode();
            if (code != 200) return "ER\nLa otra tele no ha dado su copia. Comprueba el PIN y que FotosTV esté abierto en su pantalla principal.";
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String text = bo.toString("UTF-8");
            if (!text.trim().startsWith("{")) return "ER\nLa otra tele ha contestado otra cosa. Comprueba el PIN (sin PIN pide la página de entrada).";
            Map<String, String> f = new HashMap<String, String>();
            f.put("data", text);
            return new WebConfigServer(c, null).importJson(f);
        } catch (java.net.SocketTimeoutException e) {
            return "ER\nLa otra tele no contesta. Comprueba que está encendida, con FotosTV abierto y en la misma wifi.";
        } catch (Exception e) {
            return "ER\nNo se pudo conectar con la otra tele: " + e.getMessage();
        }
    }
}
