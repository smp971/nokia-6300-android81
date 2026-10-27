package ro.leo.visual;
import ro.leo.i18n.L;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local calendar backed by the Android CalendarProvider; no account synchronization. */
public final class CalendarActivity extends LeoActivity {
    private final Calendar day=Calendar.getInstance();
    private long calendarId=-1,searchEvent=-1;
    private boolean month;private int pageGeneration;private volatile int dotsGeneration;private final HashSet<Integer> eventDays=new HashSet<Integer>();
    private MonthFace face;
    private static final class Event { long id,start,end; String title,rule;int reminder=-1; }
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        day.setTimeInMillis(state!=null?state.getLong("day",System.currentTimeMillis()):getIntent().getLongExtra("day",System.currentTimeMillis()));
        searchEvent=getIntent().getLongExtra("event_id",-1);if(searchEvent>=0)events(false);else showMonth();
    }
    protected void onSaveInstanceState(Bundle state) { state.putLong("day",day.getTimeInMillis()); super.onSaveInstanceState(state); }
    private void showMonth() {
        ++pageGeneration;month=true;loadDots(); begin("Calendar"); face=new MonthFace(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() {
            options("Calendar",new String[]{L.t("Eveniment nou"),L.t("Azi"),L.t("Agenda Leo"),L.t("Instrumente · săptămână, căutare, ICS")},new Choice() { public void pick(int i) {
                if(i==3) startActivity(new Intent(CalendarActivity.this,CalendarToolsActivity.class)); else if(i==0) edit(null); else if(i==1) { day.setTimeInMillis(System.currentTimeMillis()); showMonth(); } else events(true);
            }});
        }};
        footer(L.t("Opțiuni"),L.t("Ziua"),L.t("Înapoi")); face.setFocusable(true); face.requestFocus();
    }
    private long ownCalendar(boolean create) {
        if(calendarId>=0) return calendarId;
        try(Cursor c=getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{"_id"},
            "account_name=? AND account_type=? AND name=?",new String[]{"Leo local",CalendarContract.ACCOUNT_TYPE_LOCAL,"Leo"},null)) {
            if(c!=null && c.moveToFirst()) { calendarId=c.getLong(0); return calendarId; }
        }
        if(!create) return -1;
        ContentValues v=new ContentValues(); v.put("account_name","Leo local"); v.put("account_type",CalendarContract.ACCOUNT_TYPE_LOCAL);
        v.put("name","Leo"); v.put("calendar_displayName","Calendar Leo"); v.put("calendar_color",0xff8b5fc9);
        v.put("calendar_access_level",CalendarContract.Calendars.CAL_ACCESS_OWNER); v.put("ownerAccount","Leo local");
        v.put("visible",1); v.put("sync_events",1); v.put("calendar_timezone",TimeZone.getDefault().getID());
        android.net.Uri uri=CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true")
            .appendQueryParameter("account_name","Leo local").appendQueryParameter("account_type",CalendarContract.ACCOUNT_TYPE_LOCAL).build();
        android.net.Uri added=getContentResolver().insert(uri,v);
        if(added==null) throw new IllegalStateException(); calendarId=ContentUris.parseId(added); return calendarId;
    }
    private void events(final boolean agenda) {
        final int page=++pageGeneration;month=false;
        final Calendar start=(Calendar)day.clone(); start.set(Calendar.HOUR_OF_DAY,0); start.set(Calendar.MINUTE,0); start.set(Calendar.SECOND,0); start.set(Calendar.MILLISECOND,0);
        final Calendar end=(Calendar)start.clone(); end.add(Calendar.DATE,1);
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Event> rows=new ArrayList<Event>();
            try {
                long own=ownCalendar(false);
                if(agenda)end.add(Calendar.DATE,89);
                if(own>=0) try(Cursor c=getContentResolver().query(CalendarReminders.instances(start.getTimeInMillis(),end.getTimeInMillis()),new String[]{"event_id","title","begin","end","rrule"},"calendar_id=?",new String[]{""+own},"begin ASC")) {
                    while(c!=null && c.moveToNext() && rows.size()<200) {
                        Event e=new Event(); e.id=c.getLong(0); e.title=c.getString(1); e.start=c.getLong(2); e.end=c.getLong(3);e.rule=c.getString(4); rows.add(e);
                    }
                }
                ui(new Runnable() { public void run() {
                    if(page!=pageGeneration)return;if(searchEvent>=0){long wanted=searchEvent;searchEvent=-1;for(Event e:rows)if(e.id==wanted){detail(e,agenda);return;}}ArrayList<String> labels=new ArrayList<String>(); SimpleDateFormat time=new SimpleDateFormat(agenda?"dd.MM.yyyy HH:mm":"HH:mm",Locale.getDefault());
                    for(Event e:rows) labels.add(e.title+"\n"+time.format(new Date(e.start)));
                    list(agenda?L.t("Agenda · 90 zile"):new SimpleDateFormat("dd.MM.yyyy",Locale.ROOT).format(day.getTime()),labels,0,new Choice() { public void pick(int i) { detail(rows.get(i),agenda); } },L.t("Nou"),new Runnable() { public void run() { edit(null); } });
                    back=new Runnable() { public void run() { showMonth(); } };
                }});
            } catch(RuntimeException failure) { ui(new Runnable() { public void run() { if(page!=pageGeneration)return;notice(L.t("Calendarul nu poate fi citit; verifică permisiunile")); showMonth(); } }); }
        }});
    }
    private void detail(final Event event,final boolean agenda) {
        options(event.title,new String[]{event.rule==null||event.rule.isEmpty()?L.t("Editează"):L.t("Editează seria"),event.rule==null||event.rule.isEmpty()?L.t("Șterge"):L.t("Șterge seria"),L.t("Înapoi")},new Choice() { public void pick(int i) {
            if(i==0) edit(event);
            else if(i==1) options(L.t("Ștergi evenimentul?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice() { public void pick(int yes) {
                if(yes==1) try { getContentResolver().delete(CalendarContract.Events.CONTENT_URI,"_id=? AND calendar_id=?",new String[]{""+event.id,""+ownCalendar(false)}); CalendarReminders.schedule(CalendarActivity.this);loadDots();events(agenda); }
                catch(RuntimeException failure) { notice(L.t("Evenimentul nu poate fi șters")); }
            }});
        }});
    }
    private void edit(final Event event) {
        ++pageGeneration;month=false;if(event!=null){try(Cursor c=getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,event.id),new String[]{"dtstart","dtend","duration","rrule"},null,null,null)){if(c!=null&&c.moveToFirst()){event.start=c.getLong(0);event.rule=c.getString(3);String length=c.getString(2);event.end=c.isNull(1)?event.start+parseDuration(length):c.getLong(1);}}try(Cursor c=getContentResolver().query(CalendarContract.Reminders.CONTENT_URI,new String[]{"minutes"},"event_id=? AND method=?",new String[]{""+event.id,""+CalendarContract.Reminders.METHOD_ALERT},null)){if(c!=null&&c.moveToFirst())event.reminder=c.getInt(0);}}
        final Calendar date=(Calendar)day.clone(); if(event!=null) date.setTimeInMillis(event.start);
        LinearLayout fields=fields(event==null?L.t("Eveniment nou"):L.t("Editează"));
        final EditText title=field(fields,L.t("Titlu"),event==null?"":event.title,InputType.TYPE_CLASS_TEXT);
        final EditText d=field(fields,L.t("Zi"),""+date.get(Calendar.DAY_OF_MONTH),InputType.TYPE_CLASS_NUMBER);
        final EditText m=field(fields,L.t("Lună"),""+(date.get(Calendar.MONTH)+1),InputType.TYPE_CLASS_NUMBER);
        final EditText y=field(fields,L.t("An"),""+date.get(Calendar.YEAR),InputType.TYPE_CLASS_NUMBER);
        final EditText h=field(fields,L.t("Ora"),""+date.get(Calendar.HOUR_OF_DAY),InputType.TYPE_CLASS_NUMBER);
        final EditText min=field(fields,L.t("Minute"),""+date.get(Calendar.MINUTE),InputType.TYPE_CLASS_NUMBER);
        final EditText duration=field(fields,L.t("Durată (minute)"),event==null?"60":""+Math.max(1,(event.end-event.start)/60000),InputType.TYPE_CLASS_NUMBER);
        final Spinner repeat=new Spinner(this);repeat.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{L.t("Nu se repetă"),L.t("Zilnic"),L.t("Săptămânal"),L.t("Lunar"),L.t("Anual")}));if(event!=null&&event.rule!=null){repeat.setSelection(event.rule.contains("DAILY")?1:event.rule.contains("WEEKLY")?2:event.rule.contains("MONTHLY")?3:event.rule.contains("YEARLY")?4:0);}fields.addView(text(L.t("Repetare · editarea afectează seria"),14));fields.addView(repeat);
        final int[] reminderValues={-1,0,5,10,30,60};final Spinner reminder=new Spinner(this);reminder.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{L.t("Fără notificare"),L.t("La început"),L.t("Cu 5 minute înainte"),L.t("Cu 10 minute înainte"),L.t("Cu 30 minute înainte"),L.t("Cu o oră înainte")}));if(event!=null)for(int i=0;i<reminderValues.length;i++)if(reminderValues[i]==event.reminder)reminder.setSelection(i);fields.addView(text(L.t("Notificare"),14));fields.addView(reminder);
        EditText[] all={title,d,m,y,h,min,duration};
        for(int i=0;i<all.length;i++) { if(i>0) all[i].setNextFocusUpId(all[i-1].getId()); if(i+1<all.length) all[i].setNextFocusDownId(all[i+1].getId()); }
        left=new Runnable() { public void run() {
            try {
                String name=title.getText().toString().trim(); if(name.isEmpty()) { notice(L.t("Completează titlul")); return; }
                int year=Integer.parseInt(y.getText().toString()); if(year<1970 || year>2099) throw new IllegalArgumentException();
                Calendar value=Calendar.getInstance(); value.setLenient(false); value.clear();
                value.set(year,Integer.parseInt(m.getText().toString())-1,Integer.parseInt(d.getText().toString()),Integer.parseInt(h.getText().toString()),Integer.parseInt(min.getText().toString()));
                long begin=value.getTimeInMillis(),length=AlarmMath.duration(Integer.parseInt(duration.getText().toString()),0);
                ContentValues v=new ContentValues(); v.put("calendar_id",ownCalendar(true)); v.put("title",name); v.put("dtstart",begin); v.put("dtend",begin+length); v.put("eventTimezone",TimeZone.getDefault().getID());
                String[] rules={null,"FREQ=DAILY","FREQ=WEEKLY","FREQ=MONTHLY","FREQ=YEARLY"};String rule=rules[repeat.getSelectedItemPosition()];v.put("rrule",rule);if(rule==null){v.putNull("duration");}else{v.putNull("dtend");v.put("duration","PT"+(length/1000)+"S");}int alert=reminderValues[reminder.getSelectedItemPosition()];v.put("hasAlarm",alert>=0?1:0);
                ArrayList<ContentProviderOperation> ops=new ArrayList<ContentProviderOperation>();if(event==null)ops.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(v).build());else{ops.add(ContentProviderOperation.newUpdate(CalendarContract.Events.CONTENT_URI).withSelection("_id=? AND calendar_id=?",new String[]{""+event.id,""+calendarId}).withValues(v).withExpectedCount(1).build());ops.add(ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection("event_id=?",new String[]{""+event.id}).build());}if(alert>=0){ContentProviderOperation.Builder op=ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValue("minutes",alert).withValue("method",CalendarContract.Reminders.METHOD_ALERT);if(event==null)op.withValueBackReference("event_id",0);else op.withValue("event_id",event.id);ops.add(op.build());}getContentResolver().applyBatch(CalendarContract.AUTHORITY,ops);CalendarReminders.schedule(CalendarActivity.this);
                day.setTimeInMillis(begin); events(false); notice(L.t("Eveniment salvat"));
            } catch(Exception invalid) { notice(L.t("Verifică data, durata (1–1440 minute) și permisiunile")); }
        }};
        back=new Runnable() { public void run() { options(L.t("Renunți la editare?"),new String[]{L.t("Continuă"),L.t("Renunță")},new Choice() { public void pick(int i) { if(i==1) showMonth(); } }); } };
        footer(L.t("Salvează"),L.t("Următor"),L.t("Șterge")); title.requestFocus();
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(month) {
            int amount=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:key==KeyEvent.KEYCODE_DPAD_UP?-7:key==KeyEvent.KEYCODE_DPAD_DOWN?7:0;
            if(amount!=0) { day.add(Calendar.DATE,amount); loadDots();face.invalidate(); return true; }
            if(key==KeyEvent.KEYCODE_STAR || key==KeyEvent.KEYCODE_POUND) { if(event.getRepeatCount()==0) { day.add(Calendar.MONTH,key==KeyEvent.KEYCODE_STAR?-1:1); loadDots();face.invalidate(); } return true; }
            if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) { if(event.getRepeatCount()==0) events(false); return true; }
        }
        return super.onKeyDown(key,event);
    }
    static long parseDuration(String value){try{return Long.parseLong(value.substring(2,value.length()-1))*1000L;}catch(Exception ignored){return 3600000L;}}
    private void loadDots(){final int token=++dotsGeneration;final Calendar first=(Calendar)day.clone();first.set(Calendar.DAY_OF_MONTH,1);first.set(Calendar.HOUR_OF_DAY,0);first.set(Calendar.MINUTE,0);first.set(Calendar.SECOND,0);final Calendar end=(Calendar)first.clone();end.add(Calendar.MONTH,1);worker.execute(new Runnable(){public void run(){if(token!=dotsGeneration)return;final HashSet<Integer> found=new HashSet<Integer>();try{long own=ownCalendar(false);if(own>=0)try(Cursor c=getContentResolver().query(CalendarReminders.instances(first.getTimeInMillis(),end.getTimeInMillis()),new String[]{"begin"},"calendar_id=?",new String[]{""+own},"begin ASC")){int n=0;while(c!=null&&c.moveToNext()&&n++<2000){Calendar d=Calendar.getInstance();d.setTimeInMillis(c.getLong(0));if(d.get(Calendar.MONTH)==first.get(Calendar.MONTH))found.add(d.get(Calendar.DAY_OF_MONTH));}}}catch(RuntimeException ignored){}ui(new Runnable(){public void run(){if(token!=dotsGeneration)return;eventDays.clear();eventDays.addAll(found);if(face!=null&&month)face.invalidate();}});}});}
    private final class MonthFace extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MonthFace() { super(CalendarActivity.this); }
        void text(Canvas c,String s,float x,float y,int size,int color) { p.setColor(color); p.setTextSize(size); p.setTextAlign(Paint.Align.CENTER); c.drawText(s,x,y,p); }
        protected void onDraw(Canvas c) {
            c.save(); c.scale(getWidth()/240f,getHeight()/226f);
            text(c,new SimpleDateFormat("MMMM yyyy",L.locale()).format(day.getTime()),120,24,18,Color.WHITE);
            String[] names={L.t("Lu"),L.t("Ma"),L.t("Mi"),L.t("Jo"),L.t("Vi"),L.t("Sâ"),L.t("Du")}; for(int i=0;i<7;i++) text(c,names[i],18+i*34,53,12,0xffd3bddf);
            Calendar first=(Calendar)day.clone(); first.set(Calendar.DAY_OF_MONTH,1); int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7;
            for(int value=1;value<=day.getActualMaximum(Calendar.DAY_OF_MONTH);value++) {
                int cell=offset+value-1; float x=18+cell%7*34,y=78+cell/7*25;
                if(value==day.get(Calendar.DAY_OF_MONTH)) { p.setColor(palette.selection); c.drawRoundRect(new RectF(x-15,y-18,x+15,y+5),5,5,p); }
                if(eventDays.contains(value)){p.setColor(palette.accent);c.drawCircle(x,y+9,2,p);}
                text(c,""+value,x,y,14,value==day.get(Calendar.DAY_OF_MONTH)?palette.ink:Color.WHITE);
            }
            text(c,L.t("* luna precedentă · # luna următoare"),120,221,11,0xffd3bddf); c.restore();
        }
    }
}
