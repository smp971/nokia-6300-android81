package ro.leo.visual;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Original Android adaptation of the clock/stopwatch workflow studied in KaiOS. */
public final class ClockActivity extends Activity {
    private Appearance.Palette palette;
    private final Handler handler=new Handler();
    private final ArrayList<Long> laps=new ArrayList<Long>();
    private Face face;
    private boolean stopwatch,running;
    private long accumulated,started;
    private String message="";
    private int boot;
    private final Runnable tick=new Runnable() {
        public void run() {
            face.invalidate();
            handler.postDelayed(this,stopwatch && running?100:1000);
        }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);palette=Appearance.palette(this);
        getWindow().setStatusBarColor(palette.bar);
        boot=Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        SharedPreferences saved=getSharedPreferences("clock",MODE_PRIVATE);
        accumulated=saved.getLong("elapsed",0); started=saved.getLong("started",0);
        running=saved.getBoolean("running",false) && boot>=0 && saved.getInt("boot",-2)==boot
            && started<=SystemClock.elapsedRealtime();
        stopwatch=saved.getBoolean("stopwatch",false);
        for(String value:saved.getString("laps","").split(",")) {
            try { if(laps.size()<10) laps.add(Long.parseLong(value)); } catch(NumberFormatException ignored) { }
        }
        face=new Face(); setContentView(face);
    }
    protected void onResume() { super.onResume(); handler.post(tick); }
    protected void onPause() { handler.removeCallbacks(tick); save(); super.onPause(); }
    private long elapsed() { return accumulated+(running?SystemClock.elapsedRealtime()-started:0); }
    private void save() {
        if(running) { accumulated=elapsed(); started=SystemClock.elapsedRealtime(); }
        StringBuilder values=new StringBuilder();
        for(Long lap:laps) { if(values.length()>0) values.append(','); values.append(lap); }
        getSharedPreferences("clock",MODE_PRIVATE).edit().putLong("elapsed",accumulated)
            .putLong("started",started).putBoolean("running",running).putInt("boot",boot)
            .putBoolean("stopwatch",stopwatch).putString("laps",values.toString()).apply();
    }
    private void changed() { save(); handler.removeCallbacks(tick); handler.post(tick); }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_BACK) { if(event.getRepeatCount()==0) finish(); return true; }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT) {
            if(event.getRepeatCount()==0) { stopwatch=!stopwatch; message=""; changed(); } return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
            if(event.getRepeatCount()==0) {
                if(!stopwatch) stopwatch=true;
                else if(running) { accumulated=elapsed(); running=false; }
                else { started=SystemClock.elapsedRealtime(); running=true; }
                message=""; changed();
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_MENU) {
            if(event.getRepeatCount()==0) {
                if(stopwatch && running) {
                    if(laps.size()<10) laps.add(elapsed()); else message="Maximum 10 ture";
                    changed();
                } else if(stopwatch) {
                    new KeypadDialog(this,"Cronometru",new String[]{"Resetează", "Păstrează"},new KeypadDialog.Action() {
                        public void select(int choice) {
                            if(choice==0) { accumulated=0; laps.clear(); message=""; changed(); }
                        }
                    }).show();
                } else {
                    new KeypadDialog(this,"Ceas",new String[]{"Alarme", "Temporizator"},new KeypadDialog.Action() {
                        public void select(int choice) {
                            startActivity(new Intent(ClockActivity.this,choice==0?AlarmsActivity.class:TimerActivity.class));
                        }
                    }).show();
                }
            }
            return true;
        }
        return super.onKeyDown(key,event);
    }
    private static String duration(long value) {
        return String.format(Locale.ROOT,"%02d:%02d.%01d",value/60000,(value/1000)%60,(value/100)%10);
    }
    private final class Face extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Face() { super(ClockActivity.this); setFocusable(true); requestFocus(); }
        void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align) {
            paint.setTextSize(size); paint.setColor(color); paint.setTextAlign(align); c.drawText(value,x,y,paint);
        }
        protected void onDraw(Canvas c) {
            c.save(); c.scale(getWidth()/240f,getHeight()/302f); c.drawColor(palette.background);
            text(c,"Ceas",120,26,18,Color.WHITE,Paint.Align.CENTER);
            text(c,"‹  "+(stopwatch?"Cronometru":"Ora")+"  ›",120,52,14,palette.accent,Paint.Align.CENTER);
            paint.setColor(palette.card);c.drawRoundRect(10,70,230,185,16,16,paint);
            paint.setColor(palette.accent);c.drawRoundRect(97,76,143,79,2,2,paint);
            if(stopwatch) {
                text(c,duration(elapsed()),120,110,35,Color.WHITE,Paint.Align.CENTER);
                text(c,running?"În mers":accumulated>0?"În pauză":"Pregătit",120,134,12,0xffd3bddf,Paint.Align.CENTER);
                int first=Math.max(0,laps.size()-4);
                for(int i=first;i<laps.size();i++) {
                    text(c,"Tura "+(i+1),15,162+(i-first)*22,13,Color.WHITE,Paint.Align.LEFT);
                    text(c,duration(laps.get(i)),225,162+(i-first)*22,13,Color.WHITE,Paint.Align.RIGHT);
                }
            } else {
                Date now=new Date();
                text(c,new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(now),120,128,40,Color.WHITE,Paint.Align.CENTER);
                text(c,new SimpleDateFormat("d MMMM yyyy",new Locale("ro")).format(now),120,160,17,0xffd3bddf,Paint.Align.CENTER);
                text(c,"Stânga / dreapta: schimbă fila",120,212,12,0xffd3bddf,Paint.Align.CENTER);
            }
            text(c,message,120,263,11,palette.accent,Paint.Align.CENTER);
            paint.setColor(palette.bar); c.drawRect(0,276,240,302,paint);
            text(c,stopwatch?(running?"Tură":"Resetare"):"Opțiuni",7,293,11,Color.WHITE,Paint.Align.LEFT);
            text(c,stopwatch?(running?"Pauză":accumulated>0?"Continuă":"Start"):"Cronometru",120,293,12,palette.accent,Paint.Align.CENTER);
            text(c,"Înapoi",233,293,11,Color.WHITE,Paint.Align.RIGHT); c.restore();
        }
    }
}
