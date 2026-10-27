package ro.leo.visual;
import ro.leo.i18n.L;

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
    private String current="",draftId;
    private int folder,organization;
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
    private EditText recipient,body;private String replyAddress="";
    private static final class Message { String address,body;ContactIdentity identity; long date,sent; int type,sub,delivery=-1; }
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
        begin(L.t("Mesaje")); root.addView(text(L.t("Se încarcă…"),18),new LinearLayout.LayoutParams(-1,0,1)); footer("","",L.t("Înapoi"));
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
                            m.date=out.getLong(out.getColumnIndexOrThrow("date"));m.sub=out.getInt(out.getColumnIndexOrThrow("sub"));m.type=out.getInt(out.getColumnIndexOrThrow("type"));m.delivery=out.getInt(out.getColumnIndexOrThrow("delivery"));
                            Message duplicate=null;
                            for(Message system:systemMessages)if(!matched.contains(system) && system.type!=Telephony.Sms.MESSAGE_TYPE_INBOX && system.sub==m.sub
                                    && Math.abs(system.date-m.date)<=10000 && PhoneNumberUtils.normalizeNumber(system.address).equals(PhoneNumberUtils.normalizeNumber(m.address)) && system.body.equals(m.body)) { duplicate=system;break; }
                            if(duplicate==null)messages.add(m);else { matched.add(duplicate);duplicate.type=m.type;duplicate.delivery=m.delivery; }
                        }
                    }
                }
                Collections.sort(messages,new Comparator<Message>() { public int compare(Message a,Message b) { return Long.compare(b.date,a.date); } });
                HashMap<String,ContactIdentity> identities=new HashMap<String,ContactIdentity>();for(Message m:messages){ContactIdentity identity=identities.get(m.address);if(identity==null&&identities.size()<128){identity=ContactIdentity.find(MessagesActivity.this,m.address);identities.put(m.address,identity);}m.identity=identity;}
                ui(new Runnable() { public void run() { if(root==loading)display(address,messages); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { if(root==loading) { notice(L.t("Mesajele nu pot fi citite; verifică permisiunile"));display(address,messages); } } }); }
        }});
    }
    private void display(final String address,ArrayList<Message> messages) {
        viewingMessages=true;loadingMessages=false;
        final ArrayList<Message> rows=new ArrayList<Message>(); ArrayList<String> labels=new ArrayList<String>();
        final HashSet<String> unread=new HashSet<String>();if(!preview)try(SmsStore s=new SmsStore(this);Cursor c=s.getReadableDatabase().rawQuery("SELECT DISTINCT address FROM inbox WHERE unread=1",null)){while(c.moveToNext())unread.add(c.getString(0));}catch(RuntimeException ignored){}HashSet<String> threads=new HashSet<String>();
        Collections.sort(messages,new Comparator<Message>(){public int compare(Message a,Message b){if(address.isEmpty()){boolean ap=ConversationPrefs.has(MessagesActivity.this,"pinned",a.address),bp=ConversationPrefs.has(MessagesActivity.this,"pinned",b.address);if(ap!=bp)return ap?-1:1;}return Long.compare(b.date,a.date);}});
        for(Message m:messages) {
            if(address.isEmpty()) {
                if(!SmsFolders.accepts(folder,m.type)||!ConversationPrefs.visible(this,organization,m.address))continue;
                if(!threads.add(m.address)) continue;
                rows.add(m); labels.add((ConversationPrefs.has(this,"pinned",m.address)?"★ ":"")+(unread.contains(m.address)?"● ":"")+(m.identity==null?m.address:m.identity.name)+" · "+messageSim(m)+"\n"+(m.type==Telephony.Sms.MESSAGE_TYPE_INBOX?"":state(m)+" · ")+m.body);
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
        list(folder==0?L.t("Mesaje"):L.t("Mesaje · ")+SmsFolders.LABELS[folder],labels,0,new Choice() { public void pick(int i) {
            load(rows.get(i).address);
        } },L.t("Opțiuni"),new Runnable() { public void run() {
            options(L.t("Mesaje"),new String[]{L.t("SMS nou"),L.t("Caută în toate mesajele"),L.t("Ciorne salvate"),L.t("Previzualizare notificări"),L.t("Șabloane SMS"),L.t("Dosare mesaje"),L.t("Primite / Arhivate / Filtrate")},new Choice() { public void pick(int i) {
                if(i==6){options(L.t("Conversații"),new String[]{L.t("Principale"),L.t("Arhivate"),L.t("Expeditori filtrați")},new Choice(){public void pick(int n){organization=n;load("");}});}else if(i==5){options(L.t("Dosare"),SmsFolders.LABELS,new Choice(){public void pick(int at){folder=at;load("");}});}else if(i==4){replyAddress="";templates(false);}else if(i==0)compose("");else if(i==1)search("",messages);else if(i==2)drafts();else {final SharedPreferences p=getSharedPreferences("sms-options",0);options(L.t("Text în notificări"),new String[]{L.t("Ascunde textul"),L.t("Arată o previzualizare")},new Choice(){public void pick(int mode){p.edit().putBoolean("preview",mode==1).apply();notice(L.t("Preferință salvată pentru mesajele noi"));}});}
            }});
        } });
        TextView folders=text((organization==1?L.t("Arhivate"):organization==2?L.t("Expeditori filtrați"):L.t("Principale"))+" · ‹ "+SmsFolders.LABELS[folder]+L.t(" › · stânga / dreapta"),12);folders.setGravity(Gravity.CENTER);folders.setTextColor(palette.accent);root.addView(folders,1);
        SharedPreferences outgoing=getSharedPreferences("sms-outgoing",0);
        String status=outgoing.getString("token","").isEmpty()?outgoing.getString("status",""):L.t("SMS în așteptare · fără confirmare");
        if(!status.isEmpty()) root.addView(text(status,14),1);
    }
    private String state(Message m) {
        return m.type==Telephony.Sms.MESSAGE_TYPE_INBOX?L.t("Primit"):m.type==Telephony.Sms.MESSAGE_TYPE_SENT?(m.delivery==1?L.t("Livrat · raport operator"):m.delivery==2?L.t("Livrare eșuată"):m.delivery==3?L.t("Raport · livrare neconfirmată"):m.delivery==0?L.t("Trimis · așteaptă raport"):L.t("Trimis către rețea")):m.type==Telephony.Sms.MESSAGE_TYPE_FAILED?L.t("Eșuat"):L.t("În așteptare");
    }
    private String messageSim(Message m) { return preview?"SIM "+(m.sub+1):sim(m.sub); }
    static String statusMark(int type,int delivery){return type==Telephony.Sms.MESSAGE_TYPE_INBOX?"↓":type==Telephony.Sms.MESSAGE_TYPE_FAILED||delivery==2?"!":type==Telephony.Sms.MESSAGE_TYPE_SENT?(delivery==1?"✓✓":"✓"):"…";}
    private void detail(final String address,final ArrayList<Message> rows,final int index) {
        viewingMessages=false;loadingMessages=false;
        Message m=rows.get(index);
        begin(L.t("Detalii SMS"));
        root.addView(text(state(m)+" · "+messageSim(m)+"\n"+new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(m.date)),14));
        ScrollView scroll=new ScrollView(this); scroll.addView(text(m.body,19));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() { messageOptions(address,rows,index); } };
        back=new Runnable() { public void run() { conversation(address,rows,index); } };
        footer(L.t("Opțiuni"),"",L.t("Înapoi")); scroll.setFocusable(true); scroll.requestFocus();
    }
    private void conversation(String address,ArrayList<Message> rows) { conversation(address,rows,rows.size()-1); }
    private void conversation(final String address,final ArrayList<Message> rows,int selected) {
        composing=false; viewingMessages=true; loadingMessages=false; current=address;
        begin(preview?L.t("Conversație demo"):address);
        SharedPreferences outgoing=getSharedPreferences("sms-outgoing",0);
        if(!preview && address.equals(outgoing.getString("number",""))) {
            String status=outgoing.getString("token","").isEmpty()?outgoing.getString("status",""):L.t("SMS în așteptare · fără confirmare");
            if(!status.isEmpty())root.addView(text(status,14));
        }
        final ListView thread=new ListView(this);thread.setFocusableInTouchMode(true);thread.setDivider(null);thread.setSelector(android.R.color.transparent);
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
                lp.leftMargin=received?0:dp(18);lp.rightMargin=received?dp(18):0;bubble.setLayoutParams(lp);
                ((TextView)bubble.getChildAt(0)).setText(m.body);
                TextView meta=(TextView)bubble.getChildAt(1);meta.setText(statusMark(m.type,m.delivery)+" "+clock.format(new Date(m.date))+" · "+messageSim(m)+" · "+state(m));
                int slot=preview?m.sub:-1;try{if(!preview){SubscriptionInfo info=SubscriptionManager.from(MessagesActivity.this).getActiveSubscriptionInfo(m.sub);if(info!=null)slot=info.getSimSlotIndex();}}catch(RuntimeException ignored){}meta.setTextColor(m.type==Telephony.Sms.MESSAGE_TYPE_FAILED||m.delivery==2?0xffffb8ad:slot==0?0xff8fc7ff:slot==1?0xff88e5c6:0xffccd2dd);meta.setCompoundDrawables(new AvatarArt(received?address:L.t("Eu"),dp(19)),null,null,null);meta.setCompoundDrawablePadding(dp(5));
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
        if(rows.isEmpty())root.addView(text(L.t("Niciun mesaj"),18),new LinearLayout.LayoutParams(-1,0,1));
        else root.addView(thread,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() {
            messageOptions(address,rows,focus[0]);
        }};
        back=new Runnable() { public void run() { if(preview)finish();else load(""); } };
        footer(L.t("Opțiuni"),L.t("5: răspunde"),L.t("Înapoi"));thread.requestFocus();replyAddress=address;thread.setSelection(focus[0]);
    }
    private void messageOptions(final String address,final ArrayList<Message> rows,final int index) {
        final boolean hasMessage=index>=0 && index<rows.size();
        options(L.t("Conversație"),new String[]{L.t("Răspunde","message"),L.t("Detalii mesaj"),L.t("Redirecționează"),L.t("Copiază textul"),L.t("Copiază numărul"),L.t("Caută în conversație"),L.t("Reîmprospătează"),L.t("Răspuns din șablon"),L.t("Selectează text"),ConversationPrefs.has(this,"pinned",address)?L.t("Desprinde conversația"):L.t("Fixează conversația"),ConversationPrefs.has(this,"archived",address)?L.t("Dezarhivează"):L.t("Arhivează"),ConversationPrefs.has(this,"blocked",address)?L.t("Deblochează în Leo"):L.t("Filtrează expeditorul în Leo"),L.t("Numere și linkuri")},new Choice() { public void pick(int i) {
            if(i>=9&&i<=11){final String group=i==9?"pinned":i==10?"archived":"blocked";if(i==11&&!ConversationPrefs.has(MessagesActivity.this,group,address))options(L.t("Păstrează SMS, ascunde notificările în Leo?"),new String[]{L.t("Anulează"),L.t("Filtrează")},new Choice(){public void pick(int yes){if(yes==1)organize(group,address);}});else organize(group,address);}else if(i==12){if(hasMessage)links(rows.get(index).body);}else if(i==7){replyAddress=address;templates(false);}else if(i==0)compose(address);
            else if(i==5)search(address,rows);
            else if(i==6)load(address);
            else if(hasMessage) {
                Message m=rows.get(index);
                if(i==8)startActivity(new Intent(MessagesActivity.this,TextSelectionActivity.class).putExtra("text",m.body));else if(i==1)detail(address,rows,index);
                else if(i==2)forward(m);
                else if(i==3 || i==4) {
                    ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(i==3?"SMS":L.t("Număr SMS"),i==3?m.body:m.address));
                    notice(i==3?L.t("Text copiat"):L.t("Număr copiat"));
                }
            } else notice(L.t("Niciun mesaj selectat"));
        }});
    }
    private void organize(String group,String address){if(preview){notice(L.t("Demo · fără salvare"));return;}try{ConversationPrefs.toggle(this,group,address);UnreadState.refresh(this);if(group.equals("blocked"))((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel("leo-sms",address.hashCode());load("");}catch(RuntimeException e){notice(L.t("Preferința nu poate fi salvată"));}}
    private void links(String body){final ArrayList<String> links=MessageLinks.extract(body);if(links.isEmpty()){notice(L.t("Niciun număr sau link găsit"));return;}options(L.t("Numere și linkuri"),links.toArray(new String[0]),new Choice(){public void pick(int n){Uri uri=Uri.parse(links.get(n));try{if("tel".equals(uri.getScheme()))startActivity(new Intent(MessagesActivity.this,PhoneActivity.class).setAction(Intent.ACTION_DIAL).setData(uri));else if("mailto".equals(uri.getScheme()))startActivity(new Intent(MessagesActivity.this,EmailActivity.class).putExtra("compose_to",uri.getSchemeSpecificPart()));else startActivity(new Intent(MessagesActivity.this,BrowserActivity.class).setData(uri));}catch(RuntimeException e){notice(L.t("Nu există o aplicație compatibilă"));}}});}
    private static String searchable(String text) {
        return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
    }
    private void search(final String address,final ArrayList<Message> messages) {
        viewingMessages=false;loadingMessages=false;
        LinearLayout fields=fields(L.t("Caută SMS"));
        final EditText query=field(fields,L.t("Text sau număr"), "",InputType.TYPE_CLASS_TEXT);
        left=new Runnable() { public void run() {
            final String term=query.getText().toString().trim();
            if(term.isEmpty()) { notice(L.t("Introdu textul sau numărul căutat"));return; }
            results(address,messages,term);
        }};
        back=new Runnable() { public void run() { restoreMessages(address,messages); } };
        footer(L.t("Caută"),"",L.t("Șterge"));query.requestFocus();
    }
    private void restoreMessages(String address,ArrayList<Message> messages) {
        if(address.isEmpty())display("",messages);else conversation(address,messages);
    }
    private void results(final String address,final ArrayList<Message> messages,final String term) {
        begin(L.t("Caută SMS"));root.addView(text(L.t("Se caută…"),18),new LinearLayout.LayoutParams(-1,0,1));
        back=new Runnable() { public void run() { restoreMessages(address,messages); } };footer("","",L.t("Înapoi"));
        final LinearLayout loading=root;
        worker.execute(new Runnable() { public void run() {
        final ArrayList<Message> matches=new ArrayList<Message>();
        String needle=searchable(term);
        for(Message m:messages) if(GlobalSearchActivity.matches(m.body+" "+m.address,term))matches.add(m);
        Collections.sort(matches,new Comparator<Message>() { public int compare(Message a,Message b) { return Long.compare(b.date,a.date); } });
        ui(new Runnable() { public void run() { if(root==loading)showResults(address,messages,matches); } });
        }});
    }
    private void showResults(final String address,final ArrayList<Message> messages,final ArrayList<Message> matches) {
        ArrayList<String> labels=new ArrayList<String>();
        for(Message m:matches)labels.add(m.address+" · "+messageSim(m)+"\n"+m.body);
        list(L.t("Rezultate · ")+matches.size(),labels,0,new Choice() { public void pick(int i) {
            Message selected=matches.get(i);ArrayList<Message> thread=new ArrayList<Message>();
            for(Message m:messages)if(m.address.equals(selected.address))thread.add(m);
            Collections.sort(thread,new Comparator<Message>() { public int compare(Message a,Message b) { return Long.compare(a.date,b.date); } });
            detail(selected.address,thread,thread.indexOf(selected));
            if(!preview)UnreadState.readThread(getApplicationContext(),selected.address,selected.date);
        } },L.t("Altă căutare"),new Runnable() { public void run() { search(address,messages); } });
        back=new Runnable() { public void run() { restoreMessages(address,messages); } };
    }
    private void forward(final Message message) {
        if(message.body.length()>1000) { notice(L.t("Mesaj prea lung: limita editorului este 1.000 caractere. Poți copia textul integral."));return; }
        draftId=null;
        editor("",message.body,true);
    }

    private void paintBubble(View view,boolean selected) {
        if(!(view instanceof LinearLayout))return;
        LinearLayout row=(LinearLayout)view;boolean received=Boolean.TRUE.equals(row.getTag());
        GradientDrawable background=new GradientDrawable();background.setCornerRadius(dp(10));
        background.setColor(received?palette.card:Appearance.mix(palette.card,palette.accent,.22f));
        background.setStroke(dp(2),selected?palette.accent:Color.TRANSPARENT);
        row.getChildAt(1).setBackground(background);
    }
    private void demo() {
        ArrayList<Message> messages=new ArrayList<Message>();
        String[] bodies={L.t("Salut! Ne vedem mâine?"),L.t("Da, la ora zece. 🙂"),L.t("Perfect. Îți scriu când ajung.")};
        for(int i=0;i<bodies.length;i++) {
            Message m=new Message();m.address=L.t("Conversație demo");m.body=bodies[i];m.type=i==1?2:1;m.sub=i==1?1:0;
            m.date=System.currentTimeMillis()-(2-i)*120000;messages.add(m);
        }
        conversation(L.t("Conversație demo"),messages);
    }
    private void saveDraft(){if(preview||recipient==null||body==null)return;String number=recipient.getText().toString(),text=body.getText().toString();try(DraftStore store=new DraftStore(this)){if(number.isEmpty()&&text.isEmpty()){if(draftId!=null)store.remove(draftId);draftId=null;}else draftId=store.save(draftId,number,text);}catch(RuntimeException failure){notice(L.t("Ciorna nu poate fi salvată"));}}
    private void compose(String address){if(preview){draftId=null;editor(address,"",false);return;}DraftStore.Draft existing=null;if(!address.isEmpty())try(DraftStore store=new DraftStore(this)){existing=store.forNumber(address);}draftId=existing==null?null:existing.id;editor(address,existing==null?"":existing.body,false);}
    private void drafts(){viewingMessages=false;loadingMessages=false;composing=false;final List<DraftStore.Draft> all;try(DraftStore store=new DraftStore(this)){all=store.all();}ArrayList<String> labels=new ArrayList<String>();for(DraftStore.Draft d:all)labels.add((d.number.isEmpty()?L.t("SMS nou"):d.number)+"\n"+d.body);list(L.t("Ciorne · ")+all.size(),labels,0,new Choice(){public void pick(final int at){final DraftStore.Draft d=all.get(at);options(L.t("Ciornă"),new String[]{L.t("Continuă editarea"),L.t("Șterge ciorna")},new Choice(){public void pick(int action){if(action==0){draftId=d.id;editor(d.number,d.body,false);}else options(L.t("Ștergi această ciornă?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice(){public void pick(int yes){if(yes==1){try(DraftStore store=new DraftStore(MessagesActivity.this)){store.remove(d.id);}drafts();}}});}});}},L.t("SMS nou"),new Runnable(){public void run(){compose("");}});back=new Runnable(){public void run(){load(current);}};}
    private void editor(String number,String initialBody,boolean forwarded) {
        viewingMessages=false;loadingMessages=false;
        LinearLayout fields=fields(forwarded?L.t("Redirecționare"):L.t("SMS nou")); composing=true;
        recipient=field(fields,L.t("Număr"),number,InputType.TYPE_CLASS_PHONE);
        body=field(fields,L.t("Mesaj"),initialBody,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(3); body.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        final TextView count=text(SmsBudget.label(initialBody),13);fields.addView(count);body.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void onTextChanged(CharSequence s,int a,int before,int c){}public void afterTextChanged(android.text.Editable e){count.setText(SmsBudget.label(e.toString()));}});
        recipient.setNextFocusDownId(body.getId()); body.setNextFocusUpId(recipient.getId());
        left=new Runnable() { public void run() { options(L.t("Editor SMS"),new String[]{L.t("Continuă spre trimitere"),L.t("Inserează șablon"),L.t("Salvează ciorna")},new Choice(){public void pick(int n){if(n==0)prepare();else if(n==1)templates(true);else{saveDraft();notice(preview?L.t("Demo · fără salvare"):L.t("Ciornă salvată"));}}}); } };
        back=new Runnable() { public void run() {
            if(preview) { composing=false;demo();return; }
            saveDraft(); options(L.t("Ciornă SMS"),new String[]{L.t("Continuă"),L.t("Păstrează și închide"),L.t("Șterge ciorna")},new Choice() { public void pick(int i) {
                if(i==1) load(current); else if(i==2) { try(DraftStore store=new DraftStore(MessagesActivity.this)){if(draftId!=null)store.remove(draftId);}draftId=null;composing=false;load(current); }
            }});
        }};
        footer(L.t("Opțiuni"),"Editor",L.t("Șterge")); (number.isEmpty()?recipient:body).requestFocus();
    }
    private void templates(final boolean insert){final ArrayList<String> all=SmsTemplates.read(this);list(L.t("Șabloane SMS"),all,0,new Choice(){public void pick(final int n){options(L.t("Șablon"),new String[]{insert?L.t("Inserează în mesaj"):L.t("Folosește pentru răspuns"),L.t("Editează"),L.t("Șterge")},new Choice(){public void pick(int action){if(action==0){if(insert){String text=SmsTemplates.insert(body.getText().toString(),all.get(n),Math.max(0,body.getSelectionStart()));if(text.length()>1000){notice(L.t("Mesajul ar depăși 1000 de caractere"));return;}String address=recipient.getText().toString();editor(address,text,false);}else{if(composing)saveDraft();draftId=null;editor(replyAddress,all.get(n),false);}}else if(action==1)templateEdit(all,n,insert);else options(L.t("Ștergi șablonul?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice(){public void pick(int yes){if(yes==1){all.remove(n);SmsTemplates.save(MessagesActivity.this,all);templates(insert);}}});}});}},L.t("Nou"),new Runnable(){public void run(){if(all.size()>=20)notice(L.t("Maximum 20 de șabloane"));else templateEdit(all,-1,insert);}});viewingMessages=false;loadingMessages=false;back=new Runnable(){public void run(){if(insert)editor(recipient.getText().toString(),body.getText().toString(),false);else if(preview)demo();else load(current);}};}
    private void templateEdit(final ArrayList<String> all,final int at,final boolean insert){LinearLayout f=fields(L.t("Editează șablonul"));final EditText value=field(f,L.t("Text · maximum 240 caractere"),at<0?"":all.get(at),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);value.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});left=new Runnable(){public void run(){String text=value.getText().toString().trim();if(text.isEmpty()){notice(L.t("Scrie textul șablonului"));return;}if(at<0)all.add(text);else all.set(at,text);SmsTemplates.save(MessagesActivity.this,all);templates(insert);}};back=new Runnable(){public void run(){templates(insert);}};footer(L.t("Salvează"),"Editor",L.t("Șterge"));value.requestFocus();}
    public boolean onKeyDown(int key,android.view.KeyEvent e){if(!composing&&viewingMessages&&!current.isEmpty()&&key==android.view.KeyEvent.KEYCODE_5){if(e.getRepeatCount()==0)compose(current);return true;}return super.onKeyDown(key,e);}
    public boolean dispatchKeyEvent(android.view.KeyEvent e){int key=e.getKeyCode();if(!composing&&viewingMessages&&current.isEmpty()&&(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)){if(e.getAction()==0&&e.getRepeatCount()==0){folder=Math.floorMod(folder+(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT?1:-1),SmsFolders.LABELS.length);load("");}return true;}return super.dispatchKeyEvent(e);}
    private void prepare() {
        if(preview) { notice(L.t("Demonstrație locală · fără trimitere SMS"));return; }
        final String number=PhoneNumberUtils.normalizeNumber(recipient.getText().toString()),text=body.getText().toString();
        if(number.isEmpty() || text.trim().isEmpty()) { notice(L.t("Completează numărul și mesajul")); return; }
        if(!number.matches("\\+?[0-9]{3,15}") || number.replace("+","").matches("0+")) { notice(L.t("Număr SMS invalid")); return; }
        if(!getSharedPreferences("sms-outgoing",0).getString("token","").isEmpty()) { notice(L.t("Există o trimitere în așteptare; verifică starea înainte de repetare")); return; }
        try {
            final List<SubscriptionInfo> sims=SubscriptionManager.from(this).getActiveSubscriptionInfoList();
            if(sims==null || sims.isEmpty()) { notice(L.t("Niciun SIM activ")); return; }
            String[] labels=new String[sims.size()];
            for(int i=0;i<labels.length;i++) labels[i]="SIM "+(sims.get(i).getSimSlotIndex()+1)+" · "+sims.get(i).getCarrierName();
            options(L.t("Alege SIM pentru SMS"),labels,new Choice() { public void pick(final int i) {
                final SmsManager manager=SmsManager.getSmsManagerForSubscriptionId(sims.get(i).getSubscriptionId());
                final ArrayList<String> parts=manager.divideMessage(text);
                options(parts.size()+" SMS → "+number,new String[]{L.t("Anulează"),L.t("Trimite SMS")},new Choice() { public void pick(int confirmed) {
                    if(confirmed==1) send(manager,number,text,parts);
                }});
            }});
        } catch(RuntimeException failure) { notice(L.t("Serviciul SMS nu este disponibil")); }
    }
    private void send(SmsManager manager,String number,String text,ArrayList<String> parts) {
        if(preview) { notice(L.t("Demonstrație locală · fără trimitere SMS"));return; }
        saveDraft(); String token=UUID.randomUUID().toString(); JSONArray states=new JSONArray();
        ArrayList<PendingIntent> callbacks=new ArrayList<PendingIntent>(),deliveries=new ArrayList<PendingIntent>();
        for(int i=0;i<parts.size();i++) {
            states.put(0);
            Intent result=new Intent(this,SmsStatusReceiver.class).setData(Uri.parse("leo-sms:"+token+"/"+i)).putExtra("token",token).putExtra("part",i);
            callbacks.add(PendingIntent.getBroadcast(this,i,result,PendingIntent.FLAG_UPDATE_CURRENT));
            Intent delivered=new Intent(this,SmsDeliveryReceiver.class).setData(Uri.parse("leo-delivery:"+token+"/"+i)).putExtra("token",token).putExtra("part",i);deliveries.add(PendingIntent.getBroadcast(this,i,delivered,PendingIntent.FLAG_UPDATE_CURRENT));
        }
        long date=System.currentTimeMillis();int sub=manager.getSubscriptionId();
        try(SmsStore store=new SmsStore(this)) { store.outgoing(token,number,text,date,sub);store.requestDelivery(token,parts.size()); }
        catch(RuntimeException unavailable) { notice(L.t("Arhiva SMS nu poate fi salvată; mesajul nu a fost trimis"));return; }
        if(!getSharedPreferences("sms-outgoing",0).edit().putString("token",token).putString("parts",states.toString()).putString("number",number).putString("body",text).putString("draft-id",draftId==null?"":draftId).putLong("date",date).putInt("sub",sub).commit()) {
            try(SmsStore store=new SmsStore(this)) { store.completed(token,false); }
            catch(RuntimeException unavailable) { android.util.Log.w("LeoSms","Cannot update unsent archive entry"); }
            notice(L.t("Starea SMS nu poate fi salvată; mesajul nu a fost trimis"));return;
        }
        try {
            manager.sendMultipartTextMessage(number,null,parts,callbacks,deliveries);
            composing=false; load(number); notice(L.t("SMS în curs de trimitere"));
        } catch(RuntimeException failure) {
            try(SmsStore store=new SmsStore(this)) { store.completed(token,false); }
            catch(RuntimeException unavailable) { notice(L.t("Starea SMS nu poate fi arhivată; verifică înainte de reîncercare"));return; }
            getSharedPreferences("sms-outgoing",0).edit().clear().apply(); notice(L.t("Trimiterea a eșuat; ciorna este păstrată"));
        }
    }
}
