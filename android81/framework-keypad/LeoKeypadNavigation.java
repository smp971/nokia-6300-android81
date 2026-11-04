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
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
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
        int sliderKey = -1;
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
        boolean horizontal = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (code != KeyEvent.KEYCODE_DPAD_DOWN && code != KeyEvent.KEYCODE_DPAD_UP
                && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER && !horizontal) {
            return false;
        }
        if (state == null) {
            state = new State();
            STATES.put(root, state);
        }
        if (event.getAction() == KeyEvent.ACTION_UP && state.consumed == code) {
            View selected = state.selected.get();
            if (state.sliderKey == code && selected instanceof android.widget.SeekBar)
                selected.dispatchKeyEvent(event);
            state.sliderKey = -1;
            state.consumed = -1;
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        View focused = root.findFocus();
        if (nativeKeys(focused, root)) return false;
        View slider = state.selected.get();
        if (!(slider instanceof android.widget.SeekBar)) slider = focused;
        if (horizontal && slider instanceof android.widget.SeekBar && slider.isShown()
                && slider.isEnabled() && slider.getRootView() == root.getRootView()) {
            if (slider.dispatchKeyEvent(event)) {
                state.selected = new WeakReference<>(slider);
                state.consumed = state.sliderKey = code;
                return true;
            }
            return false;
        }
        ArrayList<View> actions = new ArrayList<>();
        collect(root, actions, new int[]{0});
        if (actions.isEmpty()) {
            if (scroll(root, code, new int[]{0})) { state.consumed = code; return true; }
            return false;
        }
        View previous = state.selected.get();
        // The outline is the keypad selection. Native focus may move later in
        // a traversal even when requestFocus() was refused by a container.
        int at = actions.indexOf(previous);
        if (at < 0) at = actions.indexOf(focused);
        boolean activate = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (horizontal) {
            View current = at < 0 ? null : actions.get(at);
            View next = horizontalTarget(current, actions, code == KeyEvent.KEYCODE_DPAD_RIGHT);
            if (next == null) {
                if (scroll(root, code, new int[]{0})) { state.consumed = code; return true; }
                return false;
            }
            select(state, next);
            state.consumed = code;
            return true;
        }
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
        select(state, target);
        state.consumed = code;
        return true;
    }

    private static void select(State state, View target) {
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
    }

    /** Move within a horizontal button/tab row, leaving custom app gestures alone. */
    private static View horizontalTarget(View current, ArrayList<View> actions, boolean right) {
        if (current == null) return null;
        Rect from = new Rect();
        if (!current.getGlobalVisibleRect(from)) return null;
        View best = null; int distance = Integer.MAX_VALUE;
        for (View candidate : actions) {
            if (candidate == current || candidate instanceof EditText || candidate instanceof AdapterView) continue;
            Rect to = new Rect();
            if (!candidate.getGlobalVisibleRect(to) || Math.min(from.bottom,to.bottom)<=Math.max(from.top,to.top)) continue;
            int delta = to.centerX()-from.centerX();
            if ((right ? delta>0 : delta<0) && Math.abs(delta)<distance) {
                best=candidate;distance=Math.abs(delta);
            }
        }
        return best;
    }

    /** Read-only scrollable dialogs also need keypad access, even without buttons. */
    private static boolean scroll(View view, int code, int[] visited) {
        if (++visited[0]>MAX_VISITS || !view.isShown() || !view.isEnabled()) return false;
        boolean vertical=code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN;
        boolean horizontal=code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT;
        int direction=(code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_LEFT)?-1:1;
        if (vertical && view instanceof ScrollView && view.canScrollVertically(direction)) {
            view.scrollBy(0,direction*Math.max(1,view.getHeight()*2/3));return true;
        }
        if (horizontal && view instanceof HorizontalScrollView && view.canScrollHorizontally(direction)) {
            view.scrollBy(direction*Math.max(1,view.getWidth()*2/3),0);return true;
        }
        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            ViewGroup group=(ViewGroup)view;
            for (int n=0;n<group.getChildCount();n++) if(scroll(group.getChildAt(n),code,visited))return true;
        }
        return false;
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
                if (!bounds.isEmpty() && bounds.equals(other)) { duplicate = true; break; }
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
