package ro.leo.visual;
import android.content.*;
import android.os.Bundle;
import java.util.*;

public final class WallpaperActivity extends LeoActivity {
    protected void onCreate(Bundle state) { super.onCreate(state); show(); }
    private void show() {
        ArrayList<String> values=new ArrayList<String>(Arrays.asList(Appearance.THEMES)); values.add("Imagine din Galerie"); values.add("Elimină imaginea de fundal");
        list("Fundaluri și teme",values,0,new Choice() { public void pick(int i) {
            if(i<Appearance.THEMES.length) { getSharedPreferences("appearance",0).edit().putInt("theme",i).putBoolean("warm",i==1).apply(); notice("Tema aplicată"); }
            else if(i==Appearance.THEMES.length) startActivity(new Intent(WallpaperActivity.this,GalleryActivity.class).putExtra("wallpaper",true));
            else options("Elimini fundalul?",new String[]{"Păstrează","Elimină"},new Choice() { public void pick(int yes) { if(yes==1) { if(Appearance.wallpaper(WallpaperActivity.this).exists()&&!Appearance.wallpaper(WallpaperActivity.this).delete()) notice("Nu poate fi eliminat"); else notice("Fundal eliminat"); } } });
        } },"",null);
    }
}
