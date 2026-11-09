package ro.leo.visual;
import ro.leo.i18n.L;
import android.app.*;import android.content.*;import android.database.Cursor;import android.os.*;import android.provider.*;import android.text.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;

/** On-demand, bounded local search. No persistent index or background service. */
public final class GlobalSearchActivity extends LeoActivity {
 static final class Hit {final String label;final Intent intent;Hit(String s,Intent i){label=s;intent=i;}}
 static final String[] SCOPES={"Toate","Aplicații","Contacte","Setări","Muzică","Altele"};
 private int scope;private boolean numericEntry;
 private TextView summary;private EditText query;private ListView results;private final Handler handler=new Handler();private volatile int generation;
 private final Runnable search=new Runnable(){public void run(){find();}};
 static boolean dialable(String value){return value!=null&&value.matches("[+0-9*#][0-9*#]{0,31}");}
 static boolean matches(String value,String query){return (query.matches("[0-9]+")&&ContactKeys.matches(value==null?"":value,"",query))||fold(value).contains(fold(query));}
 private static String fold(String s){return java.text.Normalizer.normalize(s==null?"":s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
 protected void onCreate(Bundle saved) {
  super.onCreate(saved);
  begin(L.t("Caută în Leo"));
  form = true;
  scope = saved == null ? (getIntent().getBooleanExtra("apps_first", false) ? 1 : 0)
      : Math.max(0, Math.min(SCOPES.length - 1, saved.getInt("scope", 0)));
  numericEntry = saved == null ? getIntent().getBooleanExtra("home_digits", false) : saved.getBoolean("numeric");
  query = new EditText(this) {
   @Override public boolean onKeyPreIme(int key, KeyEvent event) {
    if (key != KeyEvent.KEYCODE_BACK) return super.onKeyPreIme(key, event);
    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
     GlobalSearchActivity.this.onBackPressed();
    }
    return true;
   }
  };
  query.setTextSize(18 * Appearance.textScale(this));
  query.setSingleLine(true);
  query.setTextColor(android.graphics.Color.WHITE);
  query.setHintTextColor(0xffaaaaaa);
  query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
  setInputMode();
  root.addView(query);
  summary = text(L.t("Se caută…"), 12);
  root.addView(summary);
  results = new ListView(this);
  results.setFocusableInTouchMode(true);
  root.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
  left = new Runnable() { public void run() { showScopes(); } };
  footer(L.t("Categorii"), L.t("Rezultate ↓"), L.t("Înapoi"));
  query.setText(saved == null ? getIntent().getStringExtra("query") : saved.getString("query", ""));
  query.setSelection(query.length());
  query.addTextChangedListener(new TextWatcher() {
   public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
   public void onTextChanged(CharSequence value, int start, int before, int count) {
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
  new KeypadDialog(this, L.t("Caută în Leo"), labels, new KeypadDialog.Action() {
   public void select(int index) {
    if (index == SCOPES.length) { numericEntry = !numericEntry; setInputMode(); }
    else { scope = index; find(); }
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
 protected void onSaveInstanceState(Bundle b){b.putBoolean("numeric",numericEntry);b.putInt("scope",scope);b.putString("query",query.getText().toString());super.onSaveInstanceState(b);}
 protected void onResume(){super.onResume();if(query!=null)find();}
 protected void onPause(){handler.removeCallbacks(search);++generation;super.onPause();}protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
 private void find(){summary.setText(L.t("Se caută…"));results.setEnabled(false);final String term=query.getText().toString().trim();final int token=++generation,chosen=scope;worker.execute(new Runnable(){public void run(){if(token!=generation)return;final ArrayList<Hit> hits=collect(GlobalSearchActivity.this,term,chosen);for(int i=hits.size()-1;i>=0;i--)if(!inScope(hits.get(i),chosen))hits.remove(i);ui(new Runnable(){public void run(){if(token!=generation||isFinishing())return;summary.setText(L.t(SCOPES[chosen])+" · "+hits.size());results.setEnabled(!hits.isEmpty());if(hits.isEmpty())query.requestFocus();ArrayList<String> labels=new ArrayList<String>();for(Hit h:hits)labels.add(h.label);if(labels.isEmpty())labels.add(term.isEmpty()?L.t("Scrie pentru a căuta local"):L.t("Niciun rezultat · limite locale"));results.setAdapter(new ArrayAdapter<String>(GlobalSearchActivity.this,android.R.layout.simple_list_item_1,labels){public View getView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(android.graphics.Color.WHITE);t.setTextSize(16*Appearance.textScale(GlobalSearchActivity.this));t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(8),dp(5),dp(8),dp(5));t.setBackground(UiChrome.row(palette,dp(6),dp(1)));t.setSelected(p==results.getSelectedItemPosition());return t;}});results.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> a,View v,int i,long id){if(i<hits.size())try{startActivity(hits.get(i).intent);}catch(RuntimeException e){notice(L.t("Rezultat indisponibil"));}}});}});}});}
 static boolean inScope(Hit hit,int scope){if(scope==0)return true;String name=hit.intent.getComponent()==null?"":hit.intent.getComponent().getClassName();int type=name.endsWith("MainActivity")?1:(name.endsWith("ContactsActivity")||name.endsWith("PhoneActivity"))?2:name.endsWith("MediaActivity")?4:(hit.label.startsWith(L.t("Setare · "))||name.contains("SettingsActivity")||name.endsWith("SoundScheduleActivity")||name.endsWith("SetupWizardActivity"))?3:5;return type==scope;}
 static ArrayList<Hit> collect(Context c,String term){return collect(c,term,0);}
 static ArrayList<Hit> collect(Context c,String term,int scope){ArrayList<Hit> found=new ArrayList<Hit>();if(term.isEmpty()){if(scope==0||scope==1)for(int id:HomeAccess.get(c,"recent"))if(!AppCatalog.settingsOnly(id))found.add(new Hit(L.t("Recent · ")+AppCatalog.NAMES[id],new Intent(c,MainActivity.class).putExtra("open_app",id)));return found;}if((scope==0||scope==2)&&dialable(term))found.add(new Hit(L.t("Deschide în Telefon · ")+term,new Intent(c,PhoneActivity.class).putExtra("number",term)));
  if(scope==0||scope==1)for(int i=0;i<AppCatalog.NAMES.length&&found.size()<20;i++)if(!AppCatalog.settingsOnly(i)&&matches(AppCatalog.NAMES[i],term))found.add(new Hit(L.t("Aplicație · ")+AppCatalog.NAMES[i],new Intent(c,MainActivity.class).putExtra("open_app",i).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)));
  if(scope==1)return found;
  String[] settingNames=L.array(new String[]{"Wi-Fi","Bluetooth","Program sonor","Accesibilitate Leo","Configurare inițială","Setări Leo","Tastatură și text","Fundaluri și teme","SIM-uri și rețea mobilă"});
  Class<?>[] settingPages={ConnectivityActivity.class,ConnectivityActivity.class,SoundScheduleActivity.class,AccessibilitySettingsActivity.class,SetupWizardActivity.class,SettingsActivity.class,InputSettingsActivity.class,WallpaperActivity.class,MobileSettingsActivity.class};
  if(scope==0||scope==3)for(int i=0;i<settingNames.length;i++)if(matches(settingNames[i],term)){Intent intent=new Intent(c,settingPages[i]);if(i<2)intent.putExtra("kind",i==0?"wifi":"bluetooth");found.add(new Hit(L.t("Setare · ")+settingNames[i],intent));}
  if(scope==3)return found;
  if(scope==0||scope==2)try(Cursor rows=c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{"contact_id","display_name","data1"},null,null,"display_name COLLATE LOCALIZED ASC")){HashSet<Long> ids=new HashSet<Long>();int scanned=0,n=0;while(rows!=null&&rows.moveToNext()&&scanned++<2000&&n<20)if((matches(rows.getString(1),term)||matches(rows.getString(2),term))&&ids.add(rows.getLong(0))){found.add(new Hit(L.t("Contact · ")+rows.getString(1),new Intent(c,ContactsActivity.class).putExtra("contact_id",rows.getLong(0))));n++;}}catch(RuntimeException ignored){}
  if(scope==2)return found;
  if(scope==0||scope==5)try{File f=new File(c.getFilesDir(),"notes.json");if(BackupEngine.atomicExists(f)){JSONArray notes=new JSONArray(new String(BackupEngine.atomicBytes(f,1024*1024),"UTF-8"));int n=0;for(int i=0;i<notes.length()&&n<20;i++){JSONObject note=notes.getJSONObject(i);String body=note.getString("body");if(matches(body,term)){found.add(new Hit(L.t("Notiță · ")+body.replace('\n',' ').substring(0,Math.min(body.length(),60)),new Intent(c,NotesActivity.class).putExtra("note_id",note.getString("id"))));n++;}}}}catch(Exception ignored){}
  if(scope==0||scope==4)try(Cursor rows=c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","title","artist"},null,null,"title COLLATE NOCASE ASC")){int scanned=0,n=0;while(rows!=null&&rows.moveToNext()&&scanned++<2000&&n<20)if(matches(rows.getString(1),term)||matches(rows.getString(2),term)){found.add(new Hit(L.t("Muzică · ")+rows.getString(1),new Intent(c,MediaActivity.class).setData(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,rows.getLong(0)))));n++;}}catch(RuntimeException ignored){}if(scope==0||scope==5)ExtendedSearch.append(c,term,found);return found;
 }
}
