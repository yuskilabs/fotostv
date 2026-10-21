package com.yuskilabs.fotostv;

class Item {
    final String url;
    final long ts;        // fecha de captura en ms (0 = desconocida)
    String album = "";
    String id = "";       // identificador de Google Fotos (AF1Qip…)
    String akey = "";     // clave del álbum compartido

    Item(String url, long ts) { this.url = url; this.ts = ts; }

    String key() { return Integer.toHexString(url.hashCode()) + url.length(); }
}
