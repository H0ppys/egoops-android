package com.egoops.collect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 坐标与地址：
 *   手机 GPS 给的是 WGS-84，高德地图用 GCJ-02，两者在国内差 100~500 米。
 *   必须先 WGS84 → GCJ02 再查地图/逆地理，否则水印上的红点是偏的。
 */
public class Geo {

    private static final double PI = 3.1415926535897932384626;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    public static boolean outOfChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    /** WGS84 → GCJ02，返回 {lat, lon} */
    public static double[] wgs84ToGcj02(double lat, double lon) {
        if (outOfChina(lat, lon)) return new double[]{lat, lon};
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{lat + dLat, lon + dLon};
    }

    /** 高德逆地理：坐标要 GCJ02，返回「省市区街道门牌」 */
    public static String regeo(String key, double latGcj, double lonGcj) {
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?location=" + lonGcj + "," + latGcj
                    + "&extensions=base&key=" + key;
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(7000);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            JSONObject root = new JSONObject(bos.toString("UTF-8"));
            if (!"1".equals(root.optString("status"))) return "";
            JSONObject rc = root.optJSONObject("regeocode");
            if (rc == null) return "";
            String formatted = rc.optString("formatted_address", "");
            JSONObject comp = rc.optJSONObject("addressComponent");
            if (comp == null) return formatted;
            StringBuilder sb = new StringBuilder();
            String prov = comp.optString("province", "");
            String city = comp.optString("city", "");
            String dist = comp.optString("district", "");
            String town = comp.optString("township", "");
            String street = comp.optString("street", "");
            String num = comp.optString("streetNumber", "");
            if (prov != null && !prov.isEmpty() && !"[]".equals(prov)) sb.append(prov);
            if (city != null && !city.isEmpty() && !"[]".equals(city) && !city.equals(prov)) sb.append(city);
            if (dist != null && !dist.isEmpty() && !"[]".equals(dist)) sb.append(dist);
            if (town != null && !town.isEmpty() && !"[]".equals(town) && formatted.indexOf(town) < 0) sb.append(town);
            if (street != null && !street.isEmpty() && !"[]".equals(street)) {
                sb.append(street);
                if (num != null && !num.isEmpty() && !"[]".equals(num)) sb.append(num);
            }
            String text = sb.toString().trim();
            if (text.length() < 6) text = formatted;
            return text;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 抓一张 GCJ02 坐标的地图 */
    public static Bitmap staticMap(String key, double latGcj, double lonGcj, int px) {
        try {
            String url = "https://restapi.amap.com/v3/staticmap?location=" + lonGcj + "," + latGcj
                    + "&zoom=17&size=" + px + "*" + px
                    + "&markers=mid,,A:" + lonGcj + "," + latGcj + "&key=" + key;
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            byte[] d = bos.toByteArray();
            if (d.length < 2000) return null;
            return BitmapFactory.decodeByteArray(d, 0, d.length);
        } catch (Throwable t) {
            return null;
        }
    }
}
