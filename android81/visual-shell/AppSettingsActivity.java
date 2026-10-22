package ro.leo.visual;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

/** Native package information; permission changes remain in Android's trusted UI. */
public final class AppSettingsActivity extends LeoActivity {
    private boolean all;
    private String current;
    private int pageGeneration;
    protected void onResume(){super.onResume();if(current==null)load();else details(current);}
    private void load(){
        final int generation=++pageGeneration;
        current=null;begin("Aplicații Leo");root.addView(text("Se încarcă…",18));footer("","","Înapoi");
        worker.execute(new Runnable(){public void run(){
            final ArrayList<ApplicationInfo> apps=new ArrayList<>();
            for(ApplicationInfo app:getPackageManager().getInstalledApplications(0))
                if(all || (app.flags&ApplicationInfo.FLAG_SYSTEM)==0 || getPackageManager().getLaunchIntentForPackage(app.packageName)!=null)apps.add(app);
            final java.text.Collator order=java.text.Collator.getInstance(new Locale("ro"));
            Collections.sort(apps,new Comparator<ApplicationInfo>(){public int compare(ApplicationInfo a,ApplicationInfo b){return order.compare(a.loadLabel(getPackageManager()).toString(),b.loadLabel(getPackageManager()).toString());}});
            final ArrayList<String> labels=new ArrayList<>();for(ApplicationInfo app:apps)labels.add(app.loadLabel(getPackageManager()).toString()+(app.enabled?"":" · dezactivată"));
            ui(new Runnable(){public void run(){
                if(generation!=pageGeneration||current!=null)return;
                list(all?"Toate aplicațiile":"Aplicații",labels,0,new Choice(){public void pick(int i){details(apps.get(i).packageName);}},all?"Uzuale":"Toate",new Runnable(){public void run(){all=!all;load();}});
            }});
        }});
    }
    private void details(final String name){
        pageGeneration++;
        try{
            final PackageInfo info=getPackageManager().getPackageInfo(name,PackageManager.GET_PERMISSIONS);current=name;
            String label=info.applicationInfo.loadLabel(getPackageManager()).toString();
            list(label,Arrays.asList("Deschide","Permisiuni · vezi starea","Modifică permisiuni (Android)","Informații pachet","Detalii Android","Dezinstalează","Spațiu aplicație (Android)"),0,new Choice(){public void pick(int i){
                if(i==5){if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0||name.equals(getPackageName())||name.equals("ro.leo.keypad")){notice("Pachet de sistem sau componentă Leo protejată");return;}options("Dezinstalezi "+name+"?",new String[]{"Păstrează","Continuă în Android"},new Choice(){public void pick(int n){if(n==1)try{startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,Uri.parse("package:"+name)));}catch(RuntimeException e){notice("Dezinstalarea nu este disponibilă");}}});}
                else if(i==6)androidDetails(name);
                else if(i==0){Intent intent=getPackageManager().getLaunchIntentForPackage(name);if(intent==null)notice("Pachet fără ecran de lansare");else startActivity(intent);}
                else if(i==1)permissions(info);
                else if(i==2){try{startActivity(new Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra("android.intent.extra.PACKAGE_NAME",name));}catch(RuntimeException missing){androidDetails(name);}}
                else if(i==3)page("Informații pachet",name+"\nVersiune · "+info.versionName+" ("+info.versionCode+")\n"+((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0?"Aplicație de sistem":"Aplicație utilizator")+"\nAPK · "+android.text.format.Formatter.formatFileSize(AppSettingsActivity.this,codeBytes(info.applicationInfo)));
                else androidDetails(name);
            }},"",null);
            back=new Runnable(){public void run(){load();}};
        }catch(PackageManager.NameNotFoundException gone){notice("Aplicația nu mai este instalată");load();}
    }
    static long codeBytes(ApplicationInfo a){long total=new java.io.File(a.sourceDir).length();if(a.splitSourceDirs!=null)for(String path:a.splitSourceDirs)total+=new java.io.File(path).length();return total;}
    private void androidDetails(String name){try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+name)));}catch(RuntimeException missing){notice("Pagina Android este indisponibilă");}}
    private void permissions(PackageInfo info){
        StringBuilder lines=new StringBuilder();
        if(info.requestedPermissions!=null)for(int i=0;i<info.requestedPermissions.length;i++)try{
            PermissionInfo p=getPackageManager().getPermissionInfo(info.requestedPermissions[i],0);
            if((p.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)!=PermissionInfo.PROTECTION_DANGEROUS)continue;
            boolean granted=info.requestedPermissionsFlags!=null && (info.requestedPermissionsFlags[i]&PackageInfo.REQUESTED_PERMISSION_GRANTED)!=0;
            lines.append(granted?"✓ ":"○ ").append(p.loadLabel(getPackageManager())).append('\n');
        }catch(PackageManager.NameNotFoundException ignored){}
        page("Permisiuni",lines.length()==0?"Nicio permisiune la execuție solicitată.":lines.toString());
    }
    private void page(String title,String value){begin(title);ScrollView scroll=new ScrollView(this);scroll.addView(text(value,17));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){details(current);}};footer("","","Înapoi");}
}
