package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import java.io.*;
/** Four original, deterministic wallpapers. No downloaded artwork or animation. */
final class StockWallpapers {
    static final String[] NAMES=L.array(new String[]{"Aurora mov","Orbită albastră","Valuri de jad","Apus geometric"});
    static void draw(Canvas c,int kind,float width,float height){
        c.save();c.scale(width/240f,height/302f);Paint p=new Paint(3);Path path=new Path();
        int[][] colors={{0xff120e35,0xff713b9c,0xffdf81b3},{0xff031529,0xff124887,0xff50afc4},{0xff082a2d,0xff257263,0xff93c5aa},{0xff321728,0xffa74d68,0xffffb77c}};
        p.setShader(new LinearGradient(0,0,240,302,colors[kind],null,Shader.TileMode.CLAMP));c.drawRect(0,0,240,302,p);p.setShader(null);
        if(kind==1){p.setColor(0x55b0efff);c.drawCircle(182,105,56,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);for(int i=0;i<6;i++){p.setColor(0x4488dfff);c.drawOval(-60+i*15,55+i*12,300-i*9,190+i*13,p);}p.setStyle(Paint.Style.FILL);}
        else if(kind==3){p.setColor(0x66ffedc8);c.drawCircle(168,121,37,p);for(int i=0;i<4;i++){path.reset();path.moveTo(-20,300);path.lineTo(54+i*50,146+i*14);path.lineTo(290,302);path.close();p.setColor(Appearance.mix(colors[kind][0],colors[kind][1],i*.19f));c.drawPath(path,p);}}
        else for(int i=0;i<8;i++){path.reset();path.moveTo(-30,156+i*23);path.cubicTo(45,66+i*29,125,301-i*9,270,114+i*25);path.lineTo(270,330);path.lineTo(-30,330);path.close();p.setColor(i%2==0?0x152effdc:0x18200060);c.drawPath(path,p);}
        p.setColor(0x15ffffff);for(int i=0;i<22;i++)c.drawCircle((i*71+19)%240,(i*43+16)%174,i%3==0?1.3f:.6f,p);c.restore();
    }
    static void apply(Context c,int kind)throws IOException{Bitmap b=Bitmap.createBitmap(240,302,Bitmap.Config.RGB_565);draw(new Canvas(b),kind,240,302);AtomicFile file=new AtomicFile(Appearance.wallpaper(c));FileOutputStream out=null;try{out=file.startWrite();if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}finally{b.recycle();}}
}
