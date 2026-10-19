package ro.leo.visual;

import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

public final class TimerActivity extends LeoActivity {
    private final Handler handler=new Handler();
    private TextView remaining;
    private final Runnable tick=new Runnable() { public void run() {
        if(remaining!=null) {
            long value=(AlarmScheduler.remaining(TimerActivity.this)+999)/1000;
            remaining.setText(String.format(Locale.ROOT,"%02d:%02d:%02d",value/3600,value/60%60,value%60));
            if(!AlarmScheduler.state(TimerActivity.this).getBoolean("timer",false)) { remaining=null; show(); return; }
            handler.postDelayed(this,500);
        }
    }};
    protected void onCreate(Bundle state) { super.onCreate(state); show(); }
    protected void onResume() { super.onResume(); handler.removeCallbacks(tick); handler.post(tick); }
    protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    private void show() {
        handler.removeCallbacks(tick); remaining=null;
        if(AlarmScheduler.state(this).getBoolean("timer",false)) {
            begin("Temporizator"); remaining=text("",38); remaining.setGravity(Gravity.CENTER);
            root.addView(remaining,new LinearLayout.LayoutParams(-1,0,1));
            root.addView(text("Continuă și cu aplicația închisă",16));
            left=new Runnable() { public void run() {
                options("Oprești temporizatorul?",new String[]{"Continuă","Oprește"},new Choice() { public void pick(int i) { if(i==1) { AlarmScheduler.cancelTimer(TimerActivity.this); show(); } } });
            }};
            footer("Oprește","","Înapoi"); handler.post(tick);
        } else {
            LinearLayout fields=fields("Temporizator");
            final EditText minutes=field(fields,"Minute (maximum 1440)","1",InputType.TYPE_CLASS_NUMBER);
            final EditText seconds=field(fields,"Secunde (0–59)","0",InputType.TYPE_CLASS_NUMBER);
            minutes.setNextFocusDownId(seconds.getId()); seconds.setNextFocusUpId(minutes.getId());
            left=new Runnable() { public void run() {
                try { AlarmScheduler.timer(TimerActivity.this,AlarmMath.duration(Integer.parseInt(minutes.getText().toString()),Integer.parseInt(seconds.getText().toString()))); show(); }
                catch(RuntimeException invalid) { notice("Durata trebuie să fie între 1 secundă și 24 de ore"); }
            }};
            footer("Start","Următor","Șterge"); minutes.requestFocus();
        }
    }
}
