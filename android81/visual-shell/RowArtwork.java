package ro.leo.visual;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Only visible rows request artwork; pressure invalidates pending decodes without recycling bound images. */
final class RowArtwork implements UiMemory.Client {
    final LeoActivity activity;
    final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(384*1024){
        protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    final Set<String> pending=new HashSet<String>(),failed=new LinkedHashSet<String>();
    private boolean closed;
    private volatile int generation;
    RowArtwork(LeoActivity activity){this.activity=activity;UiMemory.register(this);}
    void close(){closed=true;generation++;cache.evictAll();failed.clear();pending.clear();UiMemory.unregister(this);}
    public void trimUiMemory(int level){generation++;cache.evictAll();failed.clear();pending.clear();}
    Drawable get(final String key,final Uri uri,String label){
        Bitmap image=cache.get(key);
        if(image!=null){Drawable drawable=new BitmapDrawable(activity.getResources(),image);drawable.setBounds(0,0,activity.dp(40),activity.dp(40));return drawable;}
        if(!closed&&activity.uiActive&&uri!=null&&pending.size()<8&&!failed.contains(key)&&pending.add(key)){
            final int request=generation;
            activity.worker.execute(new Runnable(){public void run(){
                if(request!=generation)return;
                Bitmap decoded=null;
                try{
                    decoded=Appearance.decode(activity,uri,64);
                    if(decoded!=null&&decoded.getWidth()!=decoded.getHeight()){
                        int side=Math.min(decoded.getWidth(),decoded.getHeight());
                        Bitmap square=Bitmap.createBitmap(decoded,(decoded.getWidth()-side)/2,(decoded.getHeight()-side)/2,side,side);
                        if(square!=decoded)decoded.recycle();decoded=square;
                    }
                }catch(Exception|OutOfMemoryError unavailable){}
                final Bitmap result=decoded;
                activity.runOnUiThread(new Runnable(){public void run(){
                    if(closed||request!=generation){if(result!=null)result.recycle();return;}
                    pending.remove(key);
                    if(!activity.uiActive){if(result!=null)result.recycle();return;}
                    if(result==null){failed.add(key);while(failed.size()>48)failed.remove(failed.iterator().next());}
                    else cache.put(key,result);
                    activity.refreshRows();
                }});
            }});
        }
        return new AvatarArt(label,activity.dp(40));
    }
}
