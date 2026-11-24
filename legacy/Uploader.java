package com.egoops.collect;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** 上传流水线：建采集点(含任务) → 逐文件分片上传(断点续传) → 合并 */
public class Uploader implements Runnable {

    public interface Listener {
        void onStage(String msg);
        void onProgress(int doneFiles, int totalFiles, long sentBytes, long totalBytes);
        void onDone(int files);
        void onError(String message, String detail);
    }

    private final Api api;
    private final Model.Site site;
    private final Store store;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;

    public Uploader(Api api, Model.Site site, Store store, Listener listener) {
        this.api = api;
        this.site = site;
        this.store = store;
        this.listener = listener;
    }

    public void cancel() { cancelled = true; }

    private void stage(final String msg) {
        ui.post(new Runnable() { public void run() { listener.onStage(msg); } });
    }

    private void progress(final int doneFiles, final int total, final long sent, final long totalBytes) {
        ui.post(new Runnable() {
            public void run() { listener.onProgress(doneFiles, total, sent, totalBytes); }
        });
    }

    @Override
    public void run() {
        try {
            int total = site.fileCount();
            long totalBytes = site.bytes();
            if (total == 0) throw new Exception("还没有拍摄任何素材");

            if (site.siteId == null || site.siteId.isEmpty()) {
                stage("正在创建采集点…");
                createSite();
            }

            int done = 0;
            long sentAll = 0;
            // 用下标遍历：拍摄过程中会新增区域任务/文件，快照会遇到并发修改
            for (int ti = 0; ti < site.tasks.size(); ti++) {
                Model.Task task = site.tasks.get(ti);
                // 采集点建好之后才新增的区域任务，先到服务器登记，拿到 task_id
                if (task.serverId == null || task.serverId.isEmpty()) {
                    ensureTask(task);
                }
                for (int mi = 0; mi < task.media.size(); mi++) {
                    Model.Media m = task.media.get(mi);
                    if (cancelled) return;
                    if (!"uploaded".equals(m.state)) {
                        if ("video".equals(m.kind) && !BuildConfigVersion.NO_CAPTURE) watermarked(m);
                        stage("上传：" + task.name + " · " + new File(m.path).getName());
                        sentAll += uploadOneWithRetry(task, m);
                        store.saveQueue(site);
                    }
                    done++;
                    progress(done, total, sentAll, totalBytes);
                }
            }

            store.appendLedger(site);
            final int n = total;
            ui.post(new Runnable() { public void run() { listener.onDone(n); } });
        } catch (Api.ApiException e) {
            final String msg = e.getMessage();
            final String detail = e.detail;
            ui.post(new Runnable() { public void run() { listener.onError(msg, detail); } });
        } catch (Exception e) {
            final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            ui.post(new Runnable() { public void run() { listener.onError(msg, null); } });
        }
    }

    /** 任务名/区域改了以后同步到服务器（不动素材） */
    public void syncTaskNames() {
        try {
            if (site.siteId == null || site.siteId.isEmpty()) return;
            for (Model.Task t : site.tasks) {
                if (t.serverId == null || t.serverId.isEmpty()) continue;
                JSONObject body = new JSONObject();
                body.put("name", t.name == null ? "" : t.name);
                body.put("area", t.area == null ? "" : t.area);
                api.request("PATCH", "/api/sites/" + site.siteId + "/tasks/" + t.serverId, body);
            }
        } catch (Exception ignored) {
        }
    }

