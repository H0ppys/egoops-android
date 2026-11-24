package com.egoops.collect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 上传期间的前台服务：把上传放在通知栏，切后台也不会被杀。
 * 上传逻辑仍在 Uploader 里，这里只负责“让进程活着 + 显示进度”。
 */
public class UploadService extends Service {

    private static final String CHANNEL = "egoops_upload";
    private static final int NOTIF_ID = 4201;

    public static void start(Context ctx, String text) {
        Intent i = new Intent(ctx, UploadService.class);
        i.putExtra("text", text);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    public static void update(Context ctx, String text, int pct, int done, int total) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(ctx);
        nm.notify(NOTIF_ID, build(ctx, text, pct, done, total));
    }

    public static void stop(Context ctx, String finalText) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            if (finalText != null) {
                ensureChannel(ctx);
                nm.notify(NOTIF_ID, build(ctx, finalText, 100, 0, 0));
                final NotificationManager n2 = nm;
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        () -> n2.cancel(NOTIF_ID), 4000);
            } else {
                nm.cancel(NOTIF_ID);
            }
        }
        ctx.stopService(new Intent(ctx, UploadService.class));
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "采集上传", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("上传照片和视频时的进度");
        nm.createNotificationChannel(ch);
    }

    private static Notification build(Context ctx, String text, int pct, int done, int total) {
        PendingIntent pi = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(ctx, CHANNEL)
                : new Notification.Builder(ctx);
        b.setContentTitle("采集数据上传中")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(pct < 100)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);
        if (pct >= 0) b.setProgress(100, pct, false);
        if (total > 0) b.setSubText(done + "/" + total + " 个文件");
        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel(this);
        String text = intent != null && intent.getStringExtra("text") != null
                ? intent.getStringExtra("text") : "准备上传…";
        Notification n = build(this, text, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
