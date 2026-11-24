package com.egoops.collect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 采集点 → 任务 → 素材（对齐「申请采集点」的结构） */
public class Model {

    public static class Media {
        public String path = "";
        public String kind = "photo";        // photo | video
        public String shot = "";             // door | pano | counter | video
        public String contentType = "image/jpeg";
        public long size = 0;
        public String uploadId = "";
        public String fileId = "";
        public String state = "local";       // local | uploaded
        public String watermarkSrc = "";     // 加水印前的原文件路径
        public long capturedAt = 0;          // 拍摄时刻（毫秒）
        public String antiCode = "";         // 防伪码（SHA1 前 12 位）

        public Media() {}

        public Media(String path, String kind, String contentType, long size) {
            this.path = path;
            this.kind = kind;
            this.contentType = contentType;
            this.size = size;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("path", path);
                o.put("kind", kind);
                o.put("shot", shot);
        o.put("captured_at", capturedAt);
        o.put("anti_code", antiCode);
                o.put("contentType", contentType);
                o.put("size", size);
                o.put("uploadId", uploadId);
                o.put("fileId", fileId);
                o.put("state", state);
            } catch (Exception ignored) {}
            return o;
        }

        public static Media fromJson(JSONObject o) {
            Media m = new Media();
            m.path = o.optString("path");
            m.kind = o.optString("kind", "photo");
            m.shot = o.optString("shot", "");
            m.contentType = o.optString("contentType", "image/jpeg");
            m.size = o.optLong("size");
            m.uploadId = o.optString("uploadId");
            m.fileId = o.optString("fileId");
            m.state = o.optString("state", "local");
            return m;
        }
    }

    public static class Task {
        public String name = "";
        public String area = "";            // 区域名称，如 客厅
        public String type = "area";        // site = 采集点(门头/全景)，area = 区域
        public String serverId = "";
        public List<Media> media = new ArrayList<Media>();

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("name", name);
                o.put("area", area);
                o.put("type", type);
                o.put("serverId", serverId);
                JSONArray arr = new JSONArray();
                for (Media m : media) arr.put(m.toJson());
                o.put("media", arr);
            } catch (Exception ignored) {}
            return o;
        }

        public static Task fromJson(JSONObject o) {
            Task t = new Task();
            t.name = o.optString("name");
            t.area = o.optString("area");
            t.type = o.optString("type", "area");
            t.serverId = o.optString("serverId");
            JSONArray arr = o.optJSONArray("media");
            if (arr != null) for (int i = 0; i < arr.length(); i++) t.media.add(Media.fromJson(arr.optJSONObject(i)));
            return t;
        }
    }

    public static class Site {
        public String name = "";
        public String collector = "";
        public String wmAddress = "";
        public String address = "";
        public String note = "";
        public String scene = "";
        public String invite = "";          // 采集场景分类 path，如 home/detached_house
        public String sceneLabel = "";     // 展厅 / 自建房
        public double area = 0;            // 场地面积（㎡）
        public String siteId = "";
        public List<Task> tasks = new ArrayList<Task>();

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("name", name);
                o.put("address", address);
                o.put("note", note);
                o.put("scene", scene);
                o.put("sceneLabel", sceneLabel);
                o.put("area", area);
                o.put("siteId", siteId);
                JSONArray arr = new JSONArray();
                for (Task t : tasks) arr.put(t.toJson());
                o.put("tasks", arr);
            } catch (Exception ignored) {}
            return o;
        }

        public static Site fromJson(JSONObject o) {
            Site s = new Site();
            s.name = o.optString("name");
            s.address = o.optString("address");
            s.note = o.optString("note");
            s.scene = o.optString("scene");
            s.sceneLabel = o.optString("sceneLabel");
            s.area = o.optDouble("area", 0);
            s.siteId = o.optString("siteId");
            JSONArray arr = o.optJSONArray("tasks");
            if (arr != null) for (int i = 0; i < arr.length(); i++) s.tasks.add(Task.fromJson(arr.optJSONObject(i)));
            return s;
        }

        public int photoCount() {
            int n = 0;
            for (Task t : tasks) for (Media m : t.media) if ("photo".equals(m.kind)) n++;
            return n;
        }

        public int videoCount() {
            int n = 0;
            for (Task t : tasks) for (Media m : t.media) if ("video".equals(m.kind)) n++;
            return n;
        }

        public int fileCount() {
            int n = 0;
            for (Task t : tasks) n += t.media.size();
            return n;
        }

        public long bytes() {
            long n = 0;
            for (Task t : tasks) for (Media m : t.media) n += m.size;
            return n;
        }
    }
}
