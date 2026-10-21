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
    private int loadGeneration;
    private boolean favorites,managingFavorites;
    private Entry photoEntry;private final RowArtwork avatars=new RowArtwork(this);private String contactsTitle="";
    private static final class Entry { long data,raw,contact; String name,number; boolean starred; }
    protected void onCreate(Bundle state) { super.onCreate(state); favorites=getIntent().getBooleanExtra("favorites",false); long id=getIntent().getLongExtra("contact_id",-1);if(id<0)load();else openContact(id); }
    private void openContact(long id){try(Cursor c=getContentResolver().query(Phone.CONTENT_URI,new String[]{Phone._ID,Phone.RAW_CONTACT_ID,Phone.CONTACT_ID,Phone.DISPLAY_NAME,Phone.NUMBER,Phone.STARRED},Phone.CONTACT_ID+"=?",new String[]{""+id},null)){if(c!=null&&c.moveToFirst()){Entry e=new Entry();e.data=c.getLong(0);e.raw=c.getLong(1);e.contact=c.getLong(2);e.name=c.getString(3);e.number=c.getString(4);e.starred=c.getInt(5)!=0;detail(e);return;}}catch(RuntimeException ignored){}load();}
    private void load() {
        final int generation=++loadGeneration;
        begin("Contacte"); root.addView(text("Se încarcă…",18),new LinearLayout.LayoutParams(-1,0,1)); footer("","", "Înapoi");
        final String query=filter;final boolean numeric=query.matches("[0-9]+"),unfiltered=query.isEmpty()||numeric;
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Entry> found=new ArrayList<Entry>();
            try(Cursor cursor=getContentResolver().query(Phone.CONTENT_URI,
                new String[]{Phone._ID,Phone.RAW_CONTACT_ID,Phone.CONTACT_ID,Phone.DISPLAY_NAME,Phone.NUMBER,Phone.STARRED},
                (unfiltered?"1":"("+Phone.DISPLAY_NAME+" LIKE ? OR "+Phone.NUMBER+" LIKE ?)")+(favorites?" AND "+Phone.STARRED+"=1":""),
                unfiltered?null:new String[]{"%"+query+"%","%"+query+"%"},Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")) {
                java.util.HashSet<Long> contacts=new java.util.HashSet<Long>();while(cursor!=null && cursor.moveToNext() && found.size()<500) {
                    Entry e=new Entry(); e.data=cursor.getLong(0); e.raw=cursor.getLong(1); e.contact=cursor.getLong(2);
                    e.name=cursor.getString(3); e.number=cursor.getString(4); e.starred=cursor.getInt(5)!=0;if((!numeric||ContactKeys.matches(e.name,e.number,query))&&contacts.add(e.contact))found.add(e);
                }
                ui(new Runnable() { public void run() { if(generation!=loadGeneration)return; entries.clear(); entries.addAll(found); show(); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { if(generation!=loadGeneration)return; notice("Agenda nu poate fi citită; verifică permisiunile"); entries.clear(); show(); } }); }
        }});
    }
    private void show() {
        ArrayList<String> labels=new ArrayList<String>();
        for(Entry e:entries) labels.add((e.starred?"★ ":"")+(e.name==null?"Fără nume":e.name)+"\n"+e.number);
        contactsTitle=managingFavorites?"OK schimbă favoritul":favorites?"Favorite":filter.isEmpty()?"Contacte":"Rezultate";
        list(managingFavorites?"OK schimbă favoritul":favorites?"Favorite":filter.isEmpty()?"Contacte":"Rezultate",labels,selected,new Choice() { public void pick(int i) { selected=i;if(managingFavorites){Entry chosen=entries.get(i);try{ContentValues v=new ContentValues();v.put(ContactsContract.Contacts.STARRED,chosen.starred?0:1);getContentResolver().update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,chosen.contact),v,null,null);load();}catch(RuntimeException e){notice("Favoritul nu poate fi modificat");}return;}int slot=getIntent().getIntExtra("speed_slot",0);Entry e=entries.get(i);int favorite=getIntent().getIntExtra("dialer_slot",-1);if(favorite>=0&&favorite<3){getSharedPreferences("dialer-favorites",0).edit().putString("name"+favorite,e.name==null?"":e.name).putString("number"+favorite,e.number).apply();notice("Favoritul "+(favorite+1)+" salvat");finish();}else if(slot>=2&&slot<=9){getSharedPreferences("speed-dial",0).edit().putString("name"+slot,e.name).putString("number"+slot,e.number).apply();notice("Tasta "+slot+" atribuită");finish();}else detail(e); } },"Opțiuni",new Runnable() {
            public void run() { options("Contacte",new String[]{"Contact nou","Caută","Toate contactele","Favorite","Import / export VCF","Apelare rapidă",managingFavorites?"Încheie administrarea":"Administrează favoritele"},new Choice() {
                public void pick(int i) { if(i==6){managingFavorites=!managingFavorites;favorites=false;filter="";load();}else if(i==0) edit(null); else if(i==1) options("Caută",new String[]{"Nume / număr","Cifre T9"},new Choice(){public void pick(int mode){search(mode==1);}}); else if(i==4)startActivity(new Intent(ContactsActivity.this,VCardActivity.class));else if(i==5)startActivity(new Intent(ContactsActivity.this,SpeedDialActivity.class));else { filter=""; favorites=i==3; selected=0; load(); } }
            }); }
        });
    }
    android.graphics.drawable.Drawable rowIcon(String title,int at){if(!title.equals(contactsTitle)||at<0||at>=entries.size())return null;Entry e=entries.get(at);return avatars.get("contact:"+e.contact,ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,e.contact).buildUpon().appendPath("photo").build(),e.name);}
    private void search(boolean numeric) {
        LinearLayout fields=fields(numeric?"Caută T9":"Caută contact"); final EditText term=field(fields,numeric?"Cifre pentru literele numelui":"Nume sau număr",numeric&&!filter.matches("[0-9]*")?"":filter,numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);
        left=new Runnable() { public void run() { filter=term.getText().toString().trim(); selected=0; load(); } };
        back=new Runnable() { public void run() { show(); } }; footer("Caută","","Șterge"); term.requestFocus();
    }
    private void detail(final Entry entry) {
        ++loadGeneration;
        ArrayList<String> actions=new ArrayList<String>(); actions.add("Apelează\n"+entry.number); actions.add("Mesaj SMS"); actions.add("Editează"); actions.add("Șterge contactul");
        actions.add(entry.starred?"Elimină din favorite":"Adaugă la favorite");actions.add("Exportă acest contact VCF");actions.add("Toate numerele / adaugă număr");actions.add("Fotografia contactului");
        list(entry.name==null?"Contact":entry.name,actions,0,new Choice() { public void pick(int i) {
            if(i==0||i==1) chooseNumber(entry,i==0);
            else if(i==2) edit(entry);
            else if(i==5)startActivity(new Intent(ContactsActivity.this,VCardActivity.class).putExtra("contact",entry.contact));
            else if(i==6)numbers(entry);else if(i==7)photoMenu(entry);
            else if(i==4) {try{ContentValues value=new ContentValues();value.put(ContactsContract.Contacts.STARRED,entry.starred?0:1);getContentResolver().update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,entry.contact),value,null,null);load();}catch(RuntimeException denied){notice("Favoritul nu poate fi modificat");}}
            else options("Ștergi contactul?",new String[]{"Păstrează","Șterge definitiv"},new Choice() { public void pick(int c) {
                if(c==1) { try { getContentResolver().delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,entry.contact),null,null); load(); }
                    catch(RuntimeException failure) { notice("Contactul nu poate fi șters"); } }
            }});
        } },"",null);
        back=new Runnable() { public void run() { load(); } };
        final android.widget.LinearLayout page=root;worker.execute(new Runnable(){public void run(){android.graphics.Bitmap image=null;try(java.io.InputStream in=ContactsContract.Contacts.openContactPhotoInputStream(getContentResolver(),ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,entry.contact))){if(in!=null)image=android.graphics.BitmapFactory.decodeStream(in);}catch(Exception ignored){}final android.graphics.Bitmap bitmap=image;ui(new Runnable(){public void run(){if(root==page&&bitmap!=null){ImageView v=new ImageView(ContactsActivity.this);v.setImageBitmap(bitmap);v.setAdjustViewBounds(true);page.addView(v,1,new LinearLayout.LayoutParams(-1,dp(60)));}}});}});
    }
    private void chooseNumber(final Entry e,final boolean call){final ArrayList<ContactBook.Number> numbers=ContactBook.numbers(this,e.contact);if(numbers.isEmpty()){notice("Contact fără număr");return;}if(numbers.size()==1){startActivity(new Intent(this,call?PhoneActivity.class:MessagesActivity.class).putExtra("number",numbers.get(0).value));return;}String[] labels=new String[numbers.size()];for(int i=0;i<labels.length;i++)labels[i]=numbers.get(i).label+" · "+numbers.get(i).value;options("Alege numărul",labels,new Choice(){public void pick(int i){startActivity(new Intent(ContactsActivity.this,call?PhoneActivity.class:MessagesActivity.class).putExtra("number",numbers.get(i).value));}});}
    private void numbers(final Entry e){final ArrayList<ContactBook.Number> all=ContactBook.numbers(this,e.contact);ArrayList<String> labels=new ArrayList<String>();for(ContactBook.Number n:all)labels.add(n.label+"\n"+n.value);list("Numere · "+e.name,labels,0,new Choice(){public void pick(final int i){final ContactBook.Number n=all.get(i);options(n.label,new String[]{"Apelează","SMS","Editează numărul","Șterge numărul"},new Choice(){public void pick(int action){if(action<2)startActivity(new Intent(ContactsActivity.this,action==0?PhoneActivity.class:MessagesActivity.class).putExtra("number",n.value));else if(action==2)numberForm(e,n);else if(all.size()<2)notice("Păstrează cel puțin un număr");else options("Ștergi doar acest număr?",new String[]{"Păstrează","Șterge"},new Choice(){public void pick(int yes){if(yes==1){getContentResolver().delete(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI,n.id),ContactsContract.Data.MIMETYPE+"=?",new String[]{Phone.CONTENT_ITEM_TYPE});numbers(e);}}});}});}},"Adaugă",new Runnable(){public void run(){if(all.size()>=10)notice("Maximum 10 numere editabile aici");else numberForm(e,null);}});back=new Runnable(){public void run(){detail(e);}};}
    private void numberForm(final Entry e,final ContactBook.Number old){LinearLayout f=fields(old==null?"Număr nou":"Editează număr");final EditText value=field(f,"Număr",old==null?"":old.value,InputType.TYPE_CLASS_PHONE);final Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Mobil","Acasă","Serviciu","Altul"}));f.addView(type);left=new Runnable(){public void run(){try{ContactBook.saveNumber(ContactsActivity.this,old==null?e.raw:old.raw,old==null?-1:old.id,value.getText().toString(),new int[]{Phone.TYPE_MOBILE,Phone.TYPE_HOME,Phone.TYPE_WORK,Phone.TYPE_OTHER}[type.getSelectedItemPosition()]);numbers(e);}catch(RuntimeException fail){notice("Număr invalid sau salvare eșuată");}}};back=new Runnable(){public void run(){numbers(e);}};footer("Salvează","Tip","Șterge");value.requestFocus();}
    private void photoMenu(final Entry e){options("Fotografie",new String[]{"Alege din Galerie","Elimină fotografia"},new Choice(){public void pick(int i){if(i==0){photoEntry=e;startActivityForResult(new Intent(ContactsActivity.this,GalleryActivity.class).putExtra("contact_photo",true),91);}else options("Elimini fotografia?",new String[]{"Păstrează","Elimină"},new Choice(){public void pick(int yes){if(yes==1)try{ContactBook.photo(ContactsActivity.this,e.raw,null);detail(e);}catch(Exception fail){notice("Fotografia nu poate fi modificată");}}});}});}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=91||result!=RESULT_OK||data==null||data.getData()==null||photoEntry==null)return;final Entry e=photoEntry;final android.net.Uri uri=data.getData();worker.execute(new Runnable(){public void run(){try{android.graphics.Bitmap b=Appearance.decode(ContactsActivity.this,uri,192);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();b.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out);b.recycle();ContactBook.photo(ContactsActivity.this,e.raw,out.toByteArray());ui(new Runnable(){public void run(){detail(e);}});}catch(Exception|OutOfMemoryError fail){ui(new Runnable(){public void run(){notice("Fotografia nu poate fi salvată");}});}}});}
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
