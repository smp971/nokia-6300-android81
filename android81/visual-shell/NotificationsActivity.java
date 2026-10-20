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
        if(LeoNotificationService.active==null){begin("Notificări");root.addView(text("Serviciul de notificări nu este conectat. Accesul Leo trebuie activat de instalatorul sistemului.",18),new LinearLayout.LayoutParams(-1,0,1));left=new Runnable(){public void run(){try{LeoNotificationService.reconnect(NotificationsActivity.this);notice("Reconectare cerută");}catch(RuntimeException e){notice("Acces indisponibil");}}};footer("Reîncearcă","","Înapoi");return;}
        final List<StatusBarNotification> all=LeoNotificationService.snapshot();ArrayList<String> labels=new ArrayList<String>();
        for(StatusBarNotification n:all)labels.add(app(n)+"\n"+safe(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE)));
        list("Notificări · "+all.size(),labels,0,new Choice(){public void pick(int i){detail(all.get(i));}},"",null);
    }
    private void detail(final StatusBarNotification n){
        detailKey=n.getKey();
        begin(app(n));ScrollView scroll=new ScrollView(this);Notification a=n.getNotification();CharSequence body=a.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);if(body==null)body=a.extras.getCharSequence(Notification.EXTRA_TEXT);
        scroll.addView(text(safe(a.extras.getCharSequence(Notification.EXTRA_TITLE))+"\n\n"+safe(body),18));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable(){public void run(){
            final Notification.Action[] actions=n.getNotification().actions;
            ArrayList<String> labels=new ArrayList<String>();labels.add("Deschide");labels.add(n.isClearable()?"Elimină":"Permanentă · nu se elimină");
            if(actions!=null)for(Notification.Action action:actions)labels.add(safe(action.title));
            options("Notificare",labels.toArray(new String[0]),new Choice(){public void pick(int i){
                if(i==0){try{if(n.getNotification().contentIntent==null){notice("Fără acțiune de deschidere");return;}n.getNotification().contentIntent.send();}catch(PendingIntent.CanceledException e){notice("Notificarea a expirat");show();}}
                else if(i==1){if(LeoNotificationService.dismiss(n))show();}
                else if(actions!=null&&i-2<actions.length)notificationAction(n,actions[i-2]);
            }});
        }};
        back=new Runnable(){public void run(){show();}};footer("Acțiuni","","Înapoi");
    }
    private void notificationAction(final StatusBarNotification notification,final Notification.Action action){
        if(action.actionIntent==null){notice("Acțiune indisponibilă");return;}
        android.app.RemoteInput[] inputs=action.getRemoteInputs();android.app.RemoteInput editable=null;if(inputs!=null)for(android.app.RemoteInput input:inputs)if(input.getAllowFreeFormInput()){editable=input;break;}
        if(editable==null){options(safe(action.title),new String[]{"Anulează","Execută acțiunea"},new Choice(){public void pick(int yes){if(yes==1)try{action.actionIntent.send();show();}catch(PendingIntent.CanceledException e){notice("Acțiunea a expirat");show();}}});return;}
        final android.app.RemoteInput reply=editable;LinearLayout fields=fields("Răspunde");final EditText value=field(fields,"Mesaj","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        left=new Runnable(){public void run(){if(value.getText().toString().trim().isEmpty()){notice("Scrie răspunsul");return;}options("Trimiți răspunsul?",new String[]{"Continuă editarea","Trimite"},new Choice(){public void pick(int yes){if(yes==1){android.os.Bundle result=new android.os.Bundle();result.putCharSequence(reply.getResultKey(),value.getText().toString());Intent fill=new Intent();android.app.RemoteInput.addResultsToIntent(new android.app.RemoteInput[]{reply},fill,result);try{action.actionIntent.send(NotificationsActivity.this,0,fill);show();}catch(PendingIntent.CanceledException e){notice("Acțiunea a expirat");show();}}}});}};back=new Runnable(){public void run(){detail(notification);}};footer("Trimite","Editor","Înapoi");value.requestFocus();
    }
}
