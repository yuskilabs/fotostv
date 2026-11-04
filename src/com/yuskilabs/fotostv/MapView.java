package com.yuskilabs.fotostv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mapa de OpenStreetMap con mapa de calor de tus fotos. Flechas: mover · OK: ver fotos de la mira (o acercar) · Play: acercar · Pausa: alejar. */
class MapView extends View {
    interface Pick { void box(double minLat, double maxLat, double minLon, double maxLon); }

    private static final int MIRA = 40;                 // radio de la mira, en píxeles
    private final List<double[]> pts = new ArrayList<double[]>();   // lat, lon, fotos
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(150);
    private final HashSet<String> pending = new HashSet<String>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();
    private Pick pick;
    private int z = 2;
    private double cx, cy;                               // centro, en píxeles del mapa a este zoom
    private boolean fitted, longDone;
    private float tx, ty, downX, downY;
    private long lastTap;

    MapView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    void setPick(Pick l) { pick = l; }

    void setPoints(List<double[]> l) {
        pts.clear();
        pts.addAll(l);
        fitted = false;
        fit();
        invalidate();
    }

    int count() { return pts.size(); }

    // ---- proyección
    private static double world(int zoom) { return 256.0 * Math.pow(2, zoom); }
    private static double px(double lon, int zoom) { return (lon + 180.0) / 360.0 * world(zoom); }
    private static double py(double lat, int zoom) {
        double r = Math.toRadians(Math.max(-85, Math.min(85, lat)));
        return (1.0 - Math.log(Math.tan(r) + 1.0 / Math.cos(r)) / Math.PI) / 2.0 * world(zoom);
    }
    private static double lonOf(double x, int zoom) { return x / world(zoom) * 360.0 - 180.0; }
    private static double latOf(double y, int zoom) {
        double n = Math.PI * (1.0 - 2.0 * y / world(zoom));
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); fit(); }

    /** Encuadra todas las fotos. */
    private void fit() {
        if (fitted || getWidth() == 0 || pts.isEmpty()) return;
        double a = 90, b = -90, c = 180, d = -180;
        for (double[] q : pts) { a = Math.min(a, q[0]); b = Math.max(b, q[0]); c = Math.min(c, q[1]); d = Math.max(d, q[1]); }
        int best = 2;
        for (int k = 15; k >= 2; k--) {
            if (px(d, k) - px(c, k) <= getWidth() * 0.85 && py(a, k) - py(b, k) <= getHeight() * 0.85) { best = k; break; }
        }
        z = best;
        cx = (px(c, z) + px(d, z)) / 2;
        cy = (py(a, z) + py(b, z)) / 2;
        fitted = true;
    }

    /** Lleva el mapa a un rectángulo {minLat, maxLat, minLon, maxLon}. */
    void fitBox(double[] b) {
        if (b == null || getWidth() == 0) return;
        int best = 3;
        for (int k = 14; k >= 2; k--) {
            if (px(b[3], k) - px(b[2], k) <= getWidth() * 0.7 && py(b[0], k) - py(b[1], k) <= getHeight() * 0.7) { best = k; break; }
        }
        z = best;
        cx = (px(b[2], z) + px(b[3], z)) / 2;
        cy = (py(b[0], z) + py(b[1], z)) / 2;
        invalidate();
    }

    private void zoom(int dz) {
        int nz = Math.max(2, Math.min(17, z + dz));
        if (nz == z) return;
        double f = Math.pow(2, nz - z);
        cx *= f; cy *= f; z = nz;
        invalidate();
    }

    private void pan(double dx, double dy) {
        cx += dx; cy += dy;
        double w = world(z);
        cx = ((cx % w) + w) % w;
        cy = Math.max(0, Math.min(w, cy));
        invalidate();
    }

    private int near() {
        int n = 0;
        double left = cx - getWidth() / 2.0, top = cy - getHeight() / 2.0;
        for (double[] q : pts) {
            double dx = px(q[1], z) - left - getWidth() / 2.0, dy = py(q[0], z) - top - getHeight() / 2.0;
            if (dx * dx + dy * dy <= MIRA * MIRA) n += (int) q[2];
        }
        return n;
    }

    @Override protected void onDraw(Canvas cv) {
        cv.drawColor(0xFFDDE3EA);
        int w = getWidth(), h = getHeight();
        double left = cx - w / 2.0, top = cy - h / 2.0;
        int n = 1 << z;
        for (int ty = (int) Math.floor(top / 256.0); ty <= (int) Math.floor((top + h) / 256.0); ty++) {
            if (ty < 0 || ty >= n) continue;
            for (int tx = (int) Math.floor(left / 256.0); tx <= (int) Math.floor((left + w) / 256.0); tx++) {
                int wx = ((tx % n) + n) % n;
                String k = z + "/" + wx + "/" + ty;
                Bitmap b = cache.get(k);
                if (b == null) { load(k, z, wx, ty); continue; }
                int x0 = (int) Math.round(tx * 256.0 - left), y0 = (int) Math.round(ty * 256.0 - top);
                dst.set(x0, y0, x0 + 256, y0 + 256);
                cv.drawBitmap(b, null, dst, null);
            }
        }
        // mapa de calor: círculos translúcidos que se acumulan donde hay más fotos
        double world = world(z);
        float grow = z > 9 ? 1f + (z - 9) * 0.25f : 1f;
        for (double[] q : pts) {
            double x = px(q[1], z) - left, y = py(q[0], z) - top;
            for (int rep = -1; rep <= 1; rep++) {
                double xx = x + rep * world;
                if (xx < -40 || xx > w + 40 || y < -40 || y > h + 40) continue;
                float r = (float) ((5 + Math.min(16, Math.log(q[2] + 1) / Math.log(2) * 2.4)) * grow);
                p.setStyle(Paint.Style.FILL);
                p.setColor(q[2] >= 200 ? 0x66E5173F : q[2] >= 30 ? 0x66FF5A1F : 0x66FFB020);
                cv.drawCircle((float) xx, (float) y, r, p);
            }
        }
        // mira
        float mx = w / 2f, my = h / 2f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(7);
        p.setColor(0xCC000000);
        cv.drawCircle(mx, my, MIRA, p);
        p.setStrokeWidth(3.5f);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(mx, my, MIRA, p);
        int nr = near();
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(30);
        String t = nr > 0 ? nr + (nr == 1 ? " foto aquí · OK para verla" : " fotos aquí · OK para verlas") : "OK: acercar";
        float tw = p.measureText(t);
        p.setColor(0xB0000000);
        cv.drawRect(w / 2f - tw / 2 - 14, 10, w / 2f + tw / 2 + 14, 54, p);   // arriba, para no tapar la mira
        p.setColor(0xFFFFFFFF);
        cv.drawText(t, w / 2f - tw / 2, 42, p);
        p.setTextSize(22);
        p.setColor(0xB0000000);
        String at = "© colaboradores de OpenStreetMap";
        float aw = p.measureText(at);
        cv.drawRect(w - aw - 24, h - 36, w, h, p);
        p.setColor(0xFFFFFFFF);
        cv.drawText(at, w - aw - 12, h - 11, p);
    }

    private void load(final String k, final int zz, final int x, final int y) {
        synchronized (pending) { if (!pending.add(k)) return; }
        pool.submit(new Runnable() {
            public void run() {
                Bitmap b = MapTiles.tile(zz, x, y);
                synchronized (pending) { pending.remove(k); }
                if (b != null) { cache.put(k, b); postInvalidate(); }
            }
        });
    }

    @Override protected void onDetachedFromWindow() { pool.shutdownNow(); super.onDetachedFromWindow(); }

    // ---- mando
    @Override public boolean onKeyDown(int code, KeyEvent e) {
        int step = Math.max(36, Math.min(getWidth(), getHeight()) / 16);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: pan(-step, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: pan(step, 0); return true;
            case KeyEvent.KEYCODE_DPAD_UP: pan(0, -step); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: pan(0, step); return true;
            case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_ZOOM_IN: case KeyEvent.KEYCODE_PLUS: case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                zoom(1); return true;
            case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_ZOOM_OUT: case KeyEvent.KEYCODE_MINUS: case KeyEvent.KEYCODE_MEDIA_PAUSE:
                zoom(-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                longDone = false;
                e.startTracking();
                return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) { longDone = true; zoom(-1); return true; }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            if (!longDone) action();
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private void action() {
        if (near() > 0 && pick != null) {
            double left = cx - getWidth() / 2.0, top = cy - getHeight() / 2.0;
            double mx = left + getWidth() / 2.0, my = top + getHeight() / 2.0;
            double w = world(z);
            double lo1 = lonOf(((mx - MIRA) % w + w) % w, z), lo2 = lonOf(((mx + MIRA) % w + w) % w, z);
            double la1 = latOf(my + MIRA, z), la2 = latOf(my - MIRA, z);
            pick.box(Math.min(la1, la2), Math.max(la1, la2), Math.min(lo1, lo2), Math.max(lo1, lo2));
        } else zoom(1);
    }

    // ---- pantalla táctil
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: downX = tx = e.getX(); downY = ty = e.getY(); return true;
            case MotionEvent.ACTION_MOVE: pan(tx - e.getX(), ty - e.getY()); tx = e.getX(); ty = e.getY(); return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(e.getX() - downX) < 12 && Math.abs(e.getY() - downY) < 12) {
                    long now = System.currentTimeMillis();
                    if (now - lastTap < 350) zoom(1);
                    lastTap = now;
                }
                return true;
        }
        return super.onTouchEvent(e);
    }
}
