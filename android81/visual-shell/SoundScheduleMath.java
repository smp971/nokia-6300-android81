package ro.leo.visual;
import java.util.*;
final class SoundScheduleMath {
 static boolean active(long now,int start,int end,int days){if(start==end)return false;Calendar c=Calendar.getInstance();c.setTimeInMillis(now);int minute=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);if(start<end)return minute>=start&&minute<end&&(days&(1<<((c.get(Calendar.DAY_OF_WEEK)+5)%7)))!=0;if(minute<end)c.add(Calendar.DATE,-1);else if(minute<start)return false;return (days&(1<<((c.get(Calendar.DAY_OF_WEEK)+5)%7)))!=0;}
 static long next(long now,int start,int end){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);long best=Long.MAX_VALUE;for(int day=0;day<3;day++)for(int minute:new int[]{start,end}){Calendar d=(Calendar)c.clone();d.add(Calendar.DATE,day);d.set(Calendar.HOUR_OF_DAY,minute/60);d.set(Calendar.MINUTE,minute%60);d.set(Calendar.SECOND,0);d.set(Calendar.MILLISECOND,0);long due=d.getTimeInMillis();if(due>now&&due<best)best=due;}return best;}
 static int minute(String s){if(s==null||!s.matches("[0-2][0-9]:[0-5][0-9]"))throw new IllegalArgumentException();int h=Integer.parseInt(s.substring(0,2));if(h>23)throw new IllegalArgumentException();return h*60+Integer.parseInt(s.substring(3));}
 static String format(int n){return String.format(Locale.ROOT,"%02d:%02d",n/60,n%60);}
}
