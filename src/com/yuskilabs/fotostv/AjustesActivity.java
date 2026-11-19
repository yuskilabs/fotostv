package com.yuskilabs.fotostv;

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
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private Button cityBtn, placeBtn;
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
        ic.setImageResource(R.drawable.ic_settings);
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

        // --- música
        LinearLayout cm = card("Música del pase", R.drawable.ic_music);
        musicBtn = btn(musicLabel(), R.drawable.ic_tune, new View.OnClickListener() {
            public void onClick(View v) {
                String m = Prefs.musicMode(AjustesActivity.this);
                String nx = "off".equals(m) ? "radio" : "radio".equals(m) ? "folder" : "off";
                Prefs.sp(AjustesActivity.this).edit().putString("musicMode", nx).commit();
                refreshMusic();
            }
        });
        volBtn = btn(volLabel(), R.drawable.ic_volume, new View.OnClickListener() {
            public void onClick(View v) {
                int[] o = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
                int cur = Prefs.musicVol(AjustesActivity.this), nx = o[0];
                for (int i = 0; i < o.length; i++) if (o[i] > cur) { nx = o[i]; break; }
                Prefs.sp(AjustesActivity.this).edit().putInt("musicVol", nx).commit();
                refreshMusic();
            }
        });
        radioBtn = btn(radioLabel(), R.drawable.ic_play, new View.OnClickListener() {
            public void onClick(View v) { pickRadio(); }
        });
        folderBtn = btn(folderLabel(), R.drawable.ic_folder, new View.OnClickListener() {
            public void onClick(View v) { pickFolder(); }
        });
        dreamMusicBtn = btn(dreamMusicLabel(), R.drawable.ic_preview, new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.sp(AjustesActivity.this).edit().putBoolean("musicDream", !Prefs.musicDream(AjustesActivity.this)).commit();
                refreshMusic();
            }
        });
        Button albumMusic = fx("Música por álbum", Extras.ALBUM_MUSIC, R.drawable.ic_notes,
                "Cada álbum puede tener su emisora (pulsación larga en el álbum, «Música del álbum»); suena en el pase de ese álbum.");
        Button duck = twoFx("Con vídeo", Extras.SOFT_DUCK, "callar música", "bajar música",
                R.drawable.ic_down, "Cuando suena un vídeo con sonido, la música se calla (como siempre) o solo baja de volumen.");
        cm.addView(row(musicBtn, volBtn));
        cm.addView(row(radioBtn, folderBtn));
        cm.addView(row(dreamMusicBtn, albumMusic));
        Button musicFade = cycInt("Al empezar", Extras.MUSIC_FADE, new int[]{0, 5, 15, 30}, new String[]{"de golpe", "sube en 5 s", "sube en 15 s", "sube en 30 s"},
                R.drawable.ic_up, "Cuando empieza la música, el volumen sube poco a poco en vez de sonar de golpe.");
        Button musicStop = cycFx("Callar desde", Extras.MUSIC_STOP, new String[]{"off", "21", "22", "23", "0"},
                new String[]{"no", "21 h", "22 h", "23 h", "0 h"}, R.drawable.ic_timer,
                "La música se calla desde esa hora hasta las 8 de la mañana, aunque el modo noche esté apagado. La imagen sigue.");
        Button musicOrder = cycFx("Canciones", Extras.MUSIC_ORDER, new String[]{"shuffle", "order"}, new String[]{"aleatorias", "en orden"},
                R.drawable.ic_shuffle, "Solo con música de una carpeta: las canciones suenan al azar o por orden de nombre.");
        cm.addView(row(duck, musicFade));
        cm.addView(row(musicStop, musicOrder));
        

        // --- ubicaciones
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
        Button geoBtn = btn("Revisar ubicaciones", R.drawable.ic_place, new View.OnClickListener() {
            public void onClick(View v) {
                new Thread(new Runnable() { public void run() { GeoIndex.recheckPending(AjustesActivity.this); } }).start();
                Toast.makeText(AjustesActivity.this, "Reviso en segundo plano lo que falta (las fotos sin ubicación y los sitios sin nombre).", Toast.LENGTH_LONG).show();
                h.removeCallbacks(geoTick);
                h.postDelayed(geoTick, 1500);
            }
        });
        Button rebuild = btn("Rehacer nombres", R.drawable.ic_format, new View.OnClickListener() {
            public void onClick(View v) { rebuildDialog(); }
        });
        

        // --- salvapantallas
        Button dream = btn("Elegir salvapantallas", R.drawable.ic_cast, new View.OnClickListener() {
            public void onClick(View v) { openDreamSettings(); }
        });
        Button proj = btn("Fondo para Projectivy", R.drawable.ic_wallpaper, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(AjustesActivity.this, ProjectivyActivity.class)); }
        });
        

        // --- vídeo y caché
        LinearLayout cv = card("Vídeo y caché", R.drawable.ic_video);
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
            tog("Con sonido", "sound", R.drawable.ic_volume, new Bool() { public boolean get() { return Prefs.sound(AjustesActivity.this); } })));
        cv.addView(row(vqBtn, cache));
        cv.addView(row(
            cyc("Miniaturas", "thumbMB", true, new String[]{"100", "150", "250", "500", "1000", "2000"},
                new String[]{"100 MB", "150 MB", "250 MB", "500 MB", "1 GB", "2 GB"}, R.drawable.ic_grid,
                new Cur() { public String get() { return String.valueOf(Prefs.thumbMB(AjustesActivity.this)); } }),
            cyc("Vídeos", "videoMB", true, new String[]{"300", "500", "1000", "1500", "3000"},
                new String[]{"300 MB", "500 MB", "1 GB", "1,5 GB", "3 GB"}, R.drawable.ic_file,
                new Cur() { public String get() { return String.valueOf(Prefs.videoMB(AjustesActivity.this)); } })));
        Button videoMax = cycInt("Duración máxima", Extras.VIDEO_MAX, new int[]{0, 15, 30, 60, 120}, new String[]{"entero", "15 s", "30 s", "1 min", "2 min"},
                R.drawable.ic_timer, "Cada vídeo se ve como mucho ese rato en el pase y el salvapantallas, y luego pasa a lo siguiente. Con «entero» se ve hasta el final.");
        Button cacheUsed = btn("Ver caché usada", R.drawable.ic_info, new View.OnClickListener() {
            public void onClick(View v) { showCacheUsed(); }
        });
        describe(cacheUsed, "Cuánto ocupan ahora las miniaturas y los vídeos guardados, frente al máximo elegido.");
        cv.addView(row(videoMax, cacheUsed));
        

        // --- pase de fotos
        LinearLayout cp = card("Pase de fotos", R.drawable.ic_photo);
        placeBtn = btn(placeLabel(), R.drawable.ic_map, new View.OnClickListener() {
            public void onClick(View v) { pickPlace(); }
        });
        describe(placeBtn, "El pase y el salvapantallas enseñan solo las fotos de un lugar (de las que ya tienen ubicación).");
        Button fav = fx("Más favoritas", Extras.FAV_BOOST, R.drawable.ic_star,
                "Con el orden «inteligente», las favoritas salen tres veces más a menudo.");
        Button mosaics = cyc("Mosaicos", Extras.MOSAICS, false, new String[]{"off", "few", "normal", "many"},
                new String[]{"no", "pocos", "normal", "muchos"}, R.drawable.ic_grid,
                new Cur() { public String get() { return Extras.mosaics(AjustesActivity.this); } });
        describe(mosaics, "Fotos hechas casi a la vez (menos de 15 minutos) salen juntas, sin bordes: dos o tres verticales, o cuatro horizontales en cuadrícula, con un solo pie de foto y sin hora. «Pocos»: algo menos que lo normal; «muchos»: el doble. Nunca con vídeos.");
        Button transition = cycFx("Transición", Extras.TRANSITION, new String[]{"fade", "cut", "wipe"},
                new String[]{"fundido", "corte", "cortinilla"}, R.drawable.ic_swap,
                "Cómo pasa una foto a la siguiente: fundido suave, corte directo o cortinilla (la nueva aparece desde la derecha).");
        Button todayWeek = fx("Semana entera", Extras.TODAY_WEEK, R.drawable.ic_date_range,
                "Con «Recuerdos de hoy», incluye también las fotos de los 3 días antes y después de hoy en años anteriores, no solo del día exacto.");
        Button credit = fx("Autor", Extras.CREDIT, R.drawable.ic_person,
                "Marca de agua muy sutil abajo a la derecha: «© Lola» (iPhone 12 o 13), «© Alex» (iPhone 14 Pro), «© Pepe» (cámara que no es móvil: Leica, Nikon…) o «© Lara» (archivo que empieza por PIC). En las demás, nada. Las reglas se pueden cambiar desde la web de configuración (campo «Autor de las fotos»).");
        Button order = cyc("Orden", "order", false, new String[]{"random", "norepeat", "smart", "asc", "desc"},
                new String[]{"aleatorio", "sin repetir", "inteligente", "por fecha ↑", "por fecha ↓"}, R.drawable.ic_shuffle,
                new Cur() { public String get() { return Prefs.order(AjustesActivity.this); } });
        Button every = cyc("Cambiar cada", "interval", true, new String[]{"5", "10", "30", "60", "180", "300", "600"},
                new String[]{"5 s", "10 s", "30 s", "1 min", "3 min", "5 min", "10 min"}, R.drawable.ic_timer,
                new Cur() { public String get() { return String.valueOf(Prefs.interval(AjustesActivity.this)); } });
        final Button fitBtn = cyc("Encuadre", "fit", false, new String[]{"auto", "crop", "fit"},
                new String[]{"auto", "llenar", "completo"}, R.drawable.ic_single,
                new Cur() { public String get() { return Prefs.fit(AjustesActivity.this); } });
        Button onlyFav = fx("Solo favoritas", Extras.ONLY_FAV, R.drawable.ic_heart,
                "El pase y el salvapantallas enseñan solo las fotos marcadas como favoritas (si hay alguna).");
        paseFilterBtn = btn(paseFilterLabel(), R.drawable.ic_tune, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(AjustesActivity.this, FilterActivity.class).putExtra("pase", true)); }
        });
        describe(paseFilterBtn, "Elige año, mes, día, región, localidad, álbum, cámara o autor: el pase y el salvapantallas usarán solo las fotos que cumplan todo. Se suma a «Un solo lugar» y «Solo favoritas».");
        // pase: qué fotos salen y cuándo
        cp.addView(row(order, every));
        cp.addView(row(todayBtn(), todayWeek));
        cp.addView(row(fav, onlyFav));
        cp.addView(row(placeBtn, paseFilterBtn));

        // --- movimiento y relleno
        LinearLayout cf = card("Aspecto del pase", R.drawable.ic_fullscreen);
        Button speed = cycFx("Velocidad", Extras.KB_SPEED, new String[]{"slow", "normal"}, new String[]{"lenta", "normal"},
                R.drawable.ic_timer, "Velocidad del movimiento. En las dos, el movimiento dura más que la foto: nunca se para a la vista.");
        Button edge = cycFx("Bordes", Extras.KB_EDGE, new String[]{"hide", "show"}, new String[]{"tapados", "a la vista"},
                R.drawable.ic_terrain, "Tapados: la foto va algo ampliada y no se ve el relleno. A la vista: la foto empieza entera, sin zoom, y al moverse se ve el relleno (negro, color o difuminado).");
        Button bg = cycFx("Relleno", Extras.BACKGROUND, new String[]{"black", "color", "blur"}, new String[]{"negro", "color", "difuminado"},
                R.drawable.ic_wallpaper, "Lo que se ve donde la foto no llena la pantalla (verticales, fotos con otra forma, y al moverse): negro, el color de la foto o la foto muy difuminada.");
        cf.addView(row(transition, mosaics));
        cf.addView(row(fitBtn, motionBtn()));
        cf.addView(row(speed, edge));
        Button textSize = cycFx("Tamaño del texto", Extras.TEXT_SIZE, new String[]{"normal", "small", "big"}, new String[]{"normal", "pequeño", "grande"},
                R.drawable.ic_format, "Tamaño de la fecha, el lugar, el álbum, los pies de foto y la hora sobre la foto.");
        cf.addView(row(bg, textSize));

        // --- información en pantalla
        LinearLayout ci = card("Información en pantalla", R.drawable.ic_info);
        Button dateFmt = dateFmtBtn();
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
        cityBtn = btn(cityLabel(), R.drawable.ic_home, new View.OnClickListener() {
            public void onClick(View v) { askCity(); }
        });
        describe(cityBtn, "Ciudad del tiempo. Escribe su nombre («Málaga», «Estepona»…).");
        Button captions = fx("Pies de foto", Extras.CAPTIONS, R.drawable.ic_notes,
                "Permite escribir un texto a cada foto (pulsación larga en el mosaico) y lo enseña en el pase y en la ficha.");
        ci.addView(row(
            tog("Álbum", "showAlbum", R.drawable.ic_folder, new Bool() { public boolean get() { return Prefs.showAlbum(AjustesActivity.this); } }),
            tog("Lugar", "showPlace", R.drawable.ic_map, new Bool() { public boolean get() { return Prefs.showPlace(AjustesActivity.this); } })));
        ci.addView(row(dateFmt,
            tog("Hora", "clock", R.drawable.ic_schedule, new Bool() { public boolean get() { return Prefs.clock(AjustesActivity.this); } })));
        ci.addView(row(weather, cityBtn));
        ci.addView(row(captions, credit));
        Button infoPos = cycFx("Posición", Extras.INFO_POS, new String[]{"bottom", "top"}, new String[]{"abajo", "arriba"}, R.drawable.ic_move,
                "Dónde salen la información de la foto y la hora: abajo (como siempre) o arriba.");
        Button infoShow = cycInt("Se ve", Extras.INFO_SHOW, new int[]{0, 5, 10, 20}, new String[]{"siempre", "5 s", "10 s", "20 s"}, R.drawable.ic_visibility,
                "Cuánto rato se ve el texto de cada foto (álbum, fecha, lugar, pie). Pasado ese rato se esconde; la hora se queda.");
        ci.addView(row(infoPos, infoShow));

        // --- salvapantallas, y dentro la noche
        LinearLayout cn = card("Salvapantallas", R.drawable.ic_tv);
        Button wake = twoFx("Al despertar", "resume", "de cero", "seguir", R.drawable.ic_undo,
                "Cuando el salvapantallas vuelve a empezar: seguir por la foto donde se quedó la última vez, o empezar de cero.");
        Button timer = cycInt("Temporizador", Extras.TIMER, new int[]{0, 15, 30, 60, 90}, new String[]{"no", "15 min", "30 min", "1 h", "1 h 30"},
                R.drawable.ic_timer, "Cualquier pase (también el salvapantallas) se cierra solo pasado ese tiempo, aunque se use el mando. Después el inicio automático no lo vuelve a abrir hasta que abras la app. «Cerrar pase» (pantalla principal) es distinto: cierra el pase abierto solo si no tocas el mando en ese rato.");
        cn.addView(row(dream, proj));
        cn.addView(row(wake, timer));
        TextView nightHead = text("Noche", 16, 0xFF7FB2FF);
        nightHead.setTypeface(Ui.BLACK);
        nightHead.setPadding(dp(4), dp(10), 0, dp(6));
        cn.addView(nightHead);
        Button night = cycFx("Modo noche", Extras.NIGHT, new String[]{"off", "22-8", "23-7", "0-7"},
                new String[]{"no", "22 a 8 h", "23 a 7 h", "0 a 7 h"}, R.drawable.ic_schedule,
                "Durante esas horas el salvapantallas se atenúa (o se apaga) y la música se calla (o baja, según «Música de noche»).");
        Button nightMode = cycFx("De noche", Extras.NIGHT_MODE, new String[]{"dim", "black"}, new String[]{"tenue", "pantalla negra"},
                R.drawable.ic_visibility_off, "Tenue: fotos muy oscuras, sin textos. Pantalla negra: el salvapantallas no enseña nada a esas horas.");
        Button nightMusic = cycFx("Música", Extras.NIGHT_MUSIC, new String[]{"off", "low", "keep"}, new String[]{"se calla", "baja", "sigue"},
                R.drawable.ic_volume, "Qué hace la música durante el modo noche: callarse, bajar mucho el volumen o seguir igual.");
        Button nightDim = cycFx("Oscuridad", Extras.NIGHT_DIM, new String[]{"normal", "soft", "strong"}, new String[]{"normal", "suave", "fuerte"},
                R.drawable.ic_moon, "Cuánto se oscurecen las fotos en el modo noche tenue.");
        cn.addView(row(night, nightMode));
        cn.addView(row(nightMusic, nightDim));

        // --- explorador
        LinearLayout ce = card("Explorador", R.drawable.ic_albums);
        Button search = fx("Buscar", Extras.SEARCH, R.drawable.ic_search,
                "Barra de búsqueda en la pantalla principal (encima de los botones; se llega subiendo con el mando): por lugar, álbum, autor o fecha («Zahara», «agosto 2025», «Lola 2024»). El autor sale de la cámara de cada foto: cuantas más cámaras leas en Estadísticas, más fotos encuentra.");
        Button virt = fx("Álbumes virtuales", Extras.VIRTUAL, R.drawable.ic_folder,
                "Guarda una búsqueda como álbum («Zahara 2025») y sale en la lista de álbumes; se actualiza solo.");
        Button jump = fx("Saltar por fecha", Extras.DATE_JUMP, R.drawable.ic_event,
                "Botón «Fecha» en el mosaico de un álbum: un calendario mensual para ir a un día y ver solo sus fotos.");
        Button trips = fx("Viajes", Extras.TRIPS, R.drawable.ic_flight,
                "Botón «Viajes» en la pantalla principal: agrupa solas las fotos seguidas en el tiempo, con su lugar y fechas.");
        Button covers = fx("Varias portadas", Extras.MULTI_COVER, R.drawable.ic_wallpaper,
                "Puedes fijar varias portadas por álbum; cada vez que entras sale una de ellas.");
        Button news = fx("Marcar nuevas", Extras.NEW_BADGE, R.drawable.ic_add,
                "Las fotos añadidas al álbum desde la última vez que lo abriste llevan la marca «NUEVA».");
        Button dupes = fx("Fotos repetidas", Extras.DUPES, R.drawable.ic_copy,
                "Pulsación larga en un álbum, «Buscar repetidas»: marca las fotos casi iguales hechas seguidas para ocultarlas de una vez.");
        Button stats = fx("Estadísticas", Extras.STATS, R.drawable.ic_stats,
                "Botón «Estadísticas» en la pantalla principal: fotos por año, lugar, álbum y cámara (con filtro por cámara).");
        final int nh = Prefs.hidden(this).size();
        Button unhide = btn(nh == 0 ? "No hay fotos ocultas" : "Mostrar las " + nh + " ocultas", R.drawable.ic_visibility, new View.OnClickListener() {
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
        describe(unhide, "Las fotos que has ocultado vuelven a verse en sus álbumes.");
        Button hideAlbum = twoFx("Álbum Ocultas", Extras.HIDE_OCULTAS, "visible", "escondido", R.drawable.ic_visibility_off,
                "El álbum «Ocultas» sale en la lista de álbumes (si hay fotos ocultas) o se esconde de ella. Las fotos ocultas siguen ocultas.");
        ce.addView(row(search, virt));
        ce.addView(row(jump, trips));
        ce.addView(row(covers, news));
        ce.addView(row(dupes, stats));
        ce.addView(row(hideAlbum, unhide));

        // --- perfiles (panel propio: lista de perfiles y ficha de cada uno)
        profilesCard = card("Perfiles", R.drawable.ic_family);
        if (keepTopic < 0) profView = -1;
        refreshProfiles();

        // --- copiar de otra tele y recarga (ahora en Ayuda y mantenimiento)
        Button reload = cycInt("Recargar álbumes", Extras.RELOAD, new int[]{0, 6, 12, 24}, new String[]{"no", "cada 6 h", "cada 12 h", "cada 24 h"},
                R.drawable.ic_refresh, "Mientras la app esté abierta o en el salvapantallas, vuelve a leer los álbumes de Google cada cierto tiempo.");
        Button copyTv = btn("Copiar de otra tele", R.drawable.ic_cast, new View.OnClickListener() {
            public void onClick(View v) { copyFromTvDialog(); }
        });
        describe(copyTv, "Trae a esta tele los álbumes y todos los ajustes de otra tele con FotosTV, por la wifi y sin archivos. Hace falta su dirección y su PIN (salen en su pantalla principal).");

        // --- ayuda y estado
        LinearLayout cy = card("Ayuda y mantenimiento", R.drawable.ic_notes);
        Button diag = btn("Diagnóstico", R.drawable.ic_terrain, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(AjustesActivity.this, DiagActivity.class)); }
        });
        describe(diag, "Últimos errores del pase, la búsqueda, los vídeos y el autor, con su hora. Sirve para ver qué falla si algo se queda parado.");
        Button novBtn = btn("Novedades", R.drawable.ic_new_releases, new View.OnClickListener() {
            public void onClick(View v) { Novedades.showLatest(AjustesActivity.this); }
        });
        describe(novBtn, "Lo que ha cambiado en las últimas versiones.");
        Button upd = btn("Buscar actualización", R.drawable.ic_refresh, new View.OnClickListener() {
            public void onClick(View v) { checkUpdate(); }
        });
        describe(upd, "Mira si hay una versión nueva de FotosTV. Si la hay, aquí mismo se ve qué trae y se puede instalar.");
        updInfo = text("Versión " + Updater.installed(this) + ". Comprobando si hay otra más nueva…", 13, 0xFFBBD0E6);
        updInfo.setPadding(dp(4), 0, dp(4), dp(4));
        updNotes = text("", 12, 0xFF9FB3C8);
        updNotes.setPadding(dp(4), 0, dp(4), dp(8));
        updNotes.setVisibility(View.GONE);
        updInstall = btn("Actualizar", R.drawable.ic_download, new View.OnClickListener() {
            public void onClick(View v) { if (updAvail != null) downloadUpdate(updAvail); }
        });
        describe(updInstall, "Descarga la versión nueva y abre la instalación de Android, donde hay que confirmar.");
        updInstallRow = row(updInstall, null);
        updInstallRow.setVisibility(View.GONE);
        cy.addView(row(upd, novBtn));
        cy.addView(updInfo);
        cy.addView(updNotes);
        cy.addView(updInstallRow);
        camBtn = btn("Leer cámaras", R.drawable.ic_camera, new View.OnClickListener() {
            public void onClick(View v) { camAction(); }
        });
        describe(camBtn, "Lee la cámara de cada foto, para filtrar por cámara y saber quién la hizo. Se lee el principio de cada foto, así que con muchas fotos tarda y gasta datos. Se puede parar.");
        camInfo = text("", 12, 0xFF9FB3C8);
        camInfo.setPadding(dp(8), dp(4), dp(8), dp(8));
        cy.addView(row(diag, geoBtn));
        cy.addView(row(rebuild, camBtn));
        cy.addView(row(copyTv, reload));
        cy.addView(geoView);
        cy.addView(camInfo);
        checkUpdate();

        // temas a la izquierda, un solo panel a la derecha
        final LinearLayout[] cards = {cp, cf, ci, cn, cm, cv, ce, profilesCard, cy};
        String[] names = {"Pase de fotos", "Aspecto del pase", "Información", "Salvapantallas", "Música",
            "Vídeo y caché", "Explorador", "Perfiles", "Ayuda y mantenimiento"};
        int[] icons = {R.drawable.ic_photo, R.drawable.ic_fullscreen, R.drawable.ic_info, R.drawable.ic_tv,
            R.drawable.ic_music, R.drawable.ic_video, R.drawable.ic_albums,
            R.drawable.ic_family, R.drawable.ic_notes};
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        topics = new TopicRow[cards.length];
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        topicList = list;
        for (int i = 0; i < cards.length; i++) {
            final int k = i;
            final LinearLayout cd = cards[i];
            TopicRow tr = new TopicRow(this, names[i], icons[i]);
            tr.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { cd.requestFocus(); }
            });
            tr.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    if (has && !keepPanel) { showTopic(k); if (hint != null) hint.setText(HINT0); }
                    ((TopicRow) v).setFocusedLook(has);
                }
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, dp(42));
            tl.setMargins(0, 0, 0, dp(2));
            list.addView(tr, tl);
            topics[i] = tr;
        }
        cardList = cards;
        ScrollView ls = new ScrollView(this);
        ls.setVerticalScrollBarEnabled(false);
        ls.addView(list);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(250), -1);
        lp.setMargins(0, 0, dp(14), 0);
        cols.addView(ls, lp);
        ScrollView sv = new ScrollView(this);
        panelScroll = sv;
        sv.setVerticalScrollBarEnabled(false);
        panel.setPadding(0, 0, 0, dp(30));      // la última fila no queda pegada al borde de abajo
        sv.addView(panel, new ViewGroup.LayoutParams(-1, -2));
        cols.addView(sv, new LinearLayout.LayoutParams(0, -1, 1f));
        showTopic(0);
        root.addView(cols, new LinearLayout.LayoutParams(-1, 0, 1f));
        hint = text(HINT0, 13, 0xFFBBD0E6);
        hint.setPadding(0, dp(6), 0, 0);
        hint.setMinLines(2);
        root.addView(hint);
        setContentView(root);
        int kt = keepTopic;
        keepTopic = -1;
        topics[kt >= 0 && kt < topics.length ? kt : 0].requestFocus();
    }

    private ScrollView panelScroll;
    private LinearLayout panel;
    private TopicRow[] topics;
    private LinearLayout topicList;
    private LinearLayout[] cardList;
    private int topicSel = -1;

    private void showTopic(int i) {
        if (i == topicSel) return;
        topicSel = i;
        panel.removeAllViews();
        panel.addView(cardList[i], new LinearLayout.LayoutParams(-1, -2));
        for (int k = 0; k < topics.length; k++) topics[k].setSelectedLook(k == i);
    }

    /** Desplaza el panel lo justo para que la vista con foco se vea entera, con un margen. */
    private void showInScroll(View v) {
        if (panelScroll == null || v == null || !inPanel(v)) return;
        android.graphics.Rect r = new android.graphics.Rect();
        v.getDrawingRect(r);
        panel.offsetDescendantRectToMyCoords(v, r);
        int top = r.top - dp(12), bottom = r.bottom + dp(12), y = panelScroll.getScrollY(), hh = panelScroll.getHeight();
        if (bottom > y + hh) panelScroll.smoothScrollTo(0, bottom - hh);
        else if (top < y) panelScroll.smoothScrollTo(0, Math.max(0, top));
    }

    private boolean inPanel(View v) {
        while (v != null) {
            if (v == panel) return true;
            android.view.ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        return false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && topics != null) {
            View f = getCurrentFocus();
            int kc = e.getKeyCode();
            int dir = kc == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : kc == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT
                    : kc == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : kc == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN : 0;
            if (f != null && dir != 0) {
                if (inPanel(f)) {
                    View n = f.focusSearch(dir);
                    if (n == null || !inPanel(n)) {          // borde del panel: izquierda vuelve a los temas; el resto, se queda
                        if (dir == View.FOCUS_LEFT) topics[Math.max(0, topicSel)].requestFocus();
                        else if (dir == View.FOCUS_DOWN && panelScroll != null) panelScroll.smoothScrollBy(0, dp(140));   // por si queda algo cortado debajo
                        return true;
                    }
                    if ((dir == View.FOCUS_DOWN || dir == View.FOCUS_UP) && panelScroll != null) {
                        final View target = n;
                        h.post(new Runnable() { public void run() { showInScroll(target); } });
                    }
                } else if (f.getParent() == topicList) {
                    if (dir == View.FOCUS_RIGHT) { cardList[Math.max(0, topicSel)].requestFocus(); return true; }
                    if (dir == View.FOCUS_LEFT) return true;
                    View n = f.focusSearch(dir);
                    if (n == null || n.getParent() != topicList) return true;   // primer o último tema: se queda
                }
            }
        }
        return super.dispatchKeyEvent(e);
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
        if (paseFilterBtn != null) paseFilterBtn.setText(paseFilterLabel());
        h.removeCallbacks(geoTick);
        h.post(geoTick);
        h.removeCallbacks(camTick);
        h.post(camTick);
        refreshCamInfo();
    }

    @Override protected void onStop() {
        h.removeCallbacks(geoTick);
        h.removeCallbacks(camTick);
        super.onStop();
    }

    // ---- acciones
    private String vqLabel() {
        String q = Prefs.vq(this);
        return "Vídeos: " + ("hd".equals(q) ? "calidad alta" : "sd".equals(q) ? "calidad media" : "calidad auto");
    }

    private Button paseFilterBtn;
    private boolean keepPanel;

    private String paseFilterLabel() {
        String q = Extras.str(this, Extras.PASE_FILTER, "");
        if (q.length() == 0 || q.charAt(0) != FilterData.PREFIX) return "Filtrar: no";
        int n = 0;
        for (java.util.Set<String> s : FilterData.decode(q)) if (!s.isEmpty()) n++;
        return "Filtrar: " + (n == 1 ? "1 dato" : n + " datos");
    }

    private static long dirBytes(java.io.File d) {
        long t = 0;
        java.io.File[] fs = d == null ? null : d.listFiles();
        if (fs != null) for (java.io.File f : fs) t += f.isDirectory() ? dirBytes(f) : f.length();
        return t;
    }

    private void showCacheUsed() {
        new Thread(new Runnable() {
            public void run() {
                final long th = dirBytes(new java.io.File(getCacheDir(), "thumbs")), vd = dirBytes(new java.io.File(getCacheDir(), "videos"));
                final String m = "Miniaturas: " + (th / (1024 * 1024)) + " MB de " + Prefs.thumbMB(AjustesActivity.this) + " MB.\nVídeos: "
                    + (vd / (1024 * 1024)) + " MB de " + Prefs.videoMB(AjustesActivity.this) + " MB.";
                h.post(new Runnable() { public void run() { if (!isFinishing()) Toast.makeText(AjustesActivity.this, m, Toast.LENGTH_LONG).show(); } });
            }
        }).start();
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

    @Override protected void onDestroy() {
        try { prevExec.shutdown(); } catch (Throwable ignored) { }   // deja terminar la suelta del reproductor y cierra su hilo
        super.onDestroy();
    }

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
        Dialogs.edgeKeys(new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Carpeta de música")
            .setItems(fs.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Prefs.sp(AjustesActivity.this).edit().putString("musicFolder", fs.get(w)).putString("musicMode", "folder").commit();
                    refreshMusic();
                }
            })
            .setNegativeButton("Cancelar", null).show());
    }


    private static final String[] DATE_LABS = {"larga", "corta", "no", "con día y hora"};

    /** 0 larga, 1 corta, 2 no se enseña, 3 larga con el día de la semana y la hora. */
    private int dateState() {
        if (!Prefs.showDate(this)) return 2;
        if (Extras.on(this, Extras.DATE_FULL)) return 3;
        return "short".equals(Extras.str(this, Extras.DATE_FMT, "long")) ? 1 : 0;
    }

    /** Fecha de cada foto: larga → corta → no → larga con día y hora. */
    private Button dateFmtBtn() {
        final Button[] self = new Button[1];
        self[0] = btn("Fecha", R.drawable.ic_event, new View.OnClickListener() {
            public void onClick(View v) {
                int nx = (dateState() + 1) % 4;
                android.content.SharedPreferences.Editor e = Prefs.sp(AjustesActivity.this).edit();
                e.putBoolean("showDate", nx != 2);
                if (nx != 2) e.putString(Extras.DATE_FMT, nx == 1 ? "short" : "long");
                e.putBoolean(Extras.DATE_FULL, nx == 3);
                e.commit();
                self[0].setText("Fecha: " + DATE_LABS[nx]);
            }
        });
        self[0].setText("Fecha: " + DATE_LABS[dateState()]);
        describe(self[0], "Fecha de cada foto en el pase. Larga: «27 de diciembre de 2023». Corta: «27 dic 2023». Con día y hora: añade el día de la semana y la hora a la que se hizo («Miércoles 27 de diciembre de 2023 - 15:03»). O no enseñarla.");
        return self[0];
    }

    private static int keepTopic = -1;

    /** Pide la dirección y el PIN de la otra tele y trae su copia de seguridad por la red. */
    private void copyFromTvDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        final android.widget.EditText ip = new android.widget.EditText(this);
        ip.setSingleLine(true);
        ip.setHint("Dirección, por ejemplo 192.168.1.20");
        ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        ip.setText("192.168.1.");
        ip.setSelection(ip.getText().length());
        final android.widget.EditText pin = new android.widget.EditText(this);
        pin.setSingleLine(true);
        pin.setHint("PIN de la otra tele");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        box.addView(ip);
        box.addView(pin);
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Copiar de otra tele")
            .setMessage("En la otra tele abre FotosTV (pantalla principal): arriba salen su dirección y su PIN. Los álbumes y ajustes de esta tele se sustituirán por los de aquella.")
            .setView(box)
            .setPositiveButton("Copiar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    final String a = ip.getText().toString(), p = pin.getText().toString();
                    Toast.makeText(AjustesActivity.this, "Pidiendo la copia a la otra tele…", Toast.LENGTH_LONG).show();
                    new Thread(new Runnable() {
                        public void run() {
                            final String r = TvCopy.pull(getApplicationContext(), a, p);
                            h.post(new Runnable() {
                                public void run() {
                                    if (isFinishing()) return;
                                    boolean ok = r.startsWith("OK");
                                    Toast.makeText(AjustesActivity.this, r.substring(Math.min(3, r.length())), Toast.LENGTH_LONG).show();
                                    if (ok) { keepTopic = topicSel; recreate(); }
                                }
                            });
                        }
                    }).start();
                }
            })
            .setNegativeButton("Cancelar", null).show();
    }

    /** Preajuste: ciclo personalizado → Tranquilo → Fiesta → Solo recuerdos; al elegir uno se cambian los ajustes del pase. */
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
        self[0] = btn("Movimiento", R.drawable.ic_move, new View.OnClickListener() {
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
        Dialogs.edgeKeys(new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Salvapantallas de un solo lugar")
            .setItems(labels, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String[] r = rows.get(w);
                    Prefs.sp(AjustesActivity.this).edit().putString(Extras.PLACE, r[1]).putBoolean(Extras.PLACE_REGION, "1".equals(r[2])).commit();
                    placeBtn.setText(placeLabel());
                }
            })
            .setNegativeButton("Cancelar", null)
            .show());
    }

    // ---- lectura de cámaras en segundo plano (antes en Estadísticas)
    private Button camBtn;
    private TextView camInfo;
    private long camShownAt = System.currentTimeMillis();

    private final Runnable camTick = new Runnable() {
        public void run() {
            if (camBtn == null) return;
            if (CamScan.running) {
                camBtn.setText("Parar (" + CamScan.done + " de " + CamScan.total + ")");
                h.postDelayed(this, 1500);
            } else {
                camBtn.setText("Leer cámaras");
                if (CamScan.finishedAt > camShownAt) {
                    camShownAt = CamScan.finishedAt;
                    if (CamScan.lastTotal == 0) Toast.makeText(AjustesActivity.this, "Ya están leídas todas las fotos. Si crees que faltan cámaras, usa «Releer las de sin cámara».", Toast.LENGTH_LONG).show();
                    refreshCamInfo();
                }
                h.postDelayed(this, 3000);
            }
        }
    };

    private void refreshCamInfo() {
        new Thread(new Runnable() {
            public void run() {
                int known = 0, total = 0, read = 0;
                try {
                    java.util.List<Item> all = Search.allVisible(AjustesActivity.this);
                    java.util.HashMap<String, String> cams = GeoIndex.get(AjustesActivity.this).camMap();
                    total = all.size();
                    for (Item it : all) {
                        String c = cams.get(it.key());
                        if (c != null) { read++; if (c.length() > 0) known++; }
                    }
                } catch (Throwable e) { Diag.e("cámaras", e); }
                final String t = total == 0 ? "" : read == 0 ? "Aún no se ha leído ninguna cámara."
                    : "Cámara conocida en " + String.format(MetaFetcher.ES, "%,d", known) + " de " + String.format(MetaFetcher.ES, "%,d", total) + " fotos.";
                h.post(new Runnable() {
                    public void run() { if (camInfo != null) camInfo.setText(t); }
                });
            }
        }).start();
    }

    private void camAction() {
        if (CamScan.running) { CamScan.stop(this); return; }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Leer la cámara de cada foto")
            .setMessage("Se lee el principio de cada foto (unos 160 KB por foto) para saber con qué cámara se hizo. Con muchas fotos puede tardar y gastar bastantes datos. Puedes pararlo cuando quieras y seguir otro día.")
            .setPositiveButton("Leer las que faltan", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int w) { startCam(false); }
            })
            .setNeutralButton("Releer las de sin cámara", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int w) { startCam(true); }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void startCam(boolean retry) {
        CamScan.lastTotal = -1;
        CamScan.start(this, retry);
        h.removeCallbacks(camTick);
        h.post(camTick);
    }

    // ---- actualización dentro de «Ayuda y mantenimiento»
    private TextView updInfo, updNotes;
    private Button updInstall;
    private View updInstallRow;
    private Updater.Info updAvail;
    private boolean updBusy;

    private void checkUpdate() {
        if (updBusy) return;
        updBusy = true;
        updInfo.setText("Comprobando si hay una versión nueva…");
        new Thread(new Runnable() {
            public void run() {
                Updater.Info i = null;
                String err = null;
                try { i = Updater.latest(); } catch (Exception e) { err = String.valueOf(e.getMessage()); }
                final Updater.Info info = i;
                final String error = err;
                h.post(new Runnable() {
                    public void run() {
                        updBusy = false;
                        String cur = Updater.installed(AjustesActivity.this);
                        updAvail = null;
                        updNotes.setVisibility(View.GONE);
                        updInstallRow.setVisibility(View.GONE);
                        if (error != null) {
                            updInfo.setText("Versión " + cur + ". No se pudo comprobar (" + error + ").");
                        } else if (info == null || !Updater.newer(info.version, cur)) {
                            updInfo.setText("Última versión: " + cur + ".");
                        } else {
                            updAvail = info;
                            updInfo.setText("Hay una versión nueva: " + info.version + " (tienes la " + cur + ").");
                            String notes = info.notes.length() > 600 ? info.notes.substring(0, 600) + "…" : info.notes;
                            if (notes.length() > 0) { updNotes.setText(notes); updNotes.setVisibility(View.VISIBLE); }
                            updInstall.setText("Actualizar a la " + info.version);
                            updInstallRow.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }).start();
    }

    private void downloadUpdate(final Updater.Info info) {
        if (updBusy) return;
        updBusy = true;
        updInfo.setText("Descargando la versión " + info.version + "…");
        new Thread(new Runnable() {
            public void run() {
                java.io.File f = null;
                String err = null;
                try {
                    f = Updater.download(AjustesActivity.this, info, new Updater.Progress() {
                        public void onPercent(final int p) {
                            h.post(new Runnable() {
                                public void run() { updInfo.setText("Descargando la versión " + info.version + "…  " + p + " %"); }
                            });
                        }
                    });
                } catch (Exception e) { err = String.valueOf(e.getMessage()); }
                final java.io.File apk = f;
                final String error = err;
                h.post(new Runnable() {
                    public void run() {
                        updBusy = false;
                        if (apk == null) {
                            updInfo.setText("No se pudo descargar (" + error + ").");
                        } else {
                            try {
                                Updater.install(AjustesActivity.this, apk);
                                updInfo.setText("Descargada. Confirma la instalación en la pantalla que se abre.");
                            } catch (Exception e) {
                                updInfo.setText("No se pudo iniciar la instalación (" + e.getMessage() + ").");
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private LinearLayout profilesCard;
    private static int profView = -1;   // -1: lista de perfiles; otro número: ficha del perfil con ese id

    private boolean profileExists(int id) {
        for (String[] p : Prefs.profiles(this)) if (p[0].equals(String.valueOf(id))) return true;
        return false;
    }

    /** Rehace el panel «Perfiles» (lista o ficha) y deja el foco dentro. */
    private void refreshProfiles() {
        if (profilesCard == null) return;
        final boolean live = topics != null;
        final int before = profView;
        keepPanel = live;      // al quitar el botón con foco, Android lo pasa a los temas: que no cambien el panel
        while (profilesCard.getChildCount() > 1) profilesCard.removeViewAt(1);
        if (profView >= 0 && !profileExists(profView)) profView = -1;
        if (profView < 0) buildProfileList(); else buildProfileSheet(profView);
        if (live) {
            View target = null;
            if (before >= 0 && profView < 0) {    // de la ficha a la lista: el foco al renglón de ese perfil
                for (int i = 1; i < profilesCard.getChildCount() && target == null; i++) {
                    Object t = profilesCard.getChildAt(i).getTag();
                    if (t instanceof Integer && ((Integer) t).intValue() == before) target = profilesCard.getChildAt(i);
                }
            }
            if (target != null) target.requestFocus();
            else for (int i = 1; i < profilesCard.getChildCount(); i++) if (profilesCard.getChildAt(i).requestFocus()) break;
            keepPanel = false;
        }
    }

    private String profileStatus(int id) {
        return (id == Prefs.profile(this) ? "En uso, " : "") + (Prefs.hasCode(this, id) ? "con código" : "sin código");
    }

    private ImageView profileAvatar(int id, int size) {
        ImageView ic = new ImageView(this);
        Ui.showProfileIcon(ic, id);
        GradientDrawable sq = new GradientDrawable();
        sq.setColor(0xFF22364F);
        sq.setCornerRadius(dp(10));
        ic.setBackground(sq);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return ic;
    }

    /** Lista: activar o desactivar los perfiles, un renglón por perfil y «Añadir perfil». */
    private void buildProfileList() {
        final boolean on = Prefs.profilesOn(this);
        Button tg = btn("Perfiles: " + (on ? "sí" : "no"), R.drawable.ic_swap, new View.OnClickListener() {
            public void onClick(View v) {
                if (!on) {
                    Prefs.meta(AjustesActivity.this).edit().putBoolean("fx_profiles", true).commit();
                    refreshProfiles();
                } else PinPad.enter(AjustesActivity.this, 0, "Familia", new Runnable() {
                    public void run() {
                        Prefs.setProfile(AjustesActivity.this, 0);
                        Prefs.meta(AjustesActivity.this).edit().putBoolean("fx_profiles", false).commit();
                        Toast.makeText(AjustesActivity.this, "Perfiles desactivados: vuelves a «Familia». Los demás perfiles se guardan por si los activas otra vez.", Toast.LENGTH_LONG).show();
                        keepTopic = topicSel;
                        recreate();
                    }
                });
            }
        });
        describe(tg, "Con los perfiles, cada persona tiene sus propios álbumes, ajustes, PIN y web de configuración, y puede protegerlos con un código. Al desactivarlos vuelves a «Familia»; los demás se guardan por si los activas otra vez.");
        profilesCard.addView(row(tg, null));
        TextView t = text(on ? "«Familia» es el perfil de siempre. Los nuevos empiezan vacíos. Se cambia de perfil en la pantalla principal, con el icono junto al QR."
            : "Activa los perfiles para que cada persona tenga sus álbumes, sus ajustes y, si quiere, un código para entrar.", 13, 0xFFBBD0E6);
        t.setPadding(dp(4), 0, dp(4), dp(10));
        profilesCard.addView(t);
        if (!on) return;
        for (String[] p : Prefs.profiles(this)) profilesCard.addView(profileRow(Integer.parseInt(p[0]), p[1]));
        Button add = btn("Añadir perfil", R.drawable.ic_add, new View.OnClickListener() {
            public void onClick(View v) { askProfileName("Nuevo perfil", "", -1); }
        });
        describe(add, "Crea un perfil vacío, con los ajustes de fábrica. Luego puedes copiarle los ajustes de otro perfil desde su ficha.");
        profilesCard.addView(row(add, null));
    }

    /** Un renglón de la lista: icono (o foto), nombre y estado. Al pulsarlo se abre su ficha. */
    private View profileRow(final int id, final String name) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setTag(Integer.valueOf(id));
        r.setPadding(dp(8), dp(6), dp(12), dp(6));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x1AFFFFFF);
        off.setCornerRadius(dp(10));
        off.setStroke(dp(1), 0x33FFFFFF);
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x33FFFFFF);
        on.setCornerRadius(dp(10));
        on.setStroke(dp(3), 0xFF3D8BFD);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        r.setBackground(bg);
        r.addView(profileAvatar(id, 44));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(12), 0, 0, 0);
        TextView nm = text(name, 16, Color.WHITE);
        nm.setTypeface(Ui.BLACK);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(nm);
        tx.addView(text(profileStatus(id), 12, 0xFF9FB3C8));
        r.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { profView = id; refreshProfiles(); }
        });
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (has && hint != null) hint.setText("Abre la ficha de «" + name + "»: usarlo, nombre, icono o foto, código, copiar ajustes y borrar.");
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, 0, 0, dp(8));
        r.setLayoutParams(lp);
        return r;
    }

    /** Ficha de un perfil: todo lo que se puede hacer con él (antes, parte desde el móvil). */
    private void buildProfileSheet(final int id) {
        final String name = Prefs.profileName(this, id);
        final boolean fam = id == 0;
        final boolean coded = Prefs.hasCode(this, id);
        final boolean cur = id == Prefs.profile(this);

        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(dp(4), 0, 0, dp(12));
        hd.addView(profileAvatar(id, 64));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(14), 0, 0, 0);
        TextView nm = text(name, 20, Color.WHITE);
        nm.setTypeface(Ui.BLACK);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(nm);
        tx.addView(text(profileStatus(id), 13, 0xFF9FB3C8));
        if (cur) tx.addView(text("PIN de la web del móvil: " + Prefs.webPin(this), 13, 0xFF9FB3C8));
        hd.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        profilesCard.addView(hd, new LinearLayout.LayoutParams(-1, -2));

        final List<Button> bs = new ArrayList<Button>();
        Button back = btn("Todos los perfiles", R.drawable.ic_undo, new View.OnClickListener() {
            public void onClick(View v) { profView = -1; refreshProfiles(); }
        });
        describe(back, "Vuelve a la lista de perfiles.");
        bs.add(back);

        Button use = cur
            ? btn("Perfil en uso", R.drawable.ic_check, new View.OnClickListener() {
                public void onClick(View v) { Toast.makeText(AjustesActivity.this, "«" + name + "» ya está en uso.", Toast.LENGTH_SHORT).show(); }
            })
            : btn("Usar este perfil", R.drawable.ic_play, new View.OnClickListener() {
                public void onClick(View v) {
                    PinPad.enter(AjustesActivity.this, id, name, new Runnable() {
                        public void run() {
                            Prefs.setProfile(AjustesActivity.this, id);
                            Toast.makeText(AjustesActivity.this, "Perfil «" + name + "» en uso.", Toast.LENGTH_SHORT).show();
                            keepTopic = topicSel;
                            recreate();
                        }
                    });
                }
            });
        describe(use, "Pasa a este perfil: sus álbumes, ajustes, PIN y web. Si tiene código, lo pide.");
        bs.add(use);

        if (!fam) {
            Button rn = btn("Cambiar el nombre", R.drawable.ic_notes, new View.OnClickListener() {
                public void onClick(View v) { askProfileName("Nombre del perfil", name, id); }
            });
            describe(rn, "Cambia el nombre que sale junto al icono del perfil (hasta 20 letras).");
            bs.add(rn);
        }
        Button ic = btn("Icono o foto", R.drawable.ic_wallpaper, new View.OnClickListener() {
            public void onClick(View v) { iconPicker(id, name); }
        });
        describe(ic, "Elige un icono de la muestra o una foto de un archivo. También se puede poner la foto desde la web del móvil.");
        bs.add(ic);

        Button code = btn(coded ? "Cambiar el código" : "Poner un código", R.drawable.ic_visibility_off, new View.OnClickListener() {
            public void onClick(View v) {
                PinPad.enter(AjustesActivity.this, id, name, new Runnable() {   // para cambiarlo hace falta el actual
                    public void run() {
                        PinPad.setNew(AjustesActivity.this, id, name, new Runnable() {
                            public void run() { refreshProfiles(); }
                        });
                    }
                });
            }
        });
        describe(code, "Código de 4 cifras que se pide al pasar a este perfil. Se teclea con el mando: arriba y abajo cambian la cifra, derecha avanza, OK confirma.");
        bs.add(code);
        if (coded) {
            Button rm = btn("Quitar el código", R.drawable.ic_visibility, new View.OnClickListener() {
                public void onClick(View v) {
                    PinPad.enter(AjustesActivity.this, id, name, new Runnable() {
                        public void run() {
                            Prefs.setCode(AjustesActivity.this, id, null);
                            Toast.makeText(AjustesActivity.this, "Código de «" + name + "» quitado.", Toast.LENGTH_SHORT).show();
                            refreshProfiles();
                        }
                    });
                }
            });
            describe(rm, "Deja el perfil sin código. Si se ha olvidado, se puede quitar desde la web del móvil, con el PIN.");
            bs.add(rm);
        }

        Button cp = btn("Copiar ajustes de otro", R.drawable.ic_copy, new View.OnClickListener() {
            public void onClick(View v) { copySettingsDialog(id, name); }
        });
        describe(cp, "Pone en este perfil los ajustes de otro (pase, mosaicos, transición, estilo, música, funciones extra…). No toca los álbumes, favoritas, ocultas, código, nombre ni icono.");
        bs.add(cp);

        if (!fam) {
            Button del = btn("Borrar el perfil", R.drawable.ic_delete, new View.OnClickListener() {
                public void onClick(View v) {
                    PinPad.enter(AjustesActivity.this, id, name, new Runnable() {
                        public void run() { confirmDeleteProfile(id, name); }
                    });
                }
            });
            describe(del, "Borra el perfil con sus álbumes y todos sus ajustes. No se puede deshacer.");
            bs.add(del);
        }
        for (int i = 0; i < bs.size(); i += 2) profilesCard.addView(row(bs.get(i), i + 1 < bs.size() ? bs.get(i + 1) : null));
    }

    /** Elige de qué perfil copiar los ajustes y avisa de lo que se cambia. */
    private void copySettingsDialog(final int id, final String name) {
        final List<String[]> others = new ArrayList<String[]>();
        for (String[] p : Prefs.profiles(this)) if (!p[0].equals(String.valueOf(id))) others.add(p);
        if (others.isEmpty()) {
            Toast.makeText(this, "No hay otro perfil del que copiar.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labs = new String[others.size()];
        for (int i = 0; i < labs.length; i++) labs[i] = others.get(i)[1];
        Dialogs.edgeKeys(new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Copiar a «" + name + "» los ajustes de…")
            .setItems(labs, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    final int from = Integer.parseInt(others.get(which)[0]);
                    final String fromName = others.get(which)[1];
                    new AlertDialog.Builder(AjustesActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("¿Copiar los ajustes de «" + fromName + "»?")
                        .setMessage("Los ajustes de «" + name + "» pasan a ser los de «" + fromName + "». Sus álbumes, favoritas, ocultas, código, nombre e icono no cambian.")
                        .setPositiveButton("Copiar", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dd, int w) {
                                PinPad.enter(AjustesActivity.this, id, name, new Runnable() {
                                    public void run() {
                                        Prefs.copySettings(AjustesActivity.this, from, id);
                                        Toast.makeText(AjustesActivity.this, "Ajustes de «" + name + "» copiados de «" + fromName + "».", Toast.LENGTH_LONG).show();
                                        if (id == Prefs.profile(AjustesActivity.this)) { keepTopic = topicSel; recreate(); }
                                        else refreshProfiles();
                                    }
                                });
                            }
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show());
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
                public void onClick(DialogInterface d, int w) { refreshProfiles(); }
            }).create();
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> a, View v, int i, long l) {
                dlg.dismiss();
                if (i < n) { Prefs.setProfileIcon(AjustesActivity.this, id, i); refreshProfiles(); }
                else if (i == n) pickPhoto(id);
                else { Prefs.clearProfileLook(AjustesActivity.this, id); refreshProfiles(); }
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
            refreshProfiles();
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
        refreshProfiles();
    }

    private void confirmDeleteProfile(final int id, final String name) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("¿Borrar «" + name + "»?")
            .setMessage("Se borran sus álbumes y todos sus ajustes. No se puede deshacer.")
            .setPositiveButton("Borrar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    boolean wasCur = Prefs.profile(AjustesActivity.this) == id;
                    Prefs.deleteProfile(AjustesActivity.this, id);
                    if (wasCur) recreate(); else refreshProfiles();
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
                    if (n.length() == 0) { refreshProfiles(); return; }
                    if (id < 0) {
                        int nid = Prefs.addProfile(AjustesActivity.this, n);
                        Toast.makeText(AjustesActivity.this, "Perfil «" + n + "» creado, vacío. Pulsa «Usar este perfil» para entrar y añadir sus álbumes.", Toast.LENGTH_LONG).show();
                    } else Prefs.renameProfile(AjustesActivity.this, id, n);
                    refreshProfiles();
                }
            })
            .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { refreshProfiles(); }
            })
            .show();
    }

    /** Fila de un tema: icono y texto sueltos (sin aspecto de botón); el elegido lleva una barra de color a la izquierda. */
    private static class TopicRow extends LinearLayout {
        private final View bar;
        private final ImageView ic;
        private final TextView tv;
        private boolean sel, foc;

        TopicRow(android.content.Context c, String name, int icon) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            float d = c.getResources().getDisplayMetrics().density;
            bar = new View(c);
            addView(bar, new LinearLayout.LayoutParams((int) (3 * d), (int) (22 * d)));
            ic = new ImageView(c);
            ic.setImageResource(icon);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int) (20 * d), (int) (20 * d));
            ip.setMargins((int) (12 * d), 0, (int) (12 * d), 0);
            addView(ic, ip);
            tv = new TextView(c);
            tv.setText(name);
            tv.setTextSize(16);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
            paint();
        }

        void setSelectedLook(boolean s) { sel = s; paint(); }
        void setFocusedLook(boolean f) { foc = f; paint(); }

        private void paint() {
            bar.setBackgroundColor(sel ? 0xFF3D8BFD : 0x00000000);
            tv.setTextColor(sel ? Color.WHITE : 0xFF9FB3C8);
            tv.setTypeface(sel ? Ui.BLACK : Ui.REG);
            ic.setColorFilter(sel ? 0xFF7FB2FF : 0xFF6F86A0);
            if (foc) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0x33FFFFFF);
                g.setCornerRadius(8 * getResources().getDisplayMetrics().density);
                setBackground(g);
            } else setBackground(null);
        }
    }
}
