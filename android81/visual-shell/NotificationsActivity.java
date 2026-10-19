package ro.leo.visual;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.widget.*;
import java.util.*;

public final class NotificationsActivity extends LeoActivity {
    private String detailKey;
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(detailKey!=null)for(StatusBarNotification n:LeoNotificationService.snapshot())if(detailKey.equals(n.getKey()))return;
        show();
    }};
    protected void onCreate(Bundle b){super.onCreate(b);}
    protected void onResume(){super.onResume();registerReceiver(updates,new IntentFilter(LeoNotificationService.CHANGED));show();}
    protected void onPause(){unregisterReceiver(updates);super.onPause();}
    private String safe(CharSequence s){String v=s==null?"":s.toString();return v.length()>250?v.substring(0,250)+"…":v;}
    private String app(StatusBarNotification n){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(n.getPackageName(),0)).toString();}catch(Exception e){return n.getPackageName();}}
    private void show(){
        detailKey=null;
        if(LeoNotificationService.active==null){begin("Notificări");root.addView(text("Serviciul de notificări nu este conectat. Accesul Leo trebuie activat de instalatorul sistemului.",18),new LinearLayout.LayoutParams(-1,0,1));left=new Runnable(){public void run(){try{android.service.notification.NotificationListenerService.requestRebind(new ComponentName(NotificationsActivity.this,LeoNotificationService.class));notice("Reconectare cerută");}catch(RuntimeException e){notice("Acces indisponibil");}}};footer("Reîncearcă","","Înapoi");return;}
        final List<StatusBarNotification> all=LeoNotificationService.snapshot();ArrayList<String> labels=new ArrayList<String>();
        for(StatusBarNotification n:all)labels.add(app(n)+"\n"+safe(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE)));
        list("Notificări · "+all.size(),labels,0,new Choice(){public void pick(int i){detail(all.get(i));}},"",null);
    }
    private void detail(final StatusBarNotification n){
        detailKey=n.getKey();
        begin(app(n));ScrollView scroll=new ScrollView(this);Notification a=n.getNotification();CharSequence body=a.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);if(body==null)body=a.extras.getCharSequence(Notification.EXTRA_TEXT);
        scroll.addView(text(safe(a.extras.getCharSequence(Notification.EXTRA_TITLE))+"\n\n"+safe(body),18));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable(){public void run(){options("Notificare",new String[]{"Deschide",n.isClearable()?"Elimină":"Permanentă · nu se elimină"},new Choice(){public void pick(int i){if(i==0){try{if(n.getNotification().contentIntent==null){notice("Fără acțiune de deschidere");return;}n.getNotification().contentIntent.send();}catch(PendingIntent.CanceledException e){notice("Notificarea a expirat");show();}}else if(LeoNotificationService.dismiss(n))show();}});}};
        back=new Runnable(){public void run(){show();}};footer("Acțiuni","","Înapoi");
    }
}
