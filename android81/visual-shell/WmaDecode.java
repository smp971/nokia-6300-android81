package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Bounded local WMA preparation. Originals are never changed or uploaded. */
final class WmaDecode {
    interface Callback {void complete(File wave,String error);}
    private volatile boolean cancelled;
    private Process process;private File input,output;
    synchronized void cancel(){cancelled=true;if(process!=null)process.destroy();clean();}
    private synchronized void clean(){if(input!=null)input.delete();if(output!=null)output.delete();}
    void start(final Context context,final Uri uri,final Callback callback){
        new Thread(new Runnable(){public void run(){
            String error=null;File result=null;
            try{
                synchronized(WmaDecode.this){
                    if(cancelled)return;
                    input=File.createTempFile("leo-wma-",".wma",context.getCacheDir());
                    output=File.createTempFile("leo-wma-",".wav",context.getCacheDir());
                }
                if(context.getCacheDir().getUsableSpace()<180L*1024*1024)throw new IOException(L.t("Spațiu insuficient pentru pregătirea WMA"));
                try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(input)){
                    if(in==null)throw new IOException(L.t("Fișier indisponibil"));
                    byte[] bytes=new byte[32768];int n;long total=0;
                    while((n=in.read(bytes))!=-1){
                        if(cancelled)throw new InterruptedIOException();
                        total+=n;if(total>32L*1024*1024)throw new IOException(L.t("WMA depășește limita de 32 MB"));
                        out.write(bytes,0,n);
                    }
                }
                synchronized(WmaDecode.this){
                    if(cancelled)throw new InterruptedIOException();
                    process=new ProcessBuilder("/system/bin/leo-wma-decode","-nostdin","-hide_banner","-loglevel","error","-y",
                        "-threads","1","-i",input.getAbsolutePath(),"-vn","-ar","44100","-ac","2","-c:a","pcm_s16le",
                        "-fs","134217728",output.getAbsolutePath()).redirectErrorStream(true).start();
                }
                // Drain errors separately so malformed input cannot block the decoder.
                final Process decoder=process;
                Thread drain=new Thread(new Runnable(){public void run(){try(InputStream in=decoder.getInputStream()){byte[] b=new byte[1024];while(in.read(b)!=-1){}}catch(IOException ignored){}}},"Leo WMA log");drain.start();
                if(!decoder.waitFor(90,TimeUnit.SECONDS)){decoder.destroy();throw new IOException(L.t("Pregătirea WMA a depășit 90 de secunde"));}
                if(cancelled)throw new InterruptedIOException();
                if(decoder.exitValue()!=0||output.length()<44)throw new IOException(L.t("Varianta WMA nu poate fi decodată"));
                if(output.length()>=134217000)throw new IOException(L.t("WMA prea lung pentru memoria temporară"));
                input.delete();result=output;
            }catch(Exception failure){error=cancelled?L.t("Anulat"):failure instanceof IOException?failure.getMessage():L.t("Decodorul WMA este indisponibil");clean();}
            final File prepared=result;final String message=error;
            new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){if(!cancelled)callback.complete(prepared,message);else clean();}});
        }},"Leo WMA prepare").start();
    }
}
