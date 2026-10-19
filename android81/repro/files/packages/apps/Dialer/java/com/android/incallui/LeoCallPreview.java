package com.android.incallui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

/** Offline only: deliberately contains no Telecom, phone or network API. */
public final class LeoCallPreview extends Activity implements LeoCallKeys.Host {
  private boolean incoming = true, active, mute, speaker;
  private TextView status;
  private LeoCallKeys keys;
  private String tone = "";
  public void onCreate(Bundle state) {
    super.onCreate(state);
    status = new TextView(this);
    status.setGravity(Gravity.CENTER);
    status.setTextSize(20);
    setContentView(status);
    keys = new LeoCallKeys(this, this);
    keys.attach();
    update();
  }
  private void update() {
    status.setText("DEMO · fără apel real\n\n" + (incoming ? "Apel primit" : active ? "Apel activ" : "Apel încheiat")
        + "\n\n" + (mute ? "Microfon oprit" : "Microfon pornit")
        + "\n" + (speaker ? "Difuzor" : "Cască") + "\n" + tone);
  }
  public boolean ringing() { return incoming; }
  public boolean connected() { return active; }
  public boolean muted() { return mute; }
  public boolean speaker() { return speaker; }
  public void answer() { incoming = false; active = true; update(); }
  public void end() { incoming = false; active = false; update(); }
  public void mute() { mute = !mute; update(); }
  public void route() { speaker = !speaker; update(); }
  public void tone(char digit, boolean down) { tone = "DTMF " + digit + (down ? " apăsat" : " eliberat"); update(); }
  public boolean dispatchKeyEvent(KeyEvent event) {
    return (keys != null && keys.key(event)) || super.dispatchKeyEvent(event);
  }
  protected void onResume() { super.onResume(); keys.resume(); }
  protected void onPause() { keys.pause(); super.onPause(); }
}
