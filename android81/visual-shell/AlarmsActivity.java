package ro.leo.visual;
import ro.leo.i18n.L;

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
        catch(RuntimeException corrupt) { notice(L.t("Alarmele nu pot fi citite; datele sunt păstrate")); finish(); return; }
        ArrayList<String> labels=new ArrayList<String>();
        for(int i=0;i<values.length();i++) {
            JSONObject a=values.optJSONObject(i); labels.add(String.format(Locale.ROOT,"%02d:%02d · %s",a.optInt("hour"),a.optInt("minute"),a.optBoolean("enabled")?L.t("Pornită"):L.t("Oprită"))+"\n"+a.optString("label")+" · "+AlarmScheduler.daysLabel(a));
        }
        list(L.t("Alarme"),labels,0,new Choice() { public void pick(int i) {
            final JSONObject alarm=values.optJSONObject(i);
            options(L.t("Alarmă"),new String[]{alarm.optBoolean("enabled")?L.t("Oprește"):L.t("Activează"),L.t("Editează"),L.t("Șterge")},new Choice() { public void pick(int selected) {
                if(selected==1) edit(alarm);
                else if(selected==0) {
                    try { AlarmScheduler.save(AlarmsActivity.this,alarm.getInt("id"),alarm.getInt("hour"),alarm.getInt("minute"),alarm.getString("label"),alarm.getBoolean("daily"),!alarm.getBoolean("enabled")); show(); }
                    catch(Exception failure) { notice(L.t("Programarea a eșuat")); }
                } else options(L.t("Ștergi alarma?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice() { public void pick(int confirmed) {
                    if(confirmed==1) try { AlarmScheduler.delete(AlarmsActivity.this,alarm.getInt("id")); show(); } catch(Exception failure) { notice(L.t("Ștergerea a eșuat")); }
                }});
            }});
        } },L.t("Nouă"),new Runnable() { public void run() { edit(null); } });
    }
    private void edit(final JSONObject existing) {
        Calendar now=Calendar.getInstance(); LinearLayout fields=fields(existing==null?L.t("Alarmă nouă"):L.t("Editează alarma"));
        final EditText hour=field(fields,L.t("Ora (0–23)"),""+(existing==null?now.get(Calendar.HOUR_OF_DAY):existing.optInt("hour")),InputType.TYPE_CLASS_NUMBER);
        final EditText minute=field(fields,L.t("Minute (0–59)"),""+(existing==null?now.get(Calendar.MINUTE):existing.optInt("minute")),InputType.TYPE_CLASS_NUMBER);
        final EditText label=field(fields,L.t("Nume"),existing==null?L.t("Alarmă"):existing.optString("label"),InputType.TYPE_CLASS_TEXT);
        final CheckBox[] days=new CheckBox[7];String[] names={L.t("Luni"),L.t("Marți"),L.t("Miercuri"),L.t("Joi"),L.t("Vineri"),L.t("Sâmbătă"),L.t("Duminică")};fields.addView(text(L.t("Zile · fără selecție: o singură dată"),14));for(int n=0;n<7;n++){days[n]=new CheckBox(this);days[n].setText(names[n]);days[n].setTextColor(android.graphics.Color.WHITE);days[n].setChecked(existing!=null&&(AlarmScheduler.days(existing)&(1<<n))!=0);fields.addView(days[n]);}
        hour.setNextFocusDownId(minute.getId()); minute.setNextFocusUpId(hour.getId()); minute.setNextFocusDownId(label.getId()); label.setNextFocusUpId(minute.getId());
        left=new Runnable() { public void run() {
            try {
                int h=Integer.parseInt(hour.getText().toString()),m=Integer.parseInt(minute.getText().toString());
                String name=label.getText().toString().trim();
                int mask=0;for(int n=0;n<7;n++)if(days[n].isChecked())mask|=1<<n;AlarmScheduler.saveDays(AlarmsActivity.this,existing==null?-1:existing.getInt("id"),h,m,name.isEmpty()?L.t("Alarmă"):name,mask,true);
                show(); notice(L.t("Alarmă programată"));
            } catch(Exception invalid) { notice(L.t("Verifică ora, minutele și limita de 32 de alarme")); }
        }};
        back=new Runnable() { public void run() { show(); } }; footer(L.t("Salvează"),L.t("Următor"),L.t("Șterge")); hour.requestFocus();
    }
}
