package ro.leo.visual;
import android.graphics.*;import android.graphics.drawable.Drawable;import java.util.Locale;
/** Original compact initials; does not query contacts or retain personal data. */
final class AvatarArt extends Drawable {
 final Paint p=new Paint(3);final String initial;final int color;
 AvatarArt(String label,int size){String s=label==null?"":label.trim();initial=s.isEmpty()?"?":s.substring(0,s.offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT);int[] colors={0xff397d85,0xff5567a6,0xffa45978,0xff79713b,0xff617b4e};color=colors[Math.floorMod(s.hashCode(),colors.length)];setBounds(0,0,size,size);}
 public void draw(Canvas c){Rect b=getBounds();p.setColor(color);c.drawRoundRect(b.left,b.top,b.right,b.bottom,b.width()*.28f,b.height()*.28f,p);p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(b.height()*.46f);p.setTextAlign(Paint.Align.CENTER);c.drawText(initial,b.centerX(),b.centerY()-(p.ascent()+p.descent())/2,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
