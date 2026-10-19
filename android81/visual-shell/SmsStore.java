package ro.leo.visual;

import android.content.*;
import android.database.sqlite.*;

/** Private received-SMS mirror, needed when this image has no default SMS application. */
final class SmsStore extends SQLiteOpenHelper implements AutoCloseable {
    SmsStore(Context context) { super(context,"leo-sms.db",null,1); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE inbox (hash TEXT PRIMARY KEY,address TEXT NOT NULL,body TEXT NOT NULL,date INTEGER,sent INTEGER,sub INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }
    void received(String hash,String address,String body,long sent,int sub) {
        ContentValues values=new ContentValues(); values.put("hash",hash); values.put("address",address); values.put("body",body);
        values.put("date",System.currentTimeMillis()); values.put("sent",sent); values.put("sub",sub);
        getWritableDatabase().insertWithOnConflict("inbox",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }
}
