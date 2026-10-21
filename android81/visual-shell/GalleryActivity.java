package ro.leo.visual;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class GalleryActivity extends LeoActivity {
    private final ArrayList<Uri> images=new ArrayList<Uri>();
    private final ArrayList<String> names=new ArrayList<String>();
    private final HashMap<Integer,Bitmap> thumbs=new HashMap<Integer,Bitmap>();
    private int selected,generation,zoom=1; private float panX,panY;private boolean viewer,closed; private Bitmap photo; private Face face;
    protected void onCreate(Bundle state) { super.onCreate(state);if(getIntent().getData()!=null){images.add(getIntent().getData());names.add(getIntent().getStringExtra("title")==null?"Imagine":getIntent().getStringExtra("title"));grid();open();}else load(); }
    private void load() {
        begin("Galerie"); root.addView(text("Se încarcă…",18),new LinearLayout.LayoutParams(-1,0,1)); footer("","","Înapoi");
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Uri> found=new ArrayList<Uri>(); final ArrayList<String> labels=new ArrayList<String>();
            try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name"},null,null,"date_added DESC")) {
                while(c!=null&&c.moveToNext()&&found.size()<500) { found.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0))); labels.add(c.getString(1)); }
                ui(new Runnable() { public void run() { images.clear(); images.addAll(found); names.clear(); names.addAll(labels); grid(); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { notice("Galeria nu poate citi memoria"); grid(); } }); }
        }});
    }
    private void clearImages() { if(photo!=null) { photo.recycle(); photo=null; } for(Bitmap b:thumbs.values()) b.recycle(); thumbs.clear(); }
    private void grid() {
        viewer=false; zoom=1; generation++; clearImages(); begin("Galerie · "+images.size()); face=new Face(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() { if(images.isEmpty()) load(); else imageOptions(); } }; footer("Opțiuni","Deschide","Înapoi");
        face.setFocusable(true); face.requestFocus(); back=null; thumbnails();
    }
    private void thumbnails() {
        final int token=++generation,start=selected/9*9; for(Bitmap b:thumbs.values()) b.recycle(); thumbs.clear();
        worker.execute(new Runnable() { public void run() {
            final HashMap<Integer,Bitmap> loaded=new HashMap<Integer,Bitmap>();
            for(int i=start;i<Math.min(images.size(),start+9);i++) try { loaded.put(i,Appearance.decode(GalleryActivity.this,images.get(i),96)); } catch(Exception|OutOfMemoryError ignored) { }
            runOnUiThread(new Runnable() { public void run() { if(closed||token!=generation) { for(Bitmap b:loaded.values()) b.recycle(); return; } thumbs.putAll(loaded); face.invalidate(); } });
        }});
    }
    private void open() {
        if(getIntent().getBooleanExtra("contact_photo",false)&&!images.isEmpty()){setResult(RESULT_OK,new Intent().setData(images.get(selected)));finish();return;}
        if(images.isEmpty()) return; viewer=true; zoom=1;panX=panY=0; final int token=++generation; if(photo!=null) { photo.recycle(); photo=null; }
        begin((selected+1)+" / "+images.size()); face=new Face(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1)); left=new Runnable() { public void run() { imageOptions(); } };
        back=new Runnable() { public void run() { grid(); } }; footer("Opțiuni","Zoom","Grilă"); face.setFocusable(true); face.requestFocus();
        final Uri uri=images.get(selected);
        worker.execute(new Runnable() { public void run() { Bitmap result=null; try { result=Appearance.decode(GalleryActivity.this,uri,640); } catch(Exception|OutOfMemoryError ignored) { }
            final Bitmap loaded=result; runOnUiThread(new Runnable() { public void run() { if(closed||token!=generation) { if(loaded!=null) loaded.recycle(); return; } photo=loaded; face.invalidate(); if(loaded==null) notice("Imaginea nu poate fi decodată"); } });
        }});
    }
    private void imageOptions() {
        options("Imagine",new String[]{"Deschide","Fundal Leo","Reîncarcă galeria","Informații imagine","Imaginea anterioară","Imaginea următoare"},new Choice() { public void pick(int i) {
            if(i>=4){selected=Math.max(0,Math.min(images.size()-1,selected+(i==4?-1:1)));open();}else if(i==3){options("Imagine",new String[]{names.get(selected),photo==null?"Dimensiuni la deschidere":photo.getWidth()+" × "+photo.getHeight()+" px · previzualizare","OK: zoom 1× / 2× / 4×","La zoom: săgețile deplasează","1 / 3: imaginea anterioară / următoare"},new Choice(){public void pick(int n){}});}else if(i==0) open(); else if(i==2) load(); else options("Aplici ca fundal Leo?",new String[]{"Anulează","Aplică"},new Choice() { public void pick(int yes) {
                if(yes==1) { final Uri uri=images.get(selected); worker.execute(new Runnable() { public void run() { try { Appearance.saveWallpaper(GalleryActivity.this,uri); ui(new Runnable() { public void run() { notice("Fundal Leo salvat"); } }); } catch(Exception|OutOfMemoryError error) { ui(new Runnable() { public void run() { notice("Fundalul nu poate fi salvat"); } }); } } }); }
            }});
        }});
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(face!=null && !images.isEmpty()) {
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) { if(event.getRepeatCount()==0) { if(!viewer) { if(getIntent().getBooleanExtra("wallpaper",false)) imageOptions(); else open(); } else { zoom=zoom==4?1:zoom*2;panX=panY=0; face.invalidate(); } } return true; }
            if(viewer&&(key==KeyEvent.KEYCODE_1||key==KeyEvent.KEYCODE_3)){if(event.getRepeatCount()==0){selected=Math.max(0,Math.min(images.size()-1,selected+(key==KeyEvent.KEYCODE_1?-1:1)));open();}return true;}
            if(viewer&&zoom>1&&photo!=null&&(key==19||key==20||key==21||key==22)){float scale=Math.min(face.getWidth()/(float)photo.getWidth(),face.getHeight()/(float)photo.getHeight())*zoom;float maxX=Math.max(0,(photo.getWidth()*scale-face.getWidth())/2),maxY=Math.max(0,(photo.getHeight()*scale-face.getHeight())/2);panX=Math.max(-maxX,Math.min(maxX,panX+(key==21?24:key==22?-24:0)));panY=Math.max(-maxY,Math.min(maxY,panY+(key==19?24:key==20?-24:0)));face.invalidate();return true;}
            int step=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:key==KeyEvent.KEYCODE_DPAD_UP?(viewer?-1:-3):key==KeyEvent.KEYCODE_DPAD_DOWN?(viewer?1:3):0;
            if(step!=0) { int old=selected/9; selected=Math.max(0,Math.min(images.size()-1,selected+step)); if(viewer) open(); else { if(old!=selected/9) thumbnails(); face.invalidate(); } return true; }
        }
        return super.onKeyDown(key,event);
    }
    protected void onDestroy() { closed=true; generation++; clearImages(); super.onDestroy(); }
    private final class Face extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Face() { super(GalleryActivity.this); }
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(palette.background);if(viewer) { if(photo!=null) { float scale=Math.min(getWidth()/(float)photo.getWidth(),getHeight()/(float)photo.getHeight())*zoom,w=photo.getWidth()*scale,h=photo.getHeight()*scale; canvas.drawBitmap(photo,null,new RectF((getWidth()-w)/2+panX,(getHeight()-h)/2+panY,(getWidth()+w)/2+panX,(getHeight()+h)/2+panY),paint);paint.setColor(0xcc101020);canvas.drawRect(0,getHeight()-dp(22),getWidth(),getHeight(),paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(zoom+"× · "+(zoom>1?"↑ ↓ ← → deplasare":"1 / 3 schimbă imaginea"),getWidth()/2,getHeight()-dp(6),paint); } return; }
            canvas.save(); canvas.scale(getWidth()/240f,getHeight()/226f); paint.setTextSize(13); paint.setTextAlign(Paint.Align.CENTER);
            if(images.isEmpty()) { paint.setColor(Color.WHITE); canvas.drawText("Nicio imagine în MediaStore",120,100,paint); }
            for(int i=selected/9*9;i<Math.min(images.size(),selected/9*9+9);i++) { int cell=i%9;float x=5+cell%3*79,y=5+cell/3*70;
                paint.setColor(i==selected?0xffe8d3ff:0xff40334e);canvas.drawRoundRect(new RectF(x,y,x+73,y+66),5,5,paint); Bitmap b=thumbs.get(i);
                if(b!=null) canvas.drawBitmap(b,null,new RectF(x+3,y+3,x+70,y+48),paint); paint.setColor(i==selected?0xff21172e:Color.WHITE);canvas.drawText(""+(i+1),x+36,y+61,paint);
            } canvas.restore();
        }
    }
}
