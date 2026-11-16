package ro.leo.visual;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.AtomicFile;
import java.io.File;
import java.util.Collections;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Round trip using isolated preferences and app files. */
public final class AppDataBackupTests extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context target = getTargetContext();
        String id = "backup-test-" + UUID.randomUUID();
        File directory = new File(target.getCacheDir(), id);
        Context fixture = new ContextWrapper(target) {
            @Override public File getFilesDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return target.getSharedPreferences(id + "-" + name, mode);
            }
        };
        try {
            JSONObject playlists = new JSONObject().put("Test", new JSONArray().put(
                new JSONArray().put("content://media/external/audio/media/1").put("Piesă")));
            fixture.getSharedPreferences("music-lists", 0).edit().putString("lists", playlists.toString()).commit();
            JSONObject feeds = new JSONObject().put("feeds", new JSONArray().put(new JSONObject()
                .put("id", "test-podcast").put("name", "Podcast").put("url", "https://example.com/podcast")
                .put("podcast", true)));
            JSONObject streams = new JSONObject().put("stations", new JSONArray().put(new JSONObject()
                .put("name", "Radio").put("url", "https://example.com/radio")));
            PocketStore.write(fixture, "feeds", feeds);
            PocketStore.write(fixture, "streams", streams);
            String station = "https://example.com/radio";
            SharedPreferences radio = fixture.getSharedPreferences(BackupAppData.RADIO_FAVORITES, 0);
            radio.edit().putBoolean(station, true).putBoolean("https://example.com/unstarred", false).commit();
            PocketStore.write(fixture, "ssh-profile", new JSONObject().put("fixture", true));
            StudyActivity.add(fixture, "water", "apă");
            JSONObject saved = BackupAppData.snapshot(fixture);
            BackupAppData.validate(saved);
            check(!saved.getJSONObject("pocket").has("ssh-profile"), "SSH profile excluded");
            PocketStore.write(fixture, "feeds", new JSONObject());
            PocketStore.write(fixture, "streams", new JSONObject());
            PocketStore.write(fixture, "study", new JSONObject());
            PocketStore.write(fixture, "lexicon", new JSONObject());
            fixture.getSharedPreferences("music-lists", 0).edit().clear().commit();
            radio.edit().clear().putBoolean("https://example.com/other", true).commit();
            BackupAppData.apply(fixture, saved);
            check(MusicLists.read(fixture).get("Test").get(0)[1].equals("Piesă"), "playlist restored");
            check(PocketStore.read(fixture, "feeds").getJSONArray("feeds").getJSONObject(0)
                .getBoolean("podcast"), "podcast subscription restored");
            check(PocketStore.read(fixture, "streams").getJSONArray("stations").getJSONObject(0)
                .getString("name").equals("Radio"), "radio station restored");
            check(StudyActivity.cards(fixture).length() == 1, "study data restored");
            check(radio.getAll().size() == 1 && radio.getBoolean(station, false), "starred radio stations restored");
            check(new JSONObject(new String(BackupEngine.archive(new JSONObject().put(BackupAppData.KEY, saved)),
                java.nio.charset.StandardCharsets.UTF_8)).getInt("version") == 5, "archive version includes radio favorites");
            JSONObject previous = new JSONObject(saved.toString());
            previous.put("version", 1).remove("radio-favorites");
            BackupAppData.validate(previous);
            BackupAppData.apply(fixture, previous);
            check(radio.getBoolean(station, false), "version 1 leaves radio favorites intact");
            check(!BackupEngine.atomicExists(PocketStore.file(fixture, "lexicon")), "absent data remains absent");
            check(PocketStore.read(fixture, "ssh-profile").getBoolean("fixture"), "unselected store kept");

            JSONObject current = new JSONObject().put("preferences", new JSONObject()).put(BackupAppData.KEY, saved);
            JSONObject older = new JSONObject().put("preferences", new JSONObject());
            check(!BackupEngine.selected(older, current, Collections.singleton(BackupAppData.KEY))
                .has(BackupAppData.KEY), "older backup leaves app data untouched");
            check(BackupEngine.selected(current, current, Collections.singleton(BackupAppData.KEY))
                .has(BackupAppData.KEY), "app-data category included only when chosen");
            check(!BackupEngine.selected(current, current, Collections.<String>emptySet())
                .has(BackupAppData.KEY), "unselected app data left untouched");
            JSONObject invalid = new JSONObject(saved.toString());
            invalid.getJSONObject("pocket").put("ssh-profile", new JSONObject());
            boolean rejected = false;
            try { BackupAppData.validate(invalid); } catch (Exception expected) { rejected = true; }
            check(rejected, "unrecognized Pocket store rejected");
            File pending = BackupEngine.pending(fixture);
            BackupEngine.write(pending, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean failedRecovery = false;
            try { BackupEngine.recover(fixture); } catch (Exception expected) { failedRecovery = true; }
            check(failedRecovery && BackupEngine.atomicExists(pending), "failed recovery retains the journal");
            new AtomicFile(pending).delete();
            result.putString("result", "PASS isolated app backup checks, including radio favorites and version 1 compatibility");
        } catch (Throwable failure) {
            result.putString("error", android.util.Log.getStackTraceString(failure));
        } finally {
            fixture.getSharedPreferences("music-lists", 0).edit().clear().commit();
            fixture.getSharedPreferences(BackupAppData.RADIO_FAVORITES, 0).edit().clear().commit();
            new AtomicFile(BackupEngine.pending(fixture)).delete();
            File pocket = new File(directory, "pocket");
            File[] files = pocket.listFiles();
            if (files != null) for (File file : files) new AtomicFile(file).delete();
            pocket.delete();
            directory.delete();
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
