package ro.leo.systemtests;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import com.android.internal.policy.LeoKeypadNavigation;

/** The same framework helper is tested before boot-classpath deployment. */
public final class KeypadFrameworkTests extends Instrumentation {
    KeypadFixtureActivity page;
    int checks;
    void check(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
        checks++;
    }
    boolean key(int key, int action, int repeats) {
        return LeoKeypadNavigation.navigate(page.root,
                new KeyEvent(0, 0, action, key, repeats));
    }
    public void onCreate(Bundle args) { start(); }
    public void onStart() {
        final Bundle result = new Bundle();
        try {
            page = (KeypadFixtureActivity) startActivitySync(new Intent(getTargetContext(),
                    KeypadFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(new Runnable() {
                public void run() {
                    page.root.requestFocus();
                    check(key(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0), "Down handled");
                    check(page.first.hasFocus(), "first actionable view selected");
                    check(key(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP, 0), "paired Up consumed");
                    key(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0);
                    key(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP, 0);
                    check(page.second.hasFocus(), "next button selected");
                    key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0);
                    key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 1);
                    key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, 0);
                    check(page.clicks == 1, "OK activates once, repeats suppressed");
                    key(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN, 0);
                    key(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_UP, 0);
                    check(page.first.hasFocus(), "Up selects previous control");
                    page.editor.requestFocus();
                    check(!key(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0), "editor retains arrows");
                    check(!key(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN, 0), "editor retains Enter");
                    check("Fixture text".equals(page.editor.getText().toString()), "text unchanged");
                    page.list.requestFocus();
                    check(!key(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0), "native list retains arrows");
                    check(!key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, 0), "native list retains selection");
                    page.root.requestFocus();
                    check(!key(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0), "Back unchanged");
                    check(!key(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0), "volume unchanged");
                    check(!key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN, 0), "call key unchanged");
                    check(!LeoKeypadNavigation.dispatch(page.root,
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)),
                            "ordinary app excluded from automatic framework navigation");
                }
            });
            runOnMainSync(new Runnable() { public void run() {
                page.root = new android.widget.LinearLayout(page);
                page.root.setOrientation(1);
                page.root.setFocusableInTouchMode(true);
                android.widget.FrameLayout blocked = new android.widget.FrameLayout(page);
                blocked.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                android.widget.Button hiddenFocus = new android.widget.Button(page);
                hiddenFocus.setText("Clickable but parent blocks native focus");
                hiddenFocus.setOnClickListener(new View.OnClickListener(){public void onClick(View v){page.clicks++;}});
                blocked.addView(hiddenFocus,new ViewGroup.LayoutParams(-1,48));
                page.second = new android.widget.Button(page);
                page.second.setText("Next action");
                page.root.addView(blocked,new ViewGroup.LayoutParams(-1,48));
                page.root.addView(page.second,new ViewGroup.LayoutParams(-1,48));
                page.setContentView(page.root);
                page.root.requestFocus();
            }});
            waitForIdleSync();
            runOnMainSync(new Runnable(){public void run(){
                int before=page.clicks;
                key(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_DOWN,0);
                key(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_UP,0);
                key(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0);
                key(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,0);
                check(page.clicks==before+1,"clickable control works despite blocked native focus");
                key(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_DOWN,0);
                key(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_UP,0);
                check(page.second.hasFocus(),"next key escapes a control that refused native focus");
            }});
            result.putString("result", "PASS " + checks + " framework keypad checks");
        } catch (Throwable failure) {
            result.putString("error", android.util.Log.getStackTraceString(failure));
        } finally {
            if (page != null) runOnMainSync(new Runnable() {
                public void run() { page.finish(); }
            });
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
}
