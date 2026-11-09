package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;import android.graphics.*;import android.media.AudioManager;import android.media.audiofx.Visualizer;import android.view.View;
/** Real FFT display for the current player session; never captures microphone input. */
final class MusicFace extends View {
    private final MediaPanel panel;
    private final MediaPanel.State state = new MediaPanel.State();
    private final AudioManager audio;
    private final boolean preview;
    private Appearance.Palette palette;private Visualizer visualizer;private int session=-1;private volatile float[] spectrum=new float[12];private String meter=L.t("Spectru · în așteptare");private boolean failed;private volatile boolean captured;
    MusicFace(Context c, boolean preview) {
        super(c);
        this.preview = preview;
        panel = new MediaPanel(c);
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        palette = Appearance.palette(c);
        setFocusable(true);
        requestFocus();
    }
    void appearance(){palette=Appearance.palette(getContext());invalidate();}
    void volumeChanged(){state.refreshVolume();invalidate();}
    void release(){if(visualizer!=null){try{visualizer.setEnabled(false);visualizer.release();}catch(RuntimeException ignored){}visualizer=null;}session=-1;captured=false;spectrum=new float[12];}
    void tick(){if (preview) { invalidate(); return; } PlaybackService service=PlaybackService.instance;int wanted=service==null?0:service.audioSession();boolean playing=service!=null&&service.playing();
        if(wanted!=session){release();session=wanted;failed=false;if(wanted>0)try{visualizer=new Visualizer(wanted);if(visualizer.setCaptureSize(256)!=Visualizer.SUCCESS)throw new IllegalStateException();if(visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){public void onWaveFormDataCapture(Visualizer v,byte[] data,int rate){}public void onFftDataCapture(Visualizer v,byte[] fft,int rate){float[] values=new float[12];for(int i=0;i<values.length;i++){int lo=1+(i*i*126)/(12*12),hi=Math.max(lo+1,1+((i+1)*(i+1)*126)/(12*12));float energy=0;for(int bin=lo;bin<hi&&bin*2+1<fft.length;bin++)energy=Math.max(energy,(float)Math.hypot(fft[bin*2],fft[bin*2+1]));values[i]=(float)Math.min(1,Math.log1p(energy)/Math.log(128));}spectrum=values;captured=true;}},Math.min(8000,Visualizer.getMaxCaptureRate()),false,true)!=Visualizer.SUCCESS)throw new IllegalStateException();if(visualizer.setEnabled(playing)!=Visualizer.SUCCESS)throw new IllegalStateException();}catch(RuntimeException e){release();session=wanted;failed=true;}}
        if(visualizer!=null)try{if(visualizer.getEnabled()!=playing&&visualizer.setEnabled(playing)!=Visualizer.SUCCESS)throw new IllegalStateException();}catch(RuntimeException e){release();session=wanted;failed=true;}
        meter=failed?L.t("Spectru indisponibil"):playing?(captured?L.t("SPECTRU AUDIO · LIVE"):L.t("Spectru · conectare…")):L.t("Spectru · pauză");if(!playing)spectrum=new float[12];invalidate();
    }
    static String[] titleLines(String title, Paint paint, float width) {
        return KeypadText.twoLines(paint, title, width);
    }

    protected void onDraw(Canvas canvas) {
        long sleep = preview ? 0 : MusicSleep.remaining(getContext());
        state.heading = sleep > 0 ? L.t("Muzică · ") + (sleep + 59999) / 60000 + " min" : L.t("Muzică Leo");
        state.footer = L.t("# Bibliotecă");
        state.spectrumLabel = meter;
        state.spectrum = spectrum;
        state.readMusic(audio);
        if (preview) {
            state.title = L.t("Lumini de seară");
            state.detail = "Album · Leo Sessions";
            state.status = L.t("DEMO · fără redare");
            state.cover = null;
            state.playing = false;
            state.position = 85000;
            state.duration = 233000;
            state.volume = 6;
            state.volumeMax = 10;
        }
        panel.draw(canvas, getWidth(), getHeight(), palette, state);
    }
}
