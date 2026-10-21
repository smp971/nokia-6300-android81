package ro.leo.visual;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.Locale;
/** The same recognizable artwork as HOME, scaled to the application header. */
final class AppBadge extends Drawable {
    private final IconArt artwork=new IconArt();private final int index;
    AppBadge(String title,int size){
        String t=title.toLowerCase(Locale.ROOT);
        index=t.contains("backup")?27:t.contains("meteo")?26:t.contains("radio fm")?25:t.contains("diagnostic")?24:t.contains("terminal")?23:t.contains("email")?22:t.contains("telefon")||t.contains("apel")?0:t.contains("contact")?1:t.contains("mesaj")||t.contains("sms")||t.contains("conversa")?2:t.contains("calendar")||t.contains("eveniment")?11:t.contains("muzic")||t.contains("audio")?6:t.contains("fișier")?7:t.contains("descărc")?15:t.contains("noti")?10:t.contains("ceas")||t.contains("alarm")||t.contains("tempor")?3:t.contains("calcul")?4:t.contains("galer")?5:t.contains("video")?12:t.contains("report")?13:t.contains("bloc")?18:t.contains("fundal")?16:t.contains("browser")?19:8;
        setBounds(0,0,size,size);
    }
    public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/54f,getBounds().height()/54f);artwork.draw(c,index,27,27);c.restore();}
    public void setAlpha(int a){}
    public void setColorFilter(ColorFilter f){}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
