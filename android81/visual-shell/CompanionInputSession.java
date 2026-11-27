package ro.leo.visual;

import android.content.Context;
import android.graphics.Point;
import android.net.*;
import android.os.SystemClock;
import java.io.*;

/** One lazy root input helper per server; reused for a burst, gone after 15 idle seconds. */
final class CompanionInputSession implements Closeable {
    private final Context context;
    private volatile LocalSocket socket;
    private volatile java.lang.Process process;
    private DataInputStream input;
    private DataOutputStream output;
    private long completed;
    private volatile boolean closed;
    CompanionInputSession(Context context){this.context=context;}
    private void disconnect(){
        if(socket!=null)try{socket.close();}catch(IOException ignored){}
        socket=null;input=null;output=null;
        if(process!=null){process.destroy();try{process.getInputStream().close();process.getOutputStream().close();}catch(IOException ignored){}process=null;}
    }
    private void connect()throws Exception {
        if(closed)throw new IOException("Input session closed");if(socket!=null&&SystemClock.elapsedRealtime()-completed<10000)return;
        disconnect();String apk=context.getPackageCodePath();if(!apk.matches("[A-Za-z0-9_./=+-]+"))throw new IOException("APK path");
        String name="keyshell-input-"+CompanionServer.token();Point size=CompanionRemote.size(context);
        process=new ProcessBuilder("/system/bin/leo-su","-c","CLASSPATH="+apk+" /system/bin/app_process /system/bin ro.leo.visual.CompanionInputBridge session "+name+" "+android.os.Process.myUid()+" "+size.x+" "+size.y).redirectErrorStream(true).start();
        long until=SystemClock.elapsedRealtime()+3000;
        while(SystemClock.elapsedRealtime()<until){
            LocalSocket candidate=new LocalSocket();
            try{candidate.connect(new LocalSocketAddress(name,LocalSocketAddress.Namespace.ABSTRACT));candidate.setSoTimeout(2000);if(candidate.getPeerCredentials().getUid()!=0)throw new SecurityException("Input peer");
                DataInputStream read=new DataInputStream(candidate.getInputStream());if(read.readInt()!=1)throw new IOException("Input greeting");socket=candidate;input=read;output=new DataOutputStream(candidate.getOutputStream());return;
            }catch(IOException absent){try{candidate.close();}catch(IOException ignored){}SystemClock.sleep(20);}
            catch(SecurityException wrong){try{candidate.close();}catch(IOException ignored){}throw wrong;}
        }
        disconnect();throw new IOException("Input helper unavailable");
    }
    synchronized void send(int command,int a,int b)throws Exception {
        // A failed receipt is ambiguous: never automatically repeat a key.
        try{connect();output.writeInt(command);output.writeInt(a);output.writeInt(b);output.flush();if(input.readInt()!=1)throw new IOException("Input rejected");completed=SystemClock.elapsedRealtime();}
        catch(Exception error){disconnect();throw error;}
    }
    synchronized byte[] capture()throws Exception {
        try{
            connect();output.writeInt(3);output.writeInt(0);output.writeInt(0);output.flush();int length=input.readInt();completed=SystemClock.elapsedRealtime();
            if(length==-1)throw new UnsupportedOperationException("Fast capture unavailable");if(length==0)return null;
            if(length<8||length>1048576)throw new IOException("Frame limit");byte[] bytes=new byte[length];input.readFully(bytes);return bytes;
        }catch(UnsupportedOperationException unsupported){throw unsupported;}
        catch(Exception error){disconnect();throw error;}
    }
    public void close(){closed=true;LocalSocket current=socket;if(current!=null)try{current.close();}catch(IOException ignored){}java.lang.Process child=process;if(child!=null)child.destroy();}
}
