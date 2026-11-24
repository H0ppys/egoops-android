package com.egoops.collect.queue;

import java.io.File;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 上传队列：触发上传、进度回调、续传、远端清理（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class UploadCoordinator {

    private UploadCoordinator() {
    }

    /** 后台删掉服务器上的采集点（含文件） */
    public static void deleteSiteRemote(MainActivity host, final String siteId) {
        new Thread(() -> {
            try { host.api.request("DELETE", "/api/sites/" + siteId, null); } catch (Exception ignored) { }
        }).start();
    }

    /** 后台删掉服务器上的单个文件（重拍时替换旧文件用） */
    public static void deleteFileRemote(MainActivity host, final String fileId) {
        new Thread(() -> {
            try { host.api.request("DELETE", "/api/files/" + fileId, null); } catch (Exception ignored) { }
        }).start();
    }

    /** 传完的素材：生成缩略图留本地，原文件删掉（服务器已有） */
    public static void archiveUploaded(MainActivity host) {
        new Thread(() -> {
            try {
                File thumbDir = new File(host.getExternalFilesDir(null), "thumbs");
                if (!thumbDir.exists()) thumbDir.mkdirs();
                for (Model.Task t : host.site.tasks) {
                    for (Model.Media m : t.media) {
                        if (!"uploaded".equals(m.state)) continue;
                        File src = new File(m.path);
                        if (!src.exists()) continue;
                        File th = new File(thumbDir, src.getName() + ".jpg");
                        if (!th.exists()) {
                            if ("photo".equals(m.kind)) {
                                Thumbnails.saveThumb(src, th, 320);
                            } else {
                                Thumbnails.saveVideoThumb(src, th);
                            }
                        }
                        if (th.exists() && th.length() > 0) src.delete();
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    /** 上次没传完：打开 App 自动接着传 */
    public static void resumePendingUpload(MainActivity host) {
        try {
            Model.Site saved = host.store.loadQueue();
            if (saved == null || saved.fileCount() == 0) return;
            int pending = 0;
            for (Model.Task t : saved.tasks) {
                for (Model.Media m : t.media) {
                    if (!"uploaded".equals(m.state)) pending++;
                }
            }
            if (pending == 0) return;
            final int n = pending;
            host.toast("上次还有 " + n + " 个文件没传完，自动继续");
            host.tvUpload.postDelayed(() -> UploadCoordinator.startUpload(host), 1500);
        } catch (Exception ignored) {
        }
    }

    public static void startUpload(MainActivity host) {
        if (host.uploading) return;
        if (host.site.fileCount() == 0) return;
        if (host.api.token.isEmpty()) {
            final String name = host.etCollector != null ? host.etCollector.getText().toString().trim() : host.store.name();
            if (name.isEmpty()) { host.tvUpload.setText("请先填写采集员姓名"); host.tvUpload.setTextColor(MainActivity.DANGER); return; }
            host.tvUpload.setText("正在连接服务器…");
            host.tvUpload.setTextColor(MainActivity.MUTED);
            new Thread(() -> {
                try {
                    AccountManager.loginNow(host, name);
                    host.runOnUiThread(() -> UploadCoordinator.startUpload(host));
                } catch (final Exception e) {
                    if (e instanceof Api.ApiException && "INVITE_REQUIRED".equals(((Api.ApiException) e).errorCode)) {
                        host.runOnUiThread(() -> AccountManager.askInvite(host));
                        return;
                    }
                    final String reason2 = (e.getMessage() == null ? e.toString() : e.getMessage());
                    host.runOnUiThread(() -> {
                        host.tvUpload.setText("没连上服务器：" + reason2 + "（点这里重试）");
                        host.tvUpload.setTextColor(MainActivity.DANGER);
                    });
                }
            }).start();
            return;
        }
        host.uploading = true;
        host.tvUpload.setText("正在上传…");
        host.tvUpload.setTextColor(MainActivity.MUTED);
        UploadService.start(host, "准备上传…");
        new Thread(new Uploader(host.api, host.site, host.store, new Uploader.Listener() {
            @Override public void onStage(String msg) {
                host.runOnUiThread(() -> UploadService.update(host, msg, -1, 0, 0));
            }

            @Override public void onProgress(int done, int total, long sent, long totalBytes) {
                int pct = totalBytes > 0 ? (int) (sent * 100 / totalBytes) : 0;
                String s = "已上传 " + done + "/" + total + " 个文件 · " + pct + "%";
                UploadService.update(host, s, pct, done, total);
                host.tvUpload.setText(s);
                host.tvUpload.setTextColor(MainActivity.MUTED);
                if (host.tvDoneStatus != null) {
                    host.tvDoneStatus.setText(s);
                    host.tvDoneStatus.setTextColor(MainActivity.MUTED);
                }
            }

            @Override public void onDone(int files) {
                host.uploading = false;
                UploadCoordinator.archiveUploaded(host);
                UploadService.stop(host, "上传完成，共 " + files + " 个文件");
                host.tvUpload.setText("全部上传完成 ✓  " + files + " 个文件");
                host.tvUpload.setTextColor(MainActivity.OK);
                if (host.tvDoneStatus != null) {
                    host.tvDoneStatus.setText("全部上传完成，共 " + files + " 个文件");
                    host.tvDoneStatus.setTextColor(MainActivity.OK);
                }
            }

            @Override public void onError(String message, String detail) {
                host.uploading = false;
                UploadService.stop(host, "上传中断，打开 App 可继续");
                host.tvUpload.setText("上传失败，点这里重试");
                host.tvUpload.setTextColor(MainActivity.DANGER);
                if (host.tvDoneStatus != null) {
                    host.tvDoneStatus.setText("还有文件没传完，点“上传失败，点这里重试”");
                    host.tvDoneStatus.setTextColor(MainActivity.DANGER);
                }
            }
        })).start();
    }
}
