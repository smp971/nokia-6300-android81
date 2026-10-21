package ro.leo.visual;
import android.content.Context;import java.io.*;import java.util.*;
final class SaveLocations {
 static LinkedHashMap<String,File> folders(Context c){LinkedHashMap<String,File> out=new LinkedHashMap<String,File>();for(Map.Entry<String,File> e:RecordingLocations.folders(c).entrySet())out.put(e.getKey(),e.getKey().equals("leo")?new File(c.getFilesDir(),"documents"):new File(e.getValue().getParentFile(),"Exports"));return out;}
 static String key(Context c){return c.getSharedPreferences("storage",0).getString("exports","leo");}
 static File selected(Context c)throws IOException{File f=folders(c).get(key(c));if(f==null)throw new IOException("Cardul selectat nu este disponibil");if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Locație inaccesibilă");return f;}
}
