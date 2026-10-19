package ro.leo.visual;
import java.util.Calendar;
import java.util.TimeZone;

final class AlarmMath {
    static long next(long now,int hour,int minute,TimeZone zone) {
        if(hour<0 || hour>23 || minute<0 || minute>59) throw new IllegalArgumentException();
        Calendar target=Calendar.getInstance(zone); target.setTimeInMillis(now);
        target.set(Calendar.HOUR_OF_DAY,hour); target.set(Calendar.MINUTE,minute);
        target.set(Calendar.SECOND,0); target.set(Calendar.MILLISECOND,0);
        if(target.getTimeInMillis()<=now) target.add(Calendar.DATE,1);
        return target.getTimeInMillis();
    }
    static long duration(int minutes,int seconds) {
        if(minutes<0 || minutes>1440 || seconds<0 || seconds>59) throw new IllegalArgumentException();
        long value=minutes*60000L+seconds*1000L;
        if(value<=0 || value>86400000L) throw new IllegalArgumentException();
        return value;
    }
}
