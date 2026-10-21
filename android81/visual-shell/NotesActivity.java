package ro.leo.visual;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/** Private local notes; no contacts, storage, network or message permissions. */
public final class NotesActivity extends Activity {
    private Appearance.Palette palette;
    private final ArrayList<Note> notes=new ArrayList<Note>();
    private final Handler handler=new Handler();
    private AtomicFile store;
    private EditText editor;
    private ListView list;
    private String editingId;
    private boolean editing,readFailed;
    private int selected;
    private static final class Note { String id,body; }
    private final Runnable draft=new Runnable() { public void run() { saveDraft(); } };
    public void onCreate(Bundle state) {
        super.onCreate(state);palette=Appearance.palette(this);
        getWindow().setStatusBarColor(palette.bar);
        store=new AtomicFile(new File(getFilesDir(),"notes.json"));
        if(store.getBaseFile().exists() || new File(store.getBaseFile()+".bak").exists()) {
            try {
                JSONArray saved=new JSONArray(new String(store.readFully(),StandardCharsets.UTF_8));
                for(int i=0;i<saved.length();i++) {
                    JSONObject item=saved.getJSONObject(i); Note note=new Note();
                    note.id=item.getString("id"); note.body=item.getString("body"); notes.add(note);
                }
            } catch(Exception invalid) { readFailed=true; }
        }
        SharedPreferences saved=getSharedPreferences("notes-draft",MODE_PRIVATE);
        if(!readFailed && saved.getBoolean("editing",false)) edit(saved.getString("id",""),saved.getString("body",""));
        else {String target=getIntent().getStringExtra("note_id");boolean opened=false;if(target!=null)for(Note n:notes)if(target.equals(n.id)){edit(n.id,n.body);opened=true;break;}if(!opened)showList();}
    }
    protected void onPause() { handler.removeCallbacks(draft); saveDraft(); super.onPause(); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show(); }
    private int height(int dp) { return (int)(dp*getResources().getDisplayMetrics().density+.5f); }
    private TextView label(String value,int size) {
        TextView text=new TextView(this); text.setText(value); text.setTextSize(size);
        text.setTextColor(Color.WHITE); text.setGravity(Gravity.CENTER_VERTICAL); return text;
    }
    private LinearLayout base() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(palette.background);
        TextView title=label(editing?"Notiță":"Notițe",22); title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(palette.card);
        root.addView(title,new LinearLayout.LayoutParams(-1,height(42))); return root;
    }
    private void footer(LinearLayout root,String left,String center,String right) {
        LinearLayout bar=new LinearLayout(this); bar.setBackgroundColor(palette.bar);
        String[] labels={left,center,right};
        for(int i=0;i<3;i++) {
            TextView item=label(labels[i],14); item.setGravity(i==0?Gravity.LEFT|Gravity.CENTER_VERTICAL:i==1?Gravity.CENTER:Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            item.setPadding(height(5),0,height(5),0); bar.addView(item,new LinearLayout.LayoutParams(0,height(34),1));
        }
        root.addView(bar);
    }
    private void showList() {
        editing=false; editor=null;
        LinearLayout root=base();
        TextView help=label("# opțiuni · "+notes.size()+" / 100",14); help.setGravity(Gravity.CENTER);
        root.addView(help,new LinearLayout.LayoutParams(-1,height(24)));
        if(readFailed || notes.isEmpty()) {
            TextView empty=label(readFailed?"Nu pot citi notițele salvate":"Nicio notiță\nSoft stânga: notiță nouă",18);
            empty.setGravity(Gravity.CENTER); root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));
            list=null;
        } else {
            list=new ListView(this); list.setDividerHeight(height(3)); list.setPadding(height(6),0,height(6),0);
            list.setSelector(android.R.color.transparent); list.setAdapter(new BaseAdapter() {
                public int getCount() { return notes.size(); }
                public Object getItem(int position) { return notes.get(position); }
                public long getItemId(int position) { return position; }
                public View getView(int position,View reuse,ViewGroup parent) {
                    TextView item=label(title(notes.get(position).body),18);
                    item.setSingleLine(true); item.setEllipsize(TextUtils.TruncateAt.END);
                    item.setPadding(height(10),0,height(10),0); item.setMinHeight(height(48));
                    item.setBackground(Appearance.surface(position==selected?palette.selection:palette.card,height(8)));
                    item.setTextColor(position==selected?palette.ink:Color.WHITE); return item;
                }
            });
            list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                    selected=position; ((BaseAdapter)list.getAdapter()).notifyDataSetChanged();
                }
                public void onNothingSelected(AdapterView<?> parent) { }
            });
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent,View view,int position,long id) { open(position); }
            });
            root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        }
        footer(root,"Nouă","Deschide","Înapoi"); setContentView(root);
        if(list!=null) { selected=Math.min(selected,notes.size()-1); list.requestFocus(); list.setSelection(selected); }
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
    }
    private static String title(String body) {
        String value=body.trim(); int end=value.indexOf('\n');
        return value.isEmpty()?"Notiță fără titlu":end<0?value:value.substring(0,end);
    }
    private void open(int index) {
        if(index>=0 && index<notes.size()) { Note note=notes.get(index); edit(note.id,note.body); }
    }
    private void edit(String id,String body) {
        if(readFailed) return;
        editing=true; editingId=id;
        LinearLayout root=base(); editor=new EditText(this);
        editor.setTextColor(Color.WHITE); editor.setHintTextColor(0xffd3bddf); editor.setTextSize(19);
        editor.setBackground(Appearance.surface(palette.card,height(8))); editor.setGravity(Gravity.TOP|Gravity.LEFT);
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8192)});
        editor.setHint("Scrie din taste…"); editor.setText(body); editor.setSelection(editor.length());
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                handler.removeCallbacks(draft); handler.postDelayed(draft,400);
            }
            public void afterTextChanged(Editable value) { }
        });
        root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        footer(root,"Salvează","Rând nou","Șterge"); setContentView(root); editor.requestFocus();
        editor.post(new Runnable() { public void run() {
            if(editing && editor!=null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);
        }});
        saveDraft();
    }
    private void saveDraft() {
        if(!editing || editor==null) return;
        getSharedPreferences("notes-draft",MODE_PRIVATE).edit().putBoolean("editing",true)
            .putString("id",editingId).putString("body",editor.getText().toString()).apply();
    }
    private void clearDraft() {
        handler.removeCallbacks(draft); getSharedPreferences("notes-draft",MODE_PRIVATE).edit().clear().apply();
    }
    private boolean write(ArrayList<Note> values) {
        FileOutputStream stream=null;
        try {
            JSONArray output=new JSONArray();
            for(Note note:values) output.put(new JSONObject().put("id",note.id).put("body",note.body));
            stream=store.startWrite(); stream.write(output.toString().getBytes(StandardCharsets.UTF_8));
            store.finishWrite(stream); return true;
        } catch(Exception failure) { if(stream!=null) store.failWrite(stream); toast("Salvarea a eșuat; textul rămâne în ciornă"); return false; }
    }
    private void saveNote() {
        String body=editor.getText().toString();
        if(body.trim().isEmpty()) { toast("Scrie mai întâi o notiță"); return; }
        ArrayList<Note> changed=new ArrayList<Note>(notes); int found=-1;
        for(int i=0;i<changed.size();i++) if(changed.get(i).id.equals(editingId)) found=i;
        if(found<0 && notes.size()>=100) { toast("Maximum 100 de notițe"); return; }
        Note value=new Note(); value.id=editingId.isEmpty()?UUID.randomUUID().toString():editingId; value.body=body;
        if(found<0) { changed.add(0,value); selected=0; } else changed.set(found,value);
        if(write(changed)) { notes.clear(); notes.addAll(changed); clearDraft(); showList(); toast("Salvată"); }
    }
    private void noteOptions() {
        if(notes.isEmpty() || readFailed) return;
        final Note target=notes.get(selected);
        new KeypadDialog(this,"Notiță",new String[]{"Deschide", "Șterge"},new KeypadDialog.Action() {
            public void select(int choice) {
                if(choice==0) open(selected);
                else new KeypadDialog(NotesActivity.this,"Ștergi notița?",new String[]{"Păstrează", "Șterge definitiv"},new KeypadDialog.Action() {
                    public void select(int confirmed) {
                        if(confirmed==1) {
                            ArrayList<Note> changed=new ArrayList<Note>(notes); changed.remove(target);
                            if(write(changed)) { notes.clear(); notes.addAll(changed); selected=Math.max(0,selected-1); showList(); }
                        }
                    }
                }).show();
            }
        }).show();
    }
    public void onBackPressed() {
        if(!editing) { finish(); return; }
        new KeypadDialog(this,"Închide editorul",new String[]{"Salvează", "Renunță la modificări", "Continuă editarea"},new KeypadDialog.Action() {
            public void select(int choice) {
                if(choice==0) saveNote();
                else if(choice==1) { clearDraft(); showList(); }
            }
        }).show();
    }
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(!editing&&CircularNavigation.dispatch(this,event))return true;
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_MENU) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
                if(editing) saveNote();
                else if(!readFailed && notes.size()<100) edit("","");
                else toast(readFailed?"Fișierul existent este păstrat":"Maximum 100 de notițe");
            }
            return true;
        }
        if(!editing && key==KeyEvent.KEYCODE_POUND) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) noteOptions(); return true;
        }
        if(!editing && (key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) open(selected); return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
