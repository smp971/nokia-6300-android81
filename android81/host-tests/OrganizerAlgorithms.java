package ro.leo.visual;
import java.util.*;import java.io.*;import org.json.*;
public final class OrganizerAlgorithms {
 static int checks;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 static long at(int year,int month,int day,int hour,int minute){Calendar c=Calendar.getInstance();c.clear();c.set(year,month-1,day,hour,minute);return c.getTimeInMillis();}
 static JSONArray parse(String s)throws Exception{return CalendarIcs.decode(new ByteArrayInputStream(s.getBytes("UTF-8")));}
 static void rejects(String body,String label)throws Exception{boolean rejected=false;try{parse(body);}catch(Exception e){rejected=true;}check(rejected,label);}
 public static void main(String[] args)throws Exception{
  TimeZone old=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Europe/Bucharest"));try{
   check(SoundScheduleMath.minute("23:59")==1439,"last minute");boolean bad=false;try{SoundScheduleMath.minute("24:00");}catch(Exception e){bad=true;}check(bad,"invalid clock rejected");
   check(SoundScheduleMath.active(at(2026,10,23,23,0),1320,420,31),"Friday overnight starts");
   check(SoundScheduleMath.active(at(2026,10,24,6,59),1320,420,31),"Friday continues Saturday");
   check(!SoundScheduleMath.active(at(2026,10,24,7,0),1320,420,31),"end exclusive");
   check(!SoundScheduleMath.active(at(2026,10,24,23,0),1320,420,31),"weekend excluded");
   check(SoundScheduleMath.active(at(2026,10,26,6,0),1320,420,96),"Sunday overnight continues Monday");
   check(!SoundScheduleMath.active(at(2026,10,21,12,0),600,600,127),"empty interval inactive");
   long now=at(2026,10,21,22,0);check(SoundScheduleMath.next(now,1320,420)==at(2026,10,22,7,0),"next boundary strictly later");
   now=at(2026,10,24,23,0);check(SoundScheduleMath.next(now,1320,420)==at(2026,10,25,7,0),"DST end boundary local time");
   now=at(2026,3,28,23,0);check(SoundScheduleMath.next(now,1320,420)==at(2026,3,29,7,0),"DST start boundary local time");
   String title="Ședință,\ncu; text \\ și țțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțțț";
   JSONObject event=new JSONObject().put("title",title).put("start",at(2026,10,21,12,0)).put("end",at(2026,10,21,13,0)).put("rule",JSONObject.NULL).put("duration",JSONObject.NULL).put("zone","Europe/Bucharest").put("allDay",0).put("alerts",new JSONArray());
   String ics=CalendarIcs.encode(new JSONArray().put(event));JSONArray restored=parse(ics);check(restored.length()==1&&restored.getJSONObject(0).getString("title").equals(title),"Unicode folding escapes round trip");
   check(CalendarIcs.identity(event).equals(CalendarIcs.identity(restored.getJSONObject(0))),"dedup identity independent of nonrecurrent zone");
   for(String line:ics.split("\r\n"))check(line.getBytes("UTF-8").length<=73,"folded byte limit");
   JSONObject recurring=new JSONObject(event.toString()).put("rule","FREQ=YEARLY").put("duration","PT3600S").put("end",JSONObject.NULL);JSONObject after=parse(CalendarIcs.encode(new JSONArray().put(recurring))).getJSONObject(0);check(CalendarIcs.identity(recurring).equals(CalendarIcs.identity(after)),"annual recurrence zone preserved");
   JSONObject allDay=new JSONObject(event.toString()).put("allDay",1).put("start",CalendarIcs.parse("20261021",true)).put("end",CalendarIcs.parse("20261022",true));after=parse(CalendarIcs.encode(new JSONArray().put(allDay))).getJSONObject(0);check(after.getLong("start")==allDay.getLong("start")&&after.getInt("allDay")==1,"all-day exclusive end roundtrip");
   rejects(ics.replace("END:VCALENDAR", ""),"truncated calendar rejected");
   rejects(ics.replace("DTSTART:","DTSTART;TZID=Europe/Bucharest:"),"unsupported timezone parameter rejected");
   rejects(ics.replace("END:VEVENT","BEGIN:VALARM\r\nEND:VALARM\r\nEND:VEVENT"),"unsupported alarm rejected before import");
   rejects(ics.replace("BEGIN:VEVENT","BEGIN:VEVENT\r\nDTSTART:20261021T090000Z"),"duplicate start rejected");
   rejects(ics.replace("20261021T090000Z","20260230T090000Z"),"invalid dates rejected");
   rejects(ics.replace("VERSION:2.0","VERSION:1.0"),"unsupported version rejected");
   rejects(ics.replace("BEGIN:VEVENT","BEGIN:VEVENT\r\nDTSTART;VALUE=DATE:20261021"),"mixed duplicate start rejected");
   String longText=new String(new char[524289]).replace('\0','x');rejects(longText,"size bounded");
   System.out.println("PASS "+checks+" organizer algorithm checks");
  }finally{TimeZone.setDefault(old);}
 }
}
