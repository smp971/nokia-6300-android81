package ro.leo.visual;
/** Android's encoding-aware estimate; sending still uses the selected SIM's divideMessage. */
final class SmsBudget {
 static String label(String text){int[] n=android.telephony.SmsMessage.calculateLength(text,false);return n[0]+(n[0]==1?" SMS":" SMS-uri")+" · "+n[2]+" rămase · "+(n[3]==1?"GSM-7":"Unicode");}
}
