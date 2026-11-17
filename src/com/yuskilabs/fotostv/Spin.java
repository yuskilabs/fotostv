package com.yuskilabs.fotostv;



/** Icono que gira mientras se recarga. */
class Spin extends android.graphics.drawable.Drawable {
    final android.graphics.drawable.Drawable d;
    float angle;
    Spin(android.graphics.drawable.Drawable d) { this.d = d; }
    @Override public void draw(android.graphics.Canvas c) {
        android.graphics.Rect b = getBounds();
        c.save();
        c.rotate(angle, b.exactCenterX(), b.exactCenterY());
        d.setBounds(b);
        d.draw(c);
        c.restore();
    }
    @Override public void setAlpha(int a) { d.setAlpha(a); }
    @Override public void setColorFilter(android.graphics.ColorFilter f) { d.setColorFilter(f); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
