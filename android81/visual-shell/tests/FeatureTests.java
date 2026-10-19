package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import com.android.vcard.*;
import java.util.*;

/** Separately installed instrumentation; no test receiver or fixture data in the production APK. */
public final class FeatureTests extends Instrumentation {
    private Bundle args;
    public void onCreate(Bundle b){args=b;start();}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private int unread(Context c,String address){try(SmsStore s=new SmsStore(c);Cursor row=s.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM inbox WHERE address=? AND unread=1",new String[]{address})){row.moveToFirst();return row.getInt(0);}}
    public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();final String address="LEO LOCAL FIXTURE";long raw=-1;
        try{
            String phase=args==null?"core":args.getString("phase","core");
            if(phase.equals("seed")){
                try(SmsStore s=new SmsStore(c)){s.received("leo-six-fixture-sms",address,"Mesaj demonstrativ local · fără SMS trimis",0,-1);}
                NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel("leo-fixture","Probă locală Leo",NotificationManager.IMPORTANCE_LOW));
                PendingIntent open=PendingIntent.getActivity(c,1201,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
                n.notify("leo-six-fixture",1201,new Notification.Builder(c,"leo-fixture").setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("Probă locală Leo").setContentText("Notificare demonstrativă · deschidere și eliminare din taste").setContentIntent(open).build());
                UnreadState.refresh(c);result.putString("result","PASS local synthetic SMS + notification seeded");
            }else if(phase.equals("speed")){
                SharedPreferences p=c.getSharedPreferences("speed-dial",0);String name=p.getString("name9",null),number=p.getString("number9",null);
                try{
                    p.edit().putString("name9","Leo local fixture").putString("number9","+10000000009").commit();
                    ActivityMonitor monitor=addMonitor("ro.leo.visual.PhoneActivity",null,false);
                    final Context target=c;runOnMainSync(new Runnable(){public void run(){target.startActivity(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}});SystemClock.sleep(1000);
                    sendKeySync(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_9));SystemClock.sleep(900);
                    sendKeySync(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_9));
                    Activity dial=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check(dial!=null,"held 9 opens dialer");check("+10000000009".equals(dial.getIntent().getStringExtra("number")),"speed dial target");
                    result.putString("result","PASS held key 9 opens PhoneActivity with fixture number; no call placed");
                }finally{SharedPreferences.Editor e=p.edit();if(name==null)e.remove("name9");else e.putString("name9",name);if(number==null)e.remove("number9");else e.putString("number9",number);e.commit();}
            }else if(phase.equals("cleanup")){
                SharedPreferences speed=c.getSharedPreferences("speed-dial",0);if("+10000000009".equals(speed.getString("number9",""))&&"Leo local fixture".equals(speed.getString("name9","")))speed.edit().remove("number9").remove("name9").commit();
                try(SmsStore s=new SmsStore(c)){s.getWritableDatabase().delete("inbox","hash LIKE ? AND address=?",new String[]{"leo-six-fixture-%",address});}
                NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);n.cancel("leo-six-fixture",1201);n.deleteNotificationChannel("leo-fixture");UnreadState.refresh(c);result.putString("result","PASS owned fixtures removed");
            }else{
                String v="BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Leo Fixture Ștefan\r\nN:Ștefan;Leo Fixture;;;\r\nTEL;TYPE=CELL:+10000000001\r\nNOTE:Linia 1\\nLinia 2\\; virgulă\\, test\r\nEND:VCARD\r\n";
                ArrayList<VCardEntry> entries=VCardActivity.parse(v.getBytes("UTF-8"));check(entries.size()==1,"v3 parse");check(entries.get(0).getDisplayName().contains("Ștefan"),"Unicode name");
                String v21="BEGIN:VCARD\r\nVERSION:2.1\r\nFN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=C8=98tefan\r\nTEL:10000000002\r\nEND:VCARD\r\n";
                check(VCardActivity.parse(v21.getBytes("UTF-8")).get(0).getDisplayName().contains("Ștefan"),"v2.1 quoted printable");
                check(VCardActivity.parse(v.replace("VERSION:3.0","VERSION:4.0").getBytes("UTF-8")).size()==1,"v4 parse");
                ArrayList<ContentProviderOperation> ops=entries.get(0).constructInsertOperations(c.getContentResolver(),null);
                ContentProviderResult[] inserted=c.getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops);raw=ContentUris.parseId(inserted[0].uri);long contact=-1;
                try(Cursor row=c.getContentResolver().query(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,raw),new String[]{ContactsContract.RawContacts.CONTACT_ID},null,null,null)){check(row!=null&&row.moveToFirst(),"import raw");contact=row.getLong(0);}
                VCardComposer composer=new VCardComposer(c,VCardConfig.VCARD_TYPE_V30_GENERIC,"UTF-8");String exported;try{check(composer.init(ContactsContract.Contacts._ID+"=?",new String[]{""+contact}),"export selected");exported=composer.createOneEntry();}finally{composer.terminate();}
                ArrayList<VCardEntry> round=VCardActivity.parse(exported.getBytes("UTF-8"));check(round.size()==1&&round.get(0).getDisplayName().contains("Ștefan"),"VCF provider round trip");check(ContactKeys.digits(round.get(0).getPhoneList().get(0).getNumber()).equals("10000000001"),"roundtrip phone");
                check(ContactKeys.matches("Ștefan Țară","10000000001","783326"),"T9 match");
                try(SmsStore s=new SmsStore(c)){s.received("leo-six-fixture-core-old",address,"Fixture read",1,-1);ContentValues old=new ContentValues();old.put("date",100L);s.getWritableDatabase().update("inbox",old,"hash=?",new String[]{"leo-six-fixture-core-old"});s.received("leo-six-fixture-core-new",address,"Fixture new",2,-1);}
                check(unread(c,address)==2,"SMS new unread");UnreadState.readThread(c,address,100L);for(int n=0;n<50&&unread(c,address)!=1;n++)SystemClock.sleep(100);check(unread(c,address)==1,"read watermark preserves newer SMS");
                try(SmsStore s=new SmsStore(c)){s.getWritableDatabase().delete("inbox","hash LIKE ? AND address=?",new String[]{"leo-six-fixture-core-%",address});}
                UnreadState.refresh(c);result.putString("result","PASS VCF 2.1 QP / 3 UTF8 / 4, provider import/export roundtrip, T9, SMS read watermark");
            }
            if(raw>=0){c.getContentResolver().delete(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,raw),null,null);raw=-1;}
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            if(raw>=0)c.getContentResolver().delete(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,raw),null,null);
            try(SmsStore s=new SmsStore(c)){s.getWritableDatabase().delete("inbox","hash LIKE ? AND address=?",new String[]{"leo-six-fixture-core-%",address});}
            result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);
        }
    }
}
