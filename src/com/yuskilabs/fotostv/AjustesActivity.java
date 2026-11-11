package com.yuskilabs.fotostv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Todos los ajustes de FotosTV en una sola pantalla, por temas: pase, movimiento, información, noche, música, vídeo, explorador, casa y móvil. */
public class AjustesActivity extends SettingsBase {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Button musicBtn, radioBtn, folderBtn, volBtn, dreamMusicBtn, vqBtn;
    private TextView geoView, hint;
    private Button cityBtn, placeBtn, profilesBtn;
    private static final String HINT0 = "Pasa por cada botón para ver qué hace. Atrás: volver a la pantalla principal.";

    private final Runnable geoTick = new Runnable() {
        public void run() {
            new Thread(new Runnable() {
                public void run() {
                    String x;
                    try { x = GeoIndex.status(AjustesActivity.this); } catch (Throwable e) { x = ""; }
                    final String st = x;
                    h.post(new Runnable() {
                        public void run() { if (geoView != null) { String ac = GeoIndex.action(AjustesActivity.this); geoView.setText(st + (ac.length() > 0 ? "\nOK: " + ac : "")); } }
                    });
                }
            }).start();
            h.postDelayed(this, 4000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(44), dp(24), dp(44), dp(14));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0A1424, 0xFF14305A}));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_tune);
        ic.setColorFilter(0xFF7FB2FF);
        head.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(14), 0, 0, 0);
        TextView t = text("Ajustes", 30, Color.WHITE);
        t.setTypeface(Ui.BLACK);
        tt.addView(t);
        tt.addView(text("Todo lo que se puede ajustar, por temas. Los álbumes se gestionan desde la web.", 14, 0xFF9FB3C8));
        head.addView(tt);
        root.addView(head);
        View sep = new View(this);
        sep.setBackgroundColor(0xFF2C4766);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(1));
        sp.setMargins(0, dp(12), 0, dp(12));
        root.addView(sep, sp);

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this), right = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        right.setOrientation(LinearLayout.VERTICAL);

        // --- música
        LinearLayout cm = card("Música del pase", R.drawable.ic_play);
        musicBtn = btn(musicLabel(), R.drawable.ic_tune, new View.OnClickListener() {
            public void onClick(View v) {
                String m = Prefs.musicMode(AjustesActivity.this);
                String nx = "off".equals(m) ? "radio" : "radio".equals(m) ? "folder" : "off";
                Prefs.sp(AjustesActivity.this).edit().putString("musicMode", nx).commit();
                refreshMusic();
            }
        });
        volBtn = btn(volLabel(), R.drawable.ic_tune, new View.OnClickListener() {
            public void onClick(View v) {
                int[] o = {10, 20, 30, 40, 60, 80, 100};
                int cur = Prefs.musicVol(AjustesActivity.this), nx = o[0];
                for (int i = 0; i < o.length; i++) if (o[i] > cur) { nx = o[i]; break; }
                Prefs.sp(AjustesActivity.this).edit().putInt("musicVol", nx).commit();
                refreshMusic();
            }
        });
        radioBtn = btn(radioLabel(), R.drawable.ic_play, new View.OnClickListener() {
            public void onClick(View v) { pickRadio(); }
        });
        folderBtn = btn(folderLabel(), R.drawable.ic_albums, new View.OnClickListener() {
            public void onClick(View v) { pickFolder(); }
        });
        dreamMusicBtn = btn(dreamMusicLabel(), R.drawable.ic_tv, new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.sp(AjustesActivity.this).edit().putBoolean("musicDream", !Prefs.musicDream(AjustesActivity.this)).commit();
                refreshMusic();
            }
        });
        Button albumMusic = fx("Música por álbum", Extras.ALBUM_MUSIC, R.drawable.ic_music,
                "Cada álbum puede tener su emisora (pulsación larga en el álbum, «Música del álbum»); suena en el pase de ese álbum.");
        Button duck = twoFx("Con vídeo", Extras.SOFT_DUCK, "callar música", "bajar música",
                R.drawable.ic_video, "Cuando suena un vídeo con sonido, la música se calla (como siempre) o solo baja de volumen.");
        cm.addView(row(musicBtn, volBtn));
        cm.addView(row(folderBtn, radioBtn));
        cm.addView(row(dreamMusicBtn, albumMusic));
        cm.addView(row(duck, null));
        

        // --- ubicaciones
        LinearLayout cg = card("Ubicaciones", R.drawable.ic_place);
        geoView = text("", 13, 0xFF9FB3C8);
        geoView.setPadding(dp(8), dp(4), dp(8), dp(8));
        geoView.setFocusable(true);
        geoView.setClickable(true);
        geoView.setBackground(focusBg());
        geoView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                GeoIndex.toggle(AjustesActivity.this);
                h.removeCallbacks(geoTick);
                h.post(geoTick);
            }
        });
        cg.addView(geoView);
        Button geoBtn = btn("Revisar ubicaciones", R.drawable.ic_refresh, new View.OnClickListener() {
            public void onClick(View v) {
                new Thread(new Runnable() { public void run() { GeoIndex.recheckPending(AjustesActivity.this); } }).start();
                Toast.makeText(AjustesActivity.this, "Reviso en segundo plano lo que falta (las fotos sin ubicación y los sitios sin nombre).", Toast.LENGTH_LONG).show();
                h.removeCallbacks(geoTick);
                h.postDelayed(geoTick, 1500);
            }
        });
        Button rebuild = btn("Rehacer nombres", R.drawable.ic_place, new View.OnClickListener() {
            public void onClick(View v) { rebuildDialog(); }
        });
        cg.addView(row(geoBtn, rebuild));
        

        // --- salvapantallas
        LinearLayout cs = card("Salvapantallas", R.drawable.ic_tv);
        Button dream = btn("Elegir salvapantallas", R.drawable.ic_tv, new View.OnClickListener() {
            public void onClick(View v) { openDreamSettings(); }
        });
        Button proj = btn("Fondo para Projectivy", R.drawable.ic_wallpaper, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(AjustesActivity.this, ProjectivyActivity.class)); }
        });
        cs.addView(row(dream, proj));
        

        // --- vídeo y caché
        LinearLayout cv = card("Vídeo, caché y ocultas", R.drawable.ic_video);
        vqBtn = btn(vqLabel(), R.drawable.ic_hd, new View.OnClickListener() {
            public void onClick(View v) {
                String q = Prefs.vq(AjustesActivity.this);
                Prefs.sp(AjustesActivity.this).edit().putString("vq", "auto".equals(q) ? "hd" : "hd".equals(q) ? "sd" : "auto").commit();
                vqBtn.setText(vqLabel());
            }
        });
        Button cache = btn("Vaciar caché", R.drawable.ic_sweep, new View.OnClickListener() {
            public void onClick(View v) { clearCache(); }
        });
        cv.addView(row(
            tog("Reproducir vídeos", "video", R.drawable.ic_play, new Bool() { public boolean get() { return Prefs.video(AjustesActivity.this); } }),
            tog("Con sonido", "sound", R.drawable.ic_video, new Bool() { public boolean get() { return Prefs.sound(AjustesActivity.this); } })));
        cv.addView(row(vqBtn, cache));
        cv.addView(row(
            cyc("Miniaturas", "thumbMB", true, new String[]{"100", "150", "250", "500", "1000", "2000"},
                new String[]{"100 MB", "150 MB", "250 MB", "500 MB", "1 GB", "2 GB"}, R.drawable.ic_photo,
                new Cur() { public String get() { return String.valueOf(Prefs.thumbMB(AjustesActivity.this)); } }),
            cyc("Vídeos", "videoMB", true, new String[]{"300", "500", "1000", "1500", "3000"},
                new String[]{"300 MB", "500 MB", "1 GB", "1,5 GB", "3 GB"}, R.drawable.ic_video,
                new Cur() { public String get() { return String.valueOf(Prefs.videoMB(AjustesActivity.this)); } })));
        final int nh = Prefs.hidden(this).size();
        Button unhide = btn(nh == 0 ? "No hay fotos ocultas" : "Mostrar de nuevo las " + nh + " ocultas", R.drawable.ic_visibility, new View.OnClickListener() {
            public void onClick(View v) {
                if (Prefs.hidden(AjustesActivity.this).isEmpty()) return;
                new android.app.AlertDialog.Builder(AjustesActivity.this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Fotos ocultas")
                    .setMessage("Todas las fotos ocultas volverán a mostrarse.")
                    .setPositiveButton("Mostrarlas", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            Prefs.clearHidden(AjustesActivity.this);
                            Toast.makeText(AjustesActivity.this, "Hecho: ya no hay fotos ocultas.", Toast.LENGTH_SHORT).show();
                            ((Button) v).setText("No hay fotos ocultas");
                        }
                    })
                    .setNegativeButton("Cancelar", null).show();
            }
        });
        cv.addView(row(unhide, null));
        

        // --- pase de fotos
        LinearLayout cp = card("Pase de fotos", R.drawable.ic_photo);
        placeBtn = btn(placeLabel(), R.drawable.ic_place, new View.OnClickListener() {
            public void onClick(View v) { pickPlace(); }
        });
        describe(placeBtn, "El pase y el salvapantallas enseñan solo las fotos de un lugar (de las que ya tienen ubicación).");
        Button fav = fx("Más favoritas", Extras.FAV_BOOST, R.drawable.ic_star,
                "Con el orden «inteligente», las favoritas salen tres veces más a menudo.");
        Button collage = fx("Dos verticales", Extras.COLLAGE, R.drawable.ic_photo,
                "En el pase, dos fotos verticales seguidas del mismo álbum se ven juntas, una al lado de otra.");
        cp.addView(row(
            cyc("Orden", "order", false, new String[]{"random", "norepeat", "smart", "asc", "desc"},
                new String[]{"aleatorio", "sin repetir", "inteligente", "por fecha ↑", "por fecha ↓"}, R.drawable.ic_shuffle,
                new Cur() { public String get() { return Prefs.order(AjustesActivity.this); } }),
            cyc("Cambiar cada", "interval", true, new String[]{"5", "10", "30", "60", "180", "300", "600"},
                new String[]{"5 s", "10 s", "30 s", "1 min", "3 min", "5 min", "10 min"}, R.drawable.ic_timer,
                new Cur() { public String get() { return String.valueOf(Prefs.interval(AjustesActivity.this)); } })));
        cp.addView(row(
            cyc("Encuadre", "fit", false, new String[]{"auto", "crop", "fit"},
                new String[]{"auto", "llenar", "completo"}, R.drawable.ic_fullscreen,
                new Cur() { public String get() { return Prefs.fit(AjustesActivity.this); } }),
            tog("Continuar", "resume", R.drawable.ic_check, new Bool() { public boolean get() { return Prefs.resume(AjustesActivity.this); } })));
        cp.addView(row(todayBtn(), fav));
        cp.addView(row(collage, placeBtn));

        // --- movimiento y relleno
        LinearLayout cf = card("Movimiento y relleno", R.drawable.ic_fullscreen);
        Button speed = cycFx("Velocidad", Extras.KB_SPEED, new String[]{"slow", "normal"}, new String[]{"lenta", "normal"},
                R.drawable.ic_timer, "Velocidad del movimiento. En las dos, el movimiento dura más que la foto: nunca se para a la vista.");
        Button edge = cycFx("Bordes", Extras.KB_EDGE, new String[]{"hide", "show"}, new String[]{"tapados", "a la vista"},
                R.drawable.ic_fullscreen, "Tapados: la foto va algo ampliada y no se ve el relleno. A la vista: la foto empieza entera, sin zoom, y al moverse se ve el relleno (negro, color o difuminado).");
        Button bg = cycFx("Relleno", Extras.BACKGROUND, new String[]{"black", "color", "blur"}, new String[]{"negro", "color", "difuminado"},
                R.drawable.ic_wallpaper, "Lo que se ve donde la foto no llena la pantalla (verticales, fotos con otra forma, y al moverse): negro, el color de la foto o la foto muy difuminada.");
        cf.addView(row(motionBtn(), speed));
        cf.addView(row(edge, bg));

        // --- información en pantalla
        LinearLayout ci = card("Información en pantalla", R.drawable.ic_info);
        Button dateFmt = dateFmtBtn();
        Button dateFull = fx("Día y hora", Extras.DATE_FULL, R.drawable.ic_schedule,
                "Añade a la fecha de cada foto el día de la semana y la hora a la que se hizo; apagado, solo la fecha. Funciona con la fecha larga y con la corta.");
        Button bigClock = fx("Reloj grande", Extras.BIG_CLOCK, R.drawable.ic_schedule,
                "Reloj más grande, con el día de la semana y la fecha debajo (si la hora está activada).");
        Button weather = fx("Tiempo", Extras.WEATHER, R.drawable.ic_cloud,
                "Temperatura y cielo de tu ciudad junto a la hora, si la hora está activada (de Open-Meteo, cada media hora).");
        weather.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean now = !Extras.on(AjustesActivity.this, Extras.WEATHER);
                Prefs.sp(AjustesActivity.this).edit().putBoolean(Extras.WEATHER, now).commit();
                ((Button) v).setText("Tiempo: " + (now ? "sí" : "no"));
                if (now && Extras.str(AjustesActivity.this, Extras.CITY, "").length() == 0) askCity();
            }
        });
        cityBtn = btn(cityLabel(), R.drawable.ic_place, new View.OnClickListener() {
            public void onClick(View v) { askCity(); }
        });
        describe(cityBtn, "Ciudad del tiempo. Escribe su nombre («Málaga», «Estepona»…).");
        Button captions = fx("Pies de foto", Extras.CAPTIONS, R.drawable.ic_notes,
                "Permite escribir un texto a cada foto (pulsación larga en el mosaico) y lo enseña en el pase y en la ficha.");
        ci.addView(row(
            tog("Álbum", "showAlbum", R.drawable.ic_albums, new Bool() { public boolean get() { return Prefs.showAlbum(AjustesActivity.this); } }),
            tog("Lugar", "showPlace", R.drawable.ic_place, new Bool() { public boolean get() { return Prefs.showPlace(AjustesActivity.this); } })));
        ci.addView(row(dateFmt, dateFull));
        ci.addView(row(
            tog("Hora", "clock", R.drawable.ic_schedule, new Bool() { public boolean get() { return Prefs.clock(AjustesActivity.this); } }),
            bigClock));
        ci.addView(row(weather, cityBtn));
        ci.addView(row(captions, null));

        // --- noche y temporizador
        LinearLayout cn = card("Noche y temporizador", R.drawable.ic_moon);
        Button night = cycFx("Modo noche", Extras.NIGHT, new String[]{"off", "22-8", "23-7", "0-7"},
                new String[]{"no", "22 a 8 h", "23 a 7 h", "0 a 7 h"}, R.drawable.ic_moon,
                "Durante esas horas el salvapantallas se atenúa (o se apaga) y la música se calla.");
        Button nightMode = cycFx("De noche", Extras.NIGHT_MODE, new String[]{"dim", "black"}, new String[]{"tenue", "pantalla negra"},
                R.drawable.ic_moon, "Tenue: fotos muy oscuras, sin textos. Pantalla negra: el salvapantallas no enseña nada a esas horas.");
        Button timer = cycInt("Temporizador", Extras.TIMER, new int[]{0, 15, 30, 60, 90}, new String[]{"no", "15 min", "30 min", "1 h", "1 h 30"},
                R.drawable.ic_timer, "Cualquier pase (también el salvapantallas) se cierra solo pasado ese tiempo, aunque se use el mando.");
        cn.addView(row(night, nightMode));
        cn.addView(row(timer, null));

        // --- explorador
        LinearLayout ce = card("Explorador", R.drawable.ic_albums);
        Button search = fx("Buscar", Extras.SEARCH, R.drawable.ic_search,
                "Barra de búsqueda en la pantalla principal (encima de los botones; se llega subiendo con el mando): por lugar, álbum o fecha («Zahara», «agosto 2025»).");
        Button virt = fx("Álbumes virtuales", Extras.VIRTUAL, R.drawable.ic_albums,
                "Guarda una búsqueda como álbum («Zahara 2025») y sale en la lista de álbumes; se actualiza solo.");
        Button jump = fx("Saltar por fecha", Extras.DATE_JUMP, R.drawable.ic_event,
                "Botón «Fecha» en el mosaico de un álbum: un calendario mensual para ir a un día y ver solo sus fotos.");
        Button trips = fx("Viajes", Extras.TRIPS, R.drawable.ic_flight,
                "Botón «Viajes» en la pantalla principal: agrupa solas las fotos seguidas en el tiempo, con su lugar y fechas.");
        Button covers = fx("Varias portadas", Extras.MULTI_COVER, R.drawable.ic_photo,
                "Puedes fijar varias portadas por álbum; cada vez que entras sale una de ellas.");
        Button news = fx("Marcar nuevas", Extras.NEW_BADGE, R.drawable.ic_add,
                "Las fotos añadidas al álbum desde la última vez que lo abriste llevan la marca «NUEVA».");
        Button dupes = fx("Fotos repetidas", Extras.DUPES, R.drawable.ic_copy,
                "Pulsación larga en un álbum, «Buscar repetidas»: marca las fotos casi iguales hechas seguidas para ocultarlas de una vez.");
        Button stats = fx("Estadísticas", Extras.STATS, R.drawable.ic_stats,
                "Botón «Estadísticas» en la pantalla principal: fotos por año, lugar, álbum y cámara (con filtro por cámara).");
        ce.addView(row(search, virt));
        ce.addView(row(jump, trips));
        ce.addView(row(covers, news));
        ce.addView(row(dupes, stats));

        // --- casa y móvil
        LinearLayout ch = card("Casa y móvil", R.drawable.ic_guest);
        profilesBtn = btn(profilesLabel(), R.drawable.ic_family, new View.OnClickListener() {
            public void onClick(View v) {
                if (!Prefs.profilesOn(AjustesActivity.this)) {
                    Prefs.meta(AjustesActivity.this).edit().putBoolean("fx_profiles", true).commit();
                    profilesBtn.setText(profilesLabel());
                }
                manageProfiles();
            }
        });
        describe(profilesBtn, "Perfiles con sus propios álbumes, ajustes, PIN y web de configuración. «Familia» es el de siempre; los que añadas empiezan vacíos. Se elige en la pantalla principal con el icono junto al QR; aquí se añaden, renombran y borran.");
        Button remote = fx("Mando del móvil", Extras.REMOTE, R.drawable.ic_remote,
                "La web de ajustes del móvil enseña un mando para pasar fotos, pausar o ver la anterior.");
        Button reload = cycInt("Recargar álbumes", Extras.RELOAD, new int[]{0, 6, 12, 24}, new String[]{"no", "cada 6 h", "cada 12 h", "cada 24 h"},
                R.drawable.ic_refresh, "Mientras la app esté abierta o en el salvapantallas, vuelve a leer los álbumes de Google cada cierto tiempo.");
        ch.addView(row(profilesBtn, remote));
        ch.addView(row(reload, null));

        // orden de lectura de cada columna, de arriba abajo; la última tarjeta se estira para que acaben a la misma altura
        left.addView(cp);
        left.addView(cf);
        left.addView(ci);
        left.addView(cn);
        left.addView(cs);
        left.addView(ch, new LinearLayout.LayoutParams(-1, 0, 1f));
        right.addView(cm);
        right.addView(cv);
        right.addView(ce);
        right.addView(cg, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(0, 0, dp(8), 0);
        cols.addView(left, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
        rp.setMargins(dp(8), 0, 0, 0);
        cols.addView(right, rp);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        sv.addView(cols);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        hint = text(HINT0, 13, 0xFFBBD0E6);
        hint.setPadding(0, dp(6), 0, 0);
        hint.setMinLines(2);
        root.addView(hint);
        setContentView(root);
        musicBtn.requestFocus();
    }

    @Override protected Button btn(String label, int icon, View.OnClickListener l) {
        Button b = super.btn(label, icon, l);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { if (has && hint != null) hint.setText(HINT0); }
        });
        return b;
    }

    @Override protected void onStart() {
        super.onStart();
        h.removeCallbacks(geoTick);
        h.post(geoTick);
    }

    @Override protected void onStop() {
        h.removeCallbacks(geoTick);
        super.onStop();
    }

    // ---- acciones
    private String vqLabel() {
        String q = Prefs.vq(this);
        return "Vídeos: " + ("hd".equals(q) ? "calidad alta" : "sd".equals(q) ? "calidad media" : "calidad auto");
    }

    private void clearCache() {
        Toast.makeText(this, "Vaciando caché…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                long freed = Thumbs.get(AjustesActivity.this).clearAll();
                java.io.File[] vs = new java.io.File(getCacheDir(), "videos").listFiles();
                if (vs != null) for (java.io.File f : vs) { freed += f.length(); f.delete(); }
                final String m = "Caché vaciada (" + (freed / (1024 * 1024)) + " MB liberados).";
                h.post(new Runnable() { public void run() { Toast.makeText(AjustesActivity.this, m, Toast.LENGTH_LONG).show(); } });
            }
        }).start();
    }

    private void rebuildDialog() {
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Rehacer los nombres de lugar")
            .setMessage("Se olvidan todos los nombres y se vuelven a calcular con las reglas actuales, en segundo plano. Puede tardar un buen rato (más de un segundo por cada sitio distinto).")
            .setPositiveButton("Rehacer", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    new Thread(new Runnable() { public void run() { GeoIndex.rebuildNames(AjustesActivity.this); } }).start();
                    h.removeCallbacks(geoTick);
                    h.postDelayed(geoTick, 1500);
                }
            })
            .setNegativeButton("Cancelar", null).show();
    }

    private void openDreamSettings() {
        String[][] tv = {
            {"com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity"},
            {"com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity$Settings"},
        };
        for (String[] c : tv) {
            try {
                Intent i = new Intent();
                i.setClassName(c[0], c[1]);
                startActivity(i);
                return;
            } catch (Throwable ignored) { }
        }
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
            return;
        } catch (Throwable ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        } catch (Throwable e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ---- música

    private String musicLabel() {
        String m = Prefs.musicMode(this);
        return "Música: " + ("radio".equals(m) ? "radio" : "folder".equals(m) ? "carpeta" : "apagada");
    }
    private String radioLabel() {
        String u = Prefs.musicRadio(this);
        if (u.length() == 0) return "Emisora: elegir";
        for (String[] pr : Music.PRESETS) if (pr[1] != null && pr[1].equals(u)) {
            String n = Music.shortName(pr[0]);
            int i = n.indexOf(" (");
            return "Emisora: " + (i > 0 ? n.substring(0, i) : n);
        }
        return "Emisora: la tuya (web)";
    }
    private String folderLabel() {
        String f = Prefs.musicFolder(this);
        if (f.length() == 0) return "Carpeta: elegir";
        return "Carpeta: " + new java.io.File(f).getName();
    }
    private String volLabel() { return "Volumen: " + Prefs.musicVol(this) + " %"; }
    private String dreamMusicLabel() { return "En salvapantallas: " + (Prefs.musicDream(this) ? "sí" : "no"); }

    private void refreshMusic() {
        musicBtn.setText(musicLabel());
        radioBtn.setText(radioLabel());
        folderBtn.setText(folderLabel());
        volBtn.setText(volLabel());
        dreamMusicBtn.setText(dreamMusicLabel());
    }

    /** Lista de emisoras por secciones; las de otra dirección se añaden desde la web. */
    private void pickRadio() {
        final java.util.List<String[]> rowsL = new java.util.ArrayList<String[]>();
        for (String[] pr : Music.PRESETS) rowsL.add(pr);
        android.widget.ListAdapter ad = new android.widget.ArrayAdapter<String[]>(this, android.R.layout.simple_list_item_1, rowsL) {
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int i) { return getItem(i)[1] != null; }
            @Override public View getView(int i, View cv, android.view.ViewGroup par) {
                TextView t = (TextView) super.getView(i, cv, par);
                String[] it = getItem(i);
                boolean head = it[1] == null;
                t.setText(head ? it[0] : Music.shortName(it[0]));
                t.setTypeface(head ? Ui.BLACK : Ui.REG);
                t.setTextColor(head ? 0xFF7FB2FF : (it[1].equals(Prefs.musicRadio(AjustesActivity.this)) ? 0xFF7CE08A : Color.WHITE));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, head ? 13 : 16);
                return t;
            }
        };
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Emisora de radio")
            .setAdapter(ad, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    String u = rowsL.get(w)[1];
                    if (u == null) return;
                    Prefs.sp(AjustesActivity.this).edit().putString("musicRadio", u).putString("musicMode", "radio").commit();
                    refreshMusic();
                }
            })
            .setNegativeButton("Cancelar", null).create();
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface d) { stopPreview(); }
        });
        dlg.show();
        // al posarte sobre una emisora suena; al abrir, suena la que está elegida
        final android.widget.ListView lv = dlg.getListView();
        int cur = 0;
        String curUrl = Prefs.musicRadio(this);
        for (int i = 0; i < rowsL.size(); i++) if (rowsL.get(i)[1] != null && rowsL.get(i)[1].equals(curUrl)) { cur = i; break; }
        if (cur == 0) for (int i = 0; i < rowsL.size(); i++) if (rowsL.get(i)[1] != null) { cur = i; break; }
        lv.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) {
                if (pos >= 0 && pos < rowsL.size() && rowsL.get(pos)[1] != null) preview(rowsL.get(pos)[1]);
                else stopPreview();
            }
            public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });
        lv.requestFocus();
        lv.setSelection(cur);
        preview(rowsL.get(cur)[1]);
    }

    // ---- escucha de prueba en la lista de emisoras
    // Todo lo del reproductor va en un hilo aparte: abrir o soltar una emisora de internet puede tardar
    // segundos y no debe congelar la lista.
    private final java.util.concurrent.ExecutorService prevExec = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile int prevGen;
    private String prevUrl;
    private android.media.MediaPlayer prev;   // solo se toca desde prevExec

    private final Runnable prevRun = new Runnable() {
        public void run() {
            final String url = prevUrl;
            if (url == null) return;
            final int g = prevGen;
            final int vol = Prefs.musicVol(AjustesActivity.this);
            prevExec.submit(new Runnable() {
                public void run() {
                    releasePrev();
                    if (g != prevGen) return;
                    try {
                        final android.media.MediaPlayer m = new android.media.MediaPlayer();
                        prev = m;
                        m.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
                        m.setDataSource(url);
                        m.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                            public void onPrepared(android.media.MediaPlayer p) {
                                if (g != prevGen) return;
                                float v = (float) Math.pow(vol / 100.0, 2);
                                try { p.setVolume(v, v); p.start(); } catch (Exception ignored) { }
                            }
                        });
                        m.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                            public boolean onError(android.media.MediaPlayer p, int w, int e) { return true; }
                        });
                        m.prepareAsync();
                    } catch (Throwable ignored) { }
                }
            });
        }
    };

    private void releasePrev() {
        android.media.MediaPlayer m = prev;
        prev = null;
        if (m != null) {
            try { m.reset(); } catch (Throwable ignored) { }
            try { m.release(); } catch (Throwable ignored) { }
        }
    }

    /** Suena la emisora tras una pausa corta (así, al bajar rápido por la lista, no se abren todas). */
    private void preview(String url) {
        if (url == null) return;
        prevGen++;
        h.removeCallbacks(prevRun);
        prevUrl = url;
        h.postDelayed(prevRun, 500);
    }

    private void stopPreview() {
        prevGen++;
        h.removeCallbacks(prevRun);
        prevUrl = null;
        try {
            prevExec.submit(new Runnable() { public void run() { releasePrev(); } });
        } catch (Throwable ignored) { }
    }

    @Override protected void onPause() { stopPreview(); super.onPause(); }

    private void pickFolder() {
        final java.util.List<String> fs = Music.suggestFolders();
        if (fs.isEmpty()) {
            new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Carpeta de música")
                .setMessage("No encuentro carpetas de música en este dispositivo. Puedes escribir la ruta desde la web de configuración.")
                .setPositiveButton("Vale", null).show();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try { requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 8); } catch (Throwable ignored) { }
        }
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Carpeta de música")
            .setItems(fs.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Prefs.sp(AjustesActivity.this).edit().putString("musicFolder", fs.get(w)).putString("musicMode", "folder").commit();
                    refreshMusic();
                }
            })
            .setNegativeButton("Cancelar", null).show();
    }


    private static final String[] DATE_LABS = {"larga", "corta", "no"};

    private int dateState() {
        if (!Prefs.showDate(this)) return 2;
        return "short".equals(Extras.str(this, Extras.DATE_FMT, "long")) ? 1 : 0;
    }

    /** Fecha de cada foto: larga → corta → no. «Día y hora» (aparte) añade o quita el día de la semana y la hora. */
    private Button dateFmtBtn() {
        final Button[] self = new Button[1];
        self[0] = btn("Fecha", R.drawable.ic_event, new View.OnClickListener() {
            public void onClick(View v) {
                int nx = (dateState() + 1) % 3;
                android.content.SharedPreferences.Editor e = Prefs.sp(AjustesActivity.this).edit();
                e.putBoolean("showDate", nx != 2);
                if (nx != 2) e.putString(Extras.DATE_FMT, nx == 1 ? "short" : "long");
                e.commit();
                self[0].setText("Fecha: " + DATE_LABS[nx]);
            }
        });
        self[0].setText("Fecha: " + DATE_LABS[dateState()]);
        describe(self[0], "Fecha de cada foto en el pase. Larga: «Miércoles 27 de diciembre de 2023 - 15:03». Corta: «Mié, 27 dic 2023 - 15:03». O no enseñarla. «Día y hora» añade o quita el día de la semana y la hora.");
        return self[0];
    }

    // ---- botones con su explicación
    private void describe(final Button b, final String d) {
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { if (has && hint != null) hint.setText(d); }
        });
    }

    private Button fx(String label, final String key, int icon, String desc) {
        Button b = tog(label, key, icon, new Bool() { public boolean get() { return Extras.on(AjustesActivity.this, key); } });
        describe(b, desc);
        return b;
    }

    /** Valores de texto que rotan. */
    private Button cycFx(String prefix, final String key, final String[] vals, String[] labs, int icon, String desc) {
        Button b = cyc(prefix, key, false, vals, labs, icon, new Cur() {
            public String get() { return Extras.str(AjustesActivity.this, key, vals[0]); }
        });
        describe(b, desc);
        return b;
    }

    private static final String[] MOTION_VALS = {"off", "mix", "zoom", "in", "out", "pan"};
    private static final String[] MOTION_LABS = {"no", "variado", "solo zoom", "acercar", "alejar", "desplazar"};

    private String motionNow() {
        return Extras.on(this, Extras.KEN_BURNS) ? Extras.str(this, Extras.KB_STYLE, "mix") : "off";
    }

    /** Movimiento: no → variado → acercar → alejar → desplazar (de lado, arriba/abajo o en diagonal). */
    private Button motionBtn() {
        final Button[] self = new Button[1];
        self[0] = btn("Movimiento", R.drawable.ic_fullscreen, new View.OnClickListener() {
            public void onClick(View v) {
                String cur = motionNow();
                int i = 0;
                for (int k = 0; k < MOTION_VALS.length; k++) if (MOTION_VALS[k].equals(cur)) i = k;
                String nx = MOTION_VALS[(i + 1) % MOTION_VALS.length];
                android.content.SharedPreferences.Editor e = Prefs.sp(AjustesActivity.this).edit();
                e.putBoolean(Extras.KEN_BURNS, !"off".equals(nx));
                if (!"off".equals(nx)) e.putString(Extras.KB_STYLE, nx);
                e.commit();
                self[0].setText("Movimiento: " + MOTION_LABS[(i + 1) % MOTION_VALS.length]);
            }
        });
        String cur = motionNow();
        for (int k = 0; k < MOTION_VALS.length; k++) if (MOTION_VALS[k].equals(cur)) self[0].setText("Movimiento: " + MOTION_LABS[k]);
        describe(self[0], "Movimiento de cada foto, durante todo el tiempo que está en pantalla: variado (al azar acercar, alejar, de lado, arriba y abajo o en diagonal), solo zoom (acercar y alejar) o siempre el mismo.");
        return self[0];
    }

    /** Recuerdos de hoy: no → solo (únicamente fotos de un día como hoy) → mezclados (esas primero y también el resto). */
    private Button todayBtn() {
        final Button[] self = new Button[1];
        self[0] = btn("Recuerdos de hoy", R.drawable.ic_event, new View.OnClickListener() {
            public void onClick(View v) {
                int nx = (todayState() + 1) % 3;
                Prefs.sp(AjustesActivity.this).edit().putBoolean(Extras.TODAY, nx > 0).putBoolean(Extras.TODAY_MIX, nx == 2).commit();
                self[0].setText("Recuerdos de hoy: " + TODAY_LABS[nx]);
            }
        });
        self[0].setText("Recuerdos de hoy: " + TODAY_LABS[todayState()]);
        describe(self[0], "Solo: el salvapantallas enseña únicamente fotos de un día como hoy de años anteriores (si hay al menos 5). Mezclados: esas salen antes (una sí y una no) y también se ven todas las demás. En los dos, con «hace N años».");
        return self[0];
    }

    private static final String[] TODAY_LABS = {"no", "solo", "mezclados"};

    private int todayState() {
        if (!Extras.on(this, Extras.TODAY)) return 0;
        return Extras.on(this, Extras.TODAY_MIX) ? 2 : 1;
    }

    /** Sí/no con palabras propias («Movimiento: suave»). */
    private Button twoFx(final String prefix, final String key, final String off, final String on, int icon, String desc) {
        final Button[] self = new Button[1];
        self[0] = btn(prefix, icon, new View.OnClickListener() {
            public void onClick(View v) {
                boolean now = !Extras.on(AjustesActivity.this, key);
                Prefs.sp(AjustesActivity.this).edit().putBoolean(key, now).commit();
                self[0].setText(prefix + ": " + (now ? on : off));
            }
        });
        self[0].setText(prefix + ": " + (Extras.on(this, key) ? on : off));
        describe(self[0], desc);
        return self[0];
    }

    private Button cycInt(String prefix, final String key, int[] vals, String[] labs, int icon, String desc) {
        String[] sv = new String[vals.length];
        for (int i = 0; i < vals.length; i++) sv[i] = String.valueOf(vals[i]);
        Button b = cyc(prefix, key, true, sv, labs, icon, new Cur() {
            public String get() { return String.valueOf(Extras.num(AjustesActivity.this, key)); }
        });
        describe(b, desc);
        return b;
    }

    // ---- ciudad del tiempo
    private String cityLabel() {
        String c = Extras.str(this, Extras.CITY, "");
        return "Ciudad: " + (c.length() == 0 ? "elegir" : c);
    }

    private void askCity() {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setTextColor(Color.WHITE);
        in.setText(Extras.str(this, Extras.CITY, ""));
        in.setSelection(in.getText().length());
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Ciudad del tiempo")
            .setMessage("Escribe el nombre de tu ciudad o pueblo.")
            .setView(in)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String c = in.getText().toString().trim();
                    Prefs.sp(AjustesActivity.this).edit().putString(Extras.CITY, c).commit();
                    Weather.forget(AjustesActivity.this);
                    cityBtn.setText(cityLabel());
                    if (c.length() > 0) checkCity(c);
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Comprueba en segundo plano que la ciudad existe y avisa de cuál se ha encontrado. */
    private void checkCity(final String city) {
        new Thread(new Runnable() {
            public void run() {
                final String found = Weather.locate(AjustesActivity.this, city);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(AjustesActivity.this, found != null ? "Tiempo de: " + found
                                : "No encuentro esa ciudad (o no hay conexión). Prueba con otro nombre.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    // ---- lugar del salvapantallas
    private String placeLabel() {
        String p = Extras.str(this, Extras.PLACE, "");
        return "Solo un lugar: " + (p.length() == 0 ? "no" : p);
    }

    private void pickPlace() {
        Toast.makeText(this, "Buscando lugares…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                HashMap<String, Integer> counts;
                try { counts = GeoIndex.get(AjustesActivity.this).placeCounts(); } catch (Throwable e) { counts = new HashMap<String, Integer>(); }
                final HashMap<String, Integer> regions = new HashMap<String, Integer>();
                final List<String[]> towns = new ArrayList<String[]>();
                for (Map.Entry<String, Integer> e : counts.entrySet()) {
                    if ("Sin nombre".equals(e.getKey())) continue;
                    String r = FxLogic.regionOf(e.getKey());
                    Integer o = regions.get(r);
                    regions.put(r, (o == null ? 0 : o) + e.getValue());
                    if (e.getKey().contains(", ")) towns.add(new String[]{e.getKey(), String.valueOf(e.getValue())});
                }
                runOnUiThread(new Runnable() {
                    public void run() { showPlaceList(regions, towns); }
                });
            }
        }).start();
    }

    private void showPlaceList(Map<String, Integer> regions, List<String[]> towns) {
        if (isFinishing()) return;
        final List<String[]> rows = new ArrayList<String[]>();   // {texto, lugar, «1» si es región}
        rows.add(new String[]{"Todos los lugares (como siempre)", "", "0"});
        List<Map.Entry<String, Integer>> rl = new ArrayList<Map.Entry<String, Integer>>(regions.entrySet());
        Collections.sort(rl, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); }
        });
        for (Map.Entry<String, Integer> e : rl) rows.add(new String[]{e.getKey() + "  ·  " + e.getValue() + " fotos", e.getKey(), "1"});
        Collections.sort(towns, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) { return Integer.parseInt(b[1]) - Integer.parseInt(a[1]); }
        });
        for (String[] t : towns) if (Integer.parseInt(t[1]) >= 10) rows.add(new String[]{t[0] + "  ·  " + t[1] + " fotos", t[0], "0"});
        if (rows.size() == 1) {
            Toast.makeText(this, "Aún no hay lugares: la app los va averiguando en segundo plano.", Toast.LENGTH_LONG).show();
        }
        String[] labels = new String[rows.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = rows.get(i)[0];
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Salvapantallas de un solo lugar")
            .setItems(labels, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String[] r = rows.get(w);
                    Prefs.sp(AjustesActivity.this).edit().putString(Extras.PLACE, r[1]).putBoolean(Extras.PLACE_REGION, "1".equals(r[2])).commit();
                    placeBtn.setText(placeLabel());
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private String profilesLabel() {
        return "Perfiles: " + (Prefs.profilesOn(this) ? (Prefs.profiles(this).size() - 1 == 0 ? "sí" : Prefs.profiles(this).size() + "") : "no");
    }

    /** Lista de perfiles: elegir uno para usarlo, renombrarlo o borrarlo; añadir uno nuevo o desactivar los perfiles. */
    private void manageProfiles() {
        final List<String[]> l = Prefs.profiles(this);
        final int cur = Prefs.profile(this);
        final List<String> labs = new ArrayList<String>();
        for (String[] p : l) labs.add(p[1] + (p[0].equals(String.valueOf(cur)) ? "   (en uso)" : ""));
        labs.add("＋  Añadir perfil…");
        labs.add("Desactivar los perfiles");
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Perfiles")
            .setItems(labs.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (which < l.size()) profileMenu(Integer.parseInt(l.get(which)[0]), l.get(which)[1]);
                    else if (which == l.size()) askProfileName("Nuevo perfil", "", -1);
                    else {
                        Prefs.setProfile(AjustesActivity.this, 0);
                        Prefs.meta(AjustesActivity.this).edit().putBoolean("fx_profiles", false).commit();
                        Toast.makeText(AjustesActivity.this, "Perfiles desactivados: vuelves a «Familia». Los demás perfiles se guardan por si los activas otra vez.", Toast.LENGTH_LONG).show();
                        recreate();
                    }
                }
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void profileMenu(final int id, final String name) {
        final boolean fam = id == 0;
        final String[] it = fam ? new String[]{"Usar este perfil", "Cambiar el icono"} : new String[]{"Usar este perfil", "Cambiar el icono", "Cambiar el nombre", "Borrar"};
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(name)
            .setItems(it, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        Prefs.setProfile(AjustesActivity.this, id);
                        Toast.makeText(AjustesActivity.this, "Perfil «" + name + "» en uso.", Toast.LENGTH_SHORT).show();
                        recreate();
                    } else if (which == 1) iconPicker(id, name);
                    else if (which == 2) askProfileName("Nombre del perfil", name, id);
                    else confirmDeleteProfile(id, name);
                }
            })
            .setNegativeButton("Atrás", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { manageProfiles(); }
            })
            .show();
    }

    private int pickId = -1;

    /** Muestra de iconos para el perfil, más «Foto» (de un archivo) y, si ya tiene una, «Quitar foto». */
    private void iconPicker(final int id, final String name) {
        final boolean hasImg = Prefs.profileImageFile(this, id) != null;
        final int n = Ui.PROFILE_ICONS.length;
        final int extra = hasImg ? 2 : 1;
        final android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing(dp(6));
        grid.setHorizontalSpacing(dp(6));
        grid.setPadding(dp(14), dp(10), dp(14), dp(6));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x663D8BFD));
        grid.setAdapter(new android.widget.BaseAdapter() {
            public int getCount() { return n + extra; }
            public Object getItem(int i) { return null; }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, android.view.ViewGroup p) {
                ImageView v = new ImageView(AjustesActivity.this);
                v.setPadding(dp(10), dp(10), dp(10), dp(10));
                int res = i < n ? Ui.PROFILE_ICONS[i] : (i == n ? R.drawable.ic_file : R.drawable.ic_delete);
                v.setImageResource(res);
                GradientDrawable g = new GradientDrawable();
                g.setColor(0xFF22364F);
                g.setCornerRadius(dp(8));
                v.setBackground(g);
                v.setLayoutParams(new android.widget.AbsListView.LayoutParams(dp(52), dp(52)));
                return v;
            }
        });
        TextView tip = text("Elige un icono, o «archivo» para usar una foto" + (hasImg ? " (la papelera quita la foto actual)." : "."), 13, 0xFF9FB3C8);
        tip.setPadding(dp(18), dp(8), dp(18), 0);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tip);
        box.addView(grid);
        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Icono de «" + name + "»").setView(box)
            .setNegativeButton("Atrás", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { manageProfiles(); }
            }).create();
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> a, View v, int i, long l) {
                dlg.dismiss();
                if (i < n) { Prefs.setProfileIcon(AjustesActivity.this, id, i); manageProfiles(); }
                else if (i == n) pickPhoto(id);
                else { Prefs.clearProfileLook(AjustesActivity.this, id); manageProfiles(); }
            }
        });
        dlg.show();
    }

    /** Elige una foto de un archivo (si el aparato tiene selector de archivos). */
    private void pickPhoto(int id) {
        pickId = id;
        try {
            android.content.Intent in = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*");
            startActivityForResult(in, 77);
        } catch (Throwable t) {
            Toast.makeText(this, "Este aparato no tiene selector de archivos. Puedes poner la foto desde la web del móvil (Perfil en uso).", Toast.LENGTH_LONG).show();
            manageProfiles();
        }
    }

    @Override protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 77) return;
        if (res == RESULT_OK && data != null && data.getData() != null && pickId >= 0) {
            boolean ok = false;
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                java.io.InputStream a = getContentResolver().openInputStream(data.getData());
                android.graphics.BitmapFactory.decodeStream(a, null, o);
                a.close();
                int ss = 1;
                while (o.outWidth / ss > 800 && o.outHeight / ss > 800) ss *= 2;
                o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = ss;
                java.io.InputStream b = getContentResolver().openInputStream(data.getData());
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(b, null, o);
                b.close();
                if (bm != null) ok = Prefs.setProfileImage(this, pickId, bm);
            } catch (Throwable ignored) { }
            Toast.makeText(this, ok ? "Foto puesta como icono." : "No se ha podido leer esa imagen.", Toast.LENGTH_SHORT).show();
        }
        manageProfiles();
    }

    private void confirmDeleteProfile(final int id, final String name) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("¿Borrar «" + name + "»?")
            .setMessage("Se borran sus álbumes y todos sus ajustes. No se puede deshacer.")
            .setPositiveButton("Borrar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    boolean wasCur = Prefs.profile(AjustesActivity.this) == id;
                    Prefs.deleteProfile(AjustesActivity.this, id);
                    profilesBtn.setText(profilesLabel());
                    if (wasCur) recreate(); else manageProfiles();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    /** Pide el nombre de un perfil nuevo (id -1) o de uno existente. */
    private void askProfileName(String title, String cur, final int id) {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setTextColor(Color.WHITE);
        in.setText(cur);
        in.setSelection(in.getText().length());
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(title)
            .setView(in)
            .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String n = in.getText().toString().trim();
                    if (n.length() > 20) n = n.substring(0, 20);
                    if (n.length() == 0) { manageProfiles(); return; }
                    if (id < 0) {
                        int nid = Prefs.addProfile(AjustesActivity.this, n);
                        Toast.makeText(AjustesActivity.this, "Perfil «" + n + "» creado, vacío. Pulsa «Usar este perfil» para entrar y añadir sus álbumes.", Toast.LENGTH_LONG).show();
                    } else Prefs.renameProfile(AjustesActivity.this, id, n);
                    profilesBtn.setText(profilesLabel());
                    manageProfiles();
                }
            })
            .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { manageProfiles(); }
            })
            .show();
    }
}
