package ro.leo.radiotest;
import android.app.Activity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
public class RadioTest extends Activity {
    public void onCreate(Bundle state) {
        super.onCreate(state);
        final TextView text=new TextView(this);text.setText("Querying modem...");setContentView(text);
        final int sub=getIntent().getIntExtra("sub",1);
        final int mode=getIntent().getIntExtra("mode",-1);
        new Thread(new Runnable(){public void run(){
            String result;
            try {
                TelephonyManager tm=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
                boolean mutating=mode!=-1 || getIntent().getBooleanExtra("selectData",false)
                    || getIntent().getBooleanExtra("installOrangeApn",false)
                    || getIntent().getBooleanExtra("installVodafoneApn",false);
                if(mutating && ((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall())
                    throw new IllegalStateException("Finish calls first");
                String operator=tm.createForSubscriptionId(sub).getSimOperator();
                android.telephony.SubscriptionManager sm=(android.telephony.SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
                android.telephony.SubscriptionInfo info=sm.getActiveSubscriptionInfo(sub);
                if(mutating && info==null)throw new IllegalStateException("Active subscription missing");
                Log.i("LeoRadioTest","sub="+sub+" operator="+operator+" subscription="+(info==null?"missing":("slot="+info.getSimSlotIndex()+" mcc="+info.getMcc()+" mnc="+info.getMnc())));
                if(getIntent().getBooleanExtra("selectData",false)){
                    int slot=getIntent().getIntExtra("dataSlot",-1);
                    if(info==null||slot<0||slot>1||info.getSimSlotIndex()!=slot)throw new IllegalStateException("Active subscription/slot mismatch");
                    if(((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall())throw new IllegalStateException("Finish calls first");
                    Log.i("LeoRadioTest","DATA_BEFORE="+android.telephony.SubscriptionManager.getDefaultDataSubscriptionId());
                    sm.getClass().getMethod("setDefaultDataSubId",int.class).invoke(sm,sub);
                    TelephonyManager target=tm.createForSubscriptionId(sub);
                    target.getClass().getMethod("setDataEnabled",boolean.class).invoke(target,true);
                    Log.i("LeoRadioTest","DATA_SELECTED="+android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()+" requested_slot="+slot);
                }
                if(getIntent().getBooleanExtra("installOrangeApn",false)){
                    if(info==null || info.getSimSlotIndex()!=1 || info.getMcc()!=226 || info.getMnc()!=10)
                        throw new IllegalStateException("Not the verified Orange SIM 2");
                    android.net.Uri uri=android.net.Uri.parse("content://telephony/carriers");
                    android.database.Cursor cursor=getContentResolver().query(uri,new String[]{"_id"},"name=? AND numeric=?",new String[]{"Leo-Orange-Internet","22610"},null);
                    boolean exists=false;try{exists=cursor!=null && cursor.moveToFirst();}finally{if(cursor!=null)cursor.close();}
                    if(!exists){
                        android.content.ContentValues values=new android.content.ContentValues();
                        values.put("name","Leo-Orange-Internet");values.put("numeric","22610");
                        values.put("mcc","226");values.put("mnc","10");values.put("apn","net");
                        values.put("type","default");values.put("protocol","IP");values.put("roaming_protocol","IP");
                        values.put("carrier_enabled",1);values.put("sub_id",sub);
                        if(getContentResolver().insert(uri,values)==null)throw new IllegalStateException("Orange APN insert failed");
                        Log.i("LeoRadioTest","Orange internet APN added");
                    }else Log.i("LeoRadioTest","Orange internet APN already present");
                }
                if(getIntent().getBooleanExtra("installVodafoneApn",false)){
                    if(sub!=1 || info==null || info.getSimSlotIndex()!=0 || info.getMcc()!=226 || info.getMnc()!=1)
                        throw new IllegalStateException("Not the verified Vodafone subscription");
                    android.net.Uri uri=android.net.Uri.parse("content://telephony/carriers");
                    android.database.Cursor cursor=getContentResolver().query(uri,new String[]{"_id"},"name=? AND numeric=?",new String[]{"Leo-Vodafone-LTE","22601"},null);
                    boolean exists=false;try{exists=cursor!=null && cursor.moveToFirst();}finally{if(cursor!=null)cursor.close();}
                    if(!exists){
                        android.content.ContentValues values=new android.content.ContentValues();
                        values.put("name","Leo-Vodafone-LTE");values.put("numeric","22601");
                        values.put("mcc","226");values.put("mnc","01");values.put("apn","live.vodafone.com");
                        values.put("user","live");values.put("password","vodafone");
                        values.put("type","default,supl");values.put("protocol","IP");values.put("roaming_protocol","IP");
                        values.put("carrier_enabled",1);values.put("sub_id",sub);
                        if(getContentResolver().insert(uri,values)==null)throw new IllegalStateException("APN insert failed");
                        Log.i("LeoRadioTest","Vodafone trial APN added");
                    }else Log.i("LeoRadioTest","Vodafone trial APN already present");
                }
                try {
                    java.lang.reflect.Method binder=TelephonyManager.class.getDeclaredMethod("getITelephony");binder.setAccessible(true);
                    Object telephony=binder.invoke(tm);
                    java.lang.reflect.Method raf=Class.forName("com.android.internal.telephony.ITelephony").getMethod("getRadioAccessFamily",int.class,String.class);
                    Log.i("LeoRadioTest","RAF_SLOT0="+raf.invoke(telephony,0,getPackageName())+" RAF_SLOT1="+raf.invoke(telephony,1,getPackageName()));
                }catch(Exception e){Log.w("LeoRadioTest","RAF query unavailable",e);}
                java.lang.reflect.Method get=TelephonyManager.class.getMethod("getPreferredNetworkType",int.class);
                int before=(Integer)get.invoke(tm,sub);
                Log.i("LeoRadioTest","sub="+sub+" MODE_BEFORE="+before);
                // Only the reversible GSM/WCDMA/LTE automatic trial and its rollback are allowed.
                if(mode!=-1){
                    if(mode!=0 && mode!=9)throw new IllegalArgumentException("Unsupported trial mode");
                    Object accepted=TelephonyManager.class.getMethod("setPreferredNetworkType",int.class,int.class).invoke(tm,sub,mode);
                    Log.i("LeoRadioTest","sub="+sub+" SET_MODE="+mode+" accepted="+accepted);
                }
                result="SIM subscription "+sub+"\nModem network mode: "+get.invoke(tm,sub);
                Log.i("LeoRadioTest",result.replace('\n',' '));
            } catch(Exception e){Log.e("LeoRadioTest","Radio diagnostic failed",e);result=e.toString();}
            final String label=result;runOnUiThread(new Runnable(){public void run(){text.setText(label);}});
        }}).start();
    }
}
