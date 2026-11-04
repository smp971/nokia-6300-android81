package ro.leo.visual;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Small original line glyphs for the Sxmo-inspired list. */
final class SxmoIcon extends Drawable {
 private final Paint p=new Paint(3);private final int index;
 SxmoIcon(int index,int size){this.index=index;setBounds(0,0,size,size);}
 public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/20f,b.height()/20f);p.setColor(0xffe3e8d4);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
 switch(index){
 case 0:Path phone=new Path();phone.moveTo(4,3);phone.cubicTo(0,11,9,19,17,16);phone.lineTo(13,12);phone.lineTo(10,14);phone.lineTo(6,10);phone.lineTo(8,7);phone.close();c.drawPath(phone,p);break;
 case 1:c.drawCircle(10,6,3,p);c.drawArc(3,10,17,21,190,160,false,p);break;
 case 2:c.drawRect(2,3,18,14,p);c.drawLine(4,14,4,18,p);c.drawLine(4,18,9,14,p);break;
 case 3:c.drawCircle(10,10,8,p);c.drawLine(10,4,10,10,p);c.drawLine(10,10,14,12,p);break;
 case 6:c.drawLine(8,4,8,15,p);c.drawLine(8,4,16,2,p);c.drawLine(16,2,16,13,p);c.drawOval(3,13,8,17,p);c.drawOval(11,11,16,15,p);break;
 case 7:Path folder=new Path();folder.moveTo(2,6);folder.lineTo(2,3);folder.lineTo(8,3);folder.lineTo(10,6);folder.lineTo(18,6);folder.lineTo(16,17);folder.lineTo(2,17);folder.close();c.drawPath(folder,p);break;
 case 23:c.drawRect(1,3,19,17,p);c.drawLine(4,7,7,10,p);c.drawLine(7,10,4,13,p);c.drawLine(10,13,15,13,p);break;
 default:for(int y=0;y<3;y++)for(int x=0;x<3;x++)c.drawRect(2+x*6,2+y*6,5+x*6,5+y*6,p);break;
 }c.restore();}
 public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
