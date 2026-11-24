package com.egoops.collect;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/** 设置 + 本地断点队列 + 台账 */
public class Store {

    private final SharedPreferences sp;
    private final File queueFile;
    private final File ledgerFile;

    public Store(Context ctx) {
        sp = ctx.getSharedPreferences("egoops", Context.MODE_PRIVATE);
        File dir = new File(ctx.getFilesDir(), "queue");
        if (!dir.exists()) dir.mkdirs();
        queueFile = new File(dir, "site.json");
        ledgerFile = new File(dir, "ledger.jsonl");
    }

    public String server() { return sp.getString("server", "https://123.207.6.210/relay"); }
    public void setServer(String v) { sp.edit().putString("server", v).apply(); }

    public String name() { return sp.getString("name", ""); }
    public void setName(String v) { sp.edit().putString("name", v).apply(); }

    public String phone() { return sp.getString("phone", ""); }
    public void setPhone(String v) { sp.edit().putString("phone", v).apply(); }

    public String token() { return sp.getString("token", ""); }
    public void setToken(String v) { sp.edit().putString("token", v).apply(); }

    public String invite() { return sp.getString("invite", ""); }
    public void setInvite(String v) { sp.edit().putString("invite", v).apply(); }

    public int skippedVersion() { return sp.getInt("skippedVersion", 0); }
    public void setSkippedVersion(int v) { sp.edit().putInt("skippedVersion", v).apply(); }

    public String get(String k, String def) { return sp.getString(k, def); }
    public void put(String k, String v) { sp.edit().putString(k, v).apply(); }

    public String vendor() { return sp.getString("vendor", ""); }
    public void setVendor(String v) { sp.edit().putString("vendor", v).apply(); }

    public void saveQueue(Model.Site site) {
        try {
            writeAll(queueFile, site.toJson().toString().getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    public Model.Site loadQueue() {
        try {
            String s = readAll(queueFile);
            if (s == null || s.trim().isEmpty()) return null;
            return Model.Site.fromJson(new JSONObject(s));
        } catch (Exception e) {
            return null;
        }
    }

    public void clearQueue() {
        if (queueFile.exists()) queueFile.delete();
    }

    public void appendLedger(Model.Site site) {
        try {
            JSONObject o = site.toJson();
            o.put("savedAt", System.currentTimeMillis());
            writeAppend(ledgerFile, (o.toString() + "\n").getBytes("UTF-8"));
        } catch (Exception ignored) {}
    }

    public String tailLedger(int maxLines) {
        try {
            String all = readAll(ledgerFile);
            if (all == null) return "";
            String[] lines = all.split("\n");
            StringBuilder sb = new StringBuilder();
            int from = Math.max(0, lines.length - maxLines);
            for (int i = from; i < lines.length; i++) {
                if (lines[i].trim().isEmpty()) continue;
                JSONObject o = new JSONObject(lines[i]);
                int n = 0;
                org.json.JSONArray tasks = o.optJSONArray("tasks");
                if (tasks != null) {
                    for (int k = 0; k < tasks.length(); k++) {
                        JSONObject t = tasks.optJSONObject(k);
                        org.json.JSONArray ms = t != null ? t.optJSONArray("media") : null;
                        if (ms != null) n += ms.length();
                    }
                }
                sb.append("· ").append(o.optString("name")).append(" · ").append(n).append(" 个文件\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static void writeAll(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f, false);
        out.write(data);
        out.close();
    }

    static void writeAppend(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f, true);
        out.write(data);
        out.close();
    }

    static String readAll(File f) throws Exception {
        if (!f.exists()) return null;
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        byte[] buf = new byte[(int) raf.length()];
        raf.readFully(buf);
        raf.close();
        return new String(buf, "UTF-8");
    }
}
