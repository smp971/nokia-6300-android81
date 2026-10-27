"""Apply the scoped Leo keypad patches to the existing AOSP checkout."""
from pathlib import Path
import argparse
import shutil

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=Path('/root/android81-leo/src'))
parser.add_argument('--apps-only', action='store_true')
args = parser.parse_args()
base = args.source / 'frameworks/base'


def insert(path, anchor, replacement, marker):
    text = path.read_text(encoding='utf-8')
    if marker in text:
        return
    if text.count(anchor) != 1:
        raise RuntimeError('Unexpected source anchor: ' + str(path))
    path.write_text(text.replace(anchor, replacement), encoding='utf-8')


def copy_changed(source, destination):
    if not destination.exists() or source.read_bytes() != destination.read_bytes():
        shutil.copyfile(source, destination)


if not args.apps_only:
    copy_changed(ROOT / 'framework-keypad/LeoKeypadNavigation.java',
                 base / 'core/java/com/android/internal/policy/LeoKeypadNavigation.java')
for app in [base / 'packages/SystemUI', args.source / 'packages/apps/Settings',
            args.source / 'packages/apps/PackageInstaller']:
    local = app / 'src/com/android/internal/policy/LeoKeypadNavigation.java'
    local.parent.mkdir(parents=True, exist_ok=True)
    copy_changed(ROOT / 'framework-keypad/LeoKeypadNavigation.java', local)

path = base / 'core/java/com/android/internal/policy/DecorView.java'
anchor = '    public boolean dispatchKeyEvent(KeyEvent event) {\n'
if not args.apps_only:
    insert(path, anchor, anchor + '''        // LEO_SYSTEM_KEYPAD: preserve application-specific editors and native adapter lists.
        if (LeoKeypadNavigation.dispatch(this, event)) return true;
''', 'LEO_SYSTEM_KEYPAD')

path = base / 'packages/SystemUI/src/com/android/systemui/statusbar/phone/StatusBarWindowView.java'
anchor = '    public boolean dispatchKeyEvent(KeyEvent event) {\n'
insert(path, anchor, anchor + '''        // LEO_SHADE_KEYPAD: no keyguard, doze, media or text-editor overrides.
        if (com.android.internal.policy.LeoKeypadNavigation.enabled(getContext())
                && mNotificationPanel != null && !mNotificationPanel.isFullyCollapsed()
                && !mService.isDozing() && mService.getBarState() == 0) {
            View focused = findFocus();
            boolean editor = focused instanceof android.widget.EditText;
            int key = event.getKeyCode();
            if (!editor && (key == KeyEvent.KEYCODE_DPAD_LEFT
                    || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (key == KeyEvent.KEYCODE_DPAD_RIGHT) mService.animateExpandSettingsPanel(null);
                    else mNotificationPanel.closeQs();
                }
                return true;
            }
            if (com.android.internal.policy.LeoKeypadNavigation.dispatch(this, event)) return true;
        }
''', 'LEO_SHADE_KEYPAD')
anchor = '    public void setTouchActive(boolean touchActive) {\n'
insert(path, anchor, '''    // LEO_SHADE_HINT: attached once and only visible while the unlocked panel is expanded.
    private android.widget.TextView mLeoKeypadHint;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!"leo".equals(android.os.Build.DEVICE)) return;
        if (mLeoKeypadHint == null) {
            mLeoKeypadHint = new android.widget.TextView(getContext());
            boolean ro = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ro");
            mLeoKeypadHint.setText(ro ? "↑↓ Alege · OK Deschide · ←/→ Panouri · Înapoi"
                    : "↑↓ Select · OK Open · ←/→ Panels · Back");
            mLeoKeypadHint.setTextSize(10);
            mLeoKeypadHint.setTextColor(0xff63e1c9);
            mLeoKeypadHint.setBackgroundColor(0xf00e1821);
            mLeoKeypadHint.setGravity(android.view.Gravity.CENTER);
            mLeoKeypadHint.setFocusable(false);
            mLeoKeypadHint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int height = Math.round(24 * getResources().getDisplayMetrics().density);
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    -1, height, android.view.Gravity.BOTTOM);
            mLeoKeypadHint.setVisibility(View.GONE);
            addView(mLeoKeypadHint, params);
        }
        boolean show = bottom - top > 100 && mNotificationPanel != null
                && !mNotificationPanel.isFullyCollapsed() && !mService.isDozing()
                && mService.getBarState() == 0
                && com.android.internal.policy.LeoKeypadNavigation.enabled(getContext());
        int visible = show ? View.VISIBLE : View.GONE;
        if (mLeoKeypadHint.getVisibility() != visible) mLeoKeypadHint.setVisibility(visible);
    }

''' + anchor, 'LEO_SHADE_HINT')

# Upgrade already-patched checkouts without touching unrelated AOSP changes.
source = path.read_text(encoding='utf-8')
source = source.replace('&& !mService.isDozing()) {\n            View focused',
                        '&& !mService.isDozing() && mService.getBarState() == 0) {\n            View focused')
