package ro.leo.keypad;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.view.*;import android.widget.*;import java.util.*;
/** Explicit local word editing. No surrounding editor content is inspected. */
public final class DictionaryActivity extends Activity {
 private final PersonalWords words=new PersonalWords();private EditText edit;
 private boolean adding,searching,ranked;private String replacing,filter="";
 protected void onCreate(Bundle b){super.onCreate(b);words.load(getSharedPreferences("personal-words",0).getString("words",""));show();}
 private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setPadding(8,8,8,8);return v;}
 private LinearLayout page(String title){LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff171126);root.addView(text(title,21));setContentView(root);return root;}
 private boolean save(){return getSharedPreferences("personal-words",0).edit().putString("words",words.save()).commit();}
 private void show(){
  adding=false;searching=false;replacing=null;LinearLayout root=page("Dicționar personal · "+words.words().size()+"/256");
  final ArrayList<String> visible=new ArrayList<String>();
  for(String word:words.words())if(filter.isEmpty()||word.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))||filter.matches("[2-9]+")&&PredictiveDictionary.digits(word).startsWith(filter))visible.add(word);
  Collections.sort(visible,new Comparator<String>(){public int compare(String a,String b){int n=ranked?Integer.compare(words.count(b),words.count(a)):0;return n!=0?n:a.compareToIgnoreCase(b);}});
  ArrayList<String> rows=new ArrayList<String>();rows.add("Adaugă un cuvânt");rows.add("Caută · "+(filter.isEmpty()?"toate":filter));rows.add("Ordine · "+(ranked?"folosire":"alfabetic"));rows.add("Resetează ordinea învățată");
  for(String word:visible)rows.add(word+" · "+words.count(word)+"\n"+PredictiveDictionary.digits(word));
  ListView list=new ListView(this);list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,rows));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));root.addView(text("OK opțiuni · Meniu adaugă · Înapoi",12));
  list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> parent,View view,final int i,long id){
   if(i==0)editor(false,null);else if(i==1)editor(true,null);else if(i==2){ranked=!ranked;show();}
   else if(i==3)new AlertDialog.Builder(DictionaryActivity.this).setTitle("Păstrezi cuvintele, resetezi frecvențele?").setPositiveButton("Resetează",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){words.resetRanking();persist();}}).setNegativeButton("Păstrează",null).show();
   else {final String word=visible.get(i-4);new AlertDialog.Builder(DictionaryActivity.this).setTitle(word).setItems(new String[]{"Editează","Șterge cuvântul"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int n){if(n==0)editor(false,word);else new AlertDialog.Builder(DictionaryActivity.this).setTitle("Ștergi "+word+"?").setPositiveButton("Șterge",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int n){words.remove(word);persist();}}).setNegativeButton("Păstrează",null).show();}}).show();}
  }});list.requestFocus();root.post(new Runnable(){public void run(){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);}});
 }
 private void persist(){if(!save()){words.load(getSharedPreferences("personal-words",0).getString("words",""));Toast.makeText(this,"Salvarea a eșuat",1).show();}show();}
 private void editor(boolean search,String original){
  adding=true;searching=search;replacing=original;LinearLayout root=page(search?"Caută în dicționar":original==null?"Cuvânt nou":"Editează cuvântul");root.addView(text(search?"Text sau cifre T9. Gol: toate cuvintele.":"2–32 litere. Rămâne doar pe telefon.",15));
  edit=new EditText(this);edit.setTextColor(Color.WHITE);edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});edit.setText(search?filter:original==null?"":original);root.addView(edit);root.addView(text("Soft stânga: "+(search?"caută":"salvează")+"\nÎnapoi: anulează",13));edit.requestFocus();edit.setSelection(edit.length());getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
 }
 public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_MENU){if(e.getRepeatCount()==0){if(!adding)editor(false,null);else if(searching){filter=edit.getText().toString().trim();show();}else if(replacing==null?words.add(edit.getText().toString()):words.replace(replacing,edit.getText().toString()))persist();else Toast.makeText(this,"Cuvânt invalid, existent sau dicționar plin",1).show();}return true;}return super.onKeyDown(key,e);}
 public void onBackPressed(){if(adding)show();else if(!filter.isEmpty()){filter="";show();}else super.onBackPressed();}
}
