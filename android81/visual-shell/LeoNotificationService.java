package ro.leo.visual;
import ro.leo.i18n.L;

import android.app.Notification;
import android.content.*;
import android.service.notification.*;
import java.util.*;

/** Ephemeral view of Android notifications; never persists their contents. */
public final class LeoNotificationService extends NotificationListenerService {
    /** Refresh only our component after Oreo retains a dead service binding.
     * This never grants notification access or changes another listener. */
    static void reconnect(final Context context){
        final ComponentName own=new ComponentName(context,LeoNotificationService.class);
        requestRebind(own);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){
            if(active!=null)return;
            final android.content.pm.PackageManager pm=context.getPackageManager();
            final int original=pm.getComponentEnabledSetting(own);
            if(original!=android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&original!=android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED)return;
            pm.setComponentEnabledSetting(own,android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,android.content.pm.PackageManager.DONT_KILL_APP);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){
                pm.setComponentEnabledSetting(own,original,android.content.pm.PackageManager.DONT_KILL_APP);
                requestRebind(own);
            }},500);
        }},500);
    }
    static String[] preview(Context context,int selected){
        int mode=context.getSharedPreferences("appearance",0).getInt("lock-preview",0);if(mode==0)return new String[]{"",""};
        ArrayList<StatusBarNotification> available=new ArrayList<StatusBarNotification>();for(StatusBarNotification n:snapshot())if(n.getNotification().visibility!=Notification.VISIBILITY_SECRET)available.add(n);
        if(available.isEmpty())return new String[]{"",""};StatusBarNotification item=available.get(Math.floorMod(selected,available.size()));Notification n=item.getNotification();
        android.app.KeyguardManager keyguard=(android.app.KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        if(keyguard!=null&&keyguard.isDeviceLocked()&&n.visibility!=Notification.VISIBILITY_PUBLIC){if(n.publicVersion==null)return new String[]{L.t("Notificare privată"),L.t("Deblochează pentru conținut")};n=n.publicVersion;}
        CharSequence title=n.extras.getCharSequence(Notification.EXTRA_TITLE),body=n.extras.getCharSequence(Notification.EXTRA_TEXT);
        return new String[]{clip(title==null?item.getPackageName():title.toString()),mode==2&&body!=null?clip(body.toString()):L.t("Sus / jos · alte notificări")};
    }
    private static String clip(String text){return text.length()>34?text.substring(0,33)+"…":text;}
    static final String CHANGED="ro.leo.visual.NOTIFICATIONS_CHANGED";
    static volatile LeoNotificationService active;
    private final LinkedHashMap<String,StatusBarNotification> items=new LinkedHashMap<String,StatusBarNotification>();
    public void onListenerConnected(){active=this; synchronized(items){items.clear();StatusBarNotification[] all=getActiveNotifications();if(all!=null)for(StatusBarNotification n:all)items.put(n.getKey(),n);}changed();}
    public void onListenerDisconnected(){active=null;synchronized(items){items.clear();}changed();}
    public void onNotificationPosted(StatusBarNotification n){synchronized(items){items.put(n.getKey(),n);}changed();}
    public void onNotificationRemoved(StatusBarNotification n){synchronized(items){items.remove(n.getKey());}changed();}
    private void changed(){sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));}
    static List<StatusBarNotification> snapshot(){LeoNotificationService s=active;if(s==null)return new ArrayList<StatusBarNotification>();try{StatusBarNotification[] current=s.getActiveNotifications();if(current!=null)synchronized(s.items){s.items.clear();for(StatusBarNotification n:current)s.items.put(n.getKey(),n);}}catch(RuntimeException ignored){}synchronized(s.items){ArrayList<StatusBarNotification> copy=new ArrayList<StatusBarNotification>(s.items.values());Collections.sort(copy,new Comparator<StatusBarNotification>(){public int compare(StatusBarNotification a,StatusBarNotification b){return Long.compare(b.getPostTime(),a.getPostTime());}});return copy;}}
    static boolean canSnooze(StatusBarNotification n){return n!=null&&n.isClearable()&&!n.isOngoing()&&!Notification.CATEGORY_CALL.equals(n.getNotification().category);}
    static boolean snooze(StatusBarNotification n,long duration){LeoNotificationService s=active;if(s==null||!canSnooze(n)||duration<60000||duration>3600000)return false;try{s.snoozeNotification(n.getKey(),duration);return true;}catch(RuntimeException e){return false;}}
    static boolean dismiss(StatusBarNotification n){LeoNotificationService s=active;if(s==null||!n.isClearable())return false;try{s.cancelNotification(n.getKey());return true;}catch(RuntimeException e){return false;}}
}
