package ro.leo.visual;
import ro.leo.i18n.L;
import org.json.*;import java.net.*;import java.io.*;import java.util.*;
/** Open-Meteo HTTPS, no key or device location; bounded reads and finite timeouts. */
final class WeatherData {
 static JSONObject read(String address)throws Exception{HttpsURLConnectionGuard guard=new HttpsURLConnectionGuard(address);return guard.read();}
 private static final class HttpsURLConnectionGuard {
  final String address;HttpsURLConnectionGuard(String s){address=s;}
  JSONObject read()throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","LeoWeather/1.0 (experimental keypad client)");try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>1048576)throw new IOException("Response too large");bytes.write(b,0,n);}return new JSONObject(bytes.toString("UTF-8"));}}finally{c.disconnect();}}
 }
 static JSONArray search(String name)throws Exception{return read("https://geocoding-api.open-meteo.com/v1/search?count=5&language=ro&name="+URLEncoder.encode(name,"UTF-8")).optJSONArray("results");}
 static JSONObject forecast(double latitude,double longitude)throws Exception{if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw new IllegalArgumentException();JSONObject data=read("https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&timezone=auto&forecast_days=7&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset");if(data.optJSONObject("current")==null||data.optJSONObject("daily")==null)throw new IOException("Missing forecast");return data;}
 static String number(JSONObject o,String key,String suffix){if(o==null||o.isNull(key))return "—";double n=o.optDouble(key,Double.NaN);return Double.isNaN(n)?"—":String.format(Locale.ROOT,"%.0f%s",n,suffix);}
 static String code(int n){if(n==0)return L.t("Senin");if(n==1)return L.t("Mai mult senin");if(n==2)return L.t("Parțial noros");if(n==3)return L.t("Înnorat");if(n==45||n==48)return L.t("Ceață");if(n>=51&&n<=57)return L.t("Burniță");if(n>=61&&n<=67)return L.t("Ploaie");if(n>=71&&n<=77)return L.t("Ninsoare");if(n>=80&&n<=82)return L.t("Averse");if(n==85||n==86)return L.t("Averse de zăpadă");if(n>=95&&n<=99)return L.t("Furtună");return L.t("Date indisponibile");}
}
