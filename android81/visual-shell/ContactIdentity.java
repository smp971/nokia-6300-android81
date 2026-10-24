package ro.leo.visual;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.provider.ContactsContract;
/** Per-query identity lookup; never persist private contact details in a second store. */
final class ContactIdentity {
    final String name;final long id;final Uri photo;
    ContactIdentity(String name,long id,Uri photo){this.name=name;this.id=id;this.photo=photo;}
    static ContactIdentity find(Context c,String number){
        if(number==null||number.isEmpty())return new ContactIdentity("Număr privat",-1,null);
        try(Cursor row=c.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME,ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI},null,null,null)){
            if(row!=null&&row.moveToFirst())return new ContactIdentity(row.getString(0)==null?number:row.getString(0),row.getLong(1),row.getString(2)==null?null:Uri.parse(row.getString(2)));
        }catch(RuntimeException ignored){}
        return new ContactIdentity(number,-1,null);
    }
}
