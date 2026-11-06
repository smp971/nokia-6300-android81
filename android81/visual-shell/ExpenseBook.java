package ro.leo.visual;
import org.json.*;import java.math.*;import java.io.*;import java.text.*;import java.util.*;
final class ExpenseBook {
 static long cents(String raw)throws IOException{try{BigDecimal n=new BigDecimal(raw.trim().replace(',','.'));if(n.signum()<=0||n.scale()>2)throw new IOException("Use a positive amount with at most two decimals");long value=n.movePointRight(2).longValueExact();if(value>100000000000L)throw new IOException("Amount too large");return value;}catch(ArithmeticException|NumberFormatException e){throw new IOException("Invalid amount");}}
 static String date(String value)throws IOException{if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IOException("Use YYYY-MM-DD");SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);try{f.parse(value);}catch(ParseException e){throw new IOException("Invalid date");}return value;}
 static String money(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
 static LinkedHashMap<String,Long> totals(JSONArray entries,String month,boolean byCategory)throws Exception{LinkedHashMap<String,Long> totals=new LinkedHashMap<>();for(int n=0;n<entries.length();n++){JSONObject e=entries.getJSONObject(n);if(!e.getString("date").startsWith(month+"-"))continue;String key=e.getString("currency")+(byCategory?" · "+e.getString("category"):"");Long old=totals.get(key);totals.put(key,Math.addExact(old==null?0:old,e.getLong("cents")));}return totals;}
}
