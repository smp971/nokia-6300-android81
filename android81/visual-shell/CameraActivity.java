package ro.leo.visual;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** HAL1 UI in its own process. Camera IPC never blocks the UI/key dispatch thread. */
@SuppressWarnings("deprecation")
public final class CameraActivity extends LeoActivity implements SurfaceHolder.Callback {
    private volatile Camera camera;
    private final Object cameraLock = new Object();
    private volatile boolean taking, opening;
    private volatile int generation;
    private int captureSerial;
    private SurfaceView surface;
    private View preview;
    private TextView previewStatus;
    private int cameraId;
    private volatile List<String> flashModes=Collections.emptyList();
    private volatile String flashMode="off";
    private TextView cameraInfo;
    private HandlerThread cameraThread;
    private Handler cameraWork;
    private final Handler main = new Handler(Looper.getMainLooper());

    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cameraThread = new HandlerThread("LeoCamera");
        cameraThread.start();
        cameraWork = new Handler(cameraThread.getLooper());
        flashMode=getSharedPreferences("camera",0).getString("flash","off");
        if(getIntent().getBooleanExtra("diagnostic",false))diagnostic();else launchPreview();
    }
    private void launchPreview(){if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},73);return;}cameraId=0;viewfinder();}
    static String flashLabel(String mode){return "torch".equals(mode)?"Lanternă":"on".equals(mode)?"Pornit":"auto".equals(mode)?"Auto":"Oprit";}
    private void cameraMenu(){
        options("Cameră",new String[]{"Fotografiază","Bliț · "+flashLabel(flashMode),"Ultima fotografie","Galerie","Diagnostic cameră"},new Choice(){public void pick(int at){if(at==0)capture();else if(at==1)chooseFlash();else if(at==2)lastPhoto();else if(at==3)startActivity(new android.content.Intent(CameraActivity.this,GalleryActivity.class));else diagnostic();}});
    }
    private void lastPhoto(){String value=getSharedPreferences("camera",0).getString("last-photo","");try{File file=new File(value);File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Leo");if(!file.isFile()||!file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile()))throw new IOException();startActivity(new android.content.Intent(this,GalleryActivity.class).setData(LeoFileProvider.uri(this,file)).putExtra("title","Ultima fotografie"));}catch(Exception e){notice("Nu există încă o fotografie salvată disponibilă");}}
    private void chooseFlash(){
        final ArrayList<String> modes=new ArrayList<String>(flashModes);if(modes.isEmpty()){notice("Driverul nu oferă controlul blițului");return;}
        String[] labels=new String[modes.size()];for(int i=0;i<labels.length;i++)labels[i]=flashLabel(modes.get(i));
        options("Bliț",labels,new Choice(){public void pick(int at){applyFlash(modes.get(at));}});
    }
    private void applyFlash(final String mode){
        if(taking||opening){notice("Așteaptă camera");return;}final int ticket=generation;
        cameraWork.post(new Runnable(){public void run(){final Camera device=camera;if(ticket!=generation||device==null)return;try{Camera.Parameters p=device.getParameters();p.setFlashMode(mode);device.setParameters(p);main.post(new Runnable(){public void run(){if(ticket!=generation)return;flashMode=mode;getSharedPreferences("camera",0).edit().putString("flash","torch".equals(mode)?"off":mode).apply();cameraInfo.setText("Bliț: "+flashLabel(mode)+" · 640 × 480");if("torch".equals(mode))main.postDelayed(new Runnable(){public void run(){if(ticket==generation&&"torch".equals(flashMode))applyFlash("off");}},30000);}});}catch(RuntimeException fail){main.post(new Runnable(){public void run(){if(ticket==generation)notice("Modul de bliț a fost refuzat de driver");}});}}});
    }

    private void diagnostic() {
        release();
        final ArrayList<String> rows = new ArrayList<>();
        int count = 0;
        try { count = Camera.getNumberOfCameras(); } catch (RuntimeException ignored) {}
        final int total = count;
        rows.add("Camere raportate de Android · " + count);
        rows.add(count == 0 ? "Camera nu este încă disponibilă\nVerifică pornirea serviciilor" : "Deschide vizorul · OK declanșează");
        rows.add("Calea de salvare · Pictures/Leo");
        rows.add("BF20A1 · driver KaiOS și adaptor Android 8.1");
        list("Cameră Leo · experimental", rows, 0, new Choice() {
            public void pick(int at) {
                if (at != 1) { notice("Cameră Leo · diagnostic și salvare locală"); return; }
                if (total == 0) { notice("Camera nu este expusă de serviciul Android"); return; }
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 73); return;
                }
                cameraId = 0;
                viewfinder();
            }
        }, "Actualizează", new Runnable() { public void run() { diagnostic(); } });
    }

    private void viewfinder() {
        begin("Cameră Leo");
        preview = root;
        cameraInfo=text("Se pregătește vizorul…",12);root.addView(cameraInfo);
        surface = new SurfaceView(this);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        surface.getHolder().addCallback(this);
        left = new Runnable() { public void run() { cameraMenu(); } };
        back = new Runnable() { public void run() { finish(); } };
        footer("Opțiuni", "Se pornește…", "Înapoi");
        LinearLayout bar = (LinearLayout) root.getChildAt(root.getChildCount() - 1);
        previewStatus = (TextView) bar.getChildAt(1);
        surface.setFocusable(true);
        surface.requestFocus();
    }

    private static void closeCamera(Camera value) {
        if (value == null) return;
        try { value.stopPreview(); } catch (RuntimeException ignored) {}
        try { value.release(); } catch (RuntimeException ignored) {}
    }

    public void surfaceCreated(final SurfaceHolder holder) {
        if (root != preview || surface == null || holder != surface.getHolder() || isFinishing() || camera != null || opening) return;
        final int ticket = ++generation;
        final int rotation = getWindowManager().getDefaultDisplay().getRotation() * 90;
        opening = true;
        cameraWork.post(new Runnable() { public void run() {
            Camera value = null;
            try {
                value = Camera.open(cameraId);
                Camera.Parameters parameters = value.getParameters();
                List<String> supported=parameters.getSupportedFlashModes();flashModes=supported==null?Collections.<String>emptyList():new ArrayList<String>(supported);
                if(!flashModes.contains(flashMode))flashMode="off";
                if(flashModes.contains(flashMode))parameters.setFlashMode(flashMode);
                Camera.Size best = null;
                for (Camera.Size size : parameters.getSupportedPreviewSizes())
                    if (size.width <= 640 && (best == null || size.width * size.height > best.width * best.height)) best = size;
                if (best != null) parameters.setPreviewSize(best.width, best.height);
                best = null;
                for (Camera.Size size : parameters.getSupportedPictureSizes())
                    if (size.width * size.height <= 2000000 && (best == null || size.width * size.height > best.width * best.height)) best = size;
                if (best != null) parameters.setPictureSize(best.width, best.height);
                parameters.setJpegQuality(85);
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, info);
                parameters.setRotation((info.orientation + (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? -rotation : rotation) + 360) % 360);
                value.setParameters(parameters);
                value.setDisplayOrientation(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? (360 - (info.orientation + rotation) % 360) % 360 : (info.orientation - rotation + 360) % 360);
                if (ticket != generation || !holder.getSurface().isValid()) { closeCamera(value); return; }
                value.setPreviewDisplay(holder);
                value.startPreview();
                boolean cancelled;
                synchronized (cameraLock) {
                    cancelled = ticket != generation;
                    if (!cancelled) { camera = value; opening = false; }
                }
                if (cancelled) { closeCamera(value); return; }
                value.setErrorCallback(new Camera.ErrorCallback() { public void onError(int error, Camera source) {
                    main.post(new Runnable() { public void run() {
                        if (ticket != generation) return;
                        release(); notice("Camera s-a oprit · poți reveni cu Înapoi");
                        previewStatus.setText("Cameră oprită");
                    }});
                }});
                main.post(new Runnable() { public void run() { if (ticket == generation) {previewStatus.setText("OK declanșează");cameraInfo.setText("Bliț: "+flashLabel(flashMode)+" · 640 × 480");} }});
            } catch (Exception error) {
                closeCamera(value);
                main.post(new Runnable() { public void run() {
                    if (ticket != generation) return;
                    opening = false; camera = null;
                    notice("Vizor indisponibil · revino cu Înapoi");
                    previewStatus.setText("Cameră oprită");
                }});
            }
        }});
        main.postDelayed(new Runnable() { public void run() {
            if (ticket == generation && opening) {
                release(); notice("Pornirea camerei durează prea mult · poți ieși");
                previewStatus.setText("Cameră oprită");
            }
        }}, 15000);
    }

    public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {}
    public void surfaceDestroyed(SurfaceHolder holder) { if (surface != null && holder == surface.getHolder()) release(); }

    private void release() {
        final Camera old;
        synchronized (cameraLock) {
            ++generation; opening = false; taking = false;if("torch".equals(flashMode))flashMode="off";
            old = camera; camera = null;
        }
        if (old != null && cameraWork != null) cameraWork.post(new Runnable() { public void run() { closeCamera(old); }});
    }

    private void capture() {
        if (camera == null || taking) { notice(opening ? "Camera se pornește…" : "Vizorul nu este pregătit"); return; }
        final int ticket = generation;
        final int captureTicket = ++captureSerial;
        taking = true;
        previewStatus.setText("Capturează…");
        cameraWork.post(new Runnable() { public void run() {
            final Camera device = camera;
            if (ticket != generation || device == null) return;
            try { device.takePicture(null, null, new Camera.PictureCallback() {
                public void onPictureTaken(final byte[] jpeg, Camera source) {
                    if (ticket != generation) return;
                    if (jpeg == null || jpeg.length == 0 || jpeg.length > 16777216) { captureFailed(ticket); return; }
                    try { worker.execute(new Runnable() { public void run() {
                        try {
                            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Leo");
                            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException();
                            final File photo = File.createTempFile("LEO-", ".jpg", folder);
                            try (FileOutputStream out = new FileOutputStream(photo)) { out.write(jpeg); out.getFD().sync(); }
                            android.media.MediaScannerConnection.scanFile(CameraActivity.this, new String[]{photo.getPath()}, new String[]{"image/jpeg"}, null);
                            main.post(new Runnable() { public void run() {getSharedPreferences("camera",0).edit().putString("last-photo",photo.getPath()).apply();if (ticket == generation) { taking = false;previewStatus.setText("Salvată ✓");notice("Fotografie salvată în Pictures/Leo"); } }});
                            cameraWork.post(new Runnable() { public void run() {
                                if (ticket == generation && camera == device) try { device.startPreview(); } catch (RuntimeException ignored) { captureFailed(ticket); }
                            }});
                        } catch (Exception error) { captureFailed(ticket); }
                    }}); } catch (java.util.concurrent.RejectedExecutionException ignored) { /* Activity already destroyed. */ }
                }
            }); } catch (RuntimeException error) { captureFailed(ticket); }
        }});
        main.postDelayed(new Runnable() { public void run() {
            if (ticket == generation && captureTicket == captureSerial && taking) { release(); notice("Capturarea a expirat · revino cu Înapoi"); }
        }}, 15000);
    }

    private void captureFailed(final int ticket) {
        main.post(new Runnable() { public void run() { if (ticket == generation) { release(); notice("Capturarea a eșuat · revino cu Înapoi"); } }});
    }
    public boolean onKeyDown(int key, KeyEvent event) {
        if (root == preview && (key == 23 || key == 66 || key == KeyEvent.KEYCODE_CAMERA)) { if (event.getRepeatCount() == 0) capture(); return true; }
        return super.onKeyDown(key, event);
    }
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { super.onRequestPermissionsResult(request, permissions, results); if (request == 73) {if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)launchPreview();else diagnostic();} }
    protected void onResume() { super.onResume(); if (root == preview && surface != null && surface.getHolder().getSurface().isValid() && camera == null && !opening) surfaceCreated(surface.getHolder()); }
    protected void onPause() { release(); super.onPause(); }
    protected void onDestroy() { release(); if (cameraThread != null) cameraThread.quitSafely(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
