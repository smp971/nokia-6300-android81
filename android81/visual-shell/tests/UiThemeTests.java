package ro.leo.visual;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Regression coverage for theme inheritance and returning to an open page. */
public final class UiThemeTests extends Instrumentation {
    private LeoActivity page;
    private Activity cover;
    private ListView list;
    private EditText draft;
    private int checks;
    private final String token = "ui-theme-checks-" + java.util.UUID.randomUUID();

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private void ui(final Runnable action) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() {
            public void run() {
                try { action.run(); }
                catch (Throwable error) { failure[0] = error; }
            }
        });
        if (failure[0] != null) throw new RuntimeException(failure[0]);
    }

    private class PaletteContext extends ContextWrapper {
        PaletteContext(Context context) { super(context); }
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences(token + "-" + name, mode);
        }
    }
    private final class MobileSettingsActivity extends PaletteContext {
        MobileSettingsActivity(Context c) { super(c); }
    }
    private final class WallpaperActivity extends PaletteContext {
        WallpaperActivity(Context c) { super(c); }
    }

    private void inheritance(Context context) {
        PaletteContext settings = new MobileSettingsActivity(context);
        SharedPreferences preferences = settings.getSharedPreferences("appearance", 0);
        preferences.edit().putInt("theme", 17).putInt("app-theme-SettingsActivity", 11).commit();
        check(Appearance.palette(settings).accent == Appearance.COLORS[11][2],
                "settings subpage inherits the selected Settings theme");
        preferences.edit().putInt("app-theme-MobileSettingsActivity", 12).commit();
        check(Appearance.palette(settings).accent == Appearance.COLORS[12][2],
                "existing explicit subpage preference takes precedence");
        check(Appearance.palette(new WallpaperActivity(context)).accent == Appearance.COLORS[11][2],
                "personalization subpage inherits through its parent");
        preferences.edit().clear().commit();
    }

    private void coverPage() {
        cover = startActivitySync(new Intent(getTargetContext(), ThemePacksActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitWindow(cover);
    }

    private void uncover() {
        final Activity current = cover;
        ui(new Runnable() { public void run() { current.finish(); } });
        cover = null;
        awaitWindow(page);
    }

    private void awaitWindow(final Activity activity) {
        final boolean[] focused = {false};
        long deadline = android.os.SystemClock.uptimeMillis() + 4000;
        do {
            ui(new Runnable() { public void run() { focused[0] = activity.hasWindowFocus(); } });
            if (focused[0]) break;
            android.os.SystemClock.sleep(25);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        check(focused[0], "returned activity receives window focus");
        waitForIdleSync();
    }

    private int pixel(Drawable drawable, boolean selected) {
        Bitmap image = Bitmap.createBitmap(100, 48, Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, 100, 48);
            drawable.setState(selected ? new int[]{android.R.attr.state_selected} : new int[0]);
            drawable.draw(new Canvas(image));
            return image.getPixel(50, 24);
        } finally { image.recycle(); }
    }

    private void restore(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }
        if (!editor.commit()) throw new IllegalStateException("Appearance restoration failed");
    }

    public void onCreate(Bundle args) { start(); }

    public void onStart() {
        final Context context = getTargetContext();
        final SharedPreferences appearance = context.getSharedPreferences("appearance", 0);
        final Map<String, ?> before = new HashMap<String, Object>(appearance.getAll());
        Bundle result = new Bundle();
        try {
            inheritance(context);
            appearance.edit().putInt("theme", 17).putInt("accent", -1)
                    .remove("app-theme-HelpActivity").commit();
            setInTouchMode(false);
            page = (LeoActivity) startActivitySync(new Intent(context, HelpActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(new Runnable() {
                public void run() {
                    page.list("Theme checks", Arrays.asList("First", "Second\nSubtitle", "Third"),
                            1, new LeoActivity.Choice() { public void pick(int index) {} }, "", null);
                    for (int i = 0; i < page.root.getChildCount(); i++) {
                        if (page.root.getChildAt(i) instanceof ListView)
                            list = (ListView) page.root.getChildAt(i);
                    }
                }
            });
            waitForIdleSync();
            check(list != null, "shared list is visible");
            final View original = page.root;
            coverPage();
            appearance.edit().putInt("theme", 12).commit();
            uncover();
            ui(new Runnable() {
                public void run() {
                    check(page.root == original, "returning keeps the existing page");
                    check(((ColorDrawable) page.root.getBackground()).getColor() == page.palette.background,
                            "open page receives the new background");
                    check(list.getSelectedItemPosition() == 1, "theme change retains list selection");
                    TextView row = (TextView) list.getChildAt(1 - list.getFirstVisiblePosition());
                    check(row != null && row.isSelected(), "selected row remains visible, list focus="
                            + list.hasFocus() + ", touch mode=" + list.isInTouchMode());
                    check(pixel(row.getBackground(), true) == page.palette.selection,
                            "recycled rows receive the new selected surface");
                    check(row.getCurrentTextColor() == page.palette.ink,
                            "selected text matches its surface");
                    LinearLayout fields = page.fields("Draft check");
                    draft = page.field(fields, "Text", "Unsaved keypad draft", InputType.TYPE_CLASS_TEXT);
                    page.footer("", "", "Back");
                    draft.requestFocus();
                    draft.setSelection(4, 10);
                }
            });
            coverPage();
            appearance.edit().putInt("theme", 11).commit();
            uncover();
            ui(new Runnable() {
                public void run() {
                    check(draft.getText().toString().equals("Unsaved keypad draft"),
                            "returning preserves unsaved form text");
                    check(draft.getSelectionStart() == 4 && draft.getSelectionEnd() == 10,
                            "returning preserves the editor selection");
                    check(draft.getCurrentHintTextColor() == page.palette.secondary(),
                            "existing editors receive theme hint colors");
                    check(draft.hasFocus(), "editor retains keypad focus");
                    for (int[] colors : Appearance.COLORS) {
                        Appearance.Palette palette = new Appearance.Palette(colors);
                        Drawable surface = UiChrome.row(palette, 0, 1);
                        check(pixel(surface, false) == palette.background,
                                "unselected surface follows each palette");
                        check(pixel(surface, true) == palette.selection,
                                "selected surface follows each palette");
                        check(Color.luminance(Color.WHITE) > Color.luminance(palette.background),
                                "body text remains visible");
                    }
                }
            });
            result.putString("result", "PASS " + checks + " UI theme checks");
        } catch (Throwable failure) {
            result.putString("error", android.util.Log.getStackTraceString(failure));
        } finally {
            try {
                if (cover != null) {
                    final Activity current = cover;
                    ui(new Runnable() { public void run() { current.finish(); } });
                }
                if (page != null) ui(new Runnable() { public void run() { page.finish(); } });
                restore(appearance, before);
                context.deleteSharedPreferences(token + "-appearance");
                context.deleteSharedPreferences(token + "-accessibility");
            } catch (Throwable failure) {
                result.putString("restore_error", android.util.Log.getStackTraceString(failure));
            }
        }
        finish(result.containsKey("error") || result.containsKey("restore_error")
                ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
}
