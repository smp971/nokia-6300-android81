package ro.leo.visual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Shared scale-aware, bounded labels for the remaining Canvas pages. */
final class CanvasText {
    private final Context context;
    private final Paint paint;
    private float scale;
    CanvasText(Context context,Paint paint){this.context=context;this.paint=paint;appearance();}
    void appearance(){scale=Appearance.textScale(context);}
    void prepare(int size,int color,Paint.Align align){
        paint.setTypeface(Typeface.DEFAULT);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size*scale);paint.setTextAlign(align);paint.setColor(color);
    }
    void draw(Canvas canvas,String text,float x,float y,int size,int color,Paint.Align align,float width){
        prepare(size,color,align);canvas.drawText(KeypadText.fit(paint,text,width),x,y,paint);
    }
    String[] lines(String text,int size,float width){prepare(size,android.graphics.Color.WHITE,Paint.Align.LEFT);return KeypadText.twoLines(paint,text,width);}
}
