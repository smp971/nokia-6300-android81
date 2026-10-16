"""Do not fetch optional Wi-Fi Direct services on a device without that feature."""
from pathlib import Path

path=Path('/root/android81-leo/src/packages/apps/Settings/src/com/android/settings/wfd/WifiDisplaySettings.java')
source=path.read_text()
marker='// LEO_OPTIONAL_WIFI: querying a missing service is fatal for this eng system app.'
needle='''    public static boolean isAvailable(Context context) {
        return context.getSystemService(Context.DISPLAY_SERVICE) != null'''
replacement='''    public static boolean isAvailable(Context context) {
        // LEO_OPTIONAL_WIFI: querying a missing service is fatal for this eng system app.
        if (!context.getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)) {
            return false;
        }
        return context.getSystemService(Context.DISPLAY_SERVICE) != null'''
if marker not in source:
    assert source.count(needle)==1
    path.with_name('WifiDisplaySettings.java.leo-original').write_text(source)
    path.write_text(source.replace(needle,replacement))
