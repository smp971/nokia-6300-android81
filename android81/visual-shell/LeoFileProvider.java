package ro.leo.visual;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.*;

/** Exact, read-only URI grants for selected files inside explicitly supported roots. */
public final class LeoFileProvider extends ContentProvider {
    static LinkedHashMap<String,File> roots(Context c) {
        LinkedHashMap<String,File> roots=new LinkedHashMap<String,File>();
        roots.put("phone",Environment.getExternalStorageDirectory());
        File docs=new File(c.getFilesDir(),"documents"); docs.mkdirs(); roots.put("leo",docs);
        File[] external=c.getExternalFilesDirs(null);
        for(int i=1;i<external.length;i++) if(external[i]!=null) {
            File app=external[i].getParentFile();
            File data=app==null?null:app.getParentFile();
            File android=data==null?null:data.getParentFile();
            File volume=android==null?null:android.getParentFile();
            // Derive only the public volume backing our Android/data/<package>/files.
            // Keep canonical-path checks below for every file and URI grant.
            if(volume!=null && app.getName().equals(c.getPackageName()) && data.getName().equals("data")
                    && android.getName().equals("Android") && volume.isDirectory()
                    && Environment.isExternalStorageRemovable(external[i])) roots.put("sd"+i,volume);
        }
        return roots;
    }
    static boolean inside(File root,File file) throws IOException {
        String base=root.getCanonicalPath(),path=file.getCanonicalPath(); return path.equals(base) || path.startsWith(base+File.separator);
    }
    static Uri uri(Context c,File file) throws IOException {
        for(Map.Entry<String,File> root:roots(c).entrySet()) if(inside(root.getValue(),file)) {
            String relative=file.getCanonicalPath().substring(root.getValue().getCanonicalPath().length());
            while(relative.startsWith(File.separator)) relative=relative.substring(1);
            return new Uri.Builder().scheme("content").authority("ro.leo.visual.files").appendPath(root.getKey()).appendPath(relative).build();
        }
        File cache=new File(c.getCacheDir(),"shared");if(inside(cache,file))return new Uri.Builder().scheme("content").authority("ro.leo.visual.files").appendPath("share").appendPath(file.getName()).build();
        throw new FileNotFoundException();
    }
    private File resolve(Uri uri) throws IOException {
        List<String> segments=uri.getPathSegments(); if(segments.size()!=2) throw new FileNotFoundException();
        File base=segments.get(0).equals("share")?new File(getContext().getCacheDir(),"shared"):roots(getContext()).get(segments.get(0)); if(base==null) throw new FileNotFoundException();
        File file=new File(base,segments.get(1)); if(!inside(base,file)) throw new FileNotFoundException(); return file;
    }
    public boolean onCreate() { return true; }
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode)) throw new FileNotFoundException("Read-only URI");
        try { File f=resolve(uri); if(!f.isFile()) throw new FileNotFoundException(); return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY); }
        catch(IOException failure) { throw new FileNotFoundException(); }
    }
    public String getType(Uri uri) {
        try { String name=resolve(uri).getName(); int dot=name.lastIndexOf('.'); String mime=dot<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot+1).toLowerCase(Locale.ROOT)); return mime==null?"application/octet-stream":mime; }
        catch(IOException failure) { return "application/octet-stream"; }
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor result=new MatrixCursor(columns);
        try { File f=resolve(uri); Object[] row=new Object[columns.length]; for(int i=0;i<row.length;i++) row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?f.getName():OpenableColumns.SIZE.equals(columns[i])?f.length():null; result.addRow(row); }
        catch(IOException failure) { }
        return result;
    }
    public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
