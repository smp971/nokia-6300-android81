package ro.leo.visual;
import android.graphics.*;import android.graphics.drawable.*;
/** Leo Night: native shapes, no generated bitmap dependency. */
final class PhoneTheme {
 static Appearance.Palette palette(){return new Appearance.Palette(0xff141025,0xff0e0b1b,0xff242438,0xff42d5c4,0xff17494b,Color.WHITE);}
 static GradientDrawable surface(Appearance.Palette a,int color,boolean focused){GradientDrawable d=Appearance.surface(color,4);d.setStroke(focused?2:1,focused?a.accent:0xff3a3d55);return d;}
 static StateListDrawable focus(Appearance.Palette a,int color){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},surface(a,color,true));d.addState(new int[]{android.R.attr.state_selected},surface(a,color,true));d.addState(new int[]{},surface(a,color,false));return d;}
 static final class Avatar extends Drawable {
  final Paint p=new Paint(3);final int color;Avatar(int slot,int size){color=new int[]{0xffee4aa0,0xff36c96b,0xff31c5ed}[slot%3];setBounds(0,0,size,size);}
  public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/32f,getBounds().height()/32f);p.setColor(color);c.drawCircle(16,8,6,p);c.drawRoundRect(5,16,27,30,9,9,p);c.restore();}public void setAlpha(int v){p.setAlpha(v);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
