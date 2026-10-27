package ro.leo.visual;
import ro.leo.i18n.L;
import android.os.Bundle;import android.widget.*;
public final class ContextHelpActivity extends LeoActivity {
 protected void onCreate(Bundle b){super.onCreate(b);String page=getIntent().getStringExtra("page"),controls=getIntent().getStringExtra("controls");begin(L.t("Comenzile paginii"));ScrollView scroll=new ScrollView(this);scroll.addView(text((controls==null||controls.isEmpty()?"":controls+"\n\n")+ContextHelp.text(page),16));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.setFocusableInTouchMode(true);scroll.requestFocus();footer("",L.t("Derulează"),L.t("Înapoi"));}
}
