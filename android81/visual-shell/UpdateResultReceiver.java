package ro.leo.visual;
import ro.leo.i18n.L;
public final class UpdateResultReceiver extends android.content.BroadcastReceiver {public void onReceive(final android.content.Context c,final android.content.Intent i){final PendingResult pending=goAsync();new Thread(new Runnable(){public void run(){try{UpdateEngine.result(c,i);}catch(RuntimeException e){UpdateEngine.fail(c,L.t("Rezultatul instalării nu a putut fi procesat"));}finally{pending.finish();}}},"LeoUpdateResult").start();}}
