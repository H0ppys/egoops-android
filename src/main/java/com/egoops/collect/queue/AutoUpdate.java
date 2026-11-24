package com.egoops.collect.queue;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 版本检查、APK 下载与安装（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class AutoUpdate {

    private AutoUpdate() {
    }

    /** 启动时检查有没有新版本 */
    public static void checkUpdate(MainActivity host) {
        new Thread(() -> {
            try {
                org.json.JSONObject v = host.api.request("GET", "/api/app/version", null);
                final int code = v.optInt("versionCode", 0);
                final String name = v.optString("versionName", "");
                final String url = v.optString("url", "");
                final String notes = v.optString("notes", "");
                final long size = v.optLong("size", 0);
                if (code <= BuildConfigVersion.CODE || url.isEmpty()) return;
                if (code <= host.store.skippedVersion()) return;      // 用户选过「以后再说」
                host.runOnUiThread(() -> {
                    String msg = "发现新版本 " + name
                            + (size > 0 ? "（" + Math.round(size / 1048576.0 * 10) / 10.0 + "MB）" : "")
                            + (notes.isEmpty() ? "" : "\n\n" + notes)
                            + "\n\n要现在更新吗？";
                    new AlertDialog.Builder(host)
                            .setTitle("有新版本")
                            .setMessage(msg)
                            .setPositiveButton("立即更新", (d, w) -> AutoUpdate.downloadAndInstall(host, url, size))
                            .setNegativeButton("以后再说", (d, w) -> host.store.setSkippedVersion(code))
                            .show();
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    /** 下载 APK 并交给系统安装器 */
    public static void downloadAndInstall(MainActivity host, final String url, final long expectSize) {
        host.toast("开始下载新版本…");
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(120000);
                c.connect();
                if (c.getResponseCode() >= 300) throw new Exception("HTTP " + c.getResponseCode());
                int total = c.getContentLength();
                java.io.File out = new java.io.File(host.getCacheDir(), "egoops-update.apk");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                java.io.InputStream in = c.getInputStream();
                byte[] buf = new byte[64 * 1024];
                int n;
                long done = 0;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        final int pct = (int) (done * 100 / total);
                        host.runOnUiThread(() -> {
                            if (host.tvUpload != null) host.tvUpload.setText("正在下载新版本 " + pct + "%");
                        });
                    }
                }
                fos.close();
                in.close();
                long got = out.length();
                if (expectSize > 0 && got != expectSize) {
                    throw new Exception("大小不一致（收到 " + got + "，应为 " + expectSize + "）");
                }
                if (got < 20000) throw new Exception("文件太小（" + got + " 字节），可能没下全");
                byte[] head = new byte[4];
                java.io.FileInputStream fis = new java.io.FileInputStream(out);
                int hn = fis.read(head);
                fis.close();
                if (hn < 2 || head[0] != 'P' || head[1] != 'K') {
                    throw new Exception("下载到的不是 APK（可能被网关拦截）");
                }
                AutoUpdate.installApk(host, out);
            } catch (final Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                host.runOnUiThread(() -> {
                    host.toast("下载失败：" + msg);
                    if (host.tvUpload != null) host.tvUpload.setText("下载失败，点这里重试");
                });
            }
        }).start();
    }

    public static void installApk(MainActivity host, java.io.File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !host.getPackageManager().canRequestPackageInstalls()) {
                host.runOnUiThread(() -> {
                    host.toast("请允许安装未知来源应用");
                    try {
                        host.startActivity(new android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:" + host.getPackageName())));
                    } catch (Exception ignored) { }
                });
                return;
            }
            android.net.Uri uri = ApkProvider.uriFor(apk.getName());
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            host.runOnUiThread(() -> {
                try {
                    host.startActivity(i);
                } catch (Exception e) {
                    host.toast("打不开安装器：" + e.getMessage());
                }
            });
        } catch (Exception e) {
            host.runOnUiThread(() -> host.toast("安装失败：" + e.getMessage()));
        }
    }
}
