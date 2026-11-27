package ro.leo.visual;

import android.net.Uri;
import android.telephony.PhoneNumberUtils;

/** Keep service-code delimiters intact; only explicit Green + SIM reaches Telecom. */
final class DialDestination {
    static boolean serviceCode(String value){return value!=null&&value.matches("(?=.*[0-9])[\\*#][0-9\\*#+]{1,62}#");}
    static String parse(String input){
        String raw=input==null?"":input.trim();
        if(raw.indexOf('*')>=0||raw.indexOf('#')>=0)return serviceCode(raw)?raw:"";
        if(!raw.matches("[+0-9() .-]+"))return "";
        String number=PhoneNumberUtils.normalizeNumber(raw);
        return number.matches("\\+?[0-9]{3,15}")&&!number.replace("+","").matches("0+")?number:"";
    }
    static Uri uri(String destination){return Uri.fromParts("tel",destination,null);}
}
