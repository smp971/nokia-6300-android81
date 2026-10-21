package ro.leo.visual;
public final class SoundScheduleReceiver extends android.content.BroadcastReceiver {public void onReceive(android.content.Context c,android.content.Intent i){try{SoundSchedule.sync(c);}catch(RuntimeException e){FaultLedger.record(c,"SoundSchedule",e);}}}
