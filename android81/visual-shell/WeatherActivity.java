package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.graphics.*;import android.os.*;import android.text.InputType;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;

/** Visible-only, manual-city forecast. Cache is explicitly labelled, never a fake live reading. */
public final class WeatherActivity extends LeoActivity {
 private final KeyRelease actions=new KeyRelease();
 private Face face;private JSONObject data;private String city=L.t("București"),status="";private double latitude=44.43225,longitude=26.10626;private long updated;private int page,day,request;private boolean busy;
 protected void onCreate(Bundle b){super.onCreate(b);if(b!=null){page=Math.max(0,Math.min(2,b.getInt("page",0)));day=Math.max(0,Math.min(6,b.getInt("day",0)));}SharedPreferences p=getSharedPreferences("weather",0);city=p.getString("city",city);latitude=Double.longBitsToDouble(p.getLong("lat",Double.doubleToLongBits(latitude)));longitude=Double.longBitsToDouble(p.getLong("lon",Double.doubleToLongBits(longitude)));updated=p.getLong("updated",0);try{String s=p.getString("data","");if(!s.isEmpty())data=new JSONObject(s);}catch(JSONException ignored){}face=new Face();setContentView(face);if(data==null||System.currentTimeMillis()-updated>1800000)refresh();}
 protected void onSaveInstanceState(Bundle state){state.putInt("page",page);state.putInt("day",day);super.onSaveInstanceState(state);}
 protected void onPause(){actions.reset();super.onPause();}
 public boolean dispatchKeyEvent(KeyEvent event){
  int key=event.getKeyCode();
  if(!form&&((key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_CENTER)||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_BACK)){
   if(actions.accept(event))onKeyDown(key,event);return true;
  }
  return super.dispatchKeyEvent(event);
 }
 protected void onDestroy(){request++;super.onDestroy();}
 private void post(Runnable r){if(!isDestroyed()&&!isFinishing())ui(r);}
 private void refresh(){if(busy)return;busy=true;status=L.t("Se actualizează…");face.invalidate();final int token=++request;final double lat=latitude,lon=longitude;worker.execute(new Runnable(){public void run(){try{final JSONObject next=WeatherData.forecast(lat,lon);post(new Runnable(){public void run(){if(token!=request)return;data=next;updated=System.currentTimeMillis();busy=false;status="";save();face.invalidate();}});}catch(Exception e){post(new Runnable(){public void run(){if(token!=request)return;busy=false;status=L.t("Actualizare eșuată · vezi cache");face.invalidate();}});}}});}
 private void save(){getSharedPreferences("weather",0).edit().putString("city",city).putLong("lat",Double.doubleToLongBits(latitude)).putLong("lon",Double.doubleToLongBits(longitude)).putLong("updated",updated).putString("data",data==null?"":data.toString()).apply();}
 private void search(){LinearLayout fields=fields(L.t("Caută oraș"));final EditText query=field(fields,L.t("Oraș / oraș, țară"),city,InputType.TYPE_CLASS_TEXT);left=new Runnable(){public void run(){final String term=query.getText().toString().trim();if(term.length()<2||term.length()>100){notice(L.t("Scrie între 2 și 100 de caractere"));return;}if(busy){notice(L.t("Așteaptă actualizarea"));return;}busy=true;final int token=++request;notice(L.t("Caut orașul…"));worker.execute(new Runnable(){public void run(){try{final JSONArray results=WeatherData.search(term);post(new Runnable(){public void run(){if(token!=request)return;busy=false;if(results==null||results.length()==0){notice(L.t("Niciun oraș găsit"));return;}String[] names=new String[results.length()];for(int i=0;i<names.length;i++){JSONObject o=results.optJSONObject(i);names[i]=o.optString("name")+" · "+o.optString("admin1")+" · "+o.optString("country_code");}options(L.t("Alege localitatea"),names,new Choice(){public void pick(int i){JSONObject o=results.optJSONObject(i);city=o.optString("name");latitude=o.optDouble("latitude");longitude=o.optDouble("longitude");data=null;updated=0;day=0;save();showFace();refresh();}});}});}catch(Exception e){post(new Runnable(){public void run(){if(token!=request)return;busy=false;notice(L.t("Căutare indisponibilă; verifică Internetul"));}});}}});}};back=new Runnable(){public void run(){showFace();}};footer(L.t("Caută"),L.t("Următor"),L.t("Șterge"));query.requestFocus();}
 private void showFace(){form=false;left=null;back=null;setContentView(face);face.requestFocus();face.invalidate();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(face.getWindowToken(),0);}
 private void menu(){options(L.t("Meteo"),new String[]{L.t("Actualizează"),L.t("Schimbă orașul"),L.t("Despre date și confidențialitate")},new Choice(){public void pick(int i){if(i==0)refresh();else if(i==1)search();else options(L.t("Sursa prognozei"),new String[]{"Open-Meteo · CC BY 4.0",L.t("Localități · GeoNames"),L.t("Date modelate, nu senzor local"),L.t("Oraș transmis la căutare"),L.t("Coordonate trimise la prognoză")},new Choice(){public void pick(int n){}});}});}
 public boolean onKeyDown(int key,KeyEvent e){if(form)return super.onKeyDown(key,e);if(key==KeyEvent.KEYCODE_BACK){finish();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getRepeatCount()==0)page=(page+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:2))%3;face.invalidate();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){day=Math.max(0,Math.min(6,day+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1)));face.invalidate();return true;}if(key==KeyEvent.KEYCODE_MENU){if(e.getRepeatCount()==0)menu();return true;}if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(e.getRepeatCount()==0){if(page==1){page=2;face.invalidate();}else refresh();}return true;}return super.onKeyDown(key,e);}
 protected void onResume(){super.onResume();if(face!=null){face.panel.appearance();face.invalidate();}}
 private final class Face extends View {
  final WeatherPanel panel=new WeatherPanel(WeatherActivity.this);
  Face(){super(WeatherActivity.this);setFocusable(true);requestFocus();}
  public boolean isOpaque(){return true;}
  protected void onDraw(Canvas canvas){panel.draw(canvas,getWidth(),getHeight(),palette,data,city,page,day,busy,status,updated);}
 }
}
