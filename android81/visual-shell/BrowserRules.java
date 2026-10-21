package ro.leo.visual;
import java.io.IOException;import java.net.*;
/** Pure URL and text navigation rules; no network side effects. */
final class BrowserRules {
 static URL checked(String value)throws IOException{
  if(value==null||value.length()>2048||value.matches("(?s).*[\\x00-\\x20\\x7f].*"))throw new IOException("Adresă invalidă");
  URL u=new URL(value);if(!(u.getProtocol().equals("http")||u.getProtocol().equals("https"))||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getPort()>65535)throw new IOException("Doar adrese HTTP/HTTPS fără autentificare în URL");
  try{u.toURI();}catch(URISyntaxException e){throw new IOException("Adresă invalidă");}return u;
 }
 static String address(String text)throws IOException{
  String s=text==null?"":text.trim();if(s.isEmpty()||s.length()>512)throw new IOException("Adresă goală sau prea lungă");
  if(!s.contains("://")){if(s.contains(" ")||!s.contains("."))s="https://lite.duckduckgo.com/lite/?q="+URLEncoder.encode(s,"UTF-8");else s="https://"+s;}return checked(s).toExternalForm();
 }
 static String resolve(String base,String href)throws IOException{return checked(new URL(checked(base),href).toExternalForm()).toExternalForm();}
 static URL redirect(URL from,String location)throws IOException{if(location==null)throw new IOException("Redirecționare invalidă");URL next=checked(new URL(from,location).toExternalForm());if(from.getProtocol().equals("https")&&!next.getProtocol().equals("https"))throw new IOException("Redirecționare HTTPS spre HTTP refuzată");return next;}
 static int find(String text,String query,int after){if(text==null||query==null||query.isEmpty()||query.length()>128)return -1;int end=text.length()-query.length();if(end<0)return -1;int start=Math.max(0,Math.min(end+1,after));for(int i=start;i<=end;i++)if(text.regionMatches(true,i,query,0,query.length()))return i;for(int i=0;i<start&&i<=end;i++)if(text.regionMatches(true,i,query,0,query.length()))return i;return -1;}
}
