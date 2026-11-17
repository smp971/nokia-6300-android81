package ro.leo.visual;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ro.leo.i18n.L;

/** Native keypad list of launchable Android activities. */
public final class AndroidAppsActivity extends LeoActivity {
    private final ArrayList<Entry> apps = new ArrayList<>();
    private int selected;
    private volatile int generation;
    private boolean showHidden, favoritesOnly;

    private static final class Entry {
        ComponentName component;
        String label;
        Drawable icon;
    }

    private Set<String> stored(String key) {
        return getSharedPreferences("android-apps", 0).getStringSet(key, Collections.<String>emptySet());
    }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            selected = state.getInt("selected");
            showHidden = state.getBoolean("showHidden");
            favoritesOnly = state.getBoolean("favoritesOnly");
        }
    }

    protected void onResume() {
        super.onResume();
        reload();
    }

    protected void onDestroy() {
        generation++;
        super.onDestroy();
    }

    protected void onSaveInstanceState(Bundle state) {
        state.putInt("selected", selected);
        state.putBoolean("showHidden", showHidden);
        state.putBoolean("favoritesOnly", favoritesOnly);
        super.onSaveInstanceState(state);
    }

    private void reload() {
        final int request = ++generation;
        final ComponentName previous = apps.isEmpty() ? null : apps.get(selected).component;
        final Set<String> favorites = new HashSet<>(stored("favorites"));
        final Set<String> hidden = new HashSet<>(stored("hidden"));
        final boolean includeHidden = showHidden, onlyFavorites = favoritesOnly;
        loading(L.t("Aplicații Android"));
        worker.execute(new Runnable() {
            public void run() {
                if (request != generation) return;
                try {
                    final List<Entry> found = findApps(favorites, hidden, includeHidden, onlyFavorites);
                    ui(new Runnable() {
                        public void run() {
                            if (request != generation) return;
                            apps.clear();
                            apps.addAll(found);
                            selected = Math.max(0, Math.min(selected, apps.size() - 1));
                            if (previous != null) {
                                for (int i = 0; i < apps.size(); i++) {
                                    if (previous.equals(apps.get(i).component)) selected = i;
                                }
                            }
                            showApps(favorites, hidden);
                        }
                    });
                } catch (final RuntimeException failure) {
                    ui(new Runnable() {
                        public void run() {
                            if (request != generation) return;
                            errorPage(L.t("Aplicații Android"), L.t("Nicio aplicație disponibilă"),
                                    new Runnable() { public void run() { reload(); } });
                        }
                    });
                }
            }
        });
    }

    private List<Entry> findApps(final Set<String> favorites, Set<String> hidden,
                                 boolean includeHidden, boolean onlyFavorites) {
        PackageManager manager = getPackageManager();
        HashSet<String> homePackages = new HashSet<>();
        for (ResolveInfo home : manager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            homePackages.add(home.activityInfo.packageName);
        }
        HashSet<ComponentName> seen = new HashSet<>();
        ArrayList<Entry> found = new ArrayList<>();
        for (ResolveInfo info : manager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (!info.activityInfo.exported || !info.activityInfo.enabled
                    || getPackageName().equals(info.activityInfo.packageName)
                    || homePackages.contains(info.activityInfo.packageName)) continue;
            ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            String id = component.flattenToString();
            if (!seen.add(component) || (!includeHidden && hidden.contains(id))
                    || (onlyFavorites && !favorites.contains(id))) continue;
            Entry item = new Entry();
            item.component = component;
            item.label = info.loadLabel(manager).toString();
            item.icon = info.loadIcon(manager);
            found.add(item);
        }
        final Collator collator = Collator.getInstance(L.locale());
        Collections.sort(found, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                boolean first = favorites.contains(a.component.flattenToString());
                boolean second = favorites.contains(b.component.flattenToString());
                if (first != second) return first ? -1 : 1;
                int order = collator.compare(a.label, b.label);
                return order != 0 ? order : a.component.flattenToString().compareTo(b.component.flattenToString());
            }
        });
        return found;
    }

    private void showApps(Set<String> favorites, Set<String> hidden) {
        ArrayList<String> labels = new ArrayList<>();
        for (Entry item : apps) {
            String id = item.component.flattenToString();
            labels.add((favorites.contains(id) ? "★ " : "") + (hidden.contains(id) ? "◌ " : "") + item.label);
        }
        list(L.t("Aplicații Android"), labels, selected,
                new Choice() { public void pick(int index) { open(index); } },
                L.t("Opțiuni"), new Runnable() { public void run() { menu(); } });
    }

    @Override void onListSelection(String title, int index) { selected = index; }
    @Override String emptyMessage(String title) { return L.t("Nicio aplicație disponibilă"); }
    @Override String rowMemoryKey(String title, int index, String label) {
        return apps.get(index).component.flattenToString();
    }
    @Override boolean stableRowIcons() { return true; }
    @Override Drawable rowIcon(String title, int index) {
        Drawable icon = apps.get(index).icon;
        icon.setBounds(0, 0, dp(28), dp(28));
        return icon;
    }
    @Override void styleListRow(TextView row) {
        super.styleListRow(row);
        row.setMaxLines(2);
    }

    private void open(int index) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(apps.get(index).component));
        } catch (RuntimeException unavailable) {
            notice(L.t("Nu se poate deschide"));
            reload();
        }
    }

    private void menu() {
        final Entry item = apps.isEmpty() ? null : apps.get(selected);
        options(L.t("Aplicații Android"), new String[]{L.t("Adaugă / elimină favoritul"),
                L.t("Ascunde / arată selecția"), showHidden ? L.t("Ascunde intrările ascunse")
                : L.t("Arată și intrările ascunse"), favoritesOnly ? L.t("Toate aplicațiile")
                : L.t("Doar favorite"), L.t("Informații aplicație"), L.t("Aplicații implicite Android")},
                new Choice() {
            public void pick(int index) {
                if (index == 0 || index == 1) {
                    if (item == null) return;
                    String key = index == 0 ? "favorites" : "hidden";
                    String id = item.component.flattenToString();
                    Set<String> ids = new HashSet<>(stored(key));
                    if (!ids.remove(id)) ids.add(id);
                    getSharedPreferences("android-apps", 0).edit().putStringSet(key, ids).apply();
                } else if (index == 2) showHidden = !showHidden;
                else if (index == 3) favoritesOnly = !favoritesOnly;
                else {
                    Intent settings = new Intent(AndroidAppsActivity.this, AppControlActivity.class);
                    if (index == 4) {
                        if (item == null) return;
                        settings.putExtra("package", item.component.getPackageName());
                    }
                    startActivity(settings);
                    return;
                }
                reload();
            }
        });
    }
}
