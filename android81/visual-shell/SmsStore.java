package ro.leo.visual;

import android.content.*;
import android.database.sqlite.*;

/** Private received-SMS mirror, needed when this image has no default SMS application. */
final class SmsStore extends SQLiteOpenHelper implements AutoCloseable {
    SmsStore(Context context) { super(context,"leo-sms.db",null,2); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE inbox (hash TEXT PRIMARY KEY,address TEXT NOT NULL,body TEXT NOT NULL,date INTEGER,sent INTEGER,sub INTEGER,unread INTEGER NOT NULL DEFAULT 0)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { if(oldVersion<2)db.execSQL("ALTER TABLE inbox ADD COLUMN unread INTEGER NOT NULL DEFAULT 0"); }
    void received(String hash,String address,String body,long sent,int sub) {
        ContentValues values=new ContentValues(); values.put("hash",hash); values.put("address",address); values.put("body",body);
        values.put("date",System.currentTimeMillis()); values.put("sent",sent); values.put("sub",sub); values.put("unread",1);
        getWritableDatabase().insertWithOnConflict("inbox",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }
}
