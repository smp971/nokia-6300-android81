package ro.leo.visual;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import ro.leo.i18n.L;

public final class StreamRadioActivity extends PocketActivity {
    private static final String FAVORITES = BackupAppData.RADIO_FAVORITES;
    private final List<RomanianRadio.Station> rows = new ArrayList<>();
    private boolean directory, favoritesOnly;
    private int selected;
    private TextView mark, stationTitle, stationState;
    private String requested = "";
    private final BroadcastReceiver playback = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updatePanel(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(playback, new IntentFilter("ro.leo.visual.MUSIC_CHANGED"));
        updatePanel();
    }

    @Override protected void onStop() {
        unregisterReceiver(playback);
        super.onStop();
    }

    @Override protected void render() throws Exception {
        directory = false;
        rows.clear();
        rows.addAll(RomanianRadio.STATIONS);
        JSONArray saved = PocketStore.read(this, "streams").optJSONArray("stations");
        if (saved != null) for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.getJSONObject(i);
            String name = item.getString("name");
            String url = PocketStore.url(item.getString("url"));
            if (!contains(url)) {
                rows.add(new RomanianRadio.Station(name, url, "Post personal", "NET", 0xff8cd4e1, false));
            }
        }
        if (favoritesOnly) {
            for (int i = rows.size() - 1; i >= 0; i--) if (!favorite(rows.get(i))) rows.remove(i);
        }
        selected = Math.max(0, Math.min(selected, rows.size() - 1));

