package ro.leo.visual;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.telecom.*;
import android.telephony.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Private admin-only phone API. Prepared operations are session-bound, one-use and short-lived. */
final class CompanionCommunication {
    interface Transport {void call(Context c,String number,PhoneAccountHandle account)throws Exception;String sms(Context c,SmsManager manager,String number,String body)throws Exception;}
    static final Transport ANDROID=new Transport(){
        public void call(Context c,String n,PhoneAccountHandle account){Bundle extras=new Bundle();extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,account);((TelecomManager)c.getSystemService(Context.TELECOM_SERVICE)).placeCall(Uri.fromParts("tel",n,null),extras);}
        public String sms(Context c,SmsManager manager,String n,String body)throws Exception{return SmsSender.send(c,manager,n,body,null);}
    };
    private final Context context;private final Transport transport;
    private final LinkedHashMap<String,Pending> pending=new LinkedHashMap<String,Pending>();
    static final class Pending {String session,kind,number,body;int slot,sub;String account;long until;}
    CompanionCommunication(Context c){this(c,ANDROID);}
    CompanionCommunication(Context c,Transport transport){context=c;this.transport=transport;}
    static void number(String value){SmsSender.number(value);}
    static SubscriptionInfo sim(Context c,int slot){if(slot<0||slot>1)throw new IllegalArgumentException("SIM invalid");List<SubscriptionInfo> sims=SubscriptionManager.from(c).getActiveSubscriptionInfoList();if(sims!=null)for(SubscriptionInfo sim:sims)if(sim.getSimSlotIndex()==slot)return sim;throw new IllegalArgumentException("SIM indisponibil");}
    static PhoneAccountHandle account(Context c,SubscriptionInfo sim){TelecomManager telecom=(TelecomManager)c.getSystemService(Context.TELECOM_SERVICE);TelephonyManager phone=(TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);for(PhoneAccountHandle h:telecom.getCallCapablePhoneAccounts()){TelephonyManager selected=phone.createForPhoneAccountHandle(h);if(selected!=null&&Integer.toString(sim.getSubscriptionId()).equals(selected.getNetworkSpecifier()))return h;}throw new IllegalArgumentException("Contul SIM pentru apel nu este disponibil");}
    synchronized JSONObject prepare(String session,JSONObject values)throws Exception {
        CompanionModel.exact(values,"kind","number","slot","body");String kind=CompanionServer.string(values,"kind"),number=CompanionServer.string(values,"number"),body=CompanionServer.string(values,"body");
        number(number);if(!kind.equals("call")&&!kind.equals("sms"))throw new IllegalArgumentException("Unknown operation");
        if(kind.equals("call")&&!body.isEmpty()||kind.equals("sms")&&(body.trim().isEmpty()||body.length()>1600))throw new IllegalArgumentException("Invalid message");
        SubscriptionInfo sim=sim(context,CompanionModel.integer(values,"slot"));
        Pending p=new Pending();p.session=session;p.kind=kind;p.number=number;p.body=body;p.slot=sim.getSimSlotIndex();p.sub=sim.getSubscriptionId();p.until=SystemClock.elapsedRealtime()+60000;
        int segments=0;if(kind.equals("call")){p.account=account(context,sim).getId();if(((TelecomManager)context.getSystemService(Context.TELECOM_SERVICE)).isInCall())throw new IllegalStateException("Un apel este deja activ");}
        else {segments=SmsManager.getSmsManagerForSubscriptionId(p.sub).divideMessage(body).size();if(segments>10)throw new IllegalArgumentException("Maximum 10 SMS");if(!context.getSharedPreferences("sms-outgoing",0).getString("token","").isEmpty())throw new IllegalStateException("Un SMS este deja în așteptare");}
        Iterator<Pending> old=pending.values().iterator();while(old.hasNext())if(old.next().until<SystemClock.elapsedRealtime())old.remove();while(pending.size()>=8)pending.remove(pending.keySet().iterator().next());
        String ticket=CompanionServer.token();pending.put(ticket,p);
        return new JSONObject().put("ticket",ticket).put("kind",kind).put("number",number).put("slot",p.slot).put("segments",segments).put("body",body).put("expiresSeconds",60);
    }
    synchronized JSONObject confirm(String session,JSONObject data)throws Exception {
        CompanionModel.exact(data,"ticket");String ticket=CompanionServer.string(data,"ticket");Pending p=pending.get(ticket);
        if(p==null||!p.session.equals(session)||p.until<SystemClock.elapsedRealtime())throw new IllegalStateException("Confirmarea a expirat sau a fost deja folosită");
        pending.remove(ticket); // Consumed BEFORE platform side effects; network retries cannot repeat a send.
        SubscriptionInfo sim=sim(context,p.slot);if(sim.getSubscriptionId()!=p.sub)throw new IllegalStateException("SIM schimbat; pregătește din nou operația");
        if(p.kind.equals("call")){
            PhoneAccountHandle account=account(context,sim);if(!account.getId().equals(p.account))throw new IllegalStateException("Cont SIM schimbat");
            if(((TelecomManager)context.getSystemService(Context.TELECOM_SERVICE)).isInCall())throw new IllegalStateException("Un apel este deja activ");
            transport.call(context,p.number,account);return new JSONObject().put("accepted",true).put("state","requested");
        }
        transport.sms(context,SmsManager.getSmsManagerForSubscriptionId(p.sub),p.number,p.body);return new JSONObject().put("accepted",true).put("state","queued");
    }
    static JSONObject state(final Context c)throws Exception {
        final JSONObject[] result={null};final Exception[] error={null};final CountDownLatch done=new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){try{
            JSONObject out=new JSONObject();JSONArray sims=new JSONArray(),calls=new JSONArray();List<SubscriptionInfo> all=SubscriptionManager.from(c).getActiveSubscriptionInfoList();
            if(all!=null)for(SubscriptionInfo sim:all)sims.put(new JSONObject().put("slot",sim.getSimSlotIndex()).put("label","SIM "+(sim.getSimSlotIndex()+1)+" · "+sim.getCarrierName()));
            for(Call call:LeoInCallService.calls())if(call.getState()!=Call.STATE_DISCONNECTED)calls.put(new JSONObject().put("id",Integer.toHexString(System.identityHashCode(call))).put("number",LeoInCallService.number(call)).put("name",LeoInCallService.identity(call)).put("state",call.getState()).put("label",CallPresentation.state(call.getState())));
            out.put("sims",sims).put("calls",calls).put("defaultDialer",c.getPackageName().equals(((TelecomManager)c.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage()));
            out.put("smsPending",!c.getSharedPreferences("sms-outgoing",0).getString("token","").isEmpty()).put("smsStatus",c.getSharedPreferences("sms-outgoing",0).getString("status",""));result[0]=out;
        }catch(Exception e){error[0]=e;}finally{done.countDown();}}});
        if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Telefon ocupat");if(error[0]!=null)throw error[0];return result[0];
    }
    static JSONObject callAction(final JSONObject data)throws Exception {
        CompanionModel.exact(data,"id","action","value");final String id=CompanionServer.string(data,"id"),action=CompanionServer.string(data,"action"),value=CompanionServer.string(data,"value");
        final CountDownLatch done=new CountDownLatch(1);final RuntimeException[] error={null};
        new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){try{
            Call selected=null;for(Call call:LeoInCallService.calls())if(id.equals(Integer.toHexString(System.identityHashCode(call))))selected=call;
            if(selected==null||selected.getState()==Call.STATE_DISCONNECTED)throw new IllegalStateException("Apelul nu mai există");
            LeoInCallService service=LeoInCallService.active;if(service==null)throw new IllegalStateException("Telefon indisponibil");
            if(action.equals("answer")&&selected.getState()==Call.STATE_RINGING)selected.answer(0);
            else if(action.equals("hangup"))selected.disconnect();
            else if(action.equals("hold")&&selected.getState()==Call.STATE_ACTIVE)selected.hold();
            else if(action.equals("resume")&&selected.getState()==Call.STATE_HOLDING)selected.unhold();
            else if(action.equals("mute")&&(value.equals("true")||value.equals("false")))service.setMuted(value.equals("true"));
            else if(action.equals("speaker")&&(value.equals("true")||value.equals("false")))service.setAudioRoute(value.equals("true")?CallAudioState.ROUTE_SPEAKER:CallAudioState.ROUTE_WIRED_OR_EARPIECE);
            else if(action.equals("dtmf")&&value.matches("[0-9*#]")){final Call target=selected;target.playDtmfTone(value.charAt(0));new Handler().postDelayed(new Runnable(){public void run(){target.stopDtmfTone();}},180);}
            else throw new IllegalArgumentException("Acțiune indisponibilă");
        }catch(RuntimeException e){error[0]=e;}finally{done.countDown();}}});
        if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Telefon ocupat");if(error[0]!=null)throw error[0];return new JSONObject().put("accepted",true);
    }
    static JSONArray contacts(Context c)throws Exception {
        JSONArray rows=new JSONArray();try(Cursor cursor=c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")){
            while(cursor!=null&&cursor.moveToNext()&&rows.length()<300)rows.put(new JSONObject().put("name",cursor.getString(0)).put("number",cursor.getString(1)));
        }return rows;
    }
    static JSONArray history(Context c)throws Exception {
        JSONArray rows=new JSONArray();try(Cursor cursor=c.getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.TYPE},null,null,CallLog.Calls.DATE+" DESC LIMIT 100")){
            while(cursor!=null&&cursor.moveToNext()&&rows.length()<100)rows.put(new JSONObject().put("number",cursor.getString(0)).put("name",cursor.getString(1)).put("date",cursor.getLong(2)).put("duration",cursor.getLong(3)).put("type",cursor.getInt(4)));
        }return rows;
    }
    static JSONArray messages(Context c)throws Exception {
        ArrayList<JSONObject> rows=new ArrayList<JSONObject>();Set<String> seen=new HashSet<String>();
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"address","body","date","date_sent","type","sub_id"},null,null,"date DESC LIMIT 200")){
            while(cursor!=null&&cursor.moveToNext()&&rows.size()<200)add(rows,seen,cursor.getString(0),cursor.getString(1),cursor.getLong(2),cursor.getLong(3),cursor.getInt(4),cursor.getInt(5));
        }catch(RuntimeException unavailable){/* Native private archive is authoritative fallback on this ROM. */}
        try(SmsStore store=new SmsStore(c)){
            try(Cursor cursor=store.getReadableDatabase().query("inbox",new String[]{"address","body","date","sent","sub"},null,null,null,null,"date DESC","200")){while(cursor.moveToNext())add(rows,seen,cursor.getString(0),cursor.getString(1),cursor.getLong(2),cursor.getLong(3),1,cursor.getInt(4));}
            try(Cursor cursor=store.getReadableDatabase().query("outgoing",new String[]{"address","body","date","type","sub"},null,null,null,null,"date DESC","200")){while(cursor.moveToNext())add(rows,seen,cursor.getString(0),cursor.getString(1),cursor.getLong(2),0,cursor.getInt(3),cursor.getInt(4));}
        }
        Collections.sort(rows,new Comparator<JSONObject>(){public int compare(JSONObject a,JSONObject b){return Long.compare(b.optLong("date"),a.optLong("date"));}});
        JSONArray result=new JSONArray();for(int i=0;i<Math.min(200,rows.size());i++)result.put(rows.get(i));return result;
    }
    private static void add(List<JSONObject> rows,Set<String> seen,String address,String body,long date,long sent,int type,int sub)throws Exception {
        if(address==null||body==null||type==3)return;String key=address+"\n"+(sent>0?sent:date)+"\n"+type+"\n"+sub+"\n"+body;
        if(seen.add(key))rows.add(new JSONObject().put("number",address).put("body",body.length()>4096?body.substring(0,4096):body).put("date",date).put("type",type).put("sub",sub));
    }
    synchronized void clear(){pending.clear();}
}