    /** 视频上传前烧水印；失败就用原文件，不阻断上传 */
    private void watermarked(Model.Media m) {
        try {
            File src = new File(m.path);
            if (!src.exists() || src.length() > 200L * 1024 * 1024) return;
            File out = new File(src.getParentFile(), "wm_" + src.getName());
            if (out.exists() && out.length() > 0) {
                String oldPath = m.path;
                m.path = out.getAbsolutePath();
                m.watermarkSrc = oldPath;
                return;
            }
            int w = 0, h = 0;
            try {
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                r.setDataSource(src.getAbsolutePath());
                w = Integer.parseInt(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                h = Integer.parseInt(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                r.release();
            } catch (Throwable ignored) {
            }
            if (w <= 0 || h <= 0) return;

            String wmAddr = site.wmAddress == null || site.wmAddress.isEmpty() ? site.address : site.wmAddress;
            long when = m.capturedAt > 0 ? m.capturedAt : src.lastModified();
            String code = m.antiCode == null ? "" : m.antiCode;
            if (code.isEmpty()) {
                try {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                    java.io.FileInputStream in = new java.io.FileInputStream(src);
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                    in.close();
                    byte[] d = md.digest();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 6; i++) sb.append(String.format("%02X", d[i]));
                    code = sb.toString();
                    m.antiCode = code;
                } catch (Exception ignored) {
                }
            }
            android.graphics.Bitmap map = null;
            try {
                java.io.File dir = new java.io.File(src.getParentFile().getParentFile(), "maps");
                java.io.File[] maps = dir.listFiles();
                if (maps != null && maps.length > 0) {
                    java.io.File newest = maps[0];
                    for (java.io.File f : maps) if (f.lastModified() > newest.lastModified()) newest = f;
                    if (System.currentTimeMillis() - newest.lastModified() < 6L * 3600 * 1000) {
                        map = android.graphics.BitmapFactory.decodeFile(newest.getAbsolutePath());
                    }
                }
            } catch (Throwable ignored) {
            }
            android.graphics.Bitmap overlay = WatermarkVideo.makeOverlay(
                    wmAddr, "", "", w & ~1, h & ~1, when, code, map);
            if (overlay == null) return;
            stage("给视频加水印：" + src.getName());
            WatermarkVideo wv = new WatermarkVideo(overlay, w, h);
            boolean ok = wv.burn(src, out);
            overlay.recycle();
            if (ok && out.exists() && out.length() > 0) {
                String oldPath = m.path;
                m.path = out.getAbsolutePath();
                m.watermarkSrc = oldPath;
            }
        } catch (Throwable ignored) {
        }
    }

    /** 把后加的区域任务登记到服务器，拿回 task_id */
    private void ensureTask(Model.Task task) throws Exception {
        JSONObject body = new JSONObject();
        body.put("name", task.name);
        body.put("area", task.area);
        JSONObject r = api.request("POST", "/api/sites/" + site.siteId + "/tasks", body);
        JSONObject t = r.optJSONObject("task");
        if (t != null) task.serverId = t.optString("id");
        store.saveQueue(site);
    }

    private void createSite() throws Exception {
        JSONObject body = new JSONObject();
        body.put("name", site.name);
        body.put("address", site.address);
        body.put("note", site.note);
        body.put("scene", site.scene);
        body.put("scene_label", site.sceneLabel);
        body.put("area", site.area);
        body.put("invite", site.invite == null ? "" : site.invite);
        JSONArray tasks = new JSONArray();
        for (Model.Task t : site.tasks) {
            JSONObject o = new JSONObject();
            o.put("name", t.name);
            o.put("area", t.area);
            tasks.put(o);
        }
        body.put("tasks", tasks);

        JSONObject r = api.request("POST", "/api/sites", body);
        JSONObject s = r.optJSONObject("site");
        if (s == null) throw new Exception("服务端没有返回采集点");
        site.siteId = s.optString("id");
        JSONArray st = s.optJSONArray("tasks");
        if (st != null) {
            for (int i = 0; i < st.length() && i < site.tasks.size(); i++) {
                site.tasks.get(i).serverId = st.optJSONObject(i).optString("id");
            }
        }
        store.saveQueue(site);
    }

    /** 返回本次实际上传的字节数 */
    /** 分片上传失败自动重试：网络抖一下就重来，最多 4 次 */
    private long uploadOneWithRetry(Model.Task task, Model.Media m) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            if (cancelled) throw new Exception("已取消");
            try {
                return uploadOne(task, m);
            } catch (Exception e) {
                last = e;
                boolean fatal = e instanceof Api.ApiException
                        && ((Api.ApiException) e).errorCode != null
                        && !"NETWORK".equals(((Api.ApiException) e).errorCode);
                if (fatal || attempt == 4) throw e;
                stage("网络不稳，" + (attempt * 2) + " 秒后重试（第 " + attempt + " 次）");
                try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) {
                    throw new Exception("已取消");
                }
            }
        }
        throw last == null ? new Exception("上传失败") : last;
    }

    private long uploadOne(Model.Task task, Model.Media m) throws Exception {
        File f = new File(m.path);
        if (!f.exists()) throw new Exception("本地文件丢失：" + f.getName());

        JSONObject init;
        if (m.uploadId == null || m.uploadId.isEmpty()) {
            JSONObject body = new JSONObject();
            body.put("filename", f.getName());
            body.put("content_type", m.contentType);
            body.put("size", f.length());
            body.put("kind", m.kind);
            if (task.serverId != null && !task.serverId.isEmpty()) body.put("task_id", task.serverId);
            init = api.request("POST", "/api/sites/" + site.siteId + "/uploads", body);
            m.uploadId = init.optString("upload_id");
            store.saveQueue(site);
        } else {
            init = api.request("GET", "/api/uploads/" + m.uploadId, null);
        }

        int chunkSize = init.optInt("chunk_size", 4 * 1024 * 1024);
        int totalChunks = init.optInt("total", (int) Math.ceil(f.length() / (double) chunkSize));
        JSONArray received = init.optJSONArray("received");
        java.util.Set<Integer> have = new java.util.HashSet<Integer>();
        if (received != null) for (int i = 0; i < received.length(); i++) have.add(received.optInt(i));

        long sent = 0;
        for (int i = 0; i < totalChunks; i++) {
            if (cancelled) return sent;
            if (have.contains(i)) continue;
            long offset = (long) i * chunkSize;
            int len = (int) Math.min(chunkSize, f.length() - offset);
            api.putChunk(m.uploadId, i, f, offset, len, null, 0, 0);
            sent += len;
        }

        JSONObject r = api.request("POST", "/api/uploads/" + m.uploadId + "/complete", new JSONObject());
        JSONObject file = r.optJSONObject("file");
        m.fileId = file != null ? file.optString("id") : "";
        m.state = "uploaded";
        store.saveQueue(site);
        return sent;
    }
}