        ArrayList<String> labels = new ArrayList<>();
        for (RomanianRadio.Station row : rows) {
            labels.add(row.name + "\n" + L.t(row.category) + (favorite(row) ? "   ★" : ""));
        }
        directory = true;
        list(L.t(favoritesOnly ? "Favorite radio" : "Radio FM / Internet"), labels, selected,
                new Choice() { public void pick(int index) { toggle(index); } }, L.t("Opțiuni"),
                new Runnable() { public void run() { showOptions(); } });
        RadioSources.insert(this, true, new Runnable() { public void run() { openFm(); } });
        insertPanel();
        updatePanel();
    }

    private boolean contains(String url) {
        for (RomanianRadio.Station station : rows) if (station.url.equals(url)) return true;
        return false;
    }

    private void insertPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(9), dp(4), dp(8), dp(4));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{palette.card, Appearance.mix(palette.card, palette.accent, .12f)});
        background.setCornerRadius(dp(7));
        panel.setBackground(background);

        mark = text("NET", 16);
        mark.setGravity(Gravity.CENTER);
        mark.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        mark.setTextColor(0xff141b27);
        mark.setPadding(0, 0, 0, 0);
        panel.addView(mark, new LinearLayout.LayoutParams(dp(43), dp(38)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(9), 0, 0, 0);
        stationTitle = text("", 15);
        stationTitle.setMaxLines(2);
        stationTitle.setEllipsize(TextUtils.TruncateAt.END);
        stationTitle.setTypeface(UiChrome.TITLE);
        stationState = text("", 12);
        stationState.setTextColor(palette.accent);
        stationState.setSingleLine(true);
        stationState.setEllipsize(TextUtils.TruncateAt.END);
        for (TextView line : new TextView[]{stationTitle, stationState}) {
            line.setPadding(0, 0, 0, 0);
            details.addView(line);
        }
        panel.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.setMargins(dp(9), dp(3), dp(9), dp(5));
        panel.setMinimumHeight(dp(54));
        root.addView(panel, 3, size);
    }

    @Override void onListSelection(String title, int index) {
        selected = index;
        updatePanel();
    }

    @Override Drawable rowIcon(String title, int index) { return new AppBadge("Radio Internet", dp(20)); }

    private void updatePanel() {
        if (!directory || mark == null || isFinishing()) return;
        RomanianRadio.Station highlighted = selected < rows.size() ? rows.get(selected) : null;
        PlaybackService player = PlaybackService.instance;
        String stream = player == null ? "" : player.radioUrl();
        RomanianRadio.Station active = null;
        for (RomanianRadio.Station candidate : rows) if (candidate.url.equals(stream)) {
            active = candidate;
            break;
        }
        RomanianRadio.Station display = active != null ? active : highlighted;
        mark.setText(display == null ? "NET" : display.mark);
        mark.setBackground(Appearance.surface(display == null ? palette.accent : display.color, dp(7)));
        stationTitle.setText(!stream.isEmpty() ? PlaybackService.title : display == null
                ? L.t("Niciun post favorit") : display.name);
        if (!stream.isEmpty()) {
            stationState.setText(player.playing() ? L.t("În direct") : PlaybackService.state);
        } else if (highlighted != null && highlighted.url.equals(requested) && player == null && !L.t("Oprit").equals(PlaybackService.state)) {
            stationState.setText(PlaybackService.state);
        } else {
            stationState.setText(L.t(display == null ? "Opțiuni pentru lista completă" : "Alege un post cu OK"));
        }
        footer(L.t("Opțiuni"), highlighted == null ? "" : L.t(highlighted.url.equals(stream)
                ? "Oprește" : "Ascultă"), L.t("Înapoi"));
    }

    private void toggle(int index) {
        if (index < 0 || index >= rows.size()) return;
        RomanianRadio.Station station = rows.get(index);
        PlaybackService active = PlaybackService.instance;
        if (active != null && station.url.equals(active.radioUrl())) {
            stopRadio();
            return;
        }
        requested = station.url;
        stationState.setText(L.t("Se conectează…"));
        startForegroundService(new Intent(this, PlaybackService.class)
                .setData(Uri.parse(station.url)).putExtra("title", station.name)
                .putExtra(PlaybackService.EXTRA_RADIO, true));
    }

    private void stopRadio() {
        requested = "";
        stopService(new Intent(this, PlaybackService.class));
    }

    private boolean favorite(RomanianRadio.Station station) {
        return getSharedPreferences(FAVORITES, MODE_PRIVATE).getBoolean(station.url, false);
    }

    private void showOptions() {
        RomanianRadio.Station station = selected < rows.size() ? rows.get(selected) : null;
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        addOption(labels, actions, favoritesOnly ? "Toate posturile" : "Favorite radio", new Runnable() { public void run() {
            favoritesOnly = !favoritesOnly;
            selected = 0;
            home();
        }});
        if (station != null) {
            addOption(labels, actions, favorite(station) ? "Elimină favoritul" : "Adaugă la favorite", new Runnable() { public void run() {
                getSharedPreferences(FAVORITES, MODE_PRIVATE).edit().putBoolean(station.url, !favorite(station)).apply();
                home();
            }});
            addOption(labels, actions, "Piesa curentă · ICY", new Runnable() { public void run() { readMetadata(station); } });
            if (!station.builtIn) addOption(labels, actions, "Șterge postul", new Runnable() { public void run() { delete(station); } });
        }
        if (PlaybackService.instance != null && !PlaybackService.instance.radioUrl().isEmpty()) {
            addOption(labels, actions, "Oprește radioul", new Runnable() { public void run() { stopRadio(); } });
        }
        addOption(labels, actions, "Adaugă post", new Runnable() { public void run() { addStation(); } });
        addOption(labels, actions, "Media curentă", new Runnable() { public void run() { startActivity(new Intent(StreamRadioActivity.this, NowPlayingActivity.class)); } });
        addOption(labels, actions, "Radio FM", new Runnable() { public void run() { openFm(); } });
        options(L.t("Opțiuni"), labels.toArray(new String[0]), new Choice() { public void pick(int index) { actions.get(index).run(); } });
    }

    private void addOption(List<String> labels, List<Runnable> actions, String label, Runnable action) {
        labels.add(L.t(label));
        actions.add(action);
    }

    private void readMetadata(RomanianRadio.Station station) {
        directory = false;
        String[] info = {""};
        job(L.t("Citesc metadatele radio"), new Work() { public void run() throws Exception {
            info[0] = IcyMetadata.sample(station.url);
        }}, new Runnable() { public void run() { page(station.name, L.t(info[0]), goHome(), "", null); }});
        back = goHome();
    }

    private void addStation() {
        directory = false;
        edit(L.t("Post nou"), new String[]{L.t("Nume"), L.t("Adresă directă MP3 / AAC")},
                new String[]{"", "https://"}, new Saved() { public void save(String[] values) throws Exception {
            String url = PocketStore.url(values[1]);
            String name = values[0].trim();
            if (name.isEmpty()) throw new IOException(L.t("Introdu numele postului"));
            JSONObject data = PocketStore.read(StreamRadioActivity.this, "streams");
            JSONArray stations = data.optJSONArray("stations");
            if (stations == null) stations = new JSONArray();
            if (stations.length() >= 100) throw new IOException(L.t("Maximum 100 de posturi"));
            for (RomanianRadio.Station preset : RomanianRadio.STATIONS) if (preset.url.equals(url)) {
                throw new IOException(L.t("Postul există deja"));
            }
            for (int i = 0; i < stations.length(); i++) if (stations.getJSONObject(i).getString("url").equals(url)) {
                throw new IOException(L.t("Postul există deja"));
            }
            stations.put(new JSONObject().put("name", name).put("url", url));
            PocketStore.write(StreamRadioActivity.this, "streams", data.put("stations", stations));
            favoritesOnly = false;
            selected = Integer.MAX_VALUE;
            home();
        }}, goHome());
    }

    private void delete(RomanianRadio.Station station) {
        remove(L.t("Ștergi postul?"), new Runnable() { public void run() {
            try {
                JSONObject data = PocketStore.read(StreamRadioActivity.this, "streams");
                JSONArray stations = data.getJSONArray("stations");
                for (int i = 0; i < stations.length(); i++) {
                    JSONObject item = stations.getJSONObject(i);
                    if (item.optString("url").equals(station.url) && item.optString("name").equals(station.name)) {
                        stations.remove(i);
                        break;
                    }
                }
                PocketStore.write(StreamRadioActivity.this, "streams", data);
                getSharedPreferences(FAVORITES, MODE_PRIVATE).edit().remove(station.url).apply();
                home();
            } catch (Exception failure) { problem(failure); }
        }});
    }

    private void openFm() {
        if (getIntent().getBooleanExtra(FmActivity.FROM_OTHER_SOURCE, false)) finish();
        else startActivity(new Intent(this, FmActivity.class).putExtra(FmActivity.FROM_OTHER_SOURCE, true));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (directory && event.getKeyCode() == KeyEvent.KEYCODE_POUND) {
            if (event.getAction() == KeyEvent.ACTION_UP) openFm();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
