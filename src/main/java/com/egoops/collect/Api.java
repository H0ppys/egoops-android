package com.egoops.collect;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 服务器客户端：JSON 调用 + 分片上传 */
public class Api {

    public interface Progress { void onProgress(long sent, long total); }

    public String base;
    public String token;

    public Api(String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static class ApiException extends Exception {
        public int status;
        public String detail;
        public String errorCode;
        public ApiException(int status, String message, String detail) {
            super(message);
            this.status = status;
            this.detail = detail;
        }
    }

    /** 上一次 https 失败的原因（给界面显示用） */
    public String lastHttpsError = "";

    /** https 因为证书/握手失败时，自动退回 http（服务器 80 也开着） */
    private void fallbackToHttp(String reason) {
        try {
            lastHttpsError = reason;
            if (base.startsWith("https://")) {
                base = "http://" + base.substring(8);
            }
        } catch (Exception ignored) { }
    }

    private HttpURLConnection open(String method, String path, String contentType) throws Exception {
        URL url = new URL(base + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(300000);
        c.setRequestProperty("Accept", "application/json");
        if (contentType != null) c.setRequestProperty("Content-Type", contentType);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public JSONObject request(String method, String path, JSONObject body) throws Exception {
        try {
            return requestOnce(method, path, body);
        } catch (javax.net.ssl.SSLException e) {
            fallbackToHttp(String.valueOf(e.getMessage()));
            return requestOnce(method, path, body);
        } catch (java.io.IOException e) {
            String msg = String.valueOf(e.getMessage());
            if (base.startsWith("https://") && (msg.contains("SSL") || msg.contains("Trust") || msg.contains("Certificate"))) {
                fallbackToHttp(msg);
                return requestOnce(method, path, body);
            }
            throw e;
        }
    }

    public JSONObject requestOnce(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(method, path, body == null ? null : "application/json; charset=utf-8");
        try {
            if (body != null) {
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(data.length);
                OutputStream os = c.getOutputStream();
                os.write(data);
                os.flush();
                os.close();
            }
            int code = c.getResponseCode();
            String text = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code >= 400) throw toError(code, text);
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally {
            c.disconnect();
        }
    }

    private ApiException toError(int code, String text) {
        String msg = "HTTP " + code;
        String detail = null;
        String errorCode = null;
        try {
            JSONObject o = new JSONObject(text).optJSONObject("error");
            if (o != null) {
                msg = o.optString("message", msg);
                errorCode = o.optString("code", null);
                Object d = o.opt("detail");
                if (d != null) detail = d.toString();
            }
        } catch (Exception ignored) {
            if (text != null && text.length() > 0) detail = text.length() > 300 ? text.substring(0, 300) : text;
        }
        ApiException e = new ApiException(code, msg, detail);
        e.errorCode = errorCode;
        return e;
    }

    /** 上传一个分片：offset 起、len 字节 */
    public void putChunk(String uploadId, int index, File file, long offset, int len, Progress p, long doneBefore, long totalAll) throws Exception {
        HttpURLConnection c = open("PUT", "/api/uploads/" + uploadId + "/" + index, "application/octet-stream");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(len);
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            raf.seek(offset);
            OutputStream os = c.getOutputStream();
            byte[] buf = new byte[128 * 1024];
            int left = len;
            long sent = 0;
            while (left > 0) {
                int n = raf.read(buf, 0, Math.min(buf.length, left));
                if (n <= 0) break;
                os.write(buf, 0, n);
                left -= n;
                sent += n;
                if (p != null) p.onProgress(doneBefore + sent, totalAll);
            }
            os.flush();
            os.close();
        } finally {
            raf.close();
        }
        int code = c.getResponseCode();
        String text = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        if (code >= 400) throw toError(code, text);
    }
}
