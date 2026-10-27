package ro.leo.visual;
import ro.leo.i18n.L;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

/** Launchable Android activities only; opening this screen never launches an app. */
public final class AndroidAppsActivity extends Activity {
    private Appearance.Palette palette;
    private final ArrayList<Entry> apps=new ArrayList<Entry>();
    private AppList screen;
    private int selected;
    private String error="";private boolean showHidden,favoritesOnly;
    private java.util.Set<String> stored(String key){return getSharedPreferences("android-apps",0).getStringSet(key,java.util.Collections.<String>emptySet());}
    private static final class Entry {
        ComponentName component;
        String label;
        Drawable icon;
    }
    public void onCreate(Bundle state) {
        super.onCreate(state);palette=Appearance.palette(this);
        if(state!=null) selected=state.getInt("selected");
        getWindow().setStatusBarColor(palette.bar);
        screen=new AppList(); setContentView(screen);
    }
    protected void onResume() {
        super.onResume();
        palette=Appearance.palette(this);
        getWindow().setStatusBarColor(palette.bar);
        reload();
    }
    private void reload(){
        ComponentName previous=apps.isEmpty()?null:apps.get(Math.max(0,Math.min(selected,apps.size()-1))).component;
        apps.clear();
        HashSet<String> homePackages=new HashSet<String>();
        for(ResolveInfo home:getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)) {
            homePackages.add(home.activityInfo.packageName);
        }
        HashSet<ComponentName> seen=new HashSet<ComponentName>();
        for(ResolveInfo info:getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)) {
            if(!info.activityInfo.exported || !info.activityInfo.enabled
                    || getPackageName().equals(info.activityInfo.packageName)
                    || homePackages.contains(info.activityInfo.packageName)) continue;
            ComponentName component=new ComponentName(info.activityInfo.packageName,info.activityInfo.name);
            if(!seen.add(component)||(!showHidden&&stored("hidden").contains(component.flattenToString()))||(favoritesOnly&&!stored("favorites").contains(component.flattenToString()))) continue;
            Entry item=new Entry(); item.component=component;
            item.label=info.loadLabel(getPackageManager()).toString();
            item.icon=info.loadIcon(getPackageManager()); apps.add(item);
        }
        final Collator collator=Collator.getInstance(L.locale());
        Collections.sort(apps,new Comparator<Entry>() {
            public int compare(Entry a,Entry b) {
                boolean af=stored("favorites").contains(a.component.flattenToString()),bf=stored("favorites").contains(b.component.flattenToString());if(af!=bf)return af?-1:1;int result=collator.compare(a.label,b.label);
                return result!=0?result:a.component.flattenToString().compareTo(b.component.flattenToString());
            }
        });
        selected=Math.max(0,Math.min(selected,apps.size()-1));
        if(previous!=null) for(int i=0;i<apps.size();i++) if(previous.equals(apps.get(i).component)) selected=i;
        screen.invalidate();
    }
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("selected",selected); super.onSaveInstanceState(state);
    }
    private void menu(){final Entry item=apps.isEmpty()?null:apps.get(selected);new KeypadDialog(this,L.t("Aplicații Android"),new String[]{L.t("Adaugă / elimină favoritul"),L.t("Ascunde / arată selecția"),showHidden?L.t("Ascunde intrările ascunse"):L.t("Arată și intrările ascunse"),favoritesOnly?L.t("Toate aplicațiile"):L.t("Doar favorite"),L.t("Informații aplicație"),L.t("Aplicații implicite Android")},new KeypadDialog.Action(){public void select(int i){if(i==0||i==1){if(item==null)return;String key=i==0?"favorites":"hidden",id=item.component.flattenToString();java.util.Set<String> ids=new HashSet<String>(stored(key));if(!ids.remove(id))ids.add(id);getSharedPreferences("android-apps",0).edit().putStringSet(key,ids).apply();}else if(i==2)showHidden=!showHidden;else if(i==3)favoritesOnly=!favoritesOnly;else if(i==4){if(item!=null)try{startActivity(new Intent(AndroidAppsActivity.this,AppControlActivity.class).putExtra("package",item.component.getPackageName()));}catch(RuntimeException e){error=L.t("Pagina Android nu este disponibilă");}return;}else{try{startActivity(new Intent(AndroidAppsActivity.this,AppControlActivity.class));}catch(RuntimeException e){error=L.t("Configurarea implicitelor nu este disponibilă");}return;}onResumeList();}}).show();}
    private void onResumeList(){reload();}
    public boolean onKeyDown(int key,KeyEvent event) {
        if(LeoActivity.isJackNavigationNoise(event))return true;
        if(key==KeyEvent.KEYCODE_MENU){if(event.getRepeatCount()==0)menu();return true;}
        if(key==KeyEvent.KEYCODE_BACK) { if(event.getRepeatCount()==0) finish(); return true; }
        if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) {
            if(!apps.isEmpty())selected=CircularNavigation.step(selected,key==KeyEvent.KEYCODE_DPAD_UP?-1:1,apps.size());
            error=""; screen.invalidate(); return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
            if(event.getRepeatCount()==0 && !apps.isEmpty()) {
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(apps.get(selected).component));
                } catch(RuntimeException unavailable) { error=L.t("Nu se poate deschide"); screen.invalidate(); }
            }
            return true;
        }
        return super.onKeyDown(key,event);
    }
    private final class AppList extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        AppList() { super(AndroidAppsActivity.this); setFocusable(true); requestFocus(); }
        void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align) {
            paint.setTextSize(size); paint.setColor(color); paint.setTextAlign(align);
            c.drawText(value,x,y,paint);
        }
        String fit(String value,int width) {
            paint.setTextSize(14);
            if(paint.measureText(value)<=width) return value;
            while(value.length()>0 && paint.measureText(value+"…")>width) value=value.substring(0,value.length()-1);
            return value+"…";
        }
        protected void onDraw(Canvas c) {
            c.save(); c.scale(getWidth()/240f,getHeight()/302f); c.drawColor(palette.background);
            text(c,L.t("Aplicații Android"),120,27,18,Color.WHITE,Paint.Align.CENTER);
            int first=Math.max(0,Math.min(Math.max(0,apps.size()-5),selected-2));
            for(int row=0;row<5 && first+row<apps.size();row++) {
                int index=first+row; float top=43+row*42;
                if(index==selected) { paint.setColor(palette.selection); c.drawRoundRect(new RectF(6,top,234,top+38),7,7,paint); }
                Entry item=apps.get(index);
                c.save(); c.translate(13,top+5); item.icon.setBounds(0,0,28,28); item.icon.draw(c); c.restore();
                text(c,fit((stored("favorites").contains(item.component.flattenToString())?"★ ":"")+(stored("hidden").contains(item.component.flattenToString())?"◌ ":"")+item.label,178),49,top+25,14,index==selected?palette.ink:Color.WHITE,Paint.Align.LEFT);
            }
            if(apps.isEmpty()) text(c,L.t("Nicio aplicație disponibilă"),120,140,14,Color.WHITE,Paint.Align.CENTER);
            text(c,error.isEmpty()?(apps.isEmpty()?"0 / 0":(selected+1)+" / "+apps.size()):error,
                120,268,11,0xffd3bddf,Paint.Align.CENTER);
            paint.setColor(palette.bar); c.drawRect(0,276,240,302,paint);
            text(c,apps.isEmpty()?"":L.t("Deschide"),120,293,12,palette.accent,Paint.Align.CENTER);
            text(c,L.t("Opțiuni"),8,293,11,Color.WHITE,Paint.Align.LEFT);
            text(c,L.t("Înapoi"),232,293,11,Color.WHITE,Paint.Align.RIGHT); c.restore();
        }
    }
}
