package ro.leo.visual;
import ro.leo.i18n.L;

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
        String address=parts[0].getOriginatingAddress(); if(address==null) address=L.t("Necunoscut");
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
            notifyReceived(context,address,body.toString());
        } catch(Exception failed) { notify(context,L.t("SMS primit: stocare indisponibilă"),address); }
    }
    static void notifyReceived(Context c,String number,String body){if(ConversationPrefs.has(c,"blocked",number))return;AccessibilityPrefs.alert(c,"sms");NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);m.createNotificationChannel(new NotificationChannel("leo-sms",L.t("Mesaje Leo"),NotificationManager.IMPORTANCE_DEFAULT));m.notify("leo-sms",number.hashCode(),build(c,L.t("Mesaj SMS nou"),number,c.getSharedPreferences("sms-options",0).getBoolean("preview",false)?body:null));}
    static void notify(Context context,String title,String number) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("leo-sms",L.t("Mesaje Leo"),NotificationManager.IMPORTANCE_DEFAULT));
        manager.notify("leo-sms",number.hashCode(),build(context,title,number));
    }
    static Notification build(Context context,String title,String number){return build(context,title,number,null);}
    static Notification build(Context context,String title,String number,String preview){
        Intent open=new Intent(context,MessagesActivity.class).setData(new android.net.Uri.Builder().scheme("leo-thread").authority("local").appendPath(number).build()).putExtra("thread",number);
        PendingIntent click=PendingIntent.getActivity(context,number.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT);
        Intent read=new Intent(context,NotificationReadReceiver.class).setData(open.getData()).putExtra("thread",number).putExtra("through",System.currentTimeMillis());
        PendingIntent mark=PendingIntent.getBroadcast(context,number.hashCode(),read,PendingIntent.FLAG_UPDATE_CURRENT);
        Intent answer=new Intent(context,MessagesActivity.class).setData(new android.net.Uri.Builder().scheme("leo-reply").authority("local").appendPath(number).build()).putExtra("number",number);PendingIntent reply=PendingIntent.getActivity(context,number.hashCode(),answer,PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context,"leo-sms").setSmallIcon(android.R.drawable.stat_notify_chat)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_MESSAGE).setContentTitle(title).setContentText(preview==null?L.t("Deschide Mesaje Leo"):preview.substring(0,Math.min(160,preview.length()))).setContentIntent(click).setAutoCancel(true)
            .addAction(new Notification.Action.Builder(null,L.t("Răspunde în Leo"),reply).build()).addAction(new Notification.Action.Builder(null,L.t("Marchează ca citit"),mark).build()).build();
    }
}
