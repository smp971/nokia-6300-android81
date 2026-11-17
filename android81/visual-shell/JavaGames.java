package ro.leo.visual;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import ro.leo.i18n.L;

/** Hands Java ME games to the separately installed J2ME Loader. */
final class JavaGames {
    private static final String PACKAGE = "ru.playsoftware.j2meloader";

    static void openLibrary(LeoActivity activity) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (intent != null && start(activity, intent)) return;
        activity.notice(L.t("Instalează J2ME Loader pentru jocurile Java"));
    }

    static boolean openJar(LeoActivity activity, Uri jar) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (intent == null) return false;
        // The explicit launcher activity accepts content Uris even though its VIEW filter lists file Uris.
        intent.setAction(Intent.ACTION_VIEW).removeCategory(Intent.CATEGORY_LAUNCHER);
        intent.setDataAndType(jar, "application/java-archive");
        intent.setClipData(ClipData.newRawUri("J2ME", jar));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return start(activity, intent);
    }

    private static boolean start(LeoActivity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }
}
