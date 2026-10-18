"""Add keypad controls to the existing Oreo InCallUI, plus an offline preview."""
from pathlib import Path
import shutil

root = Path('/root/android81-leo/src/packages/apps/Dialer')
here = Path(__file__).resolve().parent
target = root / 'java/com/android/incallui'
for name in ('LeoCallKeys.java', 'LeoCallPreview.java'):
    source = here / 'call-ui' / name
    destination = target / name
    if not destination.exists() or source.read_bytes() != destination.read_bytes():
        shutil.copyfile(source, destination)
path = target / 'InCallActivity.java'
text = path.read_text()
if '// LEO_CALL_KEYS' not in text:
    text = text.replace('  private final InCallActivityCommon common;', '''  // LEO_CALL_KEYS: use Telecom's existing call objects, never synthetic calls.
  private LeoCallKeys leoKeys;
  private final InCallActivityCommon common;''')
    needle = '    pseudoBlackScreenOverlay = findViewById(R.id.psuedo_black_screen_overlay);'
    assert text.count(needle) == 1
    text = text.replace(needle, needle + '''
    leoKeys = new LeoCallKeys(this, new LeoCallKeys.Host() {
      public boolean ringing() { return CallList.getInstance().getIncomingCall() != null; }
      public boolean connected() { return CallList.getInstance().getActiveCall() != null; }
      public void answer() {
        DialerCall call = CallList.getInstance().getIncomingCall();
        if (call != null) call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY);
      }
      public void end() {
        CallList calls = CallList.getInstance();
        DialerCall call = calls.getIncomingCall();
        if (call != null) { call.reject(false, null); return; }
        call = calls.getOutgoingCall();
        if (call == null) call = calls.getPendingOutgoingCall();
        if (call == null) call = calls.getActiveOrBackgroundCall();
        if (call != null) call.disconnect();
      }
      public boolean muted() {
        return com.android.incallui.audiomode.AudioModeProvider.getInstance().getAudioState().isMuted();
      }
      public boolean speaker() {
        return com.android.incallui.audiomode.AudioModeProvider.getInstance().getAudioState().getRoute()
            == android.telecom.CallAudioState.ROUTE_SPEAKER;
      }
      public void mute() { com.android.incallui.call.TelecomAdapter.getInstance().mute(!muted()); }
      public void route() {
        com.android.incallui.call.TelecomAdapter.getInstance().setAudioRoute(speaker()
            ? android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE
            : android.telecom.CallAudioState.ROUTE_SPEAKER);
      }
      public void tone(char digit, boolean down) {
        DialerCall call = CallList.getInstance().getActiveCall();
        if (call != null) {
          if (down) com.android.incallui.call.TelecomAdapter.getInstance().playDtmfTone(call.getId(), digit);
          else com.android.incallui.call.TelecomAdapter.getInstance().stopDtmfTone(call.getId());
        }
      }
    });
    leoKeys.attach();''')
    text = text.replace('    common.onResume();', '    common.onResume();\n    leoKeys.resume();')
    text = text.replace('    common.onPause();', '    leoKeys.pause();\n    common.onPause();')
    needle = '  @Override\n  public boolean onKeyUp(int keyCode, KeyEvent event) {'
    assert text.count(needle) == 1
    text = text.replace(needle, '''  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return (leoKeys != null && leoKeys.key(event)) || super.dispatchKeyEvent(event);
  }

''' + needle)
    path.write_text(text)
path = root / 'AndroidManifest.xml'
text = path.read_text()
if 'com.android.incallui.LeoCallPreview' not in text:
    assert '</application>' in text
    text = text.replace('</application>', '''    <!-- Offline keypad preview: no Telecom calls, not exported to other apps. -->
    <activity android:name="com.android.incallui.LeoCallPreview"
      android:exported="false" android:label="Leo Call Preview"
      android:theme="@android:style/Theme.Material.NoActionBar"/>
  </application>''')
    path.write_text(text)
print('Keypad InCallUI and offline preview patched')
