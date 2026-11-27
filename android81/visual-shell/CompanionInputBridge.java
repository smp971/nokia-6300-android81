package ro.leo.visual;

import android.os.SystemClock;
import android.net.*;
import android.view.*;
import android.graphics.Bitmap;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** UID-checked, local-only input session. No shell commands or arbitrary Binder transactions. */
public final class CompanionInputBridge {
    private static Object manager;
    private static Method inject;
    private static Method screenshot;
    static boolean allowed(int code,int scan){
        if(code==82)return scan==139||scan==212;
        if(code==4)return scan==0||scan==14||scan==158;
        return scan==0&&(code>=7&&code<=25||code>=3&&code<=6||code==223||code==224||code==279);
    }
    private static void event(InputEvent event)throws Exception {
        if(!Boolean.TRUE.equals(inject.invoke(manager,event,1)))throw new IOException("Input rejected");
    }
    private static void key(int code,int scan)throws Exception {
        if(!allowed(code,scan))throw new SecurityException("Unsupported key");long time=SystemClock.uptimeMillis();
        for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}){
            event(new KeyEvent(time,SystemClock.uptimeMillis(),action,code,0,0,-1,scan,KeyEvent.FLAG_FROM_SYSTEM,InputDevice.SOURCE_KEYBOARD));
            if(action==KeyEvent.ACTION_DOWN)SystemClock.sleep(20);
        }
    }
    private static void tap(int x,int y,int width,int height)throws Exception {
        if(x<0||y<0||x>=width||y>=height)throw new SecurityException("Outside display");long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){
            MotionEvent touch=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,x,y,0);touch.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try{event(touch);}finally{touch.recycle();}if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(20);
        }
    }
    private static void screen(DataOutputStream output,int width,int height)throws Exception {
        Bitmap bitmap=null;
        try{
            if(screenshot==null)screenshot=Class.forName("android.view.SurfaceControl").getMethod("screenshot",int.class,int.class);
            bitmap=(Bitmap)screenshot.invoke(null,width,height);
            if(bitmap==null){output.writeInt(0);return;}
            if(bitmap.getConfig()==Bitmap.Config.HARDWARE){Bitmap readable=bitmap.copy(Bitmap.Config.ARGB_8888,false);bitmap.recycle();bitmap=readable;}
            ByteArrayOutputStream png=new ByteArrayOutputStream();if(bitmap==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,png)||png.size()>1048576)throw new IOException("Frame unavailable");
            output.writeInt(png.size());png.writeTo(output);
        }catch(NoSuchMethodException unsupported){output.writeInt(-1);}
        catch(Exception unavailable){output.writeInt(0);}
        finally{if(bitmap!=null)bitmap.recycle();}
    }
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException();
        Class<?> type=Class.forName("android.hardware.input.InputManager");manager=type.getMethod("getInstance").invoke(null);inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
        if(args.length==2){key(Integer.parseInt(args[0]),Integer.parseInt(args[1]));return;}
        if(args.length!=5||!args[0].equals("session")||!args[1].matches("keyshell-input-[a-f0-9]{32}"))throw new SecurityException();
        int uid=Integer.parseInt(args[2]),width=Integer.parseInt(args[3]),height=Integer.parseInt(args[4]);
        if(uid<10000||uid>19999||width<1||height<1||width>4096||height>4096)throw new SecurityException();
        final LocalServerSocket server=new LocalServerSocket(args[1]);Timer watchdog=new Timer(true);
        watchdog.schedule(new TimerTask(){public void run(){try{server.close();}catch(IOException ignored){}}},5000);
        try(LocalSocket socket=server.accept()){
            watchdog.cancel();if(socket.getPeerCredentials().getUid()!=uid)throw new SecurityException();socket.setSoTimeout(15000);
            DataInputStream input=new DataInputStream(socket.getInputStream());DataOutputStream output=new DataOutputStream(socket.getOutputStream());output.writeInt(1);output.flush();
            while(true){int command=input.readInt();if(command==0)return;int a=input.readInt(),b=input.readInt();
                if(command==3){if(a!=0||b!=0)throw new SecurityException();screen(output,width,height);output.flush();continue;}
                if(command==1)key(a,b);else if(command==2)tap(a,b,width,height);else throw new SecurityException();output.writeInt(1);output.flush();}
        }catch(EOFException|java.net.SocketTimeoutException idle){/* No wake lock or idle polling. */}
        finally{watchdog.cancel();server.close();}
    }
}
