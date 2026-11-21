package ro.leo.visual;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import ro.leo.i18n.L;
import org.json.JSONArray;
import org.json.JSONObject;

/** Forecast presentation only. Network requests, persistence and navigation stay in the activity. */
final class WeatherPanel {
    private final Context context;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final CanvasText labels;
    private final CanvasChrome chrome;
    private final WeatherArt art=new WeatherArt();
    private final String[] tabs={L.t("Acum"),L.t("7 zile"),L.t("Detalii")};
    WeatherPanel(Context context){this.context=context;labels=new CanvasText(context,paint);chrome=new CanvasChrome(context,paint);}
    void appearance(){labels.appearance();chrome.appearance(context);}
    private void text(Canvas canvas,String value,float x,float y,int size,int color,Paint.Align align,float width){labels.draw(canvas,value,x,y,size,color,align,width);}
    private void card(Canvas canvas,Appearance.Palette palette,float top,float bottom,boolean selected){
        paint.setStyle(Paint.Style.FILL);paint.setColor(selected?palette.selection:palette.card);
        canvas.drawRoundRect(8,top,232,bottom,5,5,paint);
        if(selected){paint.setColor(palette.accent);canvas.drawRect(8,top+5,11,bottom-5,paint);}
    }
    private String daily(JSONObject data,String key,int day,String suffix){
        JSONObject days=data==null?null:data.optJSONObject("daily");JSONArray values=days==null?null:days.optJSONArray(key);
        if(values==null||values.isNull(day))return "—";
        Object value=values.opt(day);if(value instanceof Number)return Math.round(((Number)value).doubleValue())+suffix;
        String text=values.optString(day);return text.contains("T")?text.substring(text.indexOf('T')+1):text;
    }
    private int code(JSONObject data,int day){JSONObject days=data.optJSONObject("daily");JSONArray values=days==null?null:days.optJSONArray("weather_code");return values==null?-1:values.optInt(day,-1);}
    void draw(Canvas canvas,int width,int height,Appearance.Palette palette,JSONObject data,String city,
              int page,int day,boolean busy,String status,long updated){
        canvas.save();canvas.scale(width/240f,height/302f);canvas.drawColor(palette.background);
        chrome.header(canvas,L.t("Meteo · ")+city,palette);chrome.tabs(canvas,palette,tabs,page);
        JSONObject current=data==null?null:data.optJSONObject("current");
        if(data==null){
            art.draw(canvas,120,123,45,-1);
            text(canvas,L.t(busy?"Încarc prognoza…":"Prognoză indisponibilă"),120,184,17,Color.WHITE,Paint.Align.CENTER,216);
        }else if(page==0){
            card(canvas,palette,80,180,false);art.draw(canvas,62,118,58,current==null?-1:current.optInt("weather_code",-1));
            text(canvas,WeatherData.number(current,"temperature_2m","°"),171,135,40,Color.WHITE,Paint.Align.CENTER,110);
            text(canvas,WeatherData.code(current==null?-1:current.optInt("weather_code",-1)),120,168,16,palette.accent,Paint.Align.CENTER,208);
            text(canvas,L.t("Resimțită ")+WeatherData.number(current,"apparent_temperature","°C"),16,204,15,Color.WHITE,Paint.Align.LEFT,208);
            text(canvas,WeatherData.number(current,"relative_humidity_2m","%")+" · "+WeatherData.number(current,"wind_speed_10m"," km/h"),16,228,15,Color.WHITE,Paint.Align.LEFT,208);
            text(canvas,L.t("Min / max · ")+daily(data,"temperature_2m_min",0,"°")+" / "+daily(data,"temperature_2m_max",0,"°"),16,252,15,palette.accent,Paint.Align.LEFT,208);
        }else if(page==1){
            int first=Math.max(0,Math.min(3,day-2));
            for(int i=first;i<Math.min(7,first+4);i++){
                float top=79+(i-first)*45;boolean selected=i==day;card(canvas,palette,top,top+42,selected);
                int primary=selected?palette.ink:Color.WHITE,secondary=selected?palette.ink:palette.secondary();
                text(canvas,daily(data,"time",i,"")+" · "+daily(data,"temperature_2m_min",i,"°")+" / "+daily(data,"temperature_2m_max",i,"°"),17,top+18,14,primary,Paint.Align.LEFT,207);
                text(canvas,WeatherData.code(code(data,i))+" · "+daily(data,"precipitation_probability_max",i,"%"),17,top+36,12,secondary,Paint.Align.LEFT,207);
            }
        }else{
            text(canvas,daily(data,"time",day,""),120,98,18,Color.WHITE,Paint.Align.CENTER,216);
            text(canvas,WeatherData.code(code(data,day)),120,125,16,palette.accent,Paint.Align.CENTER,216);
            text(canvas,L.t("Min / max · ")+daily(data,"temperature_2m_min",day,"°")+" / "+daily(data,"temperature_2m_max",day,"°"),16,156,15,Color.WHITE,Paint.Align.LEFT,208);
            text(canvas,L.t("Precipitații · ")+daily(data,"precipitation_probability_max",day,"%"),16,183,15,Color.WHITE,Paint.Align.LEFT,208);
            text(canvas,L.t("Răsărit ")+daily(data,"sunrise",day,""),16,210,15,Color.WHITE,Paint.Align.LEFT,208);
            text(canvas,L.t("Apus ")+daily(data,"sunset",day,""),16,237,15,Color.WHITE,Paint.Align.LEFT,208);
        }
        String freshness=status.isEmpty()?(updated==0?"":WeatherFreshness.label(System.currentTimeMillis(),updated)):status;
        text(canvas,freshness,120,269,10,palette.secondary(),Paint.Align.CENTER,222);
        chrome.footer(canvas,palette,L.t("Opțiuni"),page==1?L.t("Detalii"):busy?L.t("Așteaptă"):L.t("Actualiz."),L.t("Înapoi"));
        canvas.restore();
    }
}
