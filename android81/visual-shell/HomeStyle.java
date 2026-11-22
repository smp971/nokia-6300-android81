package ro.leo.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import ro.leo.i18n.L;

/** One presentation choice for Home and standby; never changes app themes or user transforms. */
final class HomeStyle {
    static final String KEY = "home-style";
    static final String[] NAMES = {"Clasic colorat", "Minimal", "Contrast mare"};
    static int index(Context context) {
        return Math.max(0, Math.min(2, context.getSharedPreferences("appearance", 0).getInt(KEY, 0)));
    }
    static boolean contrast(Context context) { return index(context) == 2 || AccessibilityPrefs.contrast(context); }
    static Appearance.Palette palette(Context context, Appearance.Palette base) {
        return contrast(context) ? new Appearance.Palette(Color.BLACK, Color.BLACK, 0xff151515,
            Color.YELLOW, Color.WHITE, Color.BLACK) : base;
    }
    static float scale(Context context) { return Math.max(Appearance.textScale(context), contrast(context) ? 1.15f : 1f); }
    static String badge(int count) { return count <= 0 ? "" : count > 99 ? "99+" : Integer.toString(count); }
    static int onAccent(int color) { return Color.red(color)*299 + Color.green(color)*587 + Color.blue(color)*114 > 150000 ? Color.BLACK : Color.WHITE; }
    static void choose(final Context context, Appearance.Palette palette, final Runnable back) {
        final SharedPreferences preferences = context.getSharedPreferences("appearance", 0);
        String[] labels = new String[NAMES.length];
        for (int i=0; i<labels.length; i++) labels[i] = L.t(NAMES[i]);
        new KeypadDialog(context, L.t("Home și standby"), labels, new KeypadDialog.Action() {
            public void select(int at) { preferences.edit().putInt(KEY, at).apply(); if (back != null) back.run(); }
        }, true, palette).initialSelection(index(context)).onBack(back).show();
    }
}
