package ro.leo.visual;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

final class Appearance {
    static final String[] THEMES={"Violet","Apus","Ocean","Pădure"};
    static final int[][] COLORS={{0xff241047,0xff593185,0xffbc6e91},{0xff243959,0xff98707c,0xffedb17c},{0xff081d36,0xff17628b,0xff58b6be},{0xff102d28,0xff3c7657,0xffaac47e}};
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
