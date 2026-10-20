package ro.leo.visual;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class AlarmsActivity extends LeoActivity {
    protected void onCreate(Bundle state) { super.onCreate(state); if(getIntent().getBooleanExtra("new",false))edit(null);else if(getIntent().hasExtra("edit_id")){org.json.JSONObject alarm=AlarmScheduler.get(this,getIntent().getIntExtra("edit_id",-1));if(alarm!=null)edit(alarm);else show();}else show(); }
    private void show() {
        final JSONArray values;
        try { values=AlarmScheduler.alarms(this); }
        catch(RuntimeException corrupt) { notice("Alarmele nu pot fi citite; datele sunt păstrate"); finish(); return; }
        ArrayList<String> labels=new ArrayList<String>();
        for(int i=0;i<values.length();i++) {
            JSONObject a=values.optJSONObject(i); labels.add(String.format(Locale.ROOT,"%02d:%02d · %s",a.optInt("hour"),a.optInt("minute"),a.optBoolean("enabled")?"Pornită":"Oprită")+"\n"+a.optString("label")+(a.optBoolean("daily")?" · zilnic":" · o dată"));
        }
        list("Alarme",labels,0,new Choice() { public void pick(int i) {
            final JSONObject alarm=values.optJSONObject(i);
            options("Alarmă",new String[]{alarm.optBoolean("enabled")?"Oprește":"Activează","Editează","Șterge"},new Choice() { public void pick(int selected) {
                if(selected==1) edit(alarm);
                else if(selected==0) {
                    try { AlarmScheduler.save(AlarmsActivity.this,alarm.getInt("id"),alarm.getInt("hour"),alarm.getInt("minute"),alarm.getString("label"),alarm.getBoolean("daily"),!alarm.getBoolean("enabled")); show(); }
                    catch(Exception failure) { notice("Programarea a eșuat"); }
                } else options("Ștergi alarma?",new String[]{"Păstrează","Șterge"},new Choice() { public void pick(int confirmed) {
                    if(confirmed==1) try { AlarmScheduler.delete(AlarmsActivity.this,alarm.getInt("id")); show(); } catch(Exception failure) { notice("Ștergerea a eșuat"); }
                }});
            }});
        } },"Nouă",new Runnable() { public void run() { edit(null); } });
    }
    private void edit(final JSONObject existing) {
        Calendar now=Calendar.getInstance(); LinearLayout fields=fields(existing==null?"Alarmă nouă":"Editează alarma");
        final EditText hour=field(fields,"Ora (0–23)",""+(existing==null?now.get(Calendar.HOUR_OF_DAY):existing.optInt("hour")),InputType.TYPE_CLASS_NUMBER);
        final EditText minute=field(fields,"Minute (0–59)",""+(existing==null?now.get(Calendar.MINUTE):existing.optInt("minute")),InputType.TYPE_CLASS_NUMBER);
        final EditText label=field(fields,"Nume",existing==null?"Alarmă":existing.optString("label"),InputType.TYPE_CLASS_TEXT);
        final CheckBox daily=new CheckBox(this); daily.setText("Repetă zilnic"); daily.setTextColor(android.graphics.Color.WHITE); daily.setChecked(existing!=null && existing.optBoolean("daily")); fields.addView(daily);
        hour.setNextFocusDownId(minute.getId()); minute.setNextFocusUpId(hour.getId()); minute.setNextFocusDownId(label.getId()); label.setNextFocusUpId(minute.getId());
        left=new Runnable() { public void run() {
            try {
                int h=Integer.parseInt(hour.getText().toString()),m=Integer.parseInt(minute.getText().toString());
                String name=label.getText().toString().trim();
                AlarmScheduler.save(AlarmsActivity.this,existing==null?-1:existing.getInt("id"),h,m,name.isEmpty()?"Alarmă":name,daily.isChecked(),true);
                show(); notice("Alarmă programată");
            } catch(Exception invalid) { notice("Verifică ora, minutele și limita de 32 de alarme"); }
        }};
        back=new Runnable() { public void run() { show(); } }; footer("Salvează","Următor","Șterge"); hour.requestFocus();
    }
}
