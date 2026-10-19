package ro.leo.visual;

import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.*;
import android.text.InputType;
import android.text.TextUtils;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import java.util.*;
import java.text.SimpleDateFormat;

/** SMS reader/composer; no automatic transmission and no change of default SMS app. */
public final class MessagesActivity extends LeoActivity {
    private String current="";
    private boolean composing;
    private boolean preview;
    private EditText recipient,body;
    private static final class Message { String address,body; long date,sent; int type,sub; }
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preview=getIntent().getBooleanExtra("preview",false);
        if(preview) { demo(); return; }
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
        if(preview) { demo(); return; }
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
        HashSet<String> threads=new HashSet<String>();
        for(Message m:messages) {
            if(address.isEmpty()) {
                if(!threads.add(m.address)) continue;
                rows.add(m); labels.add(m.address+"\n"+m.body);
            } else if(m.address.equals(address)) {
                rows.add(m);
            }
        }
        if(!address.isEmpty()) {
            Collections.reverse(rows);
            conversation(address,rows);
            if(!preview) { long through=0;for(Message m:rows)through=Math.max(through,m.date);UnreadState.readThread(getApplicationContext(),address,through); }
            return;
        }
        list("Mesaje",labels,0,new Choice() { public void pick(int i) {
            load(rows.get(i).address);
        } },"Scrie",new Runnable() { public void run() { compose(address); } });
        SharedPreferences outgoing=getSharedPreferences("sms-outgoing",0);
        String status=outgoing.getString("token","").isEmpty()?outgoing.getString("status",""):"SMS în așteptare · fără confirmare";
        if(!status.isEmpty()) root.addView(text(status,14),1);
    }
    private String state(Message m) {
        return m.type==Telephony.Sms.MESSAGE_TYPE_INBOX?"Primit":m.type==Telephony.Sms.MESSAGE_TYPE_SENT?"Trimis":m.type==Telephony.Sms.MESSAGE_TYPE_FAILED?"Eșuat":"În așteptare";
    }
    private String messageSim(Message m) { return preview?"SIM "+(m.sub+1):sim(m.sub); }
    private void detail(final String address,final ArrayList<Message> rows,final int index) {
        Message m=rows.get(index);
        begin("Detalii SMS");
        root.addView(text(state(m)+" · "+messageSim(m)+"\n"+new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(m.date)),14));
        ScrollView scroll=new ScrollView(this); scroll.addView(text(m.body,19));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() { compose(address); } };
        back=new Runnable() { public void run() { conversation(address,rows,index); } };
        footer("Răspunde","","Înapoi"); scroll.setFocusable(true); scroll.requestFocus();
    }
    private void conversation(String address,ArrayList<Message> rows) { conversation(address,rows,rows.size()-1); }
    private void conversation(final String address,final ArrayList<Message> rows,int selected) {
        composing=false; current=address;
        begin(preview?"Conversație demo":address);
        SharedPreferences outgoing=getSharedPreferences("sms-outgoing",0);
        if(!preview && address.equals(outgoing.getString("number",""))) {
            String status=outgoing.getString("token","").isEmpty()?outgoing.getString("status",""):"SMS în așteptare · fără confirmare";
            if(!status.isEmpty())root.addView(text(status,14));
        }
        final ListView thread=new ListView(this);thread.setDivider(null);thread.setSelector(android.R.color.transparent);
        thread.setPadding(dp(5),0,dp(5),0);
        final int[] focus={Math.max(0,Math.min(selected,rows.size()-1))};
        final SimpleDateFormat day=new SimpleDateFormat("dd MMM yyyy",Locale.getDefault());
        final SimpleDateFormat clock=new SimpleDateFormat("HH:mm",Locale.getDefault());
        BaseAdapter adapter=new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int i) { return rows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i,View reuse,android.view.ViewGroup parent) {
                LinearLayout row;
                if(reuse instanceof LinearLayout) row=(LinearLayout)reuse;
                else {
                    row=new LinearLayout(MessagesActivity.this);row.setOrientation(1);row.setPadding(0,dp(4),0,dp(4));
                    TextView date=text("",13);date.setGravity(Gravity.CENTER);row.addView(date);
                    LinearLayout bubble=new LinearLayout(MessagesActivity.this);bubble.setOrientation(1);bubble.setPadding(dp(8),dp(5),dp(8),dp(5));
                    TextView body=text("",18);body.setPadding(0,0,0,dp(3));body.setMaxLines(6);body.setEllipsize(TextUtils.TruncateAt.END);bubble.addView(body);
                    TextView meta=text("",12);meta.setPadding(0,0,0,0);bubble.addView(meta);
                    row.addView(bubble,new LinearLayout.LayoutParams(-1,-2));
                }
                Message m=rows.get(i);boolean received=m.type==Telephony.Sms.MESSAGE_TYPE_INBOX;
                TextView date=(TextView)row.getChildAt(0);
                String today=day.format(new Date(m.date));
                date.setText(today);date.setVisibility(i==0 || !today.equals(day.format(new Date(rows.get(i-1).date)))?View.VISIBLE:View.GONE);
                LinearLayout bubble=(LinearLayout)row.getChildAt(1);
                LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)bubble.getLayoutParams();
                lp.leftMargin=received?0:dp(22);lp.rightMargin=received?dp(22):0;bubble.setLayoutParams(lp);
                ((TextView)bubble.getChildAt(0)).setText(m.body);
                TextView meta=(TextView)bubble.getChildAt(1);meta.setText(clock.format(new Date(m.date))+" · "+messageSim(m)+" · "+state(m));
                meta.setTextColor(m.type==Telephony.Sms.MESSAGE_TYPE_FAILED?0xffffb8ad:0xffe4d6f1);
                row.setTag(received);paintBubble(row,i==focus[0]);return row;
            }
        };
        thread.setAdapter(adapter);
        thread.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p,View v,int i,long id) {
                int old=focus[0];focus[0]=i;
                if(old!=i)paintBubble(thread.getChildAt(old-thread.getFirstVisiblePosition()),false);
                paintBubble(v,true);
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        thread.setOnItemClickListener(new AdapterView.OnItemClickListener() { public void onItemClick(AdapterView<?> p,View v,int i,long id) { detail(address,rows,i); } });
        if(rows.isEmpty())root.addView(text("Niciun mesaj",18),new LinearLayout.LayoutParams(-1,0,1));
        else root.addView(thread,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() {
            options("Conversație",new String[]{"Răspunde","Detalii mesaj","Reîmprospătează"},new Choice() { public void pick(int i) {
                if(i==0)compose(address);else if(i==1 && !rows.isEmpty())detail(address,rows,focus[0]);else if(i==2)load(address);
            }});
        }};
        back=new Runnable() { public void run() { if(preview)finish();else load(""); } };
        footer("Opțiuni","Detalii","Înapoi");thread.requestFocus();thread.setSelection(focus[0]);
    }
    private void paintBubble(View view,boolean selected) {
        if(!(view instanceof LinearLayout))return;
        LinearLayout row=(LinearLayout)view;boolean received=Boolean.TRUE.equals(row.getTag());
        GradientDrawable background=new GradientDrawable();background.setCornerRadius(dp(9));
        background.setColor(received?0xff40364e:0xff633b87);
        background.setStroke(dp(2),selected?0xfff0d9ff:Color.TRANSPARENT);
        row.getChildAt(1).setBackground(background);
    }
    private void demo() {
        ArrayList<Message> messages=new ArrayList<Message>();
        String[] bodies={"Salut! Ne vedem mâine?","Da, la ora zece. 🙂","Perfect. Îți scriu când ajung."};
        for(int i=0;i<bodies.length;i++) {
            Message m=new Message();m.address="Conversație demo";m.body=bodies[i];m.type=i==1?2:1;m.sub=i==1?1:0;
            m.date=System.currentTimeMillis()-(2-i)*120000;messages.add(m);
        }
        conversation("Conversație demo",messages);
    }
    private void saveDraft() {
        if(recipient!=null && body!=null) getSharedPreferences("sms-draft",0).edit().putString("number",recipient.getText().toString()).putString("body",body.getText().toString()).apply();
    }
    private void compose(String address) {
        if(preview) { notice("Demonstrație locală · fără trimitere SMS"); return; }
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
