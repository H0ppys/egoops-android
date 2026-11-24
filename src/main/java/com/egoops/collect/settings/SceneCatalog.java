package com.egoops.collect.settings;

import android.net.Uri;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 场景分类二级联动与远端/离线加载（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class SceneCatalog {

    private SceneCatalog() {
    }

    public static void refreshSceneSpinner(MainActivity host) {
        int catPos = host.spCat == null ? 0 : host.spCat.getSelectedItemPosition();
        host.shownIndex.clear();
        List<String> labels = new ArrayList<String>();
        if (catPos <= 0) {
            labels.add("① 先选上面的大类");
        } else {
            String cat = host.categories.get(catPos - 1);
            for (int i = 0; i < host.sceneItems.size(); i++) {
                if (cat.equals(host.sceneItems.get(i)[2])) {
                    String[] row = host.sceneItems.get(i);
                    String text = row.length > 3 && row[3] != null && !row[3].isEmpty() ? row[3] : row[1];
                    labels.add(text);
                    host.shownIndex.add(i);
                }
            }
            if (labels.isEmpty()) labels.add("这个大类下没有场景");
        }
        if (host.spScene2 != null) {
            host.spScene2.setAdapter(new android.widget.ArrayAdapter<String>(host,
                    android.R.layout.simple_spinner_dropdown_item, labels));
        }
    }

    /** 输入框里的邀请码变了：自动按它加载分类（同码不重复加载） */
    public static void autoLoadScenes(MainActivity host) {
        if (host.etInvite == null) return;
        String code = host.etInvite.getText().toString().trim().toUpperCase();
        if (code.length() < 4) return;
        if (code.equals(host.lastSceneInvite)) return;
        host.store.setInvite(code);
        SceneCatalog.loadScenes(host, code);
    }

    public static void loadScenes(MainActivity host) {
        SceneCatalog.loadScenes(host, host.store.invite());
    }

    /** 先用这个邀请码问服务器「你能看到哪些大类」，拿不到就用安装包里的全量 */
    public static void loadScenes(MainActivity host, final String invite) {
        new Thread(() -> {
            String code = invite == null ? "" : invite.trim().toUpperCase();
            org.json.JSONArray items = null;
            String note = "";
            if (!code.isEmpty()) {
                try {
                    org.json.JSONObject r = host.api.request("GET",
                            "/api/scenes?vendor=" + android.net.Uri.encode(code), null);
                    items = r.optJSONArray("items");
                    if (items != null && items.length() > 0) {
                        int n = items.length();
                        note = code + " 可见 " + n + " 个场景";
                    }
                } catch (Exception e) {
                    items = null;
                }
            }
            if (items == null || items.length() == 0) {
                try {
                    java.io.InputStream in = host.getResources().openRawResource(R.raw.scenes);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    in.close();
                    items = new org.json.JSONObject(bos.toString("UTF-8")).optJSONArray("items");
                    note = "离线分类表";
                } catch (Exception e2) {
                    final String msg = e2.getMessage() == null ? e2.toString() : e2.getMessage();
                    host.runOnUiThread(() -> host.toast("场景分类读取失败：" + msg));
                    return;
                }
            }

            final List<String[]> list = new ArrayList<String[]>();
            final List<String> cats = new ArrayList<String>();
            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject o = items.optJSONObject(i);
                if (o == null) continue;
                String path = o.optString("path");
                String label = o.optString("label");
                String cat = o.optString("category");
                String scene = o.optString("scene");
                if (path == null || path.isEmpty()) continue;
                list.add(new String[]{path, label, cat, scene});
                if (!cats.contains(cat)) cats.add(cat);
            }

            final String tip = note;
            host.lastSceneInvite = code;
            host.runOnUiThread(() -> {
                host.sceneItems.clear();
                host.sceneItems.addAll(list);
                host.categories.clear();
                host.categories.addAll(cats);

                List<String> catLabels = new ArrayList<String>();
                catLabels.add("① 请选择大类");
                catLabels.addAll(host.categories);
                if (host.spCat != null) {
                    host.spCat.setAdapter(new android.widget.ArrayAdapter<String>(host,
                            android.R.layout.simple_spinner_dropdown_item, catLabels));
                    host.spCat.setSelection(0);
                    host.spCat.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                            SceneCatalog.refreshSceneSpinner(host);
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
                    });
                }
                SceneCatalog.refreshSceneSpinner(host);
                if (host.tvVendor != null && !tip.isEmpty()) host.tvVendor.setText("分类：" + tip + "（点这里可改邀请码）");
            });
        }).start();
    }
}
