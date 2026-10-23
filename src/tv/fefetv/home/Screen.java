package tv.fefetv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole home screen in one view, drawn in a 1280x720 design space scaled to the TV: a
 * sidebar on the left, a greeting with clock and weather, and rows of tiles that scroll. The
 * D-pad moves, OK opens, a long OK or MENU opens the tile's options.
 */
final class Screen extends View {
    interface Host {
        void open(Card c);
        void menu(Card c, int page, int row);
        void sidebarMoved(int index);
        void sidebarChosen(int index);
        void moved();
    }

    static final class Row {
        final String title;
        final List<Card> cards;
        final boolean big;

        Row(String title, List<Card> cards, boolean big) {
            this.title = title;
            this.cards = cards;
            this.big = big;
        }
    }

    static final int HOME = 0, SEARCH = 1, APPS = 2, VIDEO = 3, GAMES = 4, FILES = 5, SETTINGS = 6;
    static final String[] SIDE = {"Home", "Cerca", "App", "Film e Serie", "Giochi", "File", "Impostazioni"};
    private static final int[] SIDE_GLYPH = {Glyphs.HOME, Glyphs.SEARCH, Glyphs.APPS, Glyphs.TV,
            Glyphs.GAMEPAD, Glyphs.FOLDER, Glyphs.GEAR};

    private static final float W = 1280f, H = 720f;
    private static final float SIDEBAR_W = 112f, CONTENT_X = 150f, RIGHT_EDGE = 1244f;
    private static final float ROWS_TOP = 168f;
    private static final float SMALL_W = 152f, SMALL_H = 86f, SMALL_GAP = 16f;
    private static final float BIG_W = 300f, BIG_H = 150f, BIG_GAP = 18f;
    private static final float TITLE_H = 38f, LABEL_H = 30f;

    private final Host host;
    private List<Row> rows = new ArrayList<>();
    private int page = HOME, sideIdx = HOME, row, col;
    private boolean sideFocus;
    private float[] offCur = new float[0], offTgt = new float[0];
    private float scrollCur, scrollTgt, focusT = 1f;
    private boolean longFired;

    private int accent = 0xFF35E0B4;
    private String name = "", clock = "", date = "", temp = null, city = "";
    private int weatherCode;
    private int net = 1, netLevel = 3;
    private boolean weatherDay = true;
    private Bitmap backdrop;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

    Screen(Context c, Host host) {
        super(c);
        this.host = host;
        stroke.setStyle(Paint.Style.STROKE);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    // ---- state from the activity ----

    int page() {
        return page;
    }

    void setBackdrop(Bitmap b) {
        if (backdrop != null && backdrop != b) backdrop.recycle();
        backdrop = b;
        invalidate();
    }

    void setAccent(int a) {
        accent = a;
        invalidate();
    }

    void setHeader(String name, String clock, String date) {
        this.name = name;
        this.clock = clock;
        this.date = date;
        invalidate();
    }

    /** {@code kind}: 0 offline, 1 Wi-Fi (level 0..3), 2 cable. */
    void setNetwork(int kind, int level) {
        if (kind == net && level == netLevel) return;
        net = kind;
        netLevel = level;
        invalidate();
    }

    private static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8
                | Math.round(ab + (bb - ab) * t);
    }

    void setWeather(String temp, String city, int code, boolean day) {
        this.temp = temp;
        this.city = city;
        this.weatherCode = code;
        this.weatherDay = day;
        invalidate();
    }

    /** Shows {@code rows} for {@code page}; keeps the focus where it was when the page is the same. */
    void setPage(int page, List<Row> rows) {
        boolean same = page == this.page;
        this.page = page;
        this.rows = rows;
        if (!same) {
            row = 0;
            col = 0;
            scrollCur = scrollTgt = 0;
            offCur = new float[rows.size()];
            offTgt = new float[rows.size()];
        } else {
            if (offCur.length != rows.size()) {
                float[] a = new float[rows.size()], b = new float[rows.size()];
                for (int i = 0; i < Math.min(a.length, offCur.length); i++) {
                    a[i] = offCur[i];
                    b[i] = offTgt[i];
                }
                offCur = a;
                offTgt = b;
            }
            clampFocus();
        }
        if (!sideFocus) sideIdx = page;
        follow();
        invalidate();
    }

