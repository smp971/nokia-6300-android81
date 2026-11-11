package ro.leo.visual;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import ro.leo.i18n.L;

/** Keypad-first rendering for Home, the app menu and the Home editor. */
final class LeoHome {
    private static final int WIDTH = 240;
    private static final int BODY = 276;
    private static final int HEIGHT = 302;
    private static final int INK = 0xfffaf8ff;
    private static final int MUTED = 0xffd3cddc;
    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface CLOCK = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect glyphBounds = new Rect();
    private final RectF wallpaperBounds = new RectF(0, 0, WIDTH, BODY);
    private Appearance.Palette palette;
    private LinearGradient hero;
    private int tile;
    private int focus;
    private int divider;
    private float textScale;

    void draw(Canvas canvas, MainActivity.Screen screen) {
        if (palette != screen.palette) {
            palette = screen.palette;
            tile = Appearance.mix(palette.background, palette.card, .88f);
            focus = Appearance.mix(tile, palette.accent, .22f);
            divider = Appearance.mix(palette.background, palette.accent, .26f);
            hero = new LinearGradient(9, 9, 231, 135,
                Appearance.mix(palette.card, palette.accent, .18f), tile, Shader.TileMode.CLAMP);
        }
        textScale = screen.textScale;
        canvas.drawColor(palette.background);
        if (screen.grid) drawApps(canvas, screen);
        else drawHome(canvas, screen);
        if (!screen.message.isEmpty()) {
            surface(canvas, 8, 238, 232, 271, 11, tile);
            text(canvas, screen.message, 120, 259, 12, INK, Paint.Align.CENTER, 208, MEDIUM);
        }
        drawFooter(canvas, screen);
    }

