package com.android.incallui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Small, native keypad controls shared by the real call screen and offline preview. */
public final class LeoCallKeys {
  public interface Host {
    boolean ringing(); boolean connected(); boolean muted(); boolean speaker();
    void answer(); void end(); void mute(); void route(); void tone(char digit, boolean down);
  }
  private final Activity activity;
  private final Host host;
  private final Handler handler = new Handler();
  private TextView bar;
  private int selection;
  private char sounding;
  private final Runnable refresh = new Runnable() {
    public void run() { render(); handler.postDelayed(this, 500); }
  };
  public LeoCallKeys(Activity activity, Host host) { this.activity = activity; this.host = host; }
  public void attach() {
    FrameLayout content = activity.findViewById(android.R.id.content);
    int height = (int) (48 * activity.getResources().getDisplayMetrics().density + .5f);
    // Reserve space instead of covering the original call controls.
    View original = content.getChildAt(0);
    FrameLayout.LayoutParams originalParams = new FrameLayout.LayoutParams(-1, -1);
    originalParams.bottomMargin = height;
    original.setLayoutParams(originalParams);
    bar = new TextView(activity);
    bar.setTextColor(Color.WHITE);
    bar.setBackgroundColor(0xff171126);
    bar.setPadding(6, 4, 6, 4);
    bar.setTextSize(12);
    bar.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM);
    content.addView(bar, params);
    render();
  }
  public void resume() { handler.removeCallbacks(refresh); handler.post(refresh); }
  public void pause() {
    handler.removeCallbacks(refresh);
    if (sounding != 0) { host.tone(sounding, false); sounding = 0; }
  }
  private void render() {
    String text = host.ringing() ? "Verde / OK: răspunde\nRăspunde                       Respinge"
        : host.connected() ? "◀ " + (selection == 0
            ? "Microfon: " + (host.muted() ? "oprit" : "pornit")
            : "Difuzor: " + (host.speaker() ? "pornit" : "oprit"))
            + " ▶\nOpțiuni          OK          Închide"
        : "Apel în curs de conectare\n                              Anulează";
    if (!text.contentEquals(bar.getText())) {
      android.text.SpannableString flat = new android.text.SpannableString(text);
      int end = text.indexOf('\n');
      if (end > 0) flat.setSpan(new android.text.style.ForegroundColorSpan(0xfff1b8d4), 0, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      bar.setText(flat);
    }
  }
  public boolean key(KeyEvent event) {
    int key = event.getKeyCode();
    boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
    if (!down && event.getAction() != KeyEvent.ACTION_UP) return false;
    char digit = key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9
        ? (char) ('0' + key - KeyEvent.KEYCODE_0)
        : key == KeyEvent.KEYCODE_STAR ? '*' : key == KeyEvent.KEYCODE_POUND ? '#' : 0;
    if (digit != 0) {
      if (down && event.getRepeatCount() == 0 && host.connected() && !host.ringing()) {
        if (sounding != 0) host.tone(sounding, false);
        sounding = digit;
        host.tone(digit, true);
      } else if (!down && sounding == digit) { host.tone(digit, false); sounding = 0; }
      return true;
    }
    if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU
        || key == KeyEvent.KEYCODE_CALL || key == KeyEvent.KEYCODE_DPAD_CENTER
        || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_LEFT
        || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_UP
        || key == KeyEvent.KEYCODE_DPAD_DOWN) {
      if (!down || event.getRepeatCount() != 0 || event.isCanceled()) return true;
      if (key == KeyEvent.KEYCODE_BACK) host.end();
      else if (host.ringing()) {
        if (key == KeyEvent.KEYCODE_CALL || key == KeyEvent.KEYCODE_DPAD_CENTER
            || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_MENU) host.answer();
      } else if (host.connected()) {
        if (key == KeyEvent.KEYCODE_MENU) {
          new AlertDialog.Builder(activity).setTitle("Opțiuni apel")
              .setItems(new String[]{host.muted() ? "Pornește microfonul" : "Oprește microfonul",
                  host.speaker() ? "Cască" : "Difuzor", "Înapoi"}, (dialog, which) -> {
                    if (which == 0) host.mute(); else if (which == 1) host.route();
                    render();
                  }).show();
        } else if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
          selection = 1 - selection;
        } else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
          if (selection == 0) host.mute(); else host.route();
        } else if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
          ((AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE)).adjustStreamVolume(
              AudioManager.STREAM_VOICE_CALL, key == KeyEvent.KEYCODE_DPAD_UP
                  ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        }
      }
      render();
      return true;
    }
    return false;
  }
}
