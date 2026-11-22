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
    private final Context context;
    private Appearance.Palette activePalette;
    private final HomeWeatherArt weatherArt;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF(0, 0, 240, 320);
    private final RectF weatherBounds = new RectF(12,157,228,201);
    private final DashboardArt icons;
    private final CanvasText labels;
    private Appearance.Palette backgroundPalette;
    private LinearGradient gradient;

    LockScreenArt(Context context) {
        this.context=context;
        icons = new DashboardArt(context);labels=new CanvasText(context,paint);
        weatherArt=new HomeWeatherArt(context);
    }

    void background(Canvas canvas, Appearance.Palette palette, Bitmap wallpaper) {
        activePalette=palette;labels.appearance();
        if (wallpaper != null && HomeStyle.index(context)==0 && !HomeStyle.contrast(context)) {
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(wallpaper, null, bounds, paint);
            paint.setColor(0x99060913);
            canvas.drawRect(bounds, paint);
            return;
        }
        if(HomeStyle.index(context)==1||HomeStyle.contrast(context)){canvas.drawColor(palette.background);return;}
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
        paint.setTypeface(HomeStyle.contrast(context)?Typeface.create("sans-serif-medium",0):CLOCK);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(62);
        float width = paint.measureText(time);
        if (width > 214) paint.setTextSize(62 * 214 / width);
        canvas.drawText(time, 120, 113 + offset, paint);
        text(canvas, date, 120, 138 + offset, 13, activePalette.secondary(), Paint.Align.CENTER, 212);
    }

    void weather(Canvas canvas, Appearance.Palette palette, HomeCards.Card weather) {
        if(HomeStyle.index(context)!=1)panel(canvas,palette.card,12,157,228,201,8);
        weatherArt.draw(canvas,palette,weather,weatherBounds,System.currentTimeMillis());
    }

    void sim(Canvas canvas,String description,int slot,float baseline,boolean available){
        icons.sim(canvas,18,baseline,slot,available);
        String prefix="SIM "+(slot+1)+" · ";
        text(canvas,description.startsWith(prefix)?description.substring(prefix.length()):description,
            31,baseline,11,Color.WHITE,Paint.Align.LEFT,197);
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
        if(HomeStyle.contrast(context))radius=1;
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    void text(Canvas canvas, String value, float x, float y, int size, int color,
              Paint.Align alignment, float width) {
        labels.prepare(size,color,alignment);
        paint.setTextSize(size*HomeStyle.scale(context));
        canvas.drawText(KeypadText.fit(paint,value,width),x,y,paint);
    }
}
