package tv.fefetv.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Every icon the launcher draws itself, from plain shapes: the APK carries no image files. */
final class Glyphs {
    static final int NONE = 0, HOME = 1, SEARCH = 2, APPS = 3, TV = 4, GAMEPAD = 5, FOLDER = 6, GEAR = 7,
            PLUS = 8, SWAP = 9, SOUND = 10, IMAGE = 11, INFO = 12, AMAZON = 13, PLAY = 14, EYE = 15,
            PERSON = 16, MUTE = 17, PALETTE = 18, ETHERNET = 19;

    private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path p = new Path();
    private static final RectF r = new RectF();

    static {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    private Glyphs() {}

    /** Draws glyph {@code g} centred on (cx, cy), about {@code u} wide. */
    static void draw(Canvas c, int g, float cx, float cy, float u, int color) {
        fill.setColor(color);
        line.setColor(color);
        line.setStrokeWidth(u * 0.09f);
        switch (g) {
            case HOME:
                p.reset();
                p.setFillType(Path.FillType.EVEN_ODD);
                p.moveTo(cx, cy - u * 0.46f);
                p.lineTo(cx + u * 0.5f, cy - u * 0.02f);
                p.lineTo(cx + u * 0.36f, cy - u * 0.02f);
                p.lineTo(cx + u * 0.36f, cy + u * 0.42f);
                p.lineTo(cx - u * 0.36f, cy + u * 0.42f);
                p.lineTo(cx - u * 0.36f, cy - u * 0.02f);
                p.lineTo(cx - u * 0.5f, cy - u * 0.02f);
                p.close();
                p.addRect(cx - u * 0.09f, cy + u * 0.14f, cx + u * 0.09f, cy + u * 0.42f, Path.Direction.CW);
                c.drawPath(p, fill);
                break;
            case SEARCH:
                c.drawCircle(cx - u * 0.07f, cy - u * 0.07f, u * 0.28f, line);
                line.setStrokeWidth(u * 0.11f);
                c.drawLine(cx + u * 0.14f, cy + u * 0.14f, cx + u * 0.42f, cy + u * 0.42f, line);
                break;
            case APPS:
                for (int i = 0; i < 9; i++) {
                    c.drawCircle(cx + (i % 3 - 1) * u * 0.33f, cy + (i / 3 - 1) * u * 0.33f, u * 0.1f, fill);
                }
                break;
            case TV:
                r.set(cx - u * 0.46f, cy - u * 0.36f, cx + u * 0.46f, cy + u * 0.24f);
                c.drawRoundRect(r, u * 0.08f, u * 0.08f, line);
                c.drawLine(cx - u * 0.22f, cy + u * 0.44f, cx + u * 0.22f, cy + u * 0.44f, line);
                break;
            case GAMEPAD:
                r.set(cx - u * 0.5f, cy - u * 0.28f, cx + u * 0.5f, cy + u * 0.3f);
                c.drawRoundRect(r, u * 0.26f, u * 0.26f, line);
                line.setStrokeWidth(u * 0.08f);
                c.drawLine(cx - u * 0.3f, cy, cx - u * 0.1f, cy, line);
                c.drawLine(cx - u * 0.2f, cy - u * 0.1f, cx - u * 0.2f, cy + u * 0.1f, line);
                c.drawCircle(cx + u * 0.17f, cy + u * 0.04f, u * 0.05f, fill);
                c.drawCircle(cx + u * 0.29f, cy - u * 0.07f, u * 0.05f, fill);
                break;
            case FOLDER:
                p.reset();
                p.moveTo(cx - u * 0.46f, cy - u * 0.32f);
                p.lineTo(cx - u * 0.12f, cy - u * 0.32f);
                p.lineTo(cx, cy - u * 0.2f);
                p.lineTo(cx + u * 0.46f, cy - u * 0.2f);
                p.lineTo(cx + u * 0.46f, cy + u * 0.36f);
                p.lineTo(cx - u * 0.46f, cy + u * 0.36f);
                p.close();
                c.drawPath(p, line);
                break;
            case GEAR:
                for (int k = 0; k < 8; k++) {
                    c.save();
                    c.rotate(k * 45, cx, cy);
                    r.set(cx - u * 0.08f, cy - u * 0.48f, cx + u * 0.08f, cy - u * 0.3f);
                    c.drawRoundRect(r, u * 0.03f, u * 0.03f, fill);
                    c.restore();
                }
                line.setStrokeWidth(u * 0.14f);
                c.drawCircle(cx, cy, u * 0.26f, line);
                break;
            case PLUS:
                line.setStrokeWidth(u * 0.08f);
                c.drawLine(cx - u * 0.4f, cy, cx + u * 0.4f, cy, line);
                c.drawLine(cx, cy - u * 0.4f, cx, cy + u * 0.4f, line);
                break;
            case SWAP:
                c.drawLine(cx - u * 0.42f, cy - u * 0.16f, cx + u * 0.42f, cy - u * 0.16f, line);
                c.drawLine(cx + u * 0.42f, cy - u * 0.16f, cx + u * 0.22f, cy - u * 0.36f, line);
                c.drawLine(cx + u * 0.42f, cy + u * 0.18f, cx - u * 0.42f, cy + u * 0.18f, line);
                c.drawLine(cx - u * 0.42f, cy + u * 0.18f, cx - u * 0.22f, cy + u * 0.38f, line);
                break;
            case SOUND:
            case MUTE:
                p.reset();
                p.moveTo(cx - u * 0.44f, cy - u * 0.14f);
                p.lineTo(cx - u * 0.24f, cy - u * 0.14f);
                p.lineTo(cx, cy - u * 0.36f);
                p.lineTo(cx, cy + u * 0.36f);
                p.lineTo(cx - u * 0.24f, cy + u * 0.14f);
                p.lineTo(cx - u * 0.44f, cy + u * 0.14f);
                p.close();
                c.drawPath(p, fill);
                if (g == SOUND) {
                    r.set(cx - u * 0.1f, cy - u * 0.26f, cx + u * 0.3f, cy + u * 0.26f);
                    c.drawArc(r, -60, 120, false, line);
                    r.set(cx - u * 0.1f, cy - u * 0.44f, cx + u * 0.46f, cy + u * 0.44f);
                    c.drawArc(r, -60, 120, false, line);
                } else {
                    c.drawLine(cx + u * 0.14f, cy - u * 0.16f, cx + u * 0.44f, cy + u * 0.16f, line);
                    c.drawLine(cx + u * 0.44f, cy - u * 0.16f, cx + u * 0.14f, cy + u * 0.16f, line);
                }
                break;
            case IMAGE:
                r.set(cx - u * 0.46f, cy - u * 0.36f, cx + u * 0.46f, cy + u * 0.36f);
                c.drawRoundRect(r, u * 0.08f, u * 0.08f, line);
                p.reset();
                p.moveTo(cx - u * 0.36f, cy + u * 0.26f);
                p.lineTo(cx - u * 0.1f, cy - u * 0.06f);
                p.lineTo(cx + u * 0.08f, cy + u * 0.12f);
                p.lineTo(cx + u * 0.2f, cy);
                p.lineTo(cx + u * 0.36f, cy + u * 0.26f);
                p.close();
                c.drawPath(p, fill);
                c.drawCircle(cx + u * 0.2f, cy - u * 0.18f, u * 0.07f, fill);
                break;
            case INFO:
                c.drawCircle(cx, cy, u * 0.42f, line);
                c.drawCircle(cx, cy - u * 0.2f, u * 0.06f, fill);
                c.drawLine(cx, cy - u * 0.04f, cx, cy + u * 0.24f, line);
                break;
            case AMAZON:
                p.reset();
                p.moveTo(cx - u * 0.4f, cy + u * 0.02f);
                p.quadTo(cx, cy + u * 0.36f, cx + u * 0.4f, cy + u * 0.02f);
                c.drawPath(p, line);
                c.drawLine(cx + u * 0.4f, cy + u * 0.02f, cx + u * 0.26f, cy - u * 0.02f, line);
                c.drawLine(cx + u * 0.4f, cy + u * 0.02f, cx + u * 0.38f, cy + u * 0.16f, line);
                break;
            case PLAY:
                c.drawCircle(cx, cy, u * 0.44f, line);
                p.reset();
                p.moveTo(cx - u * 0.12f, cy - u * 0.2f);
                p.lineTo(cx + u * 0.22f, cy);
                p.lineTo(cx - u * 0.12f, cy + u * 0.2f);
                p.close();
                c.drawPath(p, fill);
                break;
            case EYE:
                p.reset();
                p.moveTo(cx - u * 0.48f, cy);
                p.quadTo(cx, cy - u * 0.5f, cx + u * 0.48f, cy);
                p.quadTo(cx, cy + u * 0.5f, cx - u * 0.48f, cy);
                c.drawPath(p, line);
                c.drawCircle(cx, cy, u * 0.12f, fill);
                break;
            case PERSON:
                c.drawCircle(cx, cy - u * 0.18f, u * 0.18f, line);
                r.set(cx - u * 0.38f, cy + u * 0.08f, cx + u * 0.38f, cy + u * 0.7f);
                c.drawArc(r, 180, 180, false, line);
                break;
            case PALETTE:
                c.drawCircle(cx, cy, u * 0.42f, line);
                int[] dots = {0xFFB57BFF, 0xFF35E0B4, 0xFFFFB74D, 0xFF5AA9FF};
                for (int k = 0; k < 4; k++) {
                    double a = Math.PI / 2 * k - Math.PI / 4;
                    fill.setColor(dots[k]);
                    c.drawCircle(cx + (float) Math.cos(a) * u * 0.2f, cy + (float) Math.sin(a) * u * 0.2f, u * 0.09f, fill);
                }
                break;
            case ETHERNET:
                r.set(cx - u * 0.3f, cy - u * 0.34f, cx + u * 0.3f, cy + u * 0.16f);
                c.drawRoundRect(r, u * 0.06f, u * 0.06f, line);
                c.drawLine(cx - u * 0.14f, cy - u * 0.34f, cx - u * 0.14f, cy - u * 0.18f, line);
                c.drawLine(cx, cy - u * 0.34f, cx, cy - u * 0.18f, line);
                c.drawLine(cx + u * 0.14f, cy - u * 0.34f, cx + u * 0.14f, cy - u * 0.18f, line);
                c.drawLine(cx, cy + u * 0.16f, cx, cy + u * 0.44f, line);
                break;
            default:
                break;
        }
    }

    /** Wi-Fi fan: {@code level} 0..3 bright arcs, the rest faint; {@code off} adds a slash. */
    static void wifi(Canvas c, float cx, float cy, float u, int level, boolean off, int color) {
        line.setStrokeWidth(u * 0.1f);
        float by = cy + u * 0.36f;
        for (int i = 1; i <= 3; i++) {
            line.setColor(!off && i <= level ? color : (color & 0x00FFFFFF) | 0x50000000);
            float rad = u * 0.2f * i + u * 0.02f;
            r.set(cx - rad, by - rad, cx + rad, by + rad);
            c.drawArc(r, 225, 90, false, line);
        }
        fill.setColor(off ? (color & 0x00FFFFFF) | 0x50000000 : color);
        c.drawCircle(cx, by - u * 0.02f, u * 0.08f, fill);
        if (off) {
            line.setColor(color);
            c.drawLine(cx - u * 0.42f, cy - u * 0.36f, cx + u * 0.42f, by, line);
        }
    }

    /** Weather icon for a WMO weather code (Open-Meteo), or a cloud when unknown. */
    static void weather(Canvas c, int code, boolean day, float cx, float cy, float u) {
        int sun = 0xFFFFC44D, cloud = 0xFFEFF3F8, rain = 0xFF7FC4FF;
        boolean clear = code == 0;
        boolean partly = code >= 1 && code <= 2;
        if (clear || partly) {
            float sx = clear ? cx : cx + u * 0.16f, sy = clear ? cy : cy - u * 0.14f, sr = u * (clear ? 0.24f : 0.17f);
            fill.setColor(day ? sun : 0xFFDDE3F0);
            c.drawCircle(sx, sy, sr, fill);
            if (day) {
                line.setColor(sun);
                line.setStrokeWidth(u * 0.05f);
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI / 4 * k;
                    c.drawLine(sx + (float) Math.cos(a) * sr * 1.35f, sy + (float) Math.sin(a) * sr * 1.35f,
                            sx + (float) Math.cos(a) * sr * 1.75f, sy + (float) Math.sin(a) * sr * 1.75f, line);
                }
            }
            if (clear) return;
        }
        fill.setColor(cloud);
        c.drawCircle(cx - u * 0.18f, cy + u * 0.06f, u * 0.17f, fill);
        c.drawCircle(cx + u * 0.02f, cy - u * 0.04f, u * 0.23f, fill);
        c.drawCircle(cx + u * 0.24f, cy + u * 0.08f, u * 0.15f, fill);
        r.set(cx - u * 0.35f, cy + u * 0.06f, cx + u * 0.39f, cy + u * 0.23f);
        c.drawRoundRect(r, u * 0.08f, u * 0.08f, fill);
        line.setStrokeWidth(u * 0.05f);
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            line.setColor(rain);
            for (int k = -1; k <= 1; k++) {
                c.drawLine(cx + k * u * 0.16f, cy + u * 0.32f, cx + k * u * 0.16f - u * 0.05f, cy + u * 0.46f, line);
            }
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            fill.setColor(cloud);
            for (int k = -1; k <= 1; k++) c.drawCircle(cx + k * u * 0.16f, cy + u * 0.4f, u * 0.04f, fill);
        } else if (code >= 95) {
            fill.setColor(sun);
            p.reset();
            p.moveTo(cx + u * 0.04f, cy + u * 0.24f);
            p.lineTo(cx - u * 0.1f, cy + u * 0.4f);
            p.lineTo(cx, cy + u * 0.4f);
            p.lineTo(cx - u * 0.08f, cy + u * 0.54f);
            p.lineTo(cx + u * 0.12f, cy + u * 0.34f);
            p.lineTo(cx + u * 0.02f, cy + u * 0.34f);
            p.close();
            c.drawPath(p, fill);
        } else if (code == 45 || code == 48) {
            line.setColor(cloud);
            c.drawLine(cx - u * 0.3f, cy + u * 0.34f, cx + u * 0.3f, cy + u * 0.34f, line);
            c.drawLine(cx - u * 0.2f, cy + u * 0.44f, cx + u * 0.36f, cy + u * 0.44f, line);
        }
    }
}
