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
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsActivity extends Activity implements WebConfigServer.Listener {
    private TextView geoView;
    private final Runnable geoTick = new Runnable() {
        public void run() {
            if (geoView == null) return;
            new Thread(new Runnable() {
                public void run() {
                    final String st;
                    String x;
                    try { x = GeoIndex.status(SettingsActivity.this); } catch (Throwable e) { x = ""; }
                    st = x;
                    h.post(new Runnable() { public void run() { if (geoView != null) { String ac = GeoIndex.action(SettingsActivity.this); geoView.setText(st + (ac.length() > 0 ? "  [OK: " + ac + "]" : "")); } } });
                }
            }).start();
            h.postDelayed(this, 4000);
        }
    };
    private TextView webInfo, pinInfo, status, verInfo, msgView;
    private ImageView qrView;
    private LinearLayout zoneQr, zoneGuest;
    private boolean askedStorage;
    private String webUrl;   // dirección de la web de ajustes (sin PIN), o null si aún no está lista
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
        root.setPadding(dp(44), dp(24), dp(44), dp(6));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A1424, 0xFF14305A}));
        root.setClipChildren(false);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        // el logo ocupa todo el alto que deja libre la frase de abajo: la altura de la fila la marca la caja de la derecha
        ImageView logo = new ImageView(this) {
            @Override protected void onMeasure(int w, int h) {
                if (View.MeasureSpec.getMode(h) == View.MeasureSpec.EXACTLY) super.onMeasure(w, h);
                else setMeasuredDimension(0, 0);   // no empuja la altura de la fila
            }
        };
        logo.setImageResource(R.drawable.logo_full);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        top.addView(logo, new LinearLayout.LayoutParams(-2, 0, 1f));
        // barra de búsqueda discreta (solo con «Buscar» activado en Funciones extra); se llega subiendo con el mando
        if (Extras.on(this, Extras.SEARCH)) {
            // no es un campo de texto de verdad (así ningún mando se queda «atrapado» dentro): al pulsar OK abre la búsqueda con el teclado
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setFocusable(true);
            bar.setClickable(true);
            bar.setPadding(dp(14), dp(4), dp(14), dp(4));
            ImageView lens = new ImageView(this);
            lens.setImageResource(R.drawable.ic_search);
            lens.setAlpha(0.7f);
            bar.addView(lens, new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView hintTx = text("Buscar lugar, álbum o fecha…", 14, 0xFF8FA6C0);
            hintTx.setSingleLine(true);
            hintTx.setPadding(dp(10), 0, 0, 0);
            bar.addView(hintTx, new LinearLayout.LayoutParams(0, -2, 1f));
            GradientDrawable sOff = new GradientDrawable();
            sOff.setColor(0x26FFFFFF);
            sOff.setCornerRadius(dp(18));
            sOff.setStroke(dp(1), 0x33FFFFFF);
            GradientDrawable sOn = new GradientDrawable();
            sOn.setColor(0x33FFFFFF);
            sOn.setCornerRadius(dp(18));
            sOn.setStroke(dp(2), 0xFF3D8BFD);
            StateListDrawable sBg = new StateListDrawable();
            sBg.addState(new int[]{android.R.attr.state_focused}, sOn);
            sBg.addState(new int[]{}, sOff);
            bar.setBackground(sBg);
            bar.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(SettingsActivity.this, SearchActivity.class).putExtra("kbd", true));
                }
            });
            searchBar = bar;
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(36));
            sp.setMargins(0, dp(6), 0, 0);
            top.addView(searchBar, sp);
        }
        LinearLayout vrow = new LinearLayout(this);
        vrow.setOrientation(LinearLayout.HORIZONTAL);
        vrow.setBaselineAligned(true);
        vrow.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ImageView yl = new ImageView(this);
        yl.setImageResource(R.drawable.yuski_h);
        yl.setAdjustViewBounds(true);
        // la línea base de las letras del logo está al 66,2 % de su altura: Android alinea con la de «Versión»
        yl.setBaselineAlignBottom(false);
        yl.setBaseline(Math.round(dp(18) * 0.662f));
        vrow.addView(yl, new LinearLayout.LayoutParams(-2, dp(18)));
        View bar = new View(this);
        bar.setBackgroundColor(0xFF6B7F96);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(1) + 1, dp(16));
        bp.setMargins(dp(10), 0, dp(10), 0);
        vrow.addView(bar, bp);
        verInfo = text("Versión " + Updater.installed(this), 13, 0xFF7C93AD);
        verInfo.setIncludeFontPadding(false);
        verInfo.setPadding(0, 0, 0, 0);
        vrow.addView(verInfo, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams topP = new LinearLayout.LayoutParams(0, -1, 0.50f);
        topP.setMargins(0, 0, dp(7), 0);
        row1.addView(top, topP);

        // arriba a la derecha, dos zonas separadas: el QR de la configuración (OK lo agranda) y el botón «Invitado»
        zoneQr = zone();
        zoneQr.setOrientation(LinearLayout.HORIZONTAL);
        zoneQr.setGravity(Gravity.CENTER_VERTICAL);
        zoneQr.setPadding(dp(16), dp(8), dp(10), dp(8));
        LinearLayout c1l = new LinearLayout(this);
        c1l.setOrientation(LinearLayout.VERTICAL);
        c1l.setGravity(Gravity.CENTER_VERTICAL);
        TextView c1t = cardTitle("Configúralo desde el móvil");
        c1t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        c1t.setPadding(0, 0, 0, dp(2));
        c1l.addView(c1t);
        pinInfo = text("", 20, Color.WHITE);
        pinInfo.setTypeface(Ui.BLACK);
        pinInfo.setPadding(0, dp(1), 0, dp(1));
        c1l.addView(pinInfo);
        webInfo = text("", 14, 0xFF7CC4FF);
        webInfo.setTypeface(Ui.BLACK);
        webInfo.setPadding(0, dp(1), 0, dp(1));
        c1l.addView(webInfo);
        zoneQr.addView(c1l, new LinearLayout.LayoutParams(0, -2, 1f));
        qrView = new ImageView(this);
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrView.setVisibility(View.GONE);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(dp(84), dp(84));
        qp.setMargins(dp(12), 0, 0, 0);
        zoneQr.addView(qrView, qp);
        zoneQr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { qrDialog(); }
        });

        zoneGuest = zone();
        zoneGuest.setOrientation(LinearLayout.HORIZONTAL);
        zoneGuest.setGravity(Gravity.CENTER_VERTICAL);
        zoneGuest.setPadding(dp(16), dp(4), dp(16), dp(4));
        ImageView gi = new ImageView(this);
        gi.setImageResource(R.drawable.ic_guest);
        zoneGuest.addView(gi, new LinearLayout.LayoutParams(dp(22), dp(22)));
        // «Invitado» y su frase, en una fila aparte alineada por la línea base de las letras (la zona la centra en vertical)
        LinearLayout gtxt = new LinearLayout(this);
        gtxt.setOrientation(LinearLayout.HORIZONTAL);
        gtxt.setBaselineAligned(true);
        TextView gt = text("Invitado", 15, Color.WHITE);
        gt.setTypeface(Ui.BLACK);
        gt.setIncludeFontPadding(false);
        gt.setPadding(dp(10), 0, dp(12), 0);
        gtxt.addView(gt, new LinearLayout.LayoutParams(-2, -2));
        TextView gs = text("Enseña en la tele el álbum de una visita", 12, 0xFFBBCCDD);
        gs.setSingleLine(true);
        gs.setIncludeFontPadding(false);
        gs.setPadding(0, 0, 0, 0);
        gtxt.addView(gs, new LinearLayout.LayoutParams(0, -2, 1f));
        zoneGuest.addView(gtxt, new LinearLayout.LayoutParams(0, -2, 1f));
        zoneGuest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { guestDialog(); }
        });

        LinearLayout rightTop = new LinearLayout(this);
        rightTop.setOrientation(LinearLayout.VERTICAL);
        if (Prefs.profilesOn(this)) {
            // con perfiles: el cuadro del QR, y a su derecha un icono cuadrado con el nombre del perfil en uso
            LinearLayout qrRow = new LinearLayout(this);
            qrRow.setOrientation(LinearLayout.HORIZONTAL);
            qrRow.addView(zoneQr, new LinearLayout.LayoutParams(0, -2, 1f));
            profileTile = profileTile();
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(104), -1);
            tp.setMargins(dp(10), 0, 0, 0);
            qrRow.addView(profileTile, tp);
            rightTop.addView(qrRow, new LinearLayout.LayoutParams(-1, -2));
        } else rightTop.addView(zoneQr, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams zgp = new LinearLayout.LayoutParams(-1, dp(44));
        zgp.setMargins(0, dp(14), 0, 0);   // mismo hueco de 14 dp que entre columnas y hasta «Tus álbumes»
        rightTop.addView(zoneGuest, zgp);
        LinearLayout.LayoutParams c1p = new LinearLayout.LayoutParams(0, -2, 0.50f);
        c1p.setMargins(dp(7), 0, 0, 0);
        row1.addView(rightTop, c1p);
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
        preview = side("Vista previa", R.drawable.ic_preview, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, PreviewActivity.class)); }
        });
        Button dream = side("Elegir salvapantallas", R.drawable.ic_tv, new View.OnClickListener() {
            public void onClick(View v) { openDreamSettings(); }
        });
        idleBtn = side(idleLabel(), R.drawable.ic_idle_start, new View.OnClickListener() {
            public void onClick(View v) { cycleIdle(); }
        });
        stopBtn = side(stopLabel(), R.drawable.ic_idle_stop, new View.OnClickListener() {
            public void onClick(View v) {
                int[] o = {5, 10, 15, 30, 60, 120, 0};
                int cur = Prefs.autoStopMin(SettingsActivity.this), nx = o[0];
                for (int i = 0; i < o.length; i++) if (o[i] == cur) nx = o[(i + 1) % o.length];
                Prefs.sp(SettingsActivity.this).edit().putInt("autoStopMin", nx).commit();
                stopBtn.setText(stopLabel());
            }
        });
        vqBtn = side(vqLabel(), R.drawable.ic_hd, new View.OnClickListener() {
            public void onClick(View v) {
                String q = Prefs.vq(SettingsActivity.this);
                Prefs.sp(SettingsActivity.this).edit().putString("vq", "auto".equals(q) ? "hd" : "hd".equals(q) ? "sd" : "auto").commit();
                vqBtn.setText(vqLabel());
            }
        });
        Button proj = side("Fondo para Projectivy", R.drawable.ic_wallpaper, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, ProjectivyActivity.class)); }
        });
        Button check = side("Recargar álbumes", R.drawable.ic_check, new View.OnClickListener() {
            public void onClick(View v) { checkAlbum(); }
        });
        cacheBtn = side("Vaciar caché", R.drawable.ic_sweep, new View.OnClickListener() {
            public void onClick(View v) { clearCache(); }
        });
        Button upd = side("Actualizar", R.drawable.ic_download, new View.OnClickListener() {
            public void onClick(View v) { updateFlow(); }
        });
        // pantalla principal: solo lo de uso diario. El resto, en la pantalla «Ajustes»
        Button places = side("Lugares", R.drawable.ic_place, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, PlacesActivity.class)); }
        });
        Button ajBtn = side("Ajustes", R.drawable.ic_tune, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, AjustesActivity.class)); }
        });
        left.addView(pair(preview, browse));
        left.addView(pair(places, check));
        left.addView(pair(idleBtn, stopBtn));
        // funciones extra que añaden botones (solo si están activadas)
        List<Button> fx = new ArrayList<Button>();
        if (Extras.on(this, Extras.TRIPS)) fx.add(side("Viajes", R.drawable.ic_flight, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, TripsActivity.class)); }
        }));
        if (Extras.on(this, Extras.STATS)) fx.add(side("Estadísticas", R.drawable.ic_stats, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, StatsActivity.class)); }
        }));
        for (int i = 0; i < fx.size(); i += 2) left.addView(i + 1 < fx.size() ? pair(fx.get(i), fx.get(i + 1)) : single(fx.get(i)));
        extrasSig = extrasSig();
        LinearLayout lastRow = pair(upd, ajBtn);
        ((LinearLayout.LayoutParams) lastRow.getLayoutParams()).setMargins(0, 0, 0, 0);
        left.addView(lastRow);
        LinearLayout.LayoutParams leftP = new LinearLayout.LayoutParams(0, -1, 0.50f);
        leftP.setMargins(0, 0, dp(7), 0);
        row2.addView(left, leftP);

        LinearLayout c2 = card();
        c2.addView(cardTitle("Tus álbumes"));
        msgView = text("", 15, Color.WHITE);
        msgView.setPadding(dp(8), dp(2), dp(8), dp(2));
        msgView.setVisibility(View.GONE);
        c2.addView(msgView);
        geoView = text("", 13, 0xFF9FB3C8);
        geoView.setPadding(dp(8), dp(4), dp(8), dp(4));
        geoView.setFocusable(true);
        geoView.setClickable(true);
        geoView.setBackground(svBgFocus());
        geoView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                GeoIndex.toggle(SettingsActivity.this);
                h.removeCallbacks(geoTick);
                h.post(geoTick);
            }
        });
        geoView.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { rebuildNamesDialog(); return true; }
        });
        c2.addView(geoView);
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
        LinearLayout.LayoutParams c2p = new LinearLayout.LayoutParams(0, -1, 0.50f);
        c2p.setMargins(dp(7), 0, 0, 0);
        row2.addView(c2, c2p);
        LinearLayout.LayoutParams r2p = new LinearLayout.LayoutParams(-1, 0, 1f);
        r2p.setMargins(0, dp(14), 0, 0); // mismo hueco (14 dp) que entre las dos columnas
        root.addView(row2, r2p);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.setMargins(0, dp(4), 0, 0);
        root.addView(vrow, vp);

        // recorrido del mando por las dos zonas de arriba (no se deja a la geometría):
        // desde los botones, ↑ llega a «Invitado» y otro ↑ al QR; desde la lista de álbumes, ↑ también a «Invitado»
        zoneQr.setId(View.generateViewId());
        zoneGuest.setId(View.generateViewId());
        sv.setId(View.generateViewId());
        browse.setId(View.generateViewId());
        preview.setId(View.generateViewId());
        zoneQr.setNextFocusDownId(zoneGuest.getId());
        zoneQr.setNextFocusLeftId(browse.getId());
        if (profileTile != null) {
            profileTile.setId(View.generateViewId());
            zoneQr.setNextFocusRightId(profileTile.getId());
            profileTile.setNextFocusLeftId(zoneQr.getId());
            profileTile.setNextFocusDownId(zoneGuest.getId());
        }
        zoneGuest.setNextFocusUpId(zoneQr.getId());
        zoneGuest.setNextFocusDownId(sv.getId());
        zoneGuest.setNextFocusLeftId(browse.getId());
        sv.setNextFocusUpId(zoneGuest.getId());
        browse.setNextFocusUpId(zoneGuest.getId());
        preview.setNextFocusUpId(zoneGuest.getId());
        if (searchBar != null) {
            searchBar.setId(View.generateViewId());
            browse.setNextFocusUpId(searchBar.getId());
            preview.setNextFocusUpId(searchBar.getId());
            searchBar.setNextFocusDownId(preview.getId());
            searchBar.setNextFocusUpId(zoneQr.getId());
            searchBar.setNextFocusRightId(zoneGuest.getId());
        }

        setContentView(root);
        preview.requestFocus();
        updateStatus(null);
        if (Prefs.idleMin(this) > 0 && !idleServiceOn()) reenableIdleService();
    }

    private Button side(String label, int icon, View.OnClickListener l) {
        Button b = Ui.icon(Ui.button(this, label, l), icon);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
        return b;
    }

    /** Dos botones lado a lado, ocupando la misma altura que el resto de filas. */
    private void rebuildNamesDialog() {
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Rehacer los nombres de lugar")
            .setMessage("Se olvidan todos los nombres y se vuelven a calcular con las reglas actuales, en segundo plano. Puede tardar un buen rato (más de un segundo por cada sitio distinto).")
            .setPositiveButton("Rehacer", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    new Thread(new Runnable() { public void run() { GeoIndex.rebuildNames(SettingsActivity.this); } }).start();
                    h.removeCallbacks(geoTick);
                    h.postDelayed(geoTick, 1500);
                }
            })
            .setNegativeButton("Cancelar", null).show();
    }

    private String extrasSig;
    private View searchBar, profileTile;

    /** Icono cuadrado con el nombre del perfil en uso; al pulsarlo se pasa al siguiente (y cambian el PIN, el QR y los álbumes). */
    private View profileTile() {
        final int cur = Prefs.profile(this);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(6), dp(8), dp(6), dp(8));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x1AFFFFFF);
        off.setCornerRadius(dp(10));
        off.setStroke(dp(1), 0x33FFFFFF);
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x33FFFFFF);
        on.setCornerRadius(dp(10));
        on.setStroke(dp(3), 0xFF3D8BFD);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        t.setBackground(bg);
        ImageView ic = new ImageView(this);
        ic.setImageResource(Ui.profileIcon(Prefs.profileIndex(this, cur)));
        ic.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable sq = new GradientDrawable();
        sq.setColor(0xFF22364F);
        sq.setCornerRadius(dp(10));
        ic.setBackground(sq);
        t.addView(ic, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView nm = text(Prefs.profileName(this, cur), 13, Color.WHITE);
        nm.setTypeface(Ui.BLACK);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nm.setGravity(Gravity.CENTER);
        nm.setPadding(0, dp(6), 0, 0);
        t.addView(nm, new LinearLayout.LayoutParams(-1, -2));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                java.util.List<String[]> l = Prefs.profiles(SettingsActivity.this);
                if (l.size() < 2) {
                    Toast.makeText(SettingsActivity.this, "Solo hay el perfil «Familia». Añade más en Ajustes → Funciones extra → Perfiles.", Toast.LENGTH_LONG).show();
                    return;
                }
                int nx = (Prefs.profileIndex(SettingsActivity.this, cur) + 1) % l.size();
                Prefs.setProfile(SettingsActivity.this, Integer.parseInt(l.get(nx)[0]));
                Toast.makeText(SettingsActivity.this, "Perfil «" + l.get(nx)[1] + "»: sus álbumes, ajustes, PIN y web.", Toast.LENGTH_LONG).show();
                recreate();
            }
        });
        return t;
    }

    /** Qué botones extra hay que enseñar (si cambia al volver de Ajustes, la pantalla se rehace). */
    private String extrasSig() {
        return Extras.on(this, Extras.SEARCH) + "," + Extras.on(this, Extras.TRIPS) + "," + Extras.on(this, Extras.STATS)
                + "," + Prefs.profilesOn(this) + "," + Prefs.profile(this) + "," + Prefs.profiles(this).size();
    }

    /** Un botón solo en su fila (con el mismo alto que las demás). */
    private LinearLayout single(Button a) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.addView(a, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams lr = new LinearLayout.LayoutParams(-1, 0, 1f);
        lr.setMargins(0, 0, 0, dp(6));
        r.setLayoutParams(lr);
        return r;
    }

    private LinearLayout pair(Button a, Button b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, -1, 1f);
        la.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, -1, 1f);
        lb.setMargins(dp(6), 0, 0, 0);
        r.addView(a, la);
        r.addView(b, lb);
        LinearLayout.LayoutParams lr = new LinearLayout.LayoutParams(-1, 0, 1f);
        lr.setMargins(0, 0, 0, dp(6));
        r.setLayoutParams(lr);
        return r;
    }

    /** Zona de la pantalla principal que se puede elegir con el mando: caja como las tarjetas, con borde azul al seleccionarla. */
    private LinearLayout zone() {
        LinearLayout z = new LinearLayout(this);
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x99162A40);
        off.setCornerRadius(dp(8));
        off.setStroke(dp(1), 0xFF2C4766);
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xCC1B3558);
        on.setCornerRadius(dp(8));
        on.setStroke(dp(3), 0xFF3D8BFD);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{android.R.attr.state_pressed}, on);
        bg.addState(new int[]{}, off);
        z.setBackground(bg);
        z.setFocusable(true);
        z.setClickable(true);
        return z;
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
        t.setTypeface(Ui.BLACK);
        return t;
    }

    /** Dibuja el código QR de la dirección (blanco sobre fondo claro, con margen para que lo lea el móvil). */
    private void showQr(String url, boolean ok) {
        if (!ok) { qrView.setVisibility(View.GONE); return; }
        try {
            io.nayuki.qrcodegen.QrCode qr = io.nayuki.qrcodegen.QrCode.encodeText(url, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM);
            int n = qr.size, q = 3, px = 8, dim = (n + 2 * q) * px;
            int[] pix = new int[dim * dim];
            java.util.Arrays.fill(pix, Color.WHITE);
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                if (!qr.getModule(x, y)) continue;
                for (int dy = 0; dy < px; dy++) for (int dx = 0; dx < px; dx++)
                    pix[((y + q) * px + dy) * dim + (x + q) * px + dx] = 0xFF0A1424;
            }
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(pix, dim, dim, android.graphics.Bitmap.Config.ARGB_8888);
            qrView.setImageBitmap(bm);
            qrView.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            qrView.setVisibility(View.GONE);
        }
    }

    private TextView line(String s) {
        TextView t = text(s, 12, 0xFFBBCCDD);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    @Override protected void onStart() {
        super.onStart();
        if (extrasSig != null && !extrasSig.equals(extrasSig())) { recreate(); return; }
        Guest.purgeExpired(this);
        if ("folder".equals(Prefs.musicMode(this)) && android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !askedStorage) {
            askedStorage = true;
            try { requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 8); } catch (Throwable ignored) { }
        }
        h.removeCallbacks(guestTick);
        h.postDelayed(guestTick, 60000);
        refreshCache();
        GeoService.start(this);
        h.removeCallbacks(geoTick);
        h.post(geoTick);
        silentCheck();
        if (msgView.getVisibility() == View.VISIBLE && msgView.getText().toString().endsWith("\u2026")) startPulse();
        final WebConfigServer srv = new WebConfigServer(this, this);
        server = srv;
        webUrl = null;
        pinInfo.setText("");
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
                            webUrl = ip == null ? null : "http://" + ip + ":" + srv.port;
                            webInfo.setText("http://" + (ip == null ? "IP-de-la-Shield" : ip) + ":" + srv.port);
                            pinInfo.setText("PIN  " + Prefs.webPin(SettingsActivity.this));
                            // el QR lleva el PIN puesto: escaneándolo se entra directo
                            showQr(webUrl + "/?pin=" + Prefs.webPin(SettingsActivity.this), ip != null);
                        } else {
                            webInfo.setText("No se pudo abrir la web de configuración.");
                        }
                    }
                });
            }
        }).start();
    }

    @Override protected void onStop() {
        h.removeCallbacks(geoTick);
        webUrl = null;
        h.removeCallbacks(guestTick);
        stopPulse();
        if (server != null) { server.close(); server = null; }
        super.onStop();
    }

    @Override public void onGuest(final String id) {
        h.post(new Runnable() {
            public void run() {
                if (guestDlg != null) { try { guestDlg.dismiss(); } catch (Throwable ignored) { } guestDlg = null; }
                Guest.G g = Guest.find(SettingsActivity.this, id);
                AlbumFetcher.AlbumData d = g == null ? null : Guest.data(SettingsActivity.this, g);
                updateStatus(null);
                if (g == null || d == null || d.items.isEmpty()) return;
                for (Item it : d.items) { it.album = g.title(); if (it.akey.length() == 0) it.akey = d.key; }
                Session.items = d.items;
                Session.order = "random";
                Session.manual = false;
                Session.guest = true;
                Session.guestId = g.id;
                Session.startKey = null;
                Session.lastKey = null;
                Session.musicUrl = null;
                startActivity(new Intent(SettingsActivity.this, PlayerActivity.class));
            }
        });
    }

    private android.app.AlertDialog guestDlg;

    private android.graphics.Bitmap qrBitmap(String url) {
        io.nayuki.qrcodegen.QrCode qr = io.nayuki.qrcodegen.QrCode.encodeText(url, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM);
        int n = qr.size, q = 3, px = 8, dim = (n + 2 * q) * px;
        int[] pix = new int[dim * dim];
        java.util.Arrays.fill(pix, Color.WHITE);
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            if (!qr.getModule(x, y)) continue;
            for (int dy = 0; dy < px; dy++) for (int dx = 0; dx < px; dx++)
                pix[((y + q) * px + dy) * dim + (x + q) * px + dx] = 0xFF0A1424;
        }
        return android.graphics.Bitmap.createBitmap(pix, dim, dim, android.graphics.Bitmap.Config.ARGB_8888);
    }

    /** Zona del QR: lo enseña en grande, con la dirección y los pasos, para escanearlo desde el sofá. */
    private void qrDialog() {
        if (webUrl == null) {
            updateStatus("La web todavía no está lista. Prueba en unos segundos.");
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));
        ImageView qv = new ImageView(this);
        try { qv.setImageBitmap(qrBitmap(webUrl + "/?pin=" + Prefs.webPin(this))); } catch (Throwable ignored) { }
        box.addView(qv, new LinearLayout.LayoutParams(dp(210), dp(210)));
        TextView addr = text(webUrl, 20, 0xFF7CC4FF);
        addr.setTypeface(Ui.BLACK);
        addr.setPadding(0, dp(8), 0, dp(2));
        box.addView(addr);
        TextView pinv = text("PIN  " + Prefs.webPin(this), 20, Color.WHITE);
        pinv.setTypeface(Ui.BLACK);
        pinv.setPadding(0, 0, 0, dp(6));
        box.addView(pinv);
        TextView steps = text("1. En Google Fotos: álbum → Compartir → Crear enlace → copiar.\n"
                + "2. Con el móvil en la misma wifi, escanea el código (entra solo) o abre la dirección y escribe el PIN.\n"
                + "3. Pega los enlaces y elige «FotosTV» como salvapantallas.", 13, 0xFFD0DBE8);
        box.addView(steps);
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Configúralo desde el móvil")
            .setView(sc)
            .setPositiveButton("Cerrar", null)
            .show();
    }

    /** Botón «Invitado»: código QR de corta duración para que alguien enseñe su álbum en la tele. */
    private void guestDialog() {
        String ip = localIp();
        if (server == null || server.port == 0 || ip == null) {
            updateStatus("La web todavía no está lista. Prueba en unos segundos.");
            return;
        }
        String tok = Guest.newToken(this);
        String url = "http://" + ip + ":" + server.port + "/g/" + tok;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView t1 = text("Escanea el código con el móvil y pega el enlace de tu álbum compartido de Google Fotos. "
                + "Se verá aquí 2 horas y luego desaparece solo, sin dejar rastro.", 14, 0xFFD0DBE8);
        t1.setPadding(0, 0, 0, dp(10));
        box.addView(t1);
        ImageView qv = new ImageView(this);
        try { qv.setImageBitmap(qrBitmap(url)); } catch (Throwable ignored) { }
        box.addView(qv, new LinearLayout.LayoutParams(dp(190), dp(190)));
        TextView t2 = text("El código vale 15 minutos.", 12, 0xFF9FB3C8);
        t2.setPadding(0, dp(8), 0, 0);
        box.addView(t2);
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Invitado")
            .setView(box)
            .setPositiveButton("Cerrar", null);
        if (Guest.any(this)) {
            b.setNeutralButton("Quitar todos ahora", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    for (Guest.G g : Guest.list(SettingsActivity.this)) Guest.remove(SettingsActivity.this, g.id);
                    Guest.revokeToken(SettingsActivity.this);
                    updateStatus(null);
                }
            });
        }
        guestDlg = b.create();
        guestDlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface d) { guestDlg = null; }
        });
        guestDlg.show();
    }

    private final Runnable guestTick = new Runnable() {
        public void run() {
            Guest.purgeExpired(SettingsActivity.this);
            if (msgView.getVisibility() != View.VISIBLE) updateStatus(null);
            h.postDelayed(this, 60000);
        }
    };

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
                List<Guest.G> guests = Guest.list(SettingsActivity.this);
                for (Guest.G g : guests) {
                    int gs = sb.length();
                    sb.append("\u25F7 " + g.title() + "  \u2014  temporal, quedan " + Guest.left(g.left()) + "\n");
                    sb.setSpan(new ForegroundColorSpan(0xFFFFB454), gs, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (!guests.isEmpty()) sb.append("\n");
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
        updateStatus("Recargando álbumes…");
        new Thread(new Runnable() {
            public void run() {
                int ok = 0;
                String err = null;
                // también se vuelve a buscar la ubicación de las fotos que estaban guardadas sin ella
                final int regps = MetaFetcher.forgetMissingGps(SettingsActivity.this);
                for (Prefs.Album al : albums) {
                    try {
                        Prefs.saveAlbumCache(SettingsActivity.this, al.url, AlbumFetcher.fetchAlbum(al.url));
                        ok++;
                    } catch (Exception e) {
                        err = (al.name.length() > 0 ? al.name : al.url) + ": " + e.getMessage();
                    }
                }
                final String m = albums.isEmpty() ? null
                        : err == null ? "\u2714 Álbumes recargados (" + ok + ")."
                            + (regps > 0 ? " Se vuelve a buscar la ubicación de " + regps + " fotos." : "")
                        : "\u2716 " + err;
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
    private static final int[] IDLE_OPTS = {0, 1, 2, 5, 10, 15, 30};

    private Button vqBtn, stopBtn, preview;

    private String stopLabel() {
        int m = Prefs.autoStopMin(this);
        return "Cerrar pase: " + (m == 0 ? "Nunca" : m >= 60 ? (m / 60) + (m == 60 ? " hora" : " horas") : m + " min");
    }

    private String vqLabel() {
        String q = Prefs.vq(this);
        return "Vídeos: " + ("hd".equals(q) ? "Calidad alta" : "sd".equals(q) ? "Calidad media" : "Calidad automática");
    }

    private String idleLabel() {
        int m = Prefs.idleMin(this);
        return m == 0 ? "Inicio automático: No" : "Inicio automático: " + m + " min";
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
    /** Pasos para dar el permiso una sola vez (no se puede hacer desde la propia app). */
    private void idleHelp() {
        String ip = localIp();
        String msg = "Para que arranque solo hace falta dar un permiso una vez, desde un ordenador:\n\n"
            + "1. En la tele: Ajustes \u2192 Mi Fire TV (o Preferencias del dispositivo) \u2192 Opciones de desarrollador \u2192 activar «Depuración ADB».\n\n"
            + "2. En el ordenador, con ADB instalado, escribe:\n"
            + "   adb connect " + (ip != null ? ip : "IP-DE-LA-TELE") + ":5555\n\n"
            + "3. Después:\n"
            + "   adb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS\n\n"
            + "4. Vuelve a abrir FotosTV y pulsa otra vez «Inicio automático». Ya no hace falta repetirlo.\n\n"
            + "Si ya tienes el permiso dado, activa el servicio a mano en Accesibilidad.";
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Falta un permiso")
            .setMessage(msg)
            .setPositiveButton("Abrir Accesibilidad", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Throwable ignored) { }
                }
            })
            .setNegativeButton("Cerrar", null)
            .show();
    }

    private void cycleIdle() {
        int cur = Prefs.idleMin(this), next = IDLE_OPTS[0];
        for (int i = 0; i < IDLE_OPTS.length; i++) if (IDLE_OPTS[i] == cur) next = IDLE_OPTS[(i + 1) % IDLE_OPTS.length];
        Prefs.sp(this).edit().putInt("idleMin", next).commit();
        idleBtn.setText(idleLabel());
        if (next > 0 && !idleServiceOn() && !reenableIdleService()) {
            idleHelp();
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

    private android.graphics.drawable.Drawable svBgFocus() {
        GradientDrawable on = new GradientDrawable();
        on.setColor(0x00000000);
        on.setCornerRadius(dp(5));
        on.setStroke(dp(3), 0xFF3D8BFD);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0x00000000));
        return bg;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(Ui.REG);
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
