package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;import android.os.Environment;import java.io.*;import java.util.*;
/** Existing internal recordings remain visible after selecting an optional SD folder. */
final class RecordingLocations {
    static LinkedHashMap<String,File> folders(Context c){LinkedHashMap<String,File> result=new LinkedHashMap<String,File>();result.put("leo",new File(c.getFilesDir(),"documents/Recordings"));File[] external=c.getExternalFilesDirs(null);for(int i=1;i<external.length;i++)if(external[i]!=null&&Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(external[i])))result.put("sd"+i,new File(external[i],"Recordings"));return result;}
    static String key(Context c){return c.getSharedPreferences("storage",0).getString("recordings","leo");}
    static String label(Context c){return "leo".equals(key(c))?L.t("Documente Leo / Recordings"):L.t("Card SD / înregistrări Leo");}
    static File selected(Context c)throws IOException{File dir=folders(c).get(key(c));if(dir==null)throw new IOException("Selected storage not mounted");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Storage unavailable");return dir;}
}
