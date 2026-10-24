package ro.leo.visual;
import ro.leo.i18n.L;

/** Actual boot progress, separate from carrier registration reported by Telecom. */
final class NetworkStartup {
    static String stage(){try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"sys.leo.radio.start_state","");}catch(Exception e){return "";}}
    static String label(String state,boolean connected,boolean airplane,long uptime){
        if(airplane)return L.t("MOD AVION");
        if(connected)return L.t("L E O · CONECTAT");
        if("failed".equals(state))return L.t("REȚEA · PORNIRE NEREUȘITĂ");
        if("preparing".equals(state)||"storage".equals(state))return L.t("REȚEA · PORNEȘTE MODEMUL");
        if("transport".equals(state))return L.t("REȚEA · PORNESC SIM-URILE");
        if("started".equals(state)&&uptime<180000)return L.t("REȚEA · CAUTĂ OPERATORUL");
        return uptime<60000?L.t("REȚEA · SE PREGĂTEȘTE"):"L E O · ACTIVE STANDBY";
    }
}
