package com.yuskilabs.fotostv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

/** Mapa estático de OpenStreetMap: se juntan las teselas de alrededor y se dibuja una chincheta en el punto exacto. */
class MapTiles {
    static final int SIZE = 768;

    static Bitmap render(double lat, double lon, int zoom) {
        try {
            double n = Math.pow(2, zoom);
            double px = (lon + 180.0) / 360.0 * n * 256.0;
            double rad = Math.toRadians(lat);
            double py = (1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI) / 2.0 * n * 256.0;
            double left = px - SIZE / 2.0, top = py - SIZE / 2.0;
            int tx0 = (int) Math.floor(left / 256.0), tx1 = (int) Math.floor((left + SIZE) / 256.0);
            int ty0 = (int) Math.floor(top / 256.0), ty1 = (int) Math.floor((top + SIZE) / 256.0);
            Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawColor(0xFFDDE3EA);
            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Object[]> jobs = new ArrayList<Object[]>();
            for (int ty = ty0; ty <= ty1; ty++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    if (ty < 0 || ty >= (int) n) continue;
                    final int wx = (int) (((tx % (int) n) + (int) n) % (int) n);
                    final int fz = zoom, fy = ty;
                    Future<Bitmap> f = pool.submit(new java.util.concurrent.Callable<Bitmap>() {
                        public Bitmap call() { return tile(fz, wx, fy); }
                    });
                    jobs.add(new Object[]{f, tx, ty});
                }
            }
            int got = 0;
            for (Object[] j : jobs) {
                @SuppressWarnings("unchecked") Future<Bitmap> f = (Future<Bitmap>) j[0];
                Bitmap t = null;
                try { t = f.get(); } catch (Exception ignored) { }
                if (t == null) continue;
                got++;
                cv.drawBitmap(t, (float) ((Integer) j[1] * 256.0 - left), (float) ((Integer) j[2] * 256.0 - top), null);
            }
            pool.shutdownNow();
            if (got == 0) return null;
            // chincheta en el centro
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float cx = SIZE / 2f, cy = SIZE / 2f;
            p.setColor(0x55000000);
            cv.drawCircle(cx, cy + 3, 22, p);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy, 22, p);
            p.setColor(0xFFF43F7A);
            cv.drawCircle(cx, cy, 16, p);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy, 6, p);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap tile(int z, int x, int y) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png").openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "FotosTV (https://github.com/yuskilabs/fotostv)");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            byte[] d = bo.toByteArray();
            return BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
