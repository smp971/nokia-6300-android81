package ro.leo.visual;

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
    private long calendarId=-1;
    private boolean month;
    private MonthFace face;
    private static final class Event { long id,start,end; String title; }
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null) day.setTimeInMillis(state.getLong("day",System.currentTimeMillis()));
        showMonth();
    }
    protected void onSaveInstanceState(Bundle state) { state.putLong("day",day.getTimeInMillis()); super.onSaveInstanceState(state); }
    private void showMonth() {
        month=true; begin("Calendar"); face=new MonthFace(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() {
            options("Calendar",new String[]{"Eveniment nou","Azi","Agenda Leo"},new Choice() { public void pick(int i) {
                if(i==0) edit(null); else if(i==1) { day.setTimeInMillis(System.currentTimeMillis()); showMonth(); } else events(true);
            }});
        }};
        footer("Opțiuni","Ziua","Înapoi"); face.setFocusable(true); face.requestFocus();
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
        month=false;
        final Calendar start=(Calendar)day.clone(); start.set(Calendar.HOUR_OF_DAY,0); start.set(Calendar.MINUTE,0); start.set(Calendar.SECOND,0); start.set(Calendar.MILLISECOND,0);
        final Calendar end=(Calendar)start.clone(); end.add(Calendar.DATE,1);
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Event> rows=new ArrayList<Event>();
            try {
                long own=ownCalendar(false);
                if(own>=0) try(Cursor c=getContentResolver().query(CalendarContract.Events.CONTENT_URI,new String[]{"_id","title","dtstart","dtend"},
                    "calendar_id=? AND deleted=0"+(agenda?"":" AND dtstart<? AND dtend>?"),
                    agenda?new String[]{""+own}:new String[]{""+own,""+end.getTimeInMillis(),""+start.getTimeInMillis()},"dtstart ASC")) {
                    while(c!=null && c.moveToNext() && rows.size()<200) {
                        Event e=new Event(); e.id=c.getLong(0); e.title=c.getString(1); e.start=c.getLong(2); e.end=c.getLong(3); rows.add(e);
                    }
                }
                ui(new Runnable() { public void run() {
                    ArrayList<String> labels=new ArrayList<String>(); SimpleDateFormat time=new SimpleDateFormat(agenda?"dd.MM.yyyy HH:mm":"HH:mm",Locale.getDefault());
                    for(Event e:rows) labels.add(e.title+"\n"+time.format(new Date(e.start)));
                    list(agenda?"Agenda Leo":new SimpleDateFormat("dd.MM.yyyy",Locale.ROOT).format(day.getTime()),labels,0,new Choice() { public void pick(int i) { detail(rows.get(i),agenda); } },"Nou",new Runnable() { public void run() { edit(null); } });
                    back=new Runnable() { public void run() { showMonth(); } };
                }});
            } catch(RuntimeException failure) { ui(new Runnable() { public void run() { notice("Calendarul nu poate fi citit; verifică permisiunile"); showMonth(); } }); }
        }});
    }
    private void detail(final Event event,final boolean agenda) {
        options(event.title,new String[]{"Editează","Șterge","Înapoi"},new Choice() { public void pick(int i) {
            if(i==0) edit(event);
            else if(i==1) options("Ștergi evenimentul?",new String[]{"Păstrează","Șterge"},new Choice() { public void pick(int yes) {
                if(yes==1) try { getContentResolver().delete(CalendarContract.Events.CONTENT_URI,"_id=? AND calendar_id=?",new String[]{""+event.id,""+ownCalendar(false)}); events(agenda); }
                catch(RuntimeException failure) { notice("Evenimentul nu poate fi șters"); }
            }});
        }});
    }
    private void edit(final Event event) {
        month=false; final Calendar date=(Calendar)day.clone(); if(event!=null) date.setTimeInMillis(event.start);
        LinearLayout fields=fields(event==null?"Eveniment nou":"Editează");
        final EditText title=field(fields,"Titlu",event==null?"":event.title,InputType.TYPE_CLASS_TEXT);
        final EditText d=field(fields,"Zi",""+date.get(Calendar.DAY_OF_MONTH),InputType.TYPE_CLASS_NUMBER);
        final EditText m=field(fields,"Lună",""+(date.get(Calendar.MONTH)+1),InputType.TYPE_CLASS_NUMBER);
        final EditText y=field(fields,"An",""+date.get(Calendar.YEAR),InputType.TYPE_CLASS_NUMBER);
        final EditText h=field(fields,"Ora",""+date.get(Calendar.HOUR_OF_DAY),InputType.TYPE_CLASS_NUMBER);
        final EditText min=field(fields,"Minute",""+date.get(Calendar.MINUTE),InputType.TYPE_CLASS_NUMBER);
        final EditText duration=field(fields,"Durată (minute)",event==null?"60":""+Math.max(1,(event.end-event.start)/60000),InputType.TYPE_CLASS_NUMBER);
        EditText[] all={title,d,m,y,h,min,duration};
        for(int i=0;i<all.length;i++) { if(i>0) all[i].setNextFocusUpId(all[i-1].getId()); if(i+1<all.length) all[i].setNextFocusDownId(all[i+1].getId()); }
        left=new Runnable() { public void run() {
            try {
                String name=title.getText().toString().trim(); if(name.isEmpty()) { notice("Completează titlul"); return; }
                int year=Integer.parseInt(y.getText().toString()); if(year<1970 || year>2099) throw new IllegalArgumentException();
                Calendar value=Calendar.getInstance(); value.setLenient(false); value.clear();
                value.set(year,Integer.parseInt(m.getText().toString())-1,Integer.parseInt(d.getText().toString()),Integer.parseInt(h.getText().toString()),Integer.parseInt(min.getText().toString()));
                long begin=value.getTimeInMillis(),length=AlarmMath.duration(Integer.parseInt(duration.getText().toString()),0);
                ContentValues v=new ContentValues(); v.put("calendar_id",ownCalendar(true)); v.put("title",name); v.put("dtstart",begin); v.put("dtend",begin+length); v.put("eventTimezone",TimeZone.getDefault().getID());
                if(event==null) { if(getContentResolver().insert(CalendarContract.Events.CONTENT_URI,v)==null) throw new IllegalStateException(); }
                else if(getContentResolver().update(CalendarContract.Events.CONTENT_URI,v,"_id=? AND calendar_id=?",new String[]{""+event.id,""+calendarId})!=1) throw new IllegalStateException();
                day.setTimeInMillis(begin); events(false); notice("Eveniment salvat");
            } catch(Exception invalid) { notice("Verifică data, durata (1–1440 minute) și permisiunile"); }
        }};
        back=new Runnable() { public void run() { options("Renunți la editare?",new String[]{"Continuă","Renunță"},new Choice() { public void pick(int i) { if(i==1) showMonth(); } }); } };
        footer("Salvează","Următor","Șterge"); title.requestFocus();
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(month) {
            int amount=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:key==KeyEvent.KEYCODE_DPAD_UP?-7:key==KeyEvent.KEYCODE_DPAD_DOWN?7:0;
            if(amount!=0) { day.add(Calendar.DATE,amount); face.invalidate(); return true; }
            if(key==KeyEvent.KEYCODE_STAR || key==KeyEvent.KEYCODE_POUND) { if(event.getRepeatCount()==0) { day.add(Calendar.MONTH,key==KeyEvent.KEYCODE_STAR?-1:1); face.invalidate(); } return true; }
            if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) { if(event.getRepeatCount()==0) events(false); return true; }
        }
        return super.onKeyDown(key,event);
    }
    private final class MonthFace extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MonthFace() { super(CalendarActivity.this); }
        void text(Canvas c,String s,float x,float y,int size,int color) { p.setColor(color); p.setTextSize(size); p.setTextAlign(Paint.Align.CENTER); c.drawText(s,x,y,p); }
        protected void onDraw(Canvas c) {
            c.save(); c.scale(getWidth()/240f,getHeight()/226f);
            text(c,new SimpleDateFormat("MMMM yyyy",new Locale("ro")).format(day.getTime()),120,24,18,Color.WHITE);
            String[] names={"Lu","Ma","Mi","Jo","Vi","Sâ","Du"}; for(int i=0;i<7;i++) text(c,names[i],18+i*34,53,12,0xffd3bddf);
            Calendar first=(Calendar)day.clone(); first.set(Calendar.DAY_OF_MONTH,1); int offset=(first.get(Calendar.DAY_OF_WEEK)+5)%7;
            for(int value=1;value<=day.getActualMaximum(Calendar.DAY_OF_MONTH);value++) {
                int cell=offset+value-1; float x=18+cell%7*34,y=78+cell/7*25;
                if(value==day.get(Calendar.DAY_OF_MONTH)) { p.setColor(0xfff5eefb); c.drawRoundRect(new RectF(x-15,y-18,x+15,y+5),5,5,p); }
                text(c,""+value,x,y,14,value==day.get(Calendar.DAY_OF_MONTH)?0xff38224f:Color.WHITE);
            }
            text(c,"* luna precedentă · # luna următoare",120,221,11,0xffd3bddf); c.restore();
        }
    }
}
