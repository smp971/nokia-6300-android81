package ro.leo.fm;
/** Short-lived root command for the already active Android audio service. */
public final class FmAudioCommand {
    private static int set(String value)throws Exception {int result=(Integer)Class.forName("android.media.AudioSystem").getMethod("setParameters",String.class).invoke(null,value);System.out.println("FM_AUDIO "+value+" result="+result);return result;}
    public static void main(String[] args)throws Exception {
        if(args.length!=1||(!args[0].equals("start")&&!args[0].equals("stop")&&!args[0].equals("start-muted")))throw new IllegalArgumentException("start | stop");
        if(args[0].equals("stop")){set("fm_volume=0");set("handle_fm=4");java.lang.Process route=new ProcessBuilder("/data/leo-fm/route","restore").inheritIO().start();if(!route.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)){route.destroy();throw new IllegalStateException("route restore timeout");}if(route.exitValue()!=0)throw new IllegalStateException("route restore failed");return;}
        // 0x100000 is the legacy Qualcomm FM bit; 0x4 is wired headset.
        if(set("fm_volume=0")!=0)throw new IllegalStateException("volume rejected");
        if(set("handle_fm=1048580")!=0)throw new IllegalStateException("route rejected");
        if(set(args[0].equals("start-muted")?"fm_volume=0":"fm_volume=0.08")!=0)throw new IllegalStateException("volume rejected");
    }
}
