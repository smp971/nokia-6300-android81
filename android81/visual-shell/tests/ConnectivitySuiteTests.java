package ro.leo.visual;
import android.app.*;
import android.content.*;
import android.location.*;
import android.net.Uri;
import android.os.*;
import java.util.*;
/** Offline integration contracts. Does not place calls, transmit mail or request GPS. */
public final class ConnectivitySuiteTests extends Instrumentation {
    private int checks;
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();try{
        ArrayList<Uri> files=new ArrayList<Uri>();
        Intent plain=EmailActivity.composeIntent("test@example.invalid","Subject","Body",files);
        check(Intent.ACTION_SENDTO.equals(plain.getAction()),"plain email uses SENDTO");
        check("com.android.email".equals(plain.getPackage()),"fixed confirmation client");
        check(getTargetContext().getPackageManager().resolveActivity(plain,0)!=null,"email client resolves");
        Uri one=Uri.parse("content://ro.leo.visual.files/internal/attachment.txt");files.add(one);
        Intent single=EmailActivity.composeIntent("test@example.invalid","Subject","Body",files);
        check(Intent.ACTION_SEND.equals(single.getAction()),"single attachment contract");
        check(one.equals(single.getParcelableExtra(Intent.EXTRA_STREAM)),"attachment URI retained");
        check((single.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"read grant");
        check(single.getClipData()!=null&&single.getClipData().getItemCount()==1,"single grant clip");
        check(getTargetContext().getPackageManager().resolveActivity(single,0)!=null,"attachment client resolves");
        files.add(Uri.parse("content://ro.leo.visual.files/internal/second.txt"));
        Intent multi=EmailActivity.composeIntent("test@example.invalid","Subject","Body",files);
        check(Intent.ACTION_SEND_MULTIPLE.equals(multi.getAction()),"multiple attachments contract");
        check(multi.getClipData().getItemCount()==2&&multi.getParcelableArrayListExtra(Intent.EXTRA_STREAM).size()==2,"all URIs granted");
        Location fix=new Location("gps");fix.setElapsedRealtimeNanos(1000000000L);
        check(GpsActivity.ageSeconds(fix,122000000000L)==121,"stale fix age");
        check(GpsActivity.ageSeconds(fix,0)==0,"clock age nonnegative");
        int[] order=AppCatalog.parse("34,1,1,-1,999");
        check(order.length==36&&order[0]==34&&order[1]==1&&order[35]==35,"GPS appended without changing IDs/order");
        check(AppCatalog.NAMES.length==IconArt.COLORS.length&&AppCatalog.category(35)==3,"GPS icon/category aligned");
        LocationManager gps=(LocationManager)getTargetContext().getSystemService(Context.LOCATION_SERVICE);
        result.putString("gps_provider",gps!=null&&gps.getAllProviders().contains("gps")?"present":"absent");
        result.putString("result","PASS "+checks+" connectivity, email, GPS and catalog contracts");
    }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
    finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
