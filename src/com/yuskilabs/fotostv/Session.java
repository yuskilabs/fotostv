package com.yuskilabs.fotostv;

import java.util.List;

/** Datos que pasa el explorador de álbumes al reproductor (misma app, mismo proceso). */
class Session {
    static List<Item> items;
    static String order = "random";   // random | asc | desc | keep
    static boolean manual;            // true = visor: se mueve con las flechas y no avanza solo
    static String startKey;           // foto por la que empezar (visor)
    static boolean guest;             // el pase es de un álbum temporal de invitado
    static String guestId;            // cuál (para cerrar el pase cuando caduque)
    static volatile boolean playerOn;  // hay un pase abierto (el inicio automático no debe tapar un pase manual)
    static int music;                 // 0 = según los ajustes, 1 = con música aunque esté desactivada (menú «Pase con música»)
    static String gotoUrl, gotoKey;   // «Ir al álbum» desde la ficha: álbum y foto donde abrir el explorador
    static String lastKey;            // última foto vista, para volver al mosaico en ella
}