    private void drawHome(Canvas canvas, MainActivity.Screen screen) {
        boolean showSims = screen.showSims || screen.editing;
        boolean showCard = screen.cardAvailable() || screen.editing;
        if (screen.wallpaper != null) {
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(screen.wallpaper, null, wallpaperBounds, paint);
            paint.setColor(0x66000000);
            canvas.drawRect(wallpaperBounds, paint);
        }
        int heroBottom = showSims ? 142 : 113;
        if (screen.homePart(canvas, 0, 9, 8, 231, heroBottom)) {
            paint.setColor(Color.WHITE);
            if (!screen.highContrast) paint.setShader(hero);
            else paint.setColor(tile);
            canvas.drawRoundRect(9, 8, 231, heroBottom, 17, 17, paint);
            paint.setShader(null);
            canvas.restore();
        }
        editableText(canvas, screen, 1, screen.networkBanner, 20, 29, 10, palette.accent, MEDIUM);
        editableText(canvas, screen, 2, screen.time, 17, 79, screen.compactClock ? 37 : 52, INK, CLOCK);
        editableText(canvas, screen, 3, screen.date, 20, 100, 13, MUTED, REGULAR);
        if (showSims) drawSims(canvas, screen);
        if (screen.editing) drawEditorHelp(canvas, heroBottom + 8);
        else drawActions(canvas, screen, heroBottom + 8);
        if (showCard) drawCard(canvas, screen, heroBottom + 42);
        drawShortcuts(canvas, screen, showCard ? heroBottom + 91 : heroBottom + 53);

        if (screen.editing) {
            RectF frame = screen.homeLayout.frames[screen.editItem];
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(palette.accent);
            canvas.drawRoundRect(frame, 5, 5, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void editableText(Canvas canvas, MainActivity.Screen screen, int id, String label,
                              float left, float baseline, float size, int color, Typeface font) {
        prepare(size, font);
        String fitted = KeypadText.fit(paint, label, 202);
        paint.getTextBounds(fitted, 0, fitted.length(), glyphBounds);
        if (screen.homePart(canvas, id, left + glyphBounds.left - 4, baseline + glyphBounds.top - 3,
                left + glyphBounds.right + 4, baseline + glyphBounds.bottom + 3)) {
            label(canvas, fitted, left, baseline, color, Paint.Align.LEFT);
            canvas.restore();
        }
    }

    private void drawSims(Canvas canvas, MainActivity.Screen screen) {
        RectF first = new RectF(17, 110, 117, 136);
        RectF second = new RectF(123, 110, 223, 136);
        screen.homeLayout.splitSims(new RectF(17, 110, 223, 136), first, second);
        for (int slot = 0; slot < 2; slot++) {
            RectF bounds = slot == 0 ? first : second;
            if (!screen.homePart(canvas, slot == 0 ? 4 : 7, bounds.left, bounds.top, bounds.right, bounds.bottom))
                continue;
            surface(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom, 10, palette.bar);
            float baseline = bounds.top + 17;
            screen.dashboardArt.sim(canvas, bounds.left + 12, baseline, slot, screen.inService[slot]);
            text(canvas, screen.simLabels[slot], bounds.left + 23, baseline, 10, INK, Paint.Align.LEFT, 72, MEDIUM);
            canvas.restore();
        }
    }

    private void drawActions(Canvas canvas, MainActivity.Screen screen, float top) {
        surface(canvas, 9, top, 116, top + 28, 10, tile);
        surface(canvas, 124, top, 231, top + 28, 10, tile);
        text(canvas, "↑", 22, top + 18, 14, palette.accent, Paint.Align.CENTER, 16, MEDIUM);
        text(canvas, "↓", 137, top + 18, 14, palette.accent, Paint.Align.CENTER, 16, MEDIUM);
        text(canvas, L.t("Setări rapide"), 33, top + 18, 10, INK, Paint.Align.LEFT, 79, MEDIUM);
        text(canvas, L.t("Notificări"), 148, top + 18, 11, INK, Paint.Align.LEFT, 78, MEDIUM);
        if (screen.showUnread && (UnreadState.sms > 0 || UnreadState.calls > 0)) {
            paint.setColor(palette.accent);
            canvas.drawCircle(222, top + 8, 2.5f, paint);
        }
    }

    private void drawEditorHelp(Canvas canvas, float top) {
        surface(canvas, 9, top, 231, top + 28, 10, tile);
        text(canvas, L.t("Săgeți: mută · * / #: element"), 120, top + 11, 9, INK, Paint.Align.CENTER, 203, MEDIUM);
        text(canvas, L.t("OK: aspect"), 120, top + 23, 9, palette.accent, Paint.Align.CENTER, 203, MEDIUM);
    }

    private void drawCard(Canvas canvas, MainActivity.Screen screen, float top) {
        if (!screen.homePart(canvas, 5, 9, top, 231, top + 44)) return;
        tile(canvas, 9, top, 231, top + 44, 12, screen.shortcut >= 4);
        if (screen.cards.length == 0) {
            text(canvas, L.t("Carduri"), 20, top + 26, 13, MUTED, Paint.Align.LEFT, 195, MEDIUM);
            canvas.restore();
            return;
        }
        HomeCards.Card card = screen.cards[screen.cardIndex];
        float left = 20;
        if (card.type == 0) {
            screen.dashboardArt.weather(canvas, 28, top + 22, 23, card.weatherCode);
            left = 47;
        } else if (card.type == 3 && screen.favorites.length > 0) {
            Bitmap photo = screen.favorites[screen.favoriteIndex].photo;
            if (photo != null && !photo.isRecycled()) {
                paint.setColor(Color.WHITE);
                canvas.drawBitmap(photo, null, new RectF(15, top + 9, 40, top + 34), paint);
                left = 47;
            }
        }
        text(canvas, card.title, left, top + 13, 9, palette.accent, Paint.Align.LEFT, 165 - left, MEDIUM);
        text(canvas, card.main, left, top + 29, 12, INK, Paint.Align.LEFT, 223 - left, MEDIUM);
        text(canvas, card.detail, left, top + 40, 8, MUTED, Paint.Align.LEFT, 223 - left, REGULAR);
        if (screen.cards.length > 1) {
            String page = (screen.cardIndex + 1) + "/" + screen.cards.length + "  *";
            text(canvas, page, 220, top + 13, 9, palette.accent, Paint.Align.RIGHT, 47, MEDIUM);
        }
        canvas.restore();
    }

    private void drawShortcuts(Canvas canvas, MainActivity.Screen screen, float top) {
        if (!screen.homePart(canvas, 6, 9, top, 231, top + 42)) return;
        for (int i = 0; i < screen.shortcuts.length; i++) {
            int app = screen.shortcuts[i];
            float left = 9 + i * 56;
            tile(canvas, left, top, left + 54, top + 42, 10, screen.shortcut == i);
            icon(canvas, screen, app, left + 27, top + 16, .52f);
            text(canvas, screen.names[app], left + 27, top + 37, 9, INK, Paint.Align.CENTER, 49, MEDIUM);
        }
        canvas.restore();
    }

    private void drawApps(Canvas canvas, MainActivity.Screen screen) {
        text(canvas, L.t("Aplicații"), 11, 26, 22, INK, Paint.Align.LEFT, 157, MEDIUM);
        String count = screen.order.length == 0 ? "0" : (screen.selected + 1) + " / " + screen.order.length;
        text(canvas, count, 229, 24, 11, MUTED, Paint.Align.RIGHT, 60, MEDIUM);
        if (!screen.simple) {
            surface(canvas, 9, 36, 231, 59, 11, tile);
            text(canvas, "*", 22, 53, 13, palette.accent, Paint.Align.CENTER, 19, MEDIUM);
            text(canvas, AppCatalog.CATEGORIES[screen.category], 120, 52, 11, INK, Paint.Align.CENTER, 172, MEDIUM);
            text(canvas, "#", 218, 53, 12, palette.accent, Paint.Align.CENTER, 19, MEDIUM);
        }
        int app = screen.focusedApp();
        float top = screen.simple ? 42 : 66;
        if (app < 0) {
            text(canvas, L.t("Nicio aplicație"), 120, 144, 16, MUTED, Paint.Align.CENTER, 210, MEDIUM);
            return;
        }
        if (screen.list) drawList(canvas, screen, top);
        else drawGrid(canvas, screen, top);
        if (!screen.list) {
            text(canvas, screen.names[app], 11, 268, 11, palette.accent, Paint.Align.LEFT, 169, MEDIUM);
            if (screen.order.length > 9) {
                text(canvas, "0  ›", 230, 268, 11, palette.accent, Paint.Align.RIGHT, 35, MEDIUM);
            }
        }
    }

    private void drawGrid(Canvas canvas, MainActivity.Screen screen, float top) {
        int first = screen.selected / 9 * 9;
        for (int i = first; i < Math.min(first + 9, screen.order.length); i++) {
            int cell = i - first;
            int app = screen.order[i];
            float x = 9 + 75 * (cell % 3);
            float y = top + 62 * (cell / 3);
            tile(canvas, x, y, x + 72, y + 59, 12, i == screen.selected);
            icon(canvas, screen, app, x + 36, y + 23, .78f);
            String name = app == 9 ? "Android" : screen.names[app];
            text(canvas, name, x + 36, y + 53, 10, INK, Paint.Align.CENTER, 65, MEDIUM);
        }
    }

    private void drawList(Canvas canvas, MainActivity.Screen screen, float top) {
        int count = Math.min(6, screen.order.length);
        int first = Math.max(0, Math.min(screen.order.length - count, screen.selected - count / 2));
        for (int row = 0; row < count; row++) {
            int index = first + row;
            int app = screen.order[index];
            float y = top + row * 33;
            tile(canvas, 9, y, 229, y + 30, 9, index == screen.selected);
            icon(canvas, screen, app, 26, y + 15, .45f);
            text(canvas, screen.names[app], 46, y + 20, 13, INK, Paint.Align.LEFT, 173, MEDIUM);
        }
        if (screen.order.length > count) {
            float track = 195;
            float thumb = track * count / screen.order.length;
            float position = top + (track - thumb) * first / (screen.order.length - count);
            surface(canvas, 233, top, 236, top + track, 2, divider);
            surface(canvas, 233, position, 236, position + thumb, 2, palette.accent);
        }
    }

    private void drawFooter(Canvas canvas, MainActivity.Screen screen) {
        paint.setColor(palette.bar);
        canvas.drawRect(0, BODY, WIDTH, HEIGHT, paint);
        paint.setColor(divider);
        canvas.drawRect(0, BODY, WIDTH, BODY + 1, paint);
        String left = screen.editing ? L.t("Salvează") : screen.grid ? L.t("Opțiuni") : L.t("Notificări");
        String center = screen.grid ? (screen.focusedApp() >= 0 ? L.t("Deschide") : "")
            : screen.shortcut >= 0 ? L.t("Deschide") : L.t("Meniu");
        if (screen.editing) center = L.t(HomeCanvasLayout.NAMES[screen.editItem]);
        String right = screen.editing || screen.grid ? L.t("Înapoi") : screen.names[screen.rightApp];
        text(canvas, left, 9, 293, 11, INK, Paint.Align.LEFT, 67, MEDIUM);
        if (!center.isEmpty()) {
            surface(canvas, 78, 281, 162, 298, 8, focus);
            text(canvas, center, 120, 293, 10, INK, Paint.Align.CENTER, 76, MEDIUM);
        }
        text(canvas, right, 231, 293, 11, INK, Paint.Align.RIGHT, 67, MEDIUM);
    }

    private void icon(Canvas canvas, MainActivity.Screen screen, int app, float x, float y, float scale) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        screen.icon(canvas, app, 0, 0);
        canvas.restore();
    }

    private void tile(Canvas canvas, float l, float t, float r, float b, float radius, boolean selected) {
        surface(canvas, l, t, r, b, radius, selected ? focus : tile);
        if (selected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            surface(canvas, l + 1, t + 1, r - 1, b - 1, radius - 1, palette.accent);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void surface(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(l, t, r, b, radius, radius, paint);
    }

    private void text(Canvas canvas, String text, float x, float baseline, float size, int color,
                      Paint.Align alignment, float width, Typeface font) {
        prepare(size, font);
        label(canvas, KeypadText.fit(paint, text, width), x, baseline, color, alignment);
    }

    private void prepare(float size, Typeface font) {
        paint.setTypeface(font);
        paint.setTextSize(size * (size < 26 ? Math.min(1.18f, textScale) : 1));
    }

    private void label(Canvas canvas, String text, float x, float y, int color, Paint.Align align) {
        paint.setColor(color);
        paint.setTextAlign(align);
        canvas.drawText(text, x, y, paint);
    }
}
