package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.widget.*;
import java.util.*;

public final class MediaActivity extends LeoActivity {
    private boolean video,playerScreen,preview;
    private final MusicTransport transport = new MusicTransport();private int selected;private final ArrayList<Uri> uris=new ArrayList<Uri>();private final ArrayList<String> titles=new ArrayList<String>();
    private final Handler handler=new Handler();private MusicFace face;private boolean resumed;
    private final Runnable refresh=new Runnable(){public void run(){if(resumed&&playerScreen&&face!=null){face.tick();PlaybackService s=PlaybackService.instance;handler.postDelayed(this,s!=null&&s.playing()?125:1000);}}};
    static String format(int ms){int s=ms/1000;return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    protected void onCreate(Bundle state){super.onCreate(state);video="video".equals(getIntent().getStringExtra("kind"));preview=getIntent().getBooleanExtra("preview",false);if(!preview&&!video&&getIntent().getData()!=null){startService(new Intent(this,PlaybackService.class).setData(getIntent().getData()).putExtra("title",getIntent().getData().getLastPathSegment()));now();}else if(getIntent().getBooleanExtra("now",false))now();else load();}
    protected void onResume(){super.onResume();resumed=true;if(face!=null)face.appearance();if(playerScreen){handler.removeCallbacks(refresh);handler.post(refresh);}}
    protected void onPause(){transport.reset();resumed=false;handler.removeCallbacks(refresh);if(face!=null)face.release();super.onPause();}
    private void load(){
        if(!video){startActivity(new Intent(this,MusicLibraryActivity.class));finish();return;}
        playerScreen=false;handler.removeCallbacks(refresh);if(face!=null)face.release();worker.execute(new Runnable(){public void run(){
            final ArrayList<Uri> found = new ArrayList<Uri>();
            final ArrayList<String> labels = new ArrayList<String>();
            Uri base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            try (Cursor cursor = getContentResolver().query(base, new String[]{"_id", "_display_name", "duration"},
                    null, null, "_display_name COLLATE NOCASE ASC")) {
                while (cursor != null && cursor.moveToNext() && found.size() < 500) {
                    found.add(ContentUris.withAppendedId(base, cursor.getLong(0)));
                    labels.add(cursor.getString(1) + "\n" + format(cursor.getInt(2)));
                }
                ui(new Runnable(){public void run(){uris.clear();uris.addAll(found);titles.clear();titles.addAll(labels);library();}});
            }catch(RuntimeException denied){ui(new Runnable(){public void run(){notice(L.t("Biblioteca nu poate fi citită"));library();}});}
        }});
    }
    private void library(){
        playerScreen=false;handler.removeCallbacks(refresh);if(face!=null)face.release();list(video?"Video Leo":L.t("Muzică Leo"),titles,selected,new Choice(){public void pick(int i){selected=i;play();}},L.t("Opțiuni"),new Runnable(){public void run(){options(L.t("Bibliotecă"),new String[]{L.t("Reîncarcă"), L.t("Redare curentă"), L.t("Oprește muzica")},new Choice(){public void pick(int i){if(i==0)load();else if(i==1)now();else stopService(new Intent(MediaActivity.this,PlaybackService.class));}});}});
    }
    private void play() {
        startActivity(new Intent(this, VideoActivity.class).setData(uris.get(selected))
            .putExtra("title", titles.get(selected).split("\n")[0]));
    }

    private void openLibrary() {
        startActivity(new Intent(this, MusicLibraryActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void now() {
        playerScreen = true;
        if (face != null) face.release();
        face = new MusicFace(this, preview);
        setContentView(face);
        left = new Runnable() { public void run() { playerOptions(0); } };
        back = null;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    private void playerOptions(int selected) {
        new KeypadDialog(this, L.t("Redare"), new String[]{L.t("Bibliotecă"), L.t("Coada de redare"),
            L.t("Detalii piesă"), L.t("Versuri locale · LRC"), L.t("Setări redare"), L.t("Oprește")}, new KeypadDialog.Action() {
            public void select(int index) {
                if (preview) return;
                if (index == 0) openLibrary();
                else if (index == 1) queue();
                else if (index == 2) trackDetails();
                else if (index == 3) startActivity(new Intent(MediaActivity.this, LyricsActivity.class));
                else if (index == 4) playbackSettings();
                else {
                    stopService(new Intent(MediaActivity.this, PlaybackService.class));
                    face.invalidate();
                }
            }
        }).initialSelection(selected).show();
    }

    private void trackDetails() {
        PlaybackService service = PlaybackService.instance;
        if (service == null || service.queue.index < 0 || service.queue.index >= service.queue.titles.size()) {
            notice(L.t("Nicio piesă aleasă"));
            return;
        }
        int index = service.queue.index;
        playerScreen = false;
        handler.removeCallbacks(refresh);
        face.release();
        begin(L.t("Detalii piesă"));
        TextView title = text(service.queue.titles.get(index), 20);
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(title);
        root.addView(text(L.t("Durată · ") + format(service.duration()), 15));
        root.addView(text(L.t("Piesa ") + (index + 1) + " / " + service.queue.titles.size(), 15));
        back = new Runnable() { public void run() { now(); } };
        footer("", "", L.t("Înapoi"));
    }

    private void playbackSettings() {
        PlaybackService current = PlaybackService.instance;
        new KeypadDialog(this, L.t("Setări redare"), new String[]{current != null && current.queue.shuffle
            ? L.t("Aleatoriu · pornit") : L.t("Aleatoriu · oprit"), L.t("Repetare"),
            L.t("Egalizator"), L.t("Oprire temporizată"), L.t("Salvează lista"),
            L.t("Reia ultima sesiune"), L.t("Partajează piesa"), L.t("Reluare automată · piese lungi")},
            new KeypadDialog.Action() {
                public void select(int index) {
                    PlaybackService service = PlaybackService.instance;
                    switch (index) {
                        case 0:
                            if (service == null) notice(L.t("Nu există redare activă"));
                            else service.modes(!service.queue.shuffle, service.queue.repeat);
                            break;
                        case 1:
                            if (service == null) { notice(L.t("Nu există redare activă")); break; }
                            options(L.t("Repetare"), new String[]{L.t("Fără repetare"), L.t("Repetă lista"),
                                L.t("Repetă piesa")}, new Choice() {
                                public void pick(int mode) {
                                    PlaybackService active = PlaybackService.instance;
                                    if (active != null) active.modes(active.queue.shuffle, mode);
                                }
                            });
                            break;
                        case 2: equalizer(); break;
                        case 3: sleepMenu(); break;
                        case 4: MusicLists.saveQueue(MediaActivity.this); break;
                        case 5: resumeLast(); break;
                        case 6: shareTrack(service); break;
                        case 7:
                            options(L.t("Reia poziția · minimum 10 minute"), new String[]{L.t("Pornit"), L.t("Oprit")},
                                new Choice() { public void pick(int mode) {
                                    getSharedPreferences("music-player", 0).edit().putBoolean("track-resume", mode == 0).apply();
                                }});
                            break;
                    }
                }
            }).onBack(new Runnable() { public void run() { playerOptions(4); } }).show();
    }

    private void shareTrack(PlaybackService service) {
        if (service == null || service.queue.index < 0 || service.queue.index >= service.queue.sources.size()) {
            notice(L.t("Nicio piesă aleasă"));
            return;
        }
        Uri uri = Uri.parse(service.queue.sources.get(service.queue.index));
        ShareHubActivity.send(this, uri, getContentResolver().getType(uri), PlaybackService.title);
    }
    private void equalizer(){final PlaybackService s=PlaybackService.instance;if(s==null||s.audioSession()<=0){notice(L.t("Pornește întâi o piesă"));return;}final String[] presets=s.equalizer.presets(s.audioSession());if(presets.length==0){notice(L.t("Egalizator indisponibil pe această rută audio"));return;}ArrayList<String> labels=new ArrayList<String>();labels.add(L.t("Oprit"));labels.addAll(Arrays.asList(presets));options(L.t("Egalizator"),labels.toArray(new String[0]),new Choice(){public void pick(int n){if(!s.equalizer.apply(MediaActivity.this,s.audioSession(),n-1))notice(L.t("Efectul audio nu a putut fi activat"));}});}
    private void resumeLast(){startService(new Intent(this,PlaybackService.class).setAction("resume"));now();}
    private void sleepMenu(){final PlaybackService s=PlaybackService.instance;if(s==null){notice(L.t("Pornește o piesă înainte de temporizator"));return;}long left=MusicSleep.remaining(this);options(L.t("Oprire · ")+(left>0?(left+59999)/60000+L.t(" min rămase"):L.t("inactivă")),new String[]{L.t("5 minute"),L.t("15 minute"),L.t("30 minute"),L.t("60 minute"),L.t("Anulează oprirea")},new Choice(){public void pick(int n){if(n==4)MusicSleep.cancel(MediaActivity.this);else MusicSleep.set(MediaActivity.this,new int[]{5,15,30,60}[n]);notice(n==4?L.t("Oprire anulată"):L.t("Muzica se va opri automat"));}});}
    private void queue(){playerScreen=false;handler.removeCallbacks(refresh);if(face!=null)face.release();final PlaybackService s=PlaybackService.instance;if(s==null){load();return;}ArrayList<String> labels=new ArrayList<String>();for(int i=0;i<s.queue.titles.size();i++)labels.add((i==s.queue.index?"▶ ":"")+s.queue.titles.get(i));list(L.t("Coada de redare"),labels,Math.max(0,s.queue.index),new Choice(){public void pick(final int at){options(L.t("Piesa ")+(at+1),new String[]{L.t("Redă"),L.t("Mută mai sus"),L.t("Mută mai jos"),L.t("Elimină din coadă")},new Choice(){public void pick(int action){if(PlaybackService.instance!=s){load();return;}if(action==0){s.choose(at);now();}else{if(action==3)s.remove(at);else s.move(at,action==1?Math.max(0,at-1):Math.min(s.queue.titles.size()-1,at+1));queue();}}});}},"Player",new Runnable(){public void run(){now();}});back=new Runnable(){public void run(){now();}};}
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (!playerScreen) return super.onKeyDown(key, event);
        PlaybackService service = preview ? null : PlaybackService.instance;
        if (transport.arrow(event, service)) return true;
        if (key == KeyEvent.KEYCODE_POUND) {
            if (!preview && event.getRepeatCount() == 0) openLibrary();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_5) {
            if (event.getRepeatCount() == 0 && !preview) {
                if (service == null) openLibrary();
                else { service.toggle(); face.tick(); }
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_4 || key == KeyEvent.KEYCODE_6) {
            if (event.getRepeatCount() == 0 && service != null) { service.next(key == KeyEvent.KEYCODE_6); face.tick(); }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (!preview) {
                android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                    key == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1, 0);
                face.volumeChanged();
            }
            return true;
        }
        return super.onKeyDown(key, event);
    }

    @Override public boolean onKeyUp(int key, KeyEvent event) {
        if (playerScreen && transport.arrow(event, preview ? null : PlaybackService.instance)) {
            face.invalidate();
            return true;
        }
        return super.onKeyUp(key, event);
    }
}
