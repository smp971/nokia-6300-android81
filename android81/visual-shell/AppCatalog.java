package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;
import java.util.*;
/** Stable app IDs are independent of user-defined display order. */
final class AppCatalog {
    static final String[] NAMES=L.array(new String[]{"Telefon","Contacte","Mesaje","Ceas","Calculator","Galerie","Muzică","Fișiere","Setări","Aplicații Android","Notițe","Calendar","Video","Reportofon","Convertor","Descărcări","Fundaluri","Favorite","Blocare taste","Browser text","Configurări rapide","Configurare","Email","Terminal","Diagnostic","Radio FM","Meteo","Backup Leo","Ajutor Leo","Colecții vizuale","Cărți Leo","Jocuri Leo","Conexiuni Leo","Actualizări Leo","Cameră Leo"});
    static int[] parse(String value){ArrayList<Integer> ids=new ArrayList<Integer>();for(String part:value.split(","))try{int n=Integer.parseInt(part);if(n>=0&&n<NAMES.length&&!ids.contains(n))ids.add(n);}catch(NumberFormatException ignored){}for(int n=0;n<NAMES.length;n++)if(!ids.contains(n))ids.add(n);int[] result=new int[NAMES.length];for(int n=0;n<result.length;n++)result[n]=ids.get(n);return result;}
    static int[] order(Context c){return parse(c.getSharedPreferences("appearance",0).getString("app-order",""));}
    static final String[] CATEGORIES=L.array(new String[]{"Toate","Comunicare","Media","Unelte","Relaxare","Sistem"});
    static boolean settingsOnly(int id){return id==7||id==15||id==16||id==17||id==18||id==20||id==21||id==24||id==27||id==28||id==29||id==32||id==33;}
    static int category(int id){if(id==0||id==1||id==2||id==22)return 1;if(id==5||id==6||id==12||id==13||id==25||id==34)return 2;if(id==3||id==4||id==10||id==11||id==14||id==19||id==26)return 3;if(id==30||id==31)return 4;return 5;}
    static int[] menuOrder(Context c,int category){ArrayList<Integer> ids=new ArrayList<Integer>();for(int id:order(c))if(!settingsOnly(id)&&(category==0||category(id)==category))ids.add(id);int[] result=new int[ids.size()];for(int n=0;n<result.length;n++)result[n]=ids.get(n);return result;}
    static void save(Context c,int[] order){StringBuilder out=new StringBuilder();for(int id:order){if(out.length()>0)out.append(',');out.append(id);}c.getSharedPreferences("appearance",0).edit().putString("app-order",out.toString()).apply();}
    static int[] shortcuts(Context c){int[] ids={0,2,6,22};for(int i=0;i<ids.length;i++){int n=c.getSharedPreferences("appearance",0).getInt("shortcut"+i,ids[i]);if(n>=0&&n<NAMES.length)ids[i]=n;}return ids;}
}
