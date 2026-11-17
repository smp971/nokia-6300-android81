package ro.leo.visual;
import android.graphics.*;import android.graphics.drawable.*;import android.net.Uri;import android.util.LruCache;import java.util.*;
/** Only visible list rows request a bounded thumbnail; callbacks never rebuild a header. */
final class RowArtwork {
 final LeoActivity activity;final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(384*1024){protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};final Set<String> pending=new HashSet<String>(),failed=new LinkedHashSet<String>();private boolean closed;
 RowArtwork(LeoActivity a){activity=a;}
 void close(){closed=true;cache.evictAll();failed.clear();pending.clear();}
 Drawable get(final String key,final Uri uri,String label){Bitmap image=cache.get(key);if(image!=null){Drawable d=new BitmapDrawable(activity.getResources(),image);d.setBounds(0,0,activity.dp(40),activity.dp(40));return d;}if(!closed&&uri!=null&&pending.size()<8&&!failed.contains(key)&&pending.add(key))activity.worker.execute(new Runnable(){public void run(){Bitmap b=null;try{b=Appearance.decode(activity,uri,64);if(b!=null&&b.getWidth()!=b.getHeight()){int side=Math.min(b.getWidth(),b.getHeight());Bitmap square=Bitmap.createBitmap(b,(b.getWidth()-side)/2,(b.getHeight()-side)/2,side,side);if(square!=b)b.recycle();b=square;}}catch(Exception|OutOfMemoryError ignored){}final Bitmap result=b;activity.ui(new Runnable(){public void run(){pending.remove(key);if(closed){if(result!=null)result.recycle();return;}if(result==null){failed.add(key);while(failed.size()>48)failed.remove(failed.iterator().next());}else cache.put(key,result);activity.refreshRows();}});}});return new AvatarArt(label,activity.dp(40));}
}
