package ro.leo.visual;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

final class RadioSources {
    private RadioSources() {}

    static void insert(LeoActivity owner, boolean online, Runnable other) {
        LinearLayout tabs = new LinearLayout(owner);
        tabs.setPadding(owner.dp(7), owner.dp(4), owner.dp(7), owner.dp(3));
        add(owner, tabs, online ? "# FM" : "FM", !online, online ? other : null);
        add(owner, tabs, online ? "Internet" : "# Internet", online, online ? null : other);
        owner.root.addView(tabs, 2, new LinearLayout.LayoutParams(-1, -2));
    }

    private static void add(LeoActivity owner, LinearLayout tabs, String label,
                            boolean selected, Runnable choose) {
        TextView tab = owner.text(label, 13);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine(true);
        tab.setTypeface(UiChrome.TITLE);
        tab.setTextColor(selected ? owner.palette.accent : Color.WHITE);
        tab.setBackground(Appearance.surface(selected
                ? Appearance.mix(owner.palette.card, owner.palette.accent, .19f)
                : owner.palette.card, owner.dp(6)));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, owner.dp(26), 1);
        size.setMargins(owner.dp(2), 0, owner.dp(2), 0);
        tabs.addView(tab, size);
        if (choose != null) tab.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View view) { choose.run(); }
        });
        tab.setFocusable(false);
    }
}
