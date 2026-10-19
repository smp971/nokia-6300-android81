package ro.leo.visual;

import android.content.*;
import android.database.sqlite.*;

/** Private received/sent archive, needed when this image has no default SMS application. */
final class SmsStore extends SQLiteOpenHelper implements AutoCloseable {
    SmsStore(Context context) { super(context,"leo-sms.db",null,3); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE inbox (hash TEXT PRIMARY KEY,address TEXT NOT NULL,body TEXT NOT NULL,date INTEGER,sent INTEGER,sub INTEGER,unread INTEGER NOT NULL DEFAULT 0)");
        createOutgoing(db);
    }
    private void createOutgoing(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE outgoing (token TEXT PRIMARY KEY,address TEXT NOT NULL,body TEXT NOT NULL,date INTEGER NOT NULL,sub INTEGER NOT NULL,type INTEGER NOT NULL)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2)db.execSQL("ALTER TABLE inbox ADD COLUMN unread INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<3)createOutgoing(db);
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
    void received(String hash,String address,String body,long sent,int sub) {
        ContentValues values=new ContentValues(); values.put("hash",hash); values.put("address",address); values.put("body",body);
        values.put("date",System.currentTimeMillis()); values.put("sent",sent); values.put("sub",sub); values.put("unread",1);
        getWritableDatabase().insertWithOnConflict("inbox",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }
}
