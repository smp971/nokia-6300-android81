package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;import android.telephony.*;import org.json.*;import java.util.*;

/** Read-only engineering snapshot. No subscriber identifiers or network changes.
 * Android 8.1 getDataState may describe the default phone: verify each physical
 * phone in telephony.registry before claiming a per-SIM data connection.
 */
public final class SimDataTrial extends Instrumentation {
    public void onCreate(Bundle b){start();}
    public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();JSONObject out=new JSONObject();out.put("default_data_sub",SubscriptionManager.getDefaultDataSubscriptionId());
        JSONArray rows=new JSONArray();List<SubscriptionInfo> sims=SubscriptionManager.from(c).getActiveSubscriptionInfoList();
        if(sims!=null)for(SubscriptionInfo sim:sims){
            TelephonyManager p=((TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE)).createForSubscriptionId(sim.getSubscriptionId());
            JSONObject row=new JSONObject();row.put("slot",sim.getSimSlotIndex());row.put("sub",sim.getSubscriptionId());row.put("data_enabled",p.isDataEnabled());row.put("voice_type",p.getVoiceNetworkType());row.put("data_type",p.getDataNetworkType());row.put("call_state",p.getCallState());
            ServiceState state=p.getServiceState();row.put("service_state",state==null?-1:state.getState());rows.put(row);
        }
        out.put("sims",rows);result.putString("result",out.toString());
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
    finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
