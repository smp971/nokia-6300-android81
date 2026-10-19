package ro.leo.visual;
import java.util.*;
public final class AlarmMathTest {
    public static void main(String[] args) {
        TimeZone utc=TimeZone.getTimeZone("UTC"); Calendar base=Calendar.getInstance(utc); base.clear(); base.set(2026,0,1,10,0,0);
        long now=base.getTimeInMillis();
        if(AlarmMath.next(now,11,0,utc)-now!=3600000) throw new AssertionError("same day");
        if(AlarmMath.next(now,9,0,utc)-now!=23*3600000L) throw new AssertionError("next day");
        if(AlarmMath.next(now,10,0,utc)-now!=24*3600000L) throw new AssertionError("exact time");
        base.set(2026,11,31,23,59,0); now=base.getTimeInMillis();
        if(AlarmMath.next(now,0,0,utc)-now!=60000) throw new AssertionError("year rollover");
        if(AlarmMath.duration(0,1)!=1000 || AlarmMath.duration(1,30)!=90000 || AlarmMath.duration(1440,0)!=86400000) throw new AssertionError("duration");
        int[][] invalid={{0,0},{-1,0},{1441,0},{1440,1},{1,60},{1,-1}};
        for(int[] pair:invalid) try { AlarmMath.duration(pair[0],pair[1]); throw new AssertionError("invalid duration"); } catch(IllegalArgumentException expected) { }
        try { AlarmMath.next(now,24,0,utc); throw new AssertionError("invalid hour"); } catch(IllegalArgumentException expected) { }
        System.out.println("PASS 14 alarm/timer cases");
    }
}
