package com.yuskilabs.fotostv;

class Item {
    final String url;
    final long ts;        // fecha de captura en ms (0 = desconocida)
    String album = "";
    String id = "";       // identificador de Google Fotos (AF1Qip…)
    String akey = "";     // clave del álbum compartido
    static final long NO_TZ = Long.MIN_VALUE;
    long tz = NO_TZ;      // diferencia horaria del lugar donde se hizo (ms respecto a UTC), si Google la da

    Item(String url, long ts) { this.url = url; this.ts = ts; }

    String key() { return Integer.toHexString(url.hashCode()) + url.length(); }
}
