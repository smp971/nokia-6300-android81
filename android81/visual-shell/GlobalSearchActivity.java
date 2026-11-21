package ro.leo.visual;
import ro.leo.i18n.L;
import android.app.*;import android.content.*;import android.database.Cursor;import android.os.*;import android.provider.*;import android.text.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;

/** On-demand, bounded local search. No persistent index or background service. */
public final class GlobalSearchActivity extends LeoActivity {
 static final class Hit {final String label;final Intent intent;Hit(String s,Intent i){label=s;intent=i;}}
 static final String[] SCOPES={"Toate","Aplicații","Contacte","Setări","Muzică","Altele"};
 static final String SETTINGS_FIRST="settings_first";
 private static final int SETTINGS_SCOPE=3;
 private static final SettingPage[] SETTING_PAGES={
  new SettingPage("Wi-Fi",ConnectivityActivity.class,"wifi|wireless|retea wireless","kind","wifi"),
  new SettingPage("Bluetooth",ConnectivityActivity.class,"bt|casti|headset","kind","bluetooth"),
  new SettingPage("Hotspot Wi-Fi",ConnectivityActivity.class,"hotspot|tethering|partajare internet","kind","hotspot"),
  new SettingPage("SIM-uri și rețea mobilă",MobileSettingsActivity.class,"sim|date mobile|mobile data|apn|4g|lte"),
  new SettingPage("Transfer USB",UsbTransferActivity.class,"usb|mtp|transfer fisiere"),
  new SettingPage("Panou conexiuni",ConnectionDashboardActivity.class,"conexiuni|connections|trafic date|data usage"),
  new SettingPage("Ecran și taste",DisplaySettingsActivity.class,"ecran|display|luminozitate|brightness|timeout"),
  new SettingPage("Personalizare",PersonalizationActivity.class,"home|scurtaturi|shortcuts|personalization"),
  new SettingPage("Tastatură și text",InputSettingsActivity.class,"tastatura|keyboard|t9|dictionar|dictionary"),
  new SettingPage("Fundaluri și teme",WallpaperActivity.class,"fundal|wallpaper|tema|theme"),
  new SettingPage("Sunete și tonuri",SoundSettingsActivity.class,"sunet|sound|sonerie|ringtone|vibratie|vibration"),
  new SettingPage("Volume separate",QuickSettingsActivity.class,"volum|volume|media volume","volumes",true),
  new SettingPage("Program sonor",SoundScheduleActivity.class,"program sunet|silent|sound schedule"),
  new SettingPage("Notificări",NotificationSettingsActivity.class,"notificari|notifications|alerte"),
  new SettingPage("Blocarea tastelor",KeypadLockSettingsActivity.class,"blocare|keypad lock|lock screen"),
  new SettingPage("Permisiuni și aplicații",AppControlActivity.class,"permisiuni|permissions|app control"),
  new SettingPage("Localizare",PrivacySettingsActivity.class,"gps|locatie|location|privacy"),
  new SettingPage("Accesibilitate",AccessibilitySettingsActivity.class,"accesibilitate|accessibility|contrast|text mare"),
  new SettingPage("Spațiu telefon / card SD",StorageSettingsActivity.class,"stocare|storage|memorie|sd"),
  new SettingPage("Backup",BackupActivity.class,"backup|copie|restaurare|restore"),
  new SettingPage("Baterie",BatterySettingsActivity.class,"battery|acumulator|autonomie"),
  new SettingPage("Dată și oră",DateTimeSettingsActivity.class,"data|ora|date|time|fus orar|time zone"),
  new SettingPage("Limba interfeței",LanguageSettingsActivity.class,"limba|language|romana|english"),
  new SettingPage("Actualizări",UpdatesActivity.class,"actualizare|update|versiune"),
  new SettingPage("Configurare inițială",SetupWizardActivity.class,"setup|wizard|configurare"),
  new SettingPage("Setări",SettingsActivity.class,"setari|settings"),
 };
 private int scope;private boolean numericEntry;
 private int settingsResult=-1;
 private TextView summary;private EditText query;private ListView results;private final Handler handler=new Handler();private volatile int generation;
 private final Runnable search=new Runnable(){public void run(){find();}};
 static boolean dialable(String value){return value!=null&&value.matches("[+0-9*#][0-9*#]{0,31}");}
 static boolean matches(String value,String query){return (query.matches("[0-9]+")&&ContactKeys.matches(value==null?"":value,"",query))||fold(value).contains(fold(query));}
 private static String fold(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
 private static final class SettingPage {
  final String label;
  final Class<?> activity;
  final String[] aliases;
  final String extra;
  final Object value;
  SettingPage(String label,Class<?> activity,String aliases){this(label,activity,aliases,null,null);}
  SettingPage(String label,Class<?> activity,String aliases,String extra,Object value){
   this.label=label;this.activity=activity;this.aliases=aliases.split("\\|");this.extra=extra;this.value=value;
  }
  int rank(String term,boolean numeric){
   int best=Math.min(rank(label,term,numeric),rank(L.t(label),term,numeric));
   for(String alias:aliases)best=Math.min(best,rank(alias,term,numeric));
   return best;
  }
  private static int rank(String label,String term,boolean numeric){
   String key=numeric?ContactKeys.t9(label):fold(label);
   return key.equals(term)?0:key.startsWith(term)?1:key.contains(term)?2:3;
  }
  Hit hit(Context context){
   Intent intent=new Intent(context,activity);
   if(value instanceof String)intent.putExtra(extra,(String)value);
   else if(value instanceof Boolean)intent.putExtra(extra,(boolean)(Boolean)value);
   return new Hit(L.t("Setare · ")+L.t(label),intent);
  }
 }
 private static void appendSettings(Context context,String query,ArrayList<Hit> found){
  boolean numeric=query.matches("[0-9]+");
  String term=numeric?query:fold(query);
  if(term.isEmpty())return;
  ArrayList<Hit> exact=new ArrayList<>(),prefix=new ArrayList<>(),partial=new ArrayList<>();
  for(SettingPage page:SETTING_PAGES){
   int rank=page.rank(term,numeric);
   if(rank==0)exact.add(page.hit(context));
   else if(rank==1)prefix.add(page.hit(context));
   else if(rank==2)partial.add(page.hit(context));
  }
  found.addAll(exact);
  found.addAll(prefix);
  found.addAll(partial);
 }
 protected void onCreate(Bundle saved) {
  super.onCreate(saved);
  begin(L.t("Căutare locală"));
  form = true;
  boolean settingsFirst=getIntent().getBooleanExtra(SETTINGS_FIRST,false);
  scope = saved == null ? (settingsFirst?SETTINGS_SCOPE:getIntent().getBooleanExtra("apps_first", false)?1:0)
      : Math.max(0, Math.min(SCOPES.length - 1, saved.getInt("scope", 0)));
  numericEntry = saved == null ? settingsFirst||getIntent().getBooleanExtra("home_digits", false) : saved.getBoolean("numeric");
  if(saved!=null)settingsResult=saved.getInt("settings_result",-1);
  query = new EditText(this) {
   @Override public boolean onKeyPreIme(int key, KeyEvent event) {
    if (key != KeyEvent.KEYCODE_BACK) return super.onKeyPreIme(key, event);
    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
     GlobalSearchActivity.this.onBackPressed();
    }
    return true;
   }
  };
  UiTypography.text(query,18);
  query.setSingleLine(true);
  query.setTextColor(android.graphics.Color.WHITE);
  query.setHintTextColor(palette.secondary());
  query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
  setInputMode();
  root.addView(query);
  summary = text(L.t("Se caută…"), 12);
  root.addView(summary);
  results = new ListView(this);
  results.setFocusableInTouchMode(true);
  UiChrome.list(this,results);
  root.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
  left = new Runnable() { public void run() { showScopes(); } };
  footer(L.t("Categorii"), L.t("Rezultate ↓"), L.t("Înapoi"));
  query.setText(saved == null ? getIntent().getStringExtra("query") : saved.getString("query", ""));
  query.setSelection(query.length());
  query.addTextChangedListener(new TextWatcher() {
   public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
   public void onTextChanged(CharSequence value, int start, int before, int count) {
    settingsResult=-1;
    ++generation;
    handler.removeCallbacks(search);
    handler.postDelayed(search, 220);
    refreshFooter();
   }
   public void afterTextChanged(Editable value) {}
  });
  query.setOnFocusChangeListener(new View.OnFocusChangeListener() {
   public void onFocusChange(View view, boolean focused) { refreshFooter(); }
  });
  query.setOnKeyListener(new View.OnKeyListener() {
   public boolean onKey(View view, int key, KeyEvent event) {
    if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
     focusResults();
     return true;
    }
    return false;
   }
  });
  query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
   public boolean onEditorAction(TextView view, int action, KeyEvent event) {
    if (event == null || event.getAction() == KeyEvent.ACTION_UP) focusResults();
    return true;
   }
  });
  query.requestFocus();
  refreshFooter();
 }

 @Override public boolean dispatchKeyEvent(KeyEvent event) {
  if (!LeoActivity.isJackNavigationNoise(event) && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
      && event.getAction() == KeyEvent.ACTION_DOWN && results.hasFocus()
      && results.getSelectedItemPosition() <= 0) {
   query.requestFocus();
   return true;
  }
  return super.dispatchKeyEvent(event);
 }

 private void showScopes() {
  String[] labels = Arrays.copyOf(L.array(SCOPES), SCOPES.length + 1);
  labels[SCOPES.length] = L.t("Schimbă scrierea abc / 123");
  new KeypadDialog(this, L.t("Căutare locală"), labels, new KeypadDialog.Action() {
   public void select(int index) {
    if (index == SCOPES.length) { numericEntry = !numericEntry; setInputMode(); }
    else { if(scope!=index)settingsResult=-1; scope = index; find(); }
    query.requestFocus();
   }
  }).initialSelection(scope).show();
 }

 private void setInputMode() {
  query.setInputType(numericEntry ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_TEXT);
  query.setHint(numericEntry ? L.t("Cifre T9 sau număr") : L.t("Caută local sau introdu un număr"));
  query.setSelection(query.length());
 }

 private void focusResults() {
  if (!results.isEnabled()) return;
  results.requestFocus();
  android.view.inputmethod.InputMethodManager input = (android.view.inputmethod.InputMethodManager)
      getSystemService(INPUT_METHOD_SERVICE);
  input.hideSoftInputFromWindow(query.getWindowToken(), 0);
  if (results.getSelectedItemPosition() < 0) results.setSelection(0);
  refreshFooter();
 }

 private void refreshFooter() {
  boolean editing = query.hasFocus();
  footer(L.t("Categorii"), editing ? L.t("Rezultate ↓") : L.t("Deschide"),
      editing && query.length() > 0 ? L.t("Șterge") : L.t("Înapoi"));
 }

 @Override public void onBackPressed() {
  if (query.hasFocus() && query.length() > 0) {
   int start = query.getSelectionStart(), end = query.getSelectionEnd();
   if (start < 0 || end < 0) return;
   if (start != end) query.getText().delete(Math.min(start, end), Math.max(start, end));
   else if (start > 0) query.getText().delete(Character.offsetByCodePoints(query.getText(), start, -1), start);
   return;
  }
  super.onBackPressed();
 }
 protected void onSaveInstanceState(Bundle b){b.putBoolean("numeric",numericEntry);b.putInt("scope",scope);b.putString("query",query.getText().toString());b.putInt("settings_result",settingsResult);super.onSaveInstanceState(b);}
 protected void onResume(){super.onResume();if(query!=null)find();}
 protected void onPause(){handler.removeCallbacks(search);++generation;super.onPause();}protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
 private void find(){summary.setText(L.t("Se caută…"));results.setEnabled(false);final String term=query.getText().toString().trim();final int token=++generation,chosen=scope;worker.execute(new Runnable(){public void run(){if(token!=generation)return;final ArrayList<Hit> hits=collect(GlobalSearchActivity.this,term,chosen);for(int i=hits.size()-1;i>=0;i--)if(!inScope(hits.get(i),chosen))hits.remove(i);ui(new Runnable(){public void run(){if(token!=generation||isFinishing())return;summary.setText(L.t(SCOPES[chosen])+" · "+hits.size());results.setEnabled(!hits.isEmpty());if(hits.isEmpty())query.requestFocus();ArrayList<String> labels=new ArrayList<String>();for(Hit h:hits)labels.add(h.label);if(labels.isEmpty())labels.add(term.isEmpty()?L.t("Scrie pentru a căuta local"):L.t("Niciun rezultat · limite locale"));results.setAdapter(new ArrayAdapter<String>(GlobalSearchActivity.this,android.R.layout.simple_list_item_1,labels){public View getView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);UiChrome.listRow(GlobalSearchActivity.this,t);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(8),dp(5),dp(8),dp(5));t.setBackground(UiChrome.row(palette,dp(6),dp(1)));t.setSelected(p==results.getSelectedItemPosition());return t;}});results.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> a,View v,int i,long id){
    if(i>=hits.size())return;
    try{startActivity(hits.get(i).intent);if(chosen==SETTINGS_SCOPE)settingsResult=i;}
    catch(RuntimeException e){notice(L.t("Rezultat indisponibil"));}
   }});
   if(chosen==SETTINGS_SCOPE&&settingsResult>=0&&!hits.isEmpty()){
    focusResults();
    results.setSelection(Math.min(settingsResult,hits.size()-1));
   }}});}});}
 static boolean inScope(Hit hit,int scope){if(scope==0)return true;String name=hit.intent.getComponent()==null?"":hit.intent.getComponent().getClassName();int type=(HomeAccess.internal(hit.intent)||name.endsWith("MainActivity"))?1:(name.endsWith("ContactsActivity")||name.endsWith("PhoneActivity"))?2:name.endsWith("MediaActivity")?4:(hit.label.startsWith(L.t("Setare · "))||name.contains("SettingsActivity")||name.endsWith("SoundScheduleActivity")||name.endsWith("SetupWizardActivity"))?3:5;return type==scope;}
 static ArrayList<Hit> collect(Context c,String term){return collect(c,term,0);}
 static ArrayList<Hit> collect(Context c,String term,int scope){ArrayList<Hit> found=new ArrayList<Hit>();if(term.isEmpty()){if(scope==0||scope==1)for(int id:HomeAccess.get(c,"recent"))if(!AppCatalog.settingsOnly(id))found.add(new Hit(L.t("Recent · ")+AppCatalog.NAMES[id],HomeAccess.intent(c).putExtra("open_app",id)));return found;}if((scope==0||scope==2)&&dialable(term))found.add(new Hit(L.t("Deschide în Telefon · ")+term,new Intent(c,PhoneActivity.class).putExtra("number",term)));
  if(scope==0||scope==1)for(int i=0;i<AppCatalog.NAMES.length&&found.size()<20;i++)if(!AppCatalog.settingsOnly(i)&&matches(AppCatalog.NAMES[i],term))found.add(new Hit(L.t("Aplicație · ")+AppCatalog.NAMES[i],HomeAccess.intent(c).putExtra("open_app",i).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
  if(scope==1)return found;
  if(scope==0||scope==SETTINGS_SCOPE)appendSettings(c,term,found);
  if(scope==SETTINGS_SCOPE)return found;
  if(scope==0||scope==2)try(Cursor rows=c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{"contact_id","display_name","data1"},null,null,"display_name COLLATE LOCALIZED ASC")){HashSet<Long> ids=new HashSet<Long>();int scanned=0,n=0;while(rows!=null&&rows.moveToNext()&&scanned++<2000&&n<20)if((matches(rows.getString(1),term)||matches(rows.getString(2),term))&&ids.add(rows.getLong(0))){found.add(new Hit(L.t("Contact · ")+rows.getString(1),new Intent(c,ContactsActivity.class).putExtra("contact_id",rows.getLong(0))));n++;}}catch(RuntimeException ignored){}
  if(scope==2)return found;
  if(scope==0||scope==5)try{File f=new File(c.getFilesDir(),"notes.json");if(BackupEngine.atomicExists(f)){JSONArray notes=new JSONArray(new String(BackupEngine.atomicBytes(f,1024*1024),"UTF-8"));int n=0;for(int i=0;i<notes.length()&&n<20;i++){JSONObject note=notes.getJSONObject(i);String body=note.getString("body");if(matches(body,term)){found.add(new Hit(L.t("Notiță · ")+body.replace('\n',' ').substring(0,Math.min(body.length(),60)),new Intent(c,NotesActivity.class).putExtra("note_id",note.getString("id"))));n++;}}}}catch(Exception ignored){}
  if(scope==0||scope==4)try(Cursor rows=c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","title","artist"},null,null,"title COLLATE NOCASE ASC")){int scanned=0,n=0;while(rows!=null&&rows.moveToNext()&&scanned++<2000&&n<20)if(matches(rows.getString(1),term)||matches(rows.getString(2),term)){found.add(new Hit(L.t("Muzică · ")+rows.getString(1),new Intent(c,MediaActivity.class).setData(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,rows.getLong(0)))));n++;}}catch(RuntimeException ignored){}if(scope==0||scope==5)ExtendedSearch.append(c,term,found);return found;
 }
}
