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

/** Pantalla de información de una foto: metadatos a un lado y, si tiene GPS, mapa con el punto exacto al otro. */
class InfoPanel extends FrameLayout {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Item item;
    private final LinearLayout rows;
    private final FrameLayout mapBox;
    private boolean dead;

    private final boolean actions;
    private final Runnable closer;
    private LinearLayout bar;
    private String placeName;

    InfoPanel(Context c, Item it, String album) { this(c, it, album, null); }

    /** Con «closer» la ficha lleva botones (ir al álbum, pase del álbum o del lugar, volver); sin él, cualquier botón la cierra. */
    InfoPanel(Context c, Item it, String album, Runnable closer) {
        super(c);
        this.item = it;
        this.closer = closer;
        this.actions = closer != null;
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFA0A1424, 0xFA14305A}));
        setClickable(true);
        LinearLayout main = new LinearLayout(c);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setPadding(dp(44), dp(18), dp(44), dp(12));
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (actions) {
            bar = new LinearLayout(c);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setFillViewport(true);
            hs.addView(bar);
            hs.setPadding(dp(44), 0, dp(44), dp(14));
            wrap.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        }
        addView(wrap, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(icon(R.drawable.ic_info, 28, 0xFFFFFFFF));
        TextView t = text("Información", 22, Color.WHITE);
        t.setTypeface(Ui.BLACK);
        t.setPadding(dp(12), 0, 0, 0);
        head.addView(t);
        left.addView(head);
        View sep = new View(c);
        sep.setBackgroundColor(0xFF3A506B);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(1));
        sp.setMargins(0, dp(6), 0, dp(4));
        left.addView(sep, sp);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        sv.addView(rows);
        left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = text(closer != null ? "Atrás: volver al pase" : "Pulsa cualquier botón para volver", 12, 0xFF7C93AD);
        left.addView(hint);
        main.addView(left, new LinearLayout.LayoutParams(0, -1, 0.45f));

        mapBox = new FrameLayout(c);
        GradientDrawable mb = new GradientDrawable();
        mb.setColor(0x66162A40);
        mb.setCornerRadius(dp(8));
        mb.setStroke(dp(1), 0xFF2C4766);
        mapBox.setBackground(mb);
        mapBox.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -1, 0.55f);
        mp.setMargins(dp(32), 0, 0, 0);
        main.addView(mapBox, mp);

        // lo que ya se sabe sin descargar nada
        if (it.ts > 0) {
            addRow(R.drawable.ic_event, "Fecha", new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", MetaFetcher.ES).format(new Date(it.ts)));
        }
        if (album != null && album.length() > 0) addRow(R.drawable.ic_albums, "Álbum", album);
        mapMessage("Cargando información…");
        if (actions) buildBar();
        load();
    }

    private android.widget.Button barBtn(String label, int icon, View.OnClickListener l) {
        android.widget.Button b = Ui.button(getContext(), label, l);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (icon != 0) Ui.icon(b, icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(10), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildBar() {
        bar.removeAllViews();
        final Context c = getContext();
        android.widget.Button first = barBtn("Ir al álbum", R.drawable.ic_albums, new View.OnClickListener() {
            public void onClick(View v) { Nav.run(c, Nav.ALBUM, item, null); }
        });
        bar.addView(first);
        bar.addView(barBtn("Pase de este álbum", R.drawable.ic_play, new View.OnClickListener() {
            public void onClick(View v) { Nav.run(c, Nav.ALBUM_PASE, item, null); }
        }));
        final String pl = placeName;
        if (pl != null && pl.length() > 0) {
            bar.addView(barBtn("Más de " + Nav.townOf(pl), R.drawable.ic_place, new View.OnClickListener() {
                public void onClick(View v) { Nav.run(c, Nav.PLACE_TOWN, item, pl); }
            }));
            final String rg = Nav.regionOf(pl);
            if (rg != null) bar.addView(barBtn("Más de " + rg, R.drawable.ic_place, new View.OnClickListener() {
                public void onClick(View v) { Nav.run(c, Nav.PLACE_REGION, item, pl); }
            }));
        }
        if (pl != null && pl.length() > 0) bar.addView(barBtn("Recalcular lugar", R.drawable.ic_refresh, new View.OnClickListener() {
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
        bar.addView(barBtn("Volver", R.drawable.ic_close, new View.OnClickListener() {
            public void onClick(View v) { if (closer != null) closer.run(); }
        }));
        final android.widget.Button f = first;
        post(new Runnable() { public void run() { if (!dead) f.requestFocus(); } });
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
        if (actions && place != null && !place.equals(placeName)) { placeName = place; buildBar(); }
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
        if (Prefs.favs(getContext()).contains(item.key())) addRow(R.drawable.ic_star, "Favorita", "Sí");
        if (Double.isNaN(d.lat)) mapMessage("Esta foto no tiene ubicación GPS");
    }

    private void showMap(Bitmap bm, MetaFetcher.Details d, String place) {
        mapBox.removeAllViews();
        if (bm == null) {
            mapMessage(String.format(Locale.US, "No se pudo cargar el mapa\n%.5f, %.5f", d.lat, d.lon));
            return;
        }
        ImageView iv = new ImageView(getContext());
        iv.setImageBitmap(bm);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mapBox.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout cap = new LinearLayout(getContext());
        cap.setOrientation(LinearLayout.VERTICAL);
        cap.setPadding(dp(14), dp(8), dp(14), dp(8));
        cap.setBackgroundColor(0xCC0A1424);
        if (place != null) {
            TextView p = text(place, 24, Color.WHITE);
            p.setTypeface(Ui.HAND);
            cap.addView(p);
        }
        cap.addView(text("© colaboradores de OpenStreetMap", 11, 0xFF9FB3C8));
        mapBox.addView(cap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
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
