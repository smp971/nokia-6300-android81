package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.net.Uri;import android.os.Bundle;import java.io.*;import java.util.*;
public final class UpdatesActivity extends LeoActivity {
 private boolean busy,registered,appPage,inspectionPage;private final BroadcastReceiver changed=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(!busy)refreshPage();}};
 protected void onCreate(Bundle b){super.onCreate(b);String file=getIntent().getStringExtra("file");if(file!=null)inspect(new File(file));else home();}
 protected void onResume(){super.onResume();registerReceiver(changed,new IntentFilter(UpdateEngine.CHANGED));registered=true;if(!busy)refreshPage();}
 protected void onPause(){if(registered)unregisterReceiver(changed);registered=false;super.onPause();}
 private void refreshPage(){if(inspectionPage)return;if(appPage)show();else home();}
 private void home(){inspectionPage=false;appPage=false;String version="";try{android.content.pm.PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0);version=p.versionName+" / "+p.versionCode;}catch(Exception ignored){}list(L.t("Actualizări Leo"),Arrays.asList(L.t("Versiune instalată")+" · "+version,L.t("Actualizează aplicațiile Leo"),L.t("Verifică pachet ROM pentru recovery"),L.t("Repornește în Leo Recovery"),L.t("Despre instalare și revenire")),0,new Choice(){public void pick(int at){if(at==1)show();else if(at==2)startActivityForResult(new Intent(UpdatesActivity.this,FilesActivity.class).putExtra("pick_update",true),93);else if(at==3)recoveryConfirm();else if(at==4){begin(L.t("Instalare și revenire"));android.widget.ScrollView scroll=new android.widget.ScrollView(UpdatesActivity.this);scroll.addView(text(L.t("Actualizarea aplicațiilor păstrează un set precedent de APK-uri. Datele nu sunt restaurate.\n\nLeo Recovery acceptă numai pachetele aprobate din catalogul său. Instalarea ROM actuală șterge datele interne și revine la baza publicată, fără modificările UI ulterioare. Selectarea și confirmarea instalării se fac manual în recovery."),16));root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){home();}};footer("","",L.t("Înapoi"));}}},"",null);}
 private void recoveryConfirm(){
  options(L.t("Repornești în Leo Recovery?"),new String[]{L.t("Anulează"),L.t("Continuă · fără instalare automată")},new Choice(){public void pick(int yes){
   if(yes!=1)return;busy=true;inspectionPage=true;loading(L.t("Verific Leo Recovery"));
   worker.execute(new Runnable(){public void run(){
    try{RecoveryPackage.reboot(UpdatesActivity.this);}
    catch(final Exception e){ui(new Runnable(){public void run(){busy=false;errorPage(L.t("Recovery indisponibil"),e.getMessage(),new Runnable(){public void run(){home();}});back=new Runnable(){public void run(){home();}};}});}
   }});
  }});
 }
 private void inspectRom(final File file){
  inspectionPage=true;busy=true;loading(L.t("Verific pachetul ROM"));
  worker.execute(new Runnable(){public void run(){
   try{
    RecoveryPackage.verify(file);final boolean ready=RecoveryPackage.onCard(UpdatesActivity.this,file)&&RecoveryPackage.recoveryInstalled();
    ui(new Runnable(){public void run(){
     busy=false;begin(L.t("Pachet ROM verificat"));
     root.addView(text(ready?L.t("SHA-256 corespunde catalogului. În recovery alege Install Leo, verifică backupul și confirmă manual. Instalarea șterge datele interne."):L.t("Pachet aprobat. Pentru instalare trebuie pus pe card în LeoRecovery/packages, cu numele original, și este necesar Leo Recovery compatibil."),16),new android.widget.LinearLayout.LayoutParams(-1,0,1));
     left=ready?new Runnable(){public void run(){recoveryConfirm();}}:null;back=new Runnable(){public void run(){home();}};footer(ready?L.t("Recovery"):"","",L.t("Înapoi"));
    }});
   }catch(final Exception e){ui(new Runnable(){public void run(){busy=false;errorPage(L.t("Pachet ROM refuzat"),e.getMessage(),new Runnable(){public void run(){home();}});back=new Runnable(){public void run(){home();}};}});}
  }});
 }
 private void show(){inspectionPage=false;appPage=true;SharedPreferences p=UpdateEngine.prefs(this);list(L.t("Actualizări Leo"),Arrays.asList(L.t("Alege pachetul ZIP local"),L.t("Instalează pachetul verificat · ")+p.getString("version",L.t("niciunul")),L.t("Revino la APK-urile precedente · ")+p.getString("previous-label",L.t("niciun set")),p.getString("message",L.t("Nicio actualizare în curs")),L.t("Verifică sesiunea întreruptă"),L.t("Anulează sesiunea în curs"),L.t("Permite instalările din Leo · Android"),L.t("Ce păstrează revenirea?"),ShellHealth.status(this)),0,new Choice(){public void pick(int n){if(n==0)startActivityForResult(new Intent(UpdatesActivity.this,FilesActivity.class).putExtra("pick_update",true),92);else if(n==1||n==2)confirm(n==2);else if(n==4){busy=true;worker.execute(new Runnable(){public void run(){try{UpdateEngine.reconcile(UpdatesActivity.this);}catch(Exception e){noticeUi(L.t("Sesiunea nu poate fi verificată"));}ui(new Runnable(){public void run(){busy=false;show();}});}});}else if(n==5)options(L.t("Anulezi sesiunea?"),new String[]{L.t("Păstrează"),L.t("Anulează")},new Choice(){public void pick(int yes){if(yes==1){worker.execute(new Runnable(){public void run(){UpdateEngine.cancel(UpdatesActivity.this);ui(new Runnable(){public void run(){show();}});}});}}});else if(n==6)try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){notice(L.t("Pagina Android indisponibilă"));}else if(n==8)startActivity(new Intent(UpdatesActivity.this,RecoveryCenterActivity.class));else if(n==7)notice(L.t("Se păstrează un singur set de APK-uri precedente. Datele aplicațiilor nu sunt restaurate. Revenirea la un cod mai mic necesită build debuggable; confirmarea rămâne în Android."));}},"",null);}
 private void inspect(final File file){busy=true;begin(L.t("Verific pachetul Leo"));root.addView(text(L.t("Verific manifestul, SHA-256, numele pachetelor și certificatele…"),17));footer("","",L.t("Înapoi"));worker.execute(new Runnable(){public void run(){try{final String version=UpdateEngine.stage(UpdatesActivity.this,file);noticeUi(L.t("Pachet verificat · ")+version);}catch(final Exception e){noticeUi(L.t("Pachet refuzat: ")+e.getMessage());}ui(new Runnable(){public void run(){busy=false;show();}});}});}
 private void noticeUi(final String value){ui(new Runnable(){public void run(){notice(value);}});}
 private void confirm(final boolean rollback){options(rollback?L.t("Revii la ")+UpdateEngine.prefs(this).getString("previous-label",L.t("copia precedentă"))+"?":L.t("Instalezi Leo ")+UpdateEngine.prefs(this).getString("version","")+"?",new String[]{L.t("Anulează"),L.t("Continuă · confirmare Android")},new Choice(){public void pick(int yes){if(yes!=1)return;busy=true;begin(L.t("Pregătesc instalarea"));worker.execute(new Runnable(){public void run(){try{UpdateEngine.begin(UpdatesActivity.this,rollback);}catch(Exception e){noticeUi(e.getMessage()==null?L.t("Actualizare indisponibilă"):e.getMessage());}ui(new Runnable(){public void run(){busy=false;show();}});}});}});}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result==RESULT_OK&&data!=null&&data.getStringExtra("file")!=null){if(request==92)inspect(new File(data.getStringExtra("file")));else if(request==93)inspectRom(new File(data.getStringExtra("file")));}}
}
