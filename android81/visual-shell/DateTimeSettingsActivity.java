package ro.leo.visual;

import android.app.AlarmManager;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class DateTimeSettingsActivity extends LeoActivity {
    private int selected;
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(!form)show();}};
    protected void onResume(){super.onResume();IntentFilter f=new IntentFilter(Intent.ACTION_TIME_CHANGED);f.addAction(Intent.ACTION_TIMEZONE_CHANGED);f.addAction(Intent.ACTION_TIME_TICK);registerReceiver(updates,f);show();}
    protected void onPause(){unregisterReceiver(updates);super.onPause();}
    private boolean automatic(String key){return Settings.Global.getInt(getContentResolver(),key,0)!=0;}
    private void show() {
        if(!form && root!=null)for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof ListView){int position=((ListView)root.getChildAt(i)).getSelectedItemPosition();if(position>=0)selected=position;}
        final String stamp=new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date());
        list("Dată și oră",Arrays.asList("Data/ora · "+stamp,"Oră automată · "+(automatic(Settings.Global.AUTO_TIME)?"Da":"Nu"),"Fus automat · "+(automatic(Settings.Global.AUTO_TIME_ZONE)?"Da":"Nu"),"Fus · "+TimeZone.getDefault().getID(),"Format · "+(android.text.format.DateFormat.is24HourFormat(this)?"24 ore":"12 ore")),selected,new Choice(){public void pick(int i){
            selected=i;
            if(i==0) manual();else if(i==1) toggle(Settings.Global.AUTO_TIME);else if(i==2) toggle(Settings.Global.AUTO_TIME_ZONE);else if(i==3) zones();else format();
        }},"Actualiz.",new Runnable(){public void run(){show();}});
    }
    private void toggle(String key) {try{Settings.Global.putInt(getContentResolver(),key,automatic(key)?0:1);show();}catch(SecurityException denied){notice("Acces sistem indisponibil");}}
    private void format() {
        options("Format oră",new String[]{"24 ore","12 ore"},new Choice(){public void pick(int i){
            try {Settings.System.putString(getContentResolver(),Settings.System.TIME_12_24,i==0?"24":"12");show();}catch(SecurityException denied){notice("Modificarea setărilor nu este permisă");}
        }});
    }
    private void zones() {
        if(automatic(Settings.Global.AUTO_TIME_ZONE)){notice("Oprește întâi fusul automat");return;}
        final String[] ids={"Europe/Bucharest","Europe/Chisinau","Europe/London","Europe/Berlin","Europe/Paris","Europe/Rome","Europe/Madrid","Europe/Istanbul","UTC"};
        options("Fus orar",new String[]{"București","Chișinău","Londra","Berlin","Paris","Roma","Madrid","Istanbul","UTC"},new Choice(){public void pick(int i){
            try {((AlarmManager)getSystemService(ALARM_SERVICE)).setTimeZone(ids[i]);show();}catch(RuntimeException denied){notice("Fusul orar nu poate fi schimbat");}
        }});
    }
    private void manual() {
        if(automatic(Settings.Global.AUTO_TIME)){notice("Oprește întâi ora automată");return;}
        Calendar now=Calendar.getInstance();LinearLayout panel=fields("Setează data și ora");
        final EditText day=field(panel,"Zi",String.valueOf(now.get(Calendar.DAY_OF_MONTH)),InputType.TYPE_CLASS_NUMBER);
        final EditText month=field(panel,"Lună",String.valueOf(now.get(Calendar.MONTH)+1),InputType.TYPE_CLASS_NUMBER);
        final EditText year=field(panel,"An · 2000–2037",String.valueOf(now.get(Calendar.YEAR)),InputType.TYPE_CLASS_NUMBER);
        final EditText hour=field(panel,"Oră · 0–23",String.valueOf(now.get(Calendar.HOUR_OF_DAY)),InputType.TYPE_CLASS_NUMBER);
        final EditText minute=field(panel,"Minut · 0–59",String.valueOf(now.get(Calendar.MINUTE)),InputType.TYPE_CLASS_NUMBER);
        left=new Runnable(){public void run(){
            if(automatic(Settings.Global.AUTO_TIME)){notice("Ora automată este activă");show();return;}
            final long time;
            try {
                int y=Integer.parseInt(year.getText().toString());if(y<2000||y>2037)throw new IllegalArgumentException();
                Calendar value=Calendar.getInstance();value.clear();value.setLenient(false);
                value.set(y,Integer.parseInt(month.getText().toString())-1,Integer.parseInt(day.getText().toString()),Integer.parseInt(hour.getText().toString()),Integer.parseInt(minute.getText().toString()),0);time=value.getTimeInMillis();
            }catch(IllegalArgumentException invalid){notice("Dată sau oră invalidă");return;}
            options("Aplică noua dată/oră?",new String[]{"Anulează","Aplică"},new Choice(){public void pick(int i){
                if(i!=1)return;
                if(automatic(Settings.Global.AUTO_TIME)){notice("Ora automată este activă");return;}
                try {((AlarmManager)getSystemService(ALARM_SERVICE)).setTime(time);notice("Data și ora actualizate");show();}catch(RuntimeException denied){notice("Ora nu poate fi schimbată");}
            }});
        }};
        back=new Runnable(){public void run(){show();}};footer("Salvează","","Înapoi");day.requestFocus();
    }
}
