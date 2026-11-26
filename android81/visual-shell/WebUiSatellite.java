package ro.leo.visual;
import android.graphics.*;
/** Shared vector satellite for the WebUI app and the live dashboard indicator. */
final class WebUiSatellite {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    void draw(Canvas canvas,float x,float y,float scale,int color){
        canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);canvas.rotate(-35);
        paint.setColor(color);paint.setStrokeWidth(1.4f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(-10,-3.5f,-4,3.5f,1,1,paint);canvas.drawRoundRect(4,-3.5f,10,3.5f,1,1,paint);
        canvas.drawLine(-7,-3.5f,-7,3.5f,paint);canvas.drawLine(7,-3.5f,7,3.5f,paint);canvas.drawLine(-10,0,10,0,paint);
        canvas.drawLine(0,-3,0,-7,paint);canvas.drawArc(-5,-13,5,-3,210,120,false,paint);
        paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(-2.8f,-4,2.8f,4,1,1,paint);
        canvas.restore();
    }
}
