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
        String heading, title, detail, status, signal, footer;
        String spectrumLabel;
        float[] spectrum;
        Bitmap cover;
        int position, duration, volume, volumeMax;
        boolean playing, radio, showSources;
        private long nextVolumeRead;

        void readMusic(AudioManager audio) {
            PlaybackService service = PlaybackService.instance;
            playing = service != null && service.playing();
            title = PlaybackService.title.isEmpty() ? L.t("Nicio piesă aleasă") : PlaybackService.title;
            status = PlaybackService.state;
            cover = service == null ? null : service.cover();
            detail = service == null || service.queue.titles.isEmpty() ? L.t("Bibliotecă Leo")
                : L.t("Piesa ") + (service.queue.index + 1) + " / " + service.queue.titles.size();
            position = service == null ? 0 : service.position();
            duration = service == null ? 0 : service.duration();
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
        c.scale(width / 240f, height / 302f);
        c.drawColor(palette.background);
        UiChrome.header(c, s.heading, palette, paint);
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
        text(c, lines[0], 101, 101, 16, Color.WHITE, 118);
        text(c, lines[1], 101, 122, 16, Color.WHITE, 118);
        text(c, s.detail, 101, 143, 12, palette.accent, 118);
        text(c, s.status, 14, 178, 13, palette.accent, 212);
        if (s.radio) {
            text(c, s.signal, 14, 210, 12, Color.WHITE, 212);
        } else {
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
        for (int i = 0; i < 3; i++) {
            int left = 12 + i * 74;
            box(c, left, 242, left + 68, 271, i == 1 ? palette.selection : palette.card);
            String label = i == 0 ? "4  ◀◀" : i == 2 ? "6  ▶▶" : s.playing
                ? (s.radio ? "5  Stop" : L.t("5  Pauză")) : L.t("5  Redă");
            text(c, label, left + 7, 261, 12, i == 1 ? palette.ink : Color.WHITE, 56);
        }
        box(c, 0, 280, 240, 302, palette.bar);
        text(c, L.t("Opțiuni"), 7, 295, 11, Color.WHITE, 62);
        text(c, s.footer, 76, 295, 10, palette.accent, 108);
        paint.setTextSize(11);
        String back = KeypadText.fit(paint, L.t("Înapoi"), 47);
        text(c, back, 233 - paint.measureText(back), 295, 11, Color.WHITE, 47);
        c.restore();
    }

    private void source(Canvas c, String label, int x, boolean selected, Appearance.Palette palette) {
        box(c, x, 43, x + 104, 65, selected ? palette.selection : palette.card);
        text(c, label, x + 11, 58, 12, selected ? palette.ink : Color.WHITE, 84);
    }

    private void drawArtwork(Canvas c, Appearance.Palette palette, State s) {
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
