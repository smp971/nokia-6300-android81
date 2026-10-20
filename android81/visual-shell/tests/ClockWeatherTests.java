package ro.leo.visual;
import android.app.*;import android.os.*;import org.json.*;import java.util.*;
/** Short offline contract checks; no alarm creation or network requests. */
public final class ClockWeatherTests extends Instrumentation {
 private int count;private void check(boolean b,String s){if(!b)throw new AssertionError(s);count++;}
 public void onCreate(Bundle b){start();}
 public void onStart(){Bundle result=new Bundle();try{
  check("00:00:00".equals(ClockActivity.duration(-1,false)),"negative duration clamped");check("01:01:01.2".equals(ClockActivity.duration(3661200,true)),"hours/minutes/tenths");check("25:00:00".equals(ClockActivity.duration(90000000,false)),"long stopwatch does not wrap at day");
  TimeZone ny=TimeZone.getTimeZone("America/New_York");Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.clear();c.set(2026,Calendar.MARCH,8,6,30);long before=c.getTimeInMillis();check(ny.getOffset(before+3600000)-ny.getOffset(before)==3600000,"world time observes DST");
  check("Senin".equals(WeatherData.code(0))&&"Ploaie".equals(WeatherData.code(63))&&"Ninsoare".equals(WeatherData.code(73)),"weather codes");check("Date indisponibile".equals(WeatherData.code(-1)),"unknown code is not clear sky");JSONObject j=new JSONObject("{\"temperature_2m\":12.6,\"missing\":null}");check("13°C".equals(WeatherData.number(j,"temperature_2m","°C")),"temperature rounding");check("—".equals(WeatherData.number(j,"missing","°"))&&"—".equals(WeatherData.number(j,"absent","°")),"missing weather value not zero");
  int[] order=AppCatalog.parse("25,0,3");check(order.length==27&&order[0]==25&&order[1]==0&&order[2]==3&&order[26]==26,"existing order retained; weather appended");check(AppCatalog.NAMES.length==IconArt.COLORS.length&&"Meteo".equals(AppCatalog.NAMES[26]),"catalog/icon stable IDs");result.putString("result","PASS "+count+" clock, DST, weather and catalog checks");
 }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
