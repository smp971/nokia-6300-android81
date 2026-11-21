package ro.leo.visual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Shared frame for the remaining full-screen Canvas tools, in 240 x 302 logical pixels. */
final class CanvasChrome {
    private final Paint paint;
    private final CanvasText labels;
    private float scale;
    CanvasChrome(Context context,Paint paint){this.paint=paint;labels=new CanvasText(context,paint);appearance(context);}
    void appearance(Context context){scale=Appearance.textScale(context);labels.appearance();}
    void header(Canvas canvas,String title,Appearance.Palette palette){UiChrome.header(canvas,title,palette,paint,scale);}
    void footer(Canvas canvas,Appearance.Palette palette,String left,String center,String right){
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(palette.bar);
        canvas.drawRect(0,274,240,302,paint);paint.setColor(palette.divider());canvas.drawRect(0,274,240,275,paint);
        labels.draw(canvas,left,7,293,12,android.graphics.Color.WHITE,Paint.Align.LEFT,72);
        labels.draw(canvas,center,120,293,12,palette.accent,Paint.Align.CENTER,76);
        labels.draw(canvas,right,233,293,12,android.graphics.Color.WHITE,Paint.Align.RIGHT,72);
    }
    void tabs(Canvas canvas,Appearance.Palette palette,String[] tabs,int selected){
        for(int i=0;i<tabs.length;i++){
            float left=i*240f/tabs.length,right=(i+1)*240f/tabs.length;
            boolean active=i==selected;
            paint.setColor(active?palette.selection:palette.bar);canvas.drawRect(left,36,right,72,paint);
            paint.setColor(active?palette.accent:palette.divider());canvas.drawRect(left,active?69:71,right,72,paint);
            labels.draw(canvas,tabs[i],(left+right)/2,59,active?17:15,
                active?palette.ink:palette.secondary(),Paint.Align.CENTER,right-left-8);
        }
    }
}
