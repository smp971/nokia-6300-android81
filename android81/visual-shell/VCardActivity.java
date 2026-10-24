package ro.leo.visual;
import ro.leo.i18n.L;

import android.content.*;
import android.os.*;
import android.provider.ContactsContract;
import com.android.vcard.*;
import com.android.vcard.exception.VCardVersionException;
import java.io.*;
import java.util.*;

/** vCard 2.1/3.0/4.0 via AOSP's parser; parse and preview before a single atomic import. */
public final class VCardActivity extends LeoActivity {
    private File boundary;
    protected void onCreate(Bundle b){super.onCreate(b);home();}
    private void home(){final long contact=getIntent().getLongExtra("contact",-1);list(L.t("Contacte VCF"),Arrays.asList(L.t("Importă fișier .vcf"),L.t("Exportă favoritele"),L.t("Exportă toate contactele"),contact>=0?L.t("Exportă contactul ales"):L.t("Ajutor")),0,new Choice(){public void pick(final int i){if(i==0)roots();else if(i==3&&contact<0)notice(L.t("Importul adaugă copii. Exporturile rămân locale în Documente Leo."));else options(L.t("Export local VCF"),new String[]{L.t("Anulează"),L.t("Exportă")},new Choice(){public void pick(int yes){if(yes==1)export(i==1?ContactsContract.Contacts.STARRED+"=1":i==3?ContactsContract.Contacts._ID+"=?":null,i==3?new String[]{""+contact}:null);}});}},"",null);}
    private void roots(){final LinkedHashMap<String,File> roots=LeoFileProvider.roots(this);final ArrayList<File> dirs=new ArrayList<File>(roots.values());list(L.t("Alege stocarea"),Arrays.asList(L.t("Telefon"),L.t("Documente Leo"), L.t("Card SD")).subList(0,Math.min(3,dirs.size())),0,new Choice(){public void pick(int i){boundary=dirs.get(i);browse(boundary);}},"",null);back=new Runnable(){public void run(){home();}};}
    private void browse(final File dir){
        try{if(!LeoFileProvider.inside(boundary,dir))throw new IOException();File[] contents=dir.listFiles();if(contents==null){notice(L.t("Folder inaccesibil"));return;}Arrays.sort(contents,new Comparator<File>(){public int compare(File a,File b){if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());}});
            final ArrayList<File> files=new ArrayList<File>();ArrayList<String> labels=new ArrayList<String>();for(File f:contents)if(files.size()<300&&LeoFileProvider.inside(boundary,f)&&(f.isDirectory()||f.getName().toLowerCase(Locale.ROOT).endsWith(".vcf"))){files.add(f);labels.add((f.isDirectory()?"▸ ":"")+f.getName());}
            list(dir.equals(boundary)?L.t("Fișiere VCF"):dir.getName(),labels,0,new Choice(){public void pick(int i){File f=files.get(i);if(f.isDirectory())browse(f);else preview(f);}},"",null);back=new Runnable(){public void run(){if(dir.equals(boundary))roots();else browse(dir.getParentFile());}};
        }catch(IOException e){notice(L.t("Fișier în afara stocării permise"));}
    }
    static ArrayList<VCardEntry> parse(byte[] data)throws Exception{
        Exception last=null;
        for(int version=0;version<3;version++){
            final ArrayList<VCardEntry> found=new ArrayList<VCardEntry>();VCardEntryConstructor builder=new VCardEntryConstructor(new int[]{VCardConfig.VCARD_TYPE_V21_GENERIC,VCardConfig.VCARD_TYPE_V30_GENERIC,VCardConfig.VCARD_TYPE_V40_GENERIC}[version]);
            builder.addEntryHandler(new VCardEntryHandler(){public void onStart(){}public void onEnd(){}public void onEntryCreated(VCardEntry e){if(found.size()>=500)throw new IllegalArgumentException(L.t("Peste 500 de contacte; împarte fișierul"));found.add(e);}});
            VCardParser parser=version==0?new VCardParser_V21():version==1?new VCardParser_V30():new VCardParser_V40();parser.addInterpreter(builder);
            try{parser.parse(new ByteArrayInputStream(data));return found;}catch(VCardVersionException e){last=e;}
        }throw last;
    }
    private void preview(final File file){worker.execute(new Runnable(){public void run(){try{
        if(!LeoFileProvider.inside(boundary,file)||!file.isFile()||file.length()>1048576)throw new IOException(L.t("Fișier invalid sau peste 1 MiB"));ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>1048576)throw new IOException(L.t("Fișier prea mare"));bytes.write(buf,0,n);}}
        final ArrayList<VCardEntry> entries=parse(bytes.toByteArray());if(entries.isEmpty())throw new IOException(L.t("Niciun contact în fișier"));
        ui(new Runnable(){public void run(){previewEntries(entries);}});
        }catch(final Exception e){ui(new Runnable(){public void run(){notice(L.t("Import oprit: ")+e.getMessage());}});}}});}
    private void previewEntries(final ArrayList<VCardEntry> entries){ArrayList<String> labels=new ArrayList<String>();for(VCardEntry e:entries)labels.add(e.getDisplayName()+"\n"+(e.getPhoneList()==null?0:e.getPhoneList().size())+L.t(" numere"));list(L.t("Previzualizare VCF"),labels,0,new Choice(){public void pick(int i){notice(L.t("Importul adaugă copii; agenda existentă este păstrată"));}},L.t("Importă"),new Runnable(){public void run(){options(entries.size()+L.t(" contacte · se adaugă copii"),new String[]{L.t("Anulează"),L.t("Importă")},new Choice(){public void pick(int i){if(i==1)importEntries(entries);}});}});back=new Runnable(){public void run(){home();}};}
    private void importEntries(final ArrayList<VCardEntry> entries){worker.execute(new Runnable(){public void run(){try{
        ArrayList<ContentProviderOperation> ops=new ArrayList<ContentProviderOperation>();for(VCardEntry e:entries)e.constructInsertOperations(getContentResolver(),ops);
        Parcel p=Parcel.obtain();int size;try{p.writeTypedList(ops);size=p.dataSize();}finally{p.recycle();}if(size>384*1024)throw new IOException(L.t("Import prea mare; împarte fișierul VCF"));
        getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops);ui(new Runnable(){public void run(){notice(entries.size()+L.t(" contacte importate"));home();}});
        }catch(final Exception e){ui(new Runnable(){public void run(){notice(L.t("Import nesalvat: ")+e.getMessage());}});}}});}
    private void export(final String selection,final String[] args){worker.execute(new Runnable(){public void run(){File temporary=null;VCardComposer composer=new VCardComposer(VCardActivity.this,VCardConfig.VCARD_TYPE_V30_GENERIC,"UTF-8");try{
        File folder=LeoFileProvider.roots(VCardActivity.this).get("leo");final File destination=new File(folder,"contacte-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+".vcf");temporary=new File(destination.getPath()+".tmp");
        if(!composer.init(selection,args))throw new IOException(L.t("Agenda este goală sau inaccesibilă"));int count=0,total=0;try(OutputStream out=new FileOutputStream(temporary)){while(!composer.isAfterLast()){if(Thread.currentThread().isInterrupted())throw new IOException(L.t("Anulat"));byte[] b=composer.createOneEntry().getBytes("UTF-8");total+=b.length;if(++count>5000||total>8*1024*1024)throw new IOException(L.t("Export peste limita de 5000 contacte / 8 MiB"));out.write(b);}}
        if(!temporary.renameTo(destination))throw new IOException(L.t("Fișierul nu poate fi finalizat"));ui(new Runnable(){public void run(){begin(L.t("Export salvat"));root.addView(text(L.t("Documente Leo\n")+destination.getName()+L.t("\n\nFișierul conține datele contactelor. Îl poți copia din Fișiere."),18),new android.widget.LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){home();}};footer("","",L.t("Înapoi"));}});
        }catch(final Exception e){if(temporary!=null)temporary.delete();ui(new Runnable(){public void run(){notice(L.t("Export eșuat: ")+e.getMessage());}});}finally{composer.terminate();}}});}
}
