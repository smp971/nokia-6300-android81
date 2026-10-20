package ro.leo.probe;
/** One foreground diagnostic, no boot integration. Stock Gecko's speaker-vibration protocol. */
public final class VibrationAudioProbe {
 private static int set(String value)throws Exception{return (Integer)Class.forName("android.media.AudioSystem").getMethod("setParameters",String.class).invoke(null,value);}
 public static void main(String[] args)throws Exception {
  if(args.length!=1||!"three-short-pulses".equals(args[0]))throw new IllegalArgumentException("three-short-pulses");
  try{for(int i=0;i<3;i++){int result=set("vibrate_enabled=true;vibrate_duration=650");System.out.println("Pulse "+(i+1)+" audio status="+result);Thread.sleep(650);set("vibrate_enabled=false");Thread.sleep(1350);}}finally{set("vibrate_enabled=false");}
 }
}
