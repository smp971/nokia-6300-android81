package ro.leo.visual;
import android.app.Activity;import android.view.*;
/** One native SystemUI bar for Leo windows; real controllers retain their data. */
final class SystemChrome {
 static int darkFlags(int flags){return flags&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
 static void apply(Activity a,Appearance.Palette p){Window w=a.getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);w.setStatusBarColor(p.bar);w.setNavigationBarColor(p.bar);View decor=w.getDecorView();decor.setSystemUiVisibility(darkFlags(decor.getSystemUiVisibility()));}
 static boolean volte(){return ImsStatus.registered(0)||ImsStatus.registered(1);}
}
