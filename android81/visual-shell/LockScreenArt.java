package ro.leo.visual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import ro.leo.i18n.L;

/** Shared 240 × 320 lock-screen artwork for the active theme and its preview. */
final class LockScreenArt {
    private static final Typeface CLOCK = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final int SECONDARY = 0xffe0dbe9;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF(0, 0, 240, 320);
    private final DashboardArt icons;
    private final CanvasText labels;
    private Appearance.Palette backgroundPalette;
    private LinearGradient gradient;

    LockScreenArt(Context context) {
        icons = new DashboardArt(context);labels=new CanvasText(context,paint);
    }

    void background(Canvas canvas, Appearance.Palette palette, Bitmap wallpaper) {
        if (wallpaper != null) {
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(wallpaper, null, bounds, paint);
            paint.setColor(0x99060913);
            canvas.drawRect(bounds, paint);
            return;
        }
        if (palette != backgroundPalette) {
            backgroundPalette = palette;
            int top = Appearance.mix(palette.background, palette.card, .72f);
            gradient = new LinearGradient(0, 0, 160, 254, top, palette.background, Shader.TileMode.CLAMP);
        }
        paint.setShader(gradient);
        canvas.drawRect(bounds, paint);
        paint.setShader(null);
    }

    void clock(Canvas canvas, String time, String date, int offset) {
        paint.setTypeface(CLOCK);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(62);
        float width = paint.measureText(time);
        if (width > 214) paint.setTextSize(62 * 214 / width);
        canvas.drawText(time, 120, 109 + offset, paint);
        text(canvas, date, 120, 133 + offset, 15, SECONDARY, Paint.Align.CENTER, 212);
    }

    void weather(Canvas canvas, Appearance.Palette palette, HomeCards.Card weather) {
        panel(canvas,palette.card,12,152,228,222,12);
        icons.weather(canvas,36,184,32,weather==null?-1:weather.weatherCode);
        text(canvas,weather==null?L.t("Meteo"):weather.title,61,167,11,SECONDARY,Paint.Align.LEFT,157);
        String[] lines=labels.lines(weather==null?L.t("Prognoză neîncărcată"):weather.main,16,157);
        text(canvas,lines[0],61,185,16,Color.WHITE,Paint.Align.LEFT,157);
        text(canvas,lines[1],61,201,16,Color.WHITE,Paint.Align.LEFT,157);
        if(weather!=null&&!weather.detail.isEmpty()){
            long now=System.currentTimeMillis();
            text(canvas,WeatherFreshness.label(now,weather.updatedAt),61,215,10,
                WeatherFreshness.stale(now,weather.updatedAt)?0xffffce75:SECONDARY,Paint.Align.LEFT,157);
        }
    }

    void battery(Canvas canvas, int percent, boolean charging) {
        text(canvas, (charging ? "+ " : "") + percent + "%", 204, 22, 11,
            Color.WHITE, Paint.Align.RIGHT, 56);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        canvas.drawRoundRect(210, 13, 226, 23, 2, 2, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(227, 16, 228, 20, paint);
        canvas.drawRect(212, 15, 212 + 12 * percent / 100f, 21, paint);
    }

    void panel(Canvas canvas, int color, float left, float top, float right, float bottom, float radius) {
        paint.setColor(color);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    void text(Canvas canvas, String value, float x, float y, int size, int color,
              Paint.Align alignment, float width) {
        labels.draw(canvas,value,x,y,size,color,alignment,width);
    }
}
