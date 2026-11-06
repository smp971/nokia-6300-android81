package ro.leo.visual;
import android.graphics.*;import io.nayuki.qrcodegen.*;import java.util.*;
final class PocketQr {
 static String escape(String s){return s.replace("\\","\\\\").replace(";","\\;").replace(",","\\,").replace(":","\\:").replace("\n","").replace("\r","");}
 static String wifi(String ssid,String password,boolean open){if(ssid.isEmpty()||ssid.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32||password.length()>63)throw new IllegalArgumentException("SSID/password length");return "WIFI:T:"+(open?"nopass":"WPA")+";S:"+escape(ssid)+";P:"+escape(password)+";;";}
 static String contact(String name,String phone){if(name.trim().isEmpty()||!phone.matches("[+0-9 ()-]{1,40}"))throw new IllegalArgumentException("Invalid contact");return "BEGIN:VCARD\nVERSION:3.0\nFN:"+escape(name)+"\nTEL:"+phone+"\nEND:VCARD";}
 static QrCode code(String text){if(text==null||text.isEmpty()||text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>600)throw new IllegalArgumentException("QR text limit: 600 UTF-8 bytes");return QrCode.encodeSegments(QrSegment.makeSegments(text),QrCode.Ecc.MEDIUM,1,15,-1,true);}
 static Bitmap bitmap(String text,int pixels){QrCode qr=code(text);int scale=Math.max(1,pixels/(qr.size+8)),size=(qr.size+8)*scale;Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);Paint p=new Paint();p.setColor(Color.BLACK);for(int y=0;y<qr.size;y++)for(int x=0;x<qr.size;x++)if(qr.getModule(x,y))c.drawRect((x+4)*scale,(y+4)*scale,(x+5)*scale,(y+5)*scale,p);return b;}
}
