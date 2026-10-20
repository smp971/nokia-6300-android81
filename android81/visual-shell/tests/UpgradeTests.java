package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.ContactsContract;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import java.util.*;

/** On-device checks with owned synthetic contact/notification and restored preferences.
 * No calls, SMS, email, network account or remote notification action is sent. */
public final class UpgradeTests extends Instrumentation {
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private Activity launch(Class<?> type){Activity a=startActivitySync(new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));for(int i=0;i<50&&!a.hasWindowFocus();i++){waitForIdleSync();SystemClock.sleep(100);}check(a.hasWindowFocus(),"window focused: "+type.getSimpleName());return a;}
    private void finishActivity(final Activity a){if(a!=null)runOnMainSync(new Runnable(){public void run(){a.finish();}});}
    private Object field(Object instance,String name)throws Exception{java.lang.reflect.Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}
    public void onCreate(Bundle args){start();}
    public void onStart(){final Context c=getTargetContext();Bundle result=new Bundle();SharedPreferences p=c.getSharedPreferences("appearance",0);Map<String,?> saved=new HashMap<String,Object>(p.getAll());long raw=-1;Activity activity=null;NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        try{
            runOnMainSync(new Runnable(){public void run(){LeoNotificationService.reconnect(c);}});
            int[] order=AppCatalog.parse("4,4,-1,999,x,0");check(order.length==23&&order[0]==4&&order[1]==0,"invalid order sanitized");HashSet<Integer> unique=new HashSet<Integer>();for(int id:order)unique.add(id);check(unique.size()==23,"all apps retained");
            p.edit().putString("app-order","4,0").putInt("shortcut0",11).putBoolean("large-text",true).commit();check(Appearance.textScale(c)>1&&AppCatalog.shortcuts(c)[0]==11,"text and shortcut preferences");
            activity=launch(MainActivity.class);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);ActivityMonitor calculator=addMonitor(CalculatorActivity.class.getName(),null,false);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);Activity calc=waitForMonitorWithTimeout(calculator,5000);removeMonitor(calculator);check(calc!=null,"reordered first app launches Calculator");finishActivity(calc);finishActivity(activity);activity=null;
            activity=launch(MainActivity.class);ActivityMonitor calendar=addMonitor(CalendarActivity.class.getName(),null,false);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);Activity cal=waitForMonitorWithTimeout(calendar,5000);removeMonitor(calendar);check(cal!=null,"custom Home shortcut launches Calendar");finishActivity(cal);finishActivity(activity);activity=null;
            ContentValues values=new ContentValues();values.put(ContactsContract.RawContacts.AGGREGATION_MODE,ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED);raw=ContentUris.parseId(c.getContentResolver().insert(ContactsContract.RawContacts.CONTENT_URI,values));
            values.clear();values.put("raw_contact_id",raw);values.put("mimetype","vnd.android.cursor.item/name");values.put("data1","Leo Test Flux");c.getContentResolver().insert(ContactsContract.Data.CONTENT_URI,values);
            values.clear();values.put("raw_contact_id",raw);values.put("mimetype","vnd.android.cursor.item/phone_v2");values.put("data1","0000000000");values.put("data2",2);c.getContentResolver().insert(ContactsContract.Data.CONTENT_URI,values);
            activity=launch(PhoneActivity.class);final Activity phone=activity;final EditText number=(EditText)field(phone,"number");runOnMainSync(new Runnable(){public void run(){number.setText("53683783589");}});Button matches=(Button)field(phone,"matches");boolean found=false;for(int i=0;i<60;i++){waitForIdleSync();if(matches.getVisibility()==View.VISIBLE&&matches.getText().toString().contains("Leo Test Flux")){found=true;break;}SystemClock.sleep(100);}check(found,"T9 dialer finds owned contact");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);check(matches.hasFocus(),"D-pad reaches T9 result");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();SystemClock.sleep(400);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check("0000000000".equals(number.getText().toString()),"T9 selection fills number without calling");finishActivity(activity);activity=null;
            for(int i=0;i<250&&LeoNotificationService.active==null;i++)SystemClock.sleep(100);check(LeoNotificationService.active!=null,"notification listener reconnects after instrumentation");
            nm.createNotificationChannel(new NotificationChannel("leo-upgrade-fixture","Probă locală",NotificationManager.IMPORTANCE_LOW));nm.notify("leo-upgrade-fixture",1601,new Notification.Builder(c,"leo-upgrade-fixture").setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("Leo local preview").setContentText("Synthetic local text").setVisibility(Notification.VISIBILITY_PUBLIC).build());
            boolean posted=false;for(int i=0;i<60;i++){for(StatusBarNotification n:LeoNotificationService.snapshot())if("leo-upgrade-fixture".equals(n.getTag()))posted=true;if(posted)break;SystemClock.sleep(100);}check(posted,"notification listener receives owned fixture");
            p.edit().putInt("lock-preview",0).commit();check(LeoNotificationService.preview(c,0)[0].isEmpty(),"preview disabled by preference");p.edit().putInt("lock-preview",1).commit();boolean title=false;for(int i=0;i<LeoNotificationService.snapshot().size();i++){String[] v=LeoNotificationService.preview(c,i);if(v[0].equals("Leo local preview")){title=true;check(!v[1].contains("Synthetic"),"title mode hides body");}}check(title,"title mode includes owned notification");
            p.edit().putInt("lock-preview",2).commit();boolean body=false;for(int i=0;i<LeoNotificationService.snapshot().size();i++)if(LeoNotificationService.preview(c,i)[1].equals("Synthetic local text"))body=true;check(body,"full preview uses local body");
            activity=launch(KeypadLockActivity.class);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);check(KeypadLockActivity.locked(c),"preview navigation preserves key lock");sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);try{sendKeyDownUpSync(KeyEvent.KEYCODE_STAR);}catch(SecurityException focusTransferred){check(!KeypadLockActivity.locked(c),"unlock completed before key-up focus transfer");}waitForIdleSync();check(!KeypadLockActivity.locked(c),"Menu then star still unlocks");activity=null;
            result.putString("result","PASS reordered Calculator route, Calendar shortcut, text preference, live T9 contact selection, notification title/body/off, lock navigation and unlock; no communications sent");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            finishActivity(activity);KeypadLockActivity.unlock(c);nm.cancel("leo-upgrade-fixture",1601);nm.deleteNotificationChannel("leo-upgrade-fixture");
            if(raw>=0)c.getContentResolver().delete(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI,raw).buildUpon().appendQueryParameter("caller_is_syncadapter","true").build(),null,null);
            SharedPreferences.Editor restore=p.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();if(v instanceof String)restore.putString(entry.getKey(),(String)v);else if(v instanceof Integer)restore.putInt(entry.getKey(),(Integer)v);else if(v instanceof Boolean)restore.putBoolean(entry.getKey(),(Boolean)v);else if(v instanceof Long)restore.putLong(entry.getKey(),(Long)v);else if(v instanceof Float)restore.putFloat(entry.getKey(),(Float)v);else if(v instanceof Set)restore.putStringSet(entry.getKey(),(Set<String>)v);}restore.commit();
        }
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
