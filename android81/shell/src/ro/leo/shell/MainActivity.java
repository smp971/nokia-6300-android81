package ro.leo.shell;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** A keypad-first HOME. No WebView, image wallpaper or continuous redraw. */
public class MainActivity extends Activity {
    private ShellView shell;
    private boolean traceInput;
    private long traceSequence, pendingInput;
    private final Handler handler = new Handler();
    private final Runnable tick = new Runnable() {
        public void run() {
            if (shell != null) { shell.refreshStatus(); shell.invalidate(); }
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };

    public void onCreate(Bundle state) {
        super.onCreate(state);
        traceInput = getIntent().getBooleanExtra("trace_input", false);
        // Keep Android's compact SystemUI status bar (battery, radio, clock).
        getWindow().setStatusBarColor(Color.rgb(12, 20, 30));
        shell = new ShellView();
        setContentView(shell);
    }
    protected void onResume() {
        super.onResume();
        shell.refreshApps();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        traceInput = intent.getBooleanExtra("trace_input", false);
        shell.apps = false;
        shell.message = "";
        shell.invalidate();
    }
    public void onBackPressed() {
        shell.apps = false;
        shell.message = "";
        shell.invalidate();
    }
    public boolean onKeyDown(int key, KeyEvent event) {
        if (traceInput && (key >= KeyEvent.KEYCODE_DPAD_UP && key <= KeyEvent.KEYCODE_DPAD_CENTER)) {
            pendingInput = ++traceSequence;
            Log.i("LeoInputTrace", "key seq=" + pendingInput + " event_ms=" + event.getEventTime()
                    + " dispatch_ns=" + System.nanoTime());
        }
        if (shell.handle(key)) return true;
        return super.onKeyDown(key, event);
    }

    private class ShellView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<ResolveInfo> entries = new ArrayList<ResolveInfo>();
        final HashMap<String, String> labels = new HashMap<String, String>();
        final int background = Color.rgb(12, 20, 30);
        final int accent = Color.rgb(107, 224, 176);
        boolean apps;
        int selected;
        String message = "";
        String clockText = "", dateText = "", networkText = "";
        final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM", new Locale("ro"));
        final RectF cellBounds = new RectF();

        ShellView() {
            super(MainActivity.this);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        }
        void refreshStatus() {
            Date now = new Date();
            clockText = clockFormat.format(now);
            dateText = dateFormat.format(now);
            networkText = "Rețea indisponibilă";
            try {
                String name = ((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).getNetworkOperatorName();
                if (name != null && !name.isEmpty()) networkText = name;
            } catch (RuntimeException ignored) { }
        }
        void refreshApps() {
            String previous = entries.isEmpty() ? "" : component(entries.get(selected)).flattenToString();
            entries.clear();
            labels.clear();
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo info : getPackageManager().queryIntentActivities(query, 0)) {
                String pkg = info.activityInfo.packageName;
                if (!pkg.equals(getPackageName()) && !pkg.equals("com.android.launcher3")) {
                    entries.add(info);
                    labels.put(component(info).flattenToString(), info.loadLabel(getPackageManager()).toString());
                }
            }
            Collections.sort(entries, new Comparator<ResolveInfo>() {
                public int compare(ResolveInfo a, ResolveInfo b) {
                    return labels.get(component(a).flattenToString()).compareToIgnoreCase(
                            labels.get(component(b).flattenToString()));
                }
            });
            selected = Math.min(selected, Math.max(0, entries.size() - 1));
            for (int i = 0; i < entries.size(); i++) {
                if (component(entries.get(i)).flattenToString().equals(previous)) selected = i;
            }
            invalidate();
        }
        ComponentName component(ResolveInfo info) {
            return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        }
        void open() {
            if (entries.isEmpty()) return;
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component(entries.get(selected)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try { startActivity(launch); }
            catch (RuntimeException failure) { message = "Aplicația nu poate porni"; invalidate(); }
        }
        boolean handle(int key) {
            if (key == KeyEvent.KEYCODE_MENU) {
                apps = !apps; message = ""; invalidate(); return true;
            }
            if (key == KeyEvent.KEYCODE_BACK) {
                onBackPressed(); return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (apps) open(); else { apps = true; invalidate(); }
                return true;
            }
            int delta = key == KeyEvent.KEYCODE_DPAD_UP ? -2 :
                    key == KeyEvent.KEYCODE_DPAD_DOWN ? 2 :
                    key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 :
                    key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (delta != 0) {
                apps = true;
                if (!entries.isEmpty()) selected = Math.max(0, Math.min(entries.size() - 1, selected + delta));
                message = ""; invalidate(); return true;
            }
            return false;
        }
        void text(Canvas canvas, String value, float x, float y, float size, int color, Paint.Align align) {
            paint.setColor(color); paint.setTextSize(size); paint.setTextAlign(align);
            canvas.drawText(value, x, y, paint);
        }
        String fit(String value, float width, float size) {
            paint.setTextSize(size);
            if (paint.measureText(value) <= width) return value;
            while (value.length() > 1 && paint.measureText(value + "…") > width) value = value.substring(0, value.length() - 1);
            return value + "…";
        }
        protected void onDraw(Canvas output) {
            super.onDraw(output);
            if (traceInput && pendingInput != 0) {
                Log.i("LeoInputTrace", "draw seq=" + pendingInput + " draw_ns=" + System.nanoTime());
                pendingInput = 0;
            }
            // Layout in the panel's physical 240x320 coordinates; density-independent.
            output.save(); output.scale(getWidth() / 240f, getHeight() / 320f);
            output.drawColor(background);
            String time = clockText;
            text(output, "LEO", 12, 22, 13, accent, Paint.Align.LEFT);
            text(output, time, 228, 22, 13, Color.WHITE, Paint.Align.RIGHT);
            paint.setColor(Color.rgb(38, 52, 64)); output.drawRect(12, 31, 228, 32, paint);
            if (!apps) {
                text(output, time, 120, 128, 51, Color.WHITE, Paint.Align.CENTER);
                text(output, dateText, 120, 155, 15, Color.LTGRAY, Paint.Align.CENTER);
                text(output, fit(networkText, 216, 13), 120, 201, 13, accent, Paint.Align.CENTER);
                text(output, "OK pentru aplicații", 120, 249, 15, Color.WHITE, Paint.Align.CENTER);
            } else {
                text(output, "Aplicații", 12, 55, 18, Color.WHITE, Paint.Align.LEFT);
                int page = selected / 6;
                for (int cell = 0; cell < 6; cell++) {
                    int index = page * 6 + cell;
                    if (index >= entries.size()) break;
                    float left = 10 + cell % 2 * 115;
                    float top = 70 + cell / 2 * 62;
                    boolean active = index == selected;
                    paint.setColor(active ? accent : Color.rgb(29, 42, 55));
                    cellBounds.set(left, top, left + 105, top + 53);
                    output.drawRoundRect(cellBounds, 5, 5, paint);
                    String label = labels.get(component(entries.get(index)).flattenToString());
                    text(output, fit(label, 93, 13), left + 52.5f, top + 32, 13,
                            active ? background : Color.WHITE, Paint.Align.CENTER);
                }
                text(output, entries.isEmpty() ? "Nicio aplicație" : (selected + 1) + " / " + entries.size(),
                        120, 278, 12, Color.LTGRAY, Paint.Align.CENTER);
            }
            if (!message.isEmpty()) text(output, message, 120, 280, 11, accent, Paint.Align.CENTER);
            paint.setColor(Color.rgb(27, 44, 55)); output.drawRect(0, 291, 240, 320, paint);
            text(output, apps ? "Acasă" : "Meniu", 8, 310, 13, Color.WHITE, Paint.Align.LEFT);
            text(output, apps ? "Deschide" : "Aplicații", 120, 310, 12, accent, Paint.Align.CENTER);
            text(output, apps ? "Înapoi" : "—", 232, 310, 13, Color.WHITE, Paint.Align.RIGHT);
            output.restore();
        }
    }
}
