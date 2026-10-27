package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.database.Cursor;import android.os.Bundle;
import java.io.*;import java.util.*;import org.json.*;

/** On-demand daily overview. Checklist items are pending, not invented due dates. */
public final class DailyAgendaActivity extends LeoActivity {
    static final class Item {final String label;final Intent target;Item(String l,Intent i){label=l;target=i;}}
    private int generation;
    protected void onResume(){super.onResume();show();}
    protected void onPause(){generation++;super.onPause();}
    static JSONArray notes(Context c){try{File file=new File(c.getFilesDir(),"notes.json");return BackupEngine.atomicExists(file)?new JSONArray(new String(BackupEngine.atomicBytes(file,1048576),"UTF-8")):new JSONArray();}catch(Exception e){return new JSONArray();}}
    static ArrayList<String> pending(String body){ArrayList<String> out=new ArrayList<>();for(String line:body.split("\n"))if(line.startsWith("[ ] ")&&!line.substring(4).trim().isEmpty()){out.add(line.substring(4).trim());if(out.size()==12)break;}return out;}
    static int pendingCount(Context c){JSONArray notes=notes(c);int total=0;for(int n=0;n<notes.length()&&n<500;n++){JSONObject note=notes.optJSONObject(n);if(note!=null)for(String line:note.optString("body").split("\n"))if(line.startsWith("[ ] ")&&!line.substring(4).trim().isEmpty())total++;}return total;}
    static ArrayList<Item> read(Context c,long now){ArrayList<Item> out=new ArrayList<>();out.add(new Item(L.t("Calendar astăzi")+"\n"+android.text.format.DateFormat.format("EEE dd.MM.yyyy",now),new Intent(c,CalendarActivity.class).putExtra("day",now)));
        long next=Long.MAX_VALUE;String alarmName="";JSONArray alarms=AlarmScheduler.alarms(c);for(int n=0;n<alarms.length();n++){JSONObject a=alarms.optJSONObject(n);if(a==null||!a.optBoolean("enabled"))continue;long at=AlarmScheduler.next(now,a);if(at<next){next=at;alarmName=a.optString("label",L.t("Alarmă"));}}
        out.add(new Item(L.t("Următoarea alarmă")+"\n"+(next==Long.MAX_VALUE?L.t("Nicio alarmă activă"):android.text.format.DateFormat.format("EEE HH:mm",next)+" · "+alarmName),new Intent(c,ClockActivity.class).putExtra("tab",1)));
        Calendar day=Calendar.getInstance();day.setTimeInMillis(now);day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);long from=day.getTimeInMillis();day.add(Calendar.DATE,1);long until=day.getTimeInMillis();
        try{long own=CalendarReminders.calendar(c);if(own>=0)try(Cursor rows=c.getContentResolver().query(CalendarReminders.instances(from,until),new String[]{"event_id","begin","title"},"calendar_id=?",new String[]{""+own},"begin ASC")){int n=0;while(rows!=null&&rows.moveToNext()&&n++<12)out.add(new Item(android.text.format.DateFormat.format("HH:mm",rows.getLong(1))+" · "+rows.getString(2),new Intent(c,CalendarActivity.class).putExtra("day",rows.getLong(1)).putExtra("event_id",rows.getLong(0))));}}catch(RuntimeException e){out.add(new Item(L.t("Calendar indisponibil"),new Intent(c,CalendarActivity.class)));}
        JSONArray notes=notes(c);int count=0;for(int n=0;n<notes.length()&&n<500&&count<12;n++){JSONObject note=notes.optJSONObject(n);if(note==null)continue;for(String text:pending(note.optString("body"))){out.add(new Item(L.t("De făcut · ")+text,new Intent(c,NotesActivity.class).putExtra("note_id",note.optString("id"))));if(++count>=12)break;}}
        if(count==0)out.add(new Item(L.t("Nicio sarcină nebifată"),new Intent(c,NotesActivity.class)));return out;
    }
    private void show(){final int ticket=++generation;begin(L.t("Ziua mea"));root.addView(text(L.t("Se încarcă…"),17));footer("","",L.t("Înapoi"));worker.execute(new Runnable(){public void run(){final ArrayList<Item> items=read(DailyAgendaActivity.this,System.currentTimeMillis());ui(new Runnable(){public void run(){if(ticket!=generation)return;ArrayList<String> labels=new ArrayList<>();for(Item i:items)labels.add(i.label);list(L.t("Ziua mea"),labels,0,new Choice(){public void pick(int at){startActivity(items.get(at).target);}},L.t("Actualizează"),new Runnable(){public void run(){show();}});}});}});}
}
