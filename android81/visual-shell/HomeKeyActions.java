package ro.leo.visual;
import android.content.Context;
final class HomeKeyActions {
    static boolean configurable(int digit){return digit>=2&&digit<=9;}
    static int app(Context c,int digit){if(!configurable(digit))return -1;int id=c.getSharedPreferences("home-long-keys",0).getInt("app"+digit,-1);return id>=0&&id<AppCatalog.NAMES.length?id:-1;}
    static void save(Context c,int digit,int app){if(!configurable(digit)||app< -1||app>=AppCatalog.NAMES.length)throw new IllegalArgumentException();c.getSharedPreferences("home-long-keys",0).edit().putInt("app"+digit,app).apply();}
}
