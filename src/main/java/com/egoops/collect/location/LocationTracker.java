package com.egoops.collect.location;

import android.content.Context;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 定位采集与逆地理编码、地图截图（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class LocationTracker {

    private LocationTracker() {
    }

    /** 取一次真实定位，反查成文字地址（水印用） */
    public static void startGps(MainActivity host) {
        try {
            final android.location.LocationManager lm =
                    (android.location.LocationManager) host.getSystemService(android.content.Context.LOCATION_SERVICE);
            if (lm == null) return;
            android.location.Location best = null;
            for (String pv : new String[]{android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER}) {
                try {
                    android.location.Location l = lm.getLastKnownLocation(pv);
                    if (l == null) continue;
                    if (best == null || l.getTime() > best.getTime()) best = l;
                } catch (Exception ignored) {}
            }
            if (best != null) LocationTracker.useLocation(host, best);

            final android.location.LocationListener[] holder = new android.location.LocationListener[1];
            android.location.LocationListener listener = new android.location.LocationListener() {
                @Override public void onLocationChanged(android.location.Location l) {
                    if (l.getAccuracy() > 80 && host.gpsAddress != null && !host.gpsAddress.isEmpty()) return;
                    LocationTracker.useLocation(host, l);
                    try { lm.removeUpdates(holder[0]); } catch (Exception ignored) {}
                }
                @Override public void onStatusChanged(String p, int s, android.os.Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            holder[0] = listener;
            try {
                lm.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 0, 0, listener);
            } catch (Exception ignored) {}
            try {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 0, 0, listener);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {
        }
    }

    /** 坐标 → GCJ02 → 高德逆地理 → 真实地址；同时抓一张对应地图 */
    public static void useLocation(MainActivity host, android.location.Location l) {
        try {
            final double lat = l.getLatitude();
            final double lon = l.getLongitude();
            final float acc = l.getAccuracy();
            host.gpsCoords = String.format("%.6f, %.6f", lat, lon);

            // 手机给的是 WGS84，高德用 GCJ02，必须转换，否则红点会偏几百米
            final double[] gcj = Geo.wgs84ToGcj02(lat, lon);
            final String key = MapSnap.DEFAULT_KEY;

            new Thread(() -> {
                String text = Geo.regeo(key, gcj[0], gcj[1]);
                if (text == null || text.isEmpty()) {
                    try {
                        android.location.Geocoder gc = new android.location.Geocoder(
                                host, java.util.Locale.CHINA);
                        java.util.List<android.location.Address> list = gc.getFromLocation(lat, lon, 1);
                        if (list != null && !list.isEmpty()) {
                            android.location.Address a = list.get(0);
                            StringBuilder sb = new StringBuilder();
                            if (a.getAdminArea() != null) sb.append(a.getAdminArea());
                            if (a.getLocality() != null) sb.append(a.getLocality());
                            if (a.getSubLocality() != null) sb.append(a.getSubLocality());
                            if (a.getThoroughfare() != null) sb.append(a.getThoroughfare());
                            if (a.getSubThoroughfare() != null) sb.append(a.getSubThoroughfare());
                            text = sb.toString().replace(" ", "");
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (text == null || text.isEmpty()) {
                    text = "定位 " + host.gpsCoords;
                }
                if (acc > 100) {
                    text = text + "（精度约" + Math.round(acc) + "米）";
                }
                host.gpsAddress = text;

                try {
                    android.graphics.Bitmap bmp = Geo.staticMap(key, gcj[0], gcj[1], 320);
                    if (bmp != null) {
                        java.io.File dir = new java.io.File(host.getExternalFilesDir(null), "maps");
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File out = new java.io.File(dir, "map_" + System.currentTimeMillis() + ".png");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                        bmp.recycle();
                        host.mapSnapFile = out;
                    }
                } catch (Throwable ignored) {
                }
            }).start();
        } catch (Throwable ignored) {
        }
    }
}
