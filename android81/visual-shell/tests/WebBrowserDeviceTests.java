package ro.leo.visual;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Real WebView-process tests against an app-owned loopback HTTP fixture. */
public final class WebBrowserDeviceTests extends Instrumentation {
    private ServerSocket server;
    private Thread serving;
    private volatile boolean running;
    private int checks;
    private final AtomicInteger images=new AtomicInteger();
    private PowerManager.WakeLock awake;
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private UiAutomation automation(){return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo item,String text,int depth){
        if(item==null||depth>30)return null;
        for(int i=0;i<item.getChildCount();i++){AccessibilityNodeInfo child=item.getChild(i);if(child==null)continue;AccessibilityNodeInfo found;try{found=find(child,text,depth+1);}finally{child.recycle();}if(found!=null)return found;}
        CharSequence value=item.getText(),description=item.getContentDescription();
        return item.isVisibleToUser()&&(value!=null&&value.toString().contains(text)||description!=null&&description.toString().contains(text))?AccessibilityNodeInfo.obtain(item):null;
    }
    private AccessibilityNodeInfo node(String text){
        AccessibilityNodeInfo root=automation().getRootInActiveWindow();if(root==null)return null;
        try{return find(root,text,0);}finally{root.recycle();}
    }
    private AccessibilityNodeInfo await(String text){long end=SystemClock.elapsedRealtime()+20000;AccessibilityNodeInfo found;do{found=node(text);if(found!=null)return found;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<end);throw new AssertionError("Visible browser content missing: "+text);}
    private void visible(String text){AccessibilityNodeInfo found=await(text);found.recycle();checks++;}
    private void key(int code){long now=SystemClock.uptimeMillis();check(automation().injectInputEvent(new KeyEvent(now,now,0,code,0),true),"key down injected");check(automation().injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),1,code,0),true),"key up injected");SystemClock.sleep(80);}
    private void fixture()throws IOException{
        server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));running=true;
        serving=new Thread(new Runnable(){public void run(){while(running)try(Socket socket=server.accept()){
            socket.setSoTimeout(3000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String request=input.readLine();if(request==null)continue;String path=request.split(" ")[1];for(int i=0;i<40;i++){String line=input.readLine();if(line==null||line.isEmpty())break;}
            String type="text/html; charset=utf-8",body;
            if(path.equals("/image.svg")){images.incrementAndGet();type="image/svg+xml";body="<svg xmlns='http://www.w3.org/2000/svg' width='60' height='36'><rect width='60' height='36' fill='#6847aa'/><circle cx='30' cy='18' r='12' fill='#80d7cb'/></svg>";}
            else if(path.equals("/next"))body="<!doctype html><meta name='viewport' content='width=device-width'><title>A doua pagină</title><p>Navigare reușită</p>";
            else body="<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>KeyShell web test</title><body style='font:16px sans-serif'><span id='js'></span><script>document.getElementById('js').textContent='Script activ';</script><noscript>Script oprit</noscript><p id='image'>Se încarcă imaginea</p><img src='/image.svg' alt='Imagine test' onload=\"document.getElementById('image').textContent='Imagine încărcată'\"><p><a href='/next'>Pagina următoare</a></p></body>";
            byte[] bytes=body.getBytes("UTF-8");OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes("US-ASCII"));out.write(bytes);out.flush();
        }catch(IOException error){if(!running)return;}}},"KeyShell-web-fixture");serving.setDaemon(true);serving.start();
    }
    private void capture(String name)throws IOException{SystemClock.sleep(250);Bitmap bitmap=automation().takeScreenshot();check(bitmap!=null,"browser screenshot");try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    public void onStart(){Bundle result=new Bundle();int code=0;try{
        awake=((PowerManager)getTargetContext().getSystemService("power")).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"KeyShell:web-tests");awake.acquire(180000);
        android.accessibilityservice.AccessibilityServiceInfo info=automation().getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation().setServiceInfo(info);
        check(WebBrowserActivity.allowed("https://example.org/"),"HTTPS accepted");
        check(!WebBrowserActivity.allowed("file:///data/private"),"file navigation rejected");
        check(!WebBrowserActivity.allowed("javascript:alert(1)"),"JavaScript URL rejected");
        check(!WebBrowserActivity.allowed("content://ro.leo.visual.files/leo/private"),"content navigation rejected");
        check(!WebBrowserActivity.allowed("https://name:password@example.org/"),"URL credentials rejected");
        fixture();String url="http://127.0.0.1:"+server.getLocalPort()+"/";
        getTargetContext().startActivity(new Intent(getTargetContext(),WebBrowserActivity.class).setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        visible("KeyShell web test");visible("Script activ");visible("Imagine încărcată");check(images.get()>0,"image requested over HTTP");capture("web-browser-fixture.png");
        AccessibilityNodeInfo link=await("Pagina următoare");while(!link.isClickable()&&link.getParent()!=null){AccessibilityNodeInfo parent=link.getParent();link.recycle();link=parent;}try{check(link.performAction(AccessibilityNodeInfo.ACTION_CLICK),"HTML link activated");}finally{link.recycle();}
        visible("Navigare reușită");key(KeyEvent.KEYCODE_BACK);visible("Script activ");
        key(KeyEvent.KEYCODE_MENU);visible("JavaScript · pornit");
        for(int i=0;i<6;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);visible("Script oprit");
        key(KeyEvent.KEYCODE_MENU);visible("JavaScript · oprit");for(int i=0;i<9;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);
        result.putString("result","PASS "+checks+" graphical browser checks");code=-1;
    }catch(Throwable error){result.putString("result","FAIL after "+checks+" checks, image requests="+images.get()+": "+error);StringWriter text=new StringWriter();error.printStackTrace(new PrintWriter(text));result.putString("trace",text.toString());try{capture("web-browser-failure.png");}catch(Throwable ignored){}
    }finally{running=false;if(server!=null)try{server.close();}catch(IOException ignored){}if(awake!=null&&awake.isHeld())awake.release();}
        finish(code,result);
    }
}
