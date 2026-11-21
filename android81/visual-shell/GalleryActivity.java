package ro.leo.visual;
import ro.leo.i18n.L;
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
    private final android.util.LruCache<String,Bitmap> thumbnailCache=new android.util.LruCache<String,Bitmap>(2097152){protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}};
    private final HashMap<Integer,Bitmap> thumbs=new HashMap<Integer,Bitmap>();
    private final LinkedHashSet<String> marked=new LinkedHashSet<String>();
    private Set<String> favorites(){return new HashSet<String>(getSharedPreferences("gallery",0).getStringSet("favorites",Collections.<String>emptySet()));}
    private static final int PAGE_SIZE=4;
    private TextView caption;private boolean loadingPhoto,restoreViewer,restoreGrid;private String restoreUri;
    private int selected,zoom=1;private volatile int generation; private float panX,panY;private boolean viewer,closed,slideshow;private int rotation;private final android.os.Handler slides=new android.os.Handler();
    private final Runnable advance=new Runnable(){public void run(){if(slideshow&&!closed&&!images.isEmpty()){selected=CircularNavigation.step(selected,1,images.size());open();}}}; private Bitmap photo; private Face face;
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null){album=state.getString("album","");selected=state.getInt("selected",0);restoreViewer=state.getBoolean("viewer");restoreGrid=state.getBoolean("grid");restoreUri=state.getString("selected_uri");}
        if(getIntent().getData()!=null){images.add(getIntent().getData());names.add(getIntent().getStringExtra("title")==null?L.t("Imagine"):getIntent().getStringExtra("title"));selected=0;grid();open();}else load();
    }
    protected void onSaveInstanceState(Bundle state){state.putString("album",album);state.putInt("selected",selected);state.putBoolean("viewer",viewer);state.putBoolean("grid",face!=null);if(!images.isEmpty())state.putString("selected_uri",images.get(selected).toString());super.onSaveInstanceState(state);}
    private void load() {stopSlides();face=null;final int request=++generation;thumbnailCache.evictAll();
        loading(L.t("Galerie"));
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Uri> found=new ArrayList<Uri>(); final ArrayList<String> labels=new ArrayList<String>(),ids=new ArrayList<String>(),folderNames=new ArrayList<String>();
            try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name","bucket_id","bucket_display_name"},null,null,"date_added DESC")) {
                while(c!=null&&c.moveToNext()&&found.size()<500) { found.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0))); labels.add(c.getString(1)==null?L.t("Imagine"):c.getString(1));ids.add(c.getString(2)==null?"other":c.getString(2));folderNames.add(c.getString(3)==null?L.t("Altele"):c.getString(3)); }
                ui(new Runnable() { public void run() { if(closed||request!=generation)return;allImages.clear();allImages.addAll(found);allNames.clear();allNames.addAll(labels);buckets.clear();buckets.addAll(ids);bucketLabels.clear();bucketLabels.addAll(folderNames);if(restoreGrid){restoreGrid=false;selectAlbum(album,true);if(restoreViewer)open();restoreViewer=false;}else albums(); } });
            } catch(RuntimeException denied) { ui(new Runnable() { public void run() { if(closed||request!=generation)return;errorPage(L.t("Galerie"),L.t("Galeria nu poate citi memoria"),new Runnable(){public void run(){load();}}); } }); }
        }});
    }
    private void albums(){
        stopSlides();face=null;generation++;viewer=false;clearImages();
        final LinkedHashMap<String,String> groups=new LinkedHashMap<String,String>();
        final HashMap<String,Integer> counts=new HashMap<String,Integer>();
        Set<String> favoriteIds=favorites();int favoriteCount=0;
        groups.put("",L.t("Toate imaginile"));groups.put("@favorites",L.t("★ Favorite"));
        for(int i=0;i<buckets.size();i++){String id=buckets.get(i);groups.put(id,bucketLabels.get(i));Integer count=counts.get(id);counts.put(id,count==null?1:count+1);if(favoriteIds.contains(allImages.get(i).toString()))favoriteCount++;}
        counts.put("",allImages.size());counts.put("@favorites",favoriteCount);
        final ArrayList<String> ids=new ArrayList<String>(groups.keySet());ArrayList<String> labels=new ArrayList<String>();
        for(String id:ids)labels.add(groups.get(id)+"\n"+counts.get(id)+L.t(" imagini"));
        list(L.t("Albume foto"),labels,0,new Choice(){public void pick(int at){selectAlbum(ids.get(at),false);}},L.t("Reîncarcă"),new Runnable(){public void run(){load();}});
    }
    private void selectAlbum(String id,boolean restore){
        album=id;images.clear();names.clear();Set<String> favoriteIds=favorites();
        for(int i=0;i<allImages.size();i++)if(album.isEmpty()||album.equals(buckets.get(i))||album.equals("@favorites")&&favoriteIds.contains(allImages.get(i).toString())){images.add(allImages.get(i));names.add(allNames.get(i));}
        selected=restore?Math.max(0,Math.min(selected,images.size()-1)):0;
        if(restore&&restoreUri!=null)for(int i=0;i<images.size();i++)if(restoreUri.equals(images.get(i).toString()))selected=i;
        grid();
    }
    private void caption(){headingTitle(L.t(viewer?"Imagine":"Galerie")+" · "+(images.isEmpty()?"0":(selected+1)+" / "+images.size()));if(caption!=null)caption.setText(images.isEmpty()?L.t("Nicio imagine în acest album"):names.get(selected));}
    private void clearImages() {photo=null;thumbs.clear();}
    private void grid() {
        stopSlides();viewer=false; zoom=1; generation++; clearImages(); begin(L.t("Galerie")+" · "+(images.isEmpty()?"0":(selected+1)+" / "+images.size()));caption=subtitle("");caption(); face=new Face(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable() { public void run() { if(images.isEmpty()) load(); else imageOptions(); } }; footer(L.t("Opțiuni"),L.t("Deschide"),L.t("Înapoi"));
        face.setFocusableInTouchMode(true); face.requestFocus(); back=getIntent().getData()!=null?null:new Runnable(){public void run(){albums();}}; thumbnails();
    }
    private void thumbnails() {
        final int token=++generation,start=selected/PAGE_SIZE*PAGE_SIZE; final ArrayList<Uri> snapshot=new ArrayList<Uri>(images); thumbs.clear();
        worker.execute(new Runnable() { public void run() {
            final HashMap<Integer,Bitmap> loaded=new HashMap<Integer,Bitmap>();
            for(int i=start;i<Math.min(snapshot.size(),start+PAGE_SIZE)&&token==generation;i++) try { Bitmap image=thumbnailCache.get(snapshot.get(i).toString());if(image==null)image=Appearance.decode(GalleryActivity.this,snapshot.get(i),128);if(image!=null)loaded.put(i,image); } catch(Exception|OutOfMemoryError ignored) { }
            runOnUiThread(new Runnable() { public void run() { if(closed||token!=generation)return;for(Map.Entry<Integer,Bitmap> entry:loaded.entrySet())thumbnailCache.put(snapshot.get(entry.getKey()).toString(),entry.getValue());thumbs.putAll(loaded); face.invalidate(); } });
        }});
    }
    private void open() {
        if((getIntent().getBooleanExtra("contact_photo",false)||getIntent().getBooleanExtra("pick_image",false))&&!images.isEmpty()){setResult(RESULT_OK,new Intent().setData(images.get(selected)));finish();return;}
        if(images.isEmpty()) return; slides.removeCallbacks(advance);viewer=true;zoom=1;rotation=0;panX=panY=0;photo=null;
        begin(L.t("Imagine")+" · "+(selected+1)+" / "+images.size());caption=subtitle(names.get(selected)); face=new Face(); root.addView(face,new LinearLayout.LayoutParams(-1,0,1)); left=new Runnable() { public void run() { imageOptions(); } };
        back=new Runnable() { public void run() { if(getIntent().getBooleanExtra("quick_review",false))finish();else grid(); } }; footer(L.t("Opțiuni"),"Zoom",getIntent().getBooleanExtra("quick_review",false)?L.t("Înapoi"):L.t("Grilă")); face.setFocusableInTouchMode(true); face.requestFocus();
        decodePhoto();
    }
    private void decodePhoto(){
        loadingPhoto=true;final int token=++generation;final Uri uri=images.get(selected);
        worker.execute(new Runnable() { public void run() { Bitmap result=null; try { result=Appearance.decode(GalleryActivity.this,uri,640); } catch(Exception|OutOfMemoryError ignored) { }
            final Bitmap loaded=result; runOnUiThread(new Runnable() { public void run() { if(closed||token!=generation) { if(loaded!=null) loaded.recycle(); return; } photo=loaded;loadingPhoto=false; face.invalidate();if(slideshow)slides.postDelayed(advance,getSharedPreferences("gallery",0).getInt("interval",5)*1000L); if(loaded==null) notice(L.t("Imaginea nu poate fi decodată")); } });
        }});
    }
    private void stopSlides(){slideshow=false;slides.removeCallbacks(advance);}
    protected void onResume(){
        super.onResume();
        if(face!=null&&!images.isEmpty()){
            if(viewer&&photo==null&&!loadingPhoto)decodePhoto();
            else if(!viewer&&thumbs.isEmpty())thumbnails();
        }
    }
    void onAppearanceChanged(){if(face!=null){face.labels.appearance();face.invalidate();}}
    void onUiMemoryTrim(int level){
        thumbnailCache.evictAll();
        if(face!=null&&!uiActive){
            generation++;loadingPhoto=false;photo=null;thumbs.clear();stopSlides();
        }
    }
    private void share(){if(images.isEmpty())return;stopSlides();ShareHubActivity.send(this,images.get(selected),"image/*",names.get(selected));}

    private void imageOptions() {
        stopSlides();
        options(L.t("Imagine"),new String[]{L.t("Deschide"),L.t("Fundal"),L.t("Reîncarcă galeria"),L.t("Informații imagine"),L.t("Imaginea anterioară"),L.t("Imaginea următoare"),"Slideshow",L.t("Interval slideshow"),L.t("Rotește 90°"),L.t("Partajează imaginea"),L.t("★ Adaugă / elimină favorit"),L.t("Marchează / demarchează · 5"),L.t("Selecție multiplă")},new Choice() { public void pick(int i) {
            if(i==10){Set<String> ids=favorites();String uri=images.get(selected).toString();if(!ids.remove(uri))ids.add(uri);getSharedPreferences("gallery",0).edit().putStringSet("favorites",ids).apply();notice(L.t("Favorite actualizate"));if(face!=null)face.invalidate();}
            else if(i==11)mark();else if(i==12)selection();else if(i==6){if(images.isEmpty())return;slideshow=true;open();}
            else if(i==7)options(L.t("Interval slideshow"),new String[]{L.t("3 secunde"),L.t("5 secunde"),L.t("10 secunde")},new Choice(){public void pick(int n){getSharedPreferences("gallery",0).edit().putInt("interval",new int[]{3,5,10}[n]).apply();}});
            else if(i==8){if(!viewer)open();rotation=(rotation+90)%360;if(face!=null)face.invalidate();}
            else if(i==9)share();
            else if(i>=4){selected=CircularNavigation.step(selected,i==4?-1:1,images.size());open();}else if(i==3){info();}else if(i==0) open(); else if(i==2) load(); else options(L.t("Aplici ca fundal?"),new String[]{L.t("Anulează"),L.t("Aplică")},new Choice() { public void pick(int yes) {
                if(yes==1) { final Uri uri=images.get(selected); worker.execute(new Runnable() { public void run() { try { Appearance.saveWallpaper(GalleryActivity.this,uri); ui(new Runnable() { public void run() { notice(L.t("Fundal salvat")); } }); } catch(Exception|OutOfMemoryError error) { ui(new Runnable() { public void run() { notice(L.t("Fundalul nu poate fi salvat")); } }); } } }); }
            }});
        }});
    }
    private void info(){final Uri uri=images.get(selected);final String title=names.get(selected);worker.execute(new Runnable(){public void run(){final ArrayList<String> rows=new ArrayList<String>();rows.add(title);try{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth>0)rows.add(o.outWidth+" × "+o.outHeight+" px · original");try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int size=c.getColumnIndex("_size");if(size>=0)rows.add(android.text.format.Formatter.formatFileSize(GalleryActivity.this,c.getLong(size)));int date=c.getColumnIndex("datetaken");if(date>=0&&c.getLong(date)>0)rows.add(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(c.getLong(date))));}}}catch(Exception ignored){rows.add(L.t("Unele informații nu sunt disponibile"));}ui(new Runnable(){public void run(){options(L.t("Informații imagine"),rows.toArray(new String[0]),new Choice(){public void pick(int n){}});}});}});}
    private void mark(){String uri=images.get(selected).toString();if(!marked.remove(uri)){if(marked.size()>=50){notice(L.t("Maximum 50 imagini"));return;}marked.add(uri);}if(face!=null)face.invalidate();}
    private void selection(){options(L.t("Selecție · ")+marked.size(),new String[]{L.t("Partajează selecția"),L.t("Adaugă selecția la favorite"),L.t("Golește selecția")},new Choice(){public void pick(int i){if(i==0){if(marked.isEmpty()){notice(L.t("Marchează cu 5"));return;}ArrayList<Uri> uris=new ArrayList<Uri>();for(String id:marked)uris.add(Uri.parse(id));ShareHubActivity.sendMany(GalleryActivity.this,uris,"image/*");}else if(i==1){Set<String> ids=favorites();ids.addAll(marked);getSharedPreferences("gallery",0).edit().putStringSet("favorites",ids).apply();notice(L.t("Favorite salvate"));}else{marked.clear();if(face!=null)face.invalidate();}}});}
    private final KeyRelease actions=new KeyRelease();
    private boolean discrete(int key){return key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_5||key==KeyEvent.KEYCODE_1||key==KeyEvent.KEYCODE_3||key==KeyEvent.KEYCODE_7||key==KeyEvent.KEYCODE_9;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(face!=null&&!images.isEmpty()&&discrete(event.getKeyCode())){if(actions.accept(event))onKeyDown(event.getKeyCode(),event);return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN)actions.reset();
        return super.dispatchKeyEvent(event);
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(face!=null && !images.isEmpty()) {
            if(!viewer&&key==KeyEvent.KEYCODE_5){if(event.getRepeatCount()==0)mark();return true;}
            if(viewer&&(key==KeyEvent.KEYCODE_7||key==KeyEvent.KEYCODE_9)){if(event.getRepeatCount()==0){zoom=Math.max(1,Math.min(8,zoom+(key==KeyEvent.KEYCODE_9?1:-1)));panX=panY=0;face.invalidate();}return true;}
            if(slideshow&&event.getRepeatCount()==0){stopSlides();if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){notice(L.t("Slideshow oprit"));return true;}}
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) { if(event.getRepeatCount()==0) { if(!viewer) { if(getIntent().getBooleanExtra("wallpaper",false)) imageOptions(); else open(); } else { zoom=zoom==4?1:zoom*2;panX=panY=0; face.invalidate(); } } return true; }
            if(viewer&&(key==KeyEvent.KEYCODE_1||key==KeyEvent.KEYCODE_3)){if(event.getRepeatCount()==0){selected=CircularNavigation.step(selected,key==KeyEvent.KEYCODE_1?-1:1,images.size());open();}return true;}
            if(viewer&&zoom>1&&photo!=null&&(key==19||key==20||key==21||key==22)){float pw=rotation%180==0?photo.getWidth():photo.getHeight(),ph=rotation%180==0?photo.getHeight():photo.getWidth();float scale=Math.min(face.getWidth()/pw,face.getHeight()/ph)*zoom;float maxX=Math.max(0,(pw*scale-face.getWidth())/2),maxY=Math.max(0,(ph*scale-face.getHeight())/2);panX=Math.max(-maxX,Math.min(maxX,panX+(key==21?panStep(event):key==22?-panStep(event):0)));panY=Math.max(-maxY,Math.min(maxY,panY+(key==19?panStep(event):key==20?-panStep(event):0)));face.invalidate();return true;}
            int step=key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:key==KeyEvent.KEYCODE_DPAD_UP?(viewer?-1:-2):key==KeyEvent.KEYCODE_DPAD_DOWN?(viewer?1:2):0;
            if(step!=0) { int old=selected/PAGE_SIZE; selected=viewer?CircularNavigation.step(selected,step,images.size()):selected==0&&step<0?images.size()-1:selected==images.size()-1&&step>0?0:Math.max(0,Math.min(images.size()-1,selected+step)); if(viewer) open(); else { if(old!=selected/PAGE_SIZE) thumbnails();caption(); face.invalidate(); } return true; }
        }
        return super.onKeyDown(key,event);
    }
    private int panStep(KeyEvent e){return dp(Math.min(48,12+e.getRepeatCount()*3));}
    protected void onPause(){actions.reset();stopSlides();super.onPause();}
    protected void onDestroy() {stopSlides(); closed=true; generation++; clearImages();thumbnailCache.evictAll(); super.onDestroy(); }
    private final class Face extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final CanvasText labels=new CanvasText(GalleryActivity.this,paint);
        Face(){super(GalleryActivity.this);}
        protected void onDraw(Canvas canvas){
            canvas.drawColor(palette.background);
            if(viewer){
                if(photo==null){labels.draw(canvas,L.t(loadingPhoto?"Se încarcă…":"Imagine indisponibilă"),getWidth()/2f,getHeight()/2f,17,Color.WHITE,Paint.Align.CENTER,getWidth()-dp(20));return;}
                float pw=rotation%180==0?photo.getWidth():photo.getHeight(),ph=rotation%180==0?photo.getHeight():photo.getWidth();
                float scale=Math.min(getWidth()/pw,getHeight()/ph)*zoom,w=photo.getWidth()*scale,h=photo.getHeight()*scale;
                canvas.save();canvas.translate(getWidth()/2f+panX,getHeight()/2f+panY);canvas.rotate(rotation);
                canvas.drawBitmap(photo,null,new RectF(-w/2,-h/2,w/2,h/2),paint);canvas.restore();
                paint.setColor(0xcc101020);canvas.drawRoundRect(getWidth()-dp(45),getHeight()-dp(25),getWidth()-dp(5),getHeight()-dp(5),dp(5),dp(5),paint);
                labels.draw(canvas,zoom+"×",getWidth()-dp(25),getHeight()-dp(11),13,Color.WHITE,Paint.Align.CENTER,dp(34));return;
            }
            if(images.isEmpty()){labels.draw(canvas,L.t("Nicio imagine"),getWidth()/2f,getHeight()/2f,19,Color.WHITE,Paint.Align.CENTER,getWidth()-dp(20));return;}
            Set<String> favoriteIds=favorites();float cellWidth=getWidth()/2f,cellHeight=getHeight()/2f;
            for(int i=selected/PAGE_SIZE*PAGE_SIZE;i<Math.min(images.size(),selected/PAGE_SIZE*PAGE_SIZE+PAGE_SIZE);i++){
                int cell=i%PAGE_SIZE;float x=dp(4)+cell%2*cellWidth,y=dp(3)+cell/2*cellHeight;
                float width=cellWidth-dp(8),height=cellHeight-dp(6),imageHeight=Math.max(dp(18),height-dp(37));
                paint.setColor(i==selected?palette.selection:palette.card);canvas.drawRoundRect(x,y,x+width,y+height,dp(6),dp(6),paint);
                Bitmap bitmap=thumbs.get(i);
                if(bitmap!=null&&!bitmap.isRecycled()){
                    canvas.save();canvas.clipRect(x+dp(3),y+dp(3),x+width-dp(3),y+imageHeight);
                    float scale=Math.max((width-dp(6))/bitmap.getWidth(),(imageHeight-dp(3))/bitmap.getHeight());
                    float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
                    canvas.drawBitmap(bitmap,null,new RectF(x+width/2-w/2,y+imageHeight/2-h/2,x+width/2+w/2,y+imageHeight/2+h/2),paint);canvas.restore();
                }
                int ink=i==selected?palette.ink:Color.WHITE;String[] lines=labels.lines(names.get(i),14,width-dp(10));
                labels.draw(canvas,lines[0],x+dp(5),y+imageHeight+dp(15),14,ink,Paint.Align.LEFT,width-dp(10));
                labels.draw(canvas,lines[1],x+dp(5),y+imageHeight+dp(31),14,ink,Paint.Align.LEFT,width-dp(10));
                if(marked.contains(images.get(i).toString())||favoriteIds.contains(images.get(i).toString()))labels.draw(canvas,(marked.contains(images.get(i).toString())?"✓ ":"")+(favoriteIds.contains(images.get(i).toString())?"★":""),x+dp(5),y+dp(17),14,Color.WHITE,Paint.Align.LEFT,width-dp(10));
            }
        }
    }
}
