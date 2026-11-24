package com.egoops.collect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 用高德静态地图抓一张当前定位的附近地图，贴到水印右上角。
 * 抓不到就返回 null（水印照常出，不阻塞拍摄）。
 */
public class MapSnap {

    public static final String DEFAULT_KEY = "7ac447c6eace4c0f81e28a07b0c34675";

    /** 抓图并缓存到文件；返回文件，失败返回 null */
    public static File fetch(String key, double lat, double lon, int px, File dir) {
        try {
            String url = "https://restapi.amap.com/v3/staticmap?location=" + lon + "," + lat
                    + "&zoom=16&size=" + px + "*" + px
                    + "&markers=mid,,A:" + lon + "," + lat
                    + "&key=" + key;
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "egoops-collect");
            InputStream in = c.getInputStream();
            File out = new File(dir, "map_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            in.close();
            if (out.length() < 2000) {
                out.delete();
                return null;
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把地图裁成圆角，画到右上角 */
    public static void draw(Canvas c, Bitmap map, int w, int h, float margin) {
        if (map == null) return;
        try {
            float cardW = w * 0.30f;
            float cardH = cardW * 1.02f;
            float x = w - margin - cardW;
            float y = margin;

            Bitmap scaled = Bitmap.createScaledBitmap(map, (int) cardW, (int) cardH, true);
            float r = Math.max(6, cardW * 0.06f);

            android.graphics.Path clip = new android.graphics.Path();
            android.graphics.RectF rf = new android.graphics.RectF(x, y, x + cardW, y + cardH);
            clip.addRoundRect(rf, r, r, android.graphics.Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawBitmap(scaled, x, y, null);
            c.restore();

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(2, cardW * 0.012f));
            border.setColor(Color.WHITE);
            c.drawRoundRect(rf, r, r, border);
            scaled.recycle();
        } catch (Throwable ignored) {
        }
    }
}
