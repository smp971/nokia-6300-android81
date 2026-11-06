package ro.leo.visual;
import ro.leo.i18n.L;
/** Stable actions shared by the global menu and its tests. */
final class GlobalMenuModel {
 static final int HOME=0,APPS=1,NOTIFICATIONS=2,QUICK=3,SETTINGS=4,POINTER=5,MORE=6,MEDIA=7,RECENT=8,LOCK=9,POWER=10,HELP=11,WORKSPACE=12,TRANSFERS=13,STUDIO=14,ANDROID=15,CLOSE=16;
 private static final int[] ROOT={HOME,APPS,NOTIFICATIONS,QUICK,SETTINGS,POINTER,MORE};
 private static final int[] TOOLS={MEDIA,RECENT,LOCK,POWER,HELP,TRANSFERS,WORKSPACE,STUDIO,ANDROID,CLOSE};
 static int[] actions(boolean tools){return (tools?TOOLS:ROOT).clone();}
 static String[] labels(boolean tools,boolean pointer){int[] ids=actions(tools);String[] rows=new String[ids.length];for(int i=0;i<ids.length;i++)rows[i]=label(ids[i],pointer);return rows;}
 static String label(int id,boolean pointer){switch(id){
 case HOME:return L.t("Acasă");case APPS:return L.t("Aplicații");case NOTIFICATIONS:return L.t("Notificări");case QUICK:return L.t("Configurări rapide");case SETTINGS:return L.t("Setări Leo");case POINTER:return pointer?L.t("Oprește mouse-ul"):L.t("Mouse din taste");case MORE:return L.t("Mai multe");case MEDIA:return L.t("Acum se redă");case RECENT:return L.t("Aplicații recente");case LOCK:return L.t("Blochează tastele");case POWER:return L.t("Pornire și oprire");case HELP:return L.t("Comenzile paginii");case WORKSPACE:return L.t("Spațiu de lucru");case TRANSFERS:return L.t("Centrul de transferuri");case STUDIO:return "Leo Studio";case ANDROID:return L.t("Android din taste");case CLOSE:return L.t("Închide aplicația curentă");default:throw new IllegalArgumentException("Unknown menu action");}}
}
