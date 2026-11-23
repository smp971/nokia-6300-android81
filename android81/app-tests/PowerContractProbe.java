import android.os.Parcel;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Read-only app_process diagnostic. Never invokes a power or input transaction. */
public final class PowerContractProbe {
    private static void transaction(String type, String name) throws Exception {
        Field field = Class.forName(type + "$Stub").getDeclaredField(name);
        field.setAccessible(true);
        System.out.println(name + "=" + field.getInt(null));
    }

    private static void signature(String type, String name) throws Exception {
        for (Method method : Class.forName(type).getDeclaredMethods())
            if (method.getName().equals(name)) System.out.println(method);
    }

    public static void main(String[] args) throws Exception {
        transaction("android.os.IPowerManager", "TRANSACTION_wakeUp");
        transaction("android.hardware.input.IInputManager", "TRANSACTION_injectInputEvent");
        signature("android.os.IPowerManager", "wakeUp");
        signature("android.hardware.input.IInputManager", "injectInputEvent");
        System.out.println("uptime=" + SystemClock.uptimeMillis());
        Parcel parcel = Parcel.obtain();
        try {
            new KeyEvent(100, 100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SOFT_LEFT,
                0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD).writeToParcel(parcel, 0);
            int bytes = parcel.dataSize();
            parcel.setDataPosition(0);
            System.out.print("keyparcel:");
            while (parcel.dataPosition() < bytes) System.out.print(" " + parcel.readInt());
            System.out.println();
        } finally { parcel.recycle(); }
    }
}
