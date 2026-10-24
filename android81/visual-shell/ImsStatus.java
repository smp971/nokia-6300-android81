package ro.leo.visual;
import ro.leo.i18n.L;
import android.os.IBinder;
/** Registration is read from modem callbacks and checked against a live IMS Binder. */
final class ImsStatus {
    static String property(String key){try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");}catch(Exception e){return "";}}
    static boolean registered(int slot){
        if(!Integer.toString(slot).equals(property("sys.leo.ims.slot"))||!"1".equals(property("sys.leo.ims.registration"))||!"1".equals(property("sys.leo.ims.voice")))return false;
        try{IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("checkService",String.class).invoke(null,"ims");return b!=null&&b.pingBinder();}catch(Exception e){return false;}
    }
    static String label(int slot){
        if(registered(slot))return L.t("VoLTE · Înregistrat");
        if(!"1".equals(property("persist.leo.ims.enabled")))return L.t("VoLTE · Oprit");
        if(!Integer.toString(slot).equals(property("sys.leo.ims.slot")))return L.t("VoLTE · Urmează SIM-ul de date");
        return "waiting_lte".equals(property("sys.leo.ims.state"))?L.t("VoLTE · Așteaptă 4G"):L.t("VoLTE · Se înregistrează");
    }
}
