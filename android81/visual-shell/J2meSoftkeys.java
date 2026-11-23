package ro.leo.visual;

import android.os.Parcel;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import java.lang.reflect.Field;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Translate only the Nokia's two physical softkeys inside the external MIDlet window.
 * The stock emulator already understands SOFT_LEFT/RIGHT. MENU and BACK must stay
 * distinct from scans 139/158, so changing its key-code-only profile is insufficient.
 */
final class J2meSoftkeys {
    private volatile boolean game, awake = true, available = true;
    private final ThreadPoolExecutor work = new ThreadPoolExecutor(0, 1, 2,
        TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(64));

    void foreground(String pkg, String activity) {
        // The accessibility menu is an overlay belonging to KeyShell; it does
        // not replace the underlying MIDlet activity when it opens or closes.
        if ("ro.leo.visual".equals(pkg) && !(activity.startsWith("ro.leo.visual.")
                && activity.endsWith("Activity"))) return;
        // Popup windows report a View class, not the activity beneath them.
        // Preserve the MIDlet context through its native options menu; real
        // library, file picker and configuration activities end that context.
        if ("ru.playsoftware.j2meloader".equals(pkg)
                && !"javax.microedition.shell.MicroActivity".equals(activity)
                && !(activity.startsWith("ru.playsoftware.j2meloader.")
                    && activity.endsWith("Activity"))) return;
        game = "ru.playsoftware.j2meloader".equals(pkg)
            && "javax.microedition.shell.MicroActivity".equals(activity);
    }

    void screen(boolean on) { awake = on; }

    boolean key(KeyEvent original) {
        if (!game || !awake || !available || original.getDeviceId() < 0) return false;
        int code;
        if (original.getScanCode() == 139 && original.getKeyCode() == KeyEvent.KEYCODE_MENU)
            code = KeyEvent.KEYCODE_SOFT_LEFT;
        else if (original.getScanCode() == 158 && original.getKeyCode() == KeyEvent.KEYCODE_BACK)
            code = KeyEvent.KEYCODE_SOFT_RIGHT;
        else return false;
        if (original.getAction() != KeyEvent.ACTION_DOWN && original.getAction() != KeyEvent.ACTION_UP)
            return false;
        final KeyEvent translated = new KeyEvent(original.getDownTime(), original.getEventTime(),
            original.getAction(), code, original.getRepeatCount(), original.getMetaState(),
            -1, 0, original.getFlags(), InputDevice.SOURCE_KEYBOARD);
        try {
            work.execute(new Runnable() { public void run() { inject(translated); } });
            return true;
        } catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
    }

    private void inject(KeyEvent event) {
        // Never launch a Java VM per press. The existing UID-checked root broker
        // runs the small native Binder client; the worker expires after two seconds.
        if (!game || !awake) return;
        Process process = null;
        Parcel parcel = Parcel.obtain();
        try {
            Class<?> api = Class.forName("android.hardware.input.IInputManager");
            api.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
            Field transaction = Class.forName("android.hardware.input.IInputManager$Stub")
                .getDeclaredField("TRANSACTION_injectInputEvent");
            transaction.setAccessible(true);
            int id = transaction.getInt(null);
            if (id <= 0 || id > 1000) throw new IllegalStateException("Input Binder contract");
            event.writeToParcel(parcel, 0);
            int bytes = parcel.dataSize();
            if (bytes > 128 || bytes % 4 != 0) throw new IllegalStateException("Key parcel size");
            parcel.setDataPosition(0);
            // AIDL: non-null InputEvent, its own parcel representation, async mode.
            StringBuilder command = new StringBuilder("/system/bin/service call input ")
                .append(id).append(" i32 1");
            while (parcel.dataPosition() < bytes) command.append(" i32 ").append(parcel.readInt());
            command.append(" i32 0");
            process = new ProcessBuilder("/system/bin/leo-su", "-c", command.toString())
                .redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) throw new IllegalStateException("Input timeout");
            byte[] reply = new byte[512];
            int length = process.getInputStream().read(reply);
            String result = length > 0 ? new String(reply, 0, length, "UTF-8") : "";
            // service reports transport errors on stdout, often with exit code zero.
            if (process.exitValue() != 0 || !result.contains("00000000 00000001"))
                throw new IllegalStateException("Input was not accepted: " + result);
        } catch (Exception failure) {
            available = false;
            Log.w("KeyShellJavaKeys", "Physical softkey bridge unavailable", failure);
        } finally {
            parcel.recycle();
            if (process != null) {
                process.destroy();
                try { process.getInputStream().close(); process.getOutputStream().close(); }
                catch (java.io.IOException ignored) { }
            }
        }
    }

    void close() { game = false; awake = false; work.shutdown(); }
}
