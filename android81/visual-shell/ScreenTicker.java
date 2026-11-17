package ro.leo.visual;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;

/** UI refresh work only while resumed and the physical display is on. */
final class ScreenTicker {
    interface Work { long refresh(); }
    private final Context context;
    private final Work work;
    private final Runnable inactive;
    private final Handler handler=new Handler();
    private boolean started;
    private final Runnable tick=new Runnable(){public void run(){refresh();}};
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        public void onReceive(Context c,Intent intent){refresh();}
    };
    ScreenTicker(Context context,Work work){this(context,work,null);}
    ScreenTicker(Context context,Work work,Runnable inactive){this.context=context;this.work=work;this.inactive=inactive;}
    void start(){
        if(!started){IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);context.registerReceiver(screen,filter);started=true;}
        refresh();
    }
    void refresh(){
        handler.removeCallbacks(tick);
        if(!started || !((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive()){if(inactive!=null)inactive.run();return;}
        long delay=work.refresh();
        if(delay>0)handler.postDelayed(tick,delay);
    }
    void stop(){handler.removeCallbacks(tick);if(started){started=false;context.unregisterReceiver(screen);}if(inactive!=null)inactive.run();}
}
