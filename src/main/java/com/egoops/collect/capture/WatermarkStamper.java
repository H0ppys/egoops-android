package com.egoops.collect.capture;

import android.graphics.Color;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.egoops.collect.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 照片水印烧录与素材防伪码（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class WatermarkStamper {

    private WatermarkStamper() {
    }

    /** 素材防伪码：文件 SHA1 的前 12 位（大写） */
    public static String antiCodeOf(File f) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02X", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 把地址/日期/时间/门店/采集员烧进照片（和打卡水印相机一个意思） */
    public static void stampWatermark(MainActivity host, File f, long whenMs, String antiCode) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            int maxSide = Math.max(o.outWidth, o.outHeight);
            while (maxSide / sample > 2048) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (src == null) return;

            android.graphics.Bitmap bmp = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
            src.recycle();
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            int W = bmp.getWidth();
            int H = bmp.getHeight();
            float u = W / 1080f;                    // 以 1080 宽为基准

            String gps = host.gpsAddress == null ? "" : host.gpsAddress.trim();
            String addr = gps.isEmpty()
                    ? ((host.site.address == null || host.site.address.trim().isEmpty()) ? "（未填地址）" : host.site.address.trim() + "（自填）")
                    : gps;

            java.util.Calendar cal = java.util.Calendar.getInstance();
            String date = String.format("%04d.%02d.%02d", cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH));
            char[] wk = "日一二三四五六".toCharArray();
            String week = "星期" + wk[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
            String hhmm = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE));

            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

            // 文字最大宽度，地址按宽度折行
            float pad = 34 * u;
            float maxW = W - pad * 2 - 120 * u;
            p.setTextSize(38 * u);
            java.util.List<String> lines = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (char ch : addr.toCharArray()) {
                cur.append(ch);
                if (p.measureText(cur.toString()) > maxW) {
                    cur.deleteCharAt(cur.length() - 1);
                    if (cur.length() > 0) lines.add(cur.toString());
                    cur = new StringBuilder().append(ch);
                    if (lines.size() >= 2) break;
                }
            }
            if (lines.size() < 2 && cur.length() > 0) lines.add(cur.toString());

            // 背景块高度
            float lineH = 46 * u;
            float blockH = pad * 2 + 70 * u + lines.size() * lineH + 44 * u;
            float top = H - pad - blockH;

            android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            bg.setColor(android.graphics.Color.parseColor("#99000000"));
            android.graphics.RectF rf = new android.graphics.RectF(pad * 0.4f, top, W - pad * 0.4f, H - pad * 0.4f);
            c.drawRoundRect(rf, 18 * u, 18 * u, bg);

            // 左侧黄条
            android.graphics.Paint bar = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            bar.setColor(android.graphics.Color.parseColor("#FFC400"));
            c.drawRect(pad * 0.4f, top + 18 * u, pad * 0.4f + 8 * u, H - pad * 0.4f - 18 * u, bar);

            float x = pad * 0.4f + 26 * u;
            float y = top + pad + 30 * u;

            // 打卡 + 时间
            p.setColor(android.graphics.Color.parseColor("#FFC400"));
            p.setTextSize(46 * u);
            c.drawText("打卡", x, y, p);
            float tw = p.measureText("打卡");
            p.setColor(android.graphics.Color.WHITE);
            c.drawText(" " + hhmm, x + tw, y, p);

            // 地址
            p.setColor(android.graphics.Color.WHITE);
            p.setTextSize(38 * u);
            y += 62 * u;
            for (String ln : lines) {
                c.drawText(ln, x, y, p);
                y += lineH;
            }

            // 日期 + 星期（+ 门店）
            p.setTextSize(34 * u);
            String bottom = date + " " + week;
            c.drawText(bottom, x, y + 8 * u, p);

            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out);
            out.close();
            bmp.recycle();
        } catch (Exception e) {
            // 水印失败不影响拍摄
        }
    }
}
