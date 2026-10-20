package ro.leo.visual;
import android.content.*;
/** Private action: acknowledges only the thread and timestamp in our pending intent. */
public final class NotificationReadReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){String address=intent.getStringExtra("thread");long through=intent.getLongExtra("through",-1);if(address==null||through<0)return;final PendingResult result=goAsync();UnreadState.readThread(context.getApplicationContext(),address,through,new Runnable(){public void run(){result.finish();}});}
}
