package ro.leo.visual;
import java.nio.file.*;
/** Exercises real shell I/O without touching a phone or user files. */
public final class TerminalSessionTest {
    private static final StringBuilder text=new StringBuilder();
    private static void await(String value)throws Exception{for(int i=0;i<100;i++){synchronized(text){if(text.toString().contains(value))return;}Thread.sleep(30);}throw new AssertionError("Missing shell result: "+value);}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("leo-shell-test-");TerminalSession s=new TerminalSession("/bin/sh");
        try{s.start(directory.toFile(),new TerminalSession.Output(){public void text(String value){synchronized(text){text.append(value);}}});s.send("printf 'LEO_READY\\n'; cd /; pwd");await("LEO_READY\n/\n");s.send("printf 'PERSIST='; pwd; printf 'UNICODE=șță\\n'; printf 'ERR=ok\\n' >&2");await("PERSIST=/\n");await("UNICODE=șță\n");await("ERR=ok\n");s.send("exit");await("[Sesiune încheiată]");System.out.println("PASS shell commands, cwd persistence, UTF-8, merged stderr and EOF");}finally{s.close();Files.delete(directory);}
    }
}
