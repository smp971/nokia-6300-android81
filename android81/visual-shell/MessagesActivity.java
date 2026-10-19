package ro.leo.visual;

import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.*;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import java.util.*;
import java.text.SimpleDateFormat;

/** SMS reader/composer; no automatic transmission and no change of default SMS app. */
public final class MessagesActivity extends LeoActivity {
    private String current="";
    private boolean composing;
    private EditText recipient,body;
    private static final class Message { String address,body; long date,sent; int type,sub; }
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String number=getIntent().getStringExtra("number"),thread=getIntent().getStringExtra("thread");
        if(number!=null) compose(number); else load(thread==null?"":thread);
    }
    protected void onPause() { if(composing) saveDraft(); super.onPause(); }
    private String sim(int sub) {
        try { SubscriptionInfo info=SubscriptionManager.from(this).getActiveSubscriptionInfo(sub); return info==null?"SIM ?":"SIM "+(info.getSimSlotIndex()+1); }
        catch(SecurityException denied) { return "SIM ?"; }
    }
    private String key(String address,long sent,String body,int sub) { return PhoneNumberUtils.normalizeNumber(address)+"|"+sent+"|"+sub+"|"+body; }
    private void load(final String address) {
        composing=false; current=address;
        begin("Mesaje"); root.addView(text("Se încarcă…",18),new LinearLayout.LayoutParams(-1,0,1)); footer("","","Înapoi");
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Message> messages=new ArrayList<Message>(); HashSet<String> seen=new HashSet<String>();
            try {
                try(Cursor c=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"address","body","date","date_sent","type","sub_id"},null,null,"date DESC")) {
                    while(c!=null && c.moveToNext() && messages.size()<1000) {
                        Message m=new Message(); m.address=c.getString(0); m.body=c.getString(1); m.date=c.getLong(2); m.sent=c.getLong(3); m.type=c.getInt(4); m.sub=c.getInt(5);
                        if(m.address==null || m.body==null || m.type==Telephony.Sms.MESSAGE_TYPE_DRAFT) continue;
                        messages.add(m); seen.add(key(m.address,m.sent,m.body,m.sub));
                    }
                }
                try(SmsStore store=new SmsStore(MessagesActivity.this); Cursor c=store.getReadableDatabase().query("inbox",null,null,null,null,null,"date DESC","1000")) {
                    while(c.moveToNext()) {
                        Message m=new Message(); m.address=c.getString(c.getColumnIndexOrThrow("address")); m.body=c.getString(c.getColumnIndexOrThrow("body"));
                        m.date=c.getLong(c.getColumnIndexOrThrow("date")); m.sent=c.getLong(c.getColumnIndexOrThrow("sent")); m.sub=c.getInt(c.getColumnIndexOrThrow("sub")); m.type=1;
                        if(seen.add(key(m.address,m.sent,m.body,m.sub))) messages.add(m);
                    }
                }
                Collections.sort(messages,new Comparator<Message>() { public int compare(Message a,Message b) { return Long.compare(b.date,a.date); } });
                ui(new Runnable() { public void run() { display(address,messages); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { notice("Mesajele nu pot fi citite; verifică permisiunile"); display(address,messages); } }); }
        }});
    }
    private void display(final String address,ArrayList<Message> messages) {
        final ArrayList<Message> rows=new ArrayList<Message>(); ArrayList<String> labels=new ArrayList<String>();
        HashSet<String> threads=new HashSet<String>(); SimpleDateFormat date=new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault());
        for(Message m:messages) {
            if(address.isEmpty()) {
                if(!threads.add(m.address)) continue;
                rows.add(m); labels.add(m.address+"\n"+m.body);
            } else if(m.address.equals(address)) {
                String status=m.type==Telephony.Sms.MESSAGE_TYPE_INBOX?"Primit":m.type==Telephony.Sms.MESSAGE_TYPE_SENT?"Eu · trimis":m.type==Telephony.Sms.MESSAGE_TYPE_FAILED?"Eu · eșuat":"Eu · în așteptare";
                rows.add(m); labels.add(status+" · "+sim(m.sub)+" · "+date.format(new Date(m.date))+"\n"+m.body);
            }
        }
        list(address.isEmpty()?"Mesaje":address,labels,0,new Choice() { public void pick(int i) {
            if(address.isEmpty()) load(rows.get(i).address);
            else {
                begin("SMS · "+sim(rows.get(i).sub)); ScrollView scroll=new ScrollView(MessagesActivity.this); scroll.addView(text(rows.get(i).body,19));
                root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); left=new Runnable() { public void run() { compose(address); } };
                back=new Runnable() { public void run() { load(address); } }; footer("Răspunde","","Înapoi");
            }
        } },"Scrie",new Runnable() { public void run() { compose(address); } });
        SharedPreferences outgoing=getSharedPreferences("sms-outgoing",0);
        String status=outgoing.getString("token","").isEmpty()?outgoing.getString("status",""):"SMS în așteptare · fără confirmare";
        if(!status.isEmpty()) root.addView(text(status,14),1);
        if(!address.isEmpty()) back=new Runnable() { public void run() { load(""); } };
    }
    private void saveDraft() {
        if(recipient!=null && body!=null) getSharedPreferences("sms-draft",0).edit().putString("number",recipient.getText().toString()).putString("body",body.getText().toString()).apply();
    }
    private void compose(String address) {
        SharedPreferences draft=getSharedPreferences("sms-draft",0);
        boolean reuse=address.isEmpty() || address.equals(draft.getString("number",""));
        LinearLayout fields=fields("SMS nou"); composing=true;
        recipient=field(fields,"Număr",address.isEmpty()?draft.getString("number",""):address,InputType.TYPE_CLASS_PHONE);
        body=field(fields,"Mesaj",reuse?draft.getString("body",""):"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setMinLines(3); body.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        recipient.setNextFocusDownId(body.getId()); body.setNextFocusUpId(recipient.getId());
        left=new Runnable() { public void run() { prepare(); } };
        back=new Runnable() { public void run() {
            saveDraft(); options("Ciornă SMS",new String[]{"Continuă","Păstrează și închide","Șterge ciorna"},new Choice() { public void pick(int i) {
                if(i==1) load(current); else if(i==2) { getSharedPreferences("sms-draft",0).edit().clear().apply(); load(current); }
            }});
        }};
        footer("Trimite","Următor","Șterge"); (address.isEmpty()?recipient:body).requestFocus();
    }
    private void prepare() {
        final String number=PhoneNumberUtils.normalizeNumber(recipient.getText().toString()),text=body.getText().toString();
        if(number.isEmpty() || text.trim().isEmpty()) { notice("Completează numărul și mesajul"); return; }
        if(!number.matches("\\+?[0-9]{3,15}") || number.replace("+","").matches("0+")) { notice("Număr SMS invalid"); return; }
        if(!getSharedPreferences("sms-outgoing",0).getString("token","").isEmpty()) { notice("Există o trimitere în așteptare; verifică starea înainte de repetare"); return; }
        try {
            final List<SubscriptionInfo> sims=SubscriptionManager.from(this).getActiveSubscriptionInfoList();
            if(sims==null || sims.isEmpty()) { notice("Niciun SIM activ"); return; }
            String[] labels=new String[sims.size()];
            for(int i=0;i<labels.length;i++) labels[i]="SIM "+(sims.get(i).getSimSlotIndex()+1)+" · "+sims.get(i).getCarrierName();
            options("Alege SIM pentru SMS",labels,new Choice() { public void pick(final int i) {
                final SmsManager manager=SmsManager.getSmsManagerForSubscriptionId(sims.get(i).getSubscriptionId());
                final ArrayList<String> parts=manager.divideMessage(text);
                options(parts.size()+" SMS → "+number,new String[]{"Anulează","Trimite SMS"},new Choice() { public void pick(int confirmed) {
                    if(confirmed==1) send(manager,number,text,parts);
                }});
            }});
        } catch(RuntimeException failure) { notice("Serviciul SMS nu este disponibil"); }
    }
    private void send(SmsManager manager,String number,String text,ArrayList<String> parts) {
        saveDraft(); String token=UUID.randomUUID().toString(); JSONArray states=new JSONArray();
        ArrayList<PendingIntent> callbacks=new ArrayList<PendingIntent>();
        for(int i=0;i<parts.size();i++) {
            states.put(0);
            Intent result=new Intent(this,SmsStatusReceiver.class).setData(Uri.parse("leo-sms:"+token+"/"+i)).putExtra("token",token).putExtra("part",i);
            callbacks.add(PendingIntent.getBroadcast(this,i,result,PendingIntent.FLAG_UPDATE_CURRENT));
        }
        getSharedPreferences("sms-outgoing",0).edit().putString("token",token).putString("parts",states.toString()).putString("number",number).putString("body",text).commit();
        try {
            manager.sendMultipartTextMessage(number,null,parts,callbacks,null);
            composing=false; load(number); notice("SMS în curs de trimitere");
        } catch(RuntimeException failure) {
            getSharedPreferences("sms-outgoing",0).edit().clear().apply(); notice("Trimiterea a eșuat; ciorna este păstrată");
        }
    }
}
