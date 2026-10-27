package ro.leo.systemtests;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Native APK integration: focuses controls, opens only its own silent notification. */
public class NativeSystemUiTests extends Instrumentation {
    int checks;
    boolean shadeOnly;
    UiAutomation automation() {
        return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    }
    void check(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
        checks++;
    }
    void key(int code) {
        long now=SystemClock.uptimeMillis();
        automation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0,0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD),true);
        automation().injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0,0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,0,KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD),true);
        SystemClock.sleep(650);
    }
    Rect outline() {
        android.graphics.Bitmap shot=automation().takeScreenshot();
        if(shot==null) return new Rect();
        Rect bounds=new Rect();
        try {
            int footer=Math.round(24*getTargetContext().getResources().getDisplayMetrics().density);
            for(int y=0;y<shot.getHeight()-footer;y++) for(int x=0;x<shot.getWidth();x++) {
                // Notification rows animate alpha. The mint chroma ratio remains
                // stable when composited over their neutral card and shadow.
                int color=shot.getPixel(x,y), r=(color>>16)&255, g=(color>>8)&255, b=color&255;
                int green=g-r,blue=b-r;
                if(green>=20 && blue>0 && Math.abs(blue*126-green*102)<green*6)
                    bounds.union(x,y,x+1,y+1);
            }
        } finally { shot.recycle(); }
        return bounds;
    }
    boolean same(Rect a,Rect b) {
        return !a.isEmpty() && Math.abs(a.left-b.left)<=3 && Math.abs(a.right-b.right)<=3
                && Math.abs(a.top-b.top)<=3 && Math.abs(a.bottom-b.bottom)<=24;
    }
    AccessibilityNodeInfo findText(AccessibilityNodeInfo node,String text,int[] visited) {
        if(node==null || ++visited[0]>512) return null;
        CharSequence value=node.getText(),description=node.getContentDescription();
        if((value!=null && value.toString().contains(text))
                || (description!=null && description.toString().contains(text))) return node;
        for(int n=0;n<node.getChildCount();n++) {
            AccessibilityNodeInfo found=findText(node.getChild(n),text,visited);
            if(found!=null) return found;
        }
        return null;
    }
    void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = automation().executeShellCommand(command);
                java.io.InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] bytes = new byte[1024];
            while (in.read(bytes) != -1) { }
        }
    }
    AccessibilityNodeInfo root(String pkg) {
        for (int n = 0; n < 40; n++) {
            AccessibilityNodeInfo root = automation().getRootInActiveWindow();
            if (root != null && pkg.contentEquals(root.getPackageName())) return root;
            for (android.view.accessibility.AccessibilityWindowInfo window : automation().getWindows()) {
                root = window.getRoot();
                if (root != null && pkg.contentEquals(root.getPackageName())) return root;
            }
            SystemClock.sleep(150);
        }
        return null;
    }
    boolean waitText(String pkg,String text) {
        for(int n=0;n<40;n++) {
            AccessibilityNodeInfo page=root(pkg);
            if(page!=null && findText(page,text,new int[]{0})!=null) return true;
            SystemClock.sleep(150);
        }
        return false;
    }
    void capture(String name) throws Exception {
        android.graphics.Bitmap shot=automation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(
                new java.io.File(getTargetContext().getFilesDir(),name))) {
            shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        } finally { shot.recycle(); }
    }
    public void onCreate(Bundle args) { shadeOnly = args != null && "true".equals(args.getString("shadeOnly")); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        NotificationManager manager = (NotificationManager)
                getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE);
        AccessibilityServiceInfo original = automation().getServiceInfo();
        int originalFlags = original.flags;
        try {
            original.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation().setServiceInfo(original);
            if (!shadeOnly) {
            shell("am start -W -f 0x14000000 -a android.settings.SETTINGS");
            check(root("com.android.settings") != null, "native Settings opens");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            Rect first = outline();
            check(!first.isEmpty(), "Settings obtains visible keypad selection");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            Rect next = outline();
            check(!next.isEmpty() && !first.equals(next), "Down moves the visible Settings selection: "
                    + first + " -> " + next);
            capture("native-settings.png");
            key(KeyEvent.KEYCODE_MENU);
            AccessibilityNodeInfo menu = root("com.android.settings");
            check(menu != null && !menu.findAccessibilityNodeInfosByText("Android · Leo").isEmpty(),
                    "physical softkey opens native Android/Leo actions");
            key(KeyEvent.KEYCODE_BACK);
            }
            startActivitySync(new Intent(getTargetContext(),KeypadFixtureActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            waitForIdleSync();
            manager.createNotificationChannel(new NotificationChannel("keypad-probe",
                    "Leo keypad probe", NotificationManager.IMPORTANCE_LOW));
            Intent open = new Intent(getTargetContext(), KeypadFixtureActivity.class)
                    .putExtra("notification_probe",true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            manager.notify(901, new Notification.Builder(getTargetContext(), "keypad-probe")
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Leo keypad probe")
                    .setContentText("Silent local test").setContentIntent(PendingIntent.getActivity(
                            getTargetContext(), 901, open, PendingIntent.FLAG_CANCEL_CURRENT)).build());
            shell("cmd statusbar expand-notifications");
            AccessibilityNodeInfo shade = root("com.android.systemui");
            check(shade != null, "native notification shade opens");
            SystemClock.sleep(700);
            shade = root("com.android.systemui");
            check(!shade.findAccessibilityNodeInfosByText("Panels").isEmpty()
                            || !shade.findAccessibilityNodeInfosByText("Panouri").isEmpty(),
                    "native keypad legend visible");
            boolean own = false;
            StringBuilder trace = new StringBuilder();
            for (int n = 0; n < 24 && !own; n++) {
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                shade = root("com.android.systemui");
                if (shade == null) break;
                List<AccessibilityNodeInfo> labels = shade.findAccessibilityNodeInfosByText("Leo keypad probe");
                if(labels.isEmpty()) {
                    AccessibilityNodeInfo label=findText(shade,"Leo keypad probe",new int[]{0});
                    if(label!=null) { labels=new java.util.ArrayList<>(); labels.add(label); }
                }
                Rect selected = outline();
                if (n < 14) trace.append(selected).append(" labels=").append(labels.size()).append(';');
                if (labels.isEmpty() || selected.isEmpty()) continue;
                AccessibilityNodeInfo target = labels.get(0);
                target.refresh();
                Rect titleBounds=new Rect();
                target.getBoundsInScreen(titleBounds);
                // A row-sized outline enclosing our own title is enough to
                // identify this local fixture. Opening it is verified below.
                if (!titleBounds.isEmpty() && selected.height()<=120
                        && selected.width()>=150 && selected.contains(titleBounds.centerX(),titleBounds.centerY())) {
                    own=true;
                    break;
                }
                while (target != null) {
                    Rect a = new Rect();
                    target.getBoundsInScreen(a);
                    if (target.isClickable() && same(a,selected)) { own=true; break; }
                    target=target.getParent();
                }
            }
            check(own, "D-pad selects the test notification without activating other items: " + trace);
            capture("native-notifications.png");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(waitText("ro.leo.systemtests","LOCAL NOTIFICATION OPENED"),
                    "OK opens only the local test notification");
            shell("cmd statusbar expand-notifications");
            check(root("com.android.systemui") != null, "shade reopens");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(root("com.android.systemui") != null, "Right switches to quick controls without a crash");
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            check(root("com.android.systemui") != null, "Left returns to notifications without a crash");
            key(KeyEvent.KEYCODE_BACK);
            check(root("ro.leo.systemtests") != null, "Back closes the native panel");
            result.putString("result", "PASS " + checks + " native Android keypad integration checks");
        } catch (Throwable failure) {
            result.putString("error", android.util.Log.getStackTraceString(failure));
            try {
                android.graphics.Bitmap shot=automation().takeScreenshot();
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(
                        new java.io.File(getTargetContext().getFilesDir(),"native-failure.png"))) {
                    shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
                } finally { shot.recycle(); }
            } catch(Exception ignored) { }
        } finally {
            manager.cancel(901);
            try { shell("cmd statusbar collapse"); } catch (Exception ignored) { }
            original.flags = originalFlags;
            automation().setServiceInfo(original);
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
}
