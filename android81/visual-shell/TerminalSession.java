package ro.leo.visual;
import ro.leo.i18n.L;
import java.io.*;
/** Persistent pipe shell, not a PTY emulator. Runs under the application's UID. */
final class TerminalSession {
    interface Output {void text(String text);}
    private final String executable;
    TerminalSession(){this("/system/bin/sh");}
    TerminalSession(String path){executable=path;}
    private Process process;private OutputStream input;private volatile boolean closed;
    synchronized void start(File cwd,final Output output)throws IOException{
        if(process!=null)return;ProcessBuilder builder=new ProcessBuilder(executable);builder.directory(cwd);builder.redirectErrorStream(true);builder.environment().put("TERM","dumb");process=builder.start();input=process.getOutputStream();if("/system/bin/sh".equals(executable)){input.write("su() { /system/bin/leo-su \"$@\"; }\n".getBytes("UTF-8"));input.flush();}final InputStream stream=process.getInputStream();
        new Thread(new Runnable(){public void run(){try{InputStreamReader r=new InputStreamReader(stream,"UTF-8");char[] chars=new char[512];int count;while(!closed&&(count=r.read(chars))!=-1)output.text(new String(chars,0,count));if(!closed)output.text(L.t("\n[Sesiune încheiată]\n"));}catch(IOException e){if(!closed)output.text(L.t("\n[Citirea sesiunii s-a oprit]\n"));}}},"LeoTerminalRead").start();
    }
    synchronized void send(String command)throws IOException{if(closed||input==null)throw new IOException();input.write((command+"\n").getBytes("UTF-8"));input.flush();}
    void close(){closed=true;final Process old=process;process=null;if(old!=null){old.destroy();new Thread(new Runnable(){public void run(){try{old.getOutputStream().close();old.getInputStream().close();}catch(IOException ignored){}}},"LeoTerminalClose").start();}}
}
