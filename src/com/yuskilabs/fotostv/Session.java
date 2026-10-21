package com.yuskilabs.fotostv;

import java.util.List;

/** Datos que pasa el explorador de álbumes al reproductor (misma app, mismo proceso). */
class Session {
    static List<Item> items;
    static String order = "random";   // random | asc | desc | keep
    static boolean manual;            // true = visor: se mueve con las flechas y no avanza solo
    static String startKey;           // foto por la que empezar (visor)
    static String lastKey;            // última foto vista, para volver al mosaico en ella
}
