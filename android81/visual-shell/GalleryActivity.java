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
    private final ArrayList<Uri> allImages=new ArrayList<Uri>();private final ArrayList<String> allNames=new ArrayList<String>(),buckets=new ArrayList<String>(),bucketLabels=new ArrayList<String>();private String album="";
    private final ArrayList<Uri> images=new ArrayList<Uri>();
    private final ArrayList<String> names=new ArrayList<String>();
    private final HashMap<Integer,Bitmap> thumbs=new HashMap<Integer,Bitmap>();
    private int selected,generation,zoom=1; private float panX,panY;private boolean viewer,closed,slideshow;private int rotation;private final android.os.Handler slides=new android.os.Handler();
    private final Runnable advance=new Runnable(){public void run(){if(slideshow&&!closed&&!images.isEmpty()){selected=CircularNavigation.step(selected,1,images.size());open();}}}; private Bitmap photo; private Face face;
    protected void onCreate(Bundle state) { super.onCreate(state);if(getIntent().getData()!=null){images.add(getIntent().getData());names.add(getIntent().getStringExtra("title")==null?"Imagine":getIntent().getStringExtra("title"));grid();open();}else load(); }
    private void load() {
        begin("Galerie"); root.addView(text("Se încarcă…",18),new LinearLayout.LayoutParams(-1,0,1)); footer("","","Înapoi");
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Uri> found=new ArrayList<Uri>(); final ArrayList<String> labels=new ArrayList<String>(),ids=new ArrayList<String>(),folderNames=new ArrayList<String>();
            try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name","bucket_id","bucket_display_name"},null,null,"date_added DESC")) {
                while(c!=null&&c.moveToNext()&&found.size()<500) { found.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0))); labels.add(c.getString(1)==null?"Imagine":c.getString(1));ids.add(c.getString(2)==null?"other":c.getString(2));folderNames.add(c.getString(3)==null?"Altele":c.getString(3)); }
                ui(new Runnable() { public void run() { allImages.clear();allImages.addAll(found);allNames.clear();allNames.addAll(labels);buckets.clear();buckets.addAll(ids);bucketLabels.clear();bucketLabels.addAll(folderNames);albums(); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { notice("Galeria nu poate citi memoria"); grid(); } }); }
        }});
    }
    private void albums(){stopSlides();face=null;generation++;viewer=false;clearImages();final LinkedHashMap<String,String> groups=new LinkedHashMap<String,String>();groups.put("","Toate imaginile");for(int i=0;i<buckets.size();i++)groups.put(buckets.get(i),bucketLabels.get(i));final ArrayList<String> ids=new ArrayList<String>(groups.keySet());ArrayList<String> labels=new ArrayList<String>();for(String id:ids){int count=0;for(String b:buckets)if(id.isEmpty()||id.equals(b))count++;labels.add(groups.get(id)+"\n"+count+" imagini");}list("Albume foto",labels,0,new Choice(){public void pick(int at){album=ids.get(at);images.clear();names.clear();for(int i=0;i<allImages.size();i++)if(album.isEmpty()||album.equals(buckets.get(i))){images.add(allImages.get(i));names.add(allNames.get(i));}selected=0;grid();}},"Reîncarcă",new Runnable(){public void run(){load();}});}
    private void clearImages() { if(photo!=null) { photo.recycle(); photo=null; } for(Bitmap b:thumbs.values()) b.recycle(); thumbs.clear(); }
    private void grid() {
        stopSlides();viewer=false; zoom=1; generation++; clearImages(); begin("Galerie · "+images.size()); face=new Face(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() { if(images.isEmpty()) load(); else imageOptions(); } }; footer("Opțiuni","Deschide","Înapoi");
        face.setFocusable(true); face.requestFocus(); back=getIntent().getData()!=null?null:new Runnable(){public void run(){albums();}}; thumbnails();
    }
    private void thumbnails() {
        final int token=++generation,start=selected/6*6; for(Bitmap b:thumbs.values()) b.recycle(); thumbs.clear();
        worker.execute(new Runnable() { public void run() {
            final HashMap<Integer,Bitmap> loaded=new HashMap<Integer,Bitmap>();
            for(int i=start;i<Math.min(images.size(),start+6);i++) try { loaded.put(i,Appearance.decode(GalleryActivity.this,images.get(i),128)); } catch(Exception|OutOfMemoryError ignored) { }
            runOnUiThread(new Runnable() { public void run() { if(closed||token!=generation) { for(Bitmap b:loaded.values()) b.recycle(); return; } thumbs.putAll(loaded); face.invalidate(); } });
        }});
    }
    private void open() {
        if(getIntent().getBooleanExtra("contact_photo",false)&&!images.isEmpty()){setResult(RESULT_OK,new Intent().setData(images.get(selected)));finish();return;}
        if(images.isEmpty()) return; slides.removeCallbacks(advance);viewer=true; zoom=1;rotation=0;panX=panY=0; final int token=++generation; if(photo!=null) { photo.recycle(); photo=null; }
        begin((selected+1)+" / "+images.size()); face=new Face(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1)); left=new Runnable() { public void run() { imageOptions(); } };
        back=new Runnable() { public void run() { grid(); } }; footer("Opțiuni","Zoom","Grilă"); face.setFocusable(true); face.requestFocus();
        final Uri uri=images.get(selected);
        worker.execute(new Runnable() { public void run() { Bitmap result=null; try { result=Appearance.decode(GalleryActivity.this,uri,640); } catch(Exception|OutOfMemoryError ignored) { }
            final Bitmap loaded=result; runOnUiThread(new Runnable() { public void run() { if(closed||token!=generation) { if(loaded!=null) loaded.recycle(); return; } photo=loaded; face.invalidate();if(slideshow)slides.postDelayed(advance,getSharedPreferences("gallery",0).getInt("interval",5)*1000L); if(loaded==null) notice("Imaginea nu poate fi decodată"); } });
        }});
    }
    private void stopSlides(){slideshow=false;slides.removeCallbacks(advance);}
    private void share(){if(images.isEmpty())return;stopSlides();Uri uri=images.get(selected);Intent send=new Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("Imagine",uri));try{startActivity(Intent.createChooser(send,"Trimite imaginea · Bluetooth dacă este disponibil"));}catch(RuntimeException e){notice("Nicio aplicație pentru partajare");}}
    private void imageOptions() {
        stopSlides();
        options("Imagine",new String[]{"Deschide","Fundal Leo","Reîncarcă galeria","Informații imagine","Imaginea anterioară","Imaginea următoare","Slideshow","Interval slideshow","Rotește 90°","Partajează imaginea"},new Choice() { public void pick(int i) {
            if(i==6){if(images.isEmpty())return;slideshow=true;open();}
            else if(i==7)options("Interval slideshow",new String[]{"3 secunde","5 secunde","10 secunde"},new Choice(){public void pick(int n){getSharedPreferences("gallery",0).edit().putInt("interval",new int[]{3,5,10}[n]).apply();}});
            else if(i==8){if(!viewer)open();rotation=(rotation+90)%360;if(face!=null)face.invalidate();}
            else if(i==9)share();
            else if(i>=4){selected=CircularNavigation.step(selected,i==4?-1:1,images.size());open();}else if(i==3){info();}else if(i==0) open(); else if(i==2) load(); else options("Aplici ca fundal Leo?",new String[]{"Anulează","Aplică"},new Choice() { public void pick(int yes) {
                if(yes==1) { final Uri uri=images.get(selected); worker.execute(new Runnable() { public void run() { try { Appearance.saveWallpaper(GalleryActivity.this,uri); ui(new Runnable() { public void run() { notice("Fundal Leo salvat"); } }); } catch(Exception|OutOfMemoryError error) { ui(new Runnable() { public void run() { notice("Fundalul nu poate fi salvat"); } }); } } }); }
            }});
        }});
    }
    private void info(){final Uri uri=images.get(selected);final String title=names.get(selected);worker.execute(new Runnable(){public void run(){final ArrayList<String> rows=new ArrayList<String>();rows.add(title);try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth>0)rows.add(o.outWidth+" × "+o.outHeight+" px · original");try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int size=c.getColumnIndex("_size");if(size>=0)rows.add(android.text.format.Formatter.formatFileSize(GalleryActivity.this,c.getLong(size)));int date=c.getColumnIndex("datetaken");if(date>=0&&c.getLong(date)>0)rows.add(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(c.getLong(date))));}}}catch(Exception ignored){rows.add("Unele informații nu sunt disponibile");}ui(new Runnable(){public void run(){options("Informații imagine",rows.toArray(new String[0]),new Choice(){public void pick(int n){}});}});}});}
    public boolean onKeyDown(int key,KeyEvent event) {
        if(face!=null && !images.isEmpty()) {
            if(slideshow&&event.getRepeatCount()==0){stopSlides();if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){notice("Slideshow oprit");return true;}}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) { if(event.getRepeatCount()==0) { if(!viewer) { if(getIntent().getBooleanExtra("wallpaper",false)) imageOptions(); else open(); } else { zoom=zoom==4?1:zoom*2;panX=panY=0; face.invalidate(); } } return true; }
            if(viewer&&(key==KeyEvent.KEYCODE_1||key==KeyEvent.KEYCODE_3)){if(event.getRepeatCount()==0){selected=CircularNavigation.step(selected,key==KeyEvent.KEYCODE_1?-1:1,images.size());open();}return true;}
            if(viewer&&zoom>1&&photo!=null&&(key==19||key==20||key==21||key==22)){float pw=rotation%180==0?photo.getWidth():photo.getHeight(),ph=rotation%180==0?photo.getHeight():photo.getWidth();float scale=Math.min(face.getWidth()/pw,face.getHeight()/ph)*zoom;float maxX=Math.max(0,(pw*scale-face.getWidth())/2),maxY=Math.max(0,(ph*scale-face.getHeight())/2);panX=Math.max(-maxX,Math.min(maxX,panX+(key==21?24:key==22?-24:0)));panY=Math.max(-maxY,Math.min(maxY,panY+(key==19?24:key==20?-24:0)));face.invalidate();return true;}
            int step=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:key==KeyEvent.KEYCODE_DPAD_UP?(viewer?-1:-2):key==KeyEvent.KEYCODE_DPAD_DOWN?(viewer?1:2):0;
            if(step!=0) { int old=selected/6; selected=viewer?CircularNavigation.step(selected,step,images.size()):selected==0&&step<0?images.size()-1:selected==images.size()-1&&step>0?0:Math.max(0,Math.min(images.size()-1,selected+step)); if(viewer) open(); else { if(old!=selected/6) thumbnails(); face.invalidate(); } return true; }
        }
        return super.onKeyDown(key,event);
    }
    protected void onPause(){stopSlides();super.onPause();}
    protected void onDestroy() {stopSlides(); closed=true; generation++; clearImages(); super.onDestroy(); }
    private final class Face extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Face() { super(GalleryActivity.this); }
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(palette.background);if(viewer) { if(photo!=null) { float pw=rotation%180==0?photo.getWidth():photo.getHeight(),ph=rotation%180==0?photo.getHeight():photo.getWidth();float scale=Math.min(getWidth()/pw,getHeight()/ph)*zoom,w=photo.getWidth()*scale,h=photo.getHeight()*scale;canvas.save();canvas.translate(getWidth()/2f+panX,getHeight()/2f+panY);canvas.rotate(rotation);canvas.drawBitmap(photo,null,new RectF(-w/2,-h/2,w/2,h/2),paint);canvas.restore();paint.setColor(0xcc101020);canvas.drawRect(0,getHeight()-dp(22),getWidth(),getHeight(),paint);paint.setColor(Color.WHITE);paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(zoom+"× · "+(zoom>1?"↑ ↓ ← → deplasare":"1 / 3 schimbă imaginea"),getWidth()/2,getHeight()-dp(6),paint); } return; }
            canvas.save(); canvas.scale(getWidth()/240f,getHeight()/226f); paint.setTextSize(13); paint.setTextAlign(Paint.Align.CENTER);
            if(images.isEmpty()) { paint.setColor(Color.WHITE); canvas.drawText("Nicio imagine în MediaStore",120,100,paint); }
            for(int i=selected/6*6;i<Math.min(images.size(),selected/6*6+6);i++) { int cell=i%6;float x=5+cell%2*116,y=5+cell/2*74;
                paint.setColor(i==selected?palette.selection:palette.card);canvas.drawRoundRect(new RectF(x,y,x+110,y+70),5,5,paint); Bitmap b=thumbs.get(i);
                if(b!=null){canvas.save();canvas.clipRect(x+3,y+3,x+107,y+51);float scale=Math.max(104f/b.getWidth(),48f/b.getHeight()),w=b.getWidth()*scale,h=b.getHeight()*scale;canvas.drawBitmap(b,null,new RectF(x+55-w/2,y+27-h/2,x+55+w/2,y+27+h/2),paint);canvas.restore();} paint.setColor(i==selected?palette.ink:Color.WHITE);String name=names.get(i);if(name==null)name="Imagine";while(name.length()>1&&paint.measureText(name)>100)name=name.substring(0,name.length()-1);canvas.drawText(name,x+55,y+65,paint);
            } canvas.restore();
        }
    }
}