    /** Puts the focus on the card holding the app {@code id} in row {@code inRow}, if any. */
    void focusApp(int inRow, String id) {
        if (inRow >= rows.size()) return;
        List<Card> cs = rows.get(inRow).cards;
        for (int i = 0; i < cs.size(); i++) {
            if (cs.get(i).item != null && cs.get(i).item.id.equals(id)) {
                row = inRow;
                col = i;
                sideFocus = false;
                follow();
                invalidate();
                return;
            }
        }
    }

    void focusContent() {
        sideFocus = false;
        clampFocus();
        follow();
        invalidate();
    }

    /** BACK: first to the top-left tile, then to Home. True when it did something. */
    boolean back() {
        if (sideFocus) {
            sideFocus = false;
            invalidate();
            return true;
        }
        if (row != 0 || col != 0) {
            row = 0;
            col = 0;
            follow();
            invalidate();
            return true;
        }
        return false;
    }

    Card current() {
        if (sideFocus || rows.isEmpty()) return null;
        List<Card> cs = rows.get(row).cards;
        return cs.isEmpty() ? null : cs.get(Math.min(col, cs.size() - 1));
    }

    int row() {
        return row;
    }

    private void clampFocus() {
        if (rows.isEmpty()) {
            row = col = 0;
            return;
        }
        row = Math.max(0, Math.min(row, rows.size() - 1));
        col = Math.max(0, Math.min(col, rows.get(row).cards.size() - 1));
    }

