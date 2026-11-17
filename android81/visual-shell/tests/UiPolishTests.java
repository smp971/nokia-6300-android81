package ro.leo.visual;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Native dialog, form and clock routing checks without alarms or external actions. */
public final class UiPolishTests extends Instrumentation {
    private LeoActivity page;
    private int checks;
    private final int[] chosen = {-1, 0};

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private void ui(final Runnable action) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() {
            public void run() { try { action.run(); } catch (Throwable error) { failure[0] = error; } }
        });
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }

    private void awaitFocus(final View root) {
        final boolean[] focused = {false};
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            ui(new Runnable() { public void run() {
                focused[0] = root.hasWindowFocus() && root.isLaidOut()
                        && root.getWidth() > 0 && root.getHeight() > 0;
            } });
            if (focused[0]) break;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        check(focused[0], "window receives focus, view=" + root + ", attached="
                + root.isAttachedToWindow() + ", shown=" + root.isShown()
                + ", window=" + root.hasWindowFocus() + ", root=" + root.getRootView()
                + ", finishing=" + (page != null && page.isFinishing()));
        waitForIdleSync();
    }

    private ListView list(View view) {
        if (view instanceof ListView) return (ListView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ListView found = list(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void key(final KeypadDialog dialog, final int action, final int key, final int flags) {
        ui(new Runnable() { public void run() {
            long now = SystemClock.uptimeMillis();
            dialog.dispatchKeyEvent(new KeyEvent(now, now, action, key, 0, 0, 0, 0, flags));
        }});
        waitForIdleSync();
    }

    private void release(KeypadDialog dialog, int key) {
        key(dialog, KeyEvent.ACTION_DOWN, key, 0);
        key(dialog, KeyEvent.ACTION_UP, key, 0);
    }

    private void dialogChecks() {
        final KeypadDialog[] dialog = {null};
        ui(new Runnable() { public void run() {
            dialog[0] = new KeypadDialog(page, "Long keypad options",
                    new String[]{"An option long enough to wrap across two visible lines on a keypad display",
                            "Second option", "Third option"}, new KeypadDialog.Action() {
                public void select(int index) { chosen[0] = index; chosen[1]++; }
            }, true).initialSelection(0);
            dialog[0].show();
        }});
        awaitFocus(dialog[0].getWindow().getDecorView());
        ui(new Runnable() { public void run() {
            ListView options = list(dialog[0].getWindow().getDecorView());
            check(options != null && options.getCount() == 3, "all dialog choices remain accessible");
            TextView first = (TextView) options.getChildAt(0);
            check(first != null && first.getLineCount() == 2, "long choice wraps onto two lines");
            check(options.getBottom() <= options.getRootView().getHeight(), "choices fit inside the window");
        }});
        key(dialog[0], KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_2, 0);
        check(chosen[1] == 0, "number press waits for release");
        key(dialog[0], KeyEvent.ACTION_UP, KeyEvent.KEYCODE_2, 0);
        check(chosen[0] == 1 && chosen[1] == 1, "number release accepts exactly one correct option");
        check(KeypadDialog.current() == null, "accepted dialog stops intercepting the caller");

        ui(new Runnable() { public void run() {
            dialog[0] = new KeypadDialog(page, "Cancel check", new String[]{"Keep"},
                    new KeypadDialog.Action() { public void select(int index) { chosen[1]++; } });
            dialog[0].show();
        }});
        awaitFocus(dialog[0].getWindow().getDecorView());
        key(dialog[0], KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0);
        key(dialog[0], KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.FLAG_CANCELED);
        check(chosen[1] == 1 && dialog[0].isShowing(), "cancelled OK leaves the action untouched");
        release(dialog[0], KeyEvent.KEYCODE_BACK);
        check(!dialog[0].isShowing(), "Back dismisses the option page");

        ui(new Runnable() { public void run() {
            dialog[0] = new KeypadDialog(page, "Empty", new String[0],
                    new KeypadDialog.Action() { public void select(int index) { chosen[1]++; } });
            dialog[0].show();
        }});
        awaitFocus(dialog[0].getWindow().getDecorView());
        release(dialog[0], KeyEvent.KEYCODE_DPAD_CENTER);
        check(dialog[0].isShowing() && chosen[1] == 1, "empty chooser cannot execute an action");
        release(dialog[0], KeyEvent.KEYCODE_BACK);
    }

    private void formChecks() {
        final EditText[] field = {null};
        final KeypadSpinner[] spinner = {null};
        ui(new Runnable() { public void run() {
            LinearLayout fields = page.fields("Form checks");
            field[0] = page.field(fields, "Draft", "Retained draft", InputType.TYPE_CLASS_TEXT);
            spinner[0] = new KeypadSpinner(page);
            spinner[0].setAdapter(new ArrayAdapter<String>(page,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"Mobile", "Work"}));
            fields.addView(spinner[0]);
            page.footer("", "", "Back");
            field[0].requestFocus();
        }});
        waitForIdleSync();
        awaitFocus(field[0]);
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForIdleSync();
        check(spinner[0].hasFocus(), "Down moves from a text field to its selector, focused="
                + page.getCurrentFocus() + ", touch mode=" + field[0].isInTouchMode());
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        KeypadDialog opened;
        do {
            opened = KeypadDialog.current();
            if (opened != null) break;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        final KeypadDialog chooser = opened;
        check(chooser != null, "form selector opens the shared chooser");
        awaitFocus(chooser.getWindow().getDecorView());
        release(chooser, KeyEvent.KEYCODE_DPAD_DOWN);
        release(chooser, KeyEvent.KEYCODE_DPAD_CENTER);
        ui(new Runnable() { public void run() {
            check(spinner[0].getSelectedItemPosition() == 1, "selector stores the chosen value");
            check(spinner[0].hasFocus(), "focus returns to the form selector");
            check(field[0].getText().toString().equals("Retained draft"), "choosing preserves the form draft");
        }});
    }

    private void close() {
        if (page == null) return;
        ui(new Runnable() { public void run() { page.finish(); } });
        page = null;
        waitForIdleSync();
    }

    private void clockChecks() {
        page = (LeoActivity) startActivitySync(new Intent(getTargetContext(), ClockActivity.class)
                .putExtra("tab", 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitFocus(page.getWindow().getDecorView());
        for (int expected = 0; expected < 5; expected++) {
            final int tab = expected;
            ui(new Runnable() { public void run() {
                LinearLayout tabs = (LinearLayout) page.root.getChildAt(1);
                check(tabs.getChildCount() == 3, "clock shows the active tab and both neighbours");
                check(((TextView) tabs.getChildAt(1)).isSelected(), "active clock tab is marked");
                String[] labels = {ro.leo.i18n.L.t("Ora"), ro.leo.i18n.L.t("Alarme"),
                        ro.leo.i18n.L.t("Glob"), "Timer", ro.leo.i18n.L.t("Crono")};
                check(((TextView) tabs.getChildAt(1)).getText().toString().equals(labels[tab]),
                        "Right reaches the corresponding clock page");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            awaitFocus(page.root);
        }
        ui(new Runnable() { public void run() {
            LinearLayout tabs = (LinearLayout) page.root.getChildAt(1);
            check(((TextView) tabs.getChildAt(1)).getText().toString().equals(ro.leo.i18n.L.t("Ora")),
                    "clock tabs wrap back to the first page");
        }});
        close();
    }

    private void androidAppsChecks() {
        page = (LeoActivity) startActivitySync(new Intent(getTargetContext(), AndroidAppsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitFocus(page.getWindow().getDecorView());
        final ListView[] apps = {null};
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            ui(new Runnable() { public void run() {
                apps[0] = list(page.root);
                if (apps[0] != null && !apps[0].isLaidOut()) apps[0] = null;
            } });
            if (apps[0] != null) break;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        check(apps[0] != null && apps[0].getCount() > 0, "Android apps load into the shared native list");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForIdleSync();
        check(apps[0].getSelectedItemPosition() == 1, "D-pad selects the next installed application");
        final ListView previous = apps[0];
        final Activity cover = startActivitySync(new Intent(getTargetContext(), HelpActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitFocus(cover.getWindow().getDecorView());
        ui(new Runnable() { public void run() { cover.finish(); } });
        deadline = SystemClock.uptimeMillis() + 10000;
        do {
            ui(new Runnable() { public void run() {
                apps[0] = list(page.root);
                if (apps[0] == previous || apps[0] != null && !apps[0].isLaidOut()) apps[0] = null;
            } });
            if (apps[0] != null) break;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        check(apps[0] != null && apps[0].getSelectedItemPosition() == 1,
                "returning to Android apps retains the selected component");
        ui(new Runnable() { public void run() {
            previous.getOnItemSelectedListener().onItemSelected(previous, null, 999, 999);
            page.left.run();
            check(KeypadDialog.current() != null, "stale list callbacks cannot corrupt the current app menu");
        } });
        release(KeypadDialog.current(), KeyEvent.KEYCODE_BACK);
        close();
    }

    private void restore(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }
        if (!editor.commit()) throw new IllegalStateException("Clock restoration failed");
    }

    public void onCreate(Bundle args) { start(); }
    public void onStart() {
        android.os.PowerManager power=(android.os.PowerManager)getTargetContext()
                .getSystemService(Context.POWER_SERVICE);
        android.os.PowerManager.WakeLock display=power.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,"leo:ui-polish-checks");
        display.acquire(180000);
        SharedPreferences clock = getTargetContext().getSharedPreferences("clock", 0);
        Map<String, ?> original = new HashMap<String, Object>(clock.getAll());
        Bundle result = new Bundle();
        try {
            setInTouchMode(false);
            page = (LeoActivity) startActivitySync(new Intent(getTargetContext(), HelpActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            awaitFocus(page.getWindow().getDecorView());
            dialogChecks();
            formChecks();
            close();
            clockChecks();
            androidAppsChecks();
            result.putString("result", "PASS " + checks + " UI polish checks");
        } catch (Throwable error) {
            result.putString("error", android.util.Log.getStackTraceString(error));
            try {
                android.graphics.Bitmap image=getUiAutomation(
                    android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
                try(java.io.FileOutputStream stream=new java.io.FileOutputStream(
                    new java.io.File(getTargetContext().getFilesDir(),"ui-polish-failure.png"))){
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);
                }finally{image.recycle();}
            } catch(Exception capture) { result.putString("capture_error",capture.toString()); }
        } finally {
            ui(new Runnable() { public void run() {
                KeypadDialog current = KeypadDialog.current();
                if (current != null) current.dismiss();
            }});
            close();
            restore(clock, original);
            if(display.isHeld())display.release();
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
}
