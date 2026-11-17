package ro.leo.visual;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.content.Context;
import android.os.SystemClock;
import java.io.File;
import ro.leo.i18n.L;

/** Shared 240 × 302 layout for the music player and Media Current. */
final class MediaPanel {
    static final class State {
        String heading, title, detail, status, signal;
        String artist = "";
        String spectrumLabel;
        float[] spectrum;
        Bitmap cover;
        int position, duration, volume, volumeMax;
        boolean playing, radio, showSources;
        boolean internet, canStop;
        String stationMark;
        int stationColor;
        private long nextVolumeRead;

        void readMusic(AudioManager audio) {
            PlaybackService service = PlaybackService.instance;
            playing = service != null && service.playing();
            title = PlaybackService.title.isEmpty() ? L.t("Nicio piesă aleasă") : PlaybackService.title;
            artist = service == null ? "" : service.artist();
            status = PlaybackService.state;
            cover = service == null ? null : service.cover();
            detail = service == null || service.queue.titles.isEmpty() ? L.t("Bibliotecă")
                : L.t("Piesa ") + (service.queue.index + 1) + " / " + service.queue.titles.size();
            position = service == null ? 0 : service.position();
            duration = service == null ? 0 : service.duration();
            readVolume(audio);
        }

        void readVolume(AudioManager audio) {
            long now = SystemClock.elapsedRealtime();
            if (now >= nextVolumeRead) {
                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                volumeMax = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                nextVolumeRead = now + 350;
            }
        }

        void refreshVolume() { nextVolumeRead = 0; }
    }

    private static final Typeface FONT = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF artwork = new RectF(20, 81, 88, 149);
    private final Bitmap placeholder;

    MediaPanel(Context context) {
        placeholder = loadPlaceholder(new File(context.getFilesDir(), "music-placeholder.png"));
    }

