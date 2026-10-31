package ro.leo.visual;
import android.graphics.*;

/** Small native vector symbols; no bitmap downloads or animation timers. */
final class DashboardArt {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    void sim(Canvas c,float x,float baseline,int slot,boolean available){
        c.save();c.translate(x,baseline-7);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);
        p.setColor(available?(slot==0?0xff67dbe4:0xffb7a1ff):0xff8a99ab);
        path.reset();path.moveTo(-5,-7);path.lineTo(2,-7);path.lineTo(6,-3);path.lineTo(6,7);path.lineTo(-5,7);path.close();c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(8);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(Integer.toString(slot+1),.5f,4,p);c.restore();
    }
    void weather(Canvas c,float x,float y,float size,int code){
        c.save();c.translate(x-size/2,y-size/2);c.scale(size/32,size/32);p.setStyle(Paint.Style.FILL);
        if(code>=0&&code<=2){
            p.setColor(0xffffcc54);c.drawCircle(12,11,6,p);p.setStrokeWidth(1.5f);
            for(int ray=0;ray<8;ray++){double angle=ray*Math.PI/4;c.drawLine(12+(float)Math.cos(angle)*8,11+(float)Math.sin(angle)*8,12+(float)Math.cos(angle)*10,11+(float)Math.sin(angle)*10,p);}
        }
        if(code!=0){
            p.setColor(code<0?0xff92a6bb:0xffbadbea);
            c.drawCircle(13,16,7,p);c.drawCircle(21,17,6,p);c.drawRoundRect(5,16,28,23,3,3,p);
        }
        if(code==45||code==48){p.setColor(0xffb5c7d8);p.setStrokeWidth(1.5f);c.drawLine(4,26,26,26,p);c.drawLine(8,29,30,29,p);}
        else if((code>=71&&code<=77)||code==85||code==86){p.setColor(0xffe6f7ff);p.setStrokeWidth(1.2f);for(int i=9;i<=25;i+=8){c.drawLine(i-2,27,i+2,27,p);c.drawLine(i,25,i,29,p);}}
        else if(code>=95&&code<=99){p.setColor(0xffffcd58);path.reset();path.moveTo(17,20);path.lineTo(11,27);path.lineTo(17,26);path.lineTo(14,32);path.lineTo(24,23);path.lineTo(18,24);path.close();c.drawPath(path,p);}
        else if(code>=51&&code<=82){p.setColor(0xff5fc4ff);p.setStrokeWidth(2);for(int i=9;i<=25;i+=8)c.drawLine(i,25,i-2,29,p);}
        if(code<0){p.setColor(0xff243345);p.setTextSize(13);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);c.drawText("?",16,22,p);}
        c.restore();
    }
}
