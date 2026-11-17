package ro.leo.visual;

import ro.leo.i18n.L;

/** Labels only the saved observation; never starts a weather request. */
final class WeatherFreshness {
    static boolean stale(long now,long saved){return saved>now+300000 || saved<=0 || now-saved>=6*3600000L;}
    static String label(long now,long saved){
        if(saved<=0)return L.t("Ora actualizării necunoscută");
        if(saved>now+300000)return L.t("Ora datelor de verificat");
        long minutes=Math.max(0,now-saved)/60000;
        if(minutes<2)return L.t("Actualizat acum");
        if(minutes<60)return L.t("Actualizat acum ")+minutes+" min";
        long hours=minutes/60;
        if(hours<6)return L.t("Actualizat acum ")+hours+" h";
        return L.t("Date vechi · ")+(hours<48?hours+" h":hours/24+L.t(" zile"));
    }
}
