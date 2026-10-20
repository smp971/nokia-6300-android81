package ro.leo.visual;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.telecom.*;
import android.telephony.PhoneNumberUtils;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.Gravity;
import android.graphics.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class PhoneActivity extends LeoActivity {
    private EditText number;
    private Button matches;
    private final android.os.Handler matchHandler=new android.os.Handler();
    private final ArrayList<ContactMatch> phonebook=new ArrayList<ContactMatch>();
    private final ArrayList<ContactMatch> candidates=new ArrayList<ContactMatch>();
    private static final class ContactMatch {String name,number,keys,digits;}
    private final Runnable findMatches=new Runnable(){public void run(){updateMatches();}};

    private LinearLayout favoriteRow;
    private TextView networkText;
    private NetworkTelemetry network;
    private int page,generation;
    private String draft="";
    private final String[] tabNames={"Apelare","Istoric","Statistici"};
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        network=new NetworkTelemetry(this,new Runnable(){public void run(){if(page==0&&networkText!=null)networkText.setText(network.summary());}});
        dial(state==null?getIntent().getStringExtra("number"):state.getString("number"));
        if(state!=null&&state.getInt("page")!=0)switchPage(state.getInt("page"));else if(getIntent().getBooleanExtra("history",false))switchPage(1);
    }
    protected void onResume(){super.onResume();network.start();loadPhonebook();if(page==0&&favoriteRow!=null)favoriteCards();}
    protected void onPause(){network.stop();matchHandler.removeCallbacks(findMatches);super.onPause();}
    protected void onDestroy(){generation++;super.onDestroy();}
    protected void onSaveInstanceState(Bundle state) {
        state.putString("number",number==null?draft:number.getText().toString());state.putInt("page",page);super.onSaveInstanceState(state);
    }
    private void switchPage(int selected){
        if(page==0&&number!=null)draft=number.getText().toString();
        if(selected==0)dial(draft);else if(selected==1)history();else stats();
    }
    private void tabs(){
        LinearLayout tabs=new LinearLayout(this);tabs.setBackgroundColor(palette.bar);
        for(int i=0;i<tabNames.length;i++){
            final int index=i;TextView tab=text(tabNames[i],13);tab.setPadding(0,0,0,0);tab.setGravity(Gravity.CENTER);tab.setFocusable(true);
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},Appearance.surface(palette.selection,dp(3)));states.addState(new int[]{},Appearance.surface(i==page?palette.card:palette.bar,dp(5)));tab.setBackground(states);tab.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{palette.ink,Color.WHITE}));
            tab.setOnClickListener(new View.OnClickListener(){public void onClick(View v){switchPage(index);}});
            tab.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent e){if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0)switchPage((index+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:2))%3);return true;}return false;}});
            tabs.addView(tab,new LinearLayout.LayoutParams(0,dp(28),1));
        }
        root.addView(tabs,1);
        if(page!=0)tabs.getChildAt(page).requestFocus();
    }
    private void menu(){
        options("Telefon",new String[]{"Apelează","Istoric apeluri","Statistici","Contacte","Editează favorite","Telefon Android"},new Choice(){public void pick(int i){
            if(i==0){if(page!=0)dial(draft);else chooseSim();}else if(i==1)switchPage(1);else if(i==2)switchPage(2);
            else if(i==3)startActivity(new Intent(PhoneActivity.this,ContactsActivity.class));
            else if(i==4)options("Alege căsuța",new String[]{"Favorit 1","Favorit 2","Favorit 3"},new Choice(){public void pick(int slot){favoriteMenu(slot);}});
            else{Intent original=getPackageManager().getLaunchIntentForPackage("com.android.dialer");if(original!=null)startActivity(original);else notice("Telefon Android indisponibil");}
        }});
    }
    private void dial(String initial) {
        page=0;generation++;draft=initial==null?"":initial;LinearLayout fields=fields("Telefon");
        networkText=text(network.summary(),12);networkText.setMaxLines(2);networkText.setEllipsize(android.text.TextUtils.TruncateAt.END);networkText.setTextColor(palette.accent);networkText.setPadding(dp(6),dp(2),dp(6),dp(2));fields.addView(networkText,new LinearLayout.LayoutParams(-1,dp(36)));
        number=new EditText(this);number.setId(View.generateViewId());number.setInputType(InputType.TYPE_CLASS_PHONE);number.setSingleLine(true);
        number.setText(draft);number.setHint("Introdu numărul");number.setHintTextColor(0xffa8b6c9);
        number.setTextSize(26);number.setTextColor(Color.WHITE);number.setGravity(Gravity.CENTER);
        number.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        number.setBackground(Appearance.fieldSurface(palette,dp(10)));fields.addView(number,new LinearLayout.LayoutParams(-1,dp(60)));number.setSelection(number.length());
        matches=new Button(this);matches.setAllCaps(false);matches.setTextSize(13);matches.setTextColor(palette.accent);matches.setBackground(Appearance.fieldSurface(palette,dp(7)));matches.setVisibility(View.GONE);
        matches.setOnClickListener(new View.OnClickListener(){public void onClick(View view){final ArrayList<ContactMatch> found=new ArrayList<ContactMatch>(candidates);ArrayList<String> labels=new ArrayList<String>();for(ContactMatch match:found)labels.add(match.name+"\n"+match.number);options("Contacte găsite",labels.toArray(new String[0]),new Choice(){public void pick(int i){number.setText(found.get(i).number);number.setSelection(number.length());number.requestFocus();}});}});fields.addView(matches,new LinearLayout.LayoutParams(-1,dp(38)));
        number.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int st,int count,int after){}public void afterTextChanged(android.text.Editable v){matchHandler.removeCallbacks(findMatches);matchHandler.postDelayed(findMatches,120);}public void onTextChanged(CharSequence v,int start,int before,int count){}});
        TextView heading=text("FAVORITE",12);heading.setTextColor(palette.accent);fields.addView(heading);
        favoriteRow=new LinearLayout(this);fields.addView(favoriteRow,new LinearLayout.LayoutParams(-1,dp(90)));favoriteCards();
        TextView hint=text("Tastează numărul · Verde pentru apel",12);hint.setGravity(Gravity.CENTER);hint.setTextColor(palette.accent);fields.addView(hint);
        number.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(matches.getVisibility()==View.VISIBLE)matches.requestFocus();else favoriteRow.getChildAt(0).requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){((LinearLayout)root.getChildAt(1)).getChildAt(0).requestFocus();return true;}}return false;}});
        left=new Runnable(){public void run(){menu();}};footer("Opțiuni","Verde: apel","Înapoi");tabs();number.requestFocus();updateMatches();
    }
    private void loadPhonebook(){
        worker.execute(new Runnable(){public void run(){final ArrayList<ContactMatch> found=new ArrayList<ContactMatch>();
            try(Cursor c=getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{"display_name","data1"},null,null,"display_name COLLATE LOCALIZED ASC")){
                while(c!=null&&c.moveToNext()&&found.size()<1000){ContactMatch match=new ContactMatch();match.name=c.getString(0)==null?"Contact":c.getString(0);match.number=c.getString(1)==null?"":c.getString(1);match.keys=ContactKeys.t9(match.name);match.digits=ContactKeys.digits(match.number);if(!match.digits.isEmpty())found.add(match);}
                ui(new Runnable(){public void run(){phonebook.clear();phonebook.addAll(found);if(page==0)updateMatches();}});
            }catch(RuntimeException denied){ui(new Runnable(){public void run(){phonebook.clear();if(page==0)updateMatches();}});}
        }});
    }
    private void updateMatches(){
        if(page!=0||number==null||matches==null)return;String query=ContactKeys.digits(number.getText().toString());candidates.clear();
        if(query.length()>=2)for(ContactMatch contact:phonebook)if(contact.keys.contains(query)||contact.digits.contains(query)||PhoneNumberUtils.compare(contact.number,number.getText().toString())){candidates.add(contact);if(candidates.size()>=5)break;}
        matches.setVisibility(candidates.isEmpty()?View.GONE:View.VISIBLE);
        if(!candidates.isEmpty())matches.setText(candidates.get(0).name+(candidates.size()>1?" · "+candidates.size()+" potriviri":" · OK pentru contact"));
    }
    private void favoriteCards(){
        if(favoriteRow==null)return;favoriteRow.removeAllViews();final android.content.SharedPreferences prefs=getSharedPreferences("dialer-favorites",0);
        for(int i=0;i<3;i++){
            final int slot=i;String name=prefs.getString("name"+i,""),value=prefs.getString("number"+i,"");
            Button card=new Button(this);card.setText(value.isEmpty()?"Adaugă":name.isEmpty()?"Favorit "+(i+1):name);card.setTextSize(13);card.setAllCaps(false);card.setPadding(dp(3),dp(6),dp(3),dp(5));card.setGravity(Gravity.CENTER);card.setMaxLines(2);card.setEllipsize(android.text.TextUtils.TruncateAt.END);card.setMinimumWidth(0);card.setMinWidth(0);
            AppBadge icon=new AppBadge("Contacte",dp(32));card.setCompoundDrawables(null,icon,null,null);card.setCompoundDrawablePadding(dp(8));
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},Appearance.surface(palette.selection,dp(10)));states.addState(new int[]{},Appearance.surface(palette.card,dp(10)));card.setBackground(states);
            card.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{palette.ink,Color.WHITE}));
            card.setContentDescription("Favorit "+(i+1)+" · "+(value.isEmpty()?"Adaugă contact":name));
            card.setOnClickListener(new View.OnClickListener(){public void onClick(View v){String value=prefs.getString("number"+slot,"");if(value.isEmpty())pickFavorite(slot);else{number.setText(value);number.setSelection(number.length());number.requestFocus();}}});
            card.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){favoriteMenu(slot);return true;}});
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-1,1);layout.setMargins(dp(3),0,dp(3),0);favoriteRow.addView(card,layout);
        }
    }
    private void pickFavorite(int slot){startActivity(new Intent(this,ContactsActivity.class).putExtra("dialer_slot",slot));}
    private void favoriteMenu(final int slot){options("Favorit "+(slot+1),new String[]{"Alege contact","Golește căsuța","Înapoi"},new Choice(){public void pick(int i){if(i==0)pickFavorite(slot);else if(i==1){getSharedPreferences("dialer-favorites",0).edit().remove("name"+slot).remove("number"+slot).apply();favoriteCards();}}});}
    private void chooseSim() {
        final String destination=PhoneNumberUtils.normalizeNumber(number.getText().toString());
        if(destination.isEmpty()) { notice("Introdu numărul"); return; }
        if(!destination.matches("\\+?[0-9]{3,15}") || destination.replace("+","").matches("0+")) { notice("Număr invalid"); return; }
        final TelecomManager telecom=(TelecomManager)getSystemService(TELECOM_SERVICE);
        try {
            final List<PhoneAccountHandle> accounts=telecom.getCallCapablePhoneAccounts();
            if(accounts.isEmpty()) { notice("Niciun SIM disponibil pentru apel"); return; }
            String[] labels=new String[accounts.size()];
            for(int i=0;i<labels.length;i++) { PhoneAccount account=telecom.getPhoneAccount(accounts.get(i)); labels[i]=account==null?"SIM "+(i+1):account.getLabel().toString(); }
            options("Alege SIM pentru apel",labels,new Choice() { public void pick(final int index) {
                options("Apel: "+destination,new String[]{"Anulează","Apelează"},new Choice() { public void pick(int confirmed) {
                    if(confirmed==1) {
                        Bundle extras=new Bundle(); extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,accounts.get(index));
                        try { telecom.placeCall(Uri.fromParts("tel",destination,null),extras); }
                        catch(RuntimeException failure) { notice("Apelul nu a putut fi inițiat"); }
                    }
                }});
            }});
        } catch(SecurityException denied) { notice("Permisiunea de telefonie lipsește"); }
    }
    private void history() {
        page=1;networkText=null;final int token=++generation;
        final ArrayList<String> labels=new ArrayList<String>(),numbers=new ArrayList<String>();
        final ArrayList<Long> missedDates=new ArrayList<Long>();
        worker.execute(new Runnable() { public void run() {
            try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.DATE,CallLog.Calls.TYPE},null,null,CallLog.Calls.DATE+" DESC")) {
                SimpleDateFormat date=new SimpleDateFormat("dd.MM HH:mm",java.util.Locale.getDefault());
                while(c!=null && c.moveToNext() && labels.size()<100) {
                    String p=c.getString(0),name=c.getString(1); int type=c.getInt(3);
                    if(type==CallLog.Calls.MISSED_TYPE)missedDates.add(c.getLong(2));
                    numbers.add(p); labels.add((name==null || name.isEmpty()?p:name)+"\n"+(type==CallLog.Calls.OUTGOING_TYPE?"Ieșit":type==CallLog.Calls.MISSED_TYPE?"Pierdut":"Primit")+" · "+date.format(new Date(c.getLong(2))));
                }
                ui(new Runnable() { public void run() {
                    if(token!=generation)return;long through=0;
                    for(Long value:missedDates)through=Math.max(through,value);UnreadState.readCalls(PhoneActivity.this,through);
                    list("Istoric apeluri",labels,0,new Choice() { public void pick(int i) { dial(numbers.get(i)); } },"Opțiuni",new Runnable(){public void run(){menu();}});tabs();
                    back=new Runnable() { public void run() { dial(draft); } };
                }});
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { if(token==generation){dial(draft);notice("Istoricul nu poate fi citit");} } }); }
        }});
    }
    private void stats(){
        page=2;networkText=null;final int token=++generation;
        worker.execute(new Runnable(){public void run(){
            final long[] values=new long[7];final java.util.LinkedHashMap<String,long[]> perSim=new java.util.LinkedHashMap<String,long[]>();final java.util.HashMap<String,String> simNames=new java.util.HashMap<String,String>();
            try{java.util.List<android.telephony.SubscriptionInfo> subscriptions=android.telephony.SubscriptionManager.from(PhoneActivity.this).getActiveSubscriptionInfoList();if(subscriptions!=null)for(android.telephony.SubscriptionInfo sim:subscriptions){String label="SIM "+(sim.getSimSlotIndex()+1);simNames.put(""+sim.getSubscriptionId(),label);if(sim.getIccId()!=null)simNames.put(sim.getIccId(),label);}}catch(RuntimeException ignored){}
            long cutoff=System.currentTimeMillis()-7L*86400000L;
            try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.TYPE,CallLog.Calls.DURATION,CallLog.Calls.DATE,CallLog.Calls.PHONE_ACCOUNT_ID},null,null,CallLog.Calls.DATE+" DESC")){
                while(c!=null&&c.moveToNext()&&values[0]<5000){int type=c.getInt(0);long duration=Math.max(0,c.getLong(1));values[0]++;String label=simNames.get(c.getString(3));if(label==null)label="SIM / cont neidentificat";long[] sim=perSim.get(label);if(sim==null){sim=new long[4];perSim.put(label,sim);}sim[0]++;sim[1]+=duration;if(type==CallLog.Calls.OUTGOING_TYPE)sim[2]++;if(type==CallLog.Calls.MISSED_TYPE)sim[3]++;if(type==CallLog.Calls.INCOMING_TYPE)values[1]++;else if(type==CallLog.Calls.OUTGOING_TYPE)values[2]++;else if(type==CallLog.Calls.MISSED_TYPE)values[3]++;values[4]+=duration;if(c.getLong(2)>=cutoff){values[5]++;values[6]+=duration;}}
                ui(new Runnable(){public void run(){if(token!=generation)return;final ArrayList<String> rows=new ArrayList<String>(java.util.Arrays.asList("Total · "+values[0]+" apeluri","Primite · "+values[1],"Efectuate · "+values[2],"Pierdute · "+values[3],"Durată totală · "+duration(values[4]),"Ultimele 7 zile · "+values[5]+" apeluri","Durată 7 zile · "+duration(values[6]),"Jurnal local · max. 5000 intrări"));for(java.util.Map.Entry<String,long[]> entry:perSim.entrySet()){long[] sim=entry.getValue();rows.add(entry.getKey()+" · "+sim[0]+" apeluri");rows.add(entry.getKey()+" · durată "+duration(sim[1]));rows.add(entry.getKey()+" · efectuate "+sim[2]+" / pierdute "+sim[3]);}list("Statistici apeluri",rows,0,new Choice(){public void pick(int i){notice("Statistici din jurnalul local, nu facturare operator");}},"Actualizează",new Runnable(){public void run(){stats();}});tabs();back=new Runnable(){public void run(){dial(draft);}};}});
            }catch(RuntimeException e){ui(new Runnable(){public void run(){if(token==generation){dial(draft);notice("Statisticile nu pot fi citite");}}});}
        }});
    }
    private static String duration(long seconds){return String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(page==0&&key==KeyEvent.KEYCODE_CALL){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)chooseSim();return true;}
        return super.dispatchKeyEvent(event);
    }
}
