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
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class PhoneActivity extends LeoActivity {
    private EditText number;
    protected void onCreate(Bundle state) {
        super.onCreate(state); dial(state==null?getIntent().getStringExtra("number"):state.getString("number"));
    }
    protected void onSaveInstanceState(Bundle state) {
        if(number!=null) state.putString("number",number.getText().toString()); super.onSaveInstanceState(state);
    }
    private void dial(String initial) {
        LinearLayout fields=fields("Telefon"); number=field(fields,"Număr",initial==null?"":initial,InputType.TYPE_CLASS_PHONE);
        fields.addView(text("Tasta verde: apel\nSoft stânga: opțiuni",16));
        left=new Runnable() { public void run() {
            options("Telefon",new String[]{"Apelează","Istoric apeluri","Contacte","Telefon Android"},new Choice() {
                public void pick(int i) {
                    if(i==0) chooseSim(); else if(i==1) history();
                    else if(i==2) startActivity(new Intent(PhoneActivity.this,ContactsActivity.class));
                    else {
                        Intent original=getPackageManager().getLaunchIntentForPackage("com.android.dialer");
                        if(original!=null) startActivity(original); else notice("Telefon Android indisponibil");
                    }
                }
            });
        }};
        footer("Opțiuni","Verde: apel","Șterge"); number.requestFocus();
    }
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
                    long through=0;
                    for(Long value:missedDates)through=Math.max(through,value);UnreadState.readCalls(PhoneActivity.this,through);
                    list("Istoric apeluri",labels,0,new Choice() { public void pick(int i) { dial(numbers.get(i)); } },"",null);
                    back=new Runnable() { public void run() { dial(""); } };
                }});
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { notice("Istoricul nu poate fi citit"); } }); }
        }});
    }
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getKeyCode()==KeyEvent.KEYCODE_CALL && form) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) chooseSim(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
