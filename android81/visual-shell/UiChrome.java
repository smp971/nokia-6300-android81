package ro.leo.visual;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

/** Shared surfaces and controls for native keypad pages. */
final class UiChrome {
    static final int HEADER_DP = 36, FOOTER_DP = 28;
    static final Typeface TITLE = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static Drawable panel(Appearance.Palette palette, float density, boolean footer) {
        if (!footer) return new ColorDrawable(palette.bar);
        LayerDrawable surface = new LayerDrawable(new Drawable[]{
            new ColorDrawable(palette.bar), new ColorDrawable(palette.divider())
        });
        surface.setLayerHeight(1, Math.max(1, Math.round(density)));
        surface.setLayerGravity(1, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        return surface;
    }

    static Drawable row(Appearance.Palette palette, int radius, int stripe) {
        LayerDrawable selected = new LayerDrawable(new Drawable[]{
            new ColorDrawable(palette.selection), new ColorDrawable(palette.accent)
        });
        selected.setLayerWidth(1, Math.max(1, stripe) * 3);
        selected.setLayerGravity(1, Gravity.LEFT | Gravity.FILL_VERTICAL);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, selected);
        states.addState(new int[]{android.R.attr.state_focused}, selected);
        states.addState(new int[]{android.R.attr.state_pressed}, selected);
        states.addState(new int[0], new ColorDrawable(palette.background));
        return states;
    }

    static ColorStateList textColors(Appearance.Palette palette) {
        return new ColorStateList(new int[][]{
            new int[]{-android.R.attr.state_enabled},
            new int[]{android.R.attr.state_selected},
            new int[]{android.R.attr.state_focused},
            new int[]{android.R.attr.state_pressed}, new int[0]
        }, new int[]{palette.secondary(), palette.ink, palette.ink, palette.ink, Color.WHITE});
    }

    static void list(LeoActivity owner, ListView list) {
        list.setPadding(owner.dp(8), 0, owner.dp(8), 0);
        list.setDivider(new ColorDrawable(owner.palette.divider()));
        list.setDividerHeight(owner.dp(1));
        list.setSelector(android.R.color.transparent);
    }

    static void listRow(LeoActivity owner, TextView row) {
        listRow(owner,row,owner.palette);
    }

    static void listRow(android.content.Context context,TextView row,Appearance.Palette palette) {
        float density=context.getResources().getDisplayMetrics().density;
        row.setTextSize(19 * Appearance.textScale(context));
        row.setMinHeight(Math.round(44*density));
        row.setMaxLines(3);
        row.setCompoundDrawablePadding(Math.round(12*density));
        row.setPadding(Math.round(12*density),Math.round(3*density),
            Math.round(9*density),Math.round(3*density));
        row.setTextColor(textColors(palette));
        row.setBackground(row(palette,0,Math.max(1,Math.round(density))));
    }

    static void field(EditText field, Appearance.Palette palette, int padding) {
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(palette.secondary());
        field.setHighlightColor(Appearance.mix(palette.card, palette.accent, .45f));
        field.setBackground(Appearance.fieldSurface(palette, padding));
        field.setPadding(padding, padding, padding, padding);
    }

    static void controls(View view, Appearance.Palette palette, int padding) {
        if (view instanceof EditText) field((EditText) view, palette, padding);
        else if (view instanceof CompoundButton) {
            CompoundButton button = (CompoundButton) view;
            button.setTextColor(Color.WHITE);
            button.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[0]
            }, new int[]{palette.accent, palette.secondary()}));
        } else if (view instanceof Button) {
            ((Button) view).setTextColor(textColors(palette));
            view.setBackground(row(palette, 0, Math.max(1, padding / 8)));
        } else if (view instanceof ProgressBar) {
            ((ProgressBar)view).setIndeterminateTintList(ColorStateList.valueOf(palette.accent));
            ((ProgressBar)view).setProgressTintList(ColorStateList.valueOf(palette.accent));
        } else if (view instanceof Spinner) {
            view.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) controls(group.getChildAt(i), palette, padding);
        }
        view.invalidate();
    }

    static void tab(TextView tab, Appearance.Palette palette, boolean active, int line) {
        LayerDrawable surface = new LayerDrawable(new Drawable[]{
            new ColorDrawable(palette.bar), new ColorDrawable(active ? palette.accent : palette.divider())
        });
        surface.setLayerHeight(1, line * (active ? 3 : 1));
        surface.setLayerGravity(1, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(palette.selection));
        states.addState(new int[0], surface);
        tab.setBackground(states);
        tab.setTextColor(new ColorStateList(new int[][]{
            new int[]{android.R.attr.state_focused}, new int[0]
        }, new int[]{palette.ink, active ? Color.WHITE : palette.secondary()}));
    }

    static void header(Canvas canvas, String title, Appearance.Palette palette, Paint paint) {
        Typeface previous = paint.getTypeface();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.bar);
        canvas.drawRect(0, 0, 240, HEADER_DP, paint);
        paint.setTypeface(TITLE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(22);
        paint.setColor(Color.WHITE);
        canvas.drawText(KeypadText.fit(paint, title, 216), 12, 26, paint);
        paint.setTypeface(previous);
    }
}
