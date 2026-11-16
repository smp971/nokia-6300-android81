package ro.leo.visual;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import java.io.IOException;
import java.io.InputStream;

/** Gaia artwork for the app catalog, keyed by the stable IDs in AppCatalog. */
final class CatalogIcons {
    private final AssetManager assets;
    private final SparseArray<Bitmap> icons = new SparseArray<>();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();

    CatalogIcons(Context context) {
        assets = context.getAssets();
    }

    boolean draw(Canvas canvas, int appId, float x, float y) {
        String name = assetName(appId);
        if (name == null) return false;

        if (icons.indexOfKey(appId) < 0) {
            try (InputStream stream = assets.open("catalog-icons/gaia/" + name + ".png")) {
                icons.put(appId, BitmapFactory.decodeStream(stream));
            } catch (IOException unavailable) {
                icons.put(appId, null);
            }
        }
        Bitmap icon = icons.get(appId);
        if (icon == null) return false;

        bounds.set(x - 25, y - 25, x + 25, y + 25);
        canvas.drawBitmap(icon, null, bounds, paint);
        return true;
    }

    private static String assetName(int appId) {
        switch (appId) {
            case 0: return "phone";
            case 1: return "contacts";
            case 2: return "messages";
            case 3: return "clock";
            case 5: return "gallery";
            case 6: return "music";
            case 8: return "settings";
            case 11: return "calendar";
            case 12: return "video";
            case 22: return "email";
            case 25: return "fm";
            case 34: return "camera";
            default: return null;
        }
    }
}
