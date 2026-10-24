package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.database.Cursor;import android.graphics.*;import android.net.Uri;import android.provider.ContactsContract;import java.util.*;
/** At most five provider favorites, loaded off the HOME thread. */
final class HomeFavorites {
 static final class Entry{final long id;final String name;final Bitmap photo;Entry(long i,String n,Bitmap b){id=i;name=n;photo=b;}}
 static Entry[] load(Context c){ArrayList<Entry> out=new ArrayList<Entry>();try(Cursor r=c.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,new String[]{"_id","display_name","photo_thumb_uri"},"starred=1",null,"display_name COLLATE LOCALIZED ASC")){while(r!=null&&r.moveToNext()&&out.size()<5){Bitmap b=null;String photo=r.getString(2);if(photo!=null)try{b=Appearance.decode(c,Uri.parse(photo),48);}catch(Exception|OutOfMemoryError ignored){}out.add(new Entry(r.getLong(0),r.getString(1)==null?L.t("Contact"):r.getString(1),b));}}catch(RuntimeException ignored){}return out.toArray(new Entry[0]);}
}
