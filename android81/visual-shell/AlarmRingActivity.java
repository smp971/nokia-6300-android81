package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class AlarmRingActivity extends LeoActivity {
    private final android.os.Handler watch=new android.os.Handler();
    private final Runnable check=new Runnable() { public void run() {
        if(!AlarmRingService.ringing) finish(); else watch.postDelayed(this,1000);
    }};
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LeoPointerService.prepareForAlarm();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        begin(L.t("Alarmă")); TextView label=text(getIntent().getStringExtra("label"),28); label.setGravity(Gravity.CENTER);
        root.addView(label,new LinearLayout.LayoutParams(-1,0,1)); left=new Runnable() { public void run() { int id=getIntent().getIntExtra("alarm_id",-1);if(id>=0)AlarmScheduler.snooze(AlarmRingActivity.this,id);stop(); } }; footer(getIntent().getIntExtra("alarm_id",-1)>=0?L.t("Amână"):L.t("Oprește"),L.t("Oprește"),L.t("Oprește"));
    }
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);recreate();}
    private void stop() { stopService(new Intent(this,AlarmRingService.class)); finish(); }
    protected void onResume() { super.onResume(); watch.post(check); }
    protected void onPause() { watch.removeCallbacks(check); super.onPause(); }
    public void onBackPressed() { stop(); }
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode()==KeyEvent.KEYCODE_ENTER) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) stop(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
