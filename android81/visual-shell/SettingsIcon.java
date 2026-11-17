package ro.leo.visual;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Draws a single glyph from the Gaia Settings icon font. */
final class SettingsIcon extends Drawable {
    enum Glyph {
        WIFI(0xf1d2), BLUETOOTH(0xf124), TETHERING(0xf1b8), SIM(0xf1ab), USB(0xf1c7),
        MENU(0xf17a), BRIGHTNESS(0xf125), THEMES(0xf1b9), KEYBOARD(0xf172),
        SOUND(0xf1af), HEADPHONES(0xf169), MUTE(0xf180), NOTIFICATIONS(0xf183),
        TIME(0xf1bc), LOCK(0xf178), ADDONS(0xf108), LOCATION(0xf177),
        ACCESSIBILITY(0xf104), STORAGE(0xf1b5), FILES(0xf179), DOWNLOAD(0xf148), SYNC(0xf1b7),
        BATTERY(0xf115), INFO(0xf170), HELP(0xf16a), RELOAD(0xf18f), DEVELOPER(0xf142),
        SETTINGS(0xf1a0);

        final String text;

        Glyph(int codePoint) {
            text = String.valueOf((char) codePoint);
        }
    }

    private static Typeface typeface;
    private final Glyph glyph;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect inkBounds = new Rect();
    private final ColorStateList colors;

    SettingsIcon(Context context, Glyph glyph, int size, ColorStateList colors) {
        if (typeface == null) {
            typeface = Typeface.createFromAsset(context.getAssets(), "settings-icons/gaia-icons.ttf");
        }
        this.glyph = glyph;
        this.colors = colors;
        paint.setTypeface(typeface);
        paint.setTextSize(size);
        paint.setColor(colors.getDefaultColor());
        paint.getTextBounds(glyph.text, 0, glyph.text.length(), inkBounds);
        setBounds(0, 0, size, size);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawText(glyph.text, bounds.exactCenterX() - inkBounds.exactCenterX(),
            bounds.exactCenterY() - inkBounds.exactCenterY(), paint);
    }

    @Override public boolean isStateful() {
        return colors.isStateful();
    }

    @Override protected boolean onStateChange(int[] state) {
        int color = colors.getColorForState(state, colors.getDefaultColor());
        if (paint.getColor() == color) return false;
        paint.setColor(color);
        invalidateSelf();
        return true;
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
