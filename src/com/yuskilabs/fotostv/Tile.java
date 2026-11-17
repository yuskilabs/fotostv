package com.yuskilabs.fotostv;

import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

/** Casilla con proporción fija (alto = ancho × ratio) para las cuadrículas. */
class Tile extends FrameLayout {
    final float ratio;
    ImageView img;
    TextView name, count, badge, fresh;
    ImageView fav, pin;
    View shade;

    Tile(android.content.Context c, float ratio) {
        super(c);
        this.ratio = ratio;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((int) (w * ratio), MeasureSpec.EXACTLY));
    }
}
