package ro.leo.visual;
import ro.leo.i18n.L;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.Locale;
/** The same recognizable artwork as HOME, scaled to the application header. */
final class AppBadge extends Drawable {
    private static final android.util.LruCache<Integer,Bitmap> ICONS=new android.util.LruCache<>(24);
    private final Paint bitmapPaint=new Paint(Paint.FILTER_BITMAP_FLAG);private final int index;
    AppBadge(String title,int size){
        String t=title.toLowerCase(Locale.ROOT);
        index=t.contains("studio")?36:t.contains("web")?37:t.contains("gps")?35:t.contains(L.t("colecț"))||t.contains(L.t("teme"))?29:t.contains(L.t("cărți"))||t.contains(L.t("lectur"))||t.contains(L.t("capitol"))?30:t.contains(L.t("joc"))||t.contains("snake")||t.contains("sudoku")||t.contains("puzzle")?31:t.contains(L.t("conexiuni"))?32:t.contains(L.t("actualiz"))?33:t.contains(L.t("cameră"))?34:t.contains(L.t("ajutor"))?28:t.contains("backup")?27:t.contains(L.t("meteo"))?26:t.contains("radio fm")?25:t.contains("diagnostic")?24:t.contains("terminal")?23:t.contains("email")?22:t.contains(L.t("telefon"))||t.contains(L.t("apel"))?0:t.contains("contact")?1:t.contains(L.t("mesaj"))||t.contains("sms")||t.contains(L.t("conversa"))?2:t.contains("calendar")||t.contains(L.t("eveniment"))?11:t.contains(L.t("muzic"))||t.contains("audio")?6:t.contains(L.t("fișier"))?7:t.contains(L.t("descărc"))?15:t.contains(L.t("noti"))?10:t.contains(L.t("ceas"))||t.contains("alarm")||t.contains(L.t("tempor"))?3:t.contains(L.t("calcul"))?4:t.contains(L.t("galer"))?5:t.contains("video")?12:t.contains("report")?13:t.contains(L.t("bloc"))?18:t.contains(L.t("fundal"))?16:t.contains("browser")?19:8;
        setBounds(0,0,size,size);
    }
    public void draw(Canvas c){Bitmap image=ICONS.get(index);if(image==null){image=Bitmap.createBitmap(54,54,Bitmap.Config.ARGB_8888);new IconArt().draw(new Canvas(image),index,27,27);ICONS.put(index,image);}c.drawBitmap(image,null,getBounds(),bitmapPaint);}
    public void setAlpha(int a){}
    public void setColorFilter(ColorFilter f){}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
