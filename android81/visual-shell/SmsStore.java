package ro.leo.visual;

import android.content.*;
import android.database.sqlite.*;

/** Private received/sent archive, needed when this image has no default SMS application. */
final class SmsStore extends SQLiteOpenHelper implements AutoCloseable {
    SmsStore(Context context) { super(context,"leo-sms.db",null,5); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE inbox (hash TEXT PRIMARY KEY,address TEXT NOT NULL,body TEXT NOT NULL,date INTEGER,sent INTEGER,sub INTEGER,unread INTEGER NOT NULL DEFAULT 0)");
        createOutgoing(db);
    }
    private void createOutgoing(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE outgoing (token TEXT PRIMARY KEY,address TEXT NOT NULL,body TEXT NOT NULL,date INTEGER NOT NULL,sub INTEGER NOT NULL,type INTEGER NOT NULL,delivery INTEGER NOT NULL DEFAULT -1,delivery_parts TEXT,archive_stamp INTEGER NOT NULL DEFAULT 0)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2)db.execSQL("ALTER TABLE inbox ADD COLUMN unread INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<3)createOutgoing(db);
        else {if(oldVersion<4){db.execSQL("ALTER TABLE outgoing ADD COLUMN delivery INTEGER NOT NULL DEFAULT -1");db.execSQL("ALTER TABLE outgoing ADD COLUMN delivery_parts TEXT");}if(oldVersion<5)db.execSQL("ALTER TABLE outgoing ADD COLUMN archive_stamp INTEGER NOT NULL DEFAULT 0");}
    }
    void outgoing(String token,String address,String body,long date,int sub) {
        ContentValues values=new ContentValues();values.put("token",token);values.put("address",address);values.put("body",body);
        values.put("date",date);values.put("sub",sub);values.put("type",android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX);
        // Duplicate callbacks must never create duplicate history or reset its state.
        getWritableDatabase().insertWithOnConflict("outgoing",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }
    void completed(String token,boolean success) {
        ContentValues values=new ContentValues();values.put("type",success?android.provider.Telephony.Sms.MESSAGE_TYPE_SENT:android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED);
        if(getWritableDatabase().update("outgoing",values,"token=?",new String[]{token})!=1)throw new IllegalStateException("Missing outgoing SMS record");
    }
    void requestDelivery(String token,int parts){try{org.json.JSONArray a=new org.json.JSONArray();for(int i=0;i<parts;i++)a.put(-1);ContentValues v=new ContentValues();v.put("delivery",0);v.put("delivery_parts",a.toString());getWritableDatabase().update("outgoing",v,"token=?",new String[]{token});}catch(Exception e){throw new IllegalStateException(e);}}
    boolean delivery(String token,int part,int status){android.database.sqlite.SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try(android.database.Cursor c=db.query("outgoing",new String[]{"delivery","delivery_parts"},"token=?",new String[]{token},null,null,null)){if(!c.moveToFirst()||c.isNull(1))return false;org.json.JSONArray a=new org.json.JSONArray(c.getString(1));if(part<0||part>=a.length()||status<0||status>255)return false;if(a.getInt(part)==0)return false;a.put(part,status);boolean all=true,fail=false,complete=true;for(int i=0;i<a.length();i++){int s=a.getInt(i);if(s!=0)all=false;if(s<0||(s>=32&&s<64))complete=false;if(s>=64)fail=true;}int state=fail?2:all?1:complete?3:0;ContentValues v=new ContentValues();v.put("delivery_parts",a.toString());v.put("delivery",state);db.update("outgoing",v,"token=?",new String[]{token});boolean delivered=state==1&&c.getInt(0)!=1;db.setTransactionSuccessful();return delivered;}catch(org.json.JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}}
    void received(String hash,String address,String body,long sent,int sub) {
        ContentValues values=new ContentValues(); values.put("hash",hash); values.put("address",address); values.put("body",body);
        values.put("date",System.currentTimeMillis()); values.put("sent",sent); values.put("sub",sub); values.put("unread",1);
        getWritableDatabase().insertWithOnConflict("inbox",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }
}
