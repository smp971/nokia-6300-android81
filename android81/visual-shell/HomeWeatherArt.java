package ro.leo.visual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import ro.leo.i18n.L;

/** Same two-line saved observation on Home and standby. No I/O during drawing. */
final class HomeWeatherArt {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashboardArt icons;
    private final Context context;
    HomeWeatherArt(Context context) { this.context=context; icons=new DashboardArt(context); }
    static String headline(HomeCards.Card weather) {
        if (weather == null) return L.t("Meteo");
        String city = weather.title.replace(L.t("Meteo · "), "");
        int split = weather.main.indexOf(" · ");
        return weather.weatherCode < 0 ? city : (split < 0 ? weather.main : weather.main.substring(0, split)) + " · " + city;
    }
    static String detail(HomeCards.Card weather, long now) {
        if (weather == null || weather.weatherCode < 0) return L.t("Prognoză neîncărcată");
        if(WeatherFreshness.stale(now,weather.updatedAt))return WeatherFreshness.compact(now,weather.updatedAt);
        int split = weather.main.indexOf(" · ");
        String condition = split < 0 ? "" : weather.main.substring(split + 3) + " · ";
        return condition + WeatherFreshness.compact(now, weather.updatedAt);
    }
    void draw(Canvas canvas, Appearance.Palette palette, HomeCards.Card weather, RectF bounds, long now) {
        float scale = HomeStyle.scale(context);
        icons.weather(canvas, bounds.left+18, bounds.centerY(), 26, weather==null?-1:weather.weatherCode);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(android.graphics.Color.WHITE);
        paint.setTextSize(13*scale);
        float left=bounds.left+37, width=bounds.right-left-7;
        canvas.drawText(KeypadText.fit(paint, headline(weather), width), left, bounds.centerY()-3, paint);
        paint.setTextSize(10*scale);
        paint.setColor(weather!=null && weather.weatherCode>=0 && WeatherFreshness.stale(now,weather.updatedAt)
            ? palette.accent : palette.secondary());
        canvas.drawText(KeypadText.fit(paint, detail(weather,now), width), left, bounds.centerY()+14, paint);
    }
}
