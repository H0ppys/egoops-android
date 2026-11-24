package com.egoops.collect.diag;

import java.io.File;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 全局崩溃捕获与上报（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class CrashReporter {

    private CrashReporter() {
    }

    /** 闪退时把堆栈写到本地并上报服务器 */
    public static void installCrashReporter(MainActivity host) {
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String stack = sw.toString();
                if (stack.length() > 6000) stack = stack.substring(0, 6000);
                try {
                    java.io.File dir = new java.io.File(host.getExternalFilesDir(null), "crash");
                    dir.mkdirs();
                    java.io.File f = new java.io.File(dir, "crash-" + System.currentTimeMillis() + ".txt");
                    java.io.FileWriter w = new java.io.FileWriter(f);
                    w.write(stack);
                    w.close();
                } catch (Throwable ignored) { }
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("name", host.store == null ? "" : host.store.name());
                    o.put("message", String.valueOf(e));
                    o.put("stack", stack);
                    o.put("task", host.currentTask + "/" + host.currentShot);
                    byte[] body = o.toString().getBytes("UTF-8");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(host.api.base + "/api/client-log").openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(3000);
                    c.setReadTimeout(3000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    c.setFixedLengthStreamingMode(body.length);
                    c.getOutputStream().write(body);
                    c.getResponseCode();
                    c.disconnect();
                } catch (Throwable ignored) { }
            } catch (Throwable ignored) { }
            if (def != null) def.uncaughtException(t, e);
        });
    }
}
