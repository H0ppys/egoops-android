package com.egoops.collect.settings;

import java.io.InputStream;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 省市区数据加载与联动刷新（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class RegionCatalog {

    private RegionCatalog() {
    }

    /** 从 res/raw/pca.json 读省市区（31 省 / 300+ 市 / 2900+ 区县） */
    public static void loadRegions(MainActivity host) {
        if (host.provArr.length > 0) return;
        new Thread(() -> {
            try {
                java.io.InputStream in = host.getResources().openRawResource(R.raw.pca);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                org.json.JSONArray arr = new org.json.JSONArray(bos.toString("UTF-8"));
                String[] provs = new String[arr.length() + 1];
                String[][] cities = new String[arr.length()][];
                String[][][] dists = new String[arr.length()][][];
                provs[0] = "省份";
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject po = arr.getJSONObject(i);
                    provs[i + 1] = po.getString("name");
                    org.json.JSONArray cs = po.optJSONArray("children");
                    int cn = cs == null ? 0 : cs.length();
                    cities[i] = new String[cn];
                    dists[i] = new String[cn][];
                    for (int j = 0; j < cn; j++) {
                        org.json.JSONObject co = cs.getJSONObject(j);
                        cities[i][j] = co.getString("name");
                        org.json.JSONArray ds = co.optJSONArray("children");
                        int dn = ds == null ? 0 : ds.length();
                        dists[i][j] = new String[dn];
                        for (int k = 0; k < dn; k++) dists[i][j][k] = ds.getJSONObject(k).getString("name");
                    }
                }
                host.runOnUiThread(() -> {
                    host.provArr = provs;
                    host.cityArr = cities;
                    host.distArr = dists;
                    host.spProv.setAdapter(new android.widget.ArrayAdapter<String>(host,
                            android.R.layout.simple_spinner_dropdown_item, host.provArr));
                });
            } catch (Exception e) {
                host.runOnUiThread(() -> host.toast("地址数据加载失败：" + e.getMessage()));
            }
        }).start();
    }

    public static void updateCities(MainActivity host, int provPos) {
        if (provPos <= 0 || provPos - 1 >= host.cityArr.length) {
            host.spCity.setAdapter(new android.widget.ArrayAdapter<String>(host,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"城市"}));
            return;
        }
        String[] cs = host.cityArr[provPos - 1];
        host.spCity.setAdapter(new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_dropdown_item, cs.length == 0 ? new String[]{"城市"} : cs));
        RegionCatalog.updateDistricts(host, 0);
    }

    public static void updateDistricts(MainActivity host, int cityPos) {
        int provPos = host.spProv.getSelectedItemPosition();
        if (provPos <= 0 || provPos - 1 >= host.distArr.length) return;
        String[][] ds = host.distArr[provPos - 1];
        if (cityPos < 0 || cityPos >= ds.length) {
            host.spDist.setAdapter(new android.widget.ArrayAdapter<String>(host,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"区 / 县"}));
            return;
        }
        host.spDist.setAdapter(new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_dropdown_item, ds[cityPos]));
    }
}
