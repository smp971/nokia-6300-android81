package ro.leo.visual;
import android.content.Context;import android.telecom.*;import android.telephony.*;
/** Resolve a physical slot through the telephony account, never by list order. */
final class PhoneAccounts {
 static String slot(Context c,String id){try{TelecomManager telecom=(TelecomManager)c.getSystemService(Context.TELECOM_SERVICE);TelephonyManager base=(TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);SubscriptionManager subs=SubscriptionManager.from(c);for(PhoneAccountHandle h:telecom.getCallCapablePhoneAccounts())if(h.getId().equals(id)){TelephonyManager phone=base.createForPhoneAccountHandle(h);if(phone!=null){SubscriptionInfo sim=subs.getActiveSubscriptionInfo(Integer.parseInt(phone.getNetworkSpecifier()));if(sim!=null)return "SIM "+(sim.getSimSlotIndex()+1);}}}catch(RuntimeException ignored){}return "Cont";}
}
