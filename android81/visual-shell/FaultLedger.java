package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import org.json.*;
/** Last 20 failure types only: no messages, paths, numbers or stack contents. */
final class FaultLedger {
 static synchronized void record(Context c,String area,Throwable t){try{if(!area.matches("[A-Za-z]{1,32}"))area="Leo";String type=t.getClass().getSimpleName();if(!type.matches("[A-Za-z0-9_$]{1,80}"))type="Exception";SharedPreferences p=c.getSharedPreferences("diagnostic-errors",0);JSONArray old=new JSONArray(p.getString("items","[]")),next=new JSONArray();for(int i=Math.max(0,old.length()-19);i<old.length();i++)next.put(old.get(i));next.put(new JSONObject().put("at",System.currentTimeMillis()).put("area",area).put("type",type));p.edit().putString("items",next.toString()).commit();}catch(Exception ignored){}}
 static String report(Context c){StringBuilder b=new StringBuilder(L.t("Erori Leo recente · tipuri, fără mesaje:\n"));try{JSONArray rows=new JSONArray(c.getSharedPreferences("diagnostic-errors",0).getString("items","[]"));for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String area=row.optString("area"),type=row.optString("type");if(!area.matches("[A-Za-z]{1,32}")||!type.matches("[A-Za-z0-9_$]{1,80}"))continue;b.append(new java.text.SimpleDateFormat("dd.MM HH:mm",java.util.Locale.ROOT).format(new java.util.Date(row.getLong("at")))).append(" · ").append(area).append(" · ").append(type).append('\n');}if(rows.length()==0)b.append(L.t("Nicio eroare înregistrată\n"));}catch(Exception e){b.append(L.t("Indisponibil\n"));}return b.toString();}
}
