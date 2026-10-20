package ro.leo.visual;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Keypad reader over the installed AOSP Email engine; no credential handling. */
public final class EmailActivity extends LeoActivity {
    private static final Uri BASE=Uri.parse("content://com.android.email.provider");
    private int generation;
    private long account,mailbox;
    private String accountName="",mailboxName="",accountEmail="";
    private EditText to,subject,message;
    protected void onCreate(Bundle state){super.onCreate(state);if(state!=null&&state.getBoolean("compose")){compose();to.setText(state.getString("to",""));subject.setText(state.getString("subject",""));message.setText(state.getString("message",""));}else accounts();}
    protected void onSaveInstanceState(Bundle state){if(to!=null){state.putBoolean("compose",true);state.putString("to",to.getText().toString());state.putString("subject",subject.getText().toString());state.putString("message",message.getText().toString());}super.onSaveInstanceState(state);}
    protected void onDestroy(){generation++;super.onDestroy();}
    protected void onRestart(){super.onRestart();if(to==null)accounts();}
    private Uri uri(String path){return BASE.buildUpon().appendPath(path).appendQueryParameter("limit","100").build();}
    private void loading(String title){begin(title);root.addView(text("Se încarcă…",18),new LinearLayout.LayoutParams(-1,0,1));footer("","","Înapoi");}
    private void accounts(){
        to=subject=message=null;final int token=++generation;loading("Email Leo");
        worker.execute(new Runnable(){public void run(){
            final ArrayList<String> labels=new ArrayList<String>(),emails=new ArrayList<String>();final ArrayList<Long> ids=new ArrayList<Long>();
            try(Cursor c=getContentResolver().query(uri("account"),new String[]{"_id","displayName","emailAddress"},null,null,"_id ASC")){
                while(c!=null&&c.moveToNext()&&ids.size()<100){ids.add(c.getLong(0));emails.add(safe(c.getString(2)));labels.add(safe(c.getString(1))+"\n"+safe(c.getString(2)));}
                ui(new Runnable(){public void run(){if(token!=generation)return;
                    if(ids.isEmpty())welcome();else{list("Conturi email",labels,0,new Choice(){public void pick(int i){account=ids.get(i);accountEmail=emails.get(i);accountName=labels.get(i).split("\n")[0];folders();}},"Opțiuni",new Runnable(){public void run(){menu();}});}
                }});
            }catch(RuntimeException denied){ui(new Runnable(){public void run(){if(token==generation)unavailable();}});}
        }});
    }
    private void welcome(){
        list("Email Leo",Arrays.asList("Adaugă un cont\nMeniu: configurare manuală","Scrie un email\nEditor Leo pentru taste","Despre Email Leo"),0,new Choice(){public void pick(int i){if(i==0)androidEmail();else if(i==1)compose();else about();}},"Opțiuni",new Runnable(){public void run(){menu();}});
    }
    private void menu(){options("Email",new String[]{"Actualizează lista locală","Scrie un email","Configurare cont","Sincronizează contul","Despre"},new Choice(){public void pick(int i){if(i==0)accounts();else if(i==1)compose();else if(i==2)androidEmail();else if(i==3)sync();else about();}});}
    private void sync(){if(accountEmail.isEmpty()){notice("Alege întâi un cont");return;}try{for(android.accounts.Account a:android.accounts.AccountManager.get(this).getAccountsByType("com.android.email"))if(a.name.equalsIgnoreCase(accountEmail)){Bundle extras=new Bundle();extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL,true);extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED,true);ContentResolver.requestSync(a,"com.android.email.provider",extras);notice("Sincronizare cerută; actualizează lista după terminare");return;}notice("Contul IMAP de sincronizare nu este disponibil");}catch(SecurityException denied){notice("Accesul la conturi nu este permis");}}
    private void androidEmail(){
        Intent launch=getPackageManager().getLaunchIntentForPackage("com.android.email");
        try{if(launch!=null)startActivity(launch);else notice("Clientul Email Android lipsește");}catch(RuntimeException e){notice("Clientul Email nu poate fi deschis");}
    }
    private void unavailable(){list("Email Leo",Arrays.asList("Deschide Email Android","Reîncearcă accesul la conturi"),0,new Choice(){public void pick(int i){if(i==0)androidEmail();else accounts();}},"",null);notice("Accesul la motorul Email nu este disponibil");}
    private void about(){
        begin("Despre Email Leo");ScrollView scroll=new ScrollView(this);scroll.addView(text("Conturile și sincronizarea sunt gestionate de Email Android. Leo afișează folderele și mesajele deja descărcate.\n\nÎn configurarea Android: sus/jos schimbă câmpul, Meniu oferă Continuă și Configurare manuală. În editorul Android, Meniu oferă trimiterea cu confirmare și salvarea ciornei.\n\nCompatibilitatea autentificării depinde de furnizor. Nu sunt încă validate conturi reale. Parolele se introduc doar în Email Android.",17));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){accounts();}};footer("","Derulare","Înapoi");scroll.setFocusable(true);scroll.requestFocus();
    }
    private void folders(){
        final int token=++generation;loading("Foldere email");back=new Runnable(){public void run(){accounts();}};
        worker.execute(new Runnable(){public void run(){
            final ArrayList<Long> ids=new ArrayList<Long>();final ArrayList<String> labels=new ArrayList<String>();
            try(Cursor c=getContentResolver().query(uri("mailbox"),new String[]{"_id","displayName","type"},"accountKey=?",new String[]{""+account},"type ASC, displayName ASC")){
                while(c!=null&&c.moveToNext()&&ids.size()<100){ids.add(c.getLong(0));int kind=c.getInt(2);labels.add(kind==0?"Primite":kind==3?"Ciorne":kind==4?"În curs de trimitere":kind==5?"Trimise":kind==6?"Coș":safe(c.getString(1)));}
                ui(new Runnable(){public void run(){if(token!=generation)return;list(accountName,labels,0,new Choice(){public void pick(int i){mailbox=ids.get(i);mailboxName=labels.get(i);messages();}},"Opțiuni",new Runnable(){public void run(){menu();}});back=new Runnable(){public void run(){accounts();}};}});
            }catch(RuntimeException e){ui(new Runnable(){public void run(){if(token==generation){welcome();notice("Folderele nu pot fi citite");}}});}
        }});
    }
    private void messages(){
        final int token=++generation;loading(mailboxName);back=new Runnable(){public void run(){folders();}};
        worker.execute(new Runnable(){public void run(){
            final ArrayList<Long> ids=new ArrayList<Long>();final ArrayList<String> labels=new ArrayList<String>();
            try(Cursor c=getContentResolver().query(uri("message"),new String[]{"_id","displayName","subject","flagRead"},"accountKey=? AND mailboxKey=?",new String[]{""+account,""+mailbox},"timeStamp DESC")){
                while(c!=null&&c.moveToNext()&&ids.size()<100){ids.add(c.getLong(0));labels.add((c.getInt(3)==0?"● ":"")+safe(c.getString(1))+"\n"+(safe(c.getString(2)).isEmpty()?"Fără subiect":c.getString(2)));}
                ui(new Runnable(){public void run(){if(token!=generation)return;list(mailboxName,labels,0,new Choice(){public void pick(int i){read(ids.get(i),labels.get(i));}},"Actualizează",new Runnable(){public void run(){messages();}});back=new Runnable(){public void run(){folders();}};}});
            }catch(RuntimeException e){ui(new Runnable(){public void run(){if(token==generation){welcome();notice("Mesajele nu pot fi citite");}}});}
        }});
    }
    private String body(String link)throws IOException{
        Uri u=Uri.parse(link);if(!"content".equals(u.getScheme())||!"com.android.email.provider".equals(u.getAuthority()))throw new IOException();
        try(InputStream input=getContentResolver().openInputStream(u);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            if(input==null)return "";byte[] buf=new byte[4096];int n;while((n=input.read(buf))!=-1){if(output.size()+n>262144)throw new IOException("Mesaj prea mare");output.write(buf,0,n);}return new String(output.toByteArray(),"UTF-8");
        }
    }
    private void read(final long id,final String title){
        final int token=++generation;loading("Email");back=new Runnable(){public void run(){messages();}};
        worker.execute(new Runnable(){public void run(){String value="Mesajul nu este descărcat. Deschide Email Android pentru sincronizare.";
            try(Cursor c=getContentResolver().query(uri("body"),new String[]{"textContentUri","htmlContentUri"},"messageKey=?",new String[]{""+id},null)){
                if(c!=null&&c.moveToFirst()){String plain=c.getString(0),html=c.getString(1);String loaded="";if(plain!=null&&!plain.isEmpty())loaded=body(plain);if(loaded.isEmpty()&&html!=null&&!html.isEmpty())loaded=Html.fromHtml(body(html),Html.FROM_HTML_MODE_LEGACY).toString();if(!loaded.isEmpty())value=loaded;}
                ContentValues seen=new ContentValues();seen.put("flagRead",1);getContentResolver().update(Uri.parse(BASE+"/syncedMessage/"+id),seen,"accountKey=?",new String[]{""+account});
            }catch(Exception e){value="Conținut indisponibil sau prea mare. Deschide Email Android.";}
            final String content=value;ui(new Runnable(){public void run(){if(token!=generation)return;begin("Email");ScrollView scroll=new ScrollView(EmailActivity.this);scroll.addView(text(title+"\n\n"+content,18));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));left=new Runnable(){public void run(){androidEmail();}};back=new Runnable(){public void run(){messages();}};footer("Email Android","Derulare","Înapoi");scroll.setFocusable(true);scroll.requestFocus();}});
        }});
    }
    private void compose(){
        generation++;LinearLayout fields=fields("Email nou");to=field(fields,"Către","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);subject=field(fields,"Subiect","",InputType.TYPE_CLASS_TEXT);message=field(fields,"Mesaj","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        left=new Runnable(){public void run(){
            final String address=to.getText().toString().trim();if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){notice("Introdu o adresă email validă");return;}
            options("Continuă în Email Android?",new String[]{"Înapoi la editor","Deschide pentru confirmare"},new Choice(){public void pick(int i){if(i==1){Intent send=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+Uri.encode(address,"@"))).setPackage("com.android.email");send.putExtra(Intent.EXTRA_SUBJECT,subject.getText().toString());send.putExtra(Intent.EXTRA_TEXT,message.getText().toString());try{startActivity(send);}catch(RuntimeException e){notice("Editorul Android nu este disponibil");}}}});
        }};footer("Continuă","Editor","Șterge");to.requestFocus();
    }
    private static String safe(String value){return value==null?"":value;}
}
