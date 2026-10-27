package ro.leo.visual;
import android.content.*;import android.telephony.PhoneNumberUtils;import java.util.*;
/** Local Leo inbox organization; SMS are retained and carrier delivery is unaffected. */
final class ConversationPrefs {
 static String key(String address){String raw=address==null?"":address.trim();String normalized=PhoneNumberUtils.normalizeNumber(raw);return raw.matches("[+0-9() .\\-/]+")&&!normalized.isEmpty()?normalized.replaceFirst("^00","+"):raw.toLowerCase(Locale.ROOT);}
 static boolean has(Context c,String group,String address){return c.getSharedPreferences("sms-organization",0).getStringSet(group,Collections.<String>emptySet()).contains(key(address));}
 static void toggle(Context c,String group,String address){if(!Arrays.asList("pinned","archived","blocked").contains(group))throw new IllegalArgumentException();android.content.SharedPreferences p=c.getSharedPreferences("sms-organization",0);Set<String> set=new HashSet<String>(p.getStringSet(group,Collections.<String>emptySet()));String id=key(address);if(!set.remove(id)){if(set.size()>=500)throw new IllegalStateException();set.add(id);}if(!p.edit().putStringSet(group,set).commit())throw new IllegalStateException();}
 static boolean visible(Context c,int box,String address){boolean blocked=has(c,"blocked",address),archived=has(c,"archived",address);return box==2?blocked:box==1?archived&&!blocked:!blocked&&!archived;}
}
