package com.yuskilabs.fotostv;

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

    InfoPanel(Context c, Item it, String album) {
        super(c);
        this.item = it;
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFA0A1424, 0xFA14305A}));
        setClickable(true);
        LinearLayout main = new LinearLayout(c);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setPadding(dp(44), dp(18), dp(44), dp(12));
        addView(main, new FrameLayout.LayoutParams(-1, -1));

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
        TextView hint = text("Pulsa cualquier botón para volver", 12, 0xFF7C93AD);
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
        load();
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
            TextView p = text(place, 18, Color.WHITE);
            p.setTypeface(Ui.BLACK);
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
