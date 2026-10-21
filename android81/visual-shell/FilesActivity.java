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
    private int browseGeneration;private boolean busy;private String artworkTitle="";private final RowArtwork artwork=new RowArtwork(this);
    private File base,current;private int focused,sort;private LinearLayout listing;
    private File selected;private final LinkedHashSet<File> marked=new LinkedHashSet<File>();private final ArrayList<File> clipboard=new ArrayList<File>();private File sourceBoundary;private boolean cut;
    private final ArrayList<File> entries=new ArrayList<File>();
    protected void onCreate(Bundle state) { super.onCreate(state);sort=getSharedPreferences("files-ui",0).getInt("sort",0);String folder=getIntent().getStringExtra("folder");if(folder!=null){try{File target=new File(folder);for(File candidate:LeoFileProvider.roots(this).values())if(LeoFileProvider.inside(candidate,target)&&target.isDirectory()){base=candidate;browse(target);return;}}catch(IOException ignored){}}locations(); }
    private void locations() {
        browseGeneration++;base=null; current=null; selected=null;marked.clear();
        final ArrayList<File> values=new ArrayList<File>(); ArrayList<String> labels=new ArrayList<String>();
        for(Map.Entry<String,File> root:LeoFileProvider.roots(this).entrySet()) {
            values.add(root.getValue()); labels.add((root.getKey().equals("phone")?"Memorie telefon":root.getKey().equals("leo")?"Documente Leo":"Card SD")+"\nLiber · "+android.text.format.Formatter.formatFileSize(this,root.getValue().getUsableSpace()));
        }
        list("Fișiere",labels,0,new Choice() { public void pick(int i) { base=values.get(i); browse(base); } },"Android",new Runnable() { public void run() {
            try { startActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)); }
            catch(RuntimeException unavailable) { notice("Selectorul Android nu este disponibil"); }
        }});
    }
    private void browse(final File directory) {
        try { if(base==null || !LeoFileProvider.inside(base,directory) || !directory.isDirectory()) { notice("Stocare indisponibilă"); return; } }
        catch(IOException invalid) { notice("Locație invalidă"); return; }
        final int request=++browseGeneration;final File boundary=base;
        if(!directory.equals(current))focused=0;current=directory; selected=null;
        worker.execute(new Runnable() { public void run() {
            File[] contents=directory.listFiles(); final ArrayList<File> found=new ArrayList<File>();
            if(contents!=null) for(File file:contents) {
                try { if(LeoFileProvider.inside(boundary,file) && !file.getName().startsWith(".leo-")) found.add(file); } catch(IOException ignored) { }
            }
            Collections.sort(found,new Comparator<File>() { public int compare(File a,File b) { if(a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1; return sort==1?Long.compare(b.lastModified(),a.lastModified()):sort==2?Long.compare(b.length(),a.length()):a.getName().compareToIgnoreCase(b.getName()); } });
            final boolean inaccessible=contents==null;
            ui(new Runnable() { public void run() {
                if(request!=browseGeneration)return;entries.clear(); entries.addAll(found); ArrayList<String> labels=new ArrayList<String>();
                for(File file:found) labels.add((marked.contains(file)?"✓ ":file.isDirectory()?"▸ ":"")+file.getName()+(file.isDirectory()?"":"\n"+file.length()+" octeți"));
                artworkTitle=directory.equals(base)?"Fișiere":directory.getName();list(artworkTitle,labels,Math.min(focused,Math.max(0,found.size()-1)),new Choice() { public void pick(int i) {
                    selected=entries.get(i); if(selected.isDirectory()) browse(selected); else fileOptions(selected);
                } },"Opțiuni",new Runnable() { public void run() { directoryOptions(); } });listing=root;if(!entries.isEmpty())selected=entries.get(Math.min(focused,entries.size()-1));
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
        final ArrayList<String> actions=new ArrayList<String>(Arrays.asList("Folder nou","Fișier text nou","Reîmprospătează","Caută în această stocare","Marchează fișierele de aici","Selecție · "+marked.size(),"Lipește aici · "+clipboard.size()));if(!current.equals(base)){actions.add("Redenumește folderul");actions.add("Șterge folderul gol");}
        final int info=actions.size();actions.add("Spațiu și folder");actions.add("Triere");options("Fișiere",actions.toArray(new String[0]),new Choice(){public void pick(int i){if(i==info){metadata(current);return;}if(i==info+1){options("Triere",new String[]{"Nume","Cele mai recente","Cele mai mari"},new Choice(){public void pick(int mode){sort=mode;getSharedPreferences("files-ui",0).edit().putInt("sort",sort).apply();browse(current);}});return;}if(i==0)nameForm(null);else if(i==1)textFile(null);else if(i==2)browse(current);else if(i==3)search();else if(i==4){for(File f:entries)if(f.isFile()&&marked.size()<100)marked.add(f);browse(current);}else if(i==5)selection();else if(i==6)paste();else if(i==7)nameForm(current);else options("Ștergi folderul gol?",new String[]{"Păstrează","Șterge"},new Choice(){public void pick(int yes){if(yes==1){File parent=current.getParentFile();if(current.delete())browse(parent);else notice("Folderul nu este gol sau accesul este refuzat");}}});}});
    }
    private void selection(){options("Selecție · "+marked.size(),new String[]{"Copiază selecția","Mută selecția","Anulează marcajele"},new Choice(){public void pick(int i){if(i==2){marked.clear();browse(current);}else prepareTransfer(new ArrayList<File>(marked),i==1);}});}
    private void prepareTransfer(List<File> files,boolean move){if(files.isEmpty()){notice("Marchează întâi fișierele");return;}clipboard.clear();clipboard.addAll(files);sourceBoundary=base;cut=move;locations();notice("Alege destinația, apoi Opțiuni → Lipește aici. Numele existente nu sunt suprascrise.");}
    private void paste(){if(clipboard.isEmpty()){notice("Niciun fișier de lipit");return;}final File from=sourceBoundary,to=base,folder=current;final ArrayList<File> files=new ArrayList<File>(clipboard);final boolean move=cut;options((move?"Muți ":"Copiezi ")+files.size()+" fișiere aici?",new String[]{"Anulează","Continuă"},new Choice(){public void pick(int yes){if(yes!=1)return;busy=true;begin("Transfer fișiere");final TextView progressText=text("Se copiază și se verifică…",18);root.addView(progressText);final ProgressBar progressBar=new ProgressBar(FilesActivity.this,null,android.R.attr.progressBarStyleHorizontal);progressBar.setMax(100);root.addView(progressBar);footer("","","Înapoi");back=new Runnable(){public void run(){notice("Transfer în curs");}};worker.execute(new Runnable(){public void run(){int ok=0;String problem="";for(File f:files)try{final int index=ok;final String name=f.getName();FileTransfers.transfer(from,f,to,folder,move,new FileTransfers.Progress(){long last;public void update(final long done,final long total,final String phase){long now=android.os.SystemClock.elapsedRealtime();if(done!=total&&now-last<160)return;last=now;ui(new Runnable(){public void run(){progressText.setText((index+1)+" / "+files.size()+" · "+name+"\n"+phase+" · "+android.text.format.Formatter.formatFileSize(FilesActivity.this,done)+" / "+android.text.format.Formatter.formatFileSize(FilesActivity.this,total));progressBar.setProgress(total<=0?0:(int)Math.min(100,done*100/total));}});}});ok++;}catch(IOException e){problem=e.getMessage();}final int count=ok;final String error=problem;ui(new Runnable(){public void run(){busy=false;clipboard.clear();browse(folder);notice(count+" / "+files.size()+" finalizate"+(error.isEmpty()?"":" · "+error));}});}});}});}
    private void search() {
        final File boundary=base, previous=current;
        LinearLayout panel=fields("Caută fișiere");
        final EditText query=field(panel,"O parte din numele fișierului","",InputType.TYPE_CLASS_TEXT);
        left=new Runnable(){public void run(){
            final String term=query.getText().toString().trim();
            if(term.isEmpty()){notice("Scrie ce cauți");return;}
            worker.execute(new Runnable(){public void run(){
                try {
                    final List<File> found=FileTransfers.search(boundary,term);
                    ui(new Runnable(){public void run(){
                        ArrayList<String> names=new ArrayList<String>();
                        for(File f:found)names.add(f.getName()+"\n"+f.getParentFile().getName());
                        list("Rezultate · "+found.size(),names,0,new Choice(){public void pick(int i){current=found.get(i).getParentFile();fileOptions(found.get(i));}},"",null);
                        back=new Runnable(){public void run(){browse(previous);}};
                        notice("Până la 5.000 intrări / 200 rezultate");
                    }});
                } catch(IOException e){ui(new Runnable(){public void run(){notice("Căutare întreruptă");}});}
            }});
        }};
        back=new Runnable(){public void run(){browse(previous);}};
        footer("Caută","Editor","Șterge");query.requestFocus();
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
        options(file.getName(),new String[]{"Deschide","Editează text UTF-8","Redenumește","Șterge","Copiază","Mută",marked.contains(file)?"Demarchează":"Marchează","Informații","Deschide cu","Partajează","Instalează APK"},new Choice() { public void pick(int i) {
            if(i==8||i==9){try{android.net.Uri uri=LeoFileProvider.uri(FilesActivity.this,file);String mime=getContentResolver().getType(uri);if(i==8)ShareHubActivity.view(FilesActivity.this,uri,mime);else ShareHubActivity.send(FilesActivity.this,uri,mime,file.getName());}catch(Exception e){notice("Fișier inaccesibil");}return;}if(i==10){apk(file);return;}
            if(i==0) open(file); else if(i==1) textFile(file); else if(i==2) nameForm(file);
            else if(i==7)metadata(file);else if(i==4||i==5)prepareTransfer(Arrays.asList(file),i==5);else if(i==6){if(!marked.remove(file)){if(marked.size()>=100){notice("Maximum 100 fișiere în selecție");return;}marked.add(file);}browse(current);}
            else options("Ștergi fișierul?",new String[]{"Păstrează","Șterge"},new Choice() { public void pick(int yes) {
                if(yes==1) { if(!file.delete()) notice("Ștergerea a eșuat; folderele trebuie să fie goale"); browse(current); }
            }});
        }});
    }
    void onListSelection(String title,int index){if(root==listing&&index>=0&&index<entries.size()){focused=index;selected=entries.get(index);}}
    public boolean dispatchKeyEvent(android.view.KeyEvent e){if(root==listing&&e.getKeyCode()==android.view.KeyEvent.KEYCODE_5){android.view.View v=getCurrentFocus();while(v!=null&&!(v instanceof android.widget.ListView)){android.view.ViewParent parent=v.getParent();v=parent instanceof android.view.View?(android.view.View)parent:null;}if(v instanceof android.widget.ListView){int at=((android.widget.ListView)v).getSelectedItemPosition();if(at>=0&&at<entries.size()){focused=at;selected=entries.get(at);}}}if(e.getKeyCode()==android.view.KeyEvent.KEYCODE_5&&root==listing&&selected!=null&&selected.isFile()){if(e.getAction()==android.view.KeyEvent.ACTION_DOWN)onKeyDown(e.getKeyCode(),e);return true;}return super.dispatchKeyEvent(e);}
    public boolean onKeyDown(int key,android.view.KeyEvent e){if(key==android.view.KeyEvent.KEYCODE_5&&root==listing&&selected!=null&&selected.isFile()){if(e.getRepeatCount()==0){if(!marked.remove(selected)){if(marked.size()>=100){notice("Maximum 100 fișiere");return true;}marked.add(selected);}browse(current);}return true;}return super.onKeyDown(key,e);}
    private void metadata(final File f){ArrayList<String> rows=new ArrayList<String>();rows.add(f.isDirectory()?"Folder":"Fișier");if(f.isFile())rows.add("Mărime · "+android.text.format.Formatter.formatFileSize(this,f.length()));rows.add("Liber · "+android.text.format.Formatter.formatFileSize(this,f.getUsableSpace()));rows.add("Total · "+android.text.format.Formatter.formatFileSize(this,f.getTotalSpace()));rows.add("Modificat · "+new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(f.lastModified())));rows.add("Cale · "+f.getAbsolutePath());rows.add("Acces · "+(f.canRead()?"citire ":"")+(f.canWrite()?"scriere":""));if(f.isFile())try{rows.add("Tip · "+getContentResolver().getType(LeoFileProvider.uri(this,f)));}catch(Exception ignored){}rows.add("Selecție · "+marked.size()+" fișiere");list(f.getName(),rows,0,new Choice(){public void pick(int i){}},"",null);back=new Runnable(){public void run(){browse(current);}};}
    private void open(File file) {
        try {
            android.net.Uri uri=LeoFileProvider.uri(this,file);
            Intent view=new Intent(Intent.ACTION_VIEW);
            String extension=file.getName().contains(".")?file.getName().substring(file.getName().lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
            String mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if(extension.equals("apk")){apk(file);return;}
            if(mime!=null&&mime.startsWith("image/")){startActivity(new Intent(this,GalleryActivity.class).setData(uri).putExtra("title",file.getName()));return;}
            if(mime!=null&&mime.startsWith("video/")){startActivity(new Intent(this,VideoActivity.class).setData(uri).putExtra("title",file.getName()));return;}
            if((mime!=null&&mime.startsWith("audio/"))||extension.equals("wma")){startActivity(new Intent(this,MediaActivity.class).setData(uri).putExtra("title",file.getName()));return;}
            if(file.getName().toLowerCase(Locale.ROOT).endsWith(".ics")){startActivity(new Intent(this,CalendarToolsActivity.class).putExtra("file",file.getAbsolutePath()));return;}
            if(file.getName().endsWith(".leo-people.json")){startActivity(new Intent(this,PeopleBackupActivity.class).putExtra("file",file.getAbsolutePath()));return;}
            if(file.getName().endsWith(".leo-backup.json")){startActivity(new Intent(this,BackupActivity.class).putExtra("file",file.getAbsolutePath()));return;}
            view.setDataAndType(uri,mime==null?"application/octet-stream":mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch(Exception failure) { notice("Nicio aplicație disponibilă pentru acest fișier"); }
    }
    private void apk(File f){if(!f.getName().toLowerCase(Locale.ROOT).endsWith(".apk")){notice("Alege un fișier APK");return;}startActivity(new Intent(this,ApkInstallActivity.class).putExtra("file",f.getAbsolutePath()));}
    android.graphics.drawable.Drawable rowIcon(String title,int at){if(!title.equals(artworkTitle)||at<0||at>=entries.size())return null;File f=entries.get(at);String n=f.getName().toLowerCase(Locale.ROOT);if(f.isFile()&&n.matches(".*\\.(png|jpe?g|webp|gif|bmp)$"))try{return artwork.get(f.getPath()+":"+f.lastModified(),LeoFileProvider.uri(this,f),"▧");}catch(IOException ignored){}return new AppBadge(f.isDirectory()?"Fișiere":n.endsWith(".apk")?"Setări":n.matches(".*\\.(mp3|flac|aac|m4a|wma|ogg)$")?"Muzică":"Fișier",dp(32));}
    public void onBackPressed(){if(busy){notice("Transfer în curs; așteaptă verificarea");return;}super.onBackPressed();}
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
