"""Apply Leo's compact system chrome to the existing Oreo SystemUI sources.

Run after patch-framework-keypad.py. No framework resource IDs or status-bar
height changes; the clock, signal cluster and battery retain their controllers.
"""
from pathlib import Path
import argparse
import re


def patch(source):
    root = source / 'frameworks/base/packages/SystemUI'
    changes = {}
    layout = root / 'res/layout/status_bar.xml'
    text = layout.read_text(encoding='utf-8')
    if 'LEO_MODERN_CLOCK' not in text:
        matches = list(re.finditer(r'            <com\.android\.systemui\.statusbar\.policy\.Clock\b.*?/>', text, re.S))
        if len(matches) != 1:
            raise RuntimeError('Unexpected status bar clock layout')
        clock = matches[0].group().replace('android:paddingStart="@dimen/status_bar_clock_starting_padding"', 'android:paddingStart="0dp"').replace('android:paddingEnd="@dimen/status_bar_clock_end_padding"', 'android:paddingEnd="6dp"')
        text = text[:matches[0].start()] + text[matches[0].end():]
        anchor = '        <!-- The alpha of this area'
        assert text.count(anchor) == 1
        text = text.replace(anchor, '        <!-- LEO_MODERN_CLOCK: real clock, before notification icons. -->\n' + clock + '\n\n' + anchor)
        text = text.replace('android:paddingStart="6dp"', 'android:paddingStart="4dp"').replace('android:paddingEnd="8dp"', 'android:paddingEnd="4dp"')
    changes[layout] = text

    java = root / 'src/com/android/systemui/statusbar/SignalClusterView.java'
    text = java.read_text(encoding='utf-8')
    if 'LEO_MODERN_SIM_SLOT' not in text:
        anchor = '        private final int mSubId;'
        assert text.count(anchor) == 1
        text = text.replace(anchor, anchor + '\n        // LEO_MODERN_SIM_SLOT: label the physical slot, not the subscription ID.\n        private android.widget.TextView mLeoSlot;')
        anchor = '            setViews(root);\n            mSubId = subId;'
        assert text.count(anchor) == 1
        text = text.replace(anchor, '            mSubId = subId;\n            setViews(root);')
        anchor = '            mMobileGroup    = root;'
        assert text.count(anchor) == 1
        text = text.replace(anchor, anchor + '''
            if ("leo".equals(android.os.Build.DEVICE)) {
                int slot = android.telephony.SubscriptionManager.getSlotIndex(mSubId);
                mLeoSlot = new android.widget.TextView(root.getContext());
                mLeoSlot.setText(slot >= 0 ? Integer.toString(slot + 1) : "");
                mLeoSlot.setTextSize(8);
                mLeoSlot.setTextColor(0xffffffff);
                mLeoSlot.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                mLeoSlot.setGravity(android.view.Gravity.CENTER_VERTICAL);
                mLeoSlot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(mLeoSlot, 0, new android.widget.LinearLayout.LayoutParams(
                        -2, -1));
            }
''')
        anchor = '        public void setIconTint(int tint, float darkIntensity, Rect tintArea) {'
        assert text.count(anchor) == 1
        text = text.replace(anchor, anchor + '''
            if (mLeoSlot != null) mLeoSlot.setTextColor(
                    DarkIconDispatcher.getTint(tintArea, mLeoSlot, tint));
''')
    changes[java] = text

    # This keypad device deliberately has no software navigation bar. Stock
    # Oreo's diagnostic dump dereferences its absent controller and crashes UI.
    lightbar = root / 'src/com/android/systemui/statusbar/phone/LightBarController.java'
    text = lightbar.read_text(encoding='utf-8')
    if 'LEO_OPTIONAL_NAV_DUMP' not in text:
        anchor = '        mNavigationBarController.dump(fd, pw, args);'
        assert text.count(anchor) == 1
        text = text.replace(anchor, '''        // LEO_OPTIONAL_NAV_DUMP: physical keypad, no software navigation bar.
        if (mNavigationBarController != null) mNavigationBarController.dump(fd, pw, args);
        else pw.println("  No software navigation bar");''')
    changes[lightbar] = text

    colors = root / 'res/values/colors.xml'
    text = colors.read_text(encoding='utf-8')
    for name, value in {'system_bar_background_opaque': '#ff0b121b',
                        'notification_panel_solid_background': '#ff101923',
                        'notification_material_background_color': '#ff192633'}.items():
        # Some Oreo branches have no explicit notification material color.
        text = re.sub(r'(<color name="' + name + r'">)[^<]+(</color>)', lambda m: m[1] + value + m[2], text)
    changes[colors] = text
    for path, text in changes.items():
        if path.read_text(encoding='utf-8') != text:
            path.write_text(text, encoding='utf-8')
    return list(changes)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path('/root/android81-leo/src'))
    for path in patch(parser.parse_args().source):
        print(path)
