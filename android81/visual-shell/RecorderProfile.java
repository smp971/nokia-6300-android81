package ro.leo.visual;
import ro.leo.i18n.L;import android.content.Context;
final class RecorderProfile {
 static final String[] NAMES=L.array(new String[]{"Voce · 16 kHz / 64 kbps","Echilibrat · 22 kHz / 96 kbps","Detaliat · 44 kHz / 128 kbps"});
 static final int[] RATES={16000,22050,44100},BITS={64000,96000,128000};
 static int selected(Context c){return Math.max(0,Math.min(2,c.getSharedPreferences("recorder",0).getInt("quality",0)));}
 static boolean validName(String name){return name!=null&&!name.trim().isEmpty()&&name.length()<=64&&!name.contains("/")&&!name.contains("\\")&&!name.contains("\u0000")&&!name.equals(".")&&!name.equals("..");}
}
