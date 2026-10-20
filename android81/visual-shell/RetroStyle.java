package ro.leo.visual;
import android.graphics.*;
/** Original vector artwork, inspired by compact S60-era phone interfaces. */
final class RetroStyle {
    final Paint p=new Paint(3);final Path path=new Path();private final Typeface font=Typeface.create("sans-serif",Typeface.NORMAL);
    void text(Canvas c,String value,float x,float y,float size,int color,Paint.Align align){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTypeface(font);p.setTextSize(size);p.setTextAlign(align);p.setColor(color);c.drawText(value,x,y,p);}
    void fit(Canvas c,String value,float x,float y,float size,float width,int color,Paint.Align align){p.setTypeface(font);p.setTextSize(size);while(value.length()>1&&p.measureText(value)>width){value=value.substring(0,value.length()-1);}text(c,value,x,y,size,color,align);}
    void panel(Canvas c,float l,float t,float r,float b,int top,int bottom){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(top);c.drawRoundRect(l,t,r,b,3,3,p);}
    void background(Canvas c,Appearance.Palette a){c.drawColor(a.background);}
    void title(Canvas c,String value,int accent){text(c,value,12,28,23,Color.WHITE,Paint.Align.LEFT);p.setColor(accent);c.drawRect(12,37,228,39,p);}
    void digits(Canvas c,String value,float right,float baseline,int color){
        if(!value.matches("[-0-9.]+")||value.length()>14){fit(c,value,right,baseline,28,202,color,Paint.Align.RIGHT);return;}
        float scale=Math.min(1.8f,202f/(value.length()*16));c.save();c.translate(right-value.length()*16*scale,baseline-25*scale);c.scale(scale,scale);
        int[] masks={63,6,91,79,102,109,125,7,127,111};float[][] bars={{3,0,11,2},{11,2,13,11},{11,13,13,22},{3,22,11,24},{1,13,3,22},{1,2,3,11},{3,11,11,13}};
        for(char ch:value.toCharArray()){int mask=ch=='-'?64:ch=='.'?0:masks[ch-'0'];for(int k=0;k<7;k++){p.setColor((mask&(1<<k))!=0?color:0x1026323a);float[] b=bars[k];c.drawRoundRect(b[0],b[1],b[2],b[3],.6f,.6f,p);}if(ch=='.'){p.setColor(color);c.drawCircle(4,23,1.5f,p);}c.translate(16,0);}c.restore();
    }
}
