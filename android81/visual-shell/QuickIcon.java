package ro.leo.visual;
import android.graphics.*;import android.graphics.drawable.Drawable;
final class QuickIcon extends Drawable {
    private final int kind;private final Paint p=new Paint(3);private final Path path=new Path();
    QuickIcon(int type,int size){kind=type;setBounds(0,0,size,size);}
    public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/36f,getBounds().height()/36f);p.setColor(new int[]{0xff1998df,0xff5c76eb,0xffeb9750,0xffb267db,0xffe0b348,0xff3bbd9d,0xffe77394}[Math.min(6,kind)]);p.setStyle(Paint.Style.FILL);c.drawRoundRect(0,0,36,36,8,8,p);p.setColor(Color.WHITE);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
        if(kind==0||kind==2){for(int i=0;i<3;i++)c.drawArc(7+i*4,10+i*4,29-i*4,32-i*4,220,100,false,p);c.drawCircle(18,26,1,p);if(kind==2){c.drawLine(18,6,18,11,p);}}
        else if(kind==1){path.moveTo(17,6);path.lineTo(25,13);path.lineTo(11,25);path.lineTo(17,30);path.lineTo(17,6);path.lineTo(25,23);path.lineTo(11,11);c.drawPath(path,p);path.reset();}
        else if(kind==4){c.drawCircle(18,18,6,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45,18,18);c.drawLine(18,5,18,8,p);c.restore();}}
        else if(kind==5){c.drawRoundRect(10,7,27,29,2,2,p);c.drawRect(14,18,23,25,p);c.drawLine(14,21,23,21,p);}
        else{path.reset();path.moveTo(7,15);path.lineTo(13,15);path.lineTo(19,9);path.lineTo(19,27);path.lineTo(13,21);path.lineTo(7,21);path.close();c.drawPath(path,p);c.drawArc(13,10,29,26,-55,110,false,p);if(kind==6)c.drawArc(11,6,33,30,-50,100,false,p);}p.setStyle(Paint.Style.FILL);c.restore();
    }
    public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
