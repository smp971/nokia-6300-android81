package ro.leo.visual;
import android.content.*;import java.util.*;
/** Leo launches only; never reads Android task history. */
final class HomeAccess {
 static final String INTERNAL_HOME="ro.leo.visual.InternalHome";
 static Intent intent(Context c){return new Intent().setComponent(new ComponentName(c,INTERNAL_HOME));}
 static boolean internal(Intent intent){ComponentName target=intent.getComponent();return target!=null&&"ro.leo.visual".equals(target.getPackageName())&&INTERNAL_HOME.equals(target.getClassName());}
 static Intent sanitize(Intent incoming){Intent result=new Intent(incoming);if(!internal(result)){result.removeExtra("open_app");result.removeExtra("open_menu");result.removeExtra("edit_home");}return result;}

 static ArrayList<Integer> ids(String raw){ArrayList<Integer> out=new ArrayList<Integer>();for(String s:raw.split(","))try{int n=Integer.parseInt(s);if(n>=0&&n<AppCatalog.NAMES.length&&!out.contains(n)&&out.size()<12)out.add(n);}catch(NumberFormatException ignored){}return out;}
 static void save(Context c,String key,List<Integer> ids){c.getSharedPreferences("home-menu",0).edit().putString(key,android.text.TextUtils.join(",",ids)).apply();}
 static ArrayList<Integer> get(Context c,String key){return ids(c.getSharedPreferences("home-menu",0).getString(key,key.equals("favorites")?"0,2,3,6":""));}
 static void record(Context c,int id){if(id<0||id>=AppCatalog.NAMES.length)return;ArrayList<Integer> recent=get(c,"recent");recent.remove((Integer)id);recent.add(0,id);while(recent.size()>8)recent.remove(recent.size()-1);save(c,"recent",recent);}
 static void open(Context c,int id){c.startActivity(intent(c).putExtra("open_app",id));}
}
