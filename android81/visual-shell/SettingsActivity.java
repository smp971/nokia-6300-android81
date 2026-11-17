package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.TrafficStats;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

import static ro.leo.visual.SettingsIcon.Glyph.*;
/** Six keypad categories with Gaia-inspired lists and connection summaries. */
public final class SettingsActivity extends LeoActivity {
    static final String[] TABS=L.array(new String[]{"Rețele","Aspect","Sunet","Siguranță","Stocare","Telefon"});
    private static final String[][] ROWS=L.array(new String[][]{{"Wi-Fi","Bluetooth","Hotspot Wi-Fi","SIM-uri și rețea mobilă","Transfer USB","Mai multe"},{"Ecran și taste","Personalizare","Tastatură și text","Teme"},{"Sunete și tonuri","Volume separate","Profiluri sonore","Notificări","Program sonor"},{"Blocarea tastelor","Permisiuni și aplicații","Localizare","Accesibilitate"},{"Spațiu telefon / card SD","Fișiere","Descărcări","Backup"},{"Baterie","Dată și oră","Despre telefon","Ajutor","Actualizări","Instrumente avansate"}});
    private static final String[] EVERYDAY=L.array(new String[]{"Wi-Fi","SIM-uri și rețea mobilă","Sunete și tonuri","Ecran și taste","Personalizare","Toate setările"});
    private static final SettingsIcon.Glyph[][] ROW_ICONS = {
        {WIFI, BLUETOOTH, TETHERING, SIM, USB, MENU},
        {BRIGHTNESS, SETTINGS, KEYBOARD, THEMES},
        {SOUND, HEADPHONES, MUTE, NOTIFICATIONS, TIME},
        {LOCK, ADDONS, LOCATION, ACCESSIBILITY},
        {STORAGE, FILES, DOWNLOAD, SYNC},
        {BATTERY, TIME, INFO, HELP, RELOAD, DEVELOPER}
    };
    private static final SettingsIcon.Glyph[] EVERYDAY_ICONS = {WIFI, SIM, SOUND, BRIGHTNESS, THEMES, SETTINGS};
    private int[] selections=new int[TABS.length];
    private int category,simpleSelection;
    private boolean categories,allSettings;
    private SettingsNetworkState network;
    private String activeSettingsList;
    protected void onCreate(Bundle state){
        super.onCreate(state);
        int savedCategory=state==null?getSharedPreferences("settings-ui",0).getInt("category",0):state.getInt("category",0);
        category=Math.max(0,Math.min(TABS.length-1,savedCategory));
        network=new SettingsNetworkState(this,new Runnable(){public void run(){refreshNetworkRows();}});
        if(state==null)return;
        allSettings=state.getBoolean("all_settings");
        simpleSelection=state.getInt("simple_selection");
        int[] saved=state.getIntArray("selections");
        if(saved!=null&&saved.length==TABS.length)selections=saved;
    }
    protected void onResume(){super.onResume();network.start();show();}
    protected void onPause(){network.stop();getSharedPreferences("settings-ui",0).edit().putInt("category",category).apply();super.onPause();}
    protected void onSaveInstanceState(Bundle b){b.putInt("category",category);b.putIntArray("selections",selections);b.putBoolean("all_settings",allSettings);b.putInt("simple_selection",simpleSelection);super.onSaveInstanceState(b);}
    private void show(){if(ShellMode.simple(this)&&!allSettings){simple();return;}showFull();}
    private void simple(){
        categories=false;
        activeSettingsList=L.t("Setări uzuale");
        list(activeSettingsList,networkRows(true),simpleSelection,new Choice(){
            public void pick(int at){
                if(at==0)startActivity(new Intent(SettingsActivity.this,ConnectivityActivity.class).putExtra("kind","wifi"));
                else if(at==5){allSettings=true;showFull();}
                else start(new Class<?>[]{MobileSettingsActivity.class,SoundSettingsActivity.class,DisplaySettingsActivity.class,PersonalizationActivity.class}[at-1]);
            }
        },L.t("Opțiuni"),new Runnable(){public void run(){simpleOptions();}});
    }
    private void simpleOptions(){
        options(L.t("Setări uzuale"),new String[]{L.t("Caută în setări"),L.t("Mod interfață")},new Choice(){
            public void pick(int index){
                if(index==0)searchSettings();
                else ShellMode.choose(SettingsActivity.this,new Runnable(){public void run(){show();}});
            }
        });
    }
    private String categoryListKey(int value){return L.t("Setări")+" · "+(value+1)+" / "+TABS.length;}
    private void showFull(){
        categories=true;
        final int current=category;
        activeSettingsList=categoryListKey(current);
        list(activeSettingsList,current==0?networkRows(false):Arrays.asList(ROWS[current]),selections[current],new Choice(){
            public void pick(int row){selections[current]=row;open(current,row);}
        },L.t("Categorii"),new Runnable(){public void run(){chooseCategory();}});
        LinearLayout tabs=KeypadTabs.create(this,new String[]{
            "‹ "+TABS[KeypadTabs.next(current,-1,TABS.length)],
            TABS[current],
            TABS[KeypadTabs.next(current,1,TABS.length)]+" ›"
        },1,new Choice(){public void pick(int offset){switchCategory(current+offset-1);}},
            new Runnable(){public void run(){KeypadTabs.focusList(SettingsActivity.this);}});
        tabs.setPadding(dp(6), 0, dp(6), 0);
        tabs.setBackgroundColor(palette.bar);
        int divider = Appearance.mix(palette.background, Color.WHITE, .16f);
        float textScale = Appearance.textScale(this);
        for(int index=0;index<tabs.getChildCount();index++){
            TextView tab=(TextView)tabs.getChildAt(index);
            boolean active = index == 1;
            int preferredSize = Math.round((active ? 19 : 15) * textScale);
            tab.setHorizontallyScrolling(false);
            tab.setAutoSizeTextTypeUniformWithConfiguration(
                active ? 15 : 12, preferredSize, 1, TypedValue.COMPLEX_UNIT_SP
            );
            tab.getLayoutParams().height = dp(40);
            tab.setPadding(dp(2), 0, dp(2), dp(3));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(palette.selection));
            LayerDrawable labelSurface = new LayerDrawable(new Drawable[]{
                new ColorDrawable(palette.bar), new ColorDrawable(active ? palette.accent : divider)
            });
            labelSurface.setLayerHeight(1, dp(active ? 3 : 1));
            labelSurface.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
            states.addState(new int[0], labelSurface);
            tab.setBackground(states);
            tab.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[0]},
                new int[]{palette.ink, active ? Color.WHITE : Appearance.mix(palette.bar, Color.WHITE, .76f)}
            ));
            if (active) {
                tab.setTypeface(UiChrome.TITLE);
                ((LinearLayout.LayoutParams) tab.getLayoutParams()).weight = 1.18f;
            }
        }
        root.addView(tabs,1);
        if(ShellMode.simple(this)&&allSettings)back=new Runnable(){public void run(){allSettings=false;show();}};
    }
    private void chooseCategory(){
        String[] choices=Arrays.copyOf(TABS,TABS.length+1);
        choices[TABS.length]=L.t("Caută în setări");
        new KeypadDialog(this,L.t("Categorii"),choices,new KeypadDialog.Action(){
            public void select(int index){if(index==TABS.length)searchSettings();else switchCategory(index);}
        },true,palette).initialSelection(category).show();
    }
    private void searchSettings(){
        startActivity(new Intent(this,GlobalSearchActivity.class).putExtra(GlobalSearchActivity.SETTINGS_FIRST,true));
    }
    private List<String> networkRows(boolean everyday){
        String[] labels=(everyday?EVERYDAY:ROWS[0]).clone();
        labels[0] += "\n" + network.wifi();
        if(!everyday)labels[1] += "\n" + network.bluetooth();
        labels[everyday?1:3] += "\n" + network.dataSim();
        return Arrays.asList(labels);
    }
    private void refreshNetworkRows(){
        if(activeSettingsList!=null&&(!categories||category==0))replaceRows(activeSettingsList,networkRows(!categories));
    }
    private void switchCategory(int next){category=KeypadTabs.next(next,0,TABS.length);show();}
    @Override void onListSelection(String title,int index){
        if(!title.equals(activeSettingsList))return;
        if(categories)selections[category]=index;
        else simpleSelection=index;
    }
    @Override String rowMemoryKey(String title,int index,String label){
        if(title.equals(categoryListKey(0)))return ROWS[0][index];
        if(title.equals(L.t("Setări uzuale")))return EVERYDAY[index];
        return super.rowMemoryKey(title,index,label);
    }
    @Override void styleList(TextView heading, ListView list) {
        if (categories) heading.setText(L.t("Setări"));
        heading.setBackgroundColor(palette.bar);
        heading.setTextSize(22 * Appearance.textScale(this));
        list.setPadding(dp(8), 0, dp(8), 0);
        list.setDivider(new ColorDrawable(Appearance.mix(palette.background, Color.WHITE, .16f)));
        list.setDividerHeight(dp(1));
    }

    @Override void styleListRow(TextView row) {
        row.setTextSize(19 * Appearance.textScale(this));
        row.setMinHeight(dp(44));
        row.setMaxLines(3);
        row.setCompoundDrawablePadding(dp(12));
        LayerDrawable selected = new LayerDrawable(new Drawable[]{
            new ColorDrawable(palette.selection), new ColorDrawable(palette.accent)
        });
        selected.setLayerWidth(1, dp(3));
        selected.setLayerGravity(1, Gravity.LEFT | Gravity.FILL_VERTICAL);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_selected}, selected);
        background.addState(new int[0], new ColorDrawable(palette.background));
        row.setBackground(background);
        row.setPadding(dp(12), dp(3), dp(9), dp(3));
    }

    @Override Drawable rowIcon(String title, int index) {
        if (!title.equals(activeSettingsList)) return null;
        SettingsIcon.Glyph glyph = (categories ? ROW_ICONS[category] : EVERYDAY_ICONS)[index];
        ColorStateList colors = new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_selected}, new int[0]},
            new int[]{palette.ink, palette.accent}
        );
        return new SettingsIcon(this, glyph, dp(24), colors);
    }
    boolean stableRowIcons(){return true;}
    private void start(Class<?> type){startActivity(new Intent(this,type));}
    private void advancedPhone(){options(L.t("Instrumente avansate"),new String[]{L.t("Diagnostic"),L.t("Android din taste"),L.t("Configurare inițială"),"Studio",L.t("Pregătit de utilizare")},new Choice(){public void pick(int at){start(new Class<?>[]{DiagnosticActivity.class,AndroidControlsActivity.class,SetupWizardActivity.class,PremiumStudioActivity.class,DailyReadinessActivity.class}[at]);}});}
    private void open(int group,int row){if(group==0&&row==5){options(L.t("Rețele"),new String[]{L.t("Trafic de date"),L.t("Panou conexiuni")},new Choice(){public void pick(int at){if(at==0)traffic();else start(ConnectionDashboardActivity.class);}});return;}if(group==5){if(row==5){advancedPhone();return;}row=new int[]{0,1,3,6,7}[row];}if(group==0){if(row==6){start(ConnectionDashboardActivity.class);return;}if(row<3)startActivity(new Intent(this,ConnectivityActivity.class).putExtra("kind",new String[]{"wifi","bluetooth","hotspot"}[row]));else if(row==3)start(MobileSettingsActivity.class);else if(row==4)start(UsbTransferActivity.class);else traffic();}else if(group==1){start(new Class<?>[]{DisplaySettingsActivity.class,PersonalizationActivity.class,InputSettingsActivity.class,ThemePacksActivity.class}[row]);}else if(group==2){if(row==4)start(SoundScheduleActivity.class);else if(row==0)start(SoundSettingsActivity.class);else if(row==1)startActivity(new Intent(this,QuickSettingsActivity.class).putExtra("volumes",true));else if(row==2)SoundProfiles.select(this,new Runnable(){public void run(){show();}});else start(NotificationSettingsActivity.class);}else if(group==3){start(new Class<?>[]{KeypadLockSettingsActivity.class,AppControlActivity.class,PrivacySettingsActivity.class,AccessibilitySettingsActivity.class}[row]);}else if(group==4){if(row==2){startActivity(new Intent(this,DownloadsActivity.class).putExtra("center",true));return;}start(new Class<?>[]{StorageSettingsActivity.class,FilesActivity.class,DownloadsActivity.class,BackupActivity.class}[row]);}else if(row==0)start(BatterySettingsActivity.class);else if(row==1)start(DateTimeSettingsActivity.class);else if(row==2)start(DiagnosticActivity.class);else if(row==3)aboutShell();else if(row==4)start(AndroidControlsActivity.class);else if(row==9)start(DailyReadinessActivity.class);else if(row==8)start(PremiumStudioActivity.class);else if(row==7)start(UpdatesActivity.class);else if(row==6)start(HelpActivity.class);else start(SetupWizardActivity.class);}
    private String bytes(long value){return value<0?L.t("Neraportat"):android.text.format.Formatter.formatFileSize(this,value);}
    private void traffic(){page(L.t("Trafic de date"),L.t("De la pornire / contoarele interfețelor\n\nMobil primit · ")+bytes(TrafficStats.getMobileRxBytes())+L.t("\nMobil trimis · ")+bytes(TrafficStats.getMobileTxBytes())+L.t("\nTotal primit · ")+bytes(TrafficStats.getTotalRxBytes())+L.t("\nTotal trimis · ")+bytes(TrafficStats.getTotalTxBytes())+L.t("\n\nNu este contorul facturii operatorului și nu reprezintă un ciclu lunar."));}
    private void aboutShell() {
        String name = getApplicationInfo().loadLabel(getPackageManager()).toString();
        page(L.t("Despre") + " " + name, Build.MODEL + "\nAndroid " + Build.VERSION.RELEASE
            + " · API " + Build.VERSION.SDK_INT + L.t("\nShell nativ pentru taste\n") + Build.DISPLAY);
    }

    private void page(String title,String value){categories=false;activeSettingsList=null;begin(title);ScrollView scroll=new ScrollView(this);scroll.addView(text(value,17));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.setFocusable(true);scroll.requestFocus();back=new Runnable(){public void run(){show();}};footer("",L.t("Derulează"),L.t("Înapoi"));}
    public boolean dispatchKeyEvent(KeyEvent event){if(categories&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)){if(event.getAction()==0&&event.getRepeatCount()==0)switchCategory(category+(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));return true;}return super.dispatchKeyEvent(event);}
}
