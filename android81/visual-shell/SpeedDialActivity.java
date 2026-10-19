package ro.leo.visual;
import android.content.*;
import android.os.Bundle;
import java.util.*;
public final class SpeedDialActivity extends LeoActivity {
    protected void onCreate(Bundle b){super.onCreate(b);}
    protected void onResume(){super.onResume();show();}
    private void show(){final SharedPreferences p=getSharedPreferences("speed-dial",0);ArrayList<String> labels=new ArrayList<String>();for(int n=2;n<=9;n++)labels.add(n+" · "+p.getString("name"+n,"Neatribuit"));list("Apelare rapidă",labels,0,new Choice(){public void pick(int i){final int slot=i+2;options("Tasta "+slot,new String[]{"Alege contact","Elimină atribuirea"},new Choice(){public void pick(int action){if(action==0)startActivity(new Intent(SpeedDialActivity.this,ContactsActivity.class).putExtra("speed_slot",slot));else{p.edit().remove("name"+slot).remove("number"+slot).apply();show();}}});}},"Ajutor",new Runnable(){public void run(){notice("Ține apăsat 2–9 pe Home. Se deschide numărul; confirmi apoi apelul.");}});}
}
