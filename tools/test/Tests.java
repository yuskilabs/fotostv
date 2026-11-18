package com.yuskilabs.fotostv;

import java.util.*;

/**
 * Pruebas automáticas de la lógica de FotosTV (la que no depende de Android): se ejecutan en un ordenador con
 * tools/test/run.sh. Cada comprobación que falla se apunta y al final el programa termina con error.
 */
public class Tests {
    static int ok, bad;

    static void check(String name, boolean cond) {
        if (cond) ok++; else { bad++; System.out.println("FALLA: " + name); }
    }

    static void eq(String name, Object a, Object b) {
        boolean same = a == null ? b == null : a.equals(b);
        if (same) ok++; else { bad++; System.out.println("FALLA: " + name + "  (salió «" + a + "», se esperaba «" + b + "»)"); }
    }

    static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    static long at(int y, int m, int d) {
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(y, m - 1, d, 12, 0, 0);
        return c.getTimeInMillis();
    }

    static boolean dm(String q, long ts) { return FxLogic.dateMatches(FxLogic.parse(q), ts, UTC); }

    static List<Item> items(long... ts) {
        List<Item> l = new ArrayList<Item>();
        for (int i = 0; i < ts.length; i++) l.add(new Item("u" + i, ts[i]));
        return l;
    }

