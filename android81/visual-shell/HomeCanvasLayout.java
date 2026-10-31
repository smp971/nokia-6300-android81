package ro.leo.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;

/** Draftable Home transforms in logical screen coordinates; never rotates Android. */
final class HomeCanvasLayout {
    static final String[] NAMES={"Fundal informații","Stare rețea","Ceas","Dată","Rețele SIM","Carduri","Scurtături"};
    static final int COUNT=NAMES.length;
    final int[] x=new int[COUNT],y=new int[COUNT],size=new int[COUNT],rotation=new int[COUNT],alignment=new int[COUNT];
    final boolean[] visible=new boolean[COUNT];
    final RectF[] frames=new RectF[COUNT];
    HomeCanvasLayout(Context context){SharedPreferences p=context.getSharedPreferences("home-canvas",0);for(int i=0;i<COUNT;i++){
        x[i]=limit(p.getInt(i+"x",0),-240,240);y[i]=limit(p.getInt(i+"y",0),-276,276);
        size[i]=limit(p.getInt(i+"size",100),50,150);rotation[i]=Math.floorMod(p.getInt(i+"rotation",0),4);
        alignment[i]=limit(p.getInt(i+"align",0),0,3);visible[i]=p.getBoolean(i+"visible",true);frames[i]=new RectF();
    }}
    private static int limit(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    void reset(int i){x[i]=y[i]=rotation[i]=alignment[i]=0;size[i]=100;visible[i]=true;}
    void save(Context context){SharedPreferences.Editor p=context.getSharedPreferences("home-canvas",0).edit();for(int i=0;i<COUNT;i++)
        p.putInt(i+"x",x[i]).putInt(i+"y",y[i]).putInt(i+"size",size[i]).putInt(i+"rotation",rotation[i]).putInt(i+"align",alignment[i]).putBoolean(i+"visible",visible[i]);p.apply();}
    boolean begin(Canvas c,int i,float l,float t,float r,float b,boolean editing){
        RectF base=new RectF(l,t,r,b);float s=size[i]/100f;
        float w=(rotation[i]%2==0?base.width():base.height()),h=(rotation[i]%2==0?base.height():base.width());
        s=Math.min(s,Math.min(232f/Math.max(1,w),268f/Math.max(1,h)));
        float cx=base.centerX()+x[i],cy=base.centerY()+y[i];
        if(alignment[i]==1)cx=4+w*s/2;else if(alignment[i]==2)cx=120;else if(alignment[i]==3)cx=236-w*s/2;
        cx=Math.max(4+w*s/2,Math.min(236-w*s/2,cx));cy=Math.max(4+h*s/2,Math.min(272-h*s/2,cy));
        // Store clamped offsets, so reversing direction responds immediately at an edge.
        if(editing){x[i]=Math.round(cx-base.centerX());y[i]=Math.round(cy-base.centerY());}
        Matrix transform=new Matrix();transform.setTranslate(-base.centerX(),-base.centerY());
        transform.postScale(s,s);transform.postRotate(rotation[i]*90);transform.postTranslate(cx,cy);
        transform.mapRect(frames[i],base);
        if(!visible[i]&&!editing)return false;
        if(!visible[i]&&editing)c.saveLayerAlpha(0,0,240,276,72);else c.save();
        c.concat(transform);return true;
    }
}
