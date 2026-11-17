package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.graphics.*;import android.media.*;import android.os.*;import android.view.*;import java.util.*;
/** One visible-only native dashboard for music and FM. No additional audio session. */
public final class NowPlayingActivity extends LeoActivity {
 private final MusicTransport transport = new MusicTransport();
 private int source;private Dashboard face;private boolean visible,preview,previewPlaying;private int previewVolume=6;
 private final BroadcastReceiver changes=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(face!=null){face.invalidate();ticker.refresh();}}};
 private final ScreenTicker ticker=new ScreenTicker(this,new ScreenTicker.Work(){public long refresh(){
  if(face==null)return -1;face.invalidate();
  if(preview)return -1;
  return source==0&&PlaybackService.instance!=null&&PlaybackService.instance.playing()?1000:source==1&&FmService.playing?5000:-1;
 }});
 protected void onCreate(Bundle b){super.onCreate(b);preview=getIntent().getBooleanExtra("preview",false);source=b!=null?b.getInt("source",0):PlaybackService.instance!=null&&PlaybackService.instance.playing()?0:FmService.playing||FmService.starting?1:0;face=new Dashboard();setContentView(face);}
 protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("source",source);}
 protected void onResume(){super.onResume();palette=Appearance.palette(this);visible=true;IntentFilter f=new IntentFilter("ro.leo.visual.MUSIC_CHANGED");f.addAction(FmService.CHANGED);registerReceiver(changes,f);ticker.start();}
 protected void onPause(){transport.reset();visible=false;ticker.stop();unregisterReceiver(changes);super.onPause();}
 protected void onDestroy(){ticker.stop();super.onDestroy();}
 private void player(){if(preview){notice(L.t("DEMO · fără redare audio"));return;}startActivity(new Intent(this,source==1?FmActivity.class:MediaActivity.class).putExtra("now",true));}
 private void toggle(){if(preview){previewPlaying=!previewPlaying;face.invalidate();return;}if(source==1){if(FmService.playing||FmService.starting)stopService(new Intent(this,FmService.class));else player();}else if(PlaybackService.instance!=null)PlaybackService.instance.toggle();else player();face.invalidate();}
 private void previous(boolean next){if(preview){notice(L.t("DEMO · fără redare audio"));return;}if(source==1){if(FmService.instance!=null&&FmService.playing)FmService.instance.seek(next);else notice(L.t("Pornește radioul din player"));}else if(PlaybackService.instance!=null)PlaybackService.instance.next(next);else notice(L.t("Alege o piesă din bibliotecă"));face.invalidate();}
 private void changeSource() {
  transport.reset();
  source = 1 - source;
  face.invalidate();
 }

 private void volume(int direction) {
  if (preview) previewVolume = Math.max(0, Math.min(10, previewVolume + direction));
  else if (source == 1) {
   if (FmService.instance != null) FmService.instance.setVolume(FmService.volume + direction * 2);
   else notice(L.t("Pornește radioul pentru volum FM"));
  } else {
   ((AudioManager) getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
   face.state.refreshVolume();
  }
  face.invalidate();
 }
 private void stopSource() {
  if (preview) previewPlaying = false;
  else if (source == 1) stopService(new Intent(this, FmService.class));
  else if (PlaybackService.instance != null) PlaybackService.instance.stopPlayback();
  face.invalidate();
 }

 private void menu() {
  options(L.t("Media curentă"), new String[]{L.t("Deschide playerul"),
      source == 0 ? L.t("Sursă · Radio FM") : L.t("Sursă · Muzică"),
      L.t("Piesa / postul anterior"), L.t("Piesa / postul următor"),
      L.t("Oprește sursa"), ContextHelp.label()}, new Choice() {
   public void pick(int index) {
    if (index == 0) player();
    else if (index == 1) changeSource();
    else if (index == 2 || index == 3) previous(index == 3);
    else if (index == 4) stopSource();
    else showKeyHelp();
   }
  });
 }

 private void showKeyHelp() {
  String[] rows = source == 0
      ? new String[]{L.t("OK: redare / pauză"), L.t("4 / 6: derulează cu 10 s"),
          L.t("5: oprește redarea"), L.t("← / →: piesa anterioară / următoare"),
          "↑ / ↓: volum", L.t("*: schimbă sursa Muzică / FM"), L.t("#: deschide playerul sursei")}
      : new String[]{L.t("OK / 5: pauză sau oprire FM"), L.t("← / → sau 4 / 6: anterior / următor"),
          "↑ / ↓: volum", L.t("*: schimbă sursa Muzică / FM"), L.t("#: deschide playerul sursei")};
  options(L.t("Taste media"), rows, new Choice(){public void pick(int index){}});
 }
 public boolean onKeyUp(int key, KeyEvent event) {
  if (source == 0 && (transport.arrow(event, preview ? null : PlaybackService.instance)
          || transport.seekKey(event, null) || key == KeyEvent.KEYCODE_5)) {
   face.invalidate(); return true;
  }
  return super.onKeyUp(key, event);
 }
 @Override public boolean onKeyDown(int key, KeyEvent event) {
  if (source == 0) {
   PlaybackService music = preview ? null : PlaybackService.instance;
   if (transport.arrow(event, music)) return true;
   if (transport.seekKey(event, music)) { face.invalidate(); return true; }
  }
  if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
   volume(key == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1);
   return true;
  }
  boolean first = event.getRepeatCount() == 0;
  switch (key) {
   case KeyEvent.KEYCODE_MENU: if (first) menu(); return true;
   case KeyEvent.KEYCODE_DPAD_CENTER:
   case KeyEvent.KEYCODE_ENTER: if (first) toggle(); return true;
   case KeyEvent.KEYCODE_5: if (first) { if (source == 0) stopSource(); else toggle(); } return true;
   case KeyEvent.KEYCODE_DPAD_LEFT:
   case KeyEvent.KEYCODE_4: if (first) previous(false); return true;
   case KeyEvent.KEYCODE_DPAD_RIGHT:
   case KeyEvent.KEYCODE_6: if (first) previous(true); return true;
   case KeyEvent.KEYCODE_STAR: if (first) changeSource(); return true;
   case KeyEvent.KEYCODE_POUND: if (first) player(); return true;
   default: return super.onKeyDown(key, event);
  }
 }

 private final class Dashboard extends View {
  private final MediaPanel panel = new MediaPanel(NowPlayingActivity.this);
  private final MediaPanel.State state = new MediaPanel.State();
  private final AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);

  Dashboard() { super(NowPlayingActivity.this); setFocusable(true); requestFocus(); }

  protected void onDraw(Canvas canvas) {
   state.heading = L.t("Media curentă");
   state.showSources = true;
   state.radio = source == 1;
   if (state.radio) {
    state.cover = null;
    state.title = FmService.stationName.isEmpty() ? "Radio FM" : FmService.stationName;
    state.detail = String.format(Locale.ROOT, "%.1f MHz", preview ? 101.3f : FmService.frequency / 1000f);
    state.status = FmService.state;
    state.signal = preview ? L.t("Semnal · demonstrație") : FmService.scanning ? L.t("Scanare în curs")
        : FmService.seeking ? L.t("Caută postul…") : FmService.signal <= -128
        ? L.t("Semnal indisponibil") : L.t("Semnal · ") + FmService.signal + " dBm";
    state.playing = FmService.playing;
    state.volume = FmService.volume;
    state.volumeMax = 100;
   } else state.readMusic(audio);
   if (preview) {
    state.cover = null;
    state.playing = previewPlaying;
    state.title = state.radio ? "RADIO FM" : L.t("Lumini de seară");
    if (!state.radio) {
     state.artist = "Sesiuni";
     state.detail = L.t("Piesa ") + "1 / 1";
    }
    state.status = L.t("DEMO · fără redare");
    state.position = 85000;
    state.duration = 233000;
    state.volume = state.radio ? previewVolume * 10 : previewVolume;
    state.volumeMax = state.radio ? 100 : 10;
   }
   panel.draw(canvas, getWidth(), getHeight(), palette, state);
  }
 }
}
