package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.graphics.drawable.Drawable;import android.graphics.drawable.GradientDrawable;import android.graphics.drawable.StateListDrawable;import android.net.TrafficStats;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;
/** Six keypad categories, inspired by the local KaiOS settings reference. */
public final class SettingsActivity extends LeoActivity {
    static final String[] TABS=L.array(new String[]{"Rețele","Aspect","Sunet","Siguranță","Stocare","Telefon"});
    private static final String[][] ROWS=L.array(new String[][]{{"Wi-Fi","Bluetooth","Hotspot Wi-Fi","SIM-uri și rețea mobilă","Transfer USB","Mai multe"},{"Ecran și taste","Personalizare","Tastatură și text"},{"Sunete și tonuri","Volume separate","Profiluri sonore","Notificări","Program sonor"},{"Blocarea tastelor","Permisiuni și aplicații","Localizare","Accesibilitate Leo"},{"Spațiu telefon / card SD","Fișiere Leo","Descărcări Leo","Backup Leo"},{"Baterie","Dată și oră","Despre telefon","Ajutor Leo","Actualizări Leo","Instrumente avansate"}});
    private static final String[] EVERYDAY=L.array(new String[]{"Wi-Fi","SIM-uri și rețea mobilă","Sunete și tonuri","Ecran și taste","Personalizare","Toate setările"});
    private int[] selections=new int[TABS.length];
    private int category;
    private boolean categories,allSettings;
    protected void onCreate(Bundle state){
        super.onCreate(state);
        int savedCategory=state==null?getSharedPreferences("settings-ui",0).getInt("category",0):state.getInt("category",0);
        category=Math.max(0,Math.min(TABS.length-1,savedCategory));
        if(state==null)return;
        allSettings=state.getBoolean("all_settings");
        int[] saved=state.getIntArray("selections");
        if(saved!=null&&saved.length==TABS.length)selections=saved;
    }
    protected void onResume(){super.onResume();show();}
    protected void onPause(){getSharedPreferences("settings-ui",0).edit().putInt("category",category).apply();super.onPause();}
    protected void onSaveInstanceState(Bundle b){b.putInt("category",category);b.putIntArray("selections",selections);b.putBoolean("all_settings",allSettings);super.onSaveInstanceState(b);}
    private void show(){if(ShellMode.simple(this)&&!allSettings){simple();return;}showFull();}
    private void simple(){
        categories=false;
        list(L.t("Setări uzuale"),Arrays.asList(EVERYDAY),0,new Choice(){
            public void pick(int at){
                if(at==0)startActivity(new Intent(SettingsActivity.this,ConnectivityActivity.class).putExtra("kind","wifi"));
                else if(at==5){allSettings=true;showFull();}
                else start(new Class<?>[]{MobileSettingsActivity.class,SoundSettingsActivity.class,DisplaySettingsActivity.class,PersonalizationActivity.class}[at-1]);
            }
        },L.t("Mod interfață"),new Runnable(){
            public void run(){ShellMode.choose(SettingsActivity.this,new Runnable(){public void run(){show();}});}
        });
    }
    private String categoryListKey(int value){return L.t("Setări Leo")+" · "+(value+1)+" / "+TABS.length;}
    private void showFull(){
        categories=true;
        final int current=category;
        list(categoryListKey(current),Arrays.asList(ROWS[current]),selections[current],new Choice(){
            public void pick(int row){selections[current]=row;open(current,row);}
        },L.t("Categorii"),new Runnable(){public void run(){chooseCategory();}});
        LinearLayout tabs=KeypadTabs.create(this,new String[]{
            "‹ "+TABS[KeypadTabs.next(current,-1,TABS.length)],
            TABS[current],
            TABS[KeypadTabs.next(current,1,TABS.length)]+" ›"
        },1,new Choice(){public void pick(int offset){switchCategory(current+offset-1);}},
            new Runnable(){public void run(){KeypadTabs.focusList(SettingsActivity.this);}});
        tabs.setPadding(dp(6),0,dp(6),0);
        for(int index=0;index<tabs.getChildCount();index++){
            TextView tab=(TextView)tabs.getChildAt(index);
            tab.setTextSize((index==1?12:10)*Appearance.textScale(this));
            tab.getLayoutParams().height=dp(23);
            if(index==1){
                tab.setTypeface(UiChrome.TITLE);
                tab.getLayoutParams().width=0;
                ((LinearLayout.LayoutParams)tab.getLayoutParams()).weight=1.2f;
            }
        }
        root.addView(tabs,1);
        if(ShellMode.simple(this)&&allSettings)back=new Runnable(){public void run(){allSettings=false;show();}};
    }
    private void chooseCategory(){
        new KeypadDialog(this,L.t("Categorii"),TABS,new KeypadDialog.Action(){
            public void select(int index){switchCategory(index);}
        },true,palette).initialSelection(category).show();
    }
    private void switchCategory(int next){category=KeypadTabs.next(next,0,TABS.length);show();}
    void onListSelection(String title,int index){if(categories&&title.equals(categoryListKey(category)))selections[category]=index;}
    @Override void styleList(TextView heading,ListView list){
        if(categories)heading.setText(L.t("Setări Leo"));
        list.setPadding(dp(6),dp(4),dp(6),dp(3));
        list.setDividerHeight(dp(2));
    }
    @Override void styleListRow(TextView row){
        row.setTextSize(14*Appearance.textScale(this));
        row.setTypeface(UiChrome.TITLE);
        row.setMinHeight(dp(32));
        row.setCompoundDrawablePadding(dp(8));
        int radius=dp(7);
        GradientDrawable selected=Appearance.surface(palette.selection,radius);
        selected.setStroke(dp(1),palette.accent);
        StateListDrawable surfaces=new StateListDrawable();
        surfaces.addState(new int[]{android.R.attr.state_selected},selected);
        surfaces.addState(new int[]{},Appearance.surface(palette.card,radius));
        row.setBackground(surfaces);
        row.setPadding(dp(8),dp(3),dp(8),dp(3));
    }
    @Override Drawable rowIcon(String title,int index){
        int size=dp(21);
        if(!title.equals(categories?categoryListKey(category):L.t("Setări uzuale")))return null;
        if((categories&&category==0&&index<3)||(!categories&&index==0))return new QuickIcon(index,size);
        if((categories&&category==0&&index==3)||(!categories&&index==1))return new SimIcon(0,size,palette.accent);
        return new AppBadge((categories?ROWS[category]:EVERYDAY)[index],size);
    }
    boolean stableRowIcons(){return true;}
    private void start(Class<?> type){startActivity(new Intent(this,type));}
    private void advancedPhone(){options(L.t("Instrumente avansate"),new String[]{L.t("Diagnostic Leo"),L.t("Android din taste"),L.t("Configurare inițială"),"Leo Studio",L.t("Pregătit de utilizare")},new Choice(){public void pick(int at){start(new Class<?>[]{DiagnosticActivity.class,AndroidControlsActivity.class,SetupWizardActivity.class,PremiumStudioActivity.class,DailyReadinessActivity.class}[at]);}});}
    private void open(int group,int row){if(group==0&&row==5){options(L.t("Rețele"),new String[]{L.t("Trafic de date"),L.t("Panou conexiuni Leo")},new Choice(){public void pick(int at){if(at==0)traffic();else start(ConnectionDashboardActivity.class);}});return;}if(group==5){if(row==5){advancedPhone();return;}row=new int[]{0,1,3,6,7}[row];}if(group==0){if(row==6){start(ConnectionDashboardActivity.class);return;}if(row<3)startActivity(new Intent(this,ConnectivityActivity.class).putExtra("kind",new String[]{"wifi","bluetooth","hotspot"}[row]));else if(row==3)start(MobileSettingsActivity.class);else if(row==4)start(UsbTransferActivity.class);else traffic();}else if(group==1){start(new Class<?>[]{DisplaySettingsActivity.class,PersonalizationActivity.class,InputSettingsActivity.class}[row]);}else if(group==2){if(row==4)start(SoundScheduleActivity.class);else if(row==0)start(SoundSettingsActivity.class);else if(row==1)startActivity(new Intent(this,QuickSettingsActivity.class).putExtra("volumes",true));else if(row==2)SoundProfiles.select(this,new Runnable(){public void run(){show();}});else start(NotificationSettingsActivity.class);}else if(group==3){start(new Class<?>[]{KeypadLockSettingsActivity.class,AppControlActivity.class,PrivacySettingsActivity.class,AccessibilitySettingsActivity.class}[row]);}else if(group==4){if(row==2){startActivity(new Intent(this,DownloadsActivity.class).putExtra("center",true));return;}start(new Class<?>[]{StorageSettingsActivity.class,FilesActivity.class,DownloadsActivity.class,BackupActivity.class}[row]);}else if(row==0)start(BatterySettingsActivity.class);else if(row==1)start(DateTimeSettingsActivity.class);else if(row==2)start(DiagnosticActivity.class);else if(row==3)page(L.t("Despre Leo"),Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT+L.t("\nShell nativ pentru taste\n")+Build.DISPLAY);else if(row==4)start(AndroidControlsActivity.class);else if(row==9)start(DailyReadinessActivity.class);else if(row==8)start(PremiumStudioActivity.class);else if(row==7)start(UpdatesActivity.class);else if(row==6)start(HelpActivity.class);else start(SetupWizardActivity.class);}
    private String bytes(long value){return value<0?L.t("Neraportat"):android.text.format.Formatter.formatFileSize(this,value);}
    private void traffic(){page(L.t("Trafic de date"),L.t("De la pornire / contoarele interfețelor\n\nMobil primit · ")+bytes(TrafficStats.getMobileRxBytes())+L.t("\nMobil trimis · ")+bytes(TrafficStats.getMobileTxBytes())+L.t("\nTotal primit · ")+bytes(TrafficStats.getTotalRxBytes())+L.t("\nTotal trimis · ")+bytes(TrafficStats.getTotalTxBytes())+L.t("\n\nNu este contorul facturii operatorului și nu reprezintă un ciclu lunar."));}
    private void page(String title,String value){categories=false;begin(title);ScrollView scroll=new ScrollView(this);scroll.addView(text(value,17));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.setFocusable(true);scroll.requestFocus();back=new Runnable(){public void run(){show();}};footer("",L.t("Derulează"),L.t("Înapoi"));}
    public boolean dispatchKeyEvent(KeyEvent event){if(categories&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)){if(event.getAction()==0&&event.getRepeatCount()==0)switchCategory(category+(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));return true;}return super.dispatchKeyEvent(event);}
}
