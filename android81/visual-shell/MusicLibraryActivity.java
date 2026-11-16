package ro.leo.visual;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import ro.leo.i18n.L;

/** Bounded local media index queried off the UI thread. */
public final class MusicLibraryActivity extends LeoActivity {
    private static final int STORAGE_REQUEST = 103;
    private static final String READ_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    private static final Uri AUDIO = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    private static final Uri FILES = MediaStore.Files.getContentUri("external");

    private enum Page { HOME, ARTISTS, ALBUMS, ALL, ARTIST, ALBUM, FAVORITES, PLAYLISTS, PLAYLIST, SEARCH, RESULTS }

    private final ArrayList<String[]> tracks = new ArrayList<String[]>();
    private final ArrayList<String> rowKeys = new ArrayList<String>();
    private final ArrayList<String> artKeys = new ArrayList<String>();
    private final RowArtwork artwork = new RowArtwork(this);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Page page = Page.HOME;
    private String pageArgument = "";
    private String listTitle = "";
    private int activeIndex;
    private int homeIndex;
    private boolean waitingForStorage;
    private boolean resumed;
    private boolean observed;
    private boolean loaded;
    private boolean loading;
    private boolean dirty;
    private final Runnable refresh = new Runnable() {
        public void run() { if (resumed && dirty) load(false); }
    };
    private final BroadcastReceiver storage = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            artwork.cache.evictAll();
            artwork.failed.clear();
            catalogChanged();
        }
    };
    private final ContentObserver index = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { catalogChanged(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        filter.addDataScheme("file");
        registerReceiver(storage, filter);
        load(true);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (waitingForStorage && storageAllowed()) load(true);
        else if (page == Page.PLAYLISTS) playlists();
        scheduleRefresh();
    }

    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refresh);
        unregisterReceiver(storage);
        if (observed) getContentResolver().unregisterContentObserver(index);
        super.onDestroy();
    }

    private void catalogChanged() {
        dirty = true;
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        handler.removeCallbacks(refresh);
        if (resumed && dirty && !loading) handler.postDelayed(refresh, 450);
    }

    private boolean storageAllowed() {
        return checkSelfPermission(READ_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void storageAccess() {
        waitingForStorage = true;
        list(L.t("Acces la muzica locală"),
            Arrays.asList(L.t("Permite citirea fișierelor audio\nMemorie internă și card SD"), L.t("Permisiunile aplicației")),
            0, new Choice() {
                public void pick(int at) {
                    if (at == 0) requestPermissions(new String[]{READ_STORAGE}, STORAGE_REQUEST);
                    else applicationPermissions();
                }
            }, "", null);
    }

    private void applicationPermissions() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException unavailable) {
            notice(L.t("Setările permisiunilor nu sunt disponibile"));
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] granted) {
        super.onRequestPermissionsResult(request, permissions, granted);
        if (request != STORAGE_REQUEST) return;
        if (storageAllowed()) load(true);
        else {
            storageAccess();
            notice(L.t("Accesul la stocare nu a fost acordat"));
        }
    }

    private void indexError() {
        list(L.t("Biblioteca nu poate fi citită"),
            Arrays.asList(L.t("Reîncearcă citirea indexului media"), L.t("Permisiunile aplicației")),
            0, new Choice() {
                public void pick(int at) {
                    if (at == 0) load(true);
                    else applicationPermissions();
                }
            }, "", null);
    }

    private static String label(String value, String fallback) {
        return value == null || value.isEmpty() || MediaStore.UNKNOWN_STRING.equals(value) ? fallback : value;
    }

    private void load(final boolean requested) {
        if (loading) return;
        dirty = false;
        if (!storageAllowed()) {
            storageAccess();
            return;
        }
        waitingForStorage = false;
        if (!observed) {
            try {
                getContentResolver().registerContentObserver(Uri.parse("content://media/external"), true, index);
                observed = true;
            } catch (SecurityException ignored) {
                // Mount and scan broadcasts still cover removable storage changes.
            }
        }
        if (!loaded) {
            begin(L.t("Bibliotecă muzicală"));
            root.addView(text(L.t("Se citesc piesele…"), 18));
            footer("", "", L.t("Înapoi"));
        }
        loading = true;
        worker.execute(new Runnable() {
            public void run() {
                final ArrayList<String[]> found = new ArrayList<String[]>();
                final boolean complete = readIndex(found);
                ui(new Runnable() {
                    public void run() {
                        loading = false;
                        if (!storageAllowed()) {
                            storageAccess();
                        } else if (!complete && found.isEmpty() && !loaded) {
                            indexError();
                        } else if (complete || !found.isEmpty()) {
                            boolean changed = !loaded || !sameTracks(found);
                            loaded = true;
                            if (changed) {
                                tracks.clear();
                                tracks.addAll(found);
                                showUpdatedPage();
                            }
                        }
                        if (requested && !complete && loaded) {
                            notice(L.t("Bibliotecă parțială · reîncarcă pentru a reîncerca"));
                        }
                        scheduleRefresh();
                    }
                });
            }
        });
    }

    private boolean readIndex(ArrayList<String[]> found) {
        HashSet<Long> seen = new HashSet<Long>();
        boolean complete = true;
        try (Cursor rows = getContentResolver().query(AUDIO,
            new String[]{"_id", "title", "artist", "album", "_display_name", "album_id", "_data"},
            null, null, "title COLLATE NOCASE ASC, _id ASC")) {
            if (rows == null) complete = false;
            while (rows != null && rows.moveToNext() && found.size() < MusicQueue.LIMIT) {
                long id = rows.getLong(0);
                seen.add(id);
                found.add(new String[]{ContentUris.withAppendedId(AUDIO, id).toString(),
                    label(rows.getString(1), label(rows.getString(4), L.t("Muzică Leo"))),
                    label(rows.getString(2), L.t("Artist necunoscut")),
                    label(rows.getString(3), L.t("Album necunoscut")),
                    "content://media/external/audio/albumart/" + rows.getLong(5), rows.getString(6)});
            }
        } catch (RuntimeException denied) {
            complete = false;
        }
        try (Cursor rows = getContentResolver().query(FILES, new String[]{"_id", "_display_name", "_data"},
            "_display_name LIKE ?", new String[]{"%.wma"}, "_display_name COLLATE NOCASE ASC, _id ASC")) {
            if (rows == null) complete = false;
            while (rows != null && rows.moveToNext() && found.size() < MusicQueue.LIMIT) {
                long id = rows.getLong(0);
                if (seen.add(id)) found.add(new String[]{ContentUris.withAppendedId(FILES, id).toString(),
                    label(rows.getString(1), L.t("Muzică Leo")), L.t("Artist necunoscut"),
                    L.t("Album necunoscut"), "", rows.getString(2)});
            }
        } catch (RuntimeException denied) {
            complete = false;
        }
        return complete;
    }

    private boolean sameTracks(List<String[]> found) {
        if (found.size() != tracks.size()) return false;
        for (int i = 0; i < found.size(); i++) if (!Arrays.equals(tracks.get(i), found.get(i))) return false;
        return true;
    }

    private void showUpdatedPage() {
        if (page == Page.HOME) home();
        else if (page == Page.ARTISTS || page == Page.ALBUMS) groups(page);
        else if (page != Page.SEARCH && page != Page.PLAYLISTS && page != Page.PLAYLIST)
            trackPage(page, pageArgument);
    }

    private void home() {
        page = Page.HOME;
        pageArgument = "";
        final List<String> labels = Arrays.asList(L.t("Toate piesele"), L.t("Artiști"), L.t("Albume"),
            L.t("Liste de redare"), L.t("Media curentă"), L.t("Reîncarcă biblioteca"),
            L.t("Favorite"), L.t("Caută T9 / text"), L.t("Reia ultima sesiune"));
        prepareList(L.t("Muzică · ") + tracks.size() + L.t(" piese"), labels, Collections.<String>emptyList());
        list(listTitle, labels, homeIndex, new Choice() {
            public void pick(int at) {
                switch (at) {
                    case 0: trackPage(Page.ALL, ""); break;
                    case 1: groups(Page.ARTISTS); break;
                    case 2: groups(Page.ALBUMS); break;
                    case 3: playlists(); break;
                    case 4: startActivity(new Intent(MusicLibraryActivity.this, NowPlayingActivity.class)); break;
                    case 5: load(true); break;
                    case 6: trackPage(Page.FAVORITES, ""); break;
                    case 7: search(""); break;
                    case 8: resumeLast(); break;
                }
            }
        }, "Player", new Runnable() {
            public void run() { openPlayer(); }
        });
    }

    private void resumeLast() {
        try {
            MusicResume.read(this);
            startService(new Intent(this, PlaybackService.class).setAction("resume"));
            openPlayer();
        } catch (Exception unavailable) {
            notice(L.t("Nu există o sesiune locală salvată"));
        }
    }

    private void openPlayer() {
        startActivity(new Intent(this, MediaActivity.class).putExtra("now", true));
    }

    private static String groupKey(String[] track, boolean album) {
        return album ? track[3] + " · " + track[2] : track[2];
    }

    private void groups(final Page groupPage) {
        page = groupPage;
        final boolean album = groupPage == Page.ALBUMS;
        TreeMap<String, ArrayList<String[]>> grouped = new TreeMap<String, ArrayList<String[]>>(String.CASE_INSENSITIVE_ORDER);
        for (String[] track : tracks) {
            String key = groupKey(track, album);
            ArrayList<String[]> group = grouped.get(key);
            if (group == null) {
                group = new ArrayList<String[]>();
                grouped.put(key, group);
            }
            group.add(track);
        }
        final ArrayList<String> keys = new ArrayList<String>(grouped.keySet());
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<String> images = new ArrayList<String>();
        for (String key : keys) {
            List<String[]> group = grouped.get(key);
            labels.add(key + " · " + group.size());
            images.add(artKey(group.get(0)));
        }
        prepareList(album ? L.t("Albume Leo") : L.t("Artiști Leo"), keys, images);
        list(listTitle, labels, 0, new Choice() {
            public void pick(int at) { trackPage(album ? Page.ALBUM : Page.ARTIST, keys.get(at)); }
        }, "", null);
        back = new Runnable() { public void run() { home(); } };
    }

    private void trackPage(final Page trackPage, final String argument) {
        page = trackPage;
        pageArgument = argument;
        String title;
        switch (trackPage) {
            case ALL: title = L.t("Toate piesele"); break;
            case FAVORITES: title = L.t("Piese favorite"); break;
            case RESULTS: title = L.t("Rezultate muzică"); break;
            default: title = argument; break;
        }
        final ArrayList<String[]> items = new ArrayList<String[]>();
        Set<String> favoriteIds = favorites();
        if (trackPage == Page.PLAYLIST) {
            List<String[]> saved = MusicLists.read(this).get(argument);
            if (saved != null) items.addAll(saved);
        } else {
            for (String[] track : tracks) {
                if (trackPage == Page.ALL
                    || (trackPage == Page.FAVORITES && favoriteIds.contains(track[0]))
                    || (trackPage == Page.ARTIST && groupKey(track, false).equalsIgnoreCase(argument))
                    || (trackPage == Page.ALBUM && groupKey(track, true).equalsIgnoreCase(argument))
                    || (trackPage == Page.RESULTS && GlobalSearchActivity.matches(track[1] + " " + track[2] + " " + track[3], argument)))
                    items.add(track);
            }
        }
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<String> keys = new ArrayList<String>();
        ArrayList<String> images = new ArrayList<String>();
        for (String[] track : items) {
            labels.add((favoriteIds.contains(track[0]) ? "★ " : "") + track[1] + (track.length > 2 ? "\n" + track[2] : ""));
            keys.add(track.length > 5 && track[5] != null && !track[5].isEmpty() ? track[5] : track[0]);
            images.add(artKey(track));
        }
        prepareList(title, keys, images);
        list(title, labels, 0, new Choice() {
            public void pick(int at) { play(items, at); }
        }, L.t("Opțiuni"), new Runnable() {
            public void run() { trackOptions(items); }
        });
        back = new Runnable() {
            public void run() {
                switch (trackPage) {
                    case ARTIST: groups(Page.ARTISTS); break;
                    case ALBUM: groups(Page.ALBUMS); break;
                    case PLAYLIST: playlists(); break;
                    case RESULTS: search(argument); break;
                    default: home(); break;
                }
            }
        };
    }

    private void play(List<String[]> items, int selected) {
        ArrayList<String> uris = new ArrayList<String>();
        ArrayList<String> titles = new ArrayList<String>();
        for (String[] track : items) {
            uris.add(track[0]);
            titles.add(track[1]);
        }
        startService(new Intent(this, PlaybackService.class).putStringArrayListExtra("queue", uris)
            .putStringArrayListExtra("titles", titles).putExtra("index", selected));
        openPlayer();
    }

    private void trackOptions(List<String[]> items) {
        final String[] selected = items.isEmpty() ? null : items.get(Math.min(activeIndex, items.size() - 1));
        if (selected == null) {
            options(L.t("Piese"), new String[]{L.t("Liste de redare"), L.t("Caută T9 / text")}, new Choice() {
                public void pick(int at) { if (at == 0) playlists(); else search(""); }
            });
            return;
        }
        options(L.t("Piese"), new String[]{L.t("Redă după piesa curentă"), L.t("Adaugă în coadă"),
            L.t("Adaugă / elimină favoritul"), L.t("Liste de redare"), L.t("Caută T9 / text")}, new Choice() {
            public void pick(int at) {
                if (at < 2) {
                    if (PlaybackService.queueTrack(MusicLibraryActivity.this, selected[0], selected[1], at == 0))
                        notice(L.t("Piesă adăugată în coadă"));
                    else notice(L.t("Coada de redare este plină"));
                } else if (at == 2) {
                    Set<String> ids = new HashSet<String>(favorites());
                    if (!ids.remove(selected[0])) ids.add(selected[0]);
                    getSharedPreferences("music-library", 0).edit().putStringSet("favorites", ids).apply();
                    trackPage(page, pageArgument);
                } else if (at == 3) playlists();
                else search("");
            }
        });
    }

    private Set<String> favorites() {
        return getSharedPreferences("music-library", 0).getStringSet("favorites", Collections.<String>emptySet());
    }

    private void search(String previous) {
        page = Page.SEARCH;
        LinearLayout form = fields(L.t("Caută muzică"));
        final EditText query = field(form, L.t("Titlu, artist, album sau cifre T9"), previous, InputType.TYPE_CLASS_TEXT);
        left = new Runnable() { public void run() { trackPage(Page.RESULTS, query.getText().toString().trim()); } };
        back = new Runnable() { public void run() { home(); } };
        footer(L.t("Caută"), "Editor", L.t("Șterge"));
        query.requestFocus();
    }

    private void prepareList(String title, List<String> keys, List<String> images) {
        listTitle = title;
        rowKeys.clear();
        rowKeys.addAll(keys);
        artKeys.clear();
        artKeys.addAll(images);
        activeIndex = 0;
    }

    private static String artKey(String[] track) { return track.length > 4 ? track[4] : ""; }

    @Override String rowMemoryKey(String title, int at, String label) {
        return title.equals(listTitle) && at >= 0 && at < rowKeys.size() ? rowKeys.get(at) : label;
    }

    @Override void onListSelection(String title, int at) {
        if (!title.equals(listTitle)) return;
        activeIndex = at;
        if (page == Page.HOME) homeIndex = at;
    }

    @Override Drawable rowIcon(String title, int at) {
        if (!title.equals(listTitle) || at < 0 || at >= artKeys.size()) return null;
        String uri = artKeys.get(at);
        if (page == Page.PLAYLISTS && uri.isEmpty()) return null;
        return artwork.get(uri.isEmpty() ? title + at : uri, uri.isEmpty() ? null : Uri.parse(uri), "♫");
    }

    private void playlists() {
        page = Page.PLAYLISTS;
        final TreeMap<String, ArrayList<String[]>> lists = MusicLists.read(this);
        final ArrayList<String> names = new ArrayList<String>(lists.keySet());
        ArrayList<String> images = new ArrayList<String>();
        for (String name : names) {
            String cover = getSharedPreferences("playlist-art", 0).getString(name, "");
            images.add(cover.matches("cover-[A-Za-z0-9_-]+\\.jpg")
                ? Uri.fromFile(new File(getFilesDir(), "playlist-covers/" + cover)).toString() : "");
        }
        prepareList(L.t("Liste de redare"), names, images);
        list(listTitle, names, 0, new Choice() {
            public void pick(int at) { playlistOptions(names.get(at), lists); }
        }, L.t("Salvează coada"), new Runnable() {
            public void run() { MusicLists.saveQueue(MusicLibraryActivity.this); }
        });
        back = new Runnable() { public void run() { home(); } };
    }

    private void playlistOptions(final String name, final TreeMap<String, ArrayList<String[]>> lists) {
        options(name, new String[]{L.t("Deschide"), L.t("Șterge lista · păstrează fișierele"),
            L.t("Editează lista și coperta")}, new Choice() {
            public void pick(int at) {
                if (at == 0) trackPage(Page.PLAYLIST, name);
                else if (at == 2) startActivity(new Intent(MusicLibraryActivity.this, PlaylistEditorActivity.class).putExtra("name", name));
                else options(L.t("Ștergi lista?"), new String[]{L.t("Păstrează"), L.t("Șterge lista")}, new Choice() {
                    public void pick(int yes) {
                        if (yes == 1) {
                            lists.remove(name);
                            MusicLists.write(MusicLibraryActivity.this, lists);
                            playlists();
                        }
                    }
                });
            }
        });
    }
}
