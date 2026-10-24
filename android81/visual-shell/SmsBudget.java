package ro.leo.visual;
import ro.leo.i18n.L;
/** Android's encoding-aware estimate; sending still uses the selected SIM's divideMessage. */
final class SmsBudget {
 static String label(String text){int[] n=android.telephony.SmsMessage.calculateLength(text,false);return n[0]+(n[0]==1?" SMS":L.t(" SMS-uri"))+" · "+n[2]+L.t(" rămase · ")+(n[3]==1?"GSM-7":"Unicode");}
}
