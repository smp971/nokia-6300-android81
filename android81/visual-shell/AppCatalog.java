package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;
import java.util.*;
/** Stable app IDs are independent of user-defined display order. */
final class AppCatalog {
    private static final class Entry {
        final int id; final String label; final Class<?> target;
        final String extra; final Object value;
        Entry(int id, String label, Class<?> target) { this(id,label,target,null,null); }
        Entry(int id, String label, Class<?> target, String extra, Object value) {
            this.id=id; this.label=label; this.target=target; this.extra=extra; this.value=value;
        }
    }
    private static final Entry[] APPS = entries(new Entry[]{
        new Entry(0, "Telefon", PhoneActivity.class),
        new Entry(1, "Contacte", ContactsActivity.class),
        new Entry(2, "Mesaje", MessagesActivity.class),
        new Entry(3, "Ceas", ClockActivity.class),
        new Entry(4, "Calculator", CalculatorActivity.class),
        new Entry(5, "Galerie", GalleryActivity.class),
        new Entry(6, "Muzică", MusicLibraryActivity.class),
        new Entry(7, "Fișiere", FilesActivity.class),
        new Entry(8, "Setări", SettingsActivity.class),
        new Entry(9, "Aplicații Android", AndroidAppsActivity.class),
        new Entry(10, "Notițe", NotesActivity.class),
        new Entry(11, "Calendar", CalendarActivity.class),
        new Entry(12, "Video", MediaActivity.class, "kind", "video"),
        new Entry(13, "Reportofon", RecorderActivity.class),
        new Entry(14, "Convertor", ConverterActivity.class),
        new Entry(15, "Descărcări", DownloadsActivity.class),
        new Entry(16, "Fundaluri", WallpaperActivity.class),
        new Entry(17, "Favorite", ContactsActivity.class, "favorites", true),
        new Entry(18, "Blocare taste", KeypadLockActivity.class),
        new Entry(19, "Browser", BrowserHubActivity.class),
        new Entry(20, "Configurări rapide", QuickSettingsActivity.class),
        new Entry(21, "Configurare", SetupWizardActivity.class),
        new Entry(22, "Email", EmailActivity.class),
        new Entry(23, "Terminal", TerminalActivity.class),
        new Entry(24, "Diagnostic", DiagnosticActivity.class),
        new Entry(25, "Radio FM / Internet", FmActivity.class),
        new Entry(26, "Meteo", WeatherActivity.class),
        new Entry(27, "Backup", BackupActivity.class),
        new Entry(28, "Ajutor", HelpActivity.class),
        new Entry(29, "Colecții vizuale", ThemePacksActivity.class),
        new Entry(30, "Cărți", ReaderActivity.class),
        new Entry(31, "Jocuri", GamesActivity.class),
        new Entry(32, "Conexiuni", ConnectionDashboardActivity.class),
        new Entry(33, "Actualizări", UpdatesActivity.class),
        new Entry(34, "Cameră", CameraActivity.class),
        new Entry(35, "GPS", GpsActivity.class),
        new Entry(36, "Studio", PremiumStudioActivity.class),
        new Entry(37, "Aplicații web", WebAppsActivity.class),
        new Entry(38, "Știri", FeedsActivity.class),
        new Entry(39, "Podcasturi", FeedsActivity.class, "podcast", true),
        new Entry(40, "Radio Internet", StreamRadioActivity.class),
        new Entry(41, "SSH / SFTP", SshActivity.class),
        new Entry(42, "Dicționar", LexiconActivity.class),
        new Entry(43, "Învățare", StudyActivity.class),
        new Entry(44, "Arhive ZIP", ZipActivity.class),
        new Entry(45, "Cheltuieli", ExpensesActivity.class),
        new Entry(46, "Metronom", MetronomeActivity.class),
        new Entry(47, "Coduri QR", QrActivity.class)
    });
    private static Entry[] entries(Entry[] declared) {
        Entry[] result=new Entry[declared.length];
        for(Entry entry:declared) {
            if(entry.id<0 || entry.id>=result.length || result[entry.id]!=null)
                throw new IllegalStateException("Duplicate or invalid app ID");
            result[entry.id]=entry;
        }
        for(Entry entry:result)if(entry==null)throw new IllegalStateException("Missing app ID");
        return result;
    }
    static final String[] NAMES=labels();
    private static String[] labels() {
        String[] result=new String[APPS.length];
        for(Entry entry:APPS)result[entry.id]=L.t(entry.label);
        return result;
    }
    static android.content.Intent intent(Context context,int id) {
        if(id<0 || id>=APPS.length)return null;
        Entry entry=APPS[id];
        android.content.Intent intent=new android.content.Intent(context,entry.target);
        if(entry.value instanceof Boolean)intent.putExtra(entry.extra,(Boolean)entry.value);
        else if(entry.value instanceof String)intent.putExtra(entry.extra,(String)entry.value);
        return intent;
    }
    static boolean launch(Context context,int id) {
        android.content.Intent intent=intent(context,id);
        if(intent==null)return false;
        context.startActivity(intent);
        HomeAccess.record(context,id);
        return true;
    }
    static int[] parse(String value){ArrayList<Integer> ids=new ArrayList<Integer>();for(String part:value.split(","))try{int n=Integer.parseInt(part);if(n>=0&&n<NAMES.length&&!ids.contains(n))ids.add(n);}catch(NumberFormatException ignored){}for(int n=0;n<NAMES.length;n++)if(!ids.contains(n))ids.add(n);int[] result=new int[NAMES.length];for(int n=0;n<result.length;n++)result[n]=ids.get(n);return result;}
    static int[] order(Context c){return parse(c.getSharedPreferences("appearance",0).getString("app-order",""));}
    static final String[] CATEGORIES=L.array(new String[]{"Principale","Comunicare","Media","Unelte","Relaxare","Sistem","Toate"});
    static boolean settingsOnly(int id){return id==36||id==7||id==15||id==16||id==17||id==18||id==20||id==21||id==24||id==27||id==28||id==29||id==32||id==33;}
    static int category(int id){if(id==0||id==1||id==2||id==22||id==38||id==41)return 1;if(id==5||id==6||id==12||id==13||id==25||id==34||id==39||id==40)return 2;if(id==3||id==4||id==10||id==11||id==14||id==19||id==26||id==35||id>=42&&id<=47)return 3;if(id==30||id==31||id==37)return 4;return 5;}
    static boolean primary(int id){return id==0||id==1||id==2||id==3||id==5||id==6||id==8||id==9||id==34;}
    static int[] menuOrder(Context c) {
        boolean simple = ShellMode.simple(c);
        int[] ordered = order(c);
        int[] visible = new int[ordered.length];
        int count = 0;
        for (int id : ordered) {
            if (!settingsOnly(id) && ShellMode.appVisible(id, simple)) visible[count++] = id;
        }
        return Arrays.copyOf(visible, count);
    }
    static void save(Context c,int[] order){StringBuilder out=new StringBuilder();for(int id:order){if(out.length()>0)out.append(',');out.append(id);}c.getSharedPreferences("appearance",0).edit().putString("app-order",out.toString()).apply();}
    static int[] shortcuts(Context c){int[] ids={0,2,6,22};for(int i=0;i<ids.length;i++){int n=c.getSharedPreferences("appearance",0).getInt("shortcut"+i,ids[i]);if(n>=0&&n<NAMES.length)ids[i]=n;}return ids;}
}
