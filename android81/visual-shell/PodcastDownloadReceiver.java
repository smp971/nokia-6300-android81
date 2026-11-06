package ro.leo.visual;
import android.app.DownloadManager;import android.content.*;
/** Broadcast IDs are checked against our journal and provider state before any file operation. */
public final class PodcastDownloadReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(i.getAction()))return;long id=i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1);try{if(PodcastDownloads.find(c,id)!=null)PodcastDownloads.request(c,id);}catch(Exception ignored){}}
}
