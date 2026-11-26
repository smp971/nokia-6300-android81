package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Native owner control panel, reachable without a PC or a running network listener. */
public final class CompanionActivity extends PocketActivity {
    private CompanionAuth access;
    private boolean dashboard;private int mainSelection;
    void render(){showHome();}
    void onListSelection(String title,int index){if(dashboard)mainSelection=index;}
    private final BroadcastReceiver changed=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(dashboard)showHome();}};
    protected void onCreate(Bundle state){super.onCreate(state);access=new CompanionAuth(this);}
    protected void onResume(){super.onResume();registerReceiver(changed,new IntentFilter(CompanionService.CHANGED));showHome();}
    protected void onPause(){unregisterReceiver(changed);super.onPause();}
    private String yes(boolean value){return L.t(value?"Da":"Nu");}
    private void showHome(){
        dashboard=true;if(access==null)access=new CompanionAuth(this);CompanionService live=CompanionService.instance;
        String state=live==null?L.t("Oprit"):live.status;
        String location=live!=null&&!live.address.isEmpty()?L.t("Adresă WebUI")+"\n"+live.address:L.t("Stare")+" · "+state;
        int selected=mainSelection;
        list("KeyShell WebUI",Arrays.asList(location,L.t("Activare WebUI")+" · "+yes(CompanionSettings.enabled(this)),L.t("Pornire automată")+" · "+yes(CompanionSettings.auto(this)),L.t("Tema WebUI")+" · "+L.t(CompanionSettings.THEMES[CompanionSettings.theme(this)]),L.t("Parolă administrator"),L.t("Utilizatori"),L.t("Funcționalități"),L.t("Fișiere primite"),L.t("Pachete și revenire")),selected,new Choice(){public void pick(int row){
            dashboard=false;
            if(row==0)address();
            else if(row==1){if(!access.configured())newUser("admin","admin");else{CompanionSettings.enabled(CompanionActivity.this,!CompanionSettings.enabled(CompanionActivity.this));showHome();}}
            else if(row==2){getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("auto-start",!CompanionSettings.auto(CompanionActivity.this)).apply();showHome();}
            else if(row==3)options(L.t("Tema WebUI"),L.array(CompanionSettings.THEMES),new Choice(){public void pick(int theme){getSharedPreferences(CompanionSettings.PREFS,0).edit().putInt("web-theme",theme).apply();showHome();}});
            else if(row==4){try{JSONArray users=access.users();String name="admin";for(int i=0;i<users.length();i++)if(users.getJSONObject(i).getString("role").equals("admin")){name=users.getJSONObject(i).getString("username");break;}if(users.length()==0)newUser(name,"admin");else password(name);}catch(Exception e){problem(e);}}
            else if(row==5)users();else if(row==6)features();
            else if(row==7)startActivity(new Intent(CompanionActivity.this,FilesActivity.class).putExtra("folder",CompanionServer.folder(CompanionActivity.this).getPath()));
            else startActivity(new Intent(CompanionActivity.this,UpdatesActivity.class));
        }},"",null);
    }
    private void address(){
        CompanionService live=CompanionService.instance;
        String info=live==null?L.t("Activează WebUI pentru acces de pe PC."):live.address.isEmpty()?live.status:live.address+"\n\nSHA-256 TLS\n"+live.fingerprint;
        info+="\n\n"+L.t("Conectare cu utilizator și parolă. Serverul rămâne în fundal și revine când este disponibil Wi-Fi-ul.")+"\n\n"+L.t("La prima conectare, verifică amprenta certificatului în browser și acceptă certificatul local.");
        page("KeyShell WebUI",info,new Runnable(){public void run(){showHome();}},"",null);
    }
    private EditText secret(LinearLayout form,String label){EditText field=field(form,L.t(label),"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(72)});field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);return field;}
    private void newUser(String initial,final String role){
        dashboard=false;LinearLayout form=fields(L.t("Adaugă utilizator"));final EditText name=field(form,L.t("Utilizator"),initial,InputType.TYPE_CLASS_TEXT),value=secret(form,"Parolă nouă"),confirm=secret(form,"Confirmă parola");
        left=new Runnable(){public void run(){final String username=name.getText().toString(),password=value.getText().toString();try{CompanionAuth.password(password);if(!password.equals(confirm.getText().toString())||!CompanionAuth.username(username))throw new IllegalArgumentException(L.t("Verifică utilizatorul și confirmarea parolei"));value.setText("");confirm.setText("");work(new Work(){public void run()throws Exception{access.add(username,password,role);}},new Runnable(){public void run(){users();}});}catch(Exception e){problem(e);}}};
        back=new Runnable(){public void run(){showHome();}};footer(L.t("Salvează"),"",L.t("Înapoi"));name.requestFocus();
    }
    private void password(final String username){
        dashboard=false;LinearLayout form=fields(L.t("Parolă")+" · "+username);final EditText value=secret(form,"Parolă nouă"),confirm=secret(form,"Confirmă parola");
        left=new Runnable(){public void run(){final String password=value.getText().toString();try{CompanionAuth.password(password);if(!password.equals(confirm.getText().toString()))throw new IllegalArgumentException(L.t("Parolele nu coincid"));value.setText("");confirm.setText("");work(new Work(){public void run()throws Exception{access.reset(username,password);}},new Runnable(){public void run(){users();}});}catch(Exception e){problem(e);}}};
        back=new Runnable(){public void run(){users();}};footer(L.t("Salvează"),"",L.t("Înapoi"));value.requestFocus();
    }
    private void work(final Work action,final Runnable success){
        final Exception[] error={null};job(L.t("Se salvează"),new Work(){public void run(){try{action.run();}catch(Exception e){error[0]=e;}}},new Runnable(){public void run(){if(error[0]!=null)problem(error[0]);else success.run();}});
    }
    private void users(){
        dashboard=false;
        try{final JSONArray users=access.users();ArrayList<String> rows=new ArrayList<String>();for(int i=0;i<users.length();i++){JSONObject user=users.getJSONObject(i);rows.add(user.getString("username")+" · "+L.t(user.getString("role").equals("admin")?"Administrator":"Doar citire"));}rows.add(L.t("Adaugă utilizator"));
            list(L.t("Utilizatori WebUI"),rows,0,new Choice(){public void pick(final int at){try{
                if(at==users.length()){options(L.t("Rol utilizator"),new String[]{L.t("Administrator"),L.t("Doar citire")},new Choice(){public void pick(int role){newUser("",role==0?"admin":"viewer");}});return;}
                final String name=users.getJSONObject(at).getString("username");options(name,new String[]{L.t("Schimbă parola"),L.t("Șterge utilizatorul")},new Choice(){public void pick(int action){if(action==0)password(name);else options(L.t("Ștergi utilizatorul?")+" · "+name,new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice(){public void pick(int answer){if(answer==1)try{access.remove(name);users();}catch(Exception e){problem(e);}}});}});
            }catch(Exception e){problem(e);}}},"",null);back=new Runnable(){public void run(){showHome();}};
        }catch(Exception e){problem(e);}
    }
    private void features(){
        dashboard=false;final String[] keys={"appearance","layout","files"};String[] names={L.t("Aspectul telefonului"),L.t("Aplicații și foldere"),L.t("Transfer de fișiere")};ArrayList<String> rows=new ArrayList<String>();
        for(int i=0;i<keys.length;i++)rows.add(names[i]+" · "+yes(CompanionSettings.feature(this,keys[i])));
        list(L.t("Funcționalități WebUI"),rows,0,new Choice(){public void pick(int at){getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("feature-"+keys[at],!CompanionSettings.feature(CompanionActivity.this,keys[at])).apply();features();}},"",null);back=new Runnable(){public void run(){showHome();}};
    }
}
