package ro.leo.radiotest;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.*;
import android.util.Log;
import android.widget.TextView;
import org.json.*;
import java.io.*;
import java.util.*;

/** Local platform-signed engineering tool; never ships as a user application. */
public final class ApnCatalogTrial extends Activity {
    private static final Uri APNS=Uri.parse("content://telephony/carriers");
    private static final String[] COLUMNS={"_id","name","numeric","apn","type","protocol","roaming_protocol","mvno_type","mvno_match_data","edited"};
    private JSONArray rows(Uri uri,String selection,String[] args)throws Exception{
        JSONArray out=new JSONArray();
        try(Cursor c=getContentResolver().query(uri,COLUMNS,selection,args,null)){
            if(c==null)throw new IOException("Missing APN cursor");
            while(c.moveToNext()){JSONObject row=new JSONObject();for(int i=0;i<COLUMNS.length;i++)row.put(COLUMNS[i],c.isNull(i)?JSONObject.NULL:c.getString(i));out.put(row);}
        }
        return out;
    }
    private Uri preferred(int sub){return Uri.parse("content://telephony/carriers/preferapn/subId/"+sub);}
    private void save(String name,JSONObject value)throws Exception{
        try(FileOutputStream out=openFileOutput(name,MODE_PRIVATE)){out.write(value.toString(2).getBytes("UTF-8"));out.getFD().sync();}
    }
    private JSONObject snapshot(List<SubscriptionInfo> sims)throws Exception{
        JSONObject out=new JSONObject(), prefs=new JSONObject();
        for(SubscriptionInfo sim:sims)prefs.put(""+sim.getSubscriptionId(),rows(preferred(sim.getSubscriptionId()),null,null));
        out.put("preferred",prefs).put("custom",rows(APNS,"edited<>0",null));
        return out;
    }
    private void restorePreferred(JSONObject before,List<SubscriptionInfo> sims)throws Exception{
        for(SubscriptionInfo sim:sims){int sub=sim.getSubscriptionId();JSONArray old=before.getJSONObject("preferred").getJSONArray(""+sub);if(old.length()==0)continue;
            JSONObject row=old.getJSONObject(0);JSONArray candidates=rows(APNS,"numeric=? AND apn=?",new String[]{row.getString("numeric"),row.getString("apn")});long id=-1;
            for(int i=0;i<candidates.length();i++){JSONObject candidate=candidates.getJSONObject(i);boolean same=true;for(String key:new String[]{"name","numeric","apn","type","protocol","roaming_protocol","mvno_type","mvno_match_data"})if(!row.optString(key,"").equals(candidate.optString(key,"")))same=false;
                if(same){id=Long.parseLong(candidate.getString("_id"));break;}}
            if(id<0)throw new IOException("Preferred APN identity missing for sub "+sub);
            ContentValues values=new ContentValues();values.put("apn_id",id);getContentResolver().update(preferred(sub),values,null,null);
            JSONArray actual=rows(preferred(sub),null,null);if(actual.length()!=1||!actual.getJSONObject(0).getString("_id").equals(""+id))throw new IOException("Preferred APN restore failed");
        }
    }
    public void onCreate(Bundle state){super.onCreate(state);final TextView view=new TextView(this);view.setText("Checking APN catalogue...");setContentView(view);
        new Thread(new Runnable(){public void run(){String message;try{
            SubscriptionManager sm=SubscriptionManager.from(ApnCatalogTrial.this);List<SubscriptionInfo> sims=sm.getActiveSubscriptionInfoList();if(sims==null)sims=new ArrayList<SubscriptionInfo>();
            boolean refresh=getIntent().getBooleanExtra("refresh",false);
            JSONObject before=snapshot(sims);save(refresh?"apn-before-refresh.json":"apn-audit.json",before);
            if(refresh){
                if(((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall())throw new IOException("Finish calls first");
                // update_db reloads system defaults and preserves edited APNs. Never use restore.
                getContentResolver().delete(Uri.parse("content://telephony/carriers/update_db"),null,null);
                restorePreferred(before,sims);
            }
            JSONObject after=snapshot(sims);JSONArray all=rows(APNS,null,null),ro=rows(APNS,"mcc=?",new String[]{"226"});
            after.put("total_profiles",all.length()).put("romanian_profiles",ro).put("refresh",refresh);
            JSONArray custom=before.getJSONArray("custom"), retained=after.getJSONArray("custom");
            for(int i=0;i<custom.length();i++){JSONObject old=custom.getJSONObject(i);boolean found=false;for(int j=0;j<retained.length();j++){JSONObject now=retained.getJSONObject(j);if(old.getString("_id").equals(now.getString("_id"))&&old.optString("apn").equals(now.optString("apn"))&&old.optString("numeric").equals(now.optString("numeric")))found=true;}if(!found)throw new IOException("Custom APN changed during refresh");}
            save("apn-result.json",after);message="PASS APN profiles="+all.length()+" Romania="+ro.length()+" custom_preserved="+custom.length()+" refresh="+refresh;
            Log.i("LeoApnCatalog",message);
        }catch(Exception e){message="FAIL "+e;Log.e("LeoApnCatalog",message,e);}final String text=message;runOnUiThread(new Runnable(){public void run(){view.setText(text);}});}}).start();
    }
}
