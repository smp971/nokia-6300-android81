package ro.leo.visual;
import java.text.Normalizer;
import java.util.Locale;

final class ContactKeys {
    static String digits(String value){return value==null?"":value.replaceAll("[^0-9]","");}
    static String t9(String value){
        if(value==null)return "";
        String normal=Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}+","");StringBuilder result=new StringBuilder();
        for(char c:normal.toCharArray()){if(c>='a'&&c<='z')result.append("22233344455566677778889999".charAt(c-'a'));else if(c>='0'&&c<='9')result.append(c);}
        return result.toString();
    }
    static int rank(String name,String number,String query){String q=digits(query),n=digits(number),t=t9(name);if(q.isEmpty())return 4;if(n.equals(q)||(q.length()>=7&&android.telephony.PhoneNumberUtils.compare(number,query)))return 0;if(t.startsWith(q))return 1;if(n.startsWith(q))return 2;return t.contains(q)||n.contains(q)?3:4;}
    static boolean matches(String name,String number,String query){String d=digits(query);return !d.isEmpty()&&(t9(name).contains(d)||digits(number).contains(d));}
}
