package ro.leo.visual;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.*;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.*;

/** Device SQLite/receiver checks in a fresh /tmp directory; no SMS transport. */
public final class SmsArchiveProbe {
    private static int checks;
    private static void check(boolean ok,String label) { if(!ok)throw new AssertionError(label);checks++; }
    private static final class IsolatedContext extends ContextWrapper {
        final File directory;boolean unavailable;
        final Map<String,SharedPreferences> preferences=new HashMap<String,SharedPreferences>();
        IsolatedContext(File directory) { super(null);this.directory=directory; }
        public File getDatabasePath(String name) {
            if(unavailable)throw new SQLiteException("Injected test failure");
            return new File(directory,name);
        }
        public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory f) {
            return openOrCreateDatabase(name,mode,f,null);
        }
        public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler handler) {
            if(unavailable)throw new SQLiteException("Injected test failure");
            return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(),f,SQLiteDatabase.CREATE_IF_NECESSARY,handler);
        }
        public SharedPreferences getSharedPreferences(String name,int mode) {
            if(!preferences.containsKey(name))try {
                Constructor<?> ctor=Class.forName("android.app.SharedPreferencesImpl").getDeclaredConstructor(File.class,int.class);
                ctor.setAccessible(true);preferences.put(name,(SharedPreferences)ctor.newInstance(new File(directory,name+".xml"),0));
            } catch(Exception e) { throw new RuntimeException(e); }
            return preferences.get(name);
        }
    }
    private static int type(IsolatedContext c,String token) {
        try(SmsStore store=new SmsStore(c);Cursor row=store.getReadableDatabase().query("outgoing",new String[]{"type"},"token=?",new String[]{token},null,null,null)) {
            check(row.moveToFirst(),"archive row exists");return row.getInt(0);
        }
    }
    private static void pending(IsolatedContext c,String token,String parts) {
        c.getSharedPreferences("sms-outgoing",0).edit().clear().putString("token",token).putString("number","0000000000")
            .putString("body","Mesaj fictiv șț 🙂").putString("parts",parts).putLong("date",123456L).putInt("sub",2).commit();
        c.getSharedPreferences("sms-draft",0).edit().clear().putString("number","0000000000").putString("body","Mesaj fictiv șț 🙂").commit();
    }
    private static void callback(IsolatedContext c,String token,int part,int result) {
        SmsStatusReceiver receiver=new SmsStatusReceiver();
        try {
            Constructor<?> ctor=BroadcastReceiver.PendingResult.class.getDeclaredConstructor(int.class,String.class,android.os.Bundle.class,int.class,boolean.class,boolean.class,android.os.IBinder.class,int.class,int.class);
            BroadcastReceiver.class.getDeclaredMethod("setPendingResult",BroadcastReceiver.PendingResult.class).invoke(receiver,ctor.newInstance(result,null,null,1,true,false,null,0,0));
        } catch(Exception e) { throw new RuntimeException(e); }
        receiver.onReceive(c,new Intent().putExtra("token",token).putExtra("part",part));
    }
    public static void main(String[] args) {
        File directory=new File(args[0]);
        if(!directory.getPath().startsWith("/tmp/leo-sms-probe-") || !directory.mkdir())throw new IllegalArgumentException("Use a fresh isolated /tmp probe directory");
        IsolatedContext c=new IsolatedContext(directory);
        // An existing version-2 received archive must survive the upgrade unchanged.
        try(SQLiteDatabase db=c.openOrCreateDatabase("leo-sms.db",0,null)) {
            db.execSQL("CREATE TABLE inbox (hash TEXT PRIMARY KEY,address TEXT NOT NULL,body TEXT NOT NULL,date INTEGER,sent INTEGER,sub INTEGER,unread INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO inbox VALUES ('fixture','demo','Primit fictiv',1,1,2,1)");db.setVersion(2);
        }
        try(SmsStore store=new SmsStore(c)) {
            check(store.getWritableDatabase().getVersion()==3,"migration to v3");
            try(Cursor row=store.getReadableDatabase().rawQuery("SELECT body,unread FROM inbox WHERE hash='fixture'",null)) {
                check(row.moveToFirst() && row.getString(0).equals("Primit fictiv") && row.getInt(1)==1,"received archive retained");
            }
            store.outgoing("success","0000000000","Mesaj fictiv șț 🙂",123456L,2);
            store.outgoing("success","wrong","must not overwrite",1,9);
        }
        check(type(c,"success")==4,"pending persisted across reopen");
        pending(c,"success","[0,0]");callback(c,"success",0,Activity.RESULT_OK);
        check(type(c,"success")==4,"partial multipart remains pending");
        callback(c,"success",0,Activity.RESULT_CANCELED);
        check(c.getSharedPreferences("sms-outgoing",0).getString("parts","").equals("[1,0]"),"duplicate segment ignored");
        callback(c,"success",1,Activity.RESULT_OK);
        check(type(c,"success")==2,"all segments confirmed sent");
        check(!c.getSharedPreferences("sms-outgoing",0).contains("token"),"temporary state removed after archive");
        check(c.getSharedPreferences("sms-draft",0).getAll().isEmpty(),"matching draft cleared");
        callback(c,"success",1,Activity.RESULT_OK);
        try(SmsStore store=new SmsStore(c);Cursor row=store.getReadableDatabase().rawQuery("SELECT count(*),body,sub FROM outgoing",null)) {
            row.moveToFirst();check(row.getInt(0)==1 && row.getString(1).equals("Mesaj fictiv șț 🙂") && row.getInt(2)==2,"one immutable Unicode/SIM history row");
        }
        // Legacy pending preferences can still be archived by the new receiver.
        pending(c,"failure","[1,0]");callback(c,"failure",1,Activity.RESULT_CANCELED);
        check(type(c,"failure")==5,"multipart partial failure retained");
        check(c.getSharedPreferences("sms-draft",0).contains("body"),"failed draft retained");
        pending(c,"changed-draft","[0]");c.getSharedPreferences("sms-draft",0).edit().putString("body","A newer draft").commit();
        callback(c,"changed-draft",0,Activity.RESULT_OK);
        check(c.getSharedPreferences("sms-draft",0).getString("body","").equals("A newer draft"),"newer draft retained");
        pending(c,"storage-failure","[0]");c.unavailable=true;callback(c,"storage-failure",0,Activity.RESULT_OK);c.unavailable=false;
        check(c.getSharedPreferences("sms-outgoing",0).getString("token","").equals("storage-failure"),"storage failure retains recovery state");
        callback(c,"storage-failure",0,Activity.RESULT_OK);check(type(c,"storage-failure")==2,"recovery after storage failure");
        // Delete only the known, isolated test files; never recurse over device data.
        for(File file:directory.listFiles())if(!file.isFile() || !file.delete())throw new AssertionError("Test cleanup failed");
        check(directory.delete(),"isolated test directory removed");
        System.out.println(checks+" SMS archive checks passed; no SMS transport or real application data used");
    }
}
