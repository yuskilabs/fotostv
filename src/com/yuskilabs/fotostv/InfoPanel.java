package com.yuskilabs.fotostv;

import java.util.List;
import java.util.ArrayList;
import android.widget.Toast;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pantalla de información de una foto: metadatos a la izquierda y mapa a la derecha; con botones debajo de cada lado. */
class InfoPanel extends FrameLayout {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Item item;
    private final LinearLayout rows;
    private final FrameLayout mapBox;
    private boolean dead;

    private final boolean actions;
    private final Runnable closer, hider;
    private LinearLayout leftBtns, rightBtns;
    private MapView map;
    private android.widget.Button zin, zout, favBtn;
    private String placeName;
    private double lat = Double.NaN, lon = Double.NaN;
    private final GradientDrawable mapBg = new GradientDrawable();

    InfoPanel(Context c, Item it, String album) { this(c, it, album, null, null); }

    /** Con «closer» la ficha lleva botones (ir al álbum, pase, favoritos, ocultar, más del lugar…); sin él, cualquier botón la cierra. */
    InfoPanel(Context c, Item it, String album, Runnable closer, Runnable hider) {
        super(c);
        this.item = it;
        this.closer = closer;
        this.hider = hider;
        this.actions = closer != null;
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFA0A1424, 0xFA14305A}));
        setClickable(true);
        LinearLayout main = new LinearLayout(c);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setPadding(dp(44), dp(16), dp(44), dp(12));
        addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(R.drawable.ic_info, 26, 0xFFFFFFFF));
        TextView t = text("Información", 21, Color.WHITE);
        t.setTypeface(Ui.BLACK);
        t.setPadding(dp(12), 0, 0, 0);
        head.addView(t);
        left.addView(head);
        View sep = new View(c);
        sep.setBackgroundColor(0xFF3A506B);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(1));
        sp.setMargins(0, dp(4), 0, dp(3));
        left.addView(sep, sp);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        sv.addView(rows);
        left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (actions) {
            leftBtns = new LinearLayout(c);
            leftBtns.setOrientation(LinearLayout.VERTICAL);
            leftBtns.setPadding(0, dp(6), 0, 0);
            left.addView(leftBtns, new LinearLayout.LayoutParams(-1, -2));
        } else {
            left.addView(text("Pulsa cualquier botón para volver", 12, 0xFF7C93AD));
        }
        main.addView(left, new LinearLayout.LayoutParams(0, -1, 0.5f));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        mapBox = new FrameLayout(c);
        mapBg.setColor(0x66162A40);
        mapBg.setCornerRadius(dp(8));
        mapBg.setStroke(dp(1), 0xFF2C4766);
        mapBox.setBackground(mapBg);
        mapBox.setPadding(dp(3), dp(3), dp(3), dp(3));
        right.addView(mapBox, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (actions) {
            rightBtns = new LinearLayout(c);
            rightBtns.setOrientation(LinearLayout.VERTICAL);
            rightBtns.setPadding(0, dp(6), 0, 0);
            right.addView(rightBtns, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -1, 0.5f);
        mp.setMargins(dp(5), 0, 0, 0);
        main.addView(right, mp);

        // lo que ya se sabe sin descargar nada
        if (it.ts > 0) {
            addRow(R.drawable.ic_event, "Fecha", MetaFetcher.formatDateTime(it.ts, MetaFetcher.tzFor(c, it)));
        }
        if (album != null && album.length() > 0) addRow(R.drawable.ic_albums, "Álbum", album);
        String cap = Extras.caption(c, it.key());
        if (cap.length() > 0) addRow(R.drawable.ic_notes, "Pie de foto", cap);
        mapMessage("Cargando información…");
        if (actions) { buildLeft(); buildRight(); }
        load();
    }

    /** Botón de la ficha: ancho de su columna, icono pequeño, texto a la izquierda y, si es largo, en dos líneas. */
    private android.widget.Button barBtn(String label, int icon, View.OnClickListener l) {
        android.widget.Button b = Ui.button(getContext(), label, l);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(5), dp(6), dp(5));
        if (icon != 0) setIcon(b, icon);
        return b;
    }

    /** Mismo icono y tamaño que en la pantalla principal y en Ajustes. */
    private void setIcon(android.widget.Button b, int icon) { Ui.icon(b, icon); }

    /** Los botones van de dos en dos: cada fila tiene dos botones del mismo ancho. */
    private void addBtn(LinearLayout col, android.widget.Button b) {
        LinearLayout row = null;
        int n = col.getChildCount();
        if (n > 0) {
            LinearLayout last = (LinearLayout) col.getChildAt(n - 1);
            if (last.getChildCount() < 2) row = last;
        }
        if (row == null) {
            row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(56));
            rp.setMargins(0, 0, 0, dp(5));
            rp.height = dp(56);   // misma altura en los ocho: caben dos líneas
            col.addView(row, rp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        if (row.getChildCount() == 1) lp.setMargins(dp(5), 0, 0, 0);
        row.addView(b, lp);
    }

    /** Debajo de «Información»: ir al álbum, pase de este álbum, favoritos y ocultar. */
    private void buildLeft() {
        leftBtns.removeAllViews();
        final Context c = getContext();
        final android.widget.Button first = barBtn("Ir al álbum", R.drawable.ic_albums, new View.OnClickListener() {
            public void onClick(View v) { Nav.run(c, Nav.ALBUM, item, null); }
        });
        addBtn(leftBtns, first);
        addBtn(leftBtns, barBtn("Pase de este álbum", R.drawable.ic_play, new View.OnClickListener() {
            public void onClick(View v) { Nav.run(c, Nav.ALBUM_PASE, item, null); }
        }));
        favBtn = barBtn("", 0, new View.OnClickListener() {
            public void onClick(View v) {
                boolean fav = Prefs.favs(c).contains(item.key());
                Prefs.favAll(c, java.util.Collections.singleton(item.key()), !fav);
                Toast.makeText(c, fav ? "Quitada de favoritos." : "Añadida a favoritos.", Toast.LENGTH_SHORT).show();
                refreshFav();
                load();
            }
        });
        addBtn(leftBtns, favBtn);
        refreshFav();
        addBtn(leftBtns, barBtn("Ocultar", R.drawable.ic_visibility_off, new View.OnClickListener() {
            public void onClick(View v) { if (hider != null) hider.run(); }
        }));
        post(new Runnable() { public void run() { if (!dead) first.requestFocus(); } });
    }

    private void refreshFav() {
        if (favBtn == null) return;
        boolean fav = Prefs.favs(getContext()).contains(item.key());
        favBtn.setText(fav ? "Quitar de favoritos" : "Añadir a favoritos");
        setIcon(favBtn, fav ? R.drawable.ic_star : R.drawable.ic_star_border);
    }

    /** Debajo del mapa: más de la localidad y de su provincia/isla/país (o de la zona de 1 km y del sitio), más de este día y recargar el lugar. */
    private void buildRight() {
        rightBtns.removeAllViews();
        final Context c = getContext();
        final String pl = placeName;
        if (pl != null && pl.length() > 0) {
            final String rg = Nav.regionOf(pl);
            if (rg != null) {
                addBtn(rightBtns, barBtn("Más de " + Nav.townOf(pl), R.drawable.ic_place, new View.OnClickListener() {
                    public void onClick(View v) { Nav.run(c, Nav.PLACE_TOWN, item, pl); }
                }));
                addBtn(rightBtns, barBtn("Más de " + rg, R.drawable.ic_place, new View.OnClickListener() {
                    public void onClick(View v) { Nav.run(c, Nav.PLACE_REGION, item, pl); }
                }));
            } else {
                // ciudad o sitio sin provincia: la zona (1 km) y el sitio entero
                if (!Double.isNaN(lat)) addBtn(rightBtns, barBtn("Más de la zona", R.drawable.ic_place, new View.OnClickListener() {
                    public void onClick(View v) { Nav.runZone(c, item, lat, lon, 1.0); }
                }));
                addBtn(rightBtns, barBtn("Más de " + pl, R.drawable.ic_place, new View.OnClickListener() {
                    public void onClick(View v) { Nav.run(c, Nav.PLACE_TOWN, item, pl); }
                }));
            }
        }
        if (item.ts > 0) addBtn(rightBtns, barBtn("Más de este día", R.drawable.ic_event, new View.OnClickListener() {
            public void onClick(View v) { Nav.runDay(c, item); }
        }));
        addBtn(rightBtns, barBtn("Recargar lugar", R.drawable.ic_refresh, new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(c, "Recalculando el lugar…", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    public void run() {
                        List<Item> l = new ArrayList<Item>();
                        l.add(item);
                        try { GeoIndex.recheck(c, l); } catch (Throwable ignored) { }
                        post(new Runnable() { public void run() { if (!dead) load(); } });
                    }
                }).start();
            }
        }));
    }

    /** Atrás con el foco en el mapa: lo saca del mapa (a «Acercar»). Devuelve true si lo ha gestionado. */
    boolean handleBack() {
        if (map != null && map.hasFocus() && zin != null) { zin.requestFocus(); return true; }
        return false;
    }

    void detach() {
        dead = true;
        if (getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) getParent()).removeView(this);
    }

    private void load() {
        final Context c = getContext();
        new Thread(new Runnable() {
            public void run() {
                final MetaFetcher.Details d = MetaFetcher.details(c, item);
                String fn = "";
                try { if (item.id.length() > 0 && item.akey.length() > 0) fn = AlbumFetcher.fileName(item.id, item.akey); }
                catch (Exception ignored) { }
                final String file = fn;
                final boolean gps = !Double.isNaN(d.lat);
                final String place = gps ? MetaFetcher.place(c, d.lat, d.lon) : null;
                h.post(new Runnable() {
                    public void run() { if (!dead) fill(d, file, place); }
                });
                if (gps) {
                    final Bitmap bm = MapTiles.render(d.lat, d.lon, 15);
                    h.post(new Runnable() {
                        public void run() { if (!dead) showMap(bm, d, place); }
                    });
                }
            }
        }).start();
    }

    private String albumText() {
        return item.album == null ? "" : item.album;
    }

    private void fill(MetaFetcher.Details d, String file, String place) {
        rows.removeAllViews();
        if (file.length() > 0) addRow(R.drawable.ic_file, "Archivo", file);
        if (d.date > 0) {
            addRow(R.drawable.ic_event, "Fecha", new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", MetaFetcher.ES).format(new Date(d.date)));
            if (d.hasTime) addRow(R.drawable.ic_schedule, "Hora", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(d.date)));
        }
        if (place != null) addRow(R.drawable.ic_place, "Lugar", place);
        if (!Double.isNaN(d.lat)) { lat = d.lat; lon = d.lon; }
        if (actions && (place == null ? placeName != null : !place.equals(placeName)) || actions && rightBtns.getChildCount() == 0) { placeName = place; buildRight(); }
        if (!Double.isNaN(d.lat)) {
            String c = String.format(Locale.US, "%.5f, %.5f", d.lat, d.lon);
            if (!Double.isNaN(d.alt) && d.alt != 0) c += "  \u00B7  " + Math.round(d.alt) + " m";
            addRow(R.drawable.ic_place, "Coordenadas y altitud", c);
        }
        String cam = (d.make + " " + d.model).trim();
        if (d.model.toLowerCase(Locale.US).startsWith(d.make.toLowerCase(Locale.US)) && d.make.length() > 0) cam = d.model;
        if (cam.length() > 0) addRow(R.drawable.ic_camera, "Cámara", cam);
        StringBuilder ex = new StringBuilder();
        for (String p : new String[]{d.fnum, d.exposure, d.iso.length() > 0 ? "ISO " + d.iso : "", d.focal}) {
            if (p.length() == 0) continue;
            if (ex.length() > 0) ex.append("  ·  ");
            ex.append(p);
        }
        if (ex.length() > 0) addRow(R.drawable.ic_tune, "Exposición", ex.toString());
        if (d.w > 0 && d.h > 0) {
            double mp = d.w * (double) d.h / 1e6;
            addRow(R.drawable.ic_format, "Resolución", d.w + " × " + d.h + String.format(Locale.US, "  ·  %.1f MP", mp));
        }
        String a = albumText();
        if (a.length() > 0) addRow(R.drawable.ic_albums, "Álbum", a);
        String capt = Extras.caption(getContext(), item.key());
        if (capt.length() > 0) addRow(R.drawable.ic_notes, "Pie de foto", capt);
        if (Prefs.favs(getContext()).contains(item.key())) addRow(R.drawable.ic_star, "Favorita", "Sí");
        if (Double.isNaN(d.lat)) mapMessage("Esta foto no tiene ubicación GPS");
    }

    private void showMap(Bitmap bm, MetaFetcher.Details d, String place) {
        mapBox.removeAllViews();
        map = null;
        if (!actions) {
            if (bm == null) { mapMessage(String.format(Locale.US, "No se pudo cargar el mapa\n%.5f, %.5f", d.lat, d.lon)); return; }
            ImageView iv = new ImageView(getContext());
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mapBox.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout cap = new LinearLayout(getContext());
            cap.setOrientation(LinearLayout.VERTICAL);
            cap.setPadding(dp(14), dp(8), dp(14), dp(8));
            cap.setBackgroundColor(0xCC0A1424);
            if (place != null) { TextView p = text(place, 24, Color.WHITE); p.setTypeface(Ui.HAND); cap.addView(p); }
            cap.addView(text("© colaboradores de OpenStreetMap", 11, 0xFF9FB3C8));
            mapBox.addView(cap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            return;
        }
        map = new MapView(getContext());
        map.setMarker(d.lat, d.lon, 15);
        mapBox.addView(map, new FrameLayout.LayoutParams(-1, -1));
        if (place != null) {
            TextView p = text(place, 22, Color.WHITE);
            p.setTypeface(Ui.HAND);
            p.setPadding(dp(12), dp(4), dp(12), dp(4));
            p.setBackgroundColor(0xAA0A1424);
            mapBox.addView(p, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        }
        LinearLayout z = new LinearLayout(getContext());
        z.setOrientation(LinearLayout.HORIZONTAL);
        zin = zoomBtn("+", new View.OnClickListener() { public void onClick(View v) { if (map != null) map.zoomBy(1); } });
        zout = zoomBtn("\u2212", new View.OnClickListener() { public void onClick(View v) { if (map != null) map.zoomBy(-1); } });
        z.addView(zin, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(dp(46), dp(46));
        zp.setMargins(dp(5), 0, 0, 0);
        z.addView(zout, zp);
        FrameLayout.LayoutParams fz = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        fz.setMargins(dp(8), 0, 0, dp(8));
        mapBox.addView(z, fz);
    }

    /** Botón cuadrado de zoom con un solo signo. */
    private android.widget.Button zoomBtn(String t, View.OnClickListener l) {
        android.widget.Button b = Ui.button(getContext(), t, l);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void mapMessage(String msg) {
        mapBox.removeAllViews();
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView ic = icon(R.drawable.ic_place, 64, 0xFF3A506B);
        box.addView(ic);
        TextView t = text(msg, 18, 0xFF9FB3C8);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        mapBox.addView(box, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addRow(int icon, String label, String value) {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(2), 0, dp(2));
        r.addView(icon(icon, 20, 0xFF7CC4FF));
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        TextView l = text(label, 11, 0xFF7C93AD);
        l.setPadding(0, 0, 0, 0);
        TextView v = text(value, 15, Color.WHITE);
        v.setPadding(0, 0, 0, 0);
        col.addView(l);
        col.addView(v);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        rows.addView(r);
    }

    private ImageView icon(int res, int sizeDp, int tint) {
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(getContext());
        t.setTypeface(Ui.REG);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
