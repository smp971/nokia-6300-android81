package ro.leo.keypad;

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

/** Offline physical-key IME. No network, dictionaries, logging or text persistence. */
public final class KeypadIme extends InputMethodService {
    private static final String[] LETTERS={" ",".,?!'\"-:;@1","abcăâ2","def3","ghiî4","jkl5","mno6","pqrsș7","tuvț8","wxyz9"};
    private static final String SYMBOLS=".,?!@-_/:;()*+=#";
    private final Handler handler=new Handler();
    private final SparseBooleanArray consumed=new SparseBooleanArray();
    private TextView strip;
    private int pending=-1, index, mode, inputClass;
    private boolean editing;
    private final Runnable commit=new Runnable() { public void run() { finishLetter(); } };

    public View onCreateInputView() {
        strip=new TextView(this); strip.setTextSize(12); strip.setTextColor(Color.WHITE);
        strip.setBackgroundColor(0xff20162f); strip.setGravity(Gravity.CENTER);
        int padding=(int)(5*getResources().getDisplayMetrics().density);
        strip.setPadding(2,padding,2,padding); updateStrip(); return strip;
    }
    public boolean onEvaluateFullscreenMode() { return false; }
    public boolean onEvaluateInputViewShown() { return true; }
    public boolean onShowInputRequested(int flags,boolean changing) { return editing; }
    public void onStartInput(EditorInfo info,boolean restarting) {
        super.onStartInput(info,restarting);
        handler.removeCallbacks(commit); pending=-1; consumed.clear(); mode=0;
        inputClass=info.inputType & InputType.TYPE_MASK_CLASS;
        editing=inputClass!=InputType.TYPE_NULL; updateStrip();
    }
    public void onFinishInput() { finishLetter(); editing=false; super.onFinishInput(); }
    public void onDestroy() { handler.removeCallbacks(commit); super.onDestroy(); }
    private void updateStrip() {
        if(strip!=null) strip.setText(inputClass==InputType.TYPE_CLASS_PHONE || inputClass==InputType.TYPE_CLASS_NUMBER
            || inputClass==InputType.TYPE_CLASS_DATETIME ? "123 · Taste numerice"
            : (mode==0?"abc":mode==1?"ABC":"123")+" · # mod · 0 spațiu · Șterge");
    }
    private void finishLetter() {
        handler.removeCallbacks(commit);
        InputConnection connection=getCurrentInputConnection();
        if(pending!=-1 && connection!=null) connection.finishComposingText();
        pending=-1; index=0;
    }
    private void letter(int digit) {
        InputConnection connection=getCurrentInputConnection(); if(connection==null) return;
        if(pending==digit) index++; else { finishLetter(); pending=digit; index=0; }
        String letters=digit==10?SYMBOLS:LETTERS[digit];
        String text=letters.substring(index%letters.length(), index%letters.length()+1);
        if(mode==1) text=text.toUpperCase(new Locale("ro"));
        connection.setComposingText(text,1);
        handler.removeCallbacks(commit); handler.postDelayed(commit,850);
    }
    private void erase() {
        InputConnection connection=getCurrentInputConnection(); if(connection==null) return;
        handler.removeCallbacks(commit);
        if(pending!=-1) { connection.setComposingText("",1); connection.finishComposingText(); pending=-1; index=0; }
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
            if(event.getRepeatCount()>0) { if(erase) erase(); return true; }
            InputConnection connection=getCurrentInputConnection();
            if(erase) erase();
            else if(key==KeyEvent.KEYCODE_POUND) { finishLetter(); mode=(mode+1)%3; updateStrip(); }
            else if(enter) {
                if(pending!=-1) finishLetter();
                else if(!sendDefaultEditorAction(true)) connection.commitText("\n",1);
            } else if(key==KeyEvent.KEYCODE_STAR && mode!=2) letter(10);
            else if(mode==2) { finishLetter(); connection.commitText(digit>=0?String.valueOf((char)('0'+digit)):"*",1); }
            else if(digit==0) { finishLetter(); connection.commitText(" ",1); }
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
    }
}
