package ro.leo.visual;
import android.content.*;import org.json.*;import java.text.SimpleDateFormat;import java.util.*;
/** Small snapshots from local app state; no network, media polling or account access. */
final class HomeCards {
 static final String[] KEYS={"home-weather","home-alarm","home-music"};
 static final class Card {final int type;final String title,main,detail;Card(int t,String a,String b,String c){type=t;title=a;main=b;detail=c;}String signature(){return type+title+main+detail;}}
 static Card[] read(Context c,long now){return read(c,now,true);}
 static Card[] lockInfo(Context c,long now){return read(c,now,false);}
 private static Card[] read(Context c,long now,boolean filtered){ArrayList<Card> out=new ArrayList<Card>();SharedPreferences p=c.getSharedPreferences("appearance",0);for(int type=0;type<3;type++)if(!filtered||p.getBoolean(KEYS[type],true)){if(type==0){SharedPreferences weather=c.getSharedPreferences("weather",0);String city=weather.getString("city","București"),main="Prognoză neîncărcată",detail="Deschide Meteo pentru actualizare";try{JSONObject d=new JSONObject(weather.getString("data",""));JSONObject current=d.getJSONObject("current");main=WeatherData.number(current,"temperature_2m","°C")+" · "+WeatherData.code(current.optInt("weather_code",-1));long saved=weather.getLong("updated",0);detail=saved>0?"Cache · "+new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(saved)):"Date fără ora actualizării";}catch(JSONException ignored){}out.add(new Card(0,"Meteo · "+city,main,detail));}
  else if(type==1){long next=Long.MAX_VALUE;String label="";try{JSONArray all=AlarmScheduler.alarms(c);for(int i=0;i<all.length();i++){JSONObject a=all.optJSONObject(i);if(a==null||!a.optBoolean("enabled"))continue;long when=AlarmMath.next(now,a.optInt("hour"),a.optInt("minute"),TimeZone.getDefault());if(when<next){next=when;label=a.optString("label","Alarmă");}}}catch(RuntimeException ignored){}out.add(new Card(1,"Următoarea alarmă",next==Long.MAX_VALUE?"Nicio alarmă activă":new SimpleDateFormat("EEE HH:mm",new Locale("ro")).format(new Date(next)),next==Long.MAX_VALUE?"OK: deschide Ceas Leo":label));}
  else{PlaybackService music=PlaybackService.instance;boolean playing=music!=null&&music.playing();String title="Nimic în redare",detail="OK: muzică și radio";if(music!=null&&(playing||!FmService.playing)){title=PlaybackService.title.isEmpty()?"Muzică Leo":PlaybackService.title;detail=playing?"Muzică · în redare":"Muzică · în pauză";}else if(FmService.playing){title=FmService.stationName.isEmpty()?String.format(Locale.ROOT,"%.1f MHz",FmService.frequency/1000f):FmService.stationName;detail="Radio FM · în redare";}out.add(new Card(2,"Acum se redă",title,detail));}}
  return out.toArray(new Card[0]);
 }
 static String signature(Card[] cards){StringBuilder s=new StringBuilder();for(Card card:cards)s.append(card.signature()).append('\n');return s.toString();}
}
