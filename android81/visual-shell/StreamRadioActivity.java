package ro.leo.visual;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import ro.leo.i18n.L;

public final class StreamRadioActivity extends PocketActivity {
    private static final String FAVORITES = BackupAppData.RADIO_FAVORITES;
    private final List<RomanianRadio.Station> rows = new ArrayList<>();
    private boolean directory, listRequested, favoritesOnly, connecting;
    private int selected = -1;
    private RadioFace face;
    private String requested = "";
    private final BroadcastReceiver playback = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            connecting = false;
            updatePlaybackUi();
        }
    };

    @Override protected void onCreate(Bundle state) {
        if (state != null) {
            selected = state.getInt("station", -1);
            listRequested = state.getBoolean("directory");
            favoritesOnly = state.getBoolean("favorites");
        }
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(playback, new IntentFilter("ro.leo.visual.MUSIC_CHANGED"));
        updatePlaybackUi();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("station", selected);
        state.putBoolean("directory", listRequested);
        state.putBoolean("favorites", favoritesOnly);
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
        if (selected < 0) selected = stationIndex(activeUrl());
        selected = Math.max(0, Math.min(selected, rows.size() - 1));
        if (!listRequested) {
            showPlayer();
            return;
        }

        ArrayList<String> labels = new ArrayList<>();
        for (RomanianRadio.Station row : rows) {
            labels.add(row.name + "\n" + L.t(row.category) + (favorite(row) ? "   ★" : ""));
        }
        directory = true;
        list(L.t(favoritesOnly ? "Favorite radio" : "Radio FM / Internet"), labels, selected,
                new Choice() { public void pick(int index) { toggle(index); } }, L.t("Opțiuni"),
                new Runnable() { public void run() { showOptions(); } });
        RadioSources.insert(this, true, new Runnable() { public void run() { openFm(); } });
        back = new Runnable() { public void run() { showPlayer(); } };
        updatePlaybackUi();
    }

    private boolean contains(String url) {
        for (RomanianRadio.Station station : rows) if (station.url.equals(url)) return true;
        return false;
    }

    private void showPlayer() {
        directory = false;
        listRequested = false;
        begin(L.t("Radio Internet"));
        face = new RadioFace();
        root.addView(face, new LinearLayout.LayoutParams(-1, 0, 1));
        left = new Runnable() { public void run() { showOptions(); } };
        updatePlaybackUi();
        face.requestFocus();
    }

    private void showDirectory() {
        listRequested = true;
        home();
    }

    @Override void onListSelection(String title, int index) {
        if (directory) {
            selected = index;
            updatePlaybackUi();
        }
    }

    @Override Drawable rowIcon(String title, int index) { return new AppBadge("Radio Internet", dp(20)); }

    private RomanianRadio.Station selectedStation() {
        return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
    }

    private String activeUrl() {
        PlaybackService player = PlaybackService.instance;
        return player == null ? "" : player.radioUrl();
    }

    private int stationIndex(String url) {
        if (!url.isEmpty()) {
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).url.equals(url)) return i;
        }
        return -1;
    }

    private boolean showingPlayer() {
        return face != null && face.getParent() == root;
    }

    private void updatePlaybackUi() {
        if (isFinishing() || root == null) return;
        RomanianRadio.Station station = selectedStation();
        String active = activeUrl();
        if (showingPlayer()) {
            boolean stop = !active.isEmpty() || connecting;
            footer(L.t("Opțiuni"), stop ? L.t("Oprește") : station == null ? L.t("Posturi") : L.t("Ascultă"),
                L.t("Înapoi"));
            face.invalidate();
        } else if (directory) {
            footer(L.t("Opțiuni"), station == null ? "" : L.t(station.url.equals(active) ? "Oprește" : "Ascultă"),
                L.t("Înapoi"));
        }
    }

    private void toggle(int index) {
        if (index < 0 || index >= rows.size()) return;
        selected = index;
        RomanianRadio.Station station = rows.get(index);
        if (station.url.equals(activeUrl())) stopRadio();
        else startStation(station);
        showPlayer();
    }

    private void togglePlayer() {
        if (!activeUrl().isEmpty()) {
            stopRadio();
        } else if (!connecting) {
            RomanianRadio.Station station = selectedStation();
            if (station == null) showDirectory();
            else startStation(station);
        }
    }

    private void stepStation(int direction) {
        if (rows.isEmpty() || connecting) return;
        int current = stationIndex(activeUrl());
        selected = CircularNavigation.step(current < 0 ? selected : current, direction, rows.size());
        if (!activeUrl().isEmpty() && selected != current) startStation(rows.get(selected));
        else updatePlaybackUi();
    }

    private void startStation(RomanianRadio.Station station) {
        requested = station.url;
        connecting = true;
        startForegroundService(new Intent(this, PlaybackService.class)
                .setData(Uri.parse(station.url)).putExtra("title", station.name)
                .putExtra(PlaybackService.EXTRA_RADIO, true));
        updatePlaybackUi();
    }

    private void stopRadio() {
        PlaybackService player = PlaybackService.instance;
        if (player == null || player.radioUrl().isEmpty()) return;
        requested = "";
        connecting = false;
        player.stopPlayback();
        updatePlaybackUi();
    }

    private final class RadioFace extends View {
        private final MediaPanel panel = new MediaPanel(StreamRadioActivity.this);
        private final MediaPanel.State state = new MediaPanel.State();
        private final AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        RadioFace() {
            super(StreamRadioActivity.this);
            setFocusableInTouchMode(true);
            state.internet = true;
        }

        void changeVolume(int direction) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
            state.refreshVolume();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            PlaybackService player = PlaybackService.instance;
            String active = activeUrl();
            int index = stationIndex(active);
            RomanianRadio.Station station = connecting || active.isEmpty() ? selectedStation()
                : index >= 0 ? rows.get(index) : null;
            state.stationColor = station == null ? palette.accent : station.color;
            state.stationMark = station == null ? "NET" : station.mark;
            if (!connecting && !active.isEmpty()) state.title = PlaybackService.title;
            else state.title = station == null ? L.t("Niciun post favorit") : station.name;
            state.detail = station == null ? L.t("Radio Internet") : L.t(station.category);
            state.canStop = !active.isEmpty() || connecting;
            state.playing = !active.isEmpty() && player != null && player.playing();
            if (connecting) state.status = L.t("Se conectează…");
            else if (state.playing) state.status = L.t("În direct");
            else if (!active.isEmpty() || (station != null && station.url.equals(requested) && player == null)) {
                state.status = PlaybackService.state;
            } else state.status = L.t("Oprit");
            state.signal = "";
            state.spectrumLabel = L.t("Flux live");
            state.readVolume(audio);
            panel.draw(canvas, getWidth(), getHeight(), palette, state);
        }
    }

    private boolean favorite(RomanianRadio.Station station) {
        return getSharedPreferences(FAVORITES, MODE_PRIVATE).getBoolean(station.url, false);
    }

    private void showOptions() {
        int active = directory ? -1 : stationIndex(activeUrl());
        RomanianRadio.Station station = active < 0 ? selectedStation() : rows.get(active);
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        addOption(labels, actions, directory ? "Player radio" : "Posturi", new Runnable() { public void run() {
            if (directory) showPlayer(); else showDirectory();
        }});
        addOption(labels, actions, favoritesOnly ? "Toate posturile" : "Favorite radio", new Runnable() { public void run() {
            favoritesOnly = !favoritesOnly;
            selected = 0;
            showDirectory();
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
        addOption(labels, actions, "Media curentă", new Runnable() { public void run() {
            startActivity(new Intent(StreamRadioActivity.this, NowPlayingActivity.class));
        }});
        addOption(labels, actions, "Radio FM", new Runnable() { public void run() { openFm(); } });
        addOption(labels, actions, "Info / Ajutor", new Runnable() { public void run() {
            ContextHelp.open(StreamRadioActivity.this, "StreamRadioActivity");
        }});
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
            showDirectory();
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
        if (isJackNavigationNoise(event)) return true;
        if (showingPlayer() && dispatchPlayerKey(event)) return true;
        if (directory && event.getKeyCode() == KeyEvent.KEYCODE_POUND) {
            if (event.getAction() == KeyEvent.ACTION_UP) openFm();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean dispatchPlayerKey(KeyEvent event) {
        int key = event.getKeyCode();
        boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (pressed) face.changeVolume(key == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_6:
                if (pressed && event.getRepeatCount() == 0) {
                    stepStation(key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_4 ? -1 : 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_5:
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) togglePlayer();
                return true;
            case KeyEvent.KEYCODE_POUND:
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) showDirectory();
                return true;
            default:
                return false;
        }
    }
}
