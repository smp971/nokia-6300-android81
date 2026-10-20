package ro.leo.visual;
import java.util.*;
/** Small, process-local numeric timings. Never stores keys, text or addresses. */
final class UiMetrics {
    private static final long[] keys=new long[120],draws=new long[120];private static int keyCount,drawCount;
    static synchronized void key(long nanos){keys[keyCount++%keys.length]=nanos/1000;}
    static synchronized void draw(long nanos){draws[drawCount++%draws.length]=nanos/1000;}
    private static String summary(long[] values,int count){int size=Math.min(count,values.length);if(size==0)return "fără probe";long[] ordered=Arrays.copyOf(values,size);Arrays.sort(ordered);return "n="+size+", p50="+ordered[size/2]+" µs, p95="+ordered[Math.min(size-1,size*95/100)]+" µs";}
    static synchronized String snapshot(){return "Procesarea tastelor (UI): "+summary(keys,keyCount)+"\nPregătire cadru Home/meniu (UI): "+summary(draws,drawCount)+"\nNu măsoară întârzierea fizică până la afișare.";}
}
