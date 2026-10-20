package ro.leo.visual;
import android.content.Context;
import java.util.*;
/** Stable app IDs are independent of user-defined display order. */
final class AppCatalog {
    static final String[] NAMES={"Telefon","Contacte","Mesaje","Ceas","Calculator","Galerie","Muzică","Fișiere","Setări","Aplicații Android","Notițe","Calendar","Video","Reportofon","Convertor","Descărcări","Fundaluri","Favorite","Blocare taste","Browser text","Configurări rapide","Configurare","Email","Terminal"};
    static int[] parse(String value){ArrayList<Integer> ids=new ArrayList<Integer>();for(String part:value.split(","))try{int n=Integer.parseInt(part);if(n>=0&&n<NAMES.length&&!ids.contains(n))ids.add(n);}catch(NumberFormatException ignored){}for(int n=0;n<NAMES.length;n++)if(!ids.contains(n))ids.add(n);int[] result=new int[NAMES.length];for(int n=0;n<result.length;n++)result[n]=ids.get(n);return result;}
    static int[] order(Context c){return parse(c.getSharedPreferences("appearance",0).getString("app-order",""));}
    static void save(Context c,int[] order){StringBuilder out=new StringBuilder();for(int id:order){if(out.length()>0)out.append(',');out.append(id);}c.getSharedPreferences("appearance",0).edit().putString("app-order",out.toString()).apply();}
    static int[] shortcuts(Context c){int[] ids={0,2,6,22};for(int i=0;i<ids.length;i++){int n=c.getSharedPreferences("appearance",0).getInt("shortcut"+i,ids[i]);if(n>=0&&n<NAMES.length)ids[i]=n;}return ids;}
}
