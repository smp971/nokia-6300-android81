package ro.leo.visual;

import android.content.*;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.*;
import android.text.InputType;
import android.widget.*;
import java.util.ArrayList;

public final class ContactsActivity extends LeoActivity {
    private final ArrayList<Entry> entries=new ArrayList<Entry>();
    private String filter="";
    private int selected;
    private boolean favorites;
    private static final class Entry { long data,raw,contact; String name,number; boolean starred; }
    protected void onCreate(Bundle state) { super.onCreate(state); favorites=getIntent().getBooleanExtra("favorites",false); load(); }
    private void load() {
        begin("Contacte"); root.addView(text("Se încarcă…",18),new LinearLayout.LayoutParams(-1,0,1)); footer("","", "Înapoi");
        final String query=filter;final boolean numeric=query.matches("[0-9]+"),unfiltered=query.isEmpty()||numeric;
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Entry> found=new ArrayList<Entry>();
            try(Cursor cursor=getContentResolver().query(Phone.CONTENT_URI,
                new String[]{Phone._ID,Phone.RAW_CONTACT_ID,Phone.CONTACT_ID,Phone.DISPLAY_NAME,Phone.NUMBER,Phone.STARRED},
                (unfiltered?"1":"("+Phone.DISPLAY_NAME+" LIKE ? OR "+Phone.NUMBER+" LIKE ?)")+(favorites?" AND "+Phone.STARRED+"=1":""),
                unfiltered?null:new String[]{"%"+query+"%","%"+query+"%"},Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")) {
                while(cursor!=null && cursor.moveToNext() && found.size()<500) {
                    Entry e=new Entry(); e.data=cursor.getLong(0); e.raw=cursor.getLong(1); e.contact=cursor.getLong(2);
                    e.name=cursor.getString(3); e.number=cursor.getString(4); e.starred=cursor.getInt(5)!=0;if(!numeric||ContactKeys.matches(e.name,e.number,query))found.add(e);
                }
                ui(new Runnable() { public void run() { entries.clear(); entries.addAll(found); show(); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { notice("Agenda nu poate fi citită; verifică permisiunile"); entries.clear(); show(); } }); }
        }});
    }
    private void show() {
        ArrayList<String> labels=new ArrayList<String>();
        for(Entry e:entries) labels.add((e.starred?"★ ":"")+(e.name==null?"Fără nume":e.name)+"\n"+e.number);
        list(favorites?"Favorite":filter.isEmpty()?"Contacte":"Rezultate",labels,selected,new Choice() { public void pick(int i) { selected=i;int slot=getIntent().getIntExtra("speed_slot",0);Entry e=entries.get(i);if(slot>=2&&slot<=9){getSharedPreferences("speed-dial",0).edit().putString("name"+slot,e.name).putString("number"+slot,e.number).apply();notice("Tasta "+slot+" atribuită");finish();}else detail(e); } },"Opțiuni",new Runnable() {
            public void run() { options("Contacte",new String[]{"Contact nou","Caută","Toate contactele","Favorite","Import / export VCF","Apelare rapidă"},new Choice() {
                public void pick(int i) { if(i==0) edit(null); else if(i==1) options("Caută",new String[]{"Nume / număr","Cifre T9"},new Choice(){public void pick(int mode){search(mode==1);}}); else if(i==4)startActivity(new Intent(ContactsActivity.this,VCardActivity.class));else if(i==5)startActivity(new Intent(ContactsActivity.this,SpeedDialActivity.class));else { filter=""; favorites=i==3; selected=0; load(); } }
            }); }
        });
    }
    private void search(boolean numeric) {
        LinearLayout fields=fields(numeric?"Caută T9":"Caută contact"); final EditText term=field(fields,numeric?"Cifre pentru literele numelui":"Nume sau număr",numeric&&!filter.matches("[0-9]*")?"":filter,numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);
        left=new Runnable() { public void run() { filter=term.getText().toString().trim(); selected=0; load(); } };
        back=new Runnable() { public void run() { show(); } }; footer("Caută","","Șterge"); term.requestFocus();
    }
    private void detail(final Entry entry) {
        ArrayList<String> actions=new ArrayList<String>(); actions.add("Apelează\n"+entry.number); actions.add("Mesaj SMS"); actions.add("Editează"); actions.add("Șterge contactul");
        actions.add(entry.starred?"Elimină din favorite":"Adaugă la favorite");
        list(entry.name==null?"Contact":entry.name,actions,0,new Choice() { public void pick(int i) {
            if(i==0) startActivity(new Intent(ContactsActivity.this,PhoneActivity.class).putExtra("number",entry.number));
            else if(i==1) startActivity(new Intent(ContactsActivity.this,MessagesActivity.class).putExtra("number",entry.number));
            else if(i==2) edit(entry);
            else if(i==4) {try{ContentValues value=new ContentValues();value.put(ContactsContract.Contacts.STARRED,entry.starred?0:1);getContentResolver().update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,entry.contact),value,null,null);load();}catch(RuntimeException denied){notice("Favoritul nu poate fi modificat");}}
            else options("Ștergi contactul?",new String[]{"Păstrează","Șterge definitiv"},new Choice() { public void pick(int c) {
                if(c==1) { try { getContentResolver().delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,entry.contact),null,null); load(); }
                    catch(RuntimeException failure) { notice("Contactul nu poate fi șters"); } }
            }});
        } },"",null);
        back=new Runnable() { public void run() { load(); } };
    }
    private void edit(final Entry existing) {
        LinearLayout fields=fields(existing==null?"Contact nou":"Editează");
        final EditText name=field(fields,"Nume",existing==null?"":existing.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText number=field(fields,"Număr",existing==null?"":existing.number,InputType.TYPE_CLASS_PHONE);
        name.setNextFocusDownId(number.getId()); number.setNextFocusUpId(name.getId());
        left=new Runnable() { public void run() {
            final String n=name.getText().toString().trim(),p=number.getText().toString().trim();
            if(n.isEmpty() || p.isEmpty()) { notice("Completează numele și numărul"); return; }
            ArrayList<ContentProviderOperation> ops=new ArrayList<ContentProviderOperation>();
            if(existing==null) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
                    .withValue(ContactsContract.Data.MIMETYPE,StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME,n).build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
                    .withValue(ContactsContract.Data.MIMETYPE,Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER,p).withValue(Phone.TYPE,Phone.TYPE_MOBILE).build());
            } else {
                ops.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI,existing.data))
                    .withValue(Phone.NUMBER,p).withExpectedCount(1).build());
                long nameId=-1;
                try(Cursor c=getContentResolver().query(ContactsContract.Data.CONTENT_URI,new String[]{ContactsContract.Data._ID},
                    ContactsContract.Data.RAW_CONTACT_ID+"=? AND "+ContactsContract.Data.MIMETYPE+"=?",new String[]{""+existing.raw,StructuredName.CONTENT_ITEM_TYPE},null)) {
                    if(c!=null && c.moveToFirst()) nameId=c.getLong(0);
                } catch(RuntimeException failure) { notice("Contactul nu poate fi editat"); return; }
                ContentProviderOperation.Builder op=nameId<0?ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID,existing.raw).withValue(ContactsContract.Data.MIMETYPE,StructuredName.CONTENT_ITEM_TYPE)
                    :ContentProviderOperation.newUpdate(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI,nameId)).withExpectedCount(1);
                ops.add(op.withValue(StructuredName.DISPLAY_NAME,n).build());
            }
            try { getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops); filter=""; load(); notice("Contact salvat"); }
            catch(Exception failure) { notice("Salvarea a eșuat; câmpurile sunt păstrate"); }
        }};
        back=new Runnable() { public void run() {
            options("Renunți la editare?",new String[]{"Continuă","Renunță"},new Choice() { public void pick(int i) { if(i==1) load(); } });
        }};
        footer("Salvează","Următor","Șterge"); name.requestFocus();
    }
}
