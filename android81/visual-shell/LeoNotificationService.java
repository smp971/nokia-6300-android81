package ro.leo.visual;

import android.app.Notification;
import android.content.*;
import android.service.notification.*;
import java.util.*;

/** Ephemeral view of Android notifications; never persists their contents. */
public final class LeoNotificationService extends NotificationListenerService {
    static final String CHANGED="ro.leo.visual.NOTIFICATIONS_CHANGED";
    static volatile LeoNotificationService active;
    private final LinkedHashMap<String,StatusBarNotification> items=new LinkedHashMap<String,StatusBarNotification>();
    public void onListenerConnected(){active=this; synchronized(items){items.clear();StatusBarNotification[] all=getActiveNotifications();if(all!=null)for(StatusBarNotification n:all)items.put(n.getKey(),n);}changed();}
    public void onListenerDisconnected(){active=null;synchronized(items){items.clear();}changed();}
    public void onNotificationPosted(StatusBarNotification n){synchronized(items){items.put(n.getKey(),n);}changed();}
    public void onNotificationRemoved(StatusBarNotification n){synchronized(items){items.remove(n.getKey());}changed();}
    private void changed(){sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));}
    static List<StatusBarNotification> snapshot(){LeoNotificationService s=active;if(s==null)return new ArrayList<StatusBarNotification>();synchronized(s.items){ArrayList<StatusBarNotification> copy=new ArrayList<StatusBarNotification>(s.items.values());Collections.sort(copy,new Comparator<StatusBarNotification>(){public int compare(StatusBarNotification a,StatusBarNotification b){return Long.compare(b.getPostTime(),a.getPostTime());}});return copy;}}
    static boolean dismiss(StatusBarNotification n){LeoNotificationService s=active;if(s==null||!n.isClearable())return false;try{s.cancelNotification(n.getKey());return true;}catch(RuntimeException e){return false;}}
}
