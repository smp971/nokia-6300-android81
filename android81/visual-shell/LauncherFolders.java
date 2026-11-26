package ro.leo.visual;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.Map;
import ro.leo.i18n.L;

/** Launcher-only projection. Catalog IDs, shortcuts and the legacy app order stay flat. */
final class LauncherFolders {
    static final String PREFERENCES = "launcher-folders";
    static final int ROOT = -1;
    static final int FIRST = 1000;
    static final int COUNT = 5;
    private static final String[] NAMES = {"Unelte", "Organizare", "Media", "Internet", "Avansate"};
    private static final int[][] DEFAULTS = {
        {4, 13, 14, 35, 44, 46, 47}, {10, 11, 42, 43, 45},
        {12, 30, 39, 40}, {22, 37, 38, 48}, {23, 41, 49}
    };

    static boolean isFolder(int entry) { return entry >= FIRST && entry < FIRST + COUNT; }
    static String label(int entry) {
        if (isFolder(entry)) return L.t(NAMES[entry - FIRST]);
        return entry >= 0 && entry < AppCatalog.NAMES.length ? AppCatalog.NAMES[entry] : "";
    }
    static boolean enabled(Context c) {
        return !ShellMode.simple(c) && c.getSharedPreferences(PREFERENCES, 0).getBoolean("enabled", true);
    }
    static int defaultFolder(int app) {
        for (int folder = 0; folder < COUNT; folder++)
            for (int member : DEFAULTS[folder]) if (app == member) return folder;
        return ROOT;
    }
    static int folder(Context c, int app) {
        return folder(c.getSharedPreferences(PREFERENCES, 0).getAll(), app);
    }
    private static int folder(Map<String, ?> saved, int app) {
        if (app < 0 || app >= AppCatalog.NAMES.length) return ROOT;
        Object value = saved.get("app" + app);
        if (value instanceof Integer && (Integer)value >= ROOT && (Integer)value < COUNT)
            return (Integer)value;
        return defaultFolder(app);
    }
    static void move(Context c, int app, int folder) {
        if (app < 0 || app >= AppCatalog.NAMES.length || AppCatalog.settingsOnly(app)
                || folder < ROOT || folder >= COUNT) throw new IllegalArgumentException("Invalid launcher move");
        SharedPreferences.Editor edit = c.getSharedPreferences(PREFERENCES, 0).edit();
        if (folder == defaultFolder(app)) edit.remove("app" + app);
        else edit.putInt("app" + app, folder);
        edit.apply();
    }
    static int[] entries(Context c, int folder) {
        int[] apps = AppCatalog.menuOrder(c);
        if (!enabled(c)) return folder == ROOT ? apps : new int[0];
        int[] result = new int[apps.length];
        Map<String, ?> saved = c.getSharedPreferences(PREFERENCES, 0).getAll();
        boolean[] added = new boolean[COUNT];
        int size = 0;
        for (int app : apps) {
            int parent = folder(saved, app);
            if (folder != ROOT) {
                if (parent == folder) result[size++] = app;
            } else if (parent == ROOT) result[size++] = app;
            else if (!added[parent]) {
                result[size++] = FIRST + parent;
                added[parent] = true;
            }
        }
        return Arrays.copyOf(result, size);
    }
}
