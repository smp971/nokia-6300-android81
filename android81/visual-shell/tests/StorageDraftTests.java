package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.*;
/** Owned fixtures only: no SMS, microphone, radio setters or user-file enumeration. */
public final class StorageDraftTests extends Instrumentation {
    public void onCreate(Bundle b){start();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private void erase(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)erase(child);f.delete();}
    public void onStart(){Context c=getTargetContext();Bundle out=new Bundle();ArrayList<File> roots=new ArrayList<File>();String id=null,other=null;try{
        String suffix=".leo-owned-test-"+UUID.randomUUID();
        for(File folder:RecordingLocations.folders(c).values()){File dir=new File(folder,suffix);check(dir.mkdirs(),"fixture directory");roots.add(dir);}
        check(roots.size()>=2,"SD mounted for cross-volume test");File from=roots.get(0),to=roots.get(1);File source=new File(from,"Ștefan-test.txt");try(FileOutputStream f=new FileOutputStream(source)){f.write("Leo local · transfer verificat".getBytes("UTF-8"));}
        File copied=FileTransfers.transfer(from,source,to,to,false);check(source.exists()&&FileTransfers.digest(source).equals(FileTransfers.digest(copied)),"internal to SD verified copy");
        boolean refused=false;try{FileTransfers.transfer(from,source,to,to,true);}catch(IOException e){refused=true;}check(refused&&source.exists(),"conflict keeps original");
        check(FileTransfers.search(to,"stefan").size()==1,"accent-insensitive search");File returnDir=new File(from,"returned");check(returnDir.mkdir(),"return folder");File moved=FileTransfers.transfer(to,copied,from,returnDir,true);check(!copied.exists()&&FileTransfers.digest(source).equals(FileTransfers.digest(moved)),"SD to internal verified move");
        try(DraftStore store=new DraftStore(c)){id=store.save(null,"+10000000071","local one");other=store.save(null,"+10000000072","local two");}
        try(DraftStore store=new DraftStore(c)){check(id.equals(store.forNumber("+1 000 000 0071").id),"draft survives reopen and normalization");store.save(id,"+10000000071","edited");store.sent(id,"+10000000071","local one");check(store.forNumber("+10000000071")!=null,"old send acknowledgement preserves newer edit");store.sent(id,"+10000000071","edited");check(store.forNumber("+10000000071")==null&&store.forNumber("+10000000072")!=null,"matching acknowledgement deletes only own draft");}
        out.putString("result","PASS real internal/SD copy+move+SHA256, conflict preservation, accent search, independent persistent drafts, edited draft acknowledgement guard");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{try(DraftStore store=new DraftStore(c)){if(id!=null)store.remove(id);if(other!=null)store.remove(other);}for(File root:roots)erase(root);}
        finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);
    }
}
