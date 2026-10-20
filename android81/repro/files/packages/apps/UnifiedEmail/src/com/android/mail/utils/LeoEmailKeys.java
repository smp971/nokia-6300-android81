package com.android.mail.utils;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;

/** Keypad access to existing AOSP validation/sending; never handles credentials. */
public final class LeoEmailKeys {
    private static int id(Activity a,String name){return a.getResources().getIdentifier(name,"id",a.getPackageName());}
    public static boolean dispatch(final Activity a,KeyEvent event,final boolean compose){
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_MENU){
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
                String[] choices=compose?new String[]{"Trimite","Salvează ciorna","Renunță la mesaj","Înapoi"}:new String[]{"Continuă","Configurare manuală","Înapoi"};
                new AlertDialog.Builder(a).setTitle(compose?"Email · acțiuni":"Configurare · acțiuni").setItems(choices,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                    if(compose){if(which==3)return;final String action=which==0?"send":which==1?"save":"discard";if(which==0)new AlertDialog.Builder(a).setMessage("Trimiți acest email?").setNegativeButton("Înapoi",null).setPositiveButton("Trimite",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){menu(a,action);}}).show();else menu(a,action);}
                    else if(which==2)a.onBackPressed();else{View target=a.findViewById(id(a,which==0?"next":"manual_setup"));if(target!=null&&target.isShown()&&target.isEnabled())target.performClick();else Toast.makeText(a,"Completează câmpurile necesare",Toast.LENGTH_SHORT).show();}
                }}).show();
            }return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){
            View focused=a.getCurrentFocus();if(focused instanceof EditText&&((((EditText)focused).getInputType()&android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)==0)){View next=focused.focusSearch(key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN);if(next!=null&&next!=focused){next.requestFocus();return true;}}
        }
        return false;
    }
    private static void menu(Activity a,String name){int item=id(a,name);if(item==0){Toast.makeText(a,"Acțiune indisponibilă",Toast.LENGTH_SHORT).show();return;}android.view.Menu menu=new PopupMenu(a,a.getWindow().getDecorView()).getMenu();if(!a.onOptionsItemSelected(menu.add(0,item,0,name)))Toast.makeText(a,"Acțiune indisponibilă",Toast.LENGTH_SHORT).show();}
}
