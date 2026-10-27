package ro.leo.visual;
import ro.leo.i18n.L;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.widget.*;
import java.util.*;

public final class NotificationsActivity extends LeoActivity {
    private String detailKey;private String groupPackage;private String dayFilter;
    private String selectedKey;
    private final ArrayList<String> keys=new ArrayList<String>();
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
        detailKey=null;keys.clear();
        if(LeoNotificationService.active==null){begin(L.t("Notificări"));root.addView(text(L.t("Serviciul de notificări nu este conectat. Accesul Leo trebuie activat de instalatorul sistemului."),18),new LinearLayout.LayoutParams(-1,0,1));left=new Runnable(){public void run(){try{LeoNotificationService.reconnect(NotificationsActivity.this);notice(L.t("Reconectare cerută"));}catch(RuntimeException e){notice(L.t("Acces indisponibil"));}}};footer(L.t("Reîncearcă"),"",L.t("Înapoi"));return;}
        final List<StatusBarNotification> all=new ArrayList<StatusBarNotification>(LeoNotificationService.snapshot());
        if(getSharedPreferences("notifications-ui",0).getBoolean("by-day",false)&&dayFilter==null){days(all);return;}if(dayFilter!=null){for(Iterator<StatusBarNotification> it=all.iterator();it.hasNext();)if(!dayFilter.equals(dayKey(it.next().getPostTime())))it.remove();}if(dayFilter==null&&getSharedPreferences("notifications-ui",0).getBoolean("grouped",true)&&groupPackage==null){groups(all);return;}if(groupPackage!=null){for(Iterator<StatusBarNotification> it=all.iterator();it.hasNext();)if(!groupPackage.equals(it.next().getPackageName()))it.remove();}
        ArrayList<String> labels=new ArrayList<String>();
        int selection=0;for(StatusBarNotification n:all){if(n.getKey().equals(selectedKey))selection=keys.size();keys.add(n.getKey());labels.add((missed(n)?L.t("Apel pierdut · "):"")+app(n)+"\n"+safe(n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE))+" · "+safe(n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT)));}
        list(L.t("Notificări · ")+all.size(),labels,selection,new Choice(){public void pick(int i){detail(all.get(i));}},L.t("Opțiuni"),new Runnable(){public void run(){options(L.t("Notificări"),new String[]{L.t("Acțiuni pentru selecție"),L.t("Actualizează"),L.t("Apeluri pierdute în Leo"),L.t("Elimină notificările nepermanente"),L.t("Grupează / arată toate"),L.t("Grupează pe zile"),L.t("Filtrează aplicația")},new Choice(){public void pick(int i){if(i==5){getSharedPreferences("notifications-ui",0).edit().putBoolean("by-day",true).apply();dayFilter=null;groupPackage=null;show();}else if(i==6)filter();else if(i==4){getSharedPreferences("notifications-ui",0).edit().putBoolean("by-day",false).apply();dayFilter=null;groupPackage=null;android.content.SharedPreferences p=getSharedPreferences("notifications-ui",0);p.edit().putBoolean("grouped",!p.getBoolean("grouped",true)).apply();show();}else if(i==0){StatusBarNotification n=selected();if(n!=null)actions(n);else notice(L.t("Selectează o notificare"));}else if(i==1)show();else if(i==2)startActivity(new Intent(NotificationsActivity.this,PhoneActivity.class).putExtra("history",true));else options(L.t("Elimini notificările?"),new String[]{L.t("Păstrează"),L.t("Elimină doar cele nepermanente")},new Choice(){public void pick(int yes){if(yes==1){for(StatusBarNotification n:LeoNotificationService.snapshot())LeoNotificationService.dismiss(n);show();}}});}});}});
    }
    private void groups(List<StatusBarNotification> all){selectedKey=null;keys.clear();final LinkedHashMap<String,ArrayList<StatusBarNotification>> groups=new LinkedHashMap<String,ArrayList<StatusBarNotification>>();for(StatusBarNotification n:all){ArrayList<StatusBarNotification> rows=groups.get(n.getPackageName());if(rows==null){rows=new ArrayList<StatusBarNotification>();groups.put(n.getPackageName(),rows);}rows.add(n);}final ArrayList<String> packages=new ArrayList<String>(groups.keySet());ArrayList<String> labels=new ArrayList<String>();for(String pkg:packages){List<StatusBarNotification> rows=groups.get(pkg);labels.add(app(rows.get(0))+" · "+rows.size()+"\n"+safe(rows.get(0).getNotification().extras.getCharSequence(Notification.EXTRA_TITLE)));}list(L.t("Notificări · ")+all.size(),labels,0,new Choice(){public void pick(int i){groupPackage=packages.get(i);show();}},L.t("Opțiuni"),new Runnable(){public void run(){options(L.t("Afișare notificări"),new String[]{L.t("Toate"),L.t("Pe zile"),L.t("Filtrează aplicația")},new Choice(){public void pick(int i){if(i==2){filter();return;}getSharedPreferences("notifications-ui",0).edit().putBoolean("grouped",false).putBoolean("by-day",i==1).apply();dayFilter=null;groupPackage=null;show();}});}});}
    public void onBackPressed(){if(detailKey!=null){detailKey=null;show();}else if(groupPackage!=null){groupPackage=null;show();}else if(dayFilter!=null){dayFilter=null;show();}else super.onBackPressed();}
    static String dayKey(long time){return new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(time));}
    private void filter(){final TreeMap<String,String> apps=new TreeMap<String,String>();for(StatusBarNotification n:LeoNotificationService.snapshot())apps.put(n.getPackageName(),app(n));final ArrayList<String> ids=new ArrayList<String>();ids.add("");ids.addAll(apps.keySet());ArrayList<String> labels=new ArrayList<String>();labels.add(L.t("Toate aplicațiile"));for(String id:apps.keySet())labels.add(apps.get(id));list(L.t("Filtrează notificările"),labels,0,new Choice(){public void pick(int i){groupPackage=i==0?null:ids.get(i);getSharedPreferences("notifications-ui",0).edit().putBoolean("grouped",false).putBoolean("by-day",false).apply();dayFilter=null;show();}},"",null);}
    private void days(List<StatusBarNotification> all){keys.clear();final LinkedHashMap<String,Integer> count=new LinkedHashMap<String,Integer>();for(StatusBarNotification n:all){String key=dayKey(n.getPostTime());count.put(key,count.containsKey(key)?count.get(key)+1:1);}final ArrayList<String> ids=new ArrayList<String>(count.keySet());ArrayList<String> labels=new ArrayList<String>();for(String id:ids)labels.add((id.equals(dayKey(System.currentTimeMillis()))?L.t("Astăzi"):id)+" · "+count.get(id));list(L.t("Notificări pe zile"),labels,0,new Choice(){public void pick(int i){dayFilter=ids.get(i);show();}},L.t("Toate"),new Runnable(){public void run(){getSharedPreferences("notifications-ui",0).edit().putBoolean("by-day",false).putBoolean("grouped",false).apply();dayFilter=null;show();}});}
    private void detail(final StatusBarNotification n){
        detailKey=n.getKey();
        begin(app(n));ScrollView scroll=new ScrollView(this);Notification a=n.getNotification();CharSequence body=a.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);if(body==null)body=a.extras.getCharSequence(Notification.EXTRA_TEXT);
        scroll.addView(text(safe(a.extras.getCharSequence(Notification.EXTRA_TITLE))+"\n\n"+safe(body),18));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable(){public void run(){actions(n);}};
        back=new Runnable(){public void run(){show();}};footer(L.t("Acțiuni"),L.t("Deschide"),L.t("Înapoi"));
    }
    private StatusBarNotification selected(){for(StatusBarNotification n:LeoNotificationService.snapshot())if(n.getKey().equals(selectedKey))return n;return null;}
    private void actions(final StatusBarNotification n){
            final Notification.Action[] actions=n.getNotification().actions;
            ArrayList<String> labels=new ArrayList<String>();labels.add(getPackageName().equals(n.getPackageName())&&"leo-sms".equals(n.getTag())?L.t("Deschide conversația Leo"):L.t("Deschide"));labels.add(n.isClearable()?L.t("Elimină"):L.t("Permanentă · nu se elimină"));
            if(actions!=null)for(Notification.Action action:actions)labels.add(safe(action.title));final int snoozeAt=labels.size();if(LeoNotificationService.canSnooze(n))labels.add(L.t("Amintește-mi mai târziu"));
            options(L.t("Notificare"),labels.toArray(new String[0]),new Choice(){public void pick(int i){
                if(i==snoozeAt&&LeoNotificationService.canSnooze(n)){options(L.t("Amână notificarea"),new String[]{L.t("5 minute"),L.t("15 minute"),L.t("1 oră")},new Choice(){public void pick(int at){notice(LeoNotificationService.snooze(n,new long[]{300000,900000,3600000}[at])?L.t("Notificare amânată"):L.t("Notificarea nu poate fi amânată"));show();}});}
                else if(i==0)open(n);
                else if(i==1){if(LeoNotificationService.dismiss(n))show();}
                else if(actions!=null&&i-2<actions.length)notificationAction(n,actions[i-2]);
            }});
    }
    void onListSelection(String title,int index){if(index>=0&&index<keys.size())selectedKey=keys.get(index);}
    private boolean missed(StatusBarNotification n){return "missed_call".equals(n.getNotification().category)||("com.android.dialer".equals(n.getPackageName())&&"phone_missed_call".equals(n.getNotification().getChannelId()));}
    private void open(StatusBarNotification n){try{if(missed(n)){startActivity(new Intent(this,PhoneActivity.class).putExtra("history",true));return;}if(n.getNotification().contentIntent==null){notice(L.t("Fără acțiune de deschidere"));return;}n.getNotification().contentIntent.send();}catch(PendingIntent.CanceledException e){notice(L.t("Notificarea a expirat"));show();}}
    public boolean onKeyDown(int key,android.view.KeyEvent e){if(detailKey!=null&&(key==android.view.KeyEvent.KEYCODE_DPAD_CENTER||key==android.view.KeyEvent.KEYCODE_ENTER)){if(e.getRepeatCount()==0)for(StatusBarNotification n:LeoNotificationService.snapshot())if(detailKey.equals(n.getKey())){open(n);break;}return true;}return super.onKeyDown(key,e);}
    private void notificationAction(final StatusBarNotification notification,final Notification.Action action){
        if(action.actionIntent==null){notice(L.t("Acțiune indisponibilă"));return;}
        android.app.RemoteInput[] inputs=action.getRemoteInputs();android.app.RemoteInput editable=null;if(inputs!=null)for(android.app.RemoteInput input:inputs)if(input.getAllowFreeFormInput()){editable=input;break;}
        if(editable==null){options(safe(action.title),new String[]{L.t("Anulează"),L.t("Execută acțiunea")},new Choice(){public void pick(int yes){if(yes==1)try{action.actionIntent.send();show();}catch(PendingIntent.CanceledException e){notice(L.t("Acțiunea a expirat"));show();}}});return;}
        final android.app.RemoteInput reply=editable;LinearLayout fields=fields(L.t("Răspunde","message"));final EditText value=field(fields,L.t("Mesaj"),"",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        left=new Runnable(){public void run(){if(value.getText().toString().trim().isEmpty()){notice(L.t("Scrie răspunsul"));return;}options(L.t("Trimiți răspunsul?"),new String[]{L.t("Continuă editarea"),L.t("Trimite")},new Choice(){public void pick(int yes){if(yes==1){android.os.Bundle result=new android.os.Bundle();result.putCharSequence(reply.getResultKey(),value.getText().toString());Intent fill=new Intent();android.app.RemoteInput.addResultsToIntent(new android.app.RemoteInput[]{reply},fill,result);try{action.actionIntent.send(NotificationsActivity.this,0,fill);show();}catch(PendingIntent.CanceledException e){notice(L.t("Acțiunea a expirat"));show();}}}});}};back=new Runnable(){public void run(){detail(notification);}};footer(L.t("Trimite"),"Editor",L.t("Înapoi"));value.requestFocus();
    }
}
