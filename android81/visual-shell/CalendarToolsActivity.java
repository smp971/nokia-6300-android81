package ro.leo.visual;
import android.content.*;import android.database.Cursor;import android.os.Bundle;import android.provider.CalendarContract;import android.text.InputType;import android.widget.*;import java.io.*;import java.text.*;import java.util.*;import org.json.*;
/** Additive local ICS exchange; unsupported fields are rejected before provider writes. */
public final class CalendarToolsActivity extends LeoActivity {
 private int weekOffset,generation;
 protected void onCreate(Bundle b){super.onCreate(b);String path=getIntent().getStringExtra("file");if(path!=null)preview(path);else home();}
 private void home(){generation++;list("Calendar · instrumente",Arrays.asList("Săptămâna de lucru","Caută evenimente","Exportă Calendar Leo · ICS","Importă · deschide un .ics din Fișiere","Aniversări din contacte"),0,new Choice(){public void pick(int i){if(i==0)week();else if(i==1)search();else if(i==2)export();else if(i==3)startActivity(new Intent(CalendarToolsActivity.this,FilesActivity.class));else startActivity(new Intent(CalendarToolsActivity.this,ContactExtrasActivity.class));}},"",null);}
 private void search(){generation++;LinearLayout f=fields("Caută în Calendar");final EditText q=field(f,"Titlu","",InputType.TYPE_CLASS_TEXT);left=new Runnable(){public void run(){String term=q.getText().toString().trim();if(term.isEmpty()){notice("Scrie un titlu");return;}query(false,term);}};back=new Runnable(){public void run(){home();}};footer("Caută","Editor","Șterge");q.requestFocus();}
 private void week(){query(true,"");}
 private void query(final boolean week,final String term){final int token=++generation;final int offset=weekOffset;begin("Calendar · se caută");worker.execute(new Runnable(){public void run(){final ArrayList<Long> starts=new ArrayList<Long>();final ArrayList<String> labels=new ArrayList<String>();try{long own=CalendarArchive.calendar(CalendarToolsActivity.this);Calendar from=Calendar.getInstance();from.set(Calendar.HOUR_OF_DAY,0);from.set(Calendar.MINUTE,0);from.set(Calendar.SECOND,0);from.set(Calendar.MILLISECOND,0);if(week)from.add(Calendar.DATE,-((from.get(Calendar.DAY_OF_WEEK)+5)%7));else from.add(Calendar.YEAR,-1);if(week)from.add(Calendar.DATE,offset*7);Calendar to=(Calendar)from.clone();to.add(week?Calendar.DATE:Calendar.YEAR,week?5:3);SimpleDateFormat fmt=new SimpleDateFormat("EEE dd.MM · HH:mm",new Locale("ro"));if(own>=0)try(Cursor c=getContentResolver().query(CalendarReminders.instances(from.getTimeInMillis(),to.getTimeInMillis()),new String[]{"title","begin"},"calendar_id=?",new String[]{""+own},"begin ASC")){while(c!=null&&c.moveToNext()&&labels.size()<200){String title=c.getString(0)==null?"Eveniment":c.getString(0);if(!week&&!title.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))continue;starts.add(c.getLong(1));labels.add(title+"\n"+fmt.format(new Date(c.getLong(1))));}}ui(new Runnable(){public void run(){if(token!=generation)return;list(week?"Luni – vineri":"Rezultate · −1 / +2 ani",labels,0,new Choice(){public void pick(int i){startActivity(new Intent(CalendarToolsActivity.this,CalendarActivity.class).putExtra("day",starts.get(i)));}},"Opțiuni",new Runnable(){public void run(){if(!week){home();return;}options("Săptămâna de lucru",new String[]{"Precedentă","Următoare","Săptămâna curentă"},new Choice(){public void pick(int i){weekOffset=i==2?0:Math.max(-52,Math.min(52,weekOffset+(i==0?-1:1)));week();}});}});back=new Runnable(){public void run(){home();}};}});}catch(Exception e){ui(new Runnable(){public void run(){notice("Calendar indisponibil");home();}});}}});}
 private void export(){worker.execute(new Runnable(){public void run(){try{String body=CalendarIcs.encode(CalendarArchive.snapshot(CalendarToolsActivity.this));File dir=new File(getFilesDir(),"documents");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException();File file=File.createTempFile("calendar-leo-",".ics",dir);try(FileOutputStream out=new FileOutputStream(file)){out.write(body.getBytes("UTF-8"));out.getFD().sync();}ui(new Runnable(){public void run(){notice("ICS salvat în Documente Leo");}});}catch(Exception e){ui(new Runnable(){public void run(){notice("Export nereușit");}});}}});}
 private void preview(final String path) {
  begin("Import Calendar");
  worker.execute(new Runnable(){public void run(){
   try {
    File f=new File(path); boolean valid=false;
    for(File root:LeoFileProvider.roots(CalendarToolsActivity.this).values())if(LeoFileProvider.inside(root,f))valid=true;
    if(!valid||!f.isFile()||f.length()>524288)throw new IOException();
    final JSONArray rows;try(InputStream in=new FileInputStream(f)){rows=CalendarIcs.decode(in);}
    ui(new Runnable(){public void run(){
     options("Adaugi "+rows.length()+" evenimente?",new String[]{"Anulează","Importă · fără înlocuire"},new Choice(){public void pick(int i){
      if(i==0){finish();return;}
      worker.execute(new Runnable(){public void run(){
       try {final int count=CalendarIcs.append(CalendarToolsActivity.this,rows);ui(new Runnable(){public void run(){notice(count+" evenimente noi · duplicatele omise");home();}});}
       catch(Exception e){FaultLedger.record(CalendarToolsActivity.this,"CalendarImport",e);ui(new Runnable(){public void run(){notice("Import nereușit · agenda păstrată");home();}});}
      }});
     }});
    }});
   }catch(Exception e){ui(new Runnable(){public void run(){notice("ICS invalid sau nesuportat · folosește UTC, dată simplă ori export Leo");home();}});}
  }});
 }
}
