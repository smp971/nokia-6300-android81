package ro.leo.visual;
import android.app.NotificationManager;import android.content.*;import android.media.AudioManager;
/** Shared, explicitly selected profiles. No scheduled or automatic changes. */
final class SoundProfiles {
    static final String[] NAMES={"Normal","Silențios","Exterior","Noapte · alarma rămâne","Profilul meu","Salvează nivelurile curente"};
    private static final int[] STREAMS={AudioManager.STREAM_MUSIC,AudioManager.STREAM_RING,AudioManager.STREAM_ALARM};
    static String current(Context c){AudioManager a=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);return a.getRingerMode()==AudioManager.RINGER_MODE_SILENT?"Silențios":a.getRingerMode()==AudioManager.RINGER_MODE_VIBRATE?"Vibrații":c.getSharedPreferences("sound-profile",0).getString("label","Normal");}
    static void select(final LeoActivity c,final Runnable changed){c.options("Profil sonor",NAMES,new LeoActivity.Choice(){public void pick(int i){try{apply(c,i);if(i==5)c.notice("Nivelurile personale au fost salvate");changed.run();}catch(RuntimeException failure){c.notice(failure.getMessage()==null?"Profil nesalvat":failure.getMessage());}}});}
    static void apply(Context c,int which){AudioManager a=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);SharedPreferences p=c.getSharedPreferences("sound-profile",0);
        if(which==5){SharedPreferences.Editor edit=p.edit().putBoolean("saved",true).putInt("filter",((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).getCurrentInterruptionFilter()).putInt("mode",a.getRingerMode());for(int i=0;i<STREAMS.length;i++)edit.putInt("volume"+i,a.getStreamVolume(STREAMS[i]));edit.apply();return;}
        if(!((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted())throw new IllegalStateException("Acces Nu deranjați neacordat");
        if(which==4){if(!p.getBoolean("saved",false))throw new IllegalStateException("Salvează întâi nivelurile curente");for(int i=0;i<STREAMS.length;i++)a.setStreamVolume(STREAMS[i],Math.min(a.getStreamMaxVolume(STREAMS[i]),p.getInt("volume"+i,0)),0);a.setRingerMode(p.getInt("mode",AudioManager.RINGER_MODE_NORMAL));((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).setInterruptionFilter(p.getInt("filter",NotificationManager.INTERRUPTION_FILTER_ALL));}
        else if(which==1||which==3){if(which==3)a.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);a.setRingerMode(AudioManager.RINGER_MODE_SILENT);if(which==3)((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);}
        else {a.setRingerMode(AudioManager.RINGER_MODE_NORMAL);int max=a.getStreamMaxVolume(AudioManager.STREAM_RING);if(which==2||a.getStreamVolume(AudioManager.STREAM_RING)==0)a.setStreamVolume(AudioManager.STREAM_RING,which==2?max:Math.max(1,max/2),0);}
        p.edit().putString("label",NAMES[which]).apply();
    }
}
