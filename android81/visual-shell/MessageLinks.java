package ro.leo.visual;
import android.text.util.Linkify;import android.text.SpannableString;import android.text.style.URLSpan;import java.util.*;
final class MessageLinks {
 static ArrayList<String> extract(String text){SpannableString s=new SpannableString(text==null?"":text.substring(0,Math.min(32768,text.length())));Linkify.addLinks(s,Linkify.WEB_URLS|Linkify.PHONE_NUMBERS|Linkify.EMAIL_ADDRESSES);LinkedHashSet<String> result=new LinkedHashSet<String>();for(URLSpan span:s.getSpans(0,s.length(),URLSpan.class)){String u=span.getURL();if(u.startsWith("https://")||u.startsWith("http://")||u.startsWith("tel:")||u.startsWith("mailto:"))result.add(u);if(result.size()==50)break;}return new ArrayList<String>(result);}
}
