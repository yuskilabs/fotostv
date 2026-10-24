package com.yuskilabs.fotostv;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.ImageView;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class SettingsActivity extends Activity implements WebConfigServer.Listener {
    private TextView webInfo, status, verInfo, msgView;
    private Button cacheBtn;
    private boolean updating;
    private WebConfigServer server;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String extra = getIntent().getStringExtra("url");
        if (extra != null && extra.trim().length() > 0) {
            List<Prefs.Album> l = Prefs.albums(this);
            boolean found = false;
            for (Prefs.Album a : l) if (a.url.equals(extra.trim())) found = true;
            if (!found) { l.add(new Prefs.Album(extra.trim(), "", true)); Prefs.saveAlbums(this, l); }
        }

        // fondo con degradado. Arriba: logo + tarjeta del móvil. Abajo: botones | tus álbumes (misma altura)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(44), dp(24), dp(44), dp(24));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A1424, 0xFF14305A}));
        root.setClipChildren(false);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_full);
        logo.setAdjustViewBounds(true);
        top.addView(logo, new LinearLayout.LayoutParams(-2, dp(96)));
        TextView tag = text("Tus álbumes de Google Fotos, en la tele y como salvapantallas", 13, 0xFF9FB3C8);
        tag.setPadding(0, dp(6), 0, 0);
        top.addView(tag);
        LinearLayout vrow = new LinearLayout(this);
        vrow.setOrientation(LinearLayout.HORIZONTAL);
        vrow.setGravity(Gravity.CENTER_VERTICAL);
        verInfo = text("Versión " + Updater.installed(this), 13, 0xFF7C93AD);
        verInfo.setPadding(0, dp(2), 0, 0);
        vrow.addView(verInfo, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView yl = new ImageView(this);
        yl.setImageResource(R.drawable.yuski_logo);
        yl.setAdjustViewBounds(true);
        vrow.addView(yl, new LinearLayout.LayoutParams(-2, dp(22)));
        TextView by = text("yuskilabs", 13, 0xFF9FB3C8);
        by.setPadding(dp(6), 0, 0, 0);
        vrow.addView(by);
        top.addView(vrow);
        row1.addView(top, new LinearLayout.LayoutParams(0, -2, 0.40f));

        LinearLayout c1 = card();
        c1.setPadding(dp(18), dp(8), dp(18), dp(8));
        c1.setGravity(Gravity.CENTER_VERTICAL);
        TextView c1t = cardTitle("Configúralo desde el móvil");
        c1t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        c1t.setPadding(0, 0, 0, dp(2));
        c1.addView(c1t);
        c1.addView(line("1. En Google Fotos abre un álbum → Compartir → Crear enlace, y cópialo."));
        c1.addView(line("2. Con el móvil en la misma wifi, abre esta dirección y pega los enlaces:"));
        webInfo = text("", 17, 0xFF7CC4FF);
        webInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        webInfo.setPadding(0, dp(1), 0, dp(1));
        c1.addView(webInfo);
        c1.addView(line("3. Pulsa «Elegir salvapantallas» y selecciona «FotosTV»."));
        LinearLayout.LayoutParams c1p = new LinearLayout.LayoutParams(0, -1, 0.60f);
        c1p.setMargins(dp(32), 0, 0, 0);
        row1.addView(c1, c1p);
        root.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setClipChildren(false);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        Button browse = side("Ver álbumes", R.drawable.ic_albums, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, BrowserActivity.class)); }
        });
        left.addView(browse);
        left.addView(side("Vista previa del salvapantallas", R.drawable.ic_preview, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, PreviewActivity.class)); }
        }));
        idleBtn = side(idleLabel(), R.drawable.ic_timer, new View.OnClickListener() {
            public void onClick(View v) { cycleIdle(); }
        });
        left.addView(idleBtn);
        left.addView(side("Elegir salvapantallas", R.drawable.ic_tv, new View.OnClickListener() {
            public void onClick(View v) { openDreamSettings(); }
        }));
        left.addView(side("Comprobar álbumes", R.drawable.ic_check, new View.OnClickListener() {
            public void onClick(View v) { checkAlbum(); }
        }));
        cacheBtn = side("Vaciar caché", R.drawable.ic_delete, new View.OnClickListener() {
            public void onClick(View v) { clearCache(); }
        });
        left.addView(cacheBtn);
        left.addView(side("Actualizar", R.drawable.ic_download, new View.OnClickListener() {
            public void onClick(View v) { updateFlow(); }
        }));
        row2.addView(left, new LinearLayout.LayoutParams(0, -1, 0.40f));

        LinearLayout c2 = card();
        c2.addView(cardTitle("Tus álbumes"));
        msgView = text("", 15, Color.WHITE);
        msgView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        msgView.setPadding(dp(8), dp(2), dp(8), dp(2));
        msgView.setVisibility(View.GONE);
        c2.addView(msgView);
        status = text("", 15, Color.WHITE);
        ScrollView sv = new ScrollView(this);
        // se puede entrar con el mando (derecha) y desplazar con arriba/abajo cuando la lista es larga
        sv.setFocusable(true);
        sv.setFocusableInTouchMode(false);
        sv.setVerticalScrollBarEnabled(true);
        sv.setScrollbarFadingEnabled(false);
        GradientDrawable svOn = new GradientDrawable();
        svOn.setColor(0x00000000);
        svOn.setCornerRadius(dp(5));
        svOn.setStroke(dp(3), 0xFF3D8BFD);
        StateListDrawable svBg = new StateListDrawable();
        svBg.addState(new int[]{android.R.attr.state_focused}, svOn);
        svBg.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0x00000000));
        sv.setBackground(svBg);
        sv.setPadding(dp(8), dp(4), dp(8), dp(4));
        sv.addView(status);
        c2.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams c2p = new LinearLayout.LayoutParams(0, -1, 0.60f);
        c2p.setMargins(dp(32), 0, 0, 0);
        row2.addView(c2, c2p);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, 0, 1f);
        r2p.setMargins(0, dp(14), 0, 0);
        root.addView(row2, r2p);

        setContentView(root);
        browse.requestFocus();
        updateStatus(null);
        if (Prefs.idleMin(this) > 0 && !idleServiceOn()) reenableIdleService();
    }

    private Button side(String label, int icon, View.OnClickListener l) {
        Button b = Ui.icon(Ui.button(this, label, l), icon);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(16), dp(5), dp(16), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.setMargins(0, 0, 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99162A40);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0xFF2C4766);
        c.setBackground(bg);
        return c;
    }

    private TextView cardTitle(String s) {
        TextView t = text(s, 18, Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView line(String s) {
        TextView t = text(s, 12, 0xFFBBCCDD);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    @Override protected void onStart() {
        super.onStart();
        refreshCache();
        silentCheck();
        if (msgView.getVisibility() == View.VISIBLE && msgView.getText().toString().endsWith("\u2026")) startPulse();
        final WebConfigServer srv = new WebConfigServer(this, this);
        server = srv;
        webInfo.setText("Abriendo la web de configuración…");
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = srv.open();
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (server != srv) { srv.close(); return; }   // ya se salió de la pantalla
                        if (ok) {
                            srv.start();
                            String ip = localIp();
                            webInfo.setText("http://" + (ip == null ? "IP-de-la-Shield" : ip) + ":" + srv.port);
                        } else {
                            webInfo.setText("No se pudo abrir la web de configuración.");
                        }
                    }
                });
            }
        }).start();
    }

    @Override protected void onStop() {
        stopPulse();
        if (server != null) { server.close(); server = null; }
        super.onStop();
    }

    @Override public void onSaved() {
        h.post(new Runnable() {
            public void run() {
                updateStatus("Ajustes recibidos del móvil.");
                checkAlbum();
            }
        });
    }

    private int statusSeq;

    private void updateStatus(final String msg) {
        final int seq = ++statusSeq;
        if (msg == null) {
            stopPulse();
            msgView.setVisibility(View.GONE);
        } else {
            msgView.setText(msg);
            msgView.setVisibility(View.VISIBLE);
            if (msg.endsWith("\u2026")) startPulse();
            else { stopPulse(); msgView.setTextColor(Color.WHITE); }
        }
        new Thread(new Runnable() {
            public void run() {
                SpannableStringBuilder sb = new SpannableStringBuilder();
                List<Prefs.Album> albums = Prefs.albums(SettingsActivity.this);
                if (albums.isEmpty()) sb.append("Todavía no hay ningún álbum.");
                boolean all = "all".equals(Prefs.albumMode(SettingsActivity.this));
                if (!albums.isEmpty())
                    sb.append(all ? "Se muestran todos los álbumes:\n" : "Se muestran solo los álbumes marcados:\n");
                long total = 0;
                for (Prefs.Album al : albums) {
                    String[] i0 = Prefs.albumInfo(SettingsActivity.this, al.url);
                    if (i0 != null) { try { total += Long.parseLong(i0[2]); } catch (Exception ignored) { } }
                }
                if (albums.size() > 3 && total > 0)
                    sb.append(albums.size() + " álbumes \u00B7 " + String.format(java.util.Locale.GERMANY, "%,d", total)
                      + " elementos\n");
                if (!albums.isEmpty()) sb.append("\n");
                for (Prefs.Album al : albums) {
                    String[] info = Prefs.albumInfo(SettingsActivity.this, al.url);
                    String t = info != null ? info[0] : "";
                    String name = al.name.length() > 0 ? al.name : (t.length() > 0 ? t : al.url);
                    boolean on = all || al.show;
                    sb.append(on ? "\u25CF " : "\u25CB ");
                    int st = sb.length();
                    sb.append(name);
                    sb.setSpan(new ForegroundColorSpan(on ? 0xFFFFB454 : 0xFF7C93AD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (info != null) sb.append("  \u2014  " + info[2]);
                    if (!on) sb.append("   (oculto)");
                    sb.append("\n");
                }
                final CharSequence text = trim(sb);
                h.post(new Runnable() {
                    public void run() { if (seq == statusSeq) status.setText(text); }
                });
            }
        }).start();
    }

    private static CharSequence trim(SpannableStringBuilder sb) {
        int n = sb.length();
        while (n > 0 && Character.isWhitespace(sb.charAt(n - 1))) n--;
        return sb.subSequence(0, n);
    }

    // texto "Comprobando…" con colores de la paleta del logo y parpadeo suave
    private android.animation.ValueAnimator pulse;
    private static final int[] PAL = {0xFFFFAA46, 0xFFF43F7A, 0xFF8250F5};

    private void startPulse() {
        stopPulse();
        final android.animation.ArgbEvaluator ev = new android.animation.ArgbEvaluator();
        pulse = android.animation.ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(3000);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.setInterpolator(new android.view.animation.LinearInterpolator());
        pulse.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                float pos = t * PAL.length;
                int i = Math.min((int) pos, PAL.length - 1);
                int c = (Integer) ev.evaluate(pos - i, PAL[i], PAL[(i + 1) % PAL.length]);
                float k = 0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2 * 3);
                int alpha = (int) (255 * (0.45f + 0.55f * k));
                msgView.setTextColor((alpha << 24) | (c & 0xFFFFFF));
            }
        });
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) { pulse.cancel(); pulse = null; }
    }

    private void checkAlbum() {
        final List<Prefs.Album> albums = Prefs.albums(this);
        updateStatus("Comprobando álbumes…");
        new Thread(new Runnable() {
            public void run() {
                int ok = 0;
                String err = null;
                for (Prefs.Album al : albums) {
                    try {
                        Prefs.saveAlbumCache(SettingsActivity.this, al.url, AlbumFetcher.fetchAlbum(al.url));
                        ok++;
                    } catch (Exception e) {
                        err = (al.name.length() > 0 ? al.name : al.url) + ": " + e.getMessage();
                    }
                }
                final String m = albums.isEmpty() ? null
                        : err == null ? "\u2714 Álbumes correctos (" + ok + ")." : "\u2716 " + err;
                h.post(new Runnable() { public void run() { updateStatus(m); } });
            }
        }).start();
    }

    // ------------------------------------------------------------ actualizaciones (GitHub Releases)
    private void updateFlow() {
        if (updating) return;
        updating = true;
        verInfo.setText("Buscando actualización…");
        new Thread(new Runnable() {
            public void run() {
                Updater.Info i = null;
                String err = null;
                try { i = Updater.latest(); } catch (Exception e) { err = String.valueOf(e.getMessage()); }
                final Updater.Info info = i;
                final String error = err;
                h.post(new Runnable() {
                    public void run() {
                        updating = false;
                        String cur = Updater.installed(SettingsActivity.this);
                        if (error != null) {
                            verInfo.setText("Versión " + cur + "  ·  no se pudo comprobar (" + error + ")");
                        } else if (info == null || !Updater.newer(info.version, cur)) {
                            verInfo.setText("Versión " + cur + "  ·  \u2714 es la última");
                        } else {
                            verInfo.setText("Versión " + cur + "  ·  nueva versión " + info.version + " disponible");
                            askUpdate(info);
                        }
                    }
                });
            }
        }).start();
    }

    private void askUpdate(final Updater.Info info) {
        String notes = info.notes.length() > 700 ? info.notes.substring(0, 700) + "…" : info.notes;
        new android.app.AlertDialog.Builder(this)
            .setTitle("Nueva versión " + info.version)
            .setMessage(notes.length() > 0 ? notes : "Hay una versión nueva disponible.")
            .setPositiveButton("Actualizar", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) { downloadUpdate(info); }
            })
            .setNegativeButton("Ahora no", null)
            .show();
    }

    private void downloadUpdate(final Updater.Info info) {
        if (updating) return;
        updating = true;
        verInfo.setText("Descargando la versión " + info.version + "…");
        new Thread(new Runnable() {
            public void run() {
                java.io.File f = null;
                String err = null;
                try {
                    f = Updater.download(SettingsActivity.this, info, new Updater.Progress() {
                        public void onPercent(final int p) {
                            h.post(new Runnable() {
                                public void run() { verInfo.setText("Descargando la versión " + info.version + "…  " + p + " %"); }
                            });
                        }
                    });
                } catch (Exception e) { err = String.valueOf(e.getMessage()); }
                final java.io.File apk = f;
                final String error = err;
                h.post(new Runnable() {
                    public void run() {
                        updating = false;
                        if (apk == null) {
                            verInfo.setText("No se pudo descargar (" + error + ")");
                        } else {
                            try {
                                Updater.install(SettingsActivity.this, apk);
                                verInfo.setText("Descargada. Confirma la instalación en la pantalla que se abre.");
                            } catch (Exception e) {
                                verInfo.setText("No se pudo iniciar la instalación (" + e.getMessage() + ")");
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void silentCheck() {
        new Thread(new Runnable() {
            public void run() {
                Updater.Info i = null;
                try { i = Updater.latest(); } catch (Exception ignored) { }
                final Updater.Info info = i;
                h.post(new Runnable() {
                    public void run() {
                        if (updating || info == null) return;
                        String cur = Updater.installed(SettingsActivity.this);
                        if (Updater.newer(info.version, cur))
                            verInfo.setText("Versión " + cur + "  ·  nueva versión " + info.version + " disponible (pulsa «Actualizar»)");
                    }
                });
            }
        }).start();
    }

    private void clearCache() {
        updateStatus("Vaciando caché…");
        new Thread(new Runnable() {
            public void run() {
                long freed = Thumbs.get(SettingsActivity.this).clearAll();
                java.io.File[] vs = new java.io.File(getCacheDir(), "videos").listFiles();
                if (vs != null) for (java.io.File f : vs) { freed += f.length(); f.delete(); }
                final String m = "Caché de miniaturas vaciada (" + (freed / (1024 * 1024)) + " MB liberados).";
                h.post(new Runnable() { public void run() { updateStatus(m); refreshCache(); } });
            }
        }).start();
    }

    private static long dirSize(java.io.File d) {
        long n = 0;
        java.io.File[] fs = d.listFiles();
        if (fs != null) for (java.io.File f : fs) n += f.isDirectory() ? dirSize(f) : f.length();
        return n;
    }

    private static String mb(long b) {
        return b < 1024 * 1024 ? (b / 1024) + " KB" : (b / (1024 * 1024)) + " MB";
    }

    /** Muestra cuánto ocupa la caché (miniaturas y vídeos temporales). */
    private void refreshCache() {
        new Thread(new Runnable() {
            public void run() {
                final long th = dirSize(new java.io.File(getCacheDir(), "thumbs"));
                final long vi = dirSize(new java.io.File(getCacheDir(), "videos"));
                h.post(new Runnable() {
                    public void run() {
                        cacheBtn.setText("Vaciar caché (" + mb(th + vi) + ")");
                    }
                });
            }
        }).start();
    }

    private Button idleBtn;
    private static final int[] IDLE_OPTS = {0, 2, 5, 10, 15, 30};

    private String idleLabel() {
        int m = Prefs.idleMin(this);
        return m == 0 ? "Inicio automático: no" : "Inicio automático: " + m + " min";
    }

    private boolean idleServiceOn() {
        try {
            String s = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            return s != null && s.contains(getPackageName() + "/");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Android desactiva el servicio al actualizar la app. Si se concedió una vez el permiso por ADB
     * (WRITE_SECURE_SETTINGS), la propia app lo vuelve a activar.
     */
    private boolean reenableIdleService() {
        try {
            if (checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
            String me = getPackageName() + "/" + getPackageName() + ".IdleService";
            String cur = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            if (cur != null && cur.contains(me)) return true;
            String now = cur == null || cur.length() == 0 ? me : cur + ":" + me;
            Settings.Secure.putString(getContentResolver(), "enabled_accessibility_services", now);
            Settings.Secure.putInt(getContentResolver(), "accessibility_enabled", 1);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Para equipos sin salvapantallas estándar (Fire TV con FireOS 8): abre el pase tras unos minutos sin tocar el mando. */
    private void cycleIdle() {
        int cur = Prefs.idleMin(this), next = IDLE_OPTS[0];
        for (int i = 0; i < IDLE_OPTS.length; i++) if (IDLE_OPTS[i] == cur) next = IDLE_OPTS[(i + 1) % IDLE_OPTS.length];
        Prefs.sp(this).edit().putInt("idleMin", next).commit();
        idleBtn.setText(idleLabel());
        if (next > 0 && !idleServiceOn() && !reenableIdleService()) {
            android.widget.Toast.makeText(this, "Falta activar el servicio por ADB (mira el léeme de GitHub).", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void openDreamSettings() {
        // 1) pantalla propia de Android TV («Salvapantallas» dentro de Preferencias del dispositivo)
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
        // 2) ajuste estándar de Android
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

    private static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        return Ui.button(this, label, l);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
