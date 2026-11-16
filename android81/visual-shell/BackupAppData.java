package ro.leo.visual;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** The app-owned documents represented by the optional app-data backup section. */
final class BackupAppData {
    static final String KEY = "app-data";
    private static final String[] STORES = {"feeds", "streams", "saved-news", "lexicon", "study", "expenses"};
    private static final String[] LISTS = {"feeds", "stations", "items", "words", "cards", "entries"};
    private static final int LIMIT = 4 * 1024 * 1024;
    static final String RADIO_FAVORITES = "internet-radio-favorites";
    private static final String RADIO = "radio-favorites";

    static JSONObject snapshot(Context context) throws Exception {
        String playlists = context.getSharedPreferences("music-lists", 0).getString("lists", null);
        JSONObject stores = new JSONObject();
        synchronized (PocketStore.class) {
            for (String name : STORES) {
                stores.put(name, BackupEngine.atomicExists(PocketStore.file(context, name))
                    ? PocketStore.read(context, name) : JSONObject.NULL);
            }
        }
        TreeSet<String> favorites = new TreeSet<>();
        for (java.util.Map.Entry<String, ?> entry :
                context.getSharedPreferences(RADIO_FAVORITES, 0).getAll().entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) favorites.add(entry.getKey());
        }
        return new JSONObject().put("version", 2).put("pocket", stores)
            .put(RADIO, new JSONArray(favorites))
            .put("playlists", playlists == null ? JSONObject.NULL : new JSONObject(playlists));
    }

    static void validate(JSONObject data) throws Exception {
        int version = data.getInt("version");
        if (!data.has("playlists") || (version != 1 && version != 2)
                || data.length() != version + 2 || (version == 1 && data.has(RADIO)))
            throw new IOException("Unsupported app data backup");
        if (version == 2) {
            JSONArray favorites = data.getJSONArray(RADIO);
            if (favorites.length() > 500) throw new IOException("Too many radio favorites");
            for (int i = 0; i < favorites.length(); i++) PocketStore.url(favorites.getString(i));
        }
        if (!data.isNull("playlists")) validatePlaylists(data.getJSONObject("playlists"));
        JSONObject stores = data.getJSONObject("pocket");
        if (stores.length() != STORES.length) throw new IOException("Invalid Pocket data");
        for (int i = 0; i < STORES.length; i++) {
            String name = STORES[i];
            if (!stores.has(name)) throw new IOException("Missing Pocket data: " + name);
            if (stores.isNull(name)) continue;
            JSONObject item = stores.getJSONObject(name);
            bounded(item);
            if (item.length() == 0) continue;
            JSONArray rows = item.getJSONArray(LISTS[i]);
            for (int at = 0; at < rows.length(); at++) validateRow(name, rows, at);
        }
    }

    private static void validatePlaylists(JSONObject lists) throws Exception {
        bounded(lists);
        if (lists.length() > 30) throw new IOException("Too many playlists");
        for (Iterator<String> names = lists.keys(); names.hasNext();) {
            String name = names.next();
            JSONArray tracks = lists.getJSONArray(name);
            if (name.trim().isEmpty() || name.length() > 40 || tracks.length() > 500)
                throw new IOException("Invalid playlist");
            for (int i = 0; i < tracks.length(); i++) {
                JSONArray track = tracks.getJSONArray(i);
                String scheme = Uri.parse(track.getString(0)).getScheme();
                if (track.length() != 2 || (!"file".equals(scheme) && !"content".equals(scheme))
                        || !(track.get(1) instanceof String)) throw new IOException("Invalid playlist track");
            }
        }
    }

    private static void validateRow(String name, JSONArray rows, int at) throws Exception {
        if ("lexicon".equals(name)) {
            JSONArray pair = rows.getJSONArray(at);
            if (pair.length() != 2 || !(pair.get(0) instanceof String) || !(pair.get(1) instanceof String))
                throw new IOException("Invalid dictionary entry");
            return;
        }
        JSONObject row = rows.getJSONObject(at);
        switch (name) {
            case "feeds":
                if (!row.getString("id").matches("[a-z0-9-]{1,64}")) throw new IOException("Invalid subscription ID");
                row.getBoolean("podcast");
                // Subscriptions and stations use the same URL and label rules.
            case "streams":
                PocketStore.url(row.getString("url"));
                row.getString("name");
                break;
            case "saved-news":
                row.getString("title");
                break;
            case "study":
                row.getString("id");
                row.getString("front");
                row.getString("back");
                row.getLong("due");
                row.getInt("days");
                break;
            case "expenses":
                row.getString("id");
                row.getLong("cents");
                row.getString("currency");
                row.getString("date");
                break;
            default:
                throw new IOException("Unsupported Pocket data");
        }
    }

    private static void bounded(JSONObject data) throws IOException {
        if (data.toString().getBytes(StandardCharsets.UTF_8).length > LIMIT)
            throw new IOException("App data exceeds 4 MiB");
    }

    static void apply(Context context, JSONObject data) throws Exception {
        JSONObject stores = data.getJSONObject("pocket");
        synchronized (PocketStore.class) {
            for (String name : STORES) {
                if (stores.isNull(name)) new AtomicFile(PocketStore.file(context, name)).delete();
                else PocketStore.write(context, name, stores.getJSONObject(name));
            }
        }
        SharedPreferences.Editor lists = context.getSharedPreferences("music-lists", 0).edit();
        if (data.isNull("playlists")) lists.remove("lists");
        else lists.putString("lists", data.getJSONObject("playlists").toString());
        if (!lists.commit()) throw new IOException("Could not restore playlists");
        if (data.has(RADIO)) {
            JSONArray favorites = data.getJSONArray(RADIO);
            SharedPreferences.Editor radio = context.getSharedPreferences(RADIO_FAVORITES, 0).edit().clear();
            for (int i = 0; i < favorites.length(); i++) radio.putBoolean(favorites.getString(i), true);
            if (!radio.commit()) throw new IOException("Could not restore radio favorites");
        }
    }
}
