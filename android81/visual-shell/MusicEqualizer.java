package ro.leo.visual;
import android.content.Context;import android.media.audiofx.Equalizer;
/** Attached only to Leo's active media session. Failure never interrupts playback. */
final class MusicEqualizer {
 private Equalizer effect;
 String[] presets(int session){try{attach(session);String[] result=new String[effect.getNumberOfPresets()];for(short n=0;n<result.length;n++)result[n]=effect.getPresetName(n);return result;}catch(RuntimeException e){close();return new String[0];}}
 void attach(int session){if(session<=0)throw new IllegalStateException();if(effect==null)effect=new Equalizer(0,session);}
 boolean apply(Context c,int session,int preset){try{attach(session);if(preset<0)effect.setEnabled(false);else{if(preset>=effect.getNumberOfPresets())return false;effect.usePreset((short)preset);if(effect.setEnabled(true)!=0)return false;}c.getSharedPreferences("music-player",0).edit().putInt("equalizer",preset).apply();return true;}catch(RuntimeException e){return false;}}
 void close(){if(effect!=null){effect.release();effect=null;}}
}
