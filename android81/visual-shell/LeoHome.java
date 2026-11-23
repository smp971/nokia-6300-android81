package ro.leo.visual;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import ro.leo.i18n.L;

/** Keypad-first rendering for Home, the app menu and the Home editor. */
final class LeoHome {
    private static final int WIDTH = 240;
    private static final int BODY = 276;
    private static final int HEIGHT = 302;
    private static final int APP_TILE_SIZE = 69;
    private static final int APP_TILE_GAP = 4;
    private static final int APP_GRID_LEFT = (WIDTH - APP_TILE_SIZE * 3 - APP_TILE_GAP * 2) / 2;
    private static final int INK = 0xfffaf8ff;
    private static final int MUTED = 0xffd3cddc;
    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface CLOCK = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect glyphBounds = new Rect();
    private final RectF wallpaperBounds = new RectF(0, 0, WIDTH, BODY);
    private Appearance.Palette palette;
    private int tile;
    private int focus;
    private int divider;
    private float textScale;
    private int homeStyle;
    private boolean highContrast;
    private HomeWeatherArt weatherArt;
    private final RectF weatherBounds=new RectF(9,137,231,180);

    void draw(Canvas canvas, MainActivity.Screen screen) {
        if (palette != screen.palette) {
            palette = screen.palette;
            tile = Appearance.mix(palette.background, palette.card, .88f);
            focus = Appearance.mix(tile, palette.accent, .22f);
            divider = Appearance.mix(palette.background, palette.accent, .26f);
        }
        textScale = screen.textScale;
        homeStyle=screen.homeStyle;
        highContrast=screen.highContrast;
        if(weatherArt==null)weatherArt=new HomeWeatherArt(screen.getContext());
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
        int simSlots = screen.editing ? 3 : screen.showSims ? screen.simSlots : 0;
        if (screen.wallpaper != null && homeStyle==0 && !highContrast) {
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(screen.wallpaper, null, wallpaperBounds, paint);
            paint.setColor(0x66000000);
            canvas.drawRect(wallpaperBounds, paint);
        }
        if (screen.homePart(canvas, 0, 6, 4, 234, 131)) {
            if(homeStyle==0&&!highContrast)
                surface(canvas,6,4,234,131,9,Appearance.mix(palette.background,palette.card,.32f));
            canvas.restore();
        }
        if (simSlots != 0) drawSims(canvas, screen, simSlots);
        if (!screen.networkBanner.isEmpty() || screen.editing) {
            String banner = screen.networkBanner.isEmpty() ? L.t("Stare rețea") : screen.networkBanner;
            editableText(canvas, screen, 1, banner, 12, 129, 10, palette.accent, MEDIUM);
        }
        editableText(canvas, screen, 2, screen.time, 12, 89,
            screen.compactClock ? 40 : 56, INK, highContrast?MEDIUM:CLOCK);
        editableText(canvas, screen, 3, screen.date, 14, 112, 13, palette.secondary(), REGULAR);
        if((screen.weatherAvailable()||screen.editing)&&screen.homePart(canvas,8,9,137,231,180)){
            if(screen.shortcut==MainActivity.Screen.WEATHER_FOCUS)tile(canvas,9,137,231,180,8,true);
            weatherArt.draw(canvas,palette,screen.weather,weatherBounds,System.currentTimeMillis());
            canvas.restore();
        }
        if(screen.cardAvailable()||screen.editing)drawCard(canvas,screen,185);
        drawShortcuts(canvas, screen, 235);

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

    private void drawSims(Canvas canvas, MainActivity.Screen screen, int slots) {
        float bottom=9+25*textScale;
        RectF first = new RectF(11, 9, 117, bottom);
        RectF second = new RectF(123, 9, 229, bottom);
        RectF full = new RectF(11, 9, 229, bottom);
        screen.homeLayout.splitSims(full, first, second);
        for (int slot = 0; slot < 2; slot++) {
            if ((slots & (1 << slot)) == 0) continue;
            int item = slot == 0 ? 4 : 7;
            RectF bounds = slots != 3 && screen.homeLayout.hasDefaultTransform(item)
                    ? full : slot == 0 ? first : second;
            if (!screen.homePart(canvas, item, bounds.left, bounds.top, bounds.right, bounds.bottom))
                continue;
            if(homeStyle==0&&!highContrast)surface(canvas,bounds.left,bounds.top,bounds.right,bounds.bottom,6,palette.bar);
            float baseline = bounds.top + 17*textScale;
            screen.dashboardArt.sim(canvas, bounds.left + 12, baseline, slot, screen.inService[slot]);
            String technology = HomeNetworkBadges.technology(screen.simTechnology[slot],screen.simVolte[slot]);
            boolean internet=screen.simInternet[slot];
            // Two compact SIM cards need separate operator/detail baselines to
            // keep the carrier readable beside VoLTE, RAT and the globe.
            if(bounds.width()<150){
                text(canvas,screen.simLabels[slot],bounds.left+23,bounds.top+10.5f*textScale,10,INK,Paint.Align.LEFT,bounds.width()-29,MEDIUM);
                float detailBaseline=bounds.top+22.5f*textScale;
                text(canvas,technology,bounds.left+23,detailBaseline,9.5f,palette.accent,Paint.Align.LEFT,bounds.width()-29-(internet?14:0),MEDIUM);
                if(internet)screen.dashboardArt.internet(canvas,bounds.right-10,detailBaseline,palette.accent);
                canvas.restore();continue;
            }
            float suffixWidth = 0;
            float detailRight=bounds.right-6;
            if(internet){screen.dashboardArt.internet(canvas,bounds.right-10,baseline,palette.accent);detailRight-=14;suffixWidth=14;}
            if (!technology.isEmpty()) {
                prepare(10, MEDIUM);
                suffixWidth += paint.measureText(technology) + 6;
                text(canvas, technology, detailRight, baseline, 10, palette.accent, Paint.Align.RIGHT,
                    suffixWidth, MEDIUM);
            }
            text(canvas, screen.simLabels[slot], bounds.left + 23, baseline, 10, INK, Paint.Align.LEFT,
                    bounds.width() - 29 - suffixWidth, MEDIUM);
            canvas.restore();
        }
    }

    private void drawCard(Canvas canvas, MainActivity.Screen screen, float top) {
        if (!screen.homePart(canvas, 5, 9, top, 231, top + 46)) return;
        tile(canvas, 9, top, 231, top + 46, 8, screen.shortcut >= 4);
        if (screen.cards.length == 0) {
            text(canvas, L.t("Carduri"), 20, top + 26, 13, MUTED, Paint.Align.LEFT, 195, MEDIUM);
            canvas.restore();
            return;
        }
        HomeCards.Card card = screen.cards[screen.cardIndex];
        if(card.type==5){
            String[] lines=card.main.split(" · ",2);
            text(canvas,lines[0],20,top+19,14,INK,Paint.Align.LEFT,198,MEDIUM);
            text(canvas,lines.length>1?lines[1]:card.title,20,top+38,12,
                lines.length>1?INK:palette.secondary(),Paint.Align.LEFT,198,REGULAR);
            canvas.restore();return;
        }
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
        text(canvas, card.title, left, top + 15, 10, palette.accent, Paint.Align.LEFT, 165 - left, MEDIUM);
        text(canvas, card.main, left, top + 36, 13, INK, Paint.Align.LEFT,
            223 - left, MEDIUM);
        if (screen.cards.length > 1) {
            String page = (screen.cardIndex + 1) + "/" + screen.cards.length ;
            text(canvas, page, 220, top + 13, 9, palette.accent, Paint.Align.RIGHT, 47, MEDIUM);
        }
        canvas.restore();
    }

    private void drawShortcuts(Canvas canvas, MainActivity.Screen screen, float top) {
        if (!screen.homePart(canvas, 6, 9, top, 231, top + 39)) return;
        for (int i = 0; i < screen.shortcuts.length; i++) {
            int app = screen.shortcuts[i];
            float left = 9 + i * 56;
            if(screen.shortcut==i)tile(canvas,left,top,left+54,top+39,7,true);
            icon(canvas, screen, app, left + 27, top + 13, .46f);
            text(canvas, screen.names[app], left + 27, top + 37, 9, INK, Paint.Align.CENTER, 52, MEDIUM);
        }
        canvas.restore();
    }

    private void drawApps(Canvas canvas, MainActivity.Screen screen) {
        text(canvas, L.t("Aplicații"), 11, 26, 22, INK, Paint.Align.LEFT, 157, MEDIUM);
        String count = screen.order.length == 0 ? "0" : (screen.selected + 1) + " / " + screen.order.length;
        text(canvas, count, 229, 24, 11, MUTED, Paint.Align.RIGHT, 60, MEDIUM);
        int app = screen.focusedApp();
        if (app < 0) {
            text(canvas, L.t("Nicio aplicație"), 120, 144, 16, MUTED, Paint.Align.CENTER, 210, MEDIUM);
            return;
        }
        text(canvas, screen.names[app], 11, 44, 13, palette.accent, Paint.Align.LEFT, 218, MEDIUM);
        if (screen.list) drawList(canvas, screen, 53);
        else drawGrid(canvas, screen, 53);
    }

    private void drawGrid(Canvas canvas, MainActivity.Screen screen, float top) {
        int first = screen.selected / 9 * 9;
        for (int i = first; i < Math.min(first + 9, screen.order.length); i++) {
            int cell = i - first;
            int app = screen.order[i];
            int step = APP_TILE_SIZE + APP_TILE_GAP;
            float x = APP_GRID_LEFT + step * (cell % 3);
            float y = top + step * (cell / 3);
            float center = x + APP_TILE_SIZE / 2f;
            tile(canvas, x, y, x + APP_TILE_SIZE, y + APP_TILE_SIZE, 5, i == screen.selected);
            icon(canvas, screen, app, center, y + 27, .84f);
            String name = app == 9 ? "Android" : screen.names[app];
            text(canvas, name, center, y + 60, 10, INK, Paint.Align.CENTER, APP_TILE_SIZE - 7, MEDIUM);
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
            : screen.shortcut != -1 ? L.t("Deschide") : L.t("Aplicații");
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
        int count=app==0?UnreadState.calls:app==2?UnreadState.sms:0;
        if(screen.showUnread&&count>0){
            String badge=HomeStyle.badge(count);
            prepare(9,MEDIUM);float width=Math.max(13,paint.measureText(badge)+6);
            float right=x+26*scale,top=y-29*scale;
            Paint.FontMetrics metrics=paint.getFontMetrics();
            float height=Math.max(13,metrics.descent-metrics.ascent+2);
            surface(canvas,right-width,top,right,top+height,5,palette.accent);
            label(canvas,badge,right-width/2,top+1-metrics.ascent,HomeStyle.onAccent(palette.accent),Paint.Align.CENTER);
        }
    }

    private void tile(Canvas canvas, float l, float t, float r, float b, float radius, boolean selected) {
        if(highContrast)radius=1;
        if(homeStyle!=1||selected)surface(canvas, l, t, r, b, radius, selected ? focus : tile);
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
        paint.setTextSize(size * (size < 26 ? textScale : 1));
    }

    private void label(Canvas canvas, String text, float x, float y, int color, Paint.Align align) {
        paint.setColor(color);
        paint.setTextAlign(align);
        canvas.drawText(text, x, y, paint);
    }
}
