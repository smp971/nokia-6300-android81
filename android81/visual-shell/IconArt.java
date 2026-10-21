package ro.leo.visual;
import android.graphics.*;
/** Shared, colorful 50px artwork for HOME and application headers. */
final class IconArt {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();private final Path path=new Path();
    static final int[] COLORS={0xff16c578,0xff1aa9ed,0xffffb51b,0xff9858ef,0xff5268ed,0xfff05691,0xffff596b,0xfff2a11d,0xff637fa5,0xff5ebc35,0xffffae39,0xff328fee,0xffef435b,0xffdc4e9d,0xff14aebc,0xff3e88ee,0xffa35bed,0xffffb716,0xff41ac8c,0xff16a9d4,0xff985ed7,0xff3db987,0xff249be9,0xff2b9e7b,0xffe77a32,0xff00a7bc,0xff288cda,0xff239a82,0xff548fe0};
    private void round(Canvas c,float l,float t,float r,float b,float radius,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,p);}
    private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
        void draw(Canvas c,int index,float x,float y) {
            round(c,x-25,y-25,x+25,y+25,8,COLORS[index]);
            c.save(); c.translate(x,y); p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.8f); p.setStrokeCap(Paint.Cap.ROUND);
            switch(index) {
                case 28: c.drawCircle(0,0,14,p);c.drawArc(-5,-9,5,1,195,285,false,p);line(c,0,1,0,5);c.drawPoint(0,10,p);break;
                case 27: c.drawRoundRect(-13,-10,13,12,2,2,p);line(c,-8,-10,-8,-2);line(c,-8,-2,7,-2);line(c,7,-2,7,-10);c.drawRect(-7,4,7,12,p);break;
                case 26: c.drawCircle(-5,-5,8,p);c.drawCircle(4,4,9,p);line(c,-7,13,12,13);line(c,-14,-5,-17,-5);line(c,-5,-14,-5,-17);break;
                case 25: c.drawRoundRect(-14,-7,14,12,3,3,p);line(c,-10,-8,8,-17);c.drawCircle(-5,3,5,p);line(c,4,-1,10,-1);line(c,4,5,10,5);break;
                case 24: line(c,-14,8,-7,8);line(c,-7,8,-3,-8);line(c,-3,-8,3,12);line(c,3,12,8,-2);line(c,8,-2,14,-2);break;
                case 23: c.drawRect(-14,-11,14,11,p);line(c,-9,-5,-3,0);line(c,-3,0,-9,5);line(c,1,6,9,6);break;
                case 22: c.drawRoundRect(-13,-9,13,10,2,2,p);line(c,-12,-8,0,2);line(c,0,2,12,-8);break;
                case 12: c.drawRect(-12,-10,12,10,p);line(c,-4,-5,5,0);line(c,5,0,-4,5);line(c,-4,5,-4,-5);break;
                case 13: c.drawRoundRect(new RectF(-4,-12,4,4),4,4,p);c.drawArc(-8,-3,8,9,0,180,false,p);line(c,0,9,0,13);break;
                case 14: line(c,-12,-5,12,-5);line(c,7,-10,12,-5);line(c,-12,6,12,6);line(c,-12,6,-7,11);break;
                case 15: line(c,0,-12,0,4);line(c,-6,-2,0,4);line(c,0,4,6,-2);line(c,-11,10,11,10);break;
                case 16: c.drawCircle(5,-5,4,p);line(c,-12,10,-2,-2);line(c,-2,-2,11,10);break;
                case 17: for(int n=0;n<5;n++){c.save();c.rotate(n*72);line(c,0,-13,4,-3);line(c,4,-3,12,-4);c.restore();}break;
                case 19: c.drawCircle(0,0,13,p);c.drawOval(-6,-13,6,13,p);line(c,-12,0,12,0);break;
                case 20: for(int n=0;n<3;n++){line(c,-12,-9+n*9,12,-9+n*9);c.drawCircle(-5+n*5,-9+n*9,3,p);}break;
                case 21: line(c,-10,0,-2,8);line(c,-2,8,12,-10);break;
                case 18: c.drawRoundRect(new RectF(-9,-2,9,12),2,2,p);c.drawArc(-6,-13,6,4,180,180,false,p);break;
                case 0: path.reset();path.moveTo(-12,-12);path.lineTo(-5,-13);path.lineTo(-2,-6);path.lineTo(-6,-3);path.cubicTo(-4,2,0,6,5,8);path.lineTo(8,4);path.lineTo(14,8);path.lineTo(12,14);path.cubicTo(-2,15,-14,2,-12,-12);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);break;
                case 1: c.drawCircle(0,-5,5,p); c.drawArc(-10,3,10,17,190,160,false,p); break;
                case 2: box.set(-12,-9,12,7); c.drawRoundRect(box,3,3,p); line(c,-7,7,-10,12); line(c,-5,-3,6,-3); line(c,-5,2,3,2); break;
                case 3: c.drawCircle(0,0,12,p); line(c,0,0,0,-7); line(c,0,0,6,3); break;
                case 4: box.set(-10,-13,10,13); c.drawRoundRect(box,2,2,p); line(c,-5,-7,5,-7); line(c,-5,0,-1,0); line(c,3,0,6,0); line(c,-5,6,-1,6); line(c,3,6,6,6); break;
                case 5: box.set(-13,-10,13,10); c.drawRoundRect(box,2,2,p); c.drawCircle(6,-4,2,p); line(c,-10,6,-3,-2); line(c,-3,-2,2,3); line(c,2,3,5,0); line(c,5,0,10,6); break;
                case 6: line(c,0,-9,0,8); line(c,0,-9,10,-12); line(c,10,-12,10,5); c.drawOval(-7,5,0,11,p); c.drawOval(3,2,10,8,p); break;
                case 7: path.reset(); path.moveTo(-13,-8); path.lineTo(-3,-8); path.lineTo(0,-4); path.lineTo(13,-4); path.lineTo(13,10); path.lineTo(-13,10); path.close(); c.drawPath(path,p); break;
                case 8: c.drawCircle(0,0,9,p); c.drawCircle(0,0,3,p); for(int k=0;k<8;k++){c.save();c.rotate(k*45);line(c,0,-10,0,-14);c.restore();} break;
                case 9: for(int row=0;row<2;row++) for(int col=0;col<2;col++) c.drawRect(-10+col*12,-10+row*12,-3+col*12,-3+row*12,p); break;
                case 10: c.drawRect(-10,-13,10,13,p); line(c,-5,-6,5,-6); line(c,-5,0,5,0); line(c,-5,6,2,6); break;
                case 11: c.drawRect(-12,-10,12,12,p); line(c,-12,-3,12,-3); line(c,-6,-13,-6,-7); line(c,6,-13,6,-7); line(c,-6,3,-3,3); line(c,3,3,6,3); line(c,-6,8,-3,8); break;
            }
            p.setStyle(Paint.Style.FILL); c.restore();
        }
}
