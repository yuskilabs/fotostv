package com.yuskilabs.fotostv;

import android.app.Activity;
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

/** Ajustes de FotosTV en su propia pantalla: música, salvapantallas, vídeo y caché, ubicaciones. */
public class AjustesActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Button musicBtn, radioBtn, folderBtn, volBtn, dreamMusicBtn, vqBtn;
    private TextView geoView;

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
        tt.addView(text("Presentación, música, vídeo, caché y ubicaciones. Los álbumes se gestionan desde la web.", 14, 0xFF9FB3C8));
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
        cm.addView(row(musicBtn, volBtn));
        cm.addView(row(folderBtn, dreamMusicBtn));
        cm.addView(row(radioBtn, null));
        

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
        

        // --- presentación
        LinearLayout cp = card("Presentación", R.drawable.ic_photo);
        cp.addView(row(
            cyc("Orden", "order", false, new String[]{"random", "norepeat", "smart", "asc", "desc"},
                new String[]{"aleatorio", "aleatorio sin repetir", "inteligente", "por fecha ↑", "por fecha ↓"}, R.drawable.ic_shuffle,
                new Cur() { public String get() { return Prefs.order(AjustesActivity.this); } }),
            cyc("Cambiar cada", "interval", true, new String[]{"5", "10", "30", "60", "180", "300", "600"},
                new String[]{"5 s", "10 s", "30 s", "1 min", "3 min", "5 min", "10 min"}, R.drawable.ic_timer,
                new Cur() { public String get() { return String.valueOf(Prefs.interval(AjustesActivity.this)); } })));
        cp.addView(row(
            cyc("Encuadre", "fit", false, new String[]{"auto", "crop", "fit"},
                new String[]{"auto", "llenar", "completo"}, R.drawable.ic_fullscreen,
                new Cur() { public String get() { return Prefs.fit(AjustesActivity.this); } }),
            tog("Continuar", "resume", R.drawable.ic_check, new Bool() { public boolean get() { return Prefs.resume(AjustesActivity.this); } })));
        

        LinearLayout ci = card("Información en pantalla", R.drawable.ic_info);
        ci.addView(row(
            tog("Álbum", "showAlbum", R.drawable.ic_albums, new Bool() { public boolean get() { return Prefs.showAlbum(AjustesActivity.this); } }),
            tog("Fecha", "showDate", R.drawable.ic_event, new Bool() { public boolean get() { return Prefs.showDate(AjustesActivity.this); } })));
        ci.addView(row(
            tog("Lugar", "showPlace", R.drawable.ic_place, new Bool() { public boolean get() { return Prefs.showPlace(AjustesActivity.this); } }),
            tog("Hora", "clock", R.drawable.ic_schedule, new Bool() { public boolean get() { return Prefs.clock(AjustesActivity.this); } })));
        

        // orden de lectura (izquierda→derecha, y luego hacia abajo)
        left.addView(cp);
        right.addView(ci);
        left.addView(cm);
        right.addView(cv);
        // la última tarjeta de cada columna se estira para que las dos acaben a la misma altura
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1f);
        left.addView(cg, gp);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(-1, 0, 1f);
        right.addView(cs, sp2);
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
        root.addView(text("Atrás: volver a la pantalla principal", 12, 0xFF7C93AD));
        setContentView(root);
        musicBtn.requestFocus();
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

    // ---- construcción
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(Ui.REG);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout card(String title, int icon) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(10), dp(12), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99162A40);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), 0xFF2C4766);
        c.setBackground(bg);
        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(0xFF7FB2FF);
        hd.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = text(title, 18, Color.WHITE);
        t.setTypeface(Ui.BLACK);
        t.setPadding(dp(10), 0, 0, 0);
        hd.addView(t);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, 0, 0, dp(8));
        c.addView(hd, hp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(14));
        c.setLayoutParams(cp);
        return c;
    }

    private Button btn(String label, int icon, View.OnClickListener l) {
        Button cb = new Button(this) {   // tras los dos puntos, la primera letra en mayúscula («Música: Apagada»)
            @Override public void setText(CharSequence t, BufferType type) {
                if (t != null) {
                    String s = t.toString();
                    int i = s.indexOf(": ");
                    if (i >= 0 && i + 2 < s.length() && Character.isLowerCase(s.charAt(i + 2)))
                        s = s.substring(0, i + 2) + Character.toUpperCase(s.charAt(i + 2)) + s.substring(i + 3);
                    t = s;
                }
                super.setText(t, type);
            }
        };
        Button b = Ui.icon(Ui.button(this, cb, label, l), icon);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setPadding(dp(8), dp(4), dp(6), dp(4));
        return b;
    }

    /** Fila de uno o dos botones de igual tamaño. */
    private LinearLayout row(Button a, Button b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, dp(46), 1f);
        if (b != null) la.setMargins(0, 0, dp(5), 0);
        r.addView(a, la);
        if (b != null) {
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lb.setMargins(dp(5), 0, 0, 0);
            r.addView(b, lb);
        }
        LinearLayout.LayoutParams lr = new LinearLayout.LayoutParams(-1, -2);
        lr.setMargins(0, 0, 0, dp(8));
        r.setLayoutParams(lr);
        return r;
    }

    private android.graphics.drawable.Drawable focusBg() {
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x00000000);
        on.setCornerRadius(dp(5));
        on.setStroke(dp(3), 0xFF3D8BFD);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0x00000000));
        return bg;
    }

    interface Cur { String get(); }
    interface Bool { boolean get(); }

    /** Botón que rota entre valores guardados en los ajustes. */
    private Button cyc(final String prefix, final String key, final boolean isInt, final String[] vals, final String[] labs, int icon, final Cur cur) {
        final Button[] self = new Button[1];
        final Runnable show = new Runnable() {
            public void run() {
                String c = cur.get();
                String l = c;
                for (int i = 0; i < vals.length; i++) if (vals[i].equals(c)) l = labs[i];
                self[0].setText(prefix + ": " + l);
            }
        };
        self[0] = btn(prefix, icon, new View.OnClickListener() {
            public void onClick(View v) {
                String c = cur.get();
                int idx = -1;
                for (int i = 0; i < vals.length; i++) if (vals[i].equals(c)) idx = i;
                String nx = vals[(idx + 1) % vals.length];
                android.content.SharedPreferences.Editor e = Prefs.sp(AjustesActivity.this).edit();
                if (isInt) e.putInt(key, Integer.parseInt(nx)); else e.putString(key, nx);
                e.commit();
                show.run();
            }
        });
        show.run();
        return self[0];
    }

    /** Botón de sí / no. */
    private Button tog(final String label, final String key, int icon, final Bool cur) {
        final Button[] self = new Button[1];
        self[0] = btn(label, icon, new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.sp(AjustesActivity.this).edit().putBoolean(key, !cur.get()).commit();
                self[0].setText(label + ": " + (cur.get() ? "sí" : "no"));
            }
        });
        self[0].setText(label + ": " + (cur.get() ? "sí" : "no"));
        return self[0];
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

}
