package ro.leo.visual;

import android.content.*;
import android.os.Bundle;
import android.text.InputType;
import android.util.AtomicFile;
import android.widget.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

public final class FilesActivity extends LeoActivity {
    private File base,current;
    private File selected;
    private final ArrayList<File> entries=new ArrayList<File>();
    protected void onCreate(Bundle state) { super.onCreate(state); locations(); }
    private void locations() {
        base=null; current=null; selected=null;
        final ArrayList<File> values=new ArrayList<File>(); ArrayList<String> labels=new ArrayList<String>();
        for(Map.Entry<String,File> root:LeoFileProvider.roots(this).entrySet()) {
            values.add(root.getValue()); labels.add(root.getKey().equals("phone")?"Memorie telefon":root.getKey().equals("leo")?"Documente Leo":"Card SD · folder Leo");
        }
        list("Fișiere",labels,0,new Choice() { public void pick(int i) { base=values.get(i); browse(base); } },"Android",new Runnable() { public void run() {
            try { startActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)); }
            catch(RuntimeException unavailable) { notice("Selectorul Android nu este disponibil"); }
        }});
    }
    private void browse(final File directory) {
        try { if(base==null || !LeoFileProvider.inside(base,directory) || !directory.isDirectory()) { notice("Stocare indisponibilă"); return; } }
        catch(IOException invalid) { notice("Locație invalidă"); return; }
        current=directory; selected=null;
        worker.execute(new Runnable() { public void run() {
            File[] contents=directory.listFiles(); final ArrayList<File> found=new ArrayList<File>();
            if(contents!=null) for(File file:contents) {
                try { if(LeoFileProvider.inside(base,file) && !file.getName().startsWith(".leo-")) found.add(file); } catch(IOException ignored) { }
            }
            Collections.sort(found,new Comparator<File>() { public int compare(File a,File b) { if(a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1; return a.getName().compareToIgnoreCase(b.getName()); } });
            final boolean inaccessible=contents==null;
            ui(new Runnable() { public void run() {
                entries.clear(); entries.addAll(found); ArrayList<String> labels=new ArrayList<String>();
                for(File file:found) labels.add((file.isDirectory()?"▸ ":"")+file.getName()+(file.isDirectory()?"":"\n"+file.length()+" octeți"));
                list(directory.equals(base)?"Fișiere":directory.getName(),labels,0,new Choice() { public void pick(int i) {
                    selected=entries.get(i); if(selected.isDirectory()) browse(selected); else fileOptions(selected);
                } },"Opțiuni",new Runnable() { public void run() { directoryOptions(); } });
                back=new Runnable() { public void run() { if(directory.equals(base)) locations(); else browse(directory.getParentFile()); } };
                if(inaccessible) notice("Folderul nu poate fi citit; verifică permisiunile");
            }});
        }});
    }
    private File named(String name) throws IOException {
        name=name.trim(); if(name.isEmpty() || name.length()>120 || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) throw new IOException();
        File target=new File(current,name); if(!LeoFileProvider.inside(base,target)) throw new IOException(); return target;
    }
    private void directoryOptions() {
        String[] actions=current.equals(base)?new String[]{"Folder nou","Fișier text nou","Reîmprospătează"}
            :new String[]{"Folder nou","Fișier text nou","Reîmprospătează","Redenumește folderul","Șterge folderul gol"};
        options("Fișiere",actions,new Choice() { public void pick(int i) {
            if(i==0) nameForm(null); else if(i==1) textFile(null); else if(i==2) browse(current);
            else if(i==3) nameForm(current);
            else options("Ștergi folderul gol?",new String[]{"Păstrează","Șterge"},new Choice() { public void pick(int yes) {
                if(yes==1) { File parent=current.getParentFile(); if(current.delete()) browse(parent); else notice("Folderul nu este gol sau accesul este refuzat"); }
            }});
        }});
    }
    private void nameForm(final File source) {
        LinearLayout fields=fields(source==null?"Folder nou":"Redenumește"); final EditText name=field(fields,"Nume",source==null?"":source.getName(),InputType.TYPE_CLASS_TEXT);
        left=new Runnable() { public void run() {
            try {
                File previous=current;
                if(source!=null) current=source.getParentFile();
                File target;
                try { target=named(name.getText().toString()); } finally { current=previous; }
                if(target.exists()) { notice("Numele există deja"); return; }
                if(source==null?!target.mkdir():!source.renameTo(target)) throw new IOException(); browse(source!=null && source.equals(previous)?target:previous);
            } catch(IOException failure) { notice("Operația a eșuat; verifică numele și accesul"); }
        }};
        back=new Runnable() { public void run() { browse(current); } }; footer("Salvează","","Șterge"); name.requestFocus();
    }
    private void fileOptions(final File file) {
        options(file.getName(),new String[]{"Deschide","Editează text UTF-8","Redenumește","Șterge"},new Choice() { public void pick(int i) {
            if(i==0) open(file); else if(i==1) textFile(file); else if(i==2) nameForm(file);
            else options("Ștergi fișierul?",new String[]{"Păstrează","Șterge"},new Choice() { public void pick(int yes) {
                if(yes==1) { if(!file.delete()) notice("Ștergerea a eșuat; folderele trebuie să fie goale"); browse(current); }
            }});
        }});
    }
    private void open(File file) {
        try {
            android.net.Uri uri=LeoFileProvider.uri(this,file);
            Intent view=new Intent(Intent.ACTION_VIEW);
            String extension=file.getName().contains(".")?file.getName().substring(file.getName().lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
            String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            view.setDataAndType(uri,mime==null?"application/octet-stream":mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch(Exception failure) { notice("Nicio aplicație disponibilă pentru acest fișier"); }
    }
    private void textFile(final File source) {
        String initial="";
        if(source!=null) {
            try {
                if(!source.isFile() || source.length()>32768) { notice("Editorul acceptă text UTF-8 de maximum 32 KiB"); return; }
                byte[] bytes=new AtomicFile(source).readFully();
                initial=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch(Exception invalid) { notice("Fișierul nu este text UTF-8 accesibil"); return; }
        }
        LinearLayout fields=fields(source==null?"Text nou":source.getName());
        final EditText name=source==null?field(fields,"Nume fișier","",InputType.TYPE_CLASS_TEXT):null;
        final EditText body=field(fields,"Text UTF-8",initial,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE); body.setMinLines(4);
        body.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32768)});
        if(name!=null) { name.setNextFocusDownId(body.getId()); body.setNextFocusUpId(name.getId()); }
        left=new Runnable() { public void run() {
            FileOutputStream stream=null; AtomicFile atomic=null;
            try {
                File target=source==null?named(name.getText().toString()):source;
                if(source==null && target.exists()) { notice("Fișierul există deja"); return; }
                byte[] bytes=body.getText().toString().getBytes(StandardCharsets.UTF_8); if(bytes.length>32768) { notice("Textul depășește 32 KiB"); return; }
                atomic=new AtomicFile(target); stream=atomic.startWrite(); stream.write(bytes); atomic.finishWrite(stream); browse(current); notice("Fișier salvat");
            } catch(Exception failure) { if(atomic!=null && stream!=null) atomic.failWrite(stream); notice("Salvarea a eșuat; textul rămâne în editor"); }
        }};
        back=new Runnable() { public void run() { options("Renunți la text?",new String[]{"Continuă","Renunță"},new Choice() { public void pick(int i) { if(i==1) browse(current); } }); } };
        footer("Salvează","Următor","Șterge"); (name==null?body:name).requestFocus();
    }
}
