package ro.leo.visual;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

final class Appearance {
    static float textScale(Context c){return c.getSharedPreferences("appearance",0).getBoolean("large-text",false)?1.15f:1f;}
    static final String[] THEMES={"Violet","Apus","Ocean","Pădure","Clasic · Albastru","Coral","S60 · Rubiniu","Electro · Jad","LCD · Chihlimbar"};
    static final int[][] COLORS={{0xff241047,0xff593185,0xffbc6e91},{0xff243959,0xff98707c,0xffedb17c},{0xff081d36,0xff17628b,0xff58b6be},{0xff102d28,0xff3c7657,0xffaac47e},{0xff091a37,0xff205cb4,0xff5fc9ed},{0xff32182c,0xffa44162,0xffffad83},{0xff280d2b,0xff9c255f,0xffff779f},{0xff061f28,0xff08796b,0xff83f1b0},{0xff2e2212,0xff79633a,0xffffd085}};
    static final class Palette {
        final int background,bar,card,accent,selection,ink;
        final int[] gradient;
        Palette(int[] colors){gradient=colors;background=mix(colors[0],0xff080e19,.48f);bar=mix(colors[0],0xff03070e,.6f);card=mix(colors[0],colors[1],.24f);accent=colors[2];selection=mix(colors[2],Color.WHITE,.84f);ink=mix(colors[0],Color.BLACK,.18f);}
    }
    static Palette palette(Context c){android.content.SharedPreferences prefs=c.getSharedPreferences("appearance",0);int i=Math.max(0,Math.min(COLORS.length-1,prefs.getInt("theme",prefs.getBoolean("warm",false)?1:0)));return new Palette(COLORS[i]);}
    static int mix(int a,int b,float f){return Color.rgb((int)(Color.red(a)*(1-f)+Color.red(b)*f),(int)(Color.green(a)*(1-f)+Color.green(b)*f),(int)(Color.blue(a)*(1-f)+Color.blue(b)*f));}
    static android.graphics.drawable.GradientDrawable surface(int color,int radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static android.graphics.drawable.StateListDrawable fieldSurface(Palette p,int radius){
        android.graphics.drawable.GradientDrawable focus=surface(p.card,radius);focus.setStroke(2,p.accent);
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},surface(p.card,radius));return states;
    }
    static Bitmap decode(Context context,Uri uri,int bound) throws IOException {
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
        try(InputStream stream=context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(stream,null,o); }
        if(o.outWidth<=0 || o.outHeight<=0 || (long)o.outWidth*o.outHeight>200000000L) throw new IOException("Imagine invalidă");
        o.inSampleSize=1; while(o.outWidth/o.inSampleSize>bound || o.outHeight/o.inSampleSize>bound) o.inSampleSize*=2;
        o.inJustDecodeBounds=false; o.inPreferredConfig=Bitmap.Config.RGB_565;
        Bitmap b;
        try(InputStream stream=context.getContentResolver().openInputStream(uri)) { b=BitmapFactory.decodeStream(stream,null,o); if(b==null) throw new IOException(); }
        int orientation=1;
        try(InputStream stream=context.getContentResolver().openInputStream(uri)){orientation=new android.media.ExifInterface(stream).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
        Matrix matrix=new Matrix();
        switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;}
        if(orientation>=2&&orientation<=8){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);if(rotated!=b)b.recycle();b=rotated;}
        return b;
    }
    static File wallpaper(Context c) { return new File(c.getFilesDir(),"home-wallpaper.png"); }
    static void saveWallpaper(Context c,Uri uri) throws IOException {
        Bitmap source=decode(c,uri,640),output=Bitmap.createBitmap(240,302,Bitmap.Config.RGB_565);
        Canvas canvas=new Canvas(output); float scale=Math.max(240f/source.getWidth(),302f/source.getHeight());
        float w=source.getWidth()*scale,h=source.getHeight()*scale;
        canvas.drawBitmap(source,null,new RectF((240-w)/2,(302-h)/2,(240+w)/2,(302+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
        AtomicFile atomic=new AtomicFile(wallpaper(c)); FileOutputStream stream=null;
        try { stream=atomic.startWrite(); if(!output.compress(Bitmap.CompressFormat.PNG,100,stream)) throw new IOException(); atomic.finishWrite(stream); }
        catch(IOException failure) { if(stream!=null) atomic.failWrite(stream); throw failure; }
        finally { source.recycle(); output.recycle(); }
    }
}
