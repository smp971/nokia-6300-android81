package ro.leo.visual;

import android.content.*;
import android.graphics.Point;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Fixed remote controls only. No client-supplied executable, command, path or intent. */
final class CompanionRemote {
    private static final Object IO=new Object();
    private static final Map<String,Integer> KEYS=new HashMap<String,Integer>();
    static {
        String[] names={"up","down","left","right","ok","back","softLeft","softRight","home","menu","call","end","wake","sleep","volumeUp","volumeDown","star","hash"};
        int[] codes={19,20,21,22,23,4,82,4,3,82,5,6,224,223,24,25,17,18};
        for(int i=0;i<names.length;i++)KEYS.put(names[i],codes[i]);
        for(int i=0;i<10;i++)KEYS.put(""+i,7+i);
    }
    static byte[] command(final String command,final int limit)throws Exception {
        synchronized(IO){
            final java.lang.Process process=new ProcessBuilder("/system/bin/leo-su","-c",command).redirectErrorStream(true).start();
            FutureTask<byte[]> read=new FutureTask<byte[]>(new Callable<byte[]>(){public byte[] call()throws Exception{
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] part=new byte[8192];int count;
                try(InputStream stream=process.getInputStream()){while((count=stream.read(part))!=-1){if(out.size()+count>limit)throw new IOException("Remote output limit");out.write(part,0,count);}}return out.toByteArray();
            }});new Thread(read,"KeyShellRemoteRead").start();
            try {if(!process.waitFor(4,TimeUnit.SECONDS))throw new IOException("Remote control timeout");byte[] out=read.get(1,TimeUnit.SECONDS);if(process.exitValue()!=0)throw new IOException("Remote control unavailable");return out;}
            finally {process.destroy();read.cancel(true);}
        }
    }
    static Point size(Context c){Point p=new Point();((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p);return p;}
    static byte[] screen(Context c)throws Exception {
        byte[] png=command("/system/bin/screencap -p",1048576);
        if(png.length<8||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71)throw new IOException("Screen capture unavailable");return png;
    }
    static JSONObject key(Context c,JSONObject data,CompanionInputSession input)throws Exception {
        CompanionModel.exact(data,"key");String name=CompanionServer.string(data,"key");Integer code=KEYS.get(name);
        if(code==null)throw new IllegalArgumentException("Unknown key");
        // Scancodes distinguish physical softkeys from dedicated Menu/Back.
        if(name.equals("softLeft")||name.equals("softRight")||name.equals("menu")){
            int scan=name.equals("softLeft")?139:name.equals("softRight")?158:212;
            input.send(1,code,scan);
        }else input.send(1,code,0);
        return new JSONObject().put("accepted",true);
    }
    static JSONObject tap(Context c,JSONObject data,CompanionInputSession input)throws Exception {
        CompanionModel.exact(data,"x","y");int x=CompanionModel.integer(data,"x"),y=CompanionModel.integer(data,"y");Point p=size(c);
        if(x<0||y<0||x>=p.x||y>=p.y)throw new IllegalArgumentException("Outside display");
        input.send(2,x,y);return new JSONObject().put("accepted",true);
    }
    static JSONObject text(final Context c,JSONObject data,CompanionInputSession input)throws Exception {
        CompanionModel.exact(data,"text");final String value=CompanionServer.string(data,"text");
        if(value.isEmpty()||value.length()>2048||value.indexOf(0)>=0)throw new IllegalArgumentException("Text length");
        // Android's native paste supports Unicode, unlike `input text` on Oreo.
        final CountDownLatch done=new CountDownLatch(1);final RuntimeException[] error={null};
        new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){try{((ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("KeyShell WebUI",value));}catch(RuntimeException e){error[0]=e;}finally{done.countDown();}}});
        if(!done.await(2,TimeUnit.SECONDS)||error[0]!=null)throw new IOException("Clipboard unavailable");
        input.send(1,279,0);return new JSONObject().put("accepted",true).put("clipboard",true);
    }
    static JSONObject launch(final Context c,JSONObject data)throws Exception {
        CompanionModel.exact(data,"id");final int id=CompanionModel.integer(data,"id");
        if(id<0||id>=AppCatalog.NAMES.length||AppCatalog.settingsOnly(id))throw new IllegalArgumentException("Unknown app");
        c.startActivity(HomeAccess.intent(c).putExtra("open_app",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));return new JSONObject().put("accepted",true);
    }
}
