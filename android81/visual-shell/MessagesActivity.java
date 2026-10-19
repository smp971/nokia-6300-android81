package ro.leo.visual;

import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
import java.text.Normalizer;

/** SMS reader/composer; no automatic transmission and no change of default SMS app. */
public final class MessagesActivity extends LeoActivity {
    private String current="";
    private boolean composing;
    private boolean preview;
    private boolean viewingMessages;
    private boolean loadingMessages;
    private final Handler refreshHandler=new Handler();
    private final Runnable refresh=new Runnable() { public void run() { if((viewingMessages || loadingMessages) && !composing && !preview)load(current); } };
    private final SharedPreferences.OnSharedPreferenceChangeListener sentChanged=new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
            if(!preview && (viewingMessages || loadingMessages)) { refreshHandler.removeCallbacks(refresh);refreshHandler.postDelayed(refresh,200); }
        }
    };
    private EditText recipient,body;
    private static final class Message { String address,body; long date,sent; int type,sub; }
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preview=getIntent().getBooleanExtra("preview",false);
        if(preview) { demo(); return; }
        String number=getIntent().getStringExtra("number"),thread=getIntent().getStringExtra("thread");
        if(number!=null) compose(number); else load(thread==null?"":thread);
    }
    protected void onResume() {
        super.onResume();getSharedPreferences("sms-outgoing",0).registerOnSharedPreferenceChangeListener(sentChanged);
        if(viewingMessages && !preview)load(current);
    }
    protected void onPause() {
        if(composing)saveDraft();refreshHandler.removeCallbacks(refresh);
        getSharedPreferences("sms-outgoing",0).unregisterOnSharedPreferenceChangeListener(sentChanged);super.onPause();
    }
    private String sim(int sub) {
        try { SubscriptionInfo info=SubscriptionManager.from(this).getActiveSubscriptionInfo(sub); return info==null?"SIM ?":"SIM "+(info.getSimSlotIndex()+1); }
        catch(SecurityException denied) { return "SIM ?"; }
    }
    private String key(String address,long sent,String body,int sub) { return PhoneNumberUtils.normalizeNumber(address)+"|"+sent+"|"+sub+"|"+body; }
    private void load(final String address) {
        if(preview) { demo(); return; }
        composing=false; viewingMessages=false; loadingMessages=true; current=address;
        begin("Mesaje"); root.addView(text("Se încarcă…",18),new LinearLayout.LayoutParams(-1,0,1)); footer("","","Înapoi");
        final LinearLayout loading=root;
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Message> messages=new ArrayList<Message>();final ArrayList<Message> systemMessages=new ArrayList<Message>();HashSet<String> seen=new HashSet<String>();
            try {
                try(Cursor c=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"address","body","date","date_sent","type","sub_id"},null,null,"date DESC")) {
                    while(c!=null && c.moveToNext() && messages.size()<1000) {
                        Message m=new Message(); m.address=c.getString(0); m.body=c.getString(1); m.date=c.getLong(2); m.sent=c.getLong(3); m.type=c.getInt(4); m.sub=c.getInt(5);
                        if(m.address==null || m.body==null || m.type==Telephony.Sms.MESSAGE_TYPE_DRAFT) continue;
                        messages.add(m);systemMessages.add(m);seen.add(key(m.address,m.sent,m.body,m.sub));
                    }
                } catch(RuntimeException unavailable) { android.util.Log.w("LeoSms","System SMS provider unavailable; using local archive"); }
                try(SmsStore store=new SmsStore(MessagesActivity.this); Cursor c=store.getReadableDatabase().query("inbox",null,null,null,null,null,"date DESC","1000")) {
                    while(c.moveToNext()) {
                        Message m=new Message(); m.address=c.getString(c.getColumnIndexOrThrow("address")); m.body=c.getString(c.getColumnIndexOrThrow("body"));
                        m.date=c.getLong(c.getColumnIndexOrThrow("date")); m.sent=c.getLong(c.getColumnIndexOrThrow("sent")); m.sub=c.getInt(c.getColumnIndexOrThrow("sub")); m.type=1;
                        if(seen.add(key(m.address,m.sent,m.body,m.sub))) messages.add(m);
                    }
                    SharedPreferences pending=getSharedPreferences("sms-outgoing",0);
                    String token=pending.getString("token","");
                    if(!token.isEmpty())store.outgoing(token,pending.getString("number",""),pending.getString("body",""),pending.getLong("date",System.currentTimeMillis()),pending.getInt("sub",-1));
                    HashSet<Message> matched=new HashSet<Message>();
                    try(Cursor out=store.getReadableDatabase().query("outgoing",null,null,null,null,null,"date DESC","1000")) {
                        while(out.moveToNext()) {
                            Message m=new Message();m.address=out.getString(out.getColumnIndexOrThrow("address"));m.body=out.getString(out.getColumnIndexOrThrow("body"));
                            m.date=out.getLong(out.getColumnIndexOrThrow("date"));m.sub=out.getInt(out.getColumnIndexOrThrow("sub"));m.type=out.getInt(out.getColumnIndexOrThrow("type"));
                            Message duplicate=null;
                            for(Message system:systemMessages)if(!matched.contains(system) && system.type!=Telephony.Sms.MESSAGE_TYPE_INBOX && system.sub==m.sub
                                    && Math.abs(system.date-m.date)<=10000 && PhoneNumberUtils.normalizeNumber(system.address).equals(PhoneNumberUtils.normalizeNumber(m.address)) && system.body.equals(m.body)) { duplicate=system;break; }
                            if(duplicate==null)messages.add(m);else { matched.add(duplicate);duplicate.type=m.type; }
                        }
                    }
                }
                Collections.sort(messages,new Comparator<Message>() { public int compare(Message a,Message b) { return Long.compare(b.date,a.date); } });
                ui(new Runnable() { public void run() { if(root==loading)display(address,messages); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { if(root==loading) { notice("Mesajele nu pot fi citite; verifică permisiunile");display(address,messages); } } }); }
        }});
    }
    private void display(final String address,ArrayList<Message> messages) {
        viewingMessages=true;loadingMessages=false;
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
        } },"Opțiuni",new Runnable() { public void run() {
            options("Mesaje",new String[]{"SMS nou","Caută în mesaje"},new Choice() { public void pick(int i) {
                if(i==0)compose("");else search("",messages);
            }});
        } });
        SharedPreferences outgoing=getSharedPreferences("sms-outgoing",0);
        String status=outgoing.getString("token","").isEmpty()?outgoing.getString("status",""):"SMS în așteptare · fără confirmare";
        if(!status.isEmpty()) root.addView(text(status,14),1);
    }
    private String state(Message m) {
        return m.type==Telephony.Sms.MESSAGE_TYPE_INBOX?"Primit":m.type==Telephony.Sms.MESSAGE_TYPE_SENT?"Trimis":m.type==Telephony.Sms.MESSAGE_TYPE_FAILED?"Eșuat":"În așteptare";
    }
    private String messageSim(Message m) { return preview?"SIM "+(m.sub+1):sim(m.sub); }
    private void detail(final String address,final ArrayList<Message> rows,final int index) {
        viewingMessages=false;loadingMessages=false;
        Message m=rows.get(index);
        begin("Detalii SMS");
        root.addView(text(state(m)+" · "+messageSim(m)+"\n"+new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(m.date)),14));
        ScrollView scroll=new ScrollView(this); scroll.addView(text(m.body,19));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() { messageOptions(address,rows,index); } };
        back=new Runnable() { public void run() { conversation(address,rows,index); } };
        footer("Opțiuni","","Înapoi"); scroll.setFocusable(true); scroll.requestFocus();
    }
    private void conversation(String address,ArrayList<Message> rows) { conversation(address,rows,rows.size()-1); }
    private void conversation(final String address,final ArrayList<Message> rows,int selected) {
        composing=false; viewingMessages=true; loadingMessages=false; current=address;
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
            messageOptions(address,rows,focus[0]);
        }};
        back=new Runnable() { public void run() { if(preview)finish();else load(""); } };
        footer("Opțiuni","Detalii","Înapoi");thread.requestFocus();thread.setSelection(focus[0]);
    }
    private void messageOptions(final String address,final ArrayList<Message> rows,final int index) {
        final boolean hasMessage=index>=0 && index<rows.size();
        options("Conversație",new String[]{"Răspunde","Detalii mesaj","Redirecționează","Copiază textul","Copiază numărul","Caută în conversație","Reîmprospătează"},new Choice() { public void pick(int i) {
            if(i==0)compose(address);
            else if(i==5)search(address,rows);
            else if(i==6)load(address);
            else if(hasMessage) {
                Message m=rows.get(index);
                if(i==1)detail(address,rows,index);
                else if(i==2)forward(m);
                else if(i==3 || i==4) {
                    ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(i==3?"SMS":"Număr SMS",i==3?m.body:m.address));
                    notice(i==3?"Text copiat":"Număr copiat");
                }
            } else notice("Niciun mesaj selectat");
        }});
    }
    private static String searchable(String text) {
        return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
    }
    private void search(final String address,final ArrayList<Message> messages) {
        viewingMessages=false;loadingMessages=false;
        LinearLayout fields=fields("Caută SMS");
        final EditText query=field(fields,"Text sau număr", "",InputType.TYPE_CLASS_TEXT);
        left=new Runnable() { public void run() {
            final String term=query.getText().toString().trim();
            if(term.isEmpty()) { notice("Introdu textul sau numărul căutat");return; }
            results(address,messages,term);
        }};
        back=new Runnable() { public void run() { restoreMessages(address,messages); } };
        footer("Caută","","Șterge");query.requestFocus();
    }
    private void restoreMessages(String address,ArrayList<Message> messages) {
        if(address.isEmpty())display("",messages);else conversation(address,messages);
    }
    private void results(final String address,final ArrayList<Message> messages,final String term) {
        begin("Caută SMS");root.addView(text("Se caută…",18),new LinearLayout.LayoutParams(-1,0,1));
        back=new Runnable() { public void run() { restoreMessages(address,messages); } };footer("","","Înapoi");
        final LinearLayout loading=root;
        worker.execute(new Runnable() { public void run() {
        final ArrayList<Message> matches=new ArrayList<Message>();
        String needle=searchable(term);
        for(Message m:messages) if(searchable(m.body).contains(needle) || searchable(m.address).contains(needle))matches.add(m);
        Collections.sort(matches,new Comparator<Message>() { public int compare(Message a,Message b) { return Long.compare(b.date,a.date); } });
        ui(new Runnable() { public void run() { if(root==loading)showResults(address,messages,matches); } });
        }});
    }
    private void showResults(final String address,final ArrayList<Message> messages,final ArrayList<Message> matches) {
        ArrayList<String> labels=new ArrayList<String>();
        for(Message m:matches)labels.add(m.address+" · "+messageSim(m)+"\n"+m.body);
        list("Rezultate · "+matches.size(),labels,0,new Choice() { public void pick(int i) {
            Message selected=matches.get(i);ArrayList<Message> thread=new ArrayList<Message>();
            for(Message m:messages)if(m.address.equals(selected.address))thread.add(m);
            Collections.sort(thread,new Comparator<Message>() { public int compare(Message a,Message b) { return Long.compare(a.date,b.date); } });
            detail(selected.address,thread,thread.indexOf(selected));
            if(!preview)UnreadState.readThread(getApplicationContext(),selected.address,selected.date);
        } },"Altă căutare",new Runnable() { public void run() { search(address,messages); } });
        back=new Runnable() { public void run() { restoreMessages(address,messages); } };
    }
    private void forward(final Message message) {
        if(message.body.length()>1000) { notice("Mesaj prea lung: limita editorului este 1.000 caractere. Poți copia textul integral.");return; }
        final Runnable open=new Runnable() { public void run() { editor("",message.body,true); } };
        SharedPreferences draft=getSharedPreferences("sms-draft",0);
        if(!preview && (!draft.getString("number","").isEmpty() || !draft.getString("body","").isEmpty())) {
            options("Există deja o ciornă",new String[]{"Păstrează ciorna","Înlocuiește cu redirecționarea"},new Choice() { public void pick(int i) { if(i==1)open.run(); } });
        } else open.run();
    }
    private void paintBubble(View view,boolean selected) {
        if(!(view instanceof LinearLayout))return;
        LinearLayout row=(LinearLayout)view;boolean received=Boolean.TRUE.equals(row.getTag());
        GradientDrawable background=new GradientDrawable();background.setCornerRadius(dp(9));
        background.setColor(received?palette.card:Appearance.mix(palette.gradient[1],palette.background,.15f));
        background.setStroke(dp(2),selected?palette.accent:Color.TRANSPARENT);
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
        if(preview)return;
        if(recipient!=null && body!=null) getSharedPreferences("sms-draft",0).edit().putString("number",recipient.getText().toString()).putString("body",body.getText().toString()).apply();
    }
    private void compose(String address) {
        if(preview) { notice("Demonstrație locală · fără trimitere SMS"); return; }
        SharedPreferences draft=getSharedPreferences("sms-draft",0);
        boolean reuse=address.isEmpty() || address.equals(draft.getString("number",""));
        editor(address.isEmpty()?draft.getString("number",""):address,reuse?draft.getString("body",""):"",false);
    }
    private void editor(String number,String initialBody,boolean forwarded) {
        viewingMessages=false;loadingMessages=false;
        LinearLayout fields=fields(forwarded?"Redirecționare":"SMS nou"); composing=true;
        recipient=field(fields,"Număr",number,InputType.TYPE_CLASS_PHONE);
        body=field(fields,"Mesaj",initialBody,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setMinLines(3); body.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        recipient.setNextFocusDownId(body.getId()); body.setNextFocusUpId(recipient.getId());
        left=new Runnable() { public void run() { prepare(); } };
        back=new Runnable() { public void run() {
            if(preview) { composing=false;demo();return; }
            saveDraft(); options("Ciornă SMS",new String[]{"Continuă","Păstrează și închide","Șterge ciorna"},new Choice() { public void pick(int i) {
                if(i==1) load(current); else if(i==2) { getSharedPreferences("sms-draft",0).edit().clear().apply(); load(current); }
            }});
        }};
        footer("Trimite","Următor","Șterge"); (number.isEmpty()?recipient:body).requestFocus();
    }
    private void prepare() {
        if(preview) { notice("Demonstrație locală · fără trimitere SMS");return; }
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
        if(preview) { notice("Demonstrație locală · fără trimitere SMS");return; }
        saveDraft(); String token=UUID.randomUUID().toString(); JSONArray states=new JSONArray();
        ArrayList<PendingIntent> callbacks=new ArrayList<PendingIntent>();
        for(int i=0;i<parts.size();i++) {
            states.put(0);
            Intent result=new Intent(this,SmsStatusReceiver.class).setData(Uri.parse("leo-sms:"+token+"/"+i)).putExtra("token",token).putExtra("part",i);
            callbacks.add(PendingIntent.getBroadcast(this,i,result,PendingIntent.FLAG_UPDATE_CURRENT));
        }
        long date=System.currentTimeMillis();int sub=manager.getSubscriptionId();
        try(SmsStore store=new SmsStore(this)) { store.outgoing(token,number,text,date,sub); }
        catch(RuntimeException unavailable) { notice("Arhiva SMS nu poate fi salvată; mesajul nu a fost trimis");return; }
        if(!getSharedPreferences("sms-outgoing",0).edit().putString("token",token).putString("parts",states.toString()).putString("number",number).putString("body",text).putLong("date",date).putInt("sub",sub).commit()) {
            try(SmsStore store=new SmsStore(this)) { store.completed(token,false); }
            catch(RuntimeException unavailable) { android.util.Log.w("LeoSms","Cannot update unsent archive entry"); }
            notice("Starea SMS nu poate fi salvată; mesajul nu a fost trimis");return;
        }
        try {
            manager.sendMultipartTextMessage(number,null,parts,callbacks,null);
            composing=false; load(number); notice("SMS în curs de trimitere");
        } catch(RuntimeException failure) {
            try(SmsStore store=new SmsStore(this)) { store.completed(token,false); }
            catch(RuntimeException unavailable) { notice("Starea SMS nu poate fi arhivată; verifică înainte de reîncercare");return; }
            getSharedPreferences("sms-outgoing",0).edit().clear().apply(); notice("Trimiterea a eșuat; ciorna este păstrată");
        }
    }
}
