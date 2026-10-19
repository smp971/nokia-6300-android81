package ro.leo.visual;
import android.content.Context;
/** Test-only notification sink. Never copied into the production APK. */
final class SmsReceiver {
    static int notifications;
    static void notify(Context c,String title,String address) { notifications++; }
}
