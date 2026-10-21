package ro.leo.visual;
import java.net.*;import java.io.*;
public final class BrowserAlgorithms {
 static int checks;static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 static void reject(String url)throws Exception{boolean bad=false;try{BrowserRules.checked(url);}catch(IOException e){bad=true;}check(bad,"unsafe or malformed URL rejected");}
 public static void main(String[] args)throws Exception{
  check(BrowserRules.address("example.org").equals("https://example.org"),"address defaults HTTPS");
  check(BrowserRules.address("vreme București").startsWith("https://lite.duckduckgo.com/lite/?q=vreme+"),"explicit search encoded");
  check(BrowserRules.resolve("https://example.org/a/b","../c?q=1").equals("https://example.org/c?q=1"),"relative links resolved");
  check(BrowserRules.redirect(new URL("https://example.org/a"),"/b").toString().equals("https://example.org/b"),"same-origin redirect");
  boolean bad=false;try{BrowserRules.redirect(new URL("https://example.org/a"),"http://example.org/b");}catch(IOException e){bad=true;}check(bad,"TLS downgrade refused");
  for(String s:new String[]{"javascript:alert(1)","file:///etc/passwd","https://user:password@example.org/","https://example.org/a b","https://example.org/\nheader","http://example.org:99999/","https://"})reject(s);
  check(BrowserRules.find("ȘTIRI știri","știri",0)==0,"Romanian case-insensitive search");
  check(BrowserRules.find("ȘTIRI știri","știri",1)==6,"next occurrence");
  check(BrowserRules.find("ȘTIRI știri","știri",7)==0,"search wraps");
  check(BrowserRules.find("İx țară","țară",0)==3,"Unicode offsets stay valid");
  check(BrowserRules.find("abc","",0)==-1&&BrowserRules.find("abc","z",0)==-1,"empty and absent search");
  System.out.println("PASS "+checks+" browser URL and navigation checks; no network requests");
 }
}
