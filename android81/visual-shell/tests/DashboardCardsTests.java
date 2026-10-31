package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;
/** Only isolated temporary preferences; no location/network requests or user cache changes. */
public final class DashboardCardsTests extends Instrumentation {
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public void onCreate(Bundle b){start();}
    public void onStart(){final String prefix="dashboard-test-"+java.util.UUID.randomUUID();final Context base=getTargetContext();
        Context c=new ContextWrapper(base){public SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences(prefix+name,mode);}};
        Bundle out=new Bundle();try{
            SharedPreferences.Editor prefs=c.getSharedPreferences("appearance",0).edit();for(String key:HomeCards.KEYS)prefs.putBoolean(key,key.equals("home-weather"));prefs.commit();
            HomeCards.Card[] cards=HomeCards.read(c,System.currentTimeMillis());check(cards.length==1&&cards[0].weatherCode==-1,"missing forecast keeps unknown icon");
            c.getSharedPreferences("weather",0).edit().putString("data","{\"current\":{\"temperature_2m\":10,\"weather_code\":3}}").commit();
            cards=HomeCards.read(c,System.currentTimeMillis());check(cards[0].weatherCode==3&&cards[0].main.contains("10"),"cloud icon follows cached forecast");
            c.getSharedPreferences("weather",0).edit().putString("data","{\"current\":{\"temperature_2m\":-5,\"weather_code\":73}}").commit();
            check(HomeCards.read(c,System.currentTimeMillis())[0].weatherCode==73,"snow code reaches dashboard");
            check(!new HomeCards.Card(0,"same","same","same",1).signature().equals(new HomeCards.Card(0,"same","same","same",2).signature()),"icon condition refreshes independently of text");
            out.putString("result","PASS 4 dashboard weather cache and icon-state checks");
        }catch(Throwable error){out.putString("error",android.util.Log.getStackTraceString(error));}
        finally{for(String name:new String[]{"appearance","weather"})c.getSharedPreferences(name,0).edit().clear().commit();}
        finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);
    }
}
