package tv.fefetv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;

import java.io.File;
import java.util.Random;

/**
 * The background: the user's own picture (wallpaper.jpg or .png next to home.conf) or, without
 * one, a night landscape drawn here: sky, stars, three mountain ranges and a lake. Dark scrims on
 * the left and at the bottom keep the text readable on either.
 */
final class Backdrop {
    private Backdrop() {}

    static Bitmap make(int w, int h, File dir) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        Bitmap photo = load(dir, w, h);
        if (photo != null) {
            float k = Math.max(w / (float) photo.getWidth(), h / (float) photo.getHeight());
            int sw = Math.round(w / k), sh = Math.round(h / k);
            int sx = (photo.getWidth() - sw) / 2, sy = (photo.getHeight() - sh) / 2;
            c.drawBitmap(photo, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, w, h),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            photo.recycle();
        } else {
            landscape(c, w, h);
        }
        Paint scrim = new Paint();
        scrim.setShader(new LinearGradient(0, 0, w * 0.5f, 0, 0xE6070B14, 0x00070B14, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w * 0.5f, h, scrim);
        scrim.setShader(new LinearGradient(0, h * 0.28f, 0, h * 0.62f, 0x000A0F1A, 0xF20A0F1A, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.28f, w, h * 0.62f, scrim);
        scrim.setShader(null);
        scrim.setColor(0xF20A0F1A);
        c.drawRect(0, h * 0.62f - 1, w, h, scrim);
        return out;
    }

    private static Bitmap load(File dir, int w, int h) {
        if (dir == null) return null;
        for (String name : new String[] {"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) {
            File f = new File(dir, name);
            if (!f.isFile()) continue;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
                o.inSampleSize *= 2;
            }
            o.inJustDecodeBounds = false;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b != null) return b;
        }
        return null;
    }

    private static void landscape(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float horizon = h * 0.44f;
        p.setShader(new LinearGradient(0, 0, 0, horizon, new int[] {0xFF080C1E, 0xFF1A1C40, 0xFF5A3A66, 0xFFB86A5A},
                new float[] {0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon, p);
        p.setShader(new RadialGradient(w * 0.78f, horizon, w * 0.35f, 0x88FF9A6A, 0x00FF9A6A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, horizon, p);
        p.setShader(null);

        Random rnd = new Random(26);
        for (int i = 0; i < 220; i++) {
            float x = rnd.nextFloat() * w, y = rnd.nextFloat() * horizon * 0.7f;
            p.setColor(((40 + rnd.nextInt(170)) << 24) | 0xFFFFFF);
            c.drawCircle(x, y, (rnd.nextFloat() * 1.2f + 0.4f) * h / 1080f * 1.6f, p);
        }

        int[] colors = {0xFF2A2B4E, 0xFF1A1D36, 0xFF0F1324};
        float[] base = {horizon * 0.62f, horizon * 0.78f, horizon * 0.9f};
        float[] rough = {0.22f, 0.16f, 0.1f};
        for (int layer = 0; layer < 3; layer++) {
            float[] ys = ridge(rnd, 64, rough[layer]);
            Path m = new Path();
            m.moveTo(0, horizon);
            for (int i = 0; i < ys.length; i++) {
                float x = w * i / (float) (ys.length - 1);
                float lift = 0.35f + 0.65f * (x / w);
                m.lineTo(x, base[layer] - ys[i] * h * 0.2f * lift);
            }
            m.lineTo(w, horizon);
            m.close();
            p.setColor(colors[layer]);
            c.drawPath(m, p);
        }

        p.setShader(new LinearGradient(0, horizon, 0, h * 0.62f, new int[] {0xFF3A2D4C, 0xFF151A30, 0xFF0B101C},
                new float[] {0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, h, p);
        p.setShader(null);
        for (int i = 0; i < 40; i++) {
            float x = w * (0.35f + rnd.nextFloat() * 0.5f), y = horizon + rnd.nextFloat() * h * 0.015f;
            p.setColor(0xCCFFC27A);
            c.drawCircle(x, y, h / 1080f * 1.6f, p);
        }
        p.setColor(0x33FFFFFF);
        for (int i = 0; i < 30; i++) {
            float y = horizon + h * 0.01f + rnd.nextFloat() * h * 0.12f;
            float x = rnd.nextFloat() * w, len = w * (0.02f + rnd.nextFloat() * 0.08f);
            c.drawRect(x, y, x + len, y + Math.max(1, h / 1080f), p);
        }
    }

    /** Midpoint displacement: a jagged line of {@code n+1} heights in 0..1. */
    private static float[] ridge(Random rnd, int n, float rough) {
        float[] y = new float[n + 1];
        y[0] = rnd.nextFloat() * 0.5f;
        y[n] = 0.4f + rnd.nextFloat() * 0.6f;
        for (int step = n; step > 1; step /= 2) {
            for (int i = 0; i + step <= n; i += step) {
                int mid = i + step / 2;
                y[mid] = (y[i] + y[i + step]) / 2 + (rnd.nextFloat() - 0.5f) * rough * step / 8f;
            }
        }
        for (int i = 0; i <= n; i++) y[i] = Math.max(0f, Math.min(1f, y[i]));
        return y;
    }
}
