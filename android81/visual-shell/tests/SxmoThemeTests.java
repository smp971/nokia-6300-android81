package ro.leo.visual;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import java.io.*;import java.util.*;
/** Isolated visual fixtures; optional explicit activation after successful checks. */
public final class SxmoThemeTests extends Instrumentation {
 private boolean activate;private int checks;
 public void onCreate(Bundle args){activate=args!=null&&"true".equals(args.getString("activate"));start();}
 private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 public void onStart(){final Context base=getTargetContext();final String token="sxmo-check-"+UUID.randomUUID();final File dir=new File(base.getCacheDir(),token);dir.mkdir();
 Context c=new ContextWrapper(base){public File getFilesDir(){return dir;}public SharedPreferences getSharedPreferences(String group,int mode){return base.getSharedPreferences(token+group,mode);}};
 Bundle result=new Bundle();try{
 check(Appearance.THEMES.length==Appearance.COLORS.length,"palette registry");check(ThemePacks.NAMES.length==ThemePacks.THEMES.length,"pack registry");
 c.getSharedPreferences("appearance",0).edit().putInt("theme",10).putBoolean("list",false).commit();StockWallpapers.apply(c,2);byte[] before=BackupEngine.bytes(Appearance.wallpaper(c),2097152);
 ThemeCheckpoint.apply(c,4,true);Appearance.Palette a=Appearance.palette(c);check(a.sxmo(),"Sxmo selected");check(a.selection==0xff426449&&a.ink==0xfff1f1df,"green selection and light text");check(c.getSharedPreferences("appearance",0).getBoolean("list",false),"list navigation enabled");
 Bitmap b=BitmapFactory.decodeFile(Appearance.wallpaper(c).getPath());check(b!=null&&b.getWidth()==240&&b.getHeight()==302,"wallpaper decoded");b.recycle();
 Bitmap icon=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(icon);for(int id:new int[]{0,1,2,3,6,7,23,9}){icon.eraseColor(0);new SxmoIcon(id,20).draw(canvas);boolean drawn=false;for(int y=0;y<20;y++)for(int x=0;x<20;x++)drawn|=Color.alpha(icon.getPixel(x,y))>0;check(drawn,"visible glyph "+id);}icon.recycle();
 ThemeCheckpoint.restore(c);check(c.getSharedPreferences("appearance",0).getInt("theme",-1)==10&&!c.getSharedPreferences("appearance",0).getBoolean("list",true),"appearance restored");check(Arrays.equals(before,BackupEngine.bytes(Appearance.wallpaper(c),2097152)),"wallpaper restored exactly");
 if(activate){ThemeCheckpoint.apply(base,4,true);Intent home=new Intent(base,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);base.startActivity(home);}
 result.putString("result","PASS "+checks+" isolated Sxmo checks");result.putBoolean("activated",activate);finish(Activity.RESULT_OK,result);
 }catch(Throwable error){result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}finally{for(String group:new String[]{"appearance","accessibility"})base.getSharedPreferences(token+group,0).edit().clear().commit();File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}}
}
