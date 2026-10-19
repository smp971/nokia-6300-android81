package ro.leo.visual;

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
    protected void onSaveInstanceState(Bundle state) {
        state.putString("expression",expression); state.putBoolean("result",result); super.onSaveInstanceState(state);
    }
    private void append(String value) {
        if(result && (Character.isDigit(value.charAt(0)) || value.equals(".") || value.equals("("))) expression="";
        result=false; message="";
        if(expression.length()+value.length()<=120) expression+=value;
        else message="Maximum 120 caractere";
        face.invalidate();
    }
    private void calculate() {
        try { expression=CalculatorEngine.evaluate(expression); result=true; message="Rezultat"; }
        catch(ArithmeticException zero) { message="Împărțire la zero"; }
        catch(IllegalArgumentException invalid) { message="Expresie invalidă / prea lungă"; }
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
                new KeypadDialog(this,"Calculator",new String[]{"Șterge tot", "Schimbă semnul", "Procent (%)", "Paranteză (", "Paranteză )"},new KeypadDialog.Action() {
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
        protected void onDraw(Canvas c) {
            c.save(); c.scale(getWidth()/240f,getHeight()/302f); c.drawColor(palette.background);
            text(c,"Calculator",120,28,18,Color.WHITE,Paint.Align.CENTER);
            p.setColor(palette.card);c.drawRoundRect(8,48,232,143,12,12,p);
            p.setColor(palette.accent);c.drawRoundRect(8,48,12,143,2,2,p);
            String shown=expression.isEmpty()?"0":expression.replace('*','×').replace('/','÷');
            p.setTextSize(24);
            if(p.measureText(shown)>216) {
                while(shown.length()>1 && p.measureText("…"+shown)>216) shown=shown.substring(1);
                shown="…"+shown;
            }
            text(c,shown,228,99,24,Color.WHITE,Paint.Align.RIGHT);
            text(c,message,120,131,12,palette.accent,Paint.Align.CENTER);
            String[] keys={"↑  +","↓  −","←  ×","→  ÷"};
            for(int i=0;i<4;i++){float x=8+i*57;p.setColor(palette.card);c.drawRoundRect(x,158,x+53,198,8,8,p);text(c,keys[i],x+26,184,15,palette.accent,Paint.Align.CENTER);}
            text(c,"0–9 cifre · * zecimal · # șterge tot",120,220,12,0xffd3bddf,Paint.Align.CENTER);
            text(c,"Tasta Înapoi: ieșire",120,249,12,0xffd3bddf,Paint.Align.CENTER);
            p.setColor(palette.bar); c.drawRect(0,276,240,302,p);
            text(c,"Opțiuni",7,293,11,Color.WHITE,Paint.Align.LEFT);
            text(c,"=",120,293,16,palette.accent,Paint.Align.CENTER);
            text(c,"Șterge",233,293,11,Color.WHITE,Paint.Align.RIGHT); c.restore();
        }
    }
}
