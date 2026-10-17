package ro.leo.gputest;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.util.Log;
import android.view.View;
public class HardwareBitmapProbe extends Activity {
    public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.HARDWARE;
            final Bitmap bitmap=BitmapFactory.decodeStream(getAssets().open("probe.png"),null,options);
            if(bitmap==null || bitmap.getConfig()!=Bitmap.Config.HARDWARE)
                throw new IllegalStateException("Hardware allocation not exercised");
            Log.i("LeoGpuTest","LEO_HWUI_BITMAP_OK config="+bitmap.getConfig());
            setContentView(new View(this) {
                final Paint paint=new Paint();
                protected void onDraw(Canvas canvas) {
                    canvas.drawColor(0xff0c141e);
                    paint.setColor(Color.WHITE); paint.setTextSize(15);
                    canvas.drawText("HARDWARE bitmap OK",8,24,paint);
                    canvas.drawBitmap(bitmap,null,new Rect(8,40,232,264),paint);
                }
            });
        } catch(Exception failure) { throw new RuntimeException(failure); }
    }
}
