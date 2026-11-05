package ro.leo.visual;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Small shared SIM-slot symbol; identity remains visible without long SIM prefixes. */
final class SimIcon extends Drawable {
 final Paint p=new Paint(3);final int slot,color;
 SimIcon(int slot,int size,int color){this.slot=slot;this.color=color;setBounds(0,0,size,size);}
 public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/20f,b.height()/20f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f);Path chip=new Path();chip.moveTo(4,2);chip.lineTo(12,2);chip.lineTo(17,7);chip.lineTo(17,18);chip.lineTo(4,18);chip.close();c.drawPath(chip,p);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(10);p.setTextAlign(Paint.Align.CENTER);c.drawText(""+(slot+1),10,14,p);c.restore();}
 public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
