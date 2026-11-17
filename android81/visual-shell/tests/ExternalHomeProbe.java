package ro.leo.visual;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import java.io.FileOutputStream;

/** Runs under the test APK's own UID, outside target instrumentation. */
public final class ExternalHomeProbe extends Activity {
    public void onCreate(Bundle state){
        super.onCreate(state);boolean denied=false;
        try{startActivity(new Intent().setComponent(new ComponentName("ro.leo.visual","ro.leo.visual.InternalHome")).putExtra("open_app",18));}
        catch(SecurityException expected){denied=true;}
        try(FileOutputStream out=openFileOutput("home-probe.txt",0)){
            StringBuilder report=new StringBuilder("aliasDenied="+denied+"\nuid="+android.os.Process.myUid()+"\n");
            android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
            for(android.accessibilityservice.AccessibilityServiceInfo service:manager.getInstalledAccessibilityServiceList())report.append("installedAccessibility=").append(service.getId()).append('\n');
            for(android.accessibilityservice.AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(-1))report.append("enabledAccessibility=").append(service.getId()).append('\n');
            for(android.content.pm.ResolveInfo service:getPackageManager().queryIntentServices(new Intent("android.accessibilityservice.AccessibilityService"),android.content.pm.PackageManager.GET_META_DATA))report.append("declaredAccessibility=").append(service.serviceInfo.packageName).append('/').append(service.serviceInfo.name).append('\n');
            out.write(report.toString().getBytes("UTF-8"));
        }catch(Exception error){throw new RuntimeException(error);}
        startActivity(new Intent().setComponent(new ComponentName("ro.leo.visual","ro.leo.visual.MainActivity")).putExtra("open_app",18).putExtra("open_menu",true).putExtra("edit_home",true));
        finish();
    }
}
