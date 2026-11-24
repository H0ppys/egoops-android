package com.egoops.collect.settings;

import android.app.AlertDialog;
import android.widget.EditText;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 登录、邀请码、厂商标签（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class AccountManager {

    private AccountManager() {
    }

    public static void autoLogin(MainActivity host) {
        if (!host.api.token.isEmpty()) return;
        final String name = host.store.name();
        if (name.isEmpty()) return;
        new Thread(() -> {
            try {
                AccountManager.loginNow(host, name);
                host.runOnUiThread(() -> {
                    if (host.tvUpload != null) host.tvUpload.setText("已连接服务器");
                    SceneCatalog.loadScenes(host);
                });
            } catch (final Exception e) {
                if (e instanceof Api.ApiException && "INVITE_REQUIRED".equals(((Api.ApiException) e).errorCode)) {
                    host.runOnUiThread(() -> AccountManager.askInvite(host));
                    return;
                }
                final String reason = (e.getMessage() == null ? e.toString() : e.getMessage());
                host.runOnUiThread(() -> {
                    if (host.tvUpload != null) {
                        host.tvUpload.setText("没连上服务器：" + reason + "（点这里重试）");
                        host.tvUpload.setTextColor(MainActivity.DANGER);
                    }
                });
            }
        }).start();
    }

    /** 邀请码：填对之后就知道拍的是哪个厂商的店 */
    public static void askInvite(MainActivity host) {
        final EditText et = new EditText(host);
        et.setText(host.store.invite());
        et.setHint("管理员给的邀请码，例如 ZJMEIYI");
        et.setTextSize(20);
        et.setSingleLine(true);
        new AlertDialog.Builder(host)
                .setTitle("填写邀请码")
                .setMessage("邀请码决定这批照片属于哪个厂商。\n填对后会在拍摄页显示厂商名称。")
                .setView(et)
                .setCancelable(false)
                .setPositiveButton("确定", (d, w) -> {
                    final String code = et.getText().toString().trim().toUpperCase();
                    if (code.isEmpty()) {
                        host.toast("请填邀请码");
                        return;
                    }
                    AccountManager.checkInviteThen(host, code);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 邀请码由管理员和采集员事先约定，服务器不校验，这里只检查格式并记住 */
    public static void checkInviteThen(MainActivity host, final String code) {
        if (code.length() < 4) {
            host.toast("邀请码太短，至少 4 位");
            AccountManager.askInvite(host);
            return;
        }
        host.store.setInvite(code);
        AccountManager.refreshVendorLabel(host);
        SceneCatalog.loadScenes(host, code);
        host.api.token = "";
        host.store.setToken("");
        host.toast("邀请码已记录：" + code);
        UploadCoordinator.startUpload(host);
    }

    /** 拍摄页顶部显示当前邀请码 */
    public static void refreshVendorLabel(MainActivity host) {
        if (host.tvVendor == null) return;
        String code = host.store.invite();
        if (code == null || code.isEmpty()) {
            host.tvVendor.setText("邀请码：未填写（点这里填）");
            host.tvVendor.setTextColor(MainActivity.DANGER);
        } else {
            host.tvVendor.setText("邀请码：" + code + "　（点这里可改）");
            host.tvVendor.setTextColor(MainActivity.MUTED);
        }
        if (host.etInvite != null && !code.isEmpty() && host.etInvite.getText().toString().trim().isEmpty()) {
            host.etInvite.setText(code);
        }
    }

    public static void loginNow(MainActivity host, String name) throws Exception {
        org.json.JSONObject body = new org.json.JSONObject();
        body.put("name", name);
        body.put("phone", host.store.phone());
        body.put("invite", host.store.invite());
        org.json.JSONObject r = host.api.request("POST", "/api/auth/login", body);
        host.api.token = r.optString("token");
        host.store.setToken(host.api.token);
        host.store.setName(name);
        host.runOnUiThread(() -> AccountManager.refreshVendorLabel(host));
    }
}
