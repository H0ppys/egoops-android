package com.egoops.collect.capture;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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

/** 拍摄动作流：相机/相册/水印相机、素材落盘与收录（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class CaptureFlow {

    private CaptureFlow() {
    }

    public static void captureShot(MainActivity host, String shot, String kind) {
        if (Build.VERSION.SDK_INT >= 23 && host.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            host.pendingShot = shot;
            if (!host.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                    && host.getSharedPreferences("egoops", android.content.Context.MODE_PRIVATE).getBoolean("asked_camera", false)) {
                // 被「拒绝且不再询问」了，只能去设置里开
                new AlertDialog.Builder(host)
                        .setTitle("需要相机权限")
                        .setMessage("系统里已经把相机权限关掉了，需要到「设置 → 应用 → 采集点拍照 → 权限」里打开相机。\n\n要现在去设置吗？")
                        .setPositiveButton("去设置", (d, w) -> {
                            try {
                                host.startActivity(new android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:" + host.getPackageName())));
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("先不拍", null)
                        .show();
                return;
            }
            host.getSharedPreferences("egoops", android.content.Context.MODE_PRIVATE).edit().putBoolean("asked_camera", true).apply();
            host.requestPermissions(new String[]{Manifest.permission.CAMERA}, MainActivity.REQ_PERM);
            host.toast("请点“允许”，才能用相机");
            return;
        }
        host.pendingShot = shot;
        String name = (kind.equals("video") ? "VID_" : "IMG_") + System.currentTimeMillis() + (kind.equals("video") ? ".mp4" : ".jpg");
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        if (kind.equals("video")) {
            v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            Uri uri = host.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) { host.toast("无法创建文件，请检查存储权限"); return; }
            host.tempUri = uri;
            Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                host.startActivityForResult(i, MainActivity.REQ_VIDEO);
            } catch (Exception e) {
                host.toast("这台手机没有可用的录像界面，请改用「从相册选视频」");
                CaptureFlow.pickShot(host, shot, kind);
            }
        } else {
            v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            Uri uri = host.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) { host.toast("无法创建文件，请检查存储权限"); return; }
            host.tempUri = uri;
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                host.startActivityForResult(i, MainActivity.REQ_PHOTO);
            } catch (Exception e) {
                host.toast("这台手机没有可用的相机界面，请改用「从相册选照片」");
                CaptureFlow.pickShot(host, shot, kind);
            }
        }
    }

    /** 已知的水印相机包名（按顺序尝试） */
    public static final String[] WM_PACKAGES = {
            "com.chanjikeji.watermarkcamera",
            "com.chanjikeji.todaywatermark",
            "com.chanjikeji.watermark",
            "com.todaywatermark.camera",
            "com.watermark.camera",
            "com.tencent.watermark",
            "com.yuandao.camera",
            "com.kkmh.watermark",
    };

    /** 打开水印相机：已知包名 → 名字含「水印」 → 让用户从相机类 App 里选一次 */
    public static void openWatermarkCamera(MainActivity host, String shot, String kind) {
        host.pendingShot = shot;
        android.content.pm.PackageManager pm = host.getPackageManager();

        // 0) 上次选过的
        String saved = host.store.get("wm_pkg", "");
        if (!saved.isEmpty()) {
            try {
                android.content.Intent it = pm.getLaunchIntentForPackage(saved);
                if (it != null) {
                    CaptureFlow.launchWm(host, it);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // 1) 已知包名
        for (String pkg : WM_PACKAGES) {
            try {
                android.content.Intent it = pm.getLaunchIntentForPackage(pkg);
                if (it != null) {
                    host.store.put("wm_pkg", pkg);
                    CaptureFlow.launchWm(host, it);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2) 名字含「水印 / Watermark」
        java.util.List<String[]> cams = new java.util.ArrayList<>();
        java.util.List<String[]> named = new java.util.ArrayList<>();
        try {
            java.util.List<android.content.pm.ApplicationInfo> apps =
                    pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);
            for (android.content.pm.ApplicationInfo ai : apps) {
                String label = String.valueOf(pm.getApplicationLabel(ai));
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                boolean isCam = label.contains("相机") || label.contains("摄像") || label.contains("Watermark")
                        || label.contains("水印") || label.toLowerCase().contains("camera");
                if (!isCam) continue;
                cams.add(new String[]{label, ai.packageName});
                if (label.contains("水印") || label.contains("Watermark")) {
                    named.add(new String[]{label, ai.packageName});
                }
            }
        } catch (Throwable ignored) {
        }

        if (named.size() == 1) {
            host.store.put("wm_pkg", named.get(0)[1]);
            android.content.Intent it = pm.getLaunchIntentForPackage(named.get(0)[1]);
            if (it != null) {
                CaptureFlow.launchWm(host, it);
                return;
            }
        }

        // 3) 让用户选一次（水印类排前面）
        java.util.List<String[]> show = new java.util.ArrayList<>();
        show.addAll(named);
        for (String[] c : cams) {
            boolean dup = false;
            for (String[] x : show) if (x[1].equals(c[1])) dup = true;
            if (!dup) show.add(c);
        }

        if (show.isEmpty()) {
            new AlertDialog.Builder(host)
                    .setTitle("没找到水印相机")
                    .setMessage("手机里没装名字带「水印」的相机 App。\n\n可以先去应用商店装「今日水印相机」，再回来点这个按钮。")
                    .setPositiveButton("去应用商店", (d, w) -> {
                        try {
                            host.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("market://search?q=今日水印相机")));
                        } catch (Throwable e) {
                            try {
                                host.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://sj.qq.com/appdetail/com.tencent.tmgp.sgame")));
                            } catch (Throwable ignored) {
                                host.toast("请手动打开应用商店搜索「今日水印相机」");
                            }
                        }
                    })
                    .setNegativeButton("知道了", null)
                    .show();
            return;
        }

        String[] items = new String[show.size()];
        for (int i = 0; i < show.size(); i++) {
            items[i] = show.get(i)[0];
        }
        new AlertDialog.Builder(host)
                .setTitle("用哪个 App 拍？")
                .setItems(items, (d, which) -> {
                    String pkg = show.get(which)[1];
                    host.store.put("wm_pkg", pkg);
                    try {
                        android.content.Intent it = pm.getLaunchIntentForPackage(pkg);
                        if (it != null) CaptureFlow.launchWm(host, it);
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 真正跳转，并记下开始时间（回来后自动抓文件） */
    public static void launchWm(MainActivity host, android.content.Intent it) {
        try {
            host.startActivity(it);
            host.wmLaunchAt = System.currentTimeMillis();
            host.awaitingWatermarkImport = true;
            host.toast("拍完按返回，会自动取回刚拍的");
        } catch (Throwable e) {
            host.toast("打不开这个应用：" + e);
        }
    }

    /** 一次选多张，按顺序填进当前任务剩下的项 */
    public static void pickMultiple(MainActivity host) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            String kind = (host.pendingShot != null && host.pendingShot.toLowerCase().contains("video")) ? "video" : "photo";
            i.setType(kind.equals("video") ? "video/*" : "image/*");
            i.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
            host.startActivityForResult(i, MainActivity.REQ_PICK_MULTI);
        } catch (Throwable e) {
            host.toast("这台手机不支持批量选择：" + e);
        }
    }

    /** 批量导入：把选中的素材按顺序塞进未完成的项 */
    public static void ingestMultiple(MainActivity host, android.content.ClipData clip) {
        Model.Task task = SiteFlow.task(host, host.currentTask);
        String[][] shots = Shots.shotsOf(task);
        int taken = 0;
        for (int i = 0; i < clip.getItemCount(); i++) {
            if (taken >= 30) break;
            Uri uri = clip.getItemAt(i).getUri();
            // 找下一个还没拍的项
            int idx = -1;
            for (int k = 0; k < shots.length; k++) {
                if (!Shots.hasShot(host, host.currentTask, k)) {
                    idx = k;
                    break;
                }
            }
            if (idx < 0) break;
            host.pendingShot = shots[idx][0];
            String kind = shots[idx][3];
            boolean isVideo = kind.equals("video");
            // 类型不匹配就跳过
            String type = "";
            try {
                type = String.valueOf(host.getContentResolver().getType(uri));
            } catch (Throwable ignored) {
            }
            if (isVideo && !type.startsWith("video")) continue;
            if (!isVideo && !type.startsWith("image")) continue;
            CaptureFlow.ingest(host, uri, isVideo);
            taken++;
        }
        if (taken == 0) {
            host.toast("选中的素材没对上：请确认照片/视频类型");
        } else {
            host.toast("已导入 " + taken + " 个素材");
        }
    }

    /** 从水印相机回来：自动抓「比打开时刻更新」的最新一张/一段 */
    public static void autoGrabLatest(MainActivity host, String shot, String kind) {
        boolean video = kind.equals("video");
        Uri found = null;
        String name = "";
        try {
            long since = host.wmLaunchAt > 0 ? host.wmLaunchAt - 3000 : System.currentTimeMillis() - 300000;
            android.database.Cursor c;
            if (video) {
                c = host.getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME},
                        MediaStore.Video.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since / 1000)},
                        MediaStore.Video.Media.DATE_ADDED + " DESC");
            } else {
                c = host.getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                        MediaStore.Images.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since / 1000)},
                        MediaStore.Images.Media.DATE_ADDED + " DESC");
            }
            if (c == null || !c.moveToFirst()) {
                // 放宽到 30 分钟 + 文件名前缀（水印相机常见命名）
                long since2 = System.currentTimeMillis() - 30L * 60 * 1000;
                if (c != null) c.close();
                c = video
                        ? host.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME},
                        MediaStore.Video.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since2 / 1000)},
                        MediaStore.Video.Media.DATE_ADDED + " DESC")
                        : host.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                        MediaStore.Images.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since2 / 1000)},
                        MediaStore.Images.Media.DATE_ADDED + " DESC");
            }
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                name = c.getString(1);
                Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                found = android.content.ContentUris.withAppendedId(base, id);
            }
            if (c != null) c.close();
        } catch (Throwable ignored) {
        }

        if (found == null) {
            host.toast("没找到刚拍的，请手动选一下");
            CaptureFlow.pickShot(host, shot, kind);
            return;
        }
        host.toast("已自动取回：" + name);
        CaptureFlow.ingest(host, found, video);
    }

    /** 从相册选已有照片/视频 */
    public static void pickShot(MainActivity host, String shot, String kind) {
        host.pendingShot = shot;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(kind.equals("video") ? "video/*" : "image/*");
        host.startActivityForResult(i, kind.equals("video") ? MainActivity.REQ_PICK_VIDEO : MainActivity.REQ_PICK_PHOTO);
    }

    /** 把一份素材收进当前拍摄项（相机、相册、水印相机自动取回都走这里） */
    public static void ingest(MainActivity host, Uri src, boolean video) {
        try {
            File dir = new File(host.getExternalFilesDir(null), "captures");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, (video ? "VID_" : "IMG_") + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"));
            InputStream in = host.getContentResolver().openInputStream(src);
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            try { host.getContentResolver().delete(src, null, null); } catch (Exception ignored) {}

            if (host.gpsAddress != null && !host.gpsAddress.trim().isEmpty()) {
                host.site.wmAddress = host.gpsAddress.trim();
                host.store.saveQueue(host.site);
            }
            if (!video && !BuildConfigVersion.NO_CAPTURE) {
                WatermarkStamper.stampWatermark(host, dest, dest.lastModified() > 0 ? dest.lastModified() : System.currentTimeMillis(), WatermarkStamper.antiCodeOf(dest));
            }

            String problem = QualityCheck.checkQuality(dest.getAbsolutePath(), video);
            if (problem != null) {
                final File tmpFile = dest;
                final Uri oldUri = src;
                new AlertDialog.Builder(host)
                        .setTitle("这张可能不合格")
                        .setMessage(problem + "\n\n建议重拍，也可以先留着。")
                        .setPositiveButton("重拍", (d, w) -> {
                            try { tmpFile.delete(); } catch (Exception ignored) {}
                            String kind2 = (host.pendingShot != null && host.pendingShot.toLowerCase().contains("video")) ? "video" : "photo";
                            CaptureFlow.captureShot(host, host.pendingShot, kind2);
                        })
                        .setNegativeButton("仍然使用", (d, w) -> CaptureFlow.acceptCapture(host, tmpFile, video))
                        .setCancelable(false)
                        .show();
                return;
            }

            Model.Media m = new Model.Media(dest.getAbsolutePath(), video ? "video" : "photo",
                    video ? "video/mp4" : "image/jpeg", dest.length());
            m.shot = host.pendingShot;
            Model.Task task = SiteFlow.task(host, host.currentTask);
            for (int i = task.media.size() - 1; i >= 0; i--) {
                Model.Media old = task.media.get(i);
                if (host.pendingShot.equals(old.shot)) {
                    // 重拍就是要替换：服务器上已经传过的旧文件也删掉
                    if ("uploaded".equals(old.state) && old.fileId != null && !old.fileId.isEmpty()) {
                        UploadCoordinator.deleteFileRemote(host, old.fileId);
                    }
                    task.media.remove(i);
                }
            }
            task.media.add(m);
            host.store.saveQueue(host.site);

            if (Shots.doneCount(host, host.currentTask) >= Shots.shotsOf(task).length) {
                host.toast("这个任务拍完了");
                host.tvTaskDoneTitle.setText(Shots.labelOf(task) + " 拍完了");
                host.tvTaskDoneSub.setText("site".equals(task.type) ? "这个采集点还有区域要拍吗？" : "还有区域或任务要拍吗？");
                host.showPage(3);
                if (!host.uploading) UploadCoordinator.startUpload(host);
            } else {
                host.currentShot = Shots.firstUndone(host, host.currentTask);
                host.toast("拍好了，接着拍第 " + (host.currentShot + 1) + " 项");
                ShotRenderer.renderShot(host);
                if (!host.uploading) UploadCoordinator.startUpload(host);
            }
        } catch (Exception e) {
            host.toast("保存失败：" + e.getMessage());
        } finally {
            host.tempUri = null;
            host.pendingShot = "";
        }
    }

    /** 质检通过后收下这份素材（原逻辑） */
    public static void acceptCapture(MainActivity host, File dest, boolean video) {
        Model.Media m = new Model.Media(dest.getAbsolutePath(), video ? "video" : "photo",
                video ? "video/mp4" : "image/jpeg", dest.length());
        m.shot = host.pendingShot;
        m.capturedAt = dest.lastModified() > 0 ? dest.lastModified() : System.currentTimeMillis();
        if (m.antiCode == null || m.antiCode.isEmpty()) m.antiCode = WatermarkStamper.antiCodeOf(dest);
        Model.Task task = SiteFlow.task(host, host.currentTask);
        for (int i = task.media.size() - 1; i >= 0; i--) {
            Model.Media old = task.media.get(i);
            if (host.pendingShot.equals(old.shot)) {
                if ("uploaded".equals(old.state) && old.fileId != null && !old.fileId.isEmpty()) {
                    UploadCoordinator.deleteFileRemote(host, old.fileId);
                }
                task.media.remove(i);
            }
        }
        task.media.add(m);
        host.store.saveQueue(host.site);
        if (Shots.doneCount(host, host.currentTask) >= Shots.shotsOf(task).length) {
            if ("task1".equals(task.type) && (task.area == null || task.area.isEmpty())
                    && Shots.doneCount(host, host.currentTask) >= 3) {
                host.toast("门头/全景拍完了，接着填区域和动作");
                Dialogs.showAreaForTask1(host);
            } else if ("site".equals(task.type)) {
                // 门头 + 全景照片 + 全景视频拍完：直接让填区域，不用再点一次
                host.toast("门头/全景拍完了，接着拍区域");
                host.tvTaskDoneTitle.setText("门头与全景 拍完了");
                host.tvTaskDoneSub.setText("接着填这次要拍的区域和动作");
                host.showPage(3);
                host.tvTaskDoneTitle.postDelayed(() -> Dialogs.askTask(host, ""), 400);
            } else {
                host.toast("这个任务拍完了");
                host.tvTaskDoneTitle.setText(Shots.labelOf(task) + " 拍完了");
                host.tvTaskDoneSub.setText("还有区域或任务要拍吗？");
                host.showPage(3);
            }
            if (!host.uploading) UploadCoordinator.startUpload(host);
        } else {
            host.currentShot = Shots.firstUndone(host, host.currentTask);
            host.toast("再拍下一项");
            ShotRenderer.renderShot(host);
        }
    }
}
