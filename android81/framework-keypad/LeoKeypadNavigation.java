package com.android.internal.policy;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.EditText;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Conservative keypad navigation for Leo system windows, not an input injector.
 * Text editors and Android adapter lists retain their native key handling.
 * @hide
 */
public final class LeoKeypadNavigation {
    private static final int MAX_VISITS = 512;
    private static final int MAX_ACTIONS = 96;
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();

    private static final class State {
        WeakReference<View> selected = new WeakReference<>(null);
        GradientDrawable outline;
        int consumed = -1;
    }

    private LeoKeypadNavigation() {}

    public static boolean enabled(Context context) {
        if (!"leo".equals(Build.DEVICE)) return false;
        try {
            String value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, "persist.leo.keypad_ui", "1");
            return "1".equals(value);
        } catch (Exception unavailable) {
            return false;
        }
    }

    public static boolean dispatch(View root, KeyEvent event) {
        String name = root.getContext().getPackageName();
        if (!("com.android.settings".equals(name) || "com.android.systemui".equals(name)
                || "com.android.packageinstaller".equals(name)
                || "android".equals(name)) || !enabled(root.getContext())) return false;
        // SystemUI checks its actual panel state before dispatching. Android dialogs
        // retain the WindowManager keyguard check; ordinary Settings windows keep
        // their normal WindowManager security and do not act as an unlock mechanism.
        if ("android".equals(name)) {
            KeyguardManager guard = (KeyguardManager)
                    root.getContext().getSystemService(Context.KEYGUARD_SERVICE);
            if (guard != null && guard.isKeyguardLocked()) return false;
        }
        return navigate(root, event);
    }

    /** Exposed internally so the exact helper can be exercised before system installation. */
    public static boolean navigate(View root, KeyEvent event) {
        int code = event.getKeyCode();
        State state = STATES.get(root);
        if (state != null && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            View target=state.selected.get();
            if (target instanceof android.widget.SeekBar && target.isShown() && target.isEnabled()) {
                return target.dispatchKeyEvent(event);
            }
        }
        if (code != KeyEvent.KEYCODE_DPAD_DOWN && code != KeyEvent.KEYCODE_DPAD_UP
                && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) {
            return false;
        }
        if (state == null) {
            state = new State();
            STATES.put(root, state);
        }
        if (event.getAction() == KeyEvent.ACTION_UP && state.consumed == code) {
            state.consumed = -1;
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        View focused = root.findFocus();
        if (nativeKeys(focused, root)) return false;
        ArrayList<View> actions = new ArrayList<>();
        collect(root, actions, new int[]{0});
        if (actions.isEmpty()) return false;
        View previous = state.selected.get();
        // The outline is the keypad selection. Native focus may move later in
        // a traversal even when requestFocus() was refused by a container.
        int at = actions.indexOf(previous);
        if (at < 0) at = actions.indexOf(focused);
        boolean activate = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (activate && at >= 0) {
            state.consumed = code;
            if (event.getRepeatCount() == 0) actions.get(at).performClick();
            return true;
        }
        int next = at < 0 ? (code == KeyEvent.KEYCODE_DPAD_UP ? actions.size() - 1 : 0)
                : (at + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1) + actions.size()) % actions.size();
        View target = actions.get(next);
        if (target instanceof AdapterView || target instanceof EditText) {
            target.setFocusableInTouchMode(true);
            target.requestFocus();
            clear(state);
            return false;
        }
        clear(state);
        target.setFocusableInTouchMode(true);
        target.requestFocus();
        target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), true);
        GradientDrawable border = new GradientDrawable();
        border.setColor(0x00000000);
        border.setCornerRadius(4 * target.getResources().getDisplayMetrics().density);
        border.setStroke(Math.max(2, Math.round(2 * target.getResources().getDisplayMetrics().density)),
                0xff63e1c9);
        border.setBounds(0, 0, target.getWidth(), target.getHeight());
        target.getOverlay().add(border);
        state.outline = border;
        state.selected = new WeakReference<>(target);
        state.consumed = code;
        return true;
    }

    private static boolean nativeKeys(View view, View root) {
        while (view != null) {
            if (view instanceof EditText || view instanceof AdapterView) return true;
            if (view == root) break;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static void collect(View view, ArrayList<View> actions, int[] visited) {
        if (++visited[0] > MAX_VISITS || actions.size() >= MAX_ACTIONS
                || !view.isShown() || !view.isEnabled() || view.getAlpha() <= 0
                || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        if (view instanceof AdapterView || view instanceof EditText) {
            actions.add(view);
            return;
        }
        if (view.isClickable() || view instanceof android.widget.SeekBar) {
            Rect bounds = new Rect();
            view.getGlobalVisibleRect(bounds);
            boolean duplicate = false;
            for (View action : actions) {
                Rect other = new Rect();
                action.getGlobalVisibleRect(other);
                if (bounds.equals(other)) { duplicate = true; break; }
            }
            if (!duplicate) actions.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int n = 0; n < group.getChildCount(); n++) collect(group.getChildAt(n), actions, visited);
        }
    }

    private static void clear(State state) {
        View old = state.selected.get();
        if (old != null && state.outline != null) old.getOverlay().remove(state.outline);
        state.outline = null;
        state.selected.clear();
    }
}
