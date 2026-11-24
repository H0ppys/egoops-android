package com.egoops.collect.settings;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 服务器地址设置对话框与标签工具（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    public static void showSettings(MainActivity host) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(host.dp(20), host.dp(12), host.dp(20), 0);

        final EditText srv = new EditText(host);
        srv.setText(host.store.server());
        srv.setTextSize(15);
        box.addView(SettingsDialog.labelText(host, "服务器地址"));
        box.addView(srv);

        new AlertDialog.Builder(host)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    host.store.setServer(srv.getText().toString().trim());
                    host.api.base = host.store.server();
                    host.api.token = "";
                    host.store.setToken("");
                    AccountManager.autoLogin(host);
                    LocationTracker.startGps(host);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public static TextView labelText(MainActivity host, String s) {
        TextView t = new TextView(host);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(MainActivity.MUTED);
        t.setPadding(0, host.dp(10), 0, host.dp(2));
        return t;
    }
}
