package ro.leo.visual;
import ro.leo.i18n.L;

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
    private static final class ContactMatch {String name,number,keys,digits,photo;long id;}
    private final android.util.LruCache<String,Bitmap> contactImages=new android.util.LruCache<String,Bitmap>(12);private final java.util.HashSet<String> imagePending=new java.util.HashSet<String>();private int phonebookGeneration;
    private final Runnable findMatches=new Runnable(){public void run(){updateMatches();}};

    private LinearLayout favoriteRow;
    private LinearLayout tabBar;
    private TextView networkText;
    private TextView[] simChips;
    private Button recent;
    private NetworkTelemetry network;
    private int page,generation;
    private String draft="";
    private final RowArtwork historyArt=new RowArtwork(this);
    private final ArrayList<ContactIdentity> historyPeople=new ArrayList<ContactIdentity>();
    private final String[] tabNames={L.t("Apelare"),L.t("Istoric"),L.t("Statistici")};
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        network=new NetworkTelemetry(this,new Runnable(){public void run(){if(page==0)updateSimChips();}});
        dial(state==null?intentNumber(getIntent()):state.getString("number"));
        if(state!=null&&state.getInt("page")!=0)switchPage(state.getInt("page"));else if(getIntent().getBooleanExtra("history",false))switchPage(1);
    }
    private String intentNumber(Intent i){if(i.getData()!=null&&"tel".equals(i.getData().getScheme()))return i.getData().getSchemeSpecificPart();return i.getStringExtra("number");}
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);dial(intentNumber(i));if(i.getBooleanExtra("history",false))switchPage(1);}
    protected void onResume(){super.onResume();network.start();loadPhonebook();if(page==0&&favoriteRow!=null){favoriteCards();loadRecent();}}
    protected void onPause(){network.stop();matchHandler.removeCallbacks(findMatches);super.onPause();}
    protected void onDestroy(){generation++;phonebookGeneration++;super.onDestroy();}
    protected void onSaveInstanceState(Bundle state) {
        state.putString("number",number==null?draft:number.getText().toString());state.putInt("page",page);super.onSaveInstanceState(state);
    }
    private void switchPage(int selected){
        if(page==0&&number!=null)draft=number.getText().toString();
        if(selected==0)dial(draft);else if(selected==1)history();else stats();
    }
    private void tabs(){
        LinearLayout tabs=new LinearLayout(this);tabBar=tabs;tabs.setBackgroundColor(palette.bar);
        for(int i=0;i<tabNames.length;i++){
            final int index=i;TextView tab=text(tabNames[i],13);tab.setPadding(0,0,0,0);tab.setGravity(Gravity.CENTER);tab.setFocusableInTouchMode(true);
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},PhoneTheme.surface(palette,palette.card,true));states.addState(new int[]{},Appearance.surface(i==page?palette.card:palette.bar,dp(5)));tab.setBackground(states);tab.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{palette.ink,Color.WHITE}));
            tab.setOnClickListener(new View.OnClickListener(){public void onClick(View v){switchPage(index);}});
            tab.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent e){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN){if(page==0)number.requestFocus();else for(int n=0;n<root.getChildCount();n++)if(root.getChildAt(n) instanceof ListView){root.getChildAt(n).requestFocus();break;}}return true;}return false;}});
            tabs.addView(tab,new LinearLayout.LayoutParams(0,dp(28),1));
        }
        root.addView(tabs,1);
        if(page!=0)tabs.getChildAt(page).requestFocus();
    }
    private void menu(){
        options(L.t("Telefon"),new String[]{L.t("Apelează"),L.t("Istoric apeluri"),L.t("Statistici"),L.t("Contacte"),L.t("Editează favorite"),L.t("Demo apel Leo"),L.t("Integrarea apelurilor")},new Choice(){public void pick(int i){
            if(i==0){if(page!=0)dial(draft);else chooseSim();}else if(i==1)switchPage(1);else if(i==2)switchPage(2);
            else if(i==3)startActivity(new Intent(PhoneActivity.this,ContactsActivity.class));
            else if(i==4)options(L.t("Alege căsuța"),new String[]{L.t("Favorit 1"),L.t("Favorit 2"),L.t("Favorit 3")},new Choice(){public void pick(int slot){favoriteMenu(slot);}});
            else if(i==5)startActivity(new Intent(PhoneActivity.this,InCallActivity.class).putExtra("demo",true));else integration();
        }});
    }
    private void integration(){final TelecomManager t=(TelecomManager)getSystemService(TELECOM_SERVICE);String current=t.getDefaultDialerPackage();options(L.t("Apeluri în Leo"),new String[]{L.t("Rol telefon · ")+(getPackageName().equals(current)?L.t("Leo activ"):"Android"),L.t("Activează Telefon Leo"),L.t("Deschide apelul curent")},new Choice(){public void pick(int i){if(i==1){try{startActivity(new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,getPackageName()));}catch(RuntimeException e){notice(L.t("Alegerea telefonului implicit nu este disponibilă"));}}else if(i==2){if(LeoInCallService.current()!=null)startActivity(new Intent(PhoneActivity.this,InCallActivity.class));else notice(L.t("Niciun apel curent în Leo"));}}});}
    private void dial(String initial) {
        page=0;generation++;draft=initial==null?"":initial;LinearLayout fields=fields(L.t("Telefon"));
        fields.setPadding(dp(6),dp(6),dp(6),dp(4));networkText=null;
        simChips=new TextView[2];for(int i=0;i<2;i++){TextView chip=text("",13);chip.setSingleLine();chip.setEllipsize(android.text.TextUtils.TruncateAt.END);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setPadding(dp(7),0,dp(7),0);chip.setTextColor(i==0?0xff62e8cf:0xffc4a5ff);chip.setBackground(Appearance.surface(Appearance.mix(palette.card,i==0?0xff62e8cf:0xffc4a5ff,.10f),dp(4)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(26));lp.bottomMargin=dp(4);fields.addView(chip,lp);simChips[i]=chip;}updateSimChips();
        number=new EditText(this);number.setId(View.generateViewId());number.setInputType(InputType.TYPE_CLASS_PHONE);number.setSingleLine(true);
        number.setText(draft);number.setHint(L.t("Introdu numărul"));number.setHintTextColor(0xffa8b6c9);
        number.setTextSize(23);number.setTextColor(Color.WHITE);number.setGravity(Gravity.CENTER);number.setPadding(dp(5),0,dp(5),0);
        number.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        number.setBackground(PhoneTheme.focus(palette,palette.card));fields.addView(number,new LinearLayout.LayoutParams(-1,dp(46)));number.setSelection(number.length());
        matches=new Button(this);matches.setAllCaps(false);matches.setTextSize(13);matches.setTextColor(palette.accent);matches.setBackground(Appearance.fieldSurface(palette,dp(7)));matches.setVisibility(View.GONE);matches.setPadding(dp(5),0,dp(5),0);matches.setMinHeight(0);matches.setMinimumHeight(0);
        matches.setOnClickListener(new View.OnClickListener(){public void onClick(View view){final ArrayList<ContactMatch> found=new ArrayList<ContactMatch>(candidates);ArrayList<String> labels=new ArrayList<String>();for(ContactMatch match:found)labels.add(match.name+"\n"+match.number);options(L.t("Contacte găsite"),labels.toArray(new String[0]),new Choice(){public void pick(int i){number.setText(found.get(i).number);number.setSelection(number.length());number.requestFocus();}});}});fields.addView(matches,new LinearLayout.LayoutParams(-1,dp(52)));
        number.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int st,int count,int after){}public void afterTextChanged(android.text.Editable v){if(localCode())return;matchHandler.removeCallbacks(findMatches);matchHandler.postDelayed(findMatches,120);}public void onTextChanged(CharSequence v,int start,int before,int count){}});

        favoriteRow=new LinearLayout(this);LinearLayout.LayoutParams favoritesLayout=new LinearLayout.LayoutParams(-1,dp(76));favoritesLayout.topMargin=dp(6);favoritesLayout.bottomMargin=dp(6);fields.addView(favoriteRow,favoritesLayout);favoriteCards();
        recent=new Button(this);recent.setAllCaps(false);recent.setText(L.t("Ultimul apel\nSe verifică jurnalul…"));recent.setTextSize(13);recent.setTextColor(Color.WHITE);recent.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);recent.setPadding(dp(9),0,dp(7),0);recent.setMinHeight(0);recent.setMinimumHeight(0);recent.setMaxLines(3);recent.setEllipsize(android.text.TextUtils.TruncateAt.END);recent.setBackground(PhoneTheme.focus(palette,palette.card));recent.setOnClickListener(new View.OnClickListener(){public void onClick(View v){switchPage(1);}});fields.addView(recent,new LinearLayout.LayoutParams(-1,dp(60)));loadRecent();
        number.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(matches.getVisibility()==View.VISIBLE)matches.requestFocus();else favoriteRow.getChildAt(0).requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){focusTabs();return true;}}return false;}});
        left=new Runnable(){public void run(){menu();}};footer(L.t("Opțiuni"),L.t("Verde: apel"),L.t("Înapoi"));tabs();number.requestFocus();updateMatches();
    }
    private void updateSimChips(){if(simChips==null)return;String[] rows=network.summary().split("\n",-1);for(int i=0;i<2;i++)simChips[i].setText(i<rows.length?rows[i]:"SIM "+(i+1));}
    private void loadRecent(){final int token=generation;worker.execute(new Runnable(){public void run(){String label=L.t("Ultimul apel\nJurnalul este gol");try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.DATE,CallLog.Calls.TYPE,CallLog.Calls.PHONE_ACCOUNT_ID,CallLog.Calls.NUMBER_PRESENTATION},null,null,CallLog.Calls.DATE+" DESC")){if(c!=null&&c.moveToFirst()){String name=c.getString(1);if(c.getInt(5)!=TelecomManager.PRESENTATION_ALLOWED)name=L.t("Număr privat");else if(name==null||name.isEmpty())name=c.getString(0);String sim=accountSlot(c.getString(4));label=L.t("Ultimul · ")+(name==null?L.t("Necunoscut"):name)+"\n"+(c.getInt(3)==CallLog.Calls.OUTGOING_TYPE?L.t("↗ Efectuat"):c.getInt(3)==CallLog.Calls.MISSED_TYPE?L.t("↙ Pierdut"):L.t("↙ Primit"))+" · "+sim+" · "+new SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new Date(c.getLong(2)));}}catch(RuntimeException e){label=L.t("Ultimul apel\nJurnal indisponibil");}final String text=label;ui(new Runnable(){public void run(){if(page==0&&token==generation&&recent!=null)recent.setText(text);}});}});}
    private String accountSlot(String id){return PhoneAccounts.slot(this,id);}
    private boolean localCode(){if(number==null||!"*#06#".equals(number.getText().toString()))return false;number.setText("");showDeviceIds();return true;}
    private void showDeviceIds(){android.telephony.TelephonyManager phone=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);ArrayList<String> rows=new ArrayList<String>();for(int slot=0;slot<2;slot++){String value=null;try{value=phone.getImei(slot);}catch(RuntimeException ignored){}rows.add("SIM "+(slot+1)+": "+(value==null||value.isEmpty()?L.t("Indisponibil"):value));}options(L.t("IMEI · dispozitiv"),rows.toArray(new String[0]),new Choice(){public void pick(int i){}});}
    private void loadPhonebook(){
        final int request=++phonebookGeneration;worker.execute(new Runnable(){public void run(){final ArrayList<ContactMatch> found=new ArrayList<ContactMatch>();
            try(Cursor c=getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{"display_name","data1","contact_id","photo_thumb_uri"},null,null,"display_name COLLATE LOCALIZED ASC")){
                while(c!=null&&c.moveToNext()&&found.size()<1000){ContactMatch match=new ContactMatch();match.name=c.getString(0)==null?L.t("Contact"):c.getString(0);match.number=c.getString(1)==null?"":c.getString(1);match.id=c.getLong(2);match.photo=c.getString(3);match.keys=ContactKeys.t9(match.name);match.digits=ContactKeys.digits(match.number);if(!match.digits.isEmpty())found.add(match);}
                ui(new Runnable(){public void run(){if(request!=phonebookGeneration)return;phonebook.clear();phonebook.addAll(found);if(page==0)updateMatches();}});
            }catch(RuntimeException denied){ui(new Runnable(){public void run(){if(request!=phonebookGeneration)return;phonebook.clear();if(page==0)updateMatches();}});}
        }});
    }
    private void updateMatches(){
        if(page!=0||number==null||matches==null)return;String query=ContactKeys.digits(number.getText().toString());candidates.clear();
        if(query.length()>=2){ArrayList<ContactMatch> found=new ArrayList<ContactMatch>();for(ContactMatch contact:phonebook)if(ContactKeys.rank(contact.name,contact.number,query)<4)found.add(contact);final String needle=query;java.util.Collections.sort(found,new java.util.Comparator<ContactMatch>(){public int compare(ContactMatch a,ContactMatch b){return Integer.compare(ContactKeys.rank(a.name,a.number,needle),ContactKeys.rank(b.name,b.number,needle));}});for(ContactMatch match:found){candidates.add(match);if(candidates.size()>=5)break;}}
        matches.setVisibility(candidates.isEmpty()?View.GONE:View.VISIBLE);matches.setCompoundDrawables(null,null,null,null);
        if(!candidates.isEmpty()){ContactMatch first=candidates.get(0);boolean exact=ContactKeys.rank(first.name,first.number,query)==0;matches.setText(first.name+"\n"+(exact?L.t("Număr cunoscut · "):"")+(candidates.size()>1?candidates.size()+L.t(" potriviri · OK"):L.t("OK: alege contactul")));contactImage(first);}
    }
    private void contactImage(final ContactMatch match){final String key=match.id+":"+match.photo;Bitmap cached=contactImages.get(key);android.graphics.drawable.Drawable art=cached==null?new AvatarArt(match.name,dp(36)):new android.graphics.drawable.BitmapDrawable(getResources(),cached);art.setBounds(0,0,dp(36),dp(36));matches.setCompoundDrawables(art,null,null,null);matches.setCompoundDrawablePadding(dp(5));if(cached==null&&match.photo!=null&&imagePending.add(key))worker.execute(new Runnable(){public void run(){Bitmap b=null;try{b=Appearance.decode(PhoneActivity.this,Uri.parse(match.photo),64);int side=Math.min(b.getWidth(),b.getHeight());if(b.getWidth()!=b.getHeight()){Bitmap square=Bitmap.createBitmap(b,(b.getWidth()-side)/2,(b.getHeight()-side)/2,side,side);if(square!=b)b.recycle();b=square;}}catch(Exception|OutOfMemoryError ignored){}final Bitmap image=b;runOnUiThread(new Runnable(){public void run(){imagePending.remove(key);if(isFinishing()||isDestroyed()){if(image!=null)image.recycle();return;}if(image!=null){contactImages.put(key,image);if(!candidates.isEmpty()&&candidates.get(0)==match&&page==0)contactImage(match);}}});}});}
    private void favoriteCards(){
        if(favoriteRow==null)return;favoriteRow.removeAllViews();final android.content.SharedPreferences prefs=getSharedPreferences("dialer-favorites",0);
        for(int i=0;i<3;i++){
            final int slot=i;String name=prefs.getString("name"+i,""),value=prefs.getString("number"+i,"");
            Button card=new Button(this);card.setText(value.isEmpty()?L.t("Adaugă"):name.isEmpty()?L.t("Favorit ")+(i+1):name);card.setTextSize(13);card.setAllCaps(false);card.setPadding(dp(3),dp(6),dp(3),dp(5));card.setGravity(Gravity.CENTER);card.setMaxLines(2);card.setEllipsize(android.text.TextUtils.TruncateAt.END);card.setMinimumWidth(0);card.setMinWidth(0);
            android.graphics.drawable.Drawable icon=new PhoneTheme.Avatar(slot,dp(28));card.setCompoundDrawables(null,icon,null,null);card.setCompoundDrawablePadding(dp(5));
            card.setBackground(PhoneTheme.focus(palette,Appearance.mix(palette.card,palette.accent,.08f+slot*.05f)));
            card.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{palette.ink,Color.WHITE}));
            card.setContentDescription(L.t("Favorit ")+(i+1)+" · "+(value.isEmpty()?L.t("Adaugă contact"):name));
            card.setOnClickListener(new View.OnClickListener(){public void onClick(View v){String value=prefs.getString("number"+slot,"");if(value.isEmpty())pickFavorite(slot);else{number.setText(value);number.setSelection(number.length());number.requestFocus();}}});
            card.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){favoriteMenu(slot);return true;}});
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-1,1);layout.setMargins(dp(3),0,dp(3),0);favoriteRow.addView(card,layout);
        }
    }
    private void pickFavorite(int slot){startActivity(new Intent(this,ContactsActivity.class).putExtra("dialer_slot",slot));}
    private void favoriteMenu(final int slot){options(L.t("Favorit ")+(slot+1),new String[]{L.t("Alege contact"),L.t("Golește căsuța"),L.t("Înapoi")},new Choice(){public void pick(int i){if(i==0)pickFavorite(slot);else if(i==1){getSharedPreferences("dialer-favorites",0).edit().remove("name"+slot).remove("number"+slot).apply();favoriteCards();}}});}
    private void chooseSim() {
        if(localCode())return;
        final String destination=PhoneNumberUtils.normalizeNumber(number.getText().toString());
        if(destination.isEmpty()) { notice(L.t("Introdu numărul")); return; }
        if(!destination.matches("\\+?[0-9]{3,15}") || destination.replace("+","").matches("0+")) { notice(L.t("Număr invalid")); return; }
        final TelecomManager telecom=(TelecomManager)getSystemService(TELECOM_SERVICE);
        try {
            final List<PhoneAccountHandle> accounts=telecom.getCallCapablePhoneAccounts();
            if(accounts.isEmpty()) { notice(L.t("Niciun SIM disponibil pentru apel")); return; }
            String[] labels=new String[accounts.size()];
            for(int i=0;i<labels.length;i++) { PhoneAccount account=telecom.getPhoneAccount(accounts.get(i)); labels[i]=accountSlot(accounts.get(i).getId())+" · "+(account==null?L.t("Cont de apel"):account.getLabel().toString()); }
            options(L.t("Alege SIM pentru apel"),labels,new Choice() { public void pick(final int index) {
                Bundle extras=new Bundle(); extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,accounts.get(index));
                try { telecom.placeCall(Uri.fromParts("tel",destination,null),extras); }
                catch(RuntimeException failure) { notice(L.t("Apelul nu a putut fi inițiat")); }
            }});
        } catch(SecurityException denied) { notice(L.t("Permisiunea de telefonie lipsește")); }
    }
    private void history() {
        page=1;networkText=null;final int token=++generation;
        final ArrayList<String> labels=new ArrayList<String>(),numbers=new ArrayList<String>();
        final ArrayList<Long> missedDates=new ArrayList<Long>();
        final ArrayList<ContactIdentity> people=new ArrayList<ContactIdentity>();final ArrayList<String> descriptions=new ArrayList<String>();
        worker.execute(new Runnable() { public void run() {
            try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.DATE,CallLog.Calls.TYPE,CallLog.Calls.PHONE_ACCOUNT_ID,CallLog.Calls.NUMBER_PRESENTATION,CallLog.Calls.DURATION},null,null,CallLog.Calls.DATE+" DESC")) {
                SimpleDateFormat date=new SimpleDateFormat("dd.MM HH:mm",java.util.Locale.getDefault());
                while(c!=null && c.moveToNext() && labels.size()<100) {
                    String p=c.getString(0),name=c.getString(1); int type=c.getInt(3);
                    if(type==CallLog.Calls.MISSED_TYPE)missedDates.add(c.getLong(2));
                    String only=getIntent().getStringExtra("history_number");if(only!=null&&!PhoneNumberUtils.compare(only,p))continue;boolean allowed=c.getInt(5)==TelecomManager.PRESENTATION_ALLOWED;numbers.add(allowed?p:"");ContactIdentity identity=ContactIdentity.find(PhoneActivity.this,allowed?p:"");people.add(identity);String description=(type==CallLog.Calls.OUTGOING_TYPE?L.t("Efectuat"):type==CallLog.Calls.MISSED_TYPE?L.t("Pierdut"):L.t("Primit"))+" · "+accountSlot(c.getString(4))+" · "+date.format(new Date(c.getLong(2)))+L.t("\nDurată · ")+duration(c.getLong(6));descriptions.add(description);labels.add(identity.name+"\n"+description.substring(0,description.indexOf("\n")));
                }
                ui(new Runnable() { public void run() {
                    if(token!=generation)return;long through=0;
                    for(Long value:missedDates)through=Math.max(through,value);CallNotices.clearMissed(PhoneActivity.this);UnreadState.readCalls(PhoneActivity.this,through);
                    historyPeople.clear();historyPeople.addAll(people);list(L.t("Istoric apeluri"),labels,0,new Choice() { public void pick(int i) { callDetail(people.get(i),numbers.get(i),descriptions.get(i)); } },L.t("Opțiuni"),new Runnable(){public void run(){menu();}});tabs();
                    back=new Runnable() { public void run() { dial(draft); } };
                }});
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { if(token==generation){dial(draft);notice(L.t("Istoricul nu poate fi citit"));} } }); }
        }});
    }
    android.graphics.drawable.Drawable rowIcon(String title,int at){if(!title.equals(L.t("Istoric apeluri"))||at<0||at>=historyPeople.size())return null;ContactIdentity p=historyPeople.get(at);return historyArt.get("history:"+p.id+":"+p.name,p.photo,p.name);}
    private void callDetail(final ContactIdentity person,final String number,final String description){
        generation++;begin(person.name);android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(text((number.isEmpty()?L.t("Număr indisponibil"):number)+"\n\n"+description,17));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.setFocusable(true);scroll.requestFocus();
        left=new Runnable(){public void run(){options(L.t("Apel"),new String[]{L.t("Deschide în Telefon"),L.t("Scrie SMS"),L.t("Contact")},new Choice(){public void pick(int at){if(number.isEmpty()){notice(L.t("Număr privat sau indisponibil"));return;}if(at==0)dial(number);else if(at==1)startActivity(new Intent(PhoneActivity.this,MessagesActivity.class).putExtra("number",number));else if(person.id>=0)startActivity(new Intent(PhoneActivity.this,ContactsActivity.class).putExtra("contact_id",person.id));else notice(L.t("Numărul nu este salvat în Contacte"));}});}};
        back=new Runnable(){public void run(){history();}};footer(L.t("Opțiuni"),L.t("Detalii"),L.t("Înapoi"));
    }
    private void stats(){
        page=2;networkText=null;final int token=++generation;
        worker.execute(new Runnable(){public void run(){
            final long[] values=new long[7];final java.util.LinkedHashMap<String,long[]> perSim=new java.util.LinkedHashMap<String,long[]>();final java.util.HashMap<String,String> simNames=new java.util.HashMap<String,String>();
            try{java.util.List<android.telephony.SubscriptionInfo> subscriptions=android.telephony.SubscriptionManager.from(PhoneActivity.this).getActiveSubscriptionInfoList();if(subscriptions!=null)for(android.telephony.SubscriptionInfo sim:subscriptions){String label="SIM "+(sim.getSimSlotIndex()+1);simNames.put(""+sim.getSubscriptionId(),label);if(sim.getIccId()!=null)simNames.put(sim.getIccId(),label);}}catch(RuntimeException ignored){}
            long cutoff=System.currentTimeMillis()-7L*86400000L;
            try(Cursor c=getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.TYPE,CallLog.Calls.DURATION,CallLog.Calls.DATE,CallLog.Calls.PHONE_ACCOUNT_ID},null,null,CallLog.Calls.DATE+" DESC")){
                while(c!=null&&c.moveToNext()&&values[0]<5000){int type=c.getInt(0);long duration=Math.max(0,c.getLong(1));values[0]++;String label=simNames.get(c.getString(3));if(label==null)label="SIM / cont neidentificat";long[] sim=perSim.get(label);if(sim==null){sim=new long[4];perSim.put(label,sim);}sim[0]++;sim[1]+=duration;if(type==CallLog.Calls.OUTGOING_TYPE)sim[2]++;if(type==CallLog.Calls.MISSED_TYPE)sim[3]++;if(type==CallLog.Calls.INCOMING_TYPE)values[1]++;else if(type==CallLog.Calls.OUTGOING_TYPE)values[2]++;else if(type==CallLog.Calls.MISSED_TYPE)values[3]++;values[4]+=duration;if(c.getLong(2)>=cutoff){values[5]++;values[6]+=duration;}}
                ui(new Runnable(){public void run(){if(token!=generation)return;final ArrayList<String> rows=new ArrayList<String>(java.util.Arrays.asList("Total · "+values[0]+L.t(" apeluri"),L.t("Primite · ")+values[1],L.t("Efectuate · ")+values[2],L.t("Pierdute · ")+values[3],L.t("Durată totală · ")+duration(values[4]),L.t("Ultimele 7 zile · ")+values[5]+L.t(" apeluri"),L.t("Durată 7 zile · ")+duration(values[6]),L.t("Jurnal local · max. 5000 intrări")));for(java.util.Map.Entry<String,long[]> entry:perSim.entrySet()){long[] sim=entry.getValue();rows.add(entry.getKey()+" · "+sim[0]+L.t(" apeluri"));rows.add(entry.getKey()+L.t(" · durată ")+duration(sim[1]));rows.add(entry.getKey()+L.t(" · efectuate ")+sim[2]+" / pierdute "+sim[3]);}list(L.t("Statistici apeluri"),rows,0,new Choice(){public void pick(int i){notice(L.t("Statistici din jurnalul local, nu facturare operator"));}},L.t("Actualizează"),new Runnable(){public void run(){stats();}});tabs();back=new Runnable(){public void run(){dial(draft);}};}});
            }catch(RuntimeException e){ui(new Runnable(){public void run(){if(token==generation){dial(draft);notice(L.t("Statisticile nu pot fi citite"));}}});}
        }});
    }
    private static String duration(long seconds){return String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
    private void focusTabs(){if(tabBar!=null)tabBar.getChildAt(page).requestFocus();}
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        // Switch from every non-editor area, including lists that wrap vertically.
        // Keep left/right available for editing a phone number.
        if(tabBar!=null&&tabBar.getParent()==root&&!(getCurrentFocus() instanceof EditText)
                &&(page!=0||(getCurrentFocus()!=null&&getCurrentFocus().getParent()==tabBar))
                &&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){switchPage((page+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:2))%3);if(page==0)focusTabs();}
            return true;
        }
        if(page==0&&key==KeyEvent.KEYCODE_CALL){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)chooseSim();return true;}
        return super.dispatchKeyEvent(event);
    }
}
