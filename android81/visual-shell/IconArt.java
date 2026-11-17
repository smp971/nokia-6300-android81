package ro.leo.visual;
import android.graphics.*;
/** Original flat vector artwork for Leo apps without a Gaia asset. */
final class IconArt {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();private final Path path=new Path();
    static final int[] COLORS={0xff16c578,0xff1aa9ed,0xffffb51b,0xff9858ef,0xff5268ed,0xfff05691,0xffff596b,0xfff2a11d,0xff637fa5,0xff5ebc35,0xffffae39,0xff328fee,0xffef435b,0xffdc4e9d,0xff14aebc,0xff3e88ee,0xffa35bed,0xffffb716,0xff41ac8c,0xff16a9d4,0xff985ed7,0xff3db987,0xff249be9,0xff2b9e7b,0xffe77a32,0xff00a7bc,0xff288cda,0xff239a82,0xff548fe0,0xffa651d6,0xffb67a35,0xffd95183,0xff1aabb0,0xff478de0,0xff5b748b,0xff18a99d,0xff4269c7,0xff12ad99,0xffed8b25,0xffa553cc,0xff189faa,0xff23855d,0xff3479d8,0xff9b68d7,0xffdca42a,0xff31a477,0xffdc6378,0xff5369bd};
    private void round(Canvas c,float l,float t,float r,float b,float radius,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,p);}
    private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
        void draw(Canvas c,int index,float x,float y) {
            c.save();
            c.translate(x,y);
            int color = COLORS[index];
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawCircle(0, 0, 25, p);
            p.setColor(Appearance.mix(color, 0xff17254a, .32f));
            box.set(-25, -25, 25, 25);
            c.drawArc(box, 30, 180, true, p);
            p.setColor(0xfffbfbff);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setStrokeCap(Paint.Cap.ROUND);
            switch(index) {
                case 38:c.drawArc(-14,-14,14,14,270,90,false,p);c.drawArc(-8,-8,8,8,270,90,false,p);c.drawCircle(-10,10,2,p);break;
                case 39:c.drawCircle(0,-5,6,p);c.drawArc(-13,-14,13,12,195,150,false,p);line(c,0,2,0,14);line(c,-7,14,7,14);break;
                case 40: {
                    c.drawArc(-16,-16,16,16,205,130,false,p);
                    c.drawArc(-11,-11,11,11,205,130,false,p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xffc5fff3);
                    c.drawCircle(0,1,3.5f,p);
                    c.drawRoundRect(-9,7,9,13,2,2,p);
                    break;
                }
                case 41:c.drawRect(-15,-11,15,11,p);line(c,-10,-5,-4,0);line(c,-4,0,-10,5);line(c,1,6,10,6);break;
                case 42:c.drawRect(-14,-13,14,13,p);line(c,0,-13,0,13);line(c,-10,-5,-4,-5);line(c,4,2,10,2);break;
                case 43:c.drawRoundRect(-12,-10,14,13,2,2,p);line(c,-16,-14,10,-14);line(c,-16,-14,-16,8);line(c,-6,-2,7,-2);line(c,-6,5,3,5);break;
                case 44:c.drawRoundRect(-14,-12,14,13,3,3,p);for(int z=-8;z<9;z+=5){line(c,-2,z,2,z);line(c,0,z,0,z+3);}break;
                case 45:c.drawRoundRect(-15,-9,15,11,3,3,p);line(c,-14,-3,14,-3);c.drawCircle(7,5,2,p);break;
                case 46:path.reset();path.moveTo(-14,14);path.lineTo(-7,-14);path.lineTo(7,-14);path.lineTo(14,14);path.close();c.drawPath(path,p);line(c,0,10,9,-9);break;
                case 47:c.drawRect(-13,-13,-3,-3,p);c.drawRect(3,-13,13,-3,p);c.drawRect(-13,3,-3,13,p);line(c,4,4,10,4);line(c,10,4,10,13);line(c,4,10,6,10);break;
                case 36: for(int row=0;row<2;row++)for(int col=0;col<2;col++)c.drawRoundRect(-13+col*15,-13+row*15,-3+col*15,-3+row*15,2,2,p);break;
                case 37: c.drawCircle(0,0,13,p);c.drawOval(-6,-13,6,13,p);line(c,-13,0,13,0);line(c,-10,-7,10,-7);line(c,-10,7,10,7);break;
                case 29: c.drawCircle(0,0,14,p);c.drawCircle(-6,-5,2,p);c.drawCircle(5,-7,2,p);c.drawCircle(8,3,2,p);line(c,-8,9,3,9);break;
                case 30: c.drawRoundRect(-13,-12,13,12,2,2,p);line(c,0,-12,0,12);line(c,-9,-5,-4,-5);line(c,4,-5,9,-5);line(c,-9,2,-4,2);line(c,4,2,9,2);break;
                case 31: c.drawRoundRect(-15,-8,15,10,5,5,p);line(c,-8,-3,-8,5);line(c,-12,1,-4,1);c.drawCircle(7,-2,1,p);c.drawCircle(11,4,1,p);break;
                case 32: c.drawCircle(-10,8,4,p);c.drawCircle(10,8,4,p);c.drawCircle(0,-9,4,p);line(c,-8,4,-2,-5);line(c,8,4,2,-5);break;
                case 33: c.drawArc(-13,-13,13,13,35,290,false,p);line(c,11,-10,12,0);line(c,12,0,3,-3);line(c,0,10,0,-5);break;
                case 35: c.drawCircle(0,0,11,p);c.drawCircle(0,0,3,p);line(c,0,-17,0,-10);line(c,0,10,0,17);line(c,-17,0,-10,0);line(c,10,0,17,0);break;
                case 34: c.drawRoundRect(-15,-8,15,12,3,3,p);c.drawCircle(0,2,6,p);line(c,-8,-8,-4,-13);line(c,-4,-13,7,-13);line(c,7,-13,10,-8);break;
                case 28: c.drawCircle(0,0,14,p);c.drawArc(-5,-9,5,1,195,285,false,p);line(c,0,1,0,5);c.drawPoint(0,10,p);break;
                case 27: c.drawRoundRect(-13,-10,13,12,2,2,p);line(c,-8,-10,-8,-2);line(c,-8,-2,7,-2);line(c,7,-2,7,-10);c.drawRect(-7,4,7,12,p);break;
                case 26: {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xffffd066);
                    c.drawCircle(-6,-7,10,p);
                    p.setColor(0xffe9faff);
                    c.drawCircle(7,1,8,p);
                    c.drawCircle(-7,6,6,p);
                    c.drawRoundRect(-12,4,15,12,4,4,p);
                    break;
                }
                case 25: c.drawRoundRect(-14,-7,14,12,3,3,p);line(c,-10,-8,8,-17);c.drawCircle(-5,3,5,p);line(c,4,-1,10,-1);line(c,4,5,10,5);break;
                case 24: line(c,-14,8,-7,8);line(c,-7,8,-3,-8);line(c,-3,-8,3,12);line(c,3,12,8,-2);line(c,8,-2,14,-2);break;
                case 23: {
                    round(c, -17, -12, 17, 12, 4, 0xff173238);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(0xffafffe5);
                    line(c,-10,-5,-3,0); line(c,-3,0,-10,5); line(c,2,6,10,6);
                    break;
                }
                case 22: c.drawRoundRect(-13,-9,13,10,2,2,p);line(c,-12,-8,0,2);line(c,0,2,12,-8);break;
                case 12: c.drawRect(-12,-10,12,10,p);line(c,-4,-5,5,0);line(c,5,0,-4,5);line(c,-4,5,-4,-5);break;
                case 13: {
                    round(c, -6, -16, 6, 4, 6, 0xffffecf3);
                    p.setColor(0xffffd3e5);
                    p.setStyle(Paint.Style.STROKE);
                    c.drawArc(-10,-5,10,11,0,180,false,p);
                    line(c,0,11,0,16);
                    break;
                }
                case 14: line(c,-12,-5,12,-5);line(c,7,-10,12,-5);line(c,-12,6,12,6);line(c,-12,6,-7,11);break;
                case 15: line(c,0,-12,0,4);line(c,-6,-2,0,4);line(c,0,4,6,-2);line(c,-11,10,11,10);break;
                case 16: c.drawCircle(5,-5,4,p);line(c,-12,10,-2,-2);line(c,-2,-2,11,10);break;
                case 17: for(int n=0;n<5;n++){c.save();c.rotate(n*72);line(c,0,-13,4,-3);line(c,4,-3,12,-4);c.restore();}break;
                case 19: {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xffd4f9ff);
                    c.drawCircle(0,0,16,p);
                    p.setColor(0xff176fa8);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.3f);
                    c.drawOval(-7,-15,7,15,p);
                    line(c,-14,0,14,0);
                    line(c,-11,-7,11,-7);
                    line(c,-11,7,11,7);
                    break;
                }
                case 20: for(int n=0;n<3;n++){line(c,-12,-9+n*9,12,-9+n*9);c.drawCircle(-5+n*5,-9+n*9,3,p);}break;
                case 21: line(c,-10,0,-2,8);line(c,-2,8,12,-10);break;
                case 18: c.drawRoundRect(new RectF(-9,-2,9,12),2,2,p);c.drawArc(-6,-13,6,4,180,180,false,p);break;
                case 0: path.reset();path.moveTo(-12,-12);path.lineTo(-5,-13);path.lineTo(-2,-6);path.lineTo(-6,-3);path.cubicTo(-4,2,0,6,5,8);path.lineTo(8,4);path.lineTo(14,8);path.lineTo(12,14);path.cubicTo(-2,15,-14,2,-12,-12);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);break;
                case 1: c.drawCircle(0,-5,5,p); c.drawArc(-10,3,10,17,190,160,false,p); break;
                case 2: box.set(-12,-9,12,7); c.drawRoundRect(box,3,3,p); line(c,-7,7,-10,12); line(c,-5,-3,6,-3); line(c,-5,2,3,2); break;
                case 3: c.drawCircle(0,0,12,p); line(c,0,0,0,-7); line(c,0,0,6,3); break;
                case 4: {
                    round(c, -12, -16, 12, 16, 4, 0xfff0f0ff);
                    round(c, -8, -12, 8, -5, 2, 0xff34266b);
                    p.setColor(0xff7959d9);
                    for (int row=0;row<2;row++) for (int column=0;column<3;column++) {
                        c.drawCircle(-6+column*6, row*6, 2, p);
                    }
                    break;
                }
                case 5: box.set(-13,-10,13,10); c.drawRoundRect(box,2,2,p); c.drawCircle(6,-4,2,p); line(c,-10,6,-3,-2); line(c,-3,-2,2,3); line(c,2,3,5,0); line(c,5,0,10,6); break;
                case 6: line(c,0,-9,0,8); line(c,0,-9,10,-12); line(c,10,-12,10,5); c.drawOval(-7,5,0,11,p); c.drawOval(3,2,10,8,p); break;
                case 7: {
                    round(c, -17, -11, 1, 7, 3, 0xfffce6a5);
                    round(c, -17, -7, 17, 13, 4, 0xffffedc1);
                    round(c, -16, -3, 17, 13, 3, 0xfff8ba47);
                    break;
                }
                case 8: c.drawCircle(0,0,9,p); c.drawCircle(0,0,3,p); for(int k=0;k<8;k++){c.save();c.rotate(k*45);line(c,0,-10,0,-14);c.restore();} break;
                case 9: {
                    round(c, -15, -15, -2, -2, 3, 0xfff2f7ff);
                    round(c, 2, -15, 15, -2, 3, 0xffe1ffb8);
                    round(c, -15, 2, -2, 15, 3, 0xffb8ed95);
                    round(c, 2, 2, 15, 15, 3, 0xfffbf4cd);
                    break;
                }
                case 10: {
                    round(c, -12, -16, 12, 16, 3, 0xfffff0cb);
                    round(c, -12, -16, 12, -9, 3, 0xffffd265);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(0xff9e5b18);
                    p.setStrokeWidth(2.2f);
                    line(c,-7,-4,7,-4); line(c,-7,2,7,2); line(c,-7,8,3,8);
                    break;
                }
                case 11: c.drawRect(-12,-10,12,12,p); line(c,-12,-3,12,-3); line(c,-6,-13,-6,-7); line(c,6,-13,6,-7); line(c,-6,3,-3,3); line(c,3,3,6,3); line(c,-6,8,-3,8); break;
            }
            p.setStyle(Paint.Style.FILL); c.restore();
        }
}
