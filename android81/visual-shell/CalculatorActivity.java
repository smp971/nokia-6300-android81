package ro.leo.visual;
import ro.leo.i18n.L;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

public final class CalculatorActivity extends Activity {
    private Appearance.Palette palette;
    private String expression="",message="";
    private boolean result;
    private Face face;
    public void onCreate(Bundle state) {
        super.onCreate(state);palette=Appearance.palette(this);
        if(state!=null) { expression=state.getString("expression",""); result=state.getBoolean("result"); }
        getWindow().setStatusBarColor(palette.bar);
        face=new Face(); setContentView(face);
    }
    protected void onResume(){super.onResume();palette=Appearance.palette(this);if(face!=null)face.invalidate();}
    protected void onSaveInstanceState(Bundle state) {
        state.putString("expression",expression); state.putBoolean("result",result); super.onSaveInstanceState(state);
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
                new KeypadDialog(this,"Calculator",new String[]{L.t("Șterge tot"), L.t("Schimbă semnul"), L.t("Procent (%)"), L.t("Paranteză ("), L.t("Paranteză )")},new KeypadDialog.Action() {
                    public void select(int choice) {
                        if(choice==0) { expression=""; result=false; message=""; face.invalidate(); }
                        else if(choice==1) {
                            if(!expression.isEmpty()) { expression="-("+expression+")"; result=false; calculate(); }
                        } else append(choice==2?"%":choice==3?"(":")");
                    }
                }).show(); return true;
        }
        return super.onKeyDown(key,event);
    }
    private final class Face extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Face() { super(CalculatorActivity.this); setFocusable(true); requestFocus(); }
        void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align) {
            p.setTextSize(size); p.setColor(color); p.setTextAlign(align); c.drawText(value,x,y,p);
        }
        final RetroStyle art=new RetroStyle();
        protected void onDraw(Canvas c) {
            c.save();c.scale(getWidth()/240f,getHeight()/302f);art.background(c,palette);UiChrome.header(c,"Calculator Leo",palette,art.p);
            art.panel(c,10,54,230,149,0xffd4e9c6,0xff8eaa91);
            art.text(c,"CALCULATOR  /  LEO CLASSIC",22,73,10,0xff314c40,Paint.Align.LEFT);
            String shown=expression.isEmpty()?"0":expression.replace('*','×').replace('/','÷');
            if(shown.length()>18)shown="…"+shown.substring(shown.length()-17);
            art.digits(c,shown,218,121,0xff183a30);art.fit(c,message.isEmpty()?L.t("Introduceți o expresie"):message,120,140,11,196,0xff345349,Paint.Align.CENTER);
            String[] keys={"↑  +","↓  −","←  ×","→  ÷"};
            for(int i=0;i<4;i++){float x=10+i*57;art.panel(c,x,166,x+49,205,Appearance.mix(palette.card,Color.WHITE,.16f),palette.card);art.text(c,keys[i],x+24,192,16,Color.WHITE,Paint.Align.CENTER);}
            art.text(c,L.t("0–9 cifre  ·  * zecimal"),120,229,14,Color.WHITE,Paint.Align.CENTER);
            art.text(c,L.t("# șterge tot  ·  OK rezultat"),120,250,13,palette.accent,Paint.Align.CENTER);
            p.setColor(palette.bar);c.drawRect(0,276,240,302,p);art.text(c,L.t("Opțiuni"),7,293,12,Color.WHITE,Paint.Align.LEFT);art.text(c,"=",120,294,20,palette.accent,Paint.Align.CENTER);art.text(c,L.t("Șterge"),233,293,12,Color.WHITE,Paint.Align.RIGHT);c.restore();
        }
    }
}
