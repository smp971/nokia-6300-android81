package ro.leo.systemtests;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;

/** Real permission denial and native volume slider. No audio playback or user data reads. */
public final class SystemDialogsTests extends NativeSystemUiTests {
    AccessibilityNodeInfo byId(AccessibilityNodeInfo node,String suffix,int[] visited) {
        if(node==null||++visited[0]>512)return null;
        String id=node.getViewIdResourceName();
        if(id!=null&&id.endsWith("/"+suffix)&&node.isVisibleToUser())return node;
        for(int n=0;n<node.getChildCount();n++){
            AccessibilityNodeInfo found=byId(node.getChild(n),suffix,visited);if(found!=null)return found;
        }
        return null;
    }
    AccessibilityNodeInfo target(String suffix) {
        for(AccessibilityWindowInfo window:automation().getWindows()){
            AccessibilityNodeInfo found=byId(window.getRoot(),suffix,new int[]{0});if(found!=null)return found;
        }
        return null;
    }
    boolean select(String id) {
        // SystemUI posts the volume window asynchronously; the status bar may
        // already be accessible while this new dialog has not been attached.
        for(int wait=0;wait<40&&target(id)==null;wait++)SystemClock.sleep(100);
        for(int n=0;n<16;n++){
            AccessibilityNodeInfo node=target(id);
            if(node==null)return false;
            node.refresh();Rect desired=new Rect();node.getBoundsInScreen(desired);
            key(KeyEvent.KEYCODE_DPAD_DOWN);Rect border=outline();
            if(!desired.isEmpty() && border.contains(desired.centerX(),desired.centerY())
                    && border.width()<=desired.width()+12 && border.height()<=desired.height()+12)return true;
        }
        return false;
    }
    public void onStart(){
        Bundle result=new Bundle();Activity fixture=null;
        AudioManager audio=(AudioManager)getTargetContext().getSystemService(Context.AUDIO_SERVICE);
        int[] volumes=new int[6];for(int n=0;n<volumes.length;n++)volumes[n]=audio.getStreamVolume(n);
        AccessibilityServiceInfo info=automation().getServiceInfo();int flags=info.flags;
        try{
            info.flags|=AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    |AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation().setServiceInfo(info);
            check(!audio.isMusicActive(),"no active media during the volume probe");
            shell("pm revoke ro.leo.systemtests android.permission.READ_CALENDAR");
            fixture=startActivitySync(new Intent(getTargetContext(),PermissionFixtureActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(root("com.android.packageinstaller")!=null,"native permission dialog opens");
            check(select("permission_deny_button"),"D-pad selects native Deny button");
            capture("native-permission.png");key(KeyEvent.KEYCODE_DPAD_CENTER);
            for(int n=0;n<30&&!PermissionFixtureActivity.received;n++)SystemClock.sleep(100);
            check(PermissionFixtureActivity.received&&PermissionFixtureActivity.denied,"OK denies fixture permission");
            fixture.finish();fixture=null;
            fixture=startActivitySync(new Intent(getTargetContext(),KeypadFixtureActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);
            check(root("com.android.systemui")!=null,"native volume dialog opens");
            check(select("volume_row_slider"),"D-pad selects native volume slider");
            capture("native-volume.png");
            int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            key(before>0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);
            check(audio.getStreamVolume(AudioManager.STREAM_MUSIC)!=before,"left/right changes native music volume");
            key(KeyEvent.KEYCODE_BACK);
            result.putString("result","PASS "+checks+" Android permission/volume checks");
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));
            try{capture("dialog-failure.png");}catch(Exception ignored){} }
        finally{
            for(int n=0;n<volumes.length;n++)if(audio.getStreamVolume(n)!=volumes[n])audio.setStreamVolume(n,volumes[n],0);
            try{shell("pm revoke ro.leo.systemtests android.permission.READ_CALENDAR");}catch(Exception ignored){}
            if(fixture!=null){final Activity close=fixture;runOnMainSync(new Runnable(){public void run(){close.finish();}});}
            info.flags=flags;automation().setServiceInfo(info);
        }
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
