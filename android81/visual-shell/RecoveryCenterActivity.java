package ro.leo.visual;
import ro.leo.i18n.L;import android.content.*;import android.os.Bundle;import java.util.*;
public final class RecoveryCenterActivity extends LeoActivity {
 protected void onCreate(Bundle b){super.onCreate(b);list(L.t("Recuperare Leo"),Arrays.asList(ShellHealth.status(RecoveryCenterActivity.this),L.t("Actualizări și revenire"),L.t("Diagnostic Leo"),L.t("Pornește Home")),0,new Choice(){public void pick(int n){if(n==1)startActivity(new Intent(RecoveryCenterActivity.this,UpdatesActivity.class));else if(n==2)startActivity(new Intent(RecoveryCenterActivity.this,DiagnosticActivity.class));else if(n==3){getSharedPreferences("shell-health",0).edit().putBoolean("healthy",true).putInt("failures",0).commit();startActivity(new Intent(RecoveryCenterActivity.this,MainActivity.class));finish();}}},"",null);}
}
