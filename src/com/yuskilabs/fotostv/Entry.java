package com.yuskilabs.fotostv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Entry {
    String title = "";
    String url = "";
    String owner = "";        // nombre del perfil que comparte este álbum ("" = propio); los compartidos son de solo lectura
    int kind;                 // 0 álbum normal, 1 Todas, 2 Favoritos, 3 Ocultas, 4 temporal de invitado, 5 lugar o búsqueda, 6 álbum virtual
    String gid = "";          // (temporal) identificador
    long expires;             // (temporal) cuándo caduca
    List<Item> src = new ArrayList<Item>();     // todo lo del álbum
    List<Item> items = new ArrayList<Item>();   // lo que se ve (sin las ocultas, salvo en «Ocultas»)
    List<Item> view;          // lista ordenada (null = tal cual la da Google)
    String order = "desc";   // random | asc | desc: por defecto, recientes primero
    boolean orderSet;        // el usuario eligió el orden (si no, el pase del álbum va aleatorio)
    Item cover;
    boolean coverPinned;     // portada fijada por el usuario (si no, al azar)
    List<String> coverKeys = new ArrayList<String>();   // portadas fijadas (varias con la función extra)
    String query;            // búsqueda que genera el álbum (virtual, o mosaico que viene de «Buscar»)
    Set<String> newKeys = new HashSet<String>();      // fotos nuevas desde la última visita (función extra)

    boolean onlyVideos;       // filtro «Vídeos»
    List<Item> vids = new ArrayList<Item>();

    int dayLo = -1, dayHi = -1;   // filtro «Ir a la fecha»: de un día, un mes o un año (claves de día; -1 = todas)
    private List<Item> dayOf, dayList;
    private int dayN = -1, dayCLo = -2, dayCHi = -2;

    List<Item> dupFront;      // «Buscar repetidas»: las que se quedan, cada una con sus repetidas al lado, y luego el resto (null = orden normal)

    List<Item> base() { return onlyVideos ? vids : (dupFront != null ? dupFront : (view != null ? view : items)); }

    List<Item> shown() {
        List<Item> b = base();
        if (dayLo < 0) return b;
        if (dayOf != b || dayN != b.size() || dayList == null || dayCLo != dayLo || dayCHi != dayHi) {
            java.util.TimeZone tz = java.util.TimeZone.getDefault();
            List<Item> r = new ArrayList<Item>();
            for (Item it : b) { int k = FxLogic.dayKey(it.ts, tz); if (k >= dayLo && k <= dayHi) r.add(it); }
            dayOf = b;
            dayN = b.size();
            dayCLo = dayLo;
            dayCHi = dayHi;
            dayList = r;
        }
        return dayList;
    }
}
