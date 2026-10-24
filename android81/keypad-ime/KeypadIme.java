package ro.leo.keypad;
import ro.leo.i18n.L;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.os.Handler;
import android.text.InputType;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import java.util.Locale;

/** Offline physical-key IME with an original, bounded Romanian prediction dictionary. */
public final class KeypadIme extends InputMethodService {
    private static final String[] LETTERS={" ",".,?!'\"-:;@1","abcăâ2","def3","ghiî4","jkl5","mno6","pqrsș7","tuvț8","wxyz9"};
    private static final String SYMBOLS=".,?!@-_/:;()*+=#";
    private final Handler handler=new Handler();
    private final SparseBooleanArray consumed=new SparseBooleanArray();
    private TextView strip;
    private int pending=-1, index, mode, inputClass,commitDelay=850;
    private boolean editing;
    private boolean password;
    private String sequence="";
    private int candidate;
    private final PersonalWords personal=new PersonalWords();
    private boolean learn,capitalizeSentence;private int capsMode;
    private String cachedSequence=null;
    private java.util.List<String> cachedWords=java.util.Collections.emptyList();
    private final Runnable commit=new Runnable() { public void run() { finishLetter(); } };

    public View onCreateInputView() {
        ro.leo.i18n.Language.sync(this);
        strip=new TextView(this); strip.setTextSize(12); strip.setTextColor(Color.WHITE);
        strip.setBackgroundColor(0xff20162f); strip.setGravity(Gravity.CENTER);
        int padding=(int)(5*getResources().getDisplayMetrics().density);
        strip.setPadding(2,padding,2,padding); updateStrip(); return strip;
    }
    public boolean onEvaluateFullscreenMode() { return false; }
    public boolean onEvaluateInputViewShown() { return true; }
    public boolean onShowInputRequested(int flags,boolean changing) { return editing; }
    public void onStartInput(EditorInfo info,boolean restarting) {
        super.onStartInput(info,restarting);ro.leo.i18n.Language.sync(this);
        handler.removeCallbacks(commit); pending=-1; sequence=""; candidate=0; consumed.clear();
        inputClass=info.inputType & InputType.TYPE_MASK_CLASS;
        int variation=info.inputType & InputType.TYPE_MASK_VARIATION;
        password=(inputClass==InputType.TYPE_CLASS_TEXT && (variation==InputType.TYPE_TEXT_VARIATION_PASSWORD || variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) || (inputClass==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        int configured=android.provider.Settings.Secure.getInt(getContentResolver(),"leo_keypad_default_mode",-1);
        mode=(password||(info.inputType&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0)?0:Math.max(0,Math.min(4,configured<0?getSharedPreferences("keypad",0).getInt("mode",0):configured));
        commitDelay=Math.max(400,Math.min(1500,android.provider.Settings.Secure.getInt(getContentResolver(),"leo_keypad_multitap_ms",850)));
        personal.load(getSharedPreferences("personal-words",0).getString("words",""));cachedSequence=null;
        learn=learningAllowed(info)&&android.provider.Settings.Secure.getInt(getContentResolver(),"leo_keypad_learn_choices",0)==1;
        capsMode=info.inputType&(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_CAP_WORDS|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);capitalizeSentence=false;editing=inputClass!=InputType.TYPE_NULL; updateStrip();
    }
    static boolean learningAllowed(EditorInfo info){int type=info.inputType&InputType.TYPE_MASK_CLASS,v=info.inputType&InputType.TYPE_MASK_VARIATION;if(type!=InputType.TYPE_CLASS_TEXT||(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0||(info.inputType&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0)return false;return v!=InputType.TYPE_TEXT_VARIATION_PASSWORD&&v!=InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD&&v!=InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD&&v!=InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS&&v!=InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS&&v!=InputType.TYPE_TEXT_VARIATION_URI;}
    public void onFinishInput() { finishLetter(); editing=false; super.onFinishInput(); }
    public void onDestroy() { handler.removeCallbacks(commit); super.onDestroy(); }
    private void updateStrip() {
        if(strip!=null) strip.setText(inputClass==InputType.TYPE_CLASS_PHONE || inputClass==InputType.TYPE_CLASS_NUMBER
            || inputClass==InputType.TYPE_CLASS_DATETIME ? "123 · Taste numerice"
            : (mode==0?"abc":mode==1?"ABC":mode==2?"123":mode==3?"T9":"T9 ABC")+" · # mod · "+(sequence.isEmpty()?L.t("0 spațiu"):suggestion()+" · "+(candidate%suggestions().size()+1)+"/"+suggestions().size()+L.t(" · * următor / ținut: anterior")));
    }
    private java.util.List<String> suggestions(){if(!sequence.equals(cachedSequence)){cachedWords=personal.candidates(sequence);cachedSequence=sequence;}return cachedWords;}
    private String suggestion() { java.util.List<String> words=suggestions();if(words.isEmpty())return "";String word=words.get(candidate%words.size());return PredictiveDictionary.cased(word,mode,capitalizeSentence); }
    private void predict(int digit) {
        if(sequence.length()>=32) finishLetter();
        if(sequence.isEmpty()){InputConnection c=getCurrentInputConnection();capitalizeSentence=capsMode!=0&&c!=null&&c.getCursorCapsMode(capsMode)!=0;}
        sequence+=(char)('0'+digit);candidate=0;getCurrentInputConnection().setComposingText(suggestion(),1);updateStrip();
    }
    private void finishLetter() {
        handler.removeCallbacks(commit);
        InputConnection connection=getCurrentInputConnection();
        if((pending!=-1 || !sequence.isEmpty()) && connection!=null){
            if(learn&&!sequence.isEmpty()&&personal.add(suggestion())){getSharedPreferences("personal-words",0).edit().putString("words",personal.save()).apply();cachedSequence=null;}
            connection.finishComposingText();
        }
        pending=-1; index=0; sequence=""; candidate=0; updateStrip();
    }
    private void letter(int digit) {
        InputConnection connection=getCurrentInputConnection(); if(connection==null) return;
        if(pending==digit) index++; else { finishLetter(); pending=digit; index=0; }
        String letters=digit==10?SYMBOLS:LETTERS[digit];
        String text=letters.substring(index%letters.length(), index%letters.length()+1);
        if(mode==1) text=text.toUpperCase(new Locale("ro"));
        connection.setComposingText(text,1);
        handler.removeCallbacks(commit); handler.postDelayed(commit,commitDelay);
    }
    private void erase() {
        InputConnection connection=getCurrentInputConnection(); if(connection==null) return;
        handler.removeCallbacks(commit);
        if(!sequence.isEmpty()) { sequence=sequence.substring(0,sequence.length()-1);candidate=0;connection.setComposingText(suggestion(),1);if(sequence.isEmpty())connection.finishComposingText();updateStrip(); }
        else if(pending!=-1) { connection.setComposingText("",1); connection.finishComposingText(); pending=-1; index=0; }
        else {
            CharSequence selected=connection.getSelectedText(0);
            if(selected!=null && selected.length()>0) connection.commitText("",1);
            else if(!connection.deleteSurroundingTextInCodePoints(1,0)) {
                CharSequence before=connection.getTextBeforeCursor(2,0);
                int count=before!=null && before.length()==2 && Character.isSurrogatePair(before.charAt(0),before.charAt(1))?2:1;
                connection.deleteSurroundingText(count,0);
            }
        }
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(!editing || getCurrentInputConnection()==null) return super.onKeyDown(key,event);
        int digit=key>=KeyEvent.KEYCODE_0 && key<=KeyEvent.KEYCODE_9?key-KeyEvent.KEYCODE_0:-1;
        boolean erase=key==KeyEvent.KEYCODE_DEL || (key==KeyEvent.KEYCODE_BACK && event.getScanCode()==158);
        // Numeric editors retain ordinary digit/star/pound handling, but still
        // support the physical right-softkey deletion shortcut.
        if(!erase && (inputClass==InputType.TYPE_CLASS_PHONE || inputClass==InputType.TYPE_CLASS_NUMBER
            || inputClass==InputType.TYPE_CLASS_DATETIME)) return super.onKeyDown(key,event);
        boolean enter=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER;
        if(digit>=0 || key==KeyEvent.KEYCODE_POUND || key==KeyEvent.KEYCODE_STAR || erase || enter) {
            consumed.put(key,true);
            if(event.getRepeatCount()>0) { if(erase) erase();else if(event.getRepeatCount()==1&&key==KeyEvent.KEYCODE_STAR&&mode>=3&&!sequence.isEmpty()){candidate=Math.floorMod(candidate-2,suggestions().size());getCurrentInputConnection().setComposingText(suggestion(),1);updateStrip();}return true; }
            InputConnection connection=getCurrentInputConnection();
            if(erase) erase();
            else if(key==KeyEvent.KEYCODE_POUND) { finishLetter(); mode=(mode+1)%(password?3:5);if(!password)getSharedPreferences("keypad",0).edit().putInt("mode",mode).apply();updateStrip(); }
            else if(enter) {
                if(pending!=-1 || !sequence.isEmpty()) finishLetter();
                else if(!sendDefaultEditorAction(true)) connection.commitText("\n",1);
            } else if(key==KeyEvent.KEYCODE_STAR && mode>=3 && !sequence.isEmpty()) { candidate++;connection.setComposingText(suggestion(),1);updateStrip(); }
            else if(key==KeyEvent.KEYCODE_STAR && mode!=2) { if(mode>=3)finishLetter();letter(10); }
            else if(mode==2) { finishLetter(); connection.commitText(digit>=0?String.valueOf((char)('0'+digit)):"*",1); }
            else if(digit==0) { finishLetter(); connection.commitText(" ",1); }
            else if(mode>=3 && digit>=2) predict(digit);
            else letter(digit);
            return true;
        }
        // Preserve composing text before moving cursor, changing focus or leaving the editor.
        finishLetter();
        return super.onKeyDown(key,event);
    }
    public boolean onKeyUp(int key,KeyEvent event) {
        if(consumed.get(key)) { consumed.delete(key); return true; }
        return super.onKeyUp(key,event);
    }
    public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidatesStart,int candidatesEnd) {
        super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidatesStart,candidatesEnd);
        if(pending!=-1 && (newStart!=candidatesEnd || newEnd!=candidatesEnd)) finishLetter();
        if(!sequence.isEmpty() && candidatesEnd>=0 && (newStart!=candidatesEnd || newEnd!=candidatesEnd)) finishLetter();
    }
}
