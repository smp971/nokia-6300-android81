package ro.leo.visual;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small shared list position. Updates its own bounds without rebuilding the header. */
final class ListPositionDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Appearance.Palette palette;
    private final float density;
    private String value="";
    ListPositionDrawable(Appearance.Palette palette,float density) {
        this.palette=palette;this.density=density;
        setBounds(0,0,Math.round(40*density),Math.round(23*density));
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
    }
    void position(int selected,int count) {
        String next=(count==0?0:Math.max(0,Math.min(selected,count-1))+1)+"/"+count;
        if(!value.equals(next)){value=next;invalidateSelf();}
    }
    String value(){return value;}
    public void draw(Canvas canvas) {
        Rect b=getBounds();paint.setColor(palette.background);
        canvas.drawRoundRect(b.left,b.top,b.right,b.bottom,4*density,4*density,paint);
        paint.setColor(palette.accent);paint.setTextSize(10*density);paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(value,b.exactCenterX(),b.exactCenterY()-(paint.ascent()+paint.descent())/2,paint);
    }
    public void setAlpha(int alpha){paint.setAlpha(alpha);}
    public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
