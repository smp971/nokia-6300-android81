package ro.leo.visual;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import ro.leo.i18n.L;

/** Native, wrapping option list controlled by D-pad, softkeys and optional digits. */
final class KeypadDialog extends Dialog {
    interface Action { void select(int index); }
    private static java.lang.ref.WeakReference<KeypadDialog> active = new java.lang.ref.WeakReference<>(null);
    static KeypadDialog current() {
        KeypadDialog dialog = active.get();
        return dialog != null && dialog.isShowing() ? dialog : null;
    }
    private final String[] items;
    private final Action action;
    private final Appearance.Palette palette;
    private final View content;
    private final ListView list;
    private final TextView position;
    private final boolean numbered;
    private int selected, digitPressed = -1;
    private boolean centerPressed, backPressed;
    private Runnable backAction;

    KeypadDialog(Context context, String title, String[] items, Action action) {
        this(context, title, items, action, false);
    }

    KeypadDialog(Context context, String title, String[] items, Action action, boolean numbered) {
        this(context, title, items, action, numbered, Appearance.palette(context));
    }

    KeypadDialog(Context context, String title, String[] items, Action action, boolean numbered,
                 Appearance.Palette palette) {
        super(context);
        this.items = items;
        this.action = action;
        this.numbered = numbered;
        this.palette = palette;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        content = root;

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(3), dp(8), dp(3));
        header.setBackgroundColor(palette.bar);
        TextView heading = label(title, 22, Color.WHITE);
        heading.setTypeface(UiChrome.TITLE);
        heading.setMaxLines(2);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        position = label("", 12, palette.secondary());
        position.setPadding(dp(5), 0, 0, 0);
        header.addView(position);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        list = new ListView(context);
        list.setFocusableInTouchMode(true);
        list.setPadding(dp(6), 0, dp(6), 0);
        list.setDivider(new ColorDrawable(palette.divider()));
        list.setDividerHeight(dp(1));
        list.setSelector(android.R.color.transparent);
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return items.length; }
            public Object getItem(int index) { return items[index]; }
            public long getItemId(int index) { return index; }
            public View getView(int index, View reuse, ViewGroup parent) {
                TextView row;
                if (reuse instanceof TextView) row = (TextView) reuse;
                else {
                    row = label("", 19, Color.WHITE);
                    UiChrome.listRow(getContext(), row, palette);
                    row.setMaxLines(2);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                }
                row.setText((numbered && index < 9 ? (index + 1) + "  " : "") + items[index]);
                row.setSelected(index == selected);
                return row;
            }
        });
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View row, int index, long id) {
                select(index);
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View row, int index, long id) {
                select(index);
                dismiss();
                action.select(index);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(context);
        footer.setBackground(UiChrome.panel(palette,
                context.getResources().getDisplayMetrics().density, true));
        String[] captions = {numbered && items.length > 0 ? "1–" + Math.min(9, items.length) : "",
                items.length == 0 ? "" : L.t("Alege"), L.t("Înapoi")};
        for (int i = 0; i < captions.length; i++) {
            TextView label = label(captions[i], 12, i == 1 ? palette.accent : Color.WHITE);
            label.setSingleLine(true);
            label.setPadding(dp(5), 0, dp(5), 0);
            label.setGravity(Gravity.CENTER_VERTICAL
                    | (i == 0 ? Gravity.LEFT : i == 1 ? Gravity.CENTER_HORIZONTAL : Gravity.RIGHT));
            footer.addView(label, new LinearLayout.LayoutParams(0, dp(UiChrome.FOOTER_DP), 1));
        }
        root.addView(footer);
        updatePosition();
        setContentView(content);
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size, int color) {
        TextView label = new TextView(getContext());
        label.setText(value);
        label.setTextSize(size * Appearance.textScale(getContext()));
        label.setTextColor(color);
        label.setEllipsize(TextUtils.TruncateAt.END);
        return label;
    }

    private void select(int index) {
        int previous = selected;
        selected = index;
        View old = list.getChildAt(previous - list.getFirstVisiblePosition());
        if (old != null) old.setSelected(false);
        View row = list.getChildAt(selected - list.getFirstVisiblePosition());
        if (row != null) row.setSelected(true);
        updatePosition();
    }

    private void updatePosition() {
        position.setText((items.length == 0 ? 0 : selected + 1) + " / " + items.length);
    }

    KeypadDialog onBack(Runnable action) { backAction = action; return this; }

    KeypadDialog initialSelection(int index) {
        selected = Math.max(0, Math.min(index, items.length - 1));
        updatePosition();
        return this;
    }

    public void show() {
        super.show();
        active = new java.lang.ref.WeakReference<>(this);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(palette.background));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(palette.bar);
        window.setNavigationBarColor(palette.bar);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(SystemChrome.darkFlags(decor.getSystemUiVisibility()));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.windowAnimations = 0;
        window.setAttributes(attributes);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        list.requestFocus();
        list.setSelection(selected);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (LeoActivity.isJackNavigationNoise(event)) return true;
        int key = event.getKeyCode();
        boolean digit = numbered && key >= KeyEvent.KEYCODE_1 && key <= KeyEvent.KEYCODE_9;
        boolean navigation = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU;
        if (digit || navigation) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) return onKeyDown(key, event);
            if (event.getAction() == KeyEvent.ACTION_UP) return onKeyUp(key, event);
        }
        return super.dispatchKeyEvent(event);
    }

    public boolean onKeyDown(int key, KeyEvent event) {
        if (numbered && key >= KeyEvent.KEYCODE_1 && key <= KeyEvent.KEYCODE_9) {
            int index = key - KeyEvent.KEYCODE_1;
            if (event.getRepeatCount() == 0 && index < items.length) {
                digitPressed = key;
                select(index);
                list.setSelection(selected);
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (items.length > 0) {
                select(CircularNavigation.step(selected, key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1, items.length));
                list.setSelection(selected);
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0) centerPressed = true;
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            if (event.getRepeatCount() == 0) backPressed = true;
            return true;
        }
        if (key == KeyEvent.KEYCODE_MENU) return true;
        return super.onKeyDown(key, event);
    }

    public boolean onKeyUp(int key, KeyEvent event) {
        if (numbered && key >= KeyEvent.KEYCODE_1 && key <= KeyEvent.KEYCODE_9) {
            boolean accept = digitPressed == key && !event.isCanceled();
            digitPressed = -1;
            if (accept) { dismiss(); action.select(key - KeyEvent.KEYCODE_1); }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            boolean accept = centerPressed && !event.isCanceled();
            centerPressed = false;
            if (accept && items.length > 0) { dismiss(); action.select(selected); }
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            boolean close = backPressed && !event.isCanceled();
            backPressed = false;
            if (close) { dismiss(); if (backAction != null) backAction.run(); }
            return true;
        }
        if (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_DPAD_UP
                || key == KeyEvent.KEYCODE_DPAD_DOWN) return true;
        return super.onKeyUp(key, event);
    }
}
