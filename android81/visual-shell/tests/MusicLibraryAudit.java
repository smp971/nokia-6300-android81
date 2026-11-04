package ro.leo.visual;
import android.app.*;import android.content.*;import android.database.*;import android.os.*;import android.provider.MediaStore;import java.io.*;
/** Read-only counts under the app UID. No playback, scans, filenames or content logged. */
public final class MusicLibraryAudit extends Instrumentation {
    public void onCreate(Bundle b){start();}
    public void onStart(){Context c=getTargetContext();Bundle out=new Bundle();
        out.putInt("read_permission",c.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE"));
        try(Cursor rows=c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},null,null,null)){
            out.putInt("indexed_audio",rows==null?-1:rows.getCount());

        }
        catch(RuntimeException error){out.putString("audio_query_error",error.getClass().getSimpleName());}
        try(Cursor rows=c.getContentResolver().query(MediaStore.Files.getContentUri("external"),new String[]{"_id"},null,null,null)){out.putInt("indexed_files",rows==null?-1:rows.getCount());}
        catch(RuntimeException error){out.putString("files_query_error",error.getClass().getSimpleName());}
        File[] dirs=c.getExternalFilesDirs(null);for(int i=0;i<dirs.length;i++)if(dirs[i]!=null){String path=dirs[i].getAbsolutePath();int cut=path.indexOf("/Android/data/");if(cut<0)continue;File music=new File(path.substring(0,cut),"Music");out.putBoolean("music_dir_"+i,music.isDirectory());out.putBoolean("music_readable_"+i,music.canRead());File[] files=music.listFiles();out.putInt("music_entries_"+i,files==null?-1:files.length);}
        finish(Activity.RESULT_OK,out);
    }
}
