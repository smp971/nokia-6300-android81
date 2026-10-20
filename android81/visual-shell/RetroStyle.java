package ro.leo.visual;
import android.graphics.*;
/** Original vector artwork, inspired by compact S60-era phone interfaces. */
final class RetroStyle {
    final Paint p=new Paint(3);final Path path=new Path();private final Typeface font=Typeface.create("sans-serif-condensed",Typeface.BOLD);private Appearance.Palette lastPalette;private Shader backgroundShader;
    void text(Canvas c,String value,float x,float y,float size,int color,Paint.Align align){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTypeface(font);p.setTextSize(size);p.setTextAlign(align);p.setColor(color);c.drawText(value,x,y,p);}
    void fit(Canvas c,String value,float x,float y,float size,float width,int color,Paint.Align align){p.setTypeface(font);p.setTextSize(size);while(value.length()>1&&p.measureText(value)>width){value=value.substring(0,value.length()-1);}text(c,value,x,y,size,color,align);}
    void panel(Canvas c,float l,float t,float r,float b,int top,int bottom){p.setShader(new LinearGradient(0,t,0,b,top,bottom,Shader.TileMode.CLAMP));c.drawRoundRect(l,t,r,b,8,8,p);p.setShader(null);p.setColor(0x55ffffff);c.drawRoundRect(l+1,t+1,r-1,t+3,2,2,p);}
    void background(Canvas c,Appearance.Palette a){c.drawColor(a.background);if(a!=lastPalette){backgroundShader=new LinearGradient(0,0,240,270,a.gradient,null,Shader.TileMode.CLAMP);lastPalette=a;}p.setShader(backgroundShader);c.drawRect(0,0,240,276,p);p.setShader(null);for(int i=0;i<5;i++){p.setColor(0x08000000);c.drawCircle(224,180,50+i*23,p);}}
    void title(Canvas c,String value,int accent){text(c,value,13,30,25,0xbb000000,Paint.Align.LEFT);text(c,value,12,28,25,Color.WHITE,Paint.Align.LEFT);p.setColor(accent);c.drawRect(12,37,228,39,p);}
    void digits(Canvas c,String value,float right,float baseline,int color){
        if(!value.matches("[-0-9.]+")||value.length()>14){fit(c,value,right,baseline,28,202,color,Paint.Align.RIGHT);return;}
        float scale=Math.min(1.8f,202f/(value.length()*16));c.save();c.translate(right-value.length()*16*scale,baseline-25*scale);c.scale(scale,scale);
        int[] masks={63,6,91,79,102,109,125,7,127,111};float[][] bars={{3,0,11,2},{11,2,13,11},{11,13,13,22},{3,22,11,24},{1,13,3,22},{1,2,3,11},{3,11,11,13}};
        for(char ch:value.toCharArray()){int mask=ch=='-'?64:ch=='.'?0:masks[ch-'0'];for(int k=0;k<7;k++){p.setColor((mask&(1<<k))!=0?color:0x1026323a);float[] b=bars[k];c.drawRoundRect(b[0],b[1],b[2],b[3],.6f,.6f,p);}if(ch=='.'){p.setColor(color);c.drawCircle(4,23,1.5f,p);}c.translate(16,0);}c.restore();
    }
}
