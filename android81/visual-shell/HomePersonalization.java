package ro.leo.visual;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import ro.leo.i18n.L;

/** Shell settings that can be changed and revisited entirely with the keypad. */
final class HomePersonalization {
    static final String RIGHT_APP_KEY = "home-right-app";
    private static final int DEFAULT_RIGHT_APP = 1;
    private static final int RIGHT_APP_ROW = 7;
    private static final String ACCENT_KEY = "accent";
    private static final String[] WIDGET_KEYS = {
        "home-compact", "home-sims", "home-unread", "home-contextual"
    };
    private static final String[] WIDGET_LABELS = {
        "Ceas compact", "Starea ambelor SIM-uri", "Puncte pentru notificări", "Carduri automate"
    };
    private static final String[] ACCENT_LABELS = {
        "Din temă", "Turcoaz", "Auriu", "Albastru", "Roz", "Lavandă", "Lime"
    };

    private final Activity activity;
    private final SharedPreferences preferences;
    private final Supplier<Appearance.Palette> palette;
    private final Runnable editLayout;
    private final Runnable back;

    HomePersonalization(Activity activity, Supplier<Appearance.Palette> palette,
                        Runnable editLayout, Runnable back) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("appearance", Context.MODE_PRIVATE);
        this.palette = palette;
        this.editLayout = editLayout;
        this.back = back;
    }

    private int accentIndex() {
        int index = preferences.getInt(ACCENT_KEY, -1);
        return index >= 0 && index < Appearance.ACCENTS.length ? index : -1;
    }

    static int rightApp(SharedPreferences preferences) {
        int app = preferences.getInt(RIGHT_APP_KEY, DEFAULT_RIGHT_APP);
        return app >= 0 && app < AppCatalog.NAMES.length ? app : DEFAULT_RIGHT_APP;
    }

    void show() {
        show(0);
    }

    private void show(int selected) {
        final int rightApp = rightApp(preferences);
        String[] rows = {
            L.t("Culoare de accent") + " · " + L.t(ACCENT_LABELS[accentIndex() + 1]),
            L.t("Aspect Home"),
            L.t("Scurtături Home"),
            L.t("Fundaluri și teme"),
            L.t("Afișare meniu") + " · " + L.t(preferences.getBoolean("list", false) ? "Listă" : "Grilă"),
            L.t("Editor Home"),
            L.t("Acces rapid Home"),
            L.t("Tasta soft dreapta") + " · " + AppCatalog.NAMES[rightApp]
        };
        dialog(L.t("Personalizare"), rows, selected, new KeypadDialog.Action() {
            public void select(int index) {
                switch (index) {
                    case 0: chooseAccent(); break;
                    case 1: widgets(0); break;
                    case 2: shortcuts(0); break;
                    case 3: activity.startActivity(new Intent(activity, WallpaperActivity.class)); break;
                    case 4: menuLayout(); break;
                    case 5: editLayout.run(); break;
                    case 6: activity.startActivity(new Intent(activity, QuickHomeActivity.class)); break;
                    case RIGHT_APP_ROW:
                        appCategories(RIGHT_APP_KEY, rightApp, AppCatalog.category(rightApp), backToHub(RIGHT_APP_ROW));
                        break;
                    default: break;
                }
            }
        }, back);
    }

    private void chooseAccent() {
        String[] labels = new String[ACCENT_LABELS.length];
        for (int index = 0; index < labels.length; index++) labels[index] = L.t(ACCENT_LABELS[index]);
        dialog(L.t("Culoare de accent"), labels, accentIndex() + 1, new KeypadDialog.Action() {
            public void select(int index) {
                preferences.edit().putInt(ACCENT_KEY, index - 1).apply();
                show(0);
            }
        }, backToHub(0));
    }

    private void widgets(int selected) {
        String[] rows = new String[WIDGET_KEYS.length];
        for (int index = 0; index < rows.length; index++) {
            boolean enabled = preferences.getBoolean(WIDGET_KEYS[index], index != 0);
            rows[index] = L.t(WIDGET_LABELS[index]) + " · " + L.t(enabled ? "Da" : "Nu");
        }
        dialog(L.t("Aspect Home"), rows, selected, new KeypadDialog.Action() {
            public void select(int index) {
                String key = WIDGET_KEYS[index];
                preferences.edit().putBoolean(key, !preferences.getBoolean(key, index != 0)).apply();
                widgets(index);
            }
        }, backToHub(1));
    }

    private void shortcuts(int selected) {
        int[] apps = AppCatalog.shortcuts(activity);
        String[] rows = new String[apps.length];
        for (int slot = 0; slot < apps.length; slot++) rows[slot] = AppCatalog.NAMES[apps[slot]];
        dialog(L.t("Scurtături Home"), rows, selected, new KeypadDialog.Action() {
            public void select(final int slot) {
                appCategories("shortcut" + slot, apps[slot], AppCatalog.category(apps[slot]), new Runnable() {
                    public void run() { shortcuts(slot); }
                });
            }
        }, backToHub(2));
    }

    private void appCategories(String key, int current, int selected, Runnable returnToParent) {
        dialog(L.t("Categorii"), AppCatalog.CATEGORIES, selected, new KeypadDialog.Action() {
            public void select(int category) { appChoices(key, current, category, returnToParent); }
        }, returnToParent);
    }

    private void appChoices(String key, int current, int category, Runnable returnToParent) {
        List<Integer> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int id : AppCatalog.order(activity)) {
            boolean visible = category == 0 ? AppCatalog.primary(id)
                : category == AppCatalog.CATEGORIES.length - 1 || AppCatalog.category(id) == category;
            if (!visible) continue;
            if (id == current) selected = ids.size();
            ids.add(id);
            labels.add(AppCatalog.NAMES[id]);
        }
        dialog(L.t("Alege aplicația"), labels.toArray(new String[0]), selected, new KeypadDialog.Action() {
            public void select(int index) {
                preferences.edit().putInt(key, ids.get(index)).apply();
                returnToParent.run();
            }
        }, new Runnable() {
            public void run() { appCategories(key, current, category, returnToParent); }
        });
    }

    private void menuLayout() {
        dialog(L.t("Afișare meniu"), new String[]{L.t("Grilă"), L.t("Listă")},
            preferences.getBoolean("list", false) ? 1 : 0, new KeypadDialog.Action() {
                public void select(int index) {
                    preferences.edit().putBoolean("list", index == 1).apply();
                    show(4);
                }
            }, backToHub(4));
    }

    private Runnable backToHub(int index) {
        return new Runnable() {
            public void run() { show(index); }
        };
    }

    private void dialog(String title, String[] rows, int selected,
                        KeypadDialog.Action action, Runnable backAction) {
        new KeypadDialog(activity, title, rows, action, true, palette.get())
            .initialSelection(selected).onBack(backAction).show();
    }
}
