package ro.leo.visual;
import android.content.*;import android.telephony.SmsMessage;
/** Explicit PendingIntent receiver; a report never resends a message. */
public final class SmsDeliveryReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){String token=i.getStringExtra("token");byte[] pdu=i.getByteArrayExtra("pdu");if(token==null||pdu==null)return;try{String format=i.getStringExtra("format");SmsMessage sms=format==null?SmsMessage.createFromPdu(pdu):SmsMessage.createFromPdu(pdu,format);if(sms==null||!sms.isStatusReportMessage())return;try(SmsStore store=new SmsStore(c)){boolean delivered=store.delivery(token,i.getIntExtra("part",-1),sms.getStatus());if(delivered)c.getSharedPreferences("sms-outgoing",0).edit().putString("status","Raport operator · SMS livrat").apply();}c.getSharedPreferences("sms-outgoing",0).edit().putLong("delivery-updated",System.currentTimeMillis()).apply();}catch(RuntimeException ignored){android.util.Log.w("LeoSms","Invalid delivery report");}}
}
