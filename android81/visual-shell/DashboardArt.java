package ro.leo.visual;
import android.content.Context;
import android.graphics.*;
import java.io.IOException;
import java.io.InputStream;

/** Compact SIM and weather symbols for Home. */
final class DashboardArt {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private static final ColorFilter[] SIM_COLORS = {
        new PorterDuffColorFilter(0xff67dbe4, PorterDuff.Mode.SRC_IN),
        new PorterDuffColorFilter(0xffb7a1ff, PorterDuff.Mode.SRC_IN),
    };
    private static final ColorFilter SIM_UNAVAILABLE =
        new PorterDuffColorFilter(0xff8a99ab, PorterDuff.Mode.SRC_IN);
    private final Paint simPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect simSource = new Rect();
    private final RectF simBounds = new RectF();
    private final Bitmap simCards;

    DashboardArt(Context context) {
        Bitmap sprite = null;
        try (InputStream stream = context.getAssets().open("catalog-icons/gaia/simcards.png")) {
            sprite = BitmapFactory.decodeStream(stream);
        } catch (IOException unavailable) {
            // The native SIM outline also works without the optional artwork.
        }
        simCards = sprite;
    }

    void sim(Canvas c, float x, float baseline, int slot, boolean available) {
        if (simCards == null) {
            drawSimOutline(c, x, baseline, slot, available);
            return;
        }
        // Gaia's left column contains the numbered SIM 1 and SIM 2 states.
        int width = simCards.getWidth() / 2;
        int height = simCards.getHeight() / 2;
        simSource.set(0, slot * height, width, (slot + 1) * height);
        simBounds.set(x - 9, baseline - 13, x + 9, baseline + 5);
        simPaint.setColorFilter(available ? SIM_COLORS[slot] : SIM_UNAVAILABLE);
        c.drawBitmap(simCards, simSource, simBounds, simPaint);
    }

    private void drawSimOutline(Canvas c,float x,float baseline,int slot,boolean available){
        c.save();c.translate(x,baseline-7);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);
        p.setColor(available?(slot==0?0xff67dbe4:0xffb7a1ff):0xff8a99ab);
        path.reset();path.moveTo(-5,-7);path.lineTo(2,-7);path.lineTo(6,-3);path.lineTo(6,7);path.lineTo(-5,7);path.close();c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(8);p.setTextAlign(Paint.Align.CENTER);
        c.drawText(Integer.toString(slot+1),.5f,4,p);c.restore();
    }
    private final WeatherArt weatherArt=new WeatherArt();
    void weather(Canvas canvas,float x,float y,float size,int code){weatherArt.draw(canvas,x,y,size,code);}
}
