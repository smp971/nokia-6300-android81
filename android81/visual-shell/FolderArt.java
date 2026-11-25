package ro.leo.visual;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Vector folder with the actual first four member icons; no extra bitmap cache. */
final class FolderArt {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tab = new Path();
    void draw(Canvas c, MainActivity.Screen screen, int entry, float x, float y) {
        int folder = entry - LauncherFolders.FIRST;
        int[] members = screen.folderMembers[folder];
        c.save();
        c.translate(x, y);
        tab.reset();
        tab.moveTo(-25, -17); tab.lineTo(-25, -21); tab.quadTo(-25, -25, -21, -25);
        tab.lineTo(-8, -25); tab.lineTo(-2, -20); tab.lineTo(21, -20);
        tab.quadTo(25, -20, 25, -16); tab.close();
        paint.setColor(screen.palette.accent);
        c.drawPath(tab, paint);
        paint.setColor(screen.palette.card);
        c.drawRoundRect(-25, -18, 25, 25, 6, 6, paint);
        paint.setColor(screen.palette.accent);
        paint.setAlpha(95); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        c.drawRoundRect(-25, -18, 25, 25, 6, 6, paint);
        paint.setStyle(Paint.Style.FILL); paint.setAlpha(255);
        for (int i = 0; i < Math.min(4, members.length); i++) {
            c.save(); c.translate(i % 2 == 0 ? -11 : 11, i / 2 == 0 ? -7 : 13); c.scale(.35f, .35f);
            screen.icon(c, members[i], 0, 0); c.restore();
        }
        c.restore();
    }
}
