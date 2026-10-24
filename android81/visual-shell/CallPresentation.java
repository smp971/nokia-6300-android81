package ro.leo.visual;
import ro.leo.i18n.L;
/** Pure presentation rules. Numbers match the public Oreo Call state constants. */
final class CallPresentation {
 static String state(int s){switch(s){case 1:return L.t("Se apelează");case 2:return L.t("Apel primit");case 3:return L.t("În așteptare");case 4:return L.t("În convorbire");case 7:return L.t("Apel încheiat");case 8:return L.t("Alege SIM-ul");case 9:return L.t("Conectare");case 10:return L.t("Se închide");default:return L.t("Se verifică apelul");}}
 static int priority(int s){return s==2?0:s==4?1:s==1||s==9?2:s==3?3:s==7?9:4;}
 static String duration(long millis){long seconds=Math.max(0,millis/1000);return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
}