    public static void main(String[] a) {
        // ---- modo noche
        check("noche 22-8 a las 23", FxLogic.inWindow("22-8", 23));
        check("noche 22-8 a las 3", FxLogic.inWindow("22-8", 3));
        check("noche 22-8 a las 12", !FxLogic.inWindow("22-8", 12));
        check("noche off", !FxLogic.inWindow("off", 23));

        // ---- recuerdos de hoy y de la semana
        long now = at(2026, 11, 17);
        List<Item> l = items(at(2020, 11, 17), at(2020, 11, 19), at(2020, 11, 25), at(2026, 11, 17), 0, at(2019, 11, 14), at(2018, 3, 3));
        eq("hoy: solo el mismo día de años anteriores", FxLogic.onThisDay(l, now, UTC).size(), 1);
        List<Item> w = FxLogic.onThisWeek(l, now, UTC);
        eq("semana: 17, 19 y 14 de noviembre", w.size(), 3);
        check("semana: no entra el 25", !w.contains(l.get(2)));
        check("semana: no entra el de este año", !w.contains(l.get(3)));
        List<Item> ny = items(at(2020, 12, 30), at(2020, 1, 2), at(2020, 1, 20));
        eq("semana cruzando fin de año", FxLogic.onThisWeek(ny, at(2026, 1, 1), UTC).size(), 2);
        eq("años de diferencia", FxLogic.yearsAgo(at(2020, 11, 17), now, UTC), 6);

        // ---- mosaicos
        eq("mosaicos normal", FxLogic.mosaicOdds("normal", 14), 14);
        eq("mosaicos pocos", FxLogic.mosaicOdds("few", 14), 28);
        eq("mosaicos muchos", FxLogic.mosaicOdds("many", 14), 7);
        eq("mosaicos muchos mínimo 1", FxLogic.mosaicOdds("many", 1), 1);

        // ---- preajustes
        for (String id : FxLogic.PRESET_IDS) check("preajuste con valores: " + id, !FxLogic.presetValues(id).isEmpty());
        check("preajuste desconocido vacío", FxLogic.presetValues("x").isEmpty());
        eq("siguiente preajuste desde personalizado", FxLogic.nextPreset(""), "calm");
        eq("siguiente preajuste del último", FxLogic.nextPreset("memories"), "calm");
        eq("nombre personalizado", FxLogic.presetName(""), "Personalizado");
        Map<String, Object> now1 = new HashMap<String, Object>(FxLogic.presetValues("party"));
        check("coincide con el preajuste aplicado", FxLogic.presetMatches(now1, "party"));
        now1.put("interval", 99);
        check("cambiar algo a mano lo deja personalizado", !FxLogic.presetMatches(now1, "party"));
        check("lo que falta cuenta como distinto", !FxLogic.presetMatches(new HashMap<String, Object>(), "calm"));
        Map<String, Object> before = new LinkedHashMap<String, Object>();
        before.put("order", "asc"); before.put("interval", 60); before.put("fx_kenBurns", true);
        String enc = FxLogic.encodeBase(before, FxLogic.presetValues("calm").keySet());
        Map<String, Object> back = FxLogic.decodeBase(enc);
        eq("volver: texto", back.get("order"), "asc");
        eq("volver: número", back.get("interval"), 60);
        eq("volver: sí/no", back.get("fx_kenBurns"), true);
        check("volver: lo que no existía se quita", back.containsKey("fx_mosaics") && back.get("fx_mosaics") == null);
        eq("decodificar texto vacío", FxLogic.decodeBase("").size(), 0);

        // ---- autor por cámara
        eq("autor iPhone 13", FxLogic.credit("Apple iPhone 13", null, null), "Lola");
        eq("autor iPhone 14 Pro", FxLogic.credit("Apple iPhone 14 Pro", null, null), "Alex");
        eq("autor reglas propias", FxLogic.credit("Apple iPhone 13", null, "cámara: iphone 13 = Marta"), "Marta");

        // ---- búsqueda
        List<Item> s = items(at(2023, 8, 10), at(2024, 8, 10), at(2024, 1, 5));
        Map<String, String> places = new HashMap<String, String>();
        places.put(s.get(0).key(), "Motril, Granada");
        eq("buscar por lugar", Search.filter(s, places, "motril").size(), 1);
        eq("buscar por año", Search.filter(s, places, "2024").size(), 2);

        // ---- fechas: rangos, días sueltos, épocas
        check("junio julio agosto 2026: julio", dm("junio julio agosto 2026", at(2026, 7, 15)));
        check("junio julio agosto 2026: junio", dm("junio julio agosto 2026", at(2026, 6, 2)));
        check("junio julio agosto 2026: septiembre no", !dm("junio julio agosto 2026", at(2026, 9, 2)));
        check("junio julio agosto 2026: otro año no", !dm("junio julio agosto 2026", at(2025, 7, 15)));
        check("junio-agosto 2026 incluye julio", dm("junio-agosto 2026", at(2026, 7, 15)));
        check("de junio a agosto 2026", dm("de junio a agosto 2026", at(2026, 8, 31)));
        check("junio-agosto 2026 no incluye mayo", !dm("junio-agosto 2026", at(2026, 5, 31)));
        check("2023-2025 incluye 2024", dm("2023-2025", at(2024, 3, 3)));
        check("2023-2025 no incluye 2026", !dm("2023-2025", at(2026, 3, 3)));
        check("15 de agosto de 2025", dm("15 de agosto de 2025", at(2025, 8, 15)));
        check("15 agosto 2025: otro día no", !dm("15 agosto 2025", at(2025, 8, 16)));
        check("15/08/2025", dm("15/08/2025", at(2025, 8, 15)));
        check("2025-08-15", dm("2025-08-15", at(2025, 8, 15)));
        check("del 10 al 20 de agosto 2025: 20", dm("del 10 al 20 de agosto 2025", at(2025, 8, 20)));
        check("del 10 al 20 de agosto 2025: 21 no", !dm("del 10 al 20 de agosto 2025", at(2025, 8, 21)));
        check("agosto 2024 y agosto 2025: 2025", dm("agosto 2024 y agosto 2025", at(2025, 8, 3)));
        check("agosto 2024 y agosto 2025: 2024", dm("agosto 2024 y agosto 2025", at(2024, 8, 3)));
        check("agosto 2024 y agosto 2025: 2023 no", !dm("agosto 2024 y agosto 2025", at(2023, 8, 3)));
        check("verano 2026: julio", dm("verano 2026", at(2026, 7, 1)));
        check("verano 2026: 21 de junio", dm("verano 2026", at(2026, 6, 21)));
        check("verano 2026: 20 de junio no", !dm("verano 2026", at(2026, 6, 20)));
        check("verano sin año: cualquier año", dm("verano", at(2019, 8, 1)));
        check("navidad 2025: 25 de diciembre de 2025", dm("navidad 2025", at(2025, 12, 25)));
        check("navidad 2025: 3 de enero de 2026", dm("navidad 2025", at(2026, 1, 3)));
        check("navidad 2025: 3 de enero de 2025 no", !dm("navidad 2025", at(2025, 1, 3)));
        check("navidad sin año: enero", dm("navidad", at(2021, 1, 5)));
        check("semana santa 2026 (5 de abril es Pascua)", dm("semana santa 2026", at(2026, 4, 5)));
        check("semana santa 2026: Domingo de Ramos", dm("semana santa 2026", at(2026, 3, 29)));
        check("semana santa 2026: 6 de abril no", !dm("semana santa 2026", at(2026, 4, 6)));
        check("sin fecha no encaja con una fecha", !dm("agosto 2025", 0));
        FxLogic.Query qw = FxLogic.parse("Lara junio-agosto 2026");
        eq("palabras que no son fecha", qw.words.toString(), "[lara]");
        eq("la fecha escrita se conserva", FxLogic.parse(FxLogic.parse("Zahara junio-agosto 2026").dateText).dates.size(), 1);

        // ---- novedades
        String md = "# Historial\n\n## 7.2\n- Uno\n- Dos\n\n## 7.1\n- Antiguo\n";
        List<String[]> secs = Novedades.parse(md);
        eq("novedades: secciones", secs.size(), 2);
        eq("novedades: versión", secs.get(0)[0], "7.2");
        check("novedades: viñetas", secs.get(0)[1].startsWith("• Uno"));
        eq("novedades desde 7.1", Novedades.since(secs, "7.1", 4).size(), 1);
        eq("novedades sin versión previa", Novedades.since(secs, "", 4).size(), 2);

        // ---- copia entre teles
        eq("dirección simple", TvCopy.cleanAddr("192.168.1.20"), "192.168.1.20:8080");
        eq("dirección con http y barra", TvCopy.cleanAddr(" http://192.168.1.20:8080/ "), "192.168.1.20:8080");
        eq("dirección rara", TvCopy.cleanAddr("miweb.com"), null);
        eq("dirección vacía", TvCopy.cleanAddr(""), null);

        System.out.println(ok + " comprobaciones bien, " + bad + " mal.");
        if (bad > 0) System.exit(1);
    }
}
