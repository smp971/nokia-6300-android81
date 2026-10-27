package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import org.json.*;import java.text.SimpleDateFormat;import java.util.*;
/** Small snapshots from local app state; no network, media polling or account access. */
final class HomeCards {
 static final String[] KEYS={"home-weather","home-alarm","home-music","home-favorites","home-agenda"};
 static final class Card {final int type;final String title,main,detail;Card(int t,String a,String b,String c){type=t;title=a;main=b;detail=c;}String signature(){return type+title+main+detail;}}
 static Card[] read(Context c,long now){return read(c,now,true);}
 static Card[] lockInfo(Context c,long now){return read(c,now,false);}
 static int[] order(String raw){ArrayList<Integer> out=new ArrayList<Integer>();for(String s:raw.split(","))try{int n=Integer.parseInt(s);if(n>=0&&n<5&&!out.contains(n))out.add(n);}catch(NumberFormatException ignored){}for(int n=0;n<5;n++)if(!out.contains(n))out.add(n);int[] ids=new int[5];for(int n=0;n<5;n++)ids[n]=out.get(n);return ids;}
 private static Card[] read(Context c,long now,boolean filtered){ArrayList<Card> out=new ArrayList<Card>();SharedPreferences p=c.getSharedPreferences("appearance",0);for(int type:filtered?order(p.getString("home-card-order","")):new int[]{0,1,2})if(!filtered||p.getBoolean(KEYS[type],type!=3)){if(type==0){SharedPreferences weather=c.getSharedPreferences("weather",0);String city=weather.getString("city",L.t("București")),main=L.t("Prognoză neîncărcată"),detail=L.t("Deschide Meteo pentru actualizare");try{JSONObject d=new JSONObject(weather.getString("data",""));JSONObject current=d.getJSONObject("current");main=WeatherData.number(current,"temperature_2m","°C")+" · "+WeatherData.code(current.optInt("weather_code",-1));long saved=weather.getLong("updated",0);detail=saved>0?"Cache · "+new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(saved)):L.t("Date fără ora actualizării");}catch(JSONException ignored){}out.add(new Card(0,L.t("Meteo · ")+city,main,detail));}
  else if(type==1){long next=Long.MAX_VALUE;String label="";try{JSONArray all=AlarmScheduler.alarms(c);for(int i=0;i<all.length();i++){JSONObject a=all.optJSONObject(i);if(a==null||!a.optBoolean("enabled"))continue;long when=AlarmScheduler.next(now,a);if(when<next){next=when;label=a.optString("label",L.t("Alarmă"));}}}catch(RuntimeException ignored){}out.add(new Card(1,L.t("Următoarea alarmă"),next==Long.MAX_VALUE?L.t("Nicio alarmă activă"):new SimpleDateFormat("EEE HH:mm",L.locale()).format(new Date(next)),next==Long.MAX_VALUE?L.t("OK: deschide Ceas Leo"):label));}
  else if(type==4){HomeAgenda.refresh(c);out.add(new Card(4,L.t("Agenda Leo"),HomeAgenda.title.isEmpty()?L.t("Niciun eveniment apropiat"):HomeAgenda.title,HomeAgenda.when.isEmpty()?L.t("OK: deschide Calendar"):HomeAgenda.when));}
  else if(type==3){out.add(new Card(3,L.t("Contacte favorite"),L.t("Favorite din agendă"),L.t("4 / 6 schimbă · OK deschide")));}
  else{PlaybackService music=PlaybackService.instance;boolean playing=music!=null&&music.playing();String title=L.t("Nimic în redare"),detail=L.t("OK: muzică și radio");if(music!=null&&(playing||!FmService.playing)){title=PlaybackService.title.isEmpty()?L.t("Muzică Leo"):PlaybackService.title;detail=playing?L.t("4 / 5 / 6 · control muzică"):L.t("4 / 5 / 6 · muzică în pauză");}else if(FmService.playing){title=FmService.stationName.isEmpty()?String.format(Locale.ROOT,"%.1f MHz",FmService.frequency/1000f):FmService.stationName;detail=L.t("Radio FM · în redare");}out.add(new Card(2,L.t("Acum se redă"),title,detail));}}
  return out.toArray(new Card[0]);
 }
 static String signature(Card[] cards){StringBuilder s=new StringBuilder();for(Card card:cards)s.append(card.signature()).append('\n');return s.toString();}
}
