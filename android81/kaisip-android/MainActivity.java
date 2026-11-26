package ro.n0d3.kaisip;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import android.text.InputType;
import org.json.JSONObject;

/** Physical keypad host. The SIP service owns the session across foreground changes. */
public final class MainActivity extends Activity {
    private FrameLayout content;
    private SipService session;
    private boolean bound;
    private PermissionRequest microphone;
    private Dialog editor;
    private long editDown;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){session=((SipService.LocalBinder)binder).service();session.attach(MainActivity.this,content);}
        public void onServiceDisconnected(ComponentName name){session=null;}
    };
    protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setVolumeControlStream(android.media.AudioManager.STREAM_VOICE_CALL);
        content=new FrameLayout(this);setContentView(content);
    }
    protected void onStart(){super.onStart();bound=bindService(new Intent(this,SipService.class),connection,BIND_AUTO_CREATE);}
    protected void onStop(){
        if(editor!=null){editor.dismiss();editor=null;}
        if(microphone!=null){microphone.deny();microphone=null;}
        if(session!=null)session.detach(this);
        if(bound){unbindService(connection);bound=false;}
        session=null;super.onStop();
    }
    void requestMicrophone(PermissionRequest request){
        if(!SipService.trusted(request.getOrigin())){request.deny();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});return;}
        if(microphone!=null)microphone.deny();microphone=request;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7);
    }
    public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==7&&microphone!=null){PermissionRequest request=microphone;microphone=null;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else request.deny();}
    }
    static String key(KeyEvent event){
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_MENU)return "SoftLeft";
        if(code==KeyEvent.KEYCODE_BACK)return event.getScanCode()==158?"SoftRight":"Backspace";
        if(code==KeyEvent.KEYCODE_CALL)return "Call";
        if(code==KeyEvent.KEYCODE_ENDCALL)return "EndCall";
        if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER)return "Enter";
        if(code==KeyEvent.KEYCODE_DPAD_UP)return "ArrowUp";
        if(code==KeyEvent.KEYCODE_DPAD_DOWN)return "ArrowDown";
        if(code==KeyEvent.KEYCODE_DPAD_LEFT)return "ArrowLeft";
        if(code==KeyEvent.KEYCODE_DPAD_RIGHT)return "ArrowRight";
        if(code>=KeyEvent.KEYCODE_0&&code<=KeyEvent.KEYCODE_9)return Integer.toString(code-KeyEvent.KEYCODE_0);
        if(code==KeyEvent.KEYCODE_STAR)return "*";
        if(code==KeyEvent.KEYCODE_POUND)return "#";
        return "";
    }
    public boolean dispatchKeyEvent(KeyEvent event){
        if(session==null)return super.dispatchKeyEvent(event);
        String key=key(event);if(key.isEmpty())return super.dispatchKeyEvent(event);
        boolean soft=key.equals("SoftLeft")||key.equals("SoftRight")||key.equals("Call")||key.equals("EndCall");
        if(session.editing&&key.equals("Enter")){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)editDown=event.getDownTime();
            if(event.getAction()==KeyEvent.ACTION_UP&&editDown==event.getDownTime()&&!event.isCanceled()){editDown=0;showEditor(session.editor,"");}
            return true;
        }
        if(session.editing&&!soft&&!key.startsWith("Arrow")&&!key.equals("Backspace")){
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())showEditor(session.editor,key.matches("[0-9*#]")?key:"");
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()>0&&!key.startsWith("Arrow"))return true;
        if(event.getAction()==KeyEvent.ACTION_DOWN||event.getAction()==KeyEvent.ACTION_UP){session.key(key,event.getAction()==KeyEvent.ACTION_DOWN,event.getRepeatCount()>0);return true;}
        return super.dispatchKeyEvent(event);
    }
    public void onBackPressed(){if(session!=null)session.key("Backspace",true,false);else super.onBackPressed();}
    private void showEditor(String serialized,String seed){
        if(editor!=null||session==null)return;
        try{
            JSONObject data=new JSONObject(serialized);final String id=data.getString("id");
            if(!id.matches("[A-Za-z0-9_-]{1,80}"))return;
            final SipService active=session;final Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Dialog_NoActionBar);editor=dialog;
            LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(12,12,12,12);
            TextView title=new TextView(this);title.setText(data.optString("label","keySIP"));title.setTextSize(20);panel.addView(title);
            final EditText input=new EditText(this);String type=data.optString("type","text");
            input.setInputType(type.equals("password")?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:type.equals("tel")||type.equals("number")?InputType.TYPE_CLASS_PHONE:InputType.TYPE_CLASS_TEXT|(data.optBoolean("multiline",false)?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
            input.setSingleLine(!data.optBoolean("multiline",false));
            input.setText(data.optString("value","")+seed);input.setTextSize(22);input.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setSelectAllOnFocus(false);panel.addView(input,new LinearLayout.LayoutParams(-1,-2));
            TextView help=new TextView(this);help.setText("Stânga: salvează · Înapoi: anulează");help.setTextSize(14);panel.addView(help);
            final Runnable save=new Runnable(){public void run(){active.script("AndroidPlatform.commitEditor("+JSONObject.quote(id)+","+JSONObject.quote(input.getText().toString())+")");dialog.dismiss();}};
            input.setOnEditorActionListener(new TextView.OnEditorActionListener(){public boolean onEditorAction(TextView view,int action,KeyEvent event){if(action==EditorInfo.IME_ACTION_DONE){save.run();return true;}return false;}});
            dialog.setOnKeyListener(new DialogInterface.OnKeyListener(){public boolean onKey(DialogInterface d,int code,KeyEvent event){if(code==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())save.run();return true;}return false;}});
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface ignored){editor=null;active.script("AndroidPlatform.blurEditor()");}});
            dialog.setContentView(panel);dialog.show();dialog.getWindow().setLayout(-1,-2);input.requestFocus();input.setSelection(input.length());
        }catch(Exception unavailable){editor=null;}
    }
}
