package ro.leo.visual;

import android.graphics.Paint;

/** Fit labels using the paint and width that will actually be drawn. */
final class KeypadText {
    private KeypadText() {}

    static String fit(Paint paint, String value, float width) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        if (paint.measureText(text) <= width) return text;
        float ellipsis = paint.measureText("…");
        if (width < ellipsis) return "";
        return text.substring(0, cut(paint, text, width - ellipsis)) + "…";
    }

    static String[] twoLines(Paint paint, String value, float width) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (paint.measureText(text) <= width) return new String[]{text, ""};
        int cut = cut(paint, text, width);
        int space = text.lastIndexOf(' ', cut);
        if (space >= cut / 2 && space > 0) cut = space;
        return new String[]{text.substring(0, cut), fit(paint, text.substring(cut).trim(), width)};
    }

    private static int cut(Paint paint, String value, float width) {
        int end = paint.breakText(value, true, width, null);
        if (end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return end;
    }
}
