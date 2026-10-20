package ro.leo.visual;
import android.content.*;import android.database.*;import android.database.sqlite.*;import android.telephony.PhoneNumberUtils;import java.util.*;
/** Independent drafts; legacy single draft is migrated once without discarding it. */
final class DraftStore extends SQLiteOpenHelper implements AutoCloseable {
    static final class Draft{String id,number,body;long updated;}
    DraftStore(Context c){super(c,"leo-drafts.db",null,1);SharedPreferences old=c.getSharedPreferences("sms-draft",0);if(!old.getBoolean("migrated-multiple",false)){String number=old.getString("number",""),body=old.getString("body","");if(!number.isEmpty()||!body.isEmpty())save("legacy",number,body);old.edit().putBoolean("migrated-multiple",true).commit();}}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE drafts(id TEXT PRIMARY KEY,number TEXT NOT NULL,normalized TEXT NOT NULL,body TEXT NOT NULL,updated INTEGER NOT NULL)");}
    public void onUpgrade(SQLiteDatabase db,int old,int now){}
    String save(String id,String number,String body){if(id==null)id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("number",number);v.put("normalized",PhoneNumberUtils.normalizeNumber(number));v.put("body",body);v.put("updated",System.currentTimeMillis());long inserted=getWritableDatabase().insertWithOnConflict("drafts",null,v,SQLiteDatabase.CONFLICT_REPLACE);if(inserted<0)throw new SQLiteException("Draft save failed");return id;}
    List<Draft> all(){ArrayList<Draft> result=new ArrayList<Draft>();try(Cursor rows=getReadableDatabase().query("drafts",new String[]{"id","number","body","updated"},null,null,null,null,"updated DESC","200")){while(rows.moveToNext()){Draft d=new Draft();d.id=rows.getString(0);d.number=rows.getString(1);d.body=rows.getString(2);d.updated=rows.getLong(3);result.add(d);}}return result;}
    Draft forNumber(String number){try(Cursor rows=getReadableDatabase().query("drafts",new String[]{"id","number","body","updated"},"normalized=?",new String[]{PhoneNumberUtils.normalizeNumber(number)},null,null,"updated DESC","1")){if(!rows.moveToFirst())return null;Draft d=new Draft();d.id=rows.getString(0);d.number=rows.getString(1);d.body=rows.getString(2);d.updated=rows.getLong(3);return d;}}
    void remove(String id){getWritableDatabase().delete("drafts","id=?",new String[]{id});}
    void sent(String id,String number,String body){if(id!=null)getWritableDatabase().delete("drafts","id=? AND normalized=? AND body=?",new String[]{id,PhoneNumberUtils.normalizeNumber(number),body});}
}