    private static Bitmap loadPlaceholder(File file) {
        if (!file.isFile() || file.length() > 256 * 1024) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        int largest = Math.max(options.outWidth, options.outHeight);
        if (options.outWidth < 1 || options.outHeight < 1 || largest > 1024) return null;
        options.inJustDecodeBounds = false;
        options.inSampleSize = Integer.highestOneBit(Math.max(1, largest / 100));
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    void draw(Canvas c, int width, int height, Appearance.Palette palette, State s) {
        c.save();
        c.scale(width / 240f, height / (s.internet ? 240f : 302f));
        if (s.internet) c.translate(0, -40);
        c.drawColor(palette.background);
        if (!s.internet) UiChrome.header(c, s.heading, palette, paint);
        if (s.showSources) {
            source(c, L.t("MUZICĂ"), 12, !s.radio, palette);
            source(c, "RADIO FM", 124, s.radio, palette);
        } else {
            text(c, s.spectrumLabel, 13, 59, 10, palette.accent, 110);
            if (s.spectrum != null) for (int i = 0; i < s.spectrum.length; i++) {
                float x = 130 + i * 8;
                box(c, x, 61 - 16 * s.spectrum[i], x + 5, 63,
                    s.spectrum[i] > 0 ? palette.accent : palette.selection);
            }
        }
        box(c, 12, 73, 228, 158, palette.card);
        drawArtwork(c, palette, s);
        paint.setTypeface(FONT);
        paint.setTextSize(16);
        String[] lines = KeypadText.twoLines(paint, s.title, 118);
        if (s.radio || s.internet) {
            text(c, lines[0], 101, 101, 16, Color.WHITE, 118);
            text(c, lines[1], 101, 122, 16, Color.WHITE, 118);
            text(c, s.detail, 101, 143, 12, palette.accent, 118);
            text(c, s.status, 14, 178, 13, palette.accent, 212);
            text(c, s.signal, 14, 210, 12, Color.WHITE, 212);
        } else {
            boolean twoLines = !lines[1].isEmpty();
            text(c, lines[0], 101, twoLines ? 101 : 108, 16, Color.WHITE, 118);
            if (twoLines) text(c, lines[1], 101, 121, 16, Color.WHITE, 118);
            text(c, s.artist, 101, twoLines ? 143 : 130, 12, palette.accent, 118);
            text(c, s.status, 14, 178, 13, palette.accent, 124);
            paint.setTextSize(11);
            String detail = KeypadText.fit(paint, s.detail, 82);
            text(c, detail, 226 - paint.measureText(detail), 178, 11, Color.WHITE, 82);
            box(c, 14, 190, 226, 195, Appearance.mix(palette.card, palette.accent, .25f));
            float progress = s.duration > 0 ? Math.max(0, Math.min(1, s.position / (float) s.duration)) : 0;
            if (progress > 0) box(c, 14, 190, 14 + progress * 212, 195, palette.accent);
            text(c, MediaActivity.format(s.position) + " / " + MediaActivity.format(s.duration),
                14, 211, 12, Color.WHITE, 212);
        }
        text(c, "VOL " + s.volume + (s.radio ? "%" : " / " + s.volumeMax), 14, 232, 11, Color.WHITE, 105);
        for (int i = 0; i < 10; i++) {
            box(c, 130 + i * 10, 224, 137 + i * 10, 231,
                (i + 1) * s.volumeMax <= s.volume * 10 ? palette.accent : palette.selection);
        }
        drawControls(c, palette, s);
        if (!s.internet) {
            box(c, 0, 280, 240, 302, palette.bar);
            text(c, L.t("Opțiuni"), 7, 295, 11, Color.WHITE, 62);
            paint.setTextSize(11);
            String back = KeypadText.fit(paint, L.t("Înapoi"), 47);
            text(c, back, 233 - paint.measureText(back), 295, 11, Color.WHITE, 47);
        }
        c.restore();
    }

    private void drawControls(Canvas c, Appearance.Palette palette, State s) {
        if (s.radio || s.internet) {
            control(c, palette, "4  ◀◀", 12, 68, false, 12);
            control(c, palette, (s.internet ? s.canStop : s.playing) ? "5  Stop" : L.t("5  Redă"), 86, 68, true, 12);
            control(c, palette, "6  ▶▶", 160, 68, false, 12);
            return;
        }
        control(c, palette, "4 −10 s", 12, 51, false, 11);
        control(c, palette, s.playing ? "OK  ‖" : "OK  ▶", 67, 51, true, 11);
        control(c, palette, "5 Stop", 122, 51, false, 11);
        control(c, palette, "6 +10 s", 177, 51, false, 11);
    }

    private void control(Canvas c, Appearance.Palette palette, String label, int left, int width,
                         boolean primary, int size) {
        box(c, left, 242, left + width, 271, primary ? palette.selection : palette.card);
        text(c, label, left + 5, 261, size, primary ? palette.ink : Color.WHITE, width - 10);
    }

    private void source(Canvas c, String label, int x, boolean selected, Appearance.Palette palette) {
        box(c, x, 43, x + 104, 65, selected ? palette.selection : palette.card);
        text(c, label, x + 11, 58, 12, selected ? palette.ink : Color.WHITE, 84);
    }

    private void drawArtwork(Canvas c, Appearance.Palette palette, State s) {
        if (s.internet) {
            box(c, 20, 81, 88, 149, s.stationColor);
            paint.setTypeface(FONT);
            paint.setTextSize(23);
            text(c, s.stationMark, 54 - paint.measureText(s.stationMark) / 2, 123, 23, 0xff141b27, 63);
            return;
        }
        Bitmap cover = s.cover != null && !s.cover.isRecycled() ? s.cover : s.radio ? null : placeholder;
        if (cover != null) {
            paint.setColor(Color.WHITE);
            c.drawBitmap(cover, null, artwork, paint);
            return;
        }
        box(c, 20, 81, 88, 149, Appearance.mix(palette.accent, palette.background, .75f));
        paint.setColor(palette.accent);
        if (s.radio) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            c.drawRoundRect(30, 105, 78, 133, 3, 3, paint);
            c.drawLine(38, 105, 68, 89, paint);
            c.drawCircle(64, 120, 7, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            c.drawCircle(54, 115, 25, paint);
            paint.setColor(palette.background);
            c.drawCircle(54, 115, 17, paint);
            paint.setColor(palette.accent);
            c.drawCircle(54, 115, 5, paint);
        }
    }

    private void box(Canvas c, float left, float top, float right, float bottom, int color) {
        paint.setShader(null);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(left, top, right, bottom, 5, 5, paint);
    }

    private void text(Canvas c, String label, float x, float baseline, int size, int color, float width) {
        paint.setTypeface(FONT);
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawText(KeypadText.fit(paint, label, width), x, baseline, paint);
    }
}
