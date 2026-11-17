package com.yuskilabs.fotostv;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Construye las cuadrículas y las casillas (álbum y foto) del explorador. */
final class Tiles {
    private Tiles() {}

    // ------------------------------------------------------------ cuadrícula
    static GridView makeGrid(android.content.Context ctx, int gap, int margin, int cols) {
        GridView g = new GridView(ctx);
        g.setNumColumns(cols);
        g.setHorizontalSpacing(gap);
        g.setVerticalSpacing(gap);
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setPadding(margin, gap, margin, margin);
        g.setClipToPadding(false);
        g.setVerticalScrollBarEnabled(false);
        g.setCacheColorHint(Color.TRANSPARENT);
        g.setFocusable(true);
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x22FFFFFF);
        sel.setStroke(Ui.dp(ctx, 4), Color.WHITE);
        sel.setCornerRadius(0);   // miniaturas cuadradas: recuadro con esquinas rectas
        final GradientDrawable selOn = sel;
        final android.graphics.drawable.ColorDrawable selOff = new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
        g.setSelector(sel);
        g.setDrawSelectorOnTop(true);
        // al subir a los botones, el recuadro blanco de la foto desaparece para que se vea dónde está el foco
        g.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                ((GridView) v).setSelector(has ? selOn : selOff);
                v.invalidate();
            }
        });
        return g;
    }

    static Tile albumTile(android.content.Context ctx, int albumTileH) {
        Tile t = new Tile(ctx, 0.66f);
        t.setBackgroundColor(0xFF1B2D44);
        t.img = new ImageView(ctx);
        t.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(t.img, new FrameLayout.LayoutParams(-1, -1));
        t.shade = new View(ctx);
        t.shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0xA6000000, 0x66000000, 0x2B000000, 0x00000000}));
        t.addView(t.shade, new FrameLayout.LayoutParams(-1, albumTileH * 6 / 10, Gravity.BOTTOM));
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 4));
        t.name = BrowserUi.text(ctx, "", 16, Color.WHITE);
        t.name.setPadding(0, 0, 0, 0);
        t.name.setSingleLine(true);
        t.name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.name.setTypeface(Ui.BLACK);
        t.name.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        t.count = BrowserUi.text(ctx, "", 13, 0xFFD0DBE8);
        t.count.setPadding(0, 0, 0, 0);
        t.count.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        box.addView(t.name);
        box.addView(t.count);
        t.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        t.pin = new ImageView(ctx);
        t.pin.setImageResource(R.drawable.ic_move);
        t.pin.setColorFilter(Color.WHITE);
        GradientDrawable mbg = new GradientDrawable();
        mbg.setShape(GradientDrawable.OVAL);
        mbg.setColor(0xCC3D8BFD);
        t.pin.setBackground(mbg);
        int mp = Ui.dp(ctx, 8);
        t.pin.setPadding(mp, mp, mp, mp);
        t.addView(t.pin, new FrameLayout.LayoutParams(Ui.dp(ctx, 48), Ui.dp(ctx, 48), Gravity.CENTER));
        t.pin.setVisibility(View.GONE);
        return t;
    }

    static Tile photoTile(android.content.Context ctx, float photoRatio) {
        Tile t = new Tile(ctx, photoRatio);
        t.setBackgroundColor(0xFF1B2D44);
        t.img = new ImageView(ctx);
        t.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(t.img, new FrameLayout.LayoutParams(-1, -1));
        // marca de vídeo: círculo oscuro con ▶ abajo a la izquierda
        t.badge = new TextView(ctx);
        t.badge.setTypeface(Ui.BLACK);
        t.badge.setText("▶");
        t.badge.setTextColor(Color.WHITE);
        t.badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.badge.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xB3000000);
        circle.setStroke(Ui.dp(ctx, 2), Color.WHITE);
        t.badge.setBackground(circle);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.dp(ctx, 36), Ui.dp(ctx, 36),
                Gravity.BOTTOM | Gravity.START);
        bp.setMargins(Ui.dp(ctx, 8), 0, 0, Ui.dp(ctx, 8));
        t.addView(t.badge, bp);
        t.badge.setVisibility(View.GONE);
        t.fav = new ImageView(ctx);
        t.fav.setImageResource(R.drawable.ic_star);
        t.fav.setColorFilter(0xFFFFC83D);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24), Gravity.TOP | Gravity.END);
        fp.setMargins(0, Ui.dp(ctx, 6), Ui.dp(ctx, 6), 0);
        t.addView(t.fav, fp);
        t.fav.setVisibility(View.GONE);
        t.pin = new ImageView(ctx);
        t.pin.setImageResource(R.drawable.ic_photo);
        t.pin.setColorFilter(Color.WHITE);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setShape(GradientDrawable.OVAL);
        pbg.setColor(0xB3000000);
        t.pin.setBackground(pbg);
        int pp = Ui.dp(ctx, 5);
        t.pin.setPadding(pp, pp, pp, pp);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(Ui.dp(ctx, 28), Ui.dp(ctx, 28), Gravity.TOP | Gravity.START);
        pl.setMargins(Ui.dp(ctx, 6), Ui.dp(ctx, 6), 0, 0);
        t.addView(t.pin, pl);
        t.pin.setVisibility(View.GONE);
        t.fresh = new TextView(ctx);                 // «NUEVA» (función extra)
        t.fresh.setTypeface(Ui.BLACK);
        t.fresh.setText("NUEVA");
        t.fresh.setTextColor(0xFF0A1424);
        t.fresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.fresh.setPadding(Ui.dp(ctx, 7), Ui.dp(ctx, 2), Ui.dp(ctx, 7), Ui.dp(ctx, 2));
        GradientDrawable nb = new GradientDrawable();
        nb.setColor(0xFF7CE08A);
        nb.setCornerRadius(Ui.dp(ctx, 9));
        t.fresh.setBackground(nb);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        nl.setMargins(0, 0, Ui.dp(ctx, 6), Ui.dp(ctx, 6));
        t.addView(t.fresh, nl);
        t.fresh.setVisibility(View.GONE);
        return t;
    }
}
