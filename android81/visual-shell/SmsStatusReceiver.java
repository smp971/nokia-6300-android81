package ro.leo.visual;
import ro.leo.i18n.L;

import android.app.Activity;
import android.content.*;
import org.json.*;

public final class SmsStatusReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent) {
        SharedPreferences saved=context.getSharedPreferences("sms-outgoing",0);
        String token=intent.getStringExtra("token");
        if(token==null || !token.equals(saved.getString("token",""))) return;
        try {
            JSONArray states=new JSONArray(saved.getString("parts","[]")); int part=intent.getIntExtra("part",-1);
            if(part<0 || part>=states.length() || states.getInt(part)!=0) return;
            states.put(part,getResultCode()==Activity.RESULT_OK?1:-1);
            boolean complete=true,success=true;
            for(int i=0;i<states.length();i++) { if(states.getInt(i)==0) complete=false; if(states.getInt(i)!=1) success=false; }
            if(!complete) { saved.edit().putString("parts",states.toString()).commit(); return; }
            // Persist the result before removing the sole temporary copy of the body.
            try(SmsStore store=new SmsStore(context)) {
                store.outgoing(token,saved.getString("number",""),saved.getString("body",""),saved.getLong("date",System.currentTimeMillis()),saved.getInt("sub",-1));
                store.completed(token,success);
            }
            if(success)try(DraftStore drafts=new DraftStore(context)){drafts.sent(saved.getString("draft-id",null),saved.getString("number",""),saved.getString("body",""));}
            SharedPreferences draft=context.getSharedPreferences("sms-draft",0);
            if(success && draft.getString("number","").equals(saved.getString("number","")) && draft.getString("body","").equals(saved.getString("body",""))) draft.edit().clear().putBoolean("migrated-multiple",true).commit();
            String number=saved.getString("number",""); saved.edit().clear().putString("status",success?L.t("SMS trimis către rețea · livrare neconfirmată"):L.t("Trimitere eșuată sau parțială; verifică înainte de reîncercare")).commit();
            SmsReceiver.notify(context,success?L.t("SMS trimis către rețea"):L.t("SMS netrimis complet; ciornă păstrată"),number);
        } catch(JSONException invalid) { }
        catch(RuntimeException unavailable) {
            // Keep temporary state for recovery instead of discarding the message.
            SmsReceiver.notify(context,L.t("Confirmare SMS: arhivă indisponibilă"),saved.getString("number",""));
        }
    }
}
