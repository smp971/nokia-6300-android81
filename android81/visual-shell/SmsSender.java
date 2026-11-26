package ro.leo.visual;

import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
import android.telephony.SmsManager;
import java.util.*;
import org.json.*;

/** Shared native/WebUI transport. One durable pending send, same callbacks and archive. */
final class SmsSender {
    static void number(String value){if(value==null||!value.matches("\\+?[0-9]{3,15}")||value.replace("+","").matches("0+"))throw new IllegalArgumentException("Număr invalid");}
    static synchronized String send(Context c,SmsManager manager,String number,String text,String draftId)throws Exception {
        number(number);
        if(text==null||text.trim().isEmpty()||text.length()>1600)throw new IllegalArgumentException("Mesaj gol sau prea lung");
        SharedPreferences pending=c.getSharedPreferences("sms-outgoing",0);
        if(!pending.getString("token","").isEmpty())throw new IllegalStateException("Există un SMS în așteptare; verifică starea înainte de repetare");
        ArrayList<String> parts=manager.divideMessage(text);
        if(parts.isEmpty()||parts.size()>10)throw new IllegalArgumentException("Maximum 10 segmente SMS");
        String token=UUID.randomUUID().toString();JSONArray states=new JSONArray();
        ArrayList<PendingIntent> sent=new ArrayList<PendingIntent>(),delivered=new ArrayList<PendingIntent>();
        for(int i=0;i<parts.size();i++){
            states.put(0);
            Intent result=new Intent(c,SmsStatusReceiver.class).setData(Uri.parse("leo-sms:"+token+"/"+i)).putExtra("token",token).putExtra("part",i);
            sent.add(PendingIntent.getBroadcast(c,i,result,PendingIntent.FLAG_UPDATE_CURRENT));
            Intent delivery=new Intent(c,SmsDeliveryReceiver.class).setData(Uri.parse("leo-delivery:"+token+"/"+i)).putExtra("token",token).putExtra("part",i);
            delivered.add(PendingIntent.getBroadcast(c,i,delivery,PendingIntent.FLAG_UPDATE_CURRENT));
        }
        long date=System.currentTimeMillis();int sub=manager.getSubscriptionId();
        try(SmsStore store=new SmsStore(c)){store.outgoing(token,number,text,date,sub);store.requestDelivery(token,parts.size());}
        if(!pending.edit().putString("token",token).putString("parts",states.toString()).putString("number",number).putString("body",text).putString("draft-id",draftId==null?"":draftId).putLong("date",date).putInt("sub",sub).commit()){
            try(SmsStore store=new SmsStore(c)){store.completed(token,false);}throw new IllegalStateException("Starea SMS nu poate fi salvată; mesajul nu a fost trimis");
        }
        try{manager.sendMultipartTextMessage(number,null,parts,sent,delivered);return token;}
        catch(RuntimeException failure){try(SmsStore store=new SmsStore(c)){store.completed(token,false);}pending.edit().clear().commit();throw new IllegalStateException("Trimiterea a eșuat; verifică starea înainte de reîncercare");}
    }
}
