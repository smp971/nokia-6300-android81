package ro.leo.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

/** Draftable Home transforms in logical screen coordinates; never rotates Android. */
final class HomeCanvasLayout {
    static final String[] NAMES={"Fundal informații","Stare rețea","Ceas","Dată","SIM 1","Carduri","Scurtături","SIM 2"};
    private static final int[] EDIT_ORDER={0,1,2,3,4,7,5,6};
    static final int COUNT=NAMES.length;
    final int[] x=new int[COUNT],y=new int[COUNT],size=new int[COUNT],rotation=new int[COUNT],alignment=new int[COUNT];
    final boolean[] visible=new boolean[COUNT];
    final RectF[] frames=new RectF[COUNT];
    private boolean needsSimSplit;
    HomeCanvasLayout(Context context){SharedPreferences p=context.getSharedPreferences("home-canvas",0);needsSimSplit=!p.contains("7x");for(int i=0;i<COUNT;i++){
        x[i]=limit(p.getInt(i+"x",0),-240,240);y[i]=limit(p.getInt(i+"y",0),-276,276);
        size[i]=limit(p.getInt(i+"size",100),50,150);rotation[i]=Math.floorMod(p.getInt(i+"rotation",0),4);
        alignment[i]=limit(p.getInt(i+"align",0),0,3);visible[i]=p.getBoolean(i+"visible",true);frames[i]=new RectF();
    }}
    private static int limit(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    static int nextItem(int current,int direction){for(int at=0;at<COUNT;at++)if(EDIT_ORDER[at]==current)return EDIT_ORDER[Math.floorMod(at+direction,COUNT)];return 0;}
    static RectF textBounds(Paint paint,String text,float center,float baseline){
        float width=paint.measureText(text)+8;
        Paint.FontMetrics metrics=paint.getFontMetrics();
        return new RectF(center-width/2,baseline+metrics.ascent-2,
            center+width/2,baseline+metrics.descent+2);
    }
    void reset(int i){x[i]=y[i]=rotation[i]=alignment[i]=0;size[i]=100;visible[i]=true;}
    void save(Context context){SharedPreferences.Editor p=context.getSharedPreferences("home-canvas",0).edit();for(int i=0;i<COUNT;i++)
        p.putInt(i+"x",x[i]).putInt(i+"y",y[i]).putInt(i+"size",size[i]).putInt(i+"rotation",rotation[i]).putInt(i+"align",alignment[i]).putBoolean(i+"visible",visible[i]);p.apply();}
    boolean begin(Canvas c,int i,float l,float t,float r,float b,boolean editing){
        RectF base=new RectF(l,t,r,b);Matrix transform=matrixFor(i,base,editing);
        transform.mapRect(frames[i],base);
        if(!visible[i]&&!editing)return false;
        if(!visible[i]&&editing)c.saveLayerAlpha(0,0,240,276,72);else c.save();
        c.concat(transform);return true;
    }
    private Matrix matrixFor(int i,RectF base,boolean editing){
        float s=size[i]/100f;
        float w=(rotation[i]%2==0?base.width():base.height()),h=(rotation[i]%2==0?base.height():base.width());
        s=Math.min(s,Math.min(232f/Math.max(1,w),268f/Math.max(1,h)));
        float cx=base.centerX()+x[i],cy=base.centerY()+y[i];
        if(alignment[i]==1)cx=4+w*s/2;else if(alignment[i]==2)cx=120;else if(alignment[i]==3)cx=236-w*s/2;
        cx=Math.max(4+w*s/2,Math.min(236-w*s/2,cx));cy=Math.max(4+h*s/2,Math.min(272-h*s/2,cy));
        // Store clamped offsets, so reversing direction responds immediately at an edge.
        if(editing){x[i]=Math.round(cx-base.centerX());y[i]=Math.round(cy-base.centerY());}
        Matrix transform=new Matrix();transform.setTranslate(-base.centerX(),-base.centerY());
        transform.postScale(s,s);transform.postRotate(rotation[i]*90);transform.postTranslate(cx,cy);
        return transform;
    }
    void splitSims(RectF oldGroup,RectF first,RectF second){
        if(!needsSimSplit)return;
        needsSimSplit=false;
        Matrix old=matrixFor(4,oldGroup,false);
        float[] centers={first.centerX(),first.centerY(),second.centerX(),second.centerY()};old.mapPoints(centers);
        float[] values=new float[9];old.getValues(values);
        int percent=Math.round(100*(float)Math.hypot(values[Matrix.MSCALE_X],values[Matrix.MSKEW_Y]));
        int angle=rotation[4];boolean shown=visible[4];
        RectF[] rows={first,second};int[] ids={4,7};
        for(int row=0;row<2;row++){
            int id=ids[row];x[id]=Math.round(centers[row*2]-rows[row].centerX());y[id]=Math.round(centers[row*2+1]-rows[row].centerY());
            size[id]=percent;rotation[id]=angle;alignment[id]=0;visible[id]=shown;
        }
    }
}