path.write_text(source, encoding='utf-8') if source != path.read_text(encoding='utf-8') else None
insert(path, anchor, '''    // LEO_SHADE_HINT_REFRESH: panel expansion may animate without another root layout.
    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        if (mLeoKeypadHint != null && mService != null && mNotificationPanel != null) {
            boolean show = !mNotificationPanel.isFullyCollapsed() && !mService.isDozing()
                    && mService.getBarState() == 0
                    && com.android.internal.policy.LeoKeypadNavigation.enabled(getContext());
            int visibility = show ? View.VISIBLE : View.GONE;
            if (mLeoKeypadHint.getVisibility() != visibility) mLeoKeypadHint.setVisibility(visibility);
        }
        super.dispatchDraw(canvas);
    }

''' + anchor, 'LEO_SHADE_HINT_REFRESH')

path = args.source / 'packages/apps/Settings/src/com/android/settings/SettingsActivity.java'
anchor = '    @Override\n    protected void onCreate(Bundle savedState) {\n'
insert(path, anchor, '''    // LEO_SETTINGS_KEYPAD: the physical left softkey opens a native, focusable action list.
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if ("leo".equals(android.os.Build.DEVICE)
                && android.os.SystemProperties.getBoolean("persist.leo.keypad_ui", true)
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                boolean ro = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ro");
                String[] labels = ro ? new String[]{"Setări Leo", "Notificări Android", "Comenzi rapide Android", "Înapoi"}
                        : new String[]{"Leo settings", "Android notifications", "Android quick controls", "Back"};
                new android.app.AlertDialog.Builder(this).setTitle("Android · Leo")
                        .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (which == 3) { onBackPressed(); return; }
                                if (which == 0) {
                                    try { startActivity(new android.content.Intent().setClassName("ro.leo.visual",
                                            "ro.leo.visual.MainActivity").putExtra("open_app", 8)); }
                                    catch (RuntimeException absent) { android.widget.Toast.makeText(SettingsActivity.this,
                                            "Leo unavailable", android.widget.Toast.LENGTH_SHORT).show(); }
                                } else {
                                    android.app.StatusBarManager bars = (android.app.StatusBarManager)
                                            getSystemService(android.content.Context.STATUS_BAR_SERVICE);
                                    if (which == 1) bars.expandNotificationsPanel();
                                    else bars.expandSettingsPanel();
                                }
                            }
                        }).show();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

''' + anchor, 'LEO_SETTINGS_KEYPAD')
print('Leo framework, SystemUI and Settings keypad sources ready')

anchor = '        return super.dispatchKeyEvent(event);\n    }\n\n    @Override\n    protected void onCreate'
insert(path, anchor, '''        // LEO_SETTINGS_FOCUS: app-local helper also works before boot-classpath migration.
        if (com.android.internal.policy.LeoKeypadNavigation.dispatch(getWindow().getDecorView(), event)) {
            return true;
        }
''' + anchor, 'LEO_SETTINGS_FOCUS')

path = base / 'packages/SystemUI/src/com/android/systemui/volume/VolumeDialogImpl.java'
anchor = '    private final class CustomDialog extends Dialog {\n'
insert(path, anchor, anchor + '''        // LEO_VOLUME_KEYPAD: preserve hardware volume/back handling and the native slider.
        @Override
        public boolean dispatchKeyEvent(android.view.KeyEvent event) {
            rescheduleTimeoutH();
            if (com.android.internal.policy.LeoKeypadNavigation.dispatch(getWindow().getDecorView(), event)) return true;
            return super.dispatchKeyEvent(event);
        }

''', 'LEO_VOLUME_KEYPAD')
anchor = '        if (mHovering) return 16000;\n'
insert(path,anchor,anchor+'''        // LEO_VOLUME_TIMEOUT: enough time to reach a slider using physical keys.
        if (com.android.internal.policy.LeoKeypadNavigation.enabled(mContext)) return 8000;
''','LEO_VOLUME_TIMEOUT')
anchor = '        if (mShowing) return;\n        mShowing = true;\n'
insert(path,anchor,'''        // LEO_VOLUME_FOCUS: touch-only volume windows discard D-pad events by default.
        if (com.android.internal.policy.LeoKeypadNavigation.enabled(mContext)) {
            mWindow.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
''' + anchor,'LEO_VOLUME_FOCUS')

path = base / 'packages/SystemUI/src/com/android/systemui/volume/VolumeDialogControllerImpl.java'
anchor = '    private boolean shouldShowUI(int flags) {\n        updateStatusBar();\n'
insert(path,anchor,anchor+'''        // LEO_VOLUME_WAKEFULNESS: a hot SystemUI update can miss cached keyguard wake callbacks.
        if (com.android.internal.policy.LeoKeypadNavigation.enabled(mContext)) {
            android.os.PowerManager power=(android.os.PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
            return mStatusBar != null && power != null && power.isInteractive()
                    && (flags & AudioManager.FLAG_SHOW_UI) != 0;
        }
''','LEO_VOLUME_WAKEFULNESS')

path = args.source / 'packages/apps/PackageInstaller/src/com/android/packageinstaller/permission/ui/GrantPermissionsActivity.java'
anchor = '    @Override\n    public void onCreate(Bundle icicle) {\n'
insert(path, anchor, '''    // LEO_PERMISSION_KEYPAD: selects native permission buttons; Android still decides permission grants.
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (com.android.internal.policy.LeoKeypadNavigation.dispatch(getWindow().getDecorView(), event)) return true;
        return super.dispatchKeyEvent(event);
    }

''' + anchor, 'LEO_PERMISSION_KEYPAD')
