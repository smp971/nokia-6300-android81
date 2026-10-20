package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.security.MessageDigest;

public final class SmsReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent) {
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if(parts==null || parts.length==0 || parts[0]==null) return;
        String address=parts[0].getOriginatingAddress(); if(address==null) address="Necunoscut";
        StringBuilder body=new StringBuilder();
        for(SmsMessage message:parts) if(message!=null && message.getMessageBody()!=null) body.append(message.getMessageBody());
        int sub=intent.getIntExtra("subscription",-1); long sent=parts[0].getTimestampMillis();
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            Object[] pdus=(Object[])intent.getExtras().get("pdus");
            if(pdus!=null) for(Object pdu:pdus) hash.update((byte[])pdu);
            hash.update(("|"+sub).getBytes("UTF-8"));
            StringBuilder key=new StringBuilder(); for(byte b:hash.digest()) key.append(String.format("%02x",b&255));
            try(SmsStore store=new SmsStore(context)) { store.received(key.toString(),address,body.toString(),sent,sub); }
            UnreadState.refresh(context);
            notify(context,"Mesaj SMS nou",address);
        } catch(Exception failed) { notify(context,"SMS primit: stocare indisponibilă",address); }
    }
    static void notify(Context context,String title,String number) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("leo-sms","Mesaje Leo",NotificationManager.IMPORTANCE_DEFAULT));
        manager.notify("leo-sms",number.hashCode(),build(context,title,number));
    }
    static Notification build(Context context,String title,String number){
        Intent open=new Intent(context,MessagesActivity.class).setData(new android.net.Uri.Builder().scheme("leo-thread").authority("local").appendPath(number).build()).putExtra("thread",number);
        PendingIntent click=PendingIntent.getActivity(context,number.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT);
        Intent read=new Intent(context,NotificationReadReceiver.class).setData(open.getData()).putExtra("thread",number).putExtra("through",System.currentTimeMillis());
        PendingIntent mark=PendingIntent.getBroadcast(context,number.hashCode(),read,PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context,"leo-sms").setSmallIcon(android.R.drawable.stat_notify_chat)
            .setCategory(Notification.CATEGORY_MESSAGE).setContentTitle(title).setContentText("Deschide Mesaje Leo").setContentIntent(click).setAutoCancel(true)
            .addAction(new Notification.Action.Builder(null,"Marchează ca citit",mark).build()).build();
    }
}
