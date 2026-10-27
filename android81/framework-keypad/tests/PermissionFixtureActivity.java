package ro.leo.systemtests;

import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.widget.TextView;

/** Requests a fixture permission solely to test Deny. Never reads calendar data. */
public final class PermissionFixtureActivity extends Activity {
    static volatile boolean received,denied;
    TextView label;
    public void onCreate(Bundle state) {
        super.onCreate(state);received=false;denied=false;
        label=new TextView(this);label.setText("Local permission fixture");setContentView(label);
        requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},17);
    }
    public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==17){denied=results.length==1&&results[0]==PackageManager.PERMISSION_DENIED;
            received=true;label.setText(denied?"PERMISSION DENIED":"UNEXPECTED GRANT");}
    }
}
