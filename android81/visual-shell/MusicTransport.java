package ro.leo.visual;

import android.view.KeyEvent;

/** A tap moves between tracks. A held arrow seeks without changing tracks on release. */
final class MusicTransport {
    private int held;
    private boolean sought;

    boolean arrow(KeyEvent event, PlaybackService service) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        int direction = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                held = key;
                sought = false;
            } else if (held == key) {
                sought = true;
                if (service != null && event.getRepeatCount() % 3 == 1) service.seek(10000 * direction);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && held == key) {
            if (!sought && !event.isCanceled() && service != null) service.next(direction > 0);
            reset();
        }
        return true;
    }

    void reset() {
        held = 0;
        sought = false;
    }
}
