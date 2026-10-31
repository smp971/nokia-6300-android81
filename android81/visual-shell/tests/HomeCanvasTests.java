package ro.leo.visual;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;
/** Isolated preference namespace; no calls, messages or user Home changes. */
public final class HomeCanvasTests extends Instrumentation {
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public void onCreate(Bundle b){start();}
    public void onStart(){final String name="home-canvas-test-"+java.util.UUID.randomUUID();
        Context context=new ContextWrapper(getTargetContext()){
            public SharedPreferences getSharedPreferences(String ignored,int mode){return getBaseContext().getSharedPreferences(name,mode);}
        };
        Bundle result=new Bundle();Bitmap image=Bitmap.createBitmap(240,302,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);int checks=0;
        try{
            HomeCanvasLayout draft=new HomeCanvasLayout(context);
            check(draft.begin(c,2,50,30,190,80,false),"default clock visible");c.restore();
            check(draft.frames[2].equals(new RectF(50,30,190,80)),"default layout unchanged");checks++;
            draft.x[2]=12;draft.y[2]=24;check(new HomeCanvasLayout(context).x[2]==0,"draft not persisted");checks++;
            draft.save(context);HomeCanvasLayout loaded=new HomeCanvasLayout(context);
            check(loaded.x[2]==12&&loaded.y[2]==24,"saved positions reload");checks++;
            for(int angle=0;angle<4;angle++)for(int edge:new int[]{-999,999}){
                loaded.rotation[2]=angle;loaded.size[2]=150;loaded.x[2]=edge;loaded.y[2]=edge;
                check(loaded.begin(c,2,10,20,230,100,true),"editable clock");c.restore();RectF frame=loaded.frames[2];
                check(frame.left>=3.99f&&frame.top>=3.99f&&frame.right<=236.01f&&frame.bottom<=272.01f,"rotated item stays above softkeys");checks++;
            }
            loaded.visible[2]=false;int depth=c.getSaveCount();check(!loaded.begin(c,2,50,30,190,80,false)&&c.getSaveCount()==depth,"hidden item preserves canvas stack");checks++;
            check(loaded.begin(c,2,50,30,190,80,true),"hidden item available in editor");c.restore();checks++;
            loaded.reset(2);check(loaded.visible[2]&&loaded.size[2]==100&&loaded.rotation[2]==0&&loaded.x[2]==0,"reset restores safe default");checks++;
            result.putString("result","PASS "+checks+" Home canvas persistence, bounds, orientation and visibility checks");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{context.getSharedPreferences(name,0).edit().clear().commit();image.recycle();}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
