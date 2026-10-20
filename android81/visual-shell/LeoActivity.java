package ro.leo.visual;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Common keypad lists/forms for the system applications. */
abstract class LeoActivity extends Activity {
    interface Choice { void pick(int index); }
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    LinearLayout root;
    Runnable left,back;
    boolean form;
    Appearance.Palette palette;
    private String listTitle;private java.util.ArrayList<String> listRows;private BaseAdapter listAdapter;
    protected void onCreate(Bundle state) {
        super.onCreate(state); palette=Appearance.palette(this);getWindow().setStatusBarColor(palette.bar);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    TextView text(String value,int size) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size*Appearance.textScale(this));
        view.setTextColor(Color.WHITE); view.setPadding(dp(8),dp(4),dp(8),dp(4)); return view;
    }
    void begin(String title) {
        form=false; left=null; back=null;
        listTitle=null;listRows=null;listAdapter=null;
        palette=Appearance.palette(this);getWindow().setStatusBarColor(palette.bar);
        root=new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(palette.background);
        TextView heading=text(title,21); heading.setGravity(Gravity.CENTER_VERTICAL);heading.setSingleLine(true);heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setCompoundDrawables(new AppBadge(title,dp(29)),null,null,null);heading.setCompoundDrawablePadding(dp(10));
        heading.setBackgroundColor(palette.card);
        root.addView(heading,new LinearLayout.LayoutParams(-1,dp(42)));
        View accent=new View(this);accent.setBackgroundColor(palette.accent);root.addView(accent,new LinearLayout.LayoutParams(-1,dp(2)));
    }
    void footer(String a,String b,String c) {
        LinearLayout bar=new LinearLayout(this); bar.setBackgroundColor(palette.bar);
        String[] values={a,b,c};
        for(int i=0;i<3;i++) {
            TextView label=text(values[i],13); label.setSingleLine(true);
            if(i==1)label.setTextColor(palette.accent);
            label.setGravity(i==0?Gravity.LEFT|Gravity.CENTER_VERTICAL:i==1?Gravity.CENTER:Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            bar.addView(label,new LinearLayout.LayoutParams(0,dp(34),1));
        }
        root.addView(bar); setContentView(root);
        if(!form) root.post(new Runnable() { public void run() {
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(),0);
        }});
    }
    void list(String title,final List<String> values,int selected,final Choice choice,String action,Runnable command) {
        begin(title); left=command;
        listTitle=title;listRows=new java.util.ArrayList<String>(values);final List<String> rows=listRows;
        final ListView list=new ListView(this); list.setPadding(dp(6),0,dp(6),0); list.setDividerHeight(dp(3));
        list.setSelector(android.R.color.transparent);
        final int[] focused={Math.max(0,Math.min(selected,values.size()-1))};
        final BaseAdapter adapter=new BaseAdapter() {
            public int getCount() { return rows.size(); }
            public Object getItem(int i) { return rows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i,View reuse,android.view.ViewGroup parent) {
                TextView label;
                if(reuse instanceof TextView) label=(TextView)reuse;
                else {
                    label=text("",18); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END);
                    label.setMinHeight(dp(52)); label.setGravity(Gravity.CENTER_VERTICAL);
                    android.graphics.drawable.StateListDrawable surfaces=new android.graphics.drawable.StateListDrawable();
                    surfaces.addState(new int[]{android.R.attr.state_selected},Appearance.surface(palette.selection,dp(7)));
                    surfaces.addState(new int[]{},Appearance.surface(palette.card,dp(7)));label.setBackground(surfaces);
                }
                label.setText(rows.get(i));label.setCompoundDrawables(rowIcon(title,i),null,null,null);label.setCompoundDrawablePadding(dp(9)); paintSelection(label,i==focused[0]); return label;
            }
        };
        listAdapter=adapter;list.setAdapter(adapter);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p,View v,int i,long id) {
                int previous=focused[0]; focused[0]=i;
                onListSelection(title,i);
                if(previous!=i) paintSelection(list.getChildAt(previous-list.getFirstVisiblePosition()),false);
                paintSelection(v,true);
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p,View v,int i,long id) { choice.pick(i); }
        });
        if(values.isEmpty()) {
            TextView empty=text("Nicio intrare",18); empty.setGravity(Gravity.CENTER);
            root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));
        } else root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        footer(action,"Deschide","Înapoi");
        list.requestFocus(); list.setSelection(focused[0]);
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);
    }
    boolean replaceRows(String title,List<String> rows){if(!title.equals(listTitle)||listRows==null||listAdapter==null||rows.size()!=listRows.size())return false;listRows.clear();listRows.addAll(rows);listAdapter.notifyDataSetChanged();return true;}
    android.graphics.drawable.Drawable rowIcon(String title,int index){return null;}
    void onListSelection(String title,int index){}
    private void paintSelection(View row,boolean selected) {
        if(!(row instanceof TextView)) return;
        row.setSelected(selected);
        ((TextView)row).setTextColor(selected?palette.ink:Color.WHITE);
    }
    LinearLayout fields(String title) {
        begin(title); form=true;
        ScrollView scroll=new ScrollView(this); LinearLayout fields=new LinearLayout(this); fields.setOrientation(1);
        scroll.addView(fields); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); return fields;
    }
    EditText field(LinearLayout fields,String label,String value,int type) {
        fields.addView(text(label,15)); EditText edit=new EditText(this); edit.setId(View.generateViewId());
        edit.setTextSize(19*Appearance.textScale(this)); edit.setTextColor(Color.WHITE); edit.setHintTextColor(0xffd3bddf);
        edit.setBackground(Appearance.fieldSurface(palette,dp(7)));edit.setPadding(dp(10),dp(8),dp(10),dp(8)); edit.setInputType(type); edit.setText(value);
        if((type&InputType.TYPE_TEXT_FLAG_MULTI_LINE)==0) edit.setSingleLine(true);
        // setSingleLine replaces the transformation; restore masking for password fields.
        if((type&InputType.TYPE_MASK_VARIATION)==InputType.TYPE_TEXT_VARIATION_PASSWORD)
            edit.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});
        edit.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View view,int key,KeyEvent event) {
            if(event.getAction()!=KeyEvent.ACTION_DOWN || (key!=KeyEvent.KEYCODE_DPAD_UP && key!=KeyEvent.KEYCODE_DPAD_DOWN)) return false;
            EditText field=(EditText)view;
            boolean multiline=(field.getInputType()&InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0;
            if(multiline && field.getLayout()!=null) {
                int line=field.getLayout().getLineForOffset(Math.max(0,field.getSelectionStart()));
                if(key==KeyEvent.KEYCODE_DPAD_UP && line>0) return false;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN && line<field.getLayout().getLineCount()-1) return false;
            }
            View next=view.focusSearch(key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN);
            if(next!=null && next!=view && (next instanceof EditText || next instanceof CheckBox)) { next.requestFocus(); return true; }
            return false;
        }});
        fields.addView(edit,new LinearLayout.LayoutParams(-1,-2)); return edit;
    }
    void options(String title,String[] values,final Choice action) {
        new KeypadDialog(this,title,values,new KeypadDialog.Action() { public void select(int i) { action.pick(i); } }).show();
    }
    void notice(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    void ui(final Runnable action) { runOnUiThread(new Runnable() { public void run() { if(!isFinishing() && !isDestroyed()) action.run(); } }); }
    public boolean dispatchKeyEvent(KeyEvent event) {long start=android.os.SystemClock.elapsedRealtimeNanos();try{return dispatchLeoKey(event);}finally{if(event.getAction()==KeyEvent.ACTION_DOWN)UiMetrics.key(android.os.SystemClock.elapsedRealtimeNanos()-start);}}
    private boolean dispatchLeoKey(KeyEvent event) {
        if(isJackNavigationNoise(event)) return true;
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU && left!=null) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) left.run(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    // This codec emits BTN_4/BTN_5 during analog route changes. Generic.kcm
    // falls back to DPAD_CENTER, which must never activate a foreground button.
    static boolean isJackNavigationNoise(KeyEvent event) {
        int scan=event.getScanCode();
        android.view.InputDevice device=event.getDevice();
        return (scan==259 || scan==260) && device!=null
            && "msm8909-snd-card Button Jack".equals(device.getName());
    }
    public void onBackPressed() { if(back!=null) back.run(); else finish(); }
}
