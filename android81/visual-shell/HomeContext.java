package ro.leo.visual;
import ro.leo.i18n.L;import android.content.*;import org.json.*;import java.util.*;
/** One useful Home card; notification bodies are never read for this decision. */
final class HomeContext {
 static int priority(boolean unread,boolean media,boolean notices,boolean alarm,boolean weather){return unread?5:media?2:notices?5:alarm?1:weather?0:-1;}
 static String unreadLabel(int sms,int calls){String a=sms>0?sms+(sms==1?L.t(" SMS nou"):L.t(" SMS noi")):"";String b=calls>0?calls+(calls==1?L.t(" apel pierdut"):L.t(" apeluri pierdute")):"";return a.isEmpty()?b:b.isEmpty()?a:a+" · "+b;}
 static HomeCards.Card[] read(Context c,long now){HomeCards.Card[] all=HomeCards.read(c,now);HomeCards.Card media=null,alarm=null,weather=null;for(HomeCards.Card card:all){if(card.type==2)media=card;else if(card.type==1)alarm=card;else if(card.type==0)weather=card;}
 boolean active=media!=null&&(FmService.playing||(PlaybackService.instance!=null&&!PlaybackService.title.isEmpty()));long next=Long.MAX_VALUE;
 if(alarm!=null)try{JSONArray alarms=AlarmScheduler.alarms(c);for(int i=0;i<alarms.length();i++){JSONObject a=alarms.optJSONObject(i);if(a!=null&&a.optBoolean("enabled"))next=Math.min(next,AlarmScheduler.next(now,a));}}catch(RuntimeException ignored){}
 long age=now-c.getSharedPreferences("weather",0).getLong("updated",0);boolean fresh=weather!=null&&weather.weatherCode>=0&&age>=0&&age<=21600000;
 int count=LeoNotificationService.actionableCount();String unread=unreadLabel(UnreadState.sms,UnreadState.calls);int type=priority(!unread.isEmpty(),active,count>0,next>=now&&next-now<=86400000,fresh);
 HomeCards.Card card=type==5?new HomeCards.Card(5,L.t("Notificări"),unread.isEmpty()?count+L.t(" notificări"):unread,L.t("#: deschide notificările")):type==2?media:type==1?alarm:type==0?weather:null;
 return card==null?new HomeCards.Card[0]:new HomeCards.Card[]{card};}
}
