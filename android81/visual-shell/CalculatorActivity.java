package ro.leo.visual;
import ro.leo.i18n.L;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

public final class CalculatorActivity extends LeoActivity {
    private String expression="",message="";
    private boolean result;
    private Face face;
    private final KeyRelease actions=new KeyRelease();
    public void onCreate(Bundle state) {
        super.onCreate(state);palette=Appearance.palette(this);
        if(state!=null) { expression=state.getString("expression",""); result=state.getBoolean("result");message=state.getString("message",""); }
        getWindow().setStatusBarColor(palette.bar);
        face=new Face(); setContentView(face);
    }
    protected void onResume(){super.onResume();if(face!=null){face.chrome.appearance(this);face.labels.appearance();face.invalidate();}}
    protected void onSaveInstanceState(Bundle state) {
        state.putString("expression",expression); state.putBoolean("result",result);state.putString("message",message);super.onSaveInstanceState(state);
    }
    private void append(String value) {
        if(result && (Character.isDigit(value.charAt(0)) || value.equals(".") || value.equals("("))) expression="";
        result=false; message="";
        if(expression.length()+value.length()<=120) expression+=value;
        else message=L.t("Maximum 120 caractere");
        face.invalidate();
    }
    private void calculate() {
        try { expression=CalculatorEngine.evaluate(expression); result=true; message=L.t("Rezultat"); }
        catch(ArithmeticException zero) { message=L.t("Împărțire la zero"); }
        catch(IllegalArgumentException invalid) { message=L.t("Expresie invalidă / prea lungă"); }
        face.invalidate();
    }
    protected void onPause(){actions.reset();super.onPause();}
    public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(key!=KeyEvent.KEYCODE_DEL&&!(key==KeyEvent.KEYCODE_BACK&&event.getScanCode()==158)
                &&((key>=KeyEvent.KEYCODE_0&&key<=KeyEvent.KEYCODE_9)||(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_CENTER)
                ||key==KeyEvent.KEYCODE_STAR||key==KeyEvent.KEYCODE_POUND||key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_BACK)){
            if(actions.accept(event))onKeyDown(key,event);return true;
        }
        return super.dispatchKeyEvent(event);
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_DEL || (key==KeyEvent.KEYCODE_BACK && event.getScanCode()==158)) {
            if(!expression.isEmpty()) expression=expression.substring(0,expression.length()-1);
            result=false; message=""; face.invalidate(); return true;
        }
        if(key==KeyEvent.KEYCODE_BACK) { if(event.getRepeatCount()==0) finish(); return true; }
        if(key>=KeyEvent.KEYCODE_0 && key<=KeyEvent.KEYCODE_9) { if(event.getRepeatCount()==0) append(""+(key-KeyEvent.KEYCODE_0)); return true; }
        if(event.getRepeatCount()>0) return true;
        switch(key) {
            case KeyEvent.KEYCODE_DPAD_UP: append("+"); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: append("-"); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: append("*"); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: append("/"); return true;
            case KeyEvent.KEYCODE_STAR: append("."); return true;
            case KeyEvent.KEYCODE_POUND: expression=""; result=false; message=""; face.invalidate(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: calculate(); return true;
            case KeyEvent.KEYCODE_MENU:
                new KeypadDialog(this,"Calculator",new String[]{L.t("Șterge tot"), L.t("Schimbă semnul"), L.t("Procent (%)"), L.t("Paranteză ("), L.t("Paranteză )"),ContextHelp.label()},new KeypadDialog.Action() {
                    public void select(int choice) {
                        if(choice==0) { expression=""; result=false; message=""; face.invalidate(); }
                        else if(choice==1) {
                            if(!expression.isEmpty()) { expression="-("+expression+")"; result=false; calculate(); }
                        } else if(choice==5) ContextHelp.open(CalculatorActivity.this,"CalculatorActivity");
                        else append(choice==2?"%":choice==3?"(":")");
                    }
                }).show(); return true;
        }
        return super.onKeyDown(key,event);
    }
    private final class Face extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final CanvasChrome chrome=new CanvasChrome(CalculatorActivity.this,p);
        final CanvasText labels=new CanvasText(CalculatorActivity.this,p);
        Face() { super(CalculatorActivity.this); setFocusable(true); requestFocus(); }
        protected void onDraw(Canvas c) {
            c.save();c.scale(getWidth()/240f,getHeight()/302f);c.drawColor(palette.background);chrome.header(c,L.t("Calculator"),palette);
            p.setStyle(Paint.Style.FILL);p.setColor(palette.card);c.drawRoundRect(8,52,232,165,6,6,p);
            labels.draw(c,L.t(result?"Rezultat":"Expresie"),20,76,14,palette.accent,Paint.Align.LEFT,200);
            String shown=expression.isEmpty()?"0":expression.replace('*','×').replace('/','÷');
            labels.prepare(36,Color.WHITE,Paint.Align.RIGHT);c.drawText(KeypadText.tail(p,shown,200),220,127,p);
            labels.draw(c,message,20,156,13,palette.secondary(),Paint.Align.LEFT,200);
            String[] keys={"↑  +","↓  −","←  ×","→  ÷"};
            for(int i=0;i<4;i++){float x=8+i*57;p.setColor(palette.card);c.drawRoundRect(x,184,x+53,232,4,4,p);
                labels.draw(c,keys[i],x+26,213,16,palette.accent,Paint.Align.CENTER,49);}
            chrome.footer(c,palette,L.t("Opțiuni"),"=",L.t("Șterge"));c.restore();
        }
    }
}