    // ---- keys ----

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(1, 0);
                return true;
            case KeyEvent.KEYCODE_MENU: {
                Card c = current();
                if (c != null) host.menu(c, page, row);
                return true;
            }
            default:
                if (isOk(code)) {
                    if (e.getRepeatCount() == 0) {
                        longFired = false;
                        e.startTracking();
                    }
                    return true;
                }
                return super.onKeyDown(code, e);
        }
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        if (isOk(code)) {
            longFired = true;
            Card c = current();
            if (c != null) host.menu(c, page, row);
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (isOk(code)) {
            if (!longFired && e.isTracking() && !e.isCanceled()) {
                if (sideFocus) {
                    host.sidebarChosen(sideIdx);
                } else {
                    Card c = current();
                    if (c != null) host.open(c);
                }
            }
            longFired = false;
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    private void move(int dx, int dy) {
        if (sideFocus) {
            if (dy != 0) {
                int next = Math.max(0, Math.min(SIDE.length - 1, sideIdx + dy));
                if (next == sideIdx) return;
                sideIdx = next;
                host.moved();
                host.sidebarMoved(sideIdx);
            } else if (dx > 0) {
                host.sidebarChosen(sideIdx);
            }
            invalidate();
            return;
        }
        if (rows.isEmpty()) {
            if (dx < 0) {
                sideFocus = true;
                sideIdx = page;
                host.moved();
                invalidate();
            }
            return;
        }
        if (dx < 0 && col == 0) {
            sideFocus = true;
            sideIdx = page;
            host.moved();
            invalidate();
            return;
        }
        int r0 = row, c0 = col;
        if (dx != 0) col = Math.max(0, Math.min(rows.get(row).cards.size() - 1, col + dx));
        if (dy != 0) {
            int next = Math.max(0, Math.min(rows.size() - 1, row + dy));
            if (next != row) {
                // keep roughly the same horizontal position between rows of different tile sizes
                float x = tileLeft(rows.get(row), col) - offTgt[row];
                row = next;
                Row nr = rows.get(row);
                float step = (nr.big ? BIG_W + BIG_GAP : SMALL_W + SMALL_GAP);
                col = Math.round((x + offTgt[row] - CONTENT_X) / step);
                col = Math.max(0, Math.min(nr.cards.size() - 1, col));
            }
        }
        if (r0 == row && c0 == col) return;
        focusT = 0f;
        host.moved();
        follow();
        invalidate();
    }

    // ---- layout ----

    private static float tileW(Row r) {
        return r.big ? BIG_W : SMALL_W;
    }

    private static float tileH(Row r) {
        return r.big ? BIG_H : SMALL_H;
    }

    private static float tileLeft(Row r, int i) {
        return CONTENT_X + i * (r.big ? BIG_W + BIG_GAP : SMALL_W + SMALL_GAP);
    }

    private static float titleH(Row r) {
        return TextUtils.isEmpty(r.title) ? 4f : TITLE_H;
    }

    private float rowTop(int i) {
        float y = ROWS_TOP;
        for (int k = 0; k < i; k++) y += titleH(rows.get(k)) + tileH(rows.get(k)) + LABEL_H;
        return y;
    }

    /** Targets for the vertical scroll and the focused row's horizontal scroll. */
    private void follow() {
        if (rows.isEmpty()) {
            scrollTgt = 0;
            return;
        }
        float total = rowTop(rows.size()) + 20;
        float maxScroll = Math.max(0, total - H);
        scrollTgt = row == 0 ? 0 : Math.min(maxScroll, Math.max(0, rowTop(row) - 120));
        Row cur = rows.get(row);
        float left = tileLeft(cur, col), right = left + tileW(cur);
        float off = offTgt[row];
        if (right - off > RIGHT_EDGE) off = right - RIGHT_EDGE;
        if (left - off < CONTENT_X) off = left - CONTENT_X;
        offTgt[row] = Math.max(0, off);
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas c) {
        boolean moving = step();
        float s = getWidth() / W;
        if (backdrop != null) {
            r.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(backdrop, null, r, bmp);
        } else {
            c.drawColor(0xFF0A0F1A);
        }
        c.save();
        c.scale(s, s);
        c.save();
        c.clipRect(SIDEBAR_W, 0, W, H);
        c.translate(0, -scrollCur);
        drawHeader(c);
        drawRows(c);
        c.restore();
        drawSidebar(c);
        drawHint(c);
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    private boolean step() {
        boolean moving = false;
        float k = 0.25f;
        if (Math.abs(scrollTgt - scrollCur) > 0.5f) {
            scrollCur += (scrollTgt - scrollCur) * k;
            moving = true;
        } else {
            scrollCur = scrollTgt;
        }
        for (int i = 0; i < offCur.length; i++) {
            if (Math.abs(offTgt[i] - offCur[i]) > 0.5f) {
                offCur[i] += (offTgt[i] - offCur[i]) * k;
                moving = true;
            } else {
                offCur[i] = offTgt[i];
            }
        }
        if (focusT < 1f) {
            focusT = Math.min(1f, focusT + 0.2f);
            moving = true;
        }
        return moving;
    }

    private void textStyle(Typeface face, float size, int color, Paint.Align align) {
        text.setTypeface(face);
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
    }

    private void drawHeader(Canvas c) {
        textStyle(medium, 40, 0xFFF4F7FA, Paint.Align.LEFT);
        String hello = name.isEmpty() ? "Ciao!" : "Ciao ";
        c.drawText(hello, CONTENT_X, 84, text);
        float hw = text.measureText(hello);
        text.setColor(accent);
        if (!name.isEmpty()) c.drawText(name, CONTENT_X + hw, 84, text);
        textStyle(regular, 18, mix(accent, 0xFFFFFFFF, 0.5f), Paint.Align.LEFT);
        c.drawText("Tutto il tuo intrattenimento, in un unico posto.", CONTENT_X, 118, text);

        float clockRight = RIGHT_EDGE;
        if (temp != null) {
            float wx = RIGHT_EDGE - 96;
            textStyle(light, 26, 0xFFF4F7FA, Paint.Align.LEFT);
            c.drawText(temp, wx + 34, 68, text);
            textStyle(regular, 14, 0xFFC9D3E0, Paint.Align.LEFT);
            c.drawText(TextUtils.ellipsize(city, text, 90, TextUtils.TruncateAt.END).toString(), wx + 34, 90, text);
            Glyphs.weather(c, weatherCode, weatherDay, wx + 4, 64, 46);
            fill.setColor(0x55FFFFFF);
            c.drawRect(wx - 26, 42, wx - 25, 98, fill);
            clockRight = wx - 44;
        }
        textStyle(light, 40, 0xFFF4F7FA, Paint.Align.RIGHT);
        c.drawText(clock, clockRight, 74, text);
        float iconX = clockRight - text.measureText(clock) - 26;
        if (net == 2) Glyphs.draw(c, Glyphs.ETHERNET, iconX, 60, 26, 0xFFE8EEF4);
        else Glyphs.wifi(c, iconX, 58, 28, netLevel, net == 0, 0xFFE8EEF4);
        textStyle(regular, 15, 0xFFC9D3E0, Paint.Align.RIGHT);
        c.drawText(date, clockRight, 96, text);
    }

    private void drawRows(Canvas c) {
        if (rows.isEmpty()) {
            textStyle(regular, 18, 0xB3FFFFFF, Paint.Align.LEFT);
            c.drawText("Niente qui per ora.", CONTENT_X, ROWS_TOP + 40, text);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row rw = rows.get(i);
            float top = rowTop(i);
            float bottom = top + titleH(rw) + tileH(rw) + LABEL_H;
            if (bottom < scrollCur || top > scrollCur + H) continue;
            if (!TextUtils.isEmpty(rw.title)) {
                textStyle(medium, 21, 0xFFE8EEF4, Paint.Align.LEFT);
                c.drawText(rw.title, CONTENT_X, top + 24, text);
            }
            float ty = top + titleH(rw);
            int fi = -1;
            for (int k = 0; k < rw.cards.size(); k++) {
                float x = tileLeft(rw, k) - offCur[i];
                if (x + tileW(rw) < SIDEBAR_W - 40 || x > W + 20) continue;
                boolean focused = !sideFocus && i == row && k == col;
                if (focused) {
                    fi = k;
                    continue;
                }
                drawCard(c, rw, rw.cards.get(k), x, ty, 0f);
            }
            if (fi >= 0) {
                float x = tileLeft(rw, fi) - offCur[i];
                drawCard(c, rw, rw.cards.get(fi), x, ty, focusT);
            }
        }
    }

    private void drawCard(Canvas c, Row rw, Card card, float x, float y, float t) {
        boolean focused = t > 0 || (!sideFocus && rows.get(row) == rw && current() == card);
        float w = tileW(rw), h = tileH(rw);
        float grow = focused ? 1f + 0.07f * ease(t) : 1f;
        float cx = x + w / 2, cy = y + h / 2;
        r.set(cx - w * grow / 2, cy - h * grow / 2, cx + w * grow / 2, cy + h * grow / 2);
        float rad = 10;

        if (focused) {
            fill.setColor(0xFF000000);
            fill.setShadowLayer(26, 0, 0, accent);
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShadowLayer(0, 0, 0, 0);
        }
        fill.setColor(0xF01A2232);
        c.drawRoundRect(r, rad, rad, fill);

        Item it = card.item;
        Drawable banner = it == null ? null : it.banner;
        Drawable icon = it == null ? null : it.icon;
        c.save();
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        boolean titleInside = false;
        if (banner != null) {
            drawCover(c, banner, r);
        } else if (icon != null) {
            float isz = r.height() * (rw.big ? 0.42f : 0.46f);
            drawFit(c, icon, r.centerX() - isz / 2, r.top + r.height() * 0.14f, isz, isz);
            titleInside = true;
        } else if (card.glyph != Glyphs.NONE) {
            Glyphs.draw(c, card.glyph, r.centerX(), r.top + r.height() * 0.38f, r.height() * 0.32f, 0xFFE8EEF4);
            titleInside = true;
        } else {
            titleInside = true;
        }
        if (rw.big) {
            fill.setShader(new LinearGradient(0, r.top + r.height() * 0.45f, 0, r.bottom,
                    0x00000000, 0xE0000000, Shader.TileMode.CLAMP));
            c.drawRect(r, fill);
            fill.setShader(null);
            Glyphs.draw(c, Glyphs.PLAY, r.left + 30, r.bottom - 30, 30, 0xFFFFFFFF);
            textStyle(medium, 17, 0xFFFFFFFF, Paint.Align.LEFT);
            c.drawText(ellipsize(card.title, r.width() - 80), r.left + 56, r.bottom - 34, text);
            if (!TextUtils.isEmpty(card.sub)) {
                textStyle(regular, 13, 0xCCFFFFFF, Paint.Align.LEFT);
                c.drawText(ellipsize(card.sub, r.width() - 80), r.left + 56, r.bottom - 16, text);
            }
        } else if (titleInside) {
            textStyle(medium, 14, 0xFFF0F4F8, Paint.Align.CENTER);
            c.drawText(ellipsize(card.title, r.width() - 14), r.centerX(), r.bottom - 14, text);
        }
        c.restore();

        if (focused) {
            stroke.setStrokeWidth(3);
            stroke.setColor(accent);
            c.drawRoundRect(r, rad, rad, stroke);
            if (!rw.big && !titleInside) {
                textStyle(medium, 14, 0xFFF0F4F8, Paint.Align.CENTER);
                c.drawText(ellipsize(card.title, w + 40), cx, r.bottom + 20, text);
            } else if (!rw.big && !TextUtils.isEmpty(card.sub)) {
                textStyle(regular, 13, 0xCCDDE6EE, Paint.Align.CENTER);
                c.drawText(ellipsize(card.sub, w + 60), cx, r.bottom + 20, text);
            }
        }
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private String ellipsize(String s, float width) {
        return TextUtils.ellipsize(s == null ? "" : s, text, Math.max(20, width), TextUtils.TruncateAt.END).toString();
    }

    /** Fills the box, cropping the overflow: banners are 16:9 like the small tiles. */
    private static void drawCover(Canvas c, Drawable d, RectF box) {
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            d.setBounds(Math.round(box.left), Math.round(box.top), Math.round(box.right), Math.round(box.bottom));
        } else {
            float k = Math.max(box.width() / iw, box.height() / ih);
            float w = iw * k, h = ih * k;
            float x = box.centerX() - w / 2, y = box.centerY() - h / 2;
            d.setBounds(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
        }
        d.setAlpha(255);
        d.draw(c);
    }

    private static void drawFit(Canvas c, Drawable d, float x, float y, float w, float h) {
        d.setBounds(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
        d.setAlpha(255);
        d.draw(c);
    }

    private void drawSidebar(Canvas c) {
        fill.setShader(new LinearGradient(0, 0, SIDEBAR_W + 30, 0,
                new int[] {0xF2060A12, 0xE6060A12, 0x00060A12}, new float[] {0f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, SIDEBAR_W + 30, H, fill);
        fill.setShader(null);
        float top = 26, step = 90;
        for (int i = 0; i < SIDE.length; i++) {
            float y = top + i * step;
            r.set(12, y, SIDEBAR_W - 12, y + 80);
            boolean current = i == page;
            boolean focused = sideFocus && i == sideIdx;
            if (current) {
                fill.setColor(mix(accent, 0xFF0A0F1A, 0.8f));
                c.drawRoundRect(r, 14, 14, fill);
            }
            if (focused) {
                fill.setColor(0x33FFFFFF);
                c.drawRoundRect(r, 14, 14, fill);
                stroke.setStrokeWidth(2.5f);
                stroke.setColor(accent);
                c.drawRoundRect(r, 14, 14, stroke);
            }
            int col = current || focused ? (current ? accent : 0xFFFFFFFF) : 0xFFC9D3E0;
            Glyphs.draw(c, SIDE_GLYPH[i], r.centerX(), y + 30, 28, col);
            textStyle(current ? medium : regular, SIDE[i].length() > 10 ? 12.5f : 14, col, Paint.Align.CENTER);
            c.drawText(SIDE[i], r.centerX(), y + 66, text);
        }
    }

    private void drawHint(Canvas c) {
        String a = "Tieni premuto ", b = "OK", d = " per le opzioni";
        textStyle(regular, 13, 0xFFDDE6EE, Paint.Align.LEFT);
        float wa = text.measureText(a), wb = text.measureText(b), wd = text.measureText(d);
        float x = 18, y = H - 22, w = 34 + wa + wb + wd;
        r.set(x, y - 22, x + w, y + 10);
        fill.setColor(0xD90D1420);
        c.drawRoundRect(r, 16, 16, fill);
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(accent);
        c.drawCircle(x + 14, y - 6, 6, stroke);
        c.drawText(a, x + 26, y, text);
        text.setColor(accent);
        c.drawText(b, x + 26 + wa, y, text);
        text.setColor(0xFFDDE6EE);
        c.drawText(d, x + 26 + wa + wb, y, text);
    }
}
