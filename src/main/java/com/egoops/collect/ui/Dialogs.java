package com.egoops.collect.ui;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;

/** 各类对话框：拍摄指引、区域/任务填写、重新开始、崩溃回看（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class Dialogs {

    private Dialogs() {
    }

    public static void showGuide(MainActivity host, String shot) {
        new AlertDialog.Builder(host)
                .setTitle("怎么拍：「" + shot + "」")
                .setMessage(Shots.guideOf(shot))
                .setPositiveButton("知道了", null)
                .show();
    }

    public static void confirmRestart(MainActivity host) {
        final String oldSiteId = host.site.siteId;
        new AlertDialog.Builder(host)
                .setTitle("重新开始？")
                .setMessage("这一家会从头再拍一遍。\n已经传到服务器上的照片/视频会一起删掉，不会留下旧的。")
                .setPositiveButton("重新开始", (d, w) -> {
                    if (oldSiteId != null && !oldSiteId.isEmpty()) UploadCoordinator.deleteSiteRemote(host, oldSiteId);
                    host.site = new Model.Site();
                    host.site.name = host.etStoreName.getText().toString().trim();
                    Model.Task t = new Model.Task();
                    t.type = "site";
                    t.name = "任务1·门头与全景";
                    host.site.tasks.add(t);
                    host.currentTask = 0;
                    host.currentShot = 0;
                    host.store.saveQueue(host.site);
                    host.showPage(2);
                    ShotRenderer.renderShot(host);
                })
                .setNegativeButton("不了", null)
                .show();
    }

    public static void addTask(MainActivity host) {
        String preset = "";
        if (host.currentTask >= 0 && host.currentTask < host.site.tasks.size()) {
            preset = host.site.tasks.get(host.currentTask).area;
        }
        Dialogs.askTask(host, preset);
    }

    /** 任务1 的区域填写：写回当前任务，不新建 */
    public static void showAreaForTask1(MainActivity host) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(host.dp(20), host.dp(8), host.dp(20), 0);

        TextView tip = new TextView(host);
        tip.setText("门头照 + 全景照片 + 全景视频 已拍好。\n现在填这次要拍的第一个区域：");
        tip.setTextSize(14);
        tip.setTextColor(MainActivity.MUTED);
        tip.setPadding(0, 0, 0, host.dp(8));
        box.addView(tip);

        TextView l1 = new TextView(host);
        l1.setText("1. 这是哪个区域？（比如：饮料区 / 后厨）");
        l1.setTextSize(13);
        l1.setTextColor(MainActivity.MUTED);
        box.addView(l1);

        final EditText etAreaName = new EditText(host);
        etAreaName.setHint("饮料区");
        etAreaName.setTextSize(20);
        box.addView(etAreaName);

        TextView l2 = new TextView(host);
        l2.setText("2. 在这个区域要做什么动作？（比如：捡垃圾 / 擦拭桌面）");
        l2.setTextSize(13);
        l2.setTextColor(MainActivity.MUTED);
        l2.setPadding(0, host.dp(12), 0, 4);
        box.addView(l2);

        final EditText etTaskName = new EditText(host);
        etTaskName.setHint("捡垃圾");
        etTaskName.setTextSize(20);
        box.addView(etTaskName);

        final AlertDialog dlg = new AlertDialog.Builder(host)
                .setTitle("填写区域和动作")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("开始拍摄这个区域", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = etAreaName.getText().toString().trim();
            String n = etTaskName.getText().toString().trim();
            if (a.isEmpty()) { host.toast("请填写区域名称"); etAreaName.requestFocus(); return; }
            if (n.isEmpty()) { host.toast("请填写动作名称"); etTaskName.requestFocus(); return; }
            Model.Task t = host.site.tasks.get(0);
            t.area = a;
            t.name = n + " · 门头与全景";
            host.store.saveQueue(host.site);
            SiteFlow.syncTaskNamesAsync(host);
            host.currentTask = 0;
            host.currentShot = Shots.firstUndone(host, 0);
            if (host.currentShot < 0) host.currentShot = 3;
            dlg.dismiss();
            ShotRenderer.renderShot(host);
        });
    }

    public static void askTask(MainActivity host, String presetArea) {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(host.dp(20), host.dp(8), host.dp(20), 0);

        TextView l1 = new TextView(host);
        l1.setText("1. 这是哪个区域？（比如：客厅 / 后厨 / 3号包间）");
        l1.setTextSize(13);
        l1.setTextColor(MainActivity.MUTED);
        l1.setPadding(0, host.dp(8), 0, host.dp(4));
        box.addView(l1);

        final EditText etAreaName = new EditText(host);
        etAreaName.setHint("客厅");
        etAreaName.setTextSize(20);
        etAreaName.setText(presetArea == null ? "" : presetArea);
        box.addView(etAreaName);

        TextView l2 = new TextView(host);
        l2.setText("2. 在这个区域要做什么动作？（比如：擦拭桌面 / 整理货架）");
        l2.setTextSize(13);
        l2.setTextColor(MainActivity.MUTED);
        l2.setPadding(0, host.dp(14), 0, host.dp(4));
        box.addView(l2);

        final EditText etTaskName = new EditText(host);
        etTaskName.setHint("擦拭桌面");
        etTaskName.setTextSize(20);
        box.addView(etTaskName);

        final AlertDialog dlg = new AlertDialog.Builder(host)
                .setTitle("填写区域和动作")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("开始拍摄这个区域", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
            String a = etAreaName.getText().toString().trim();
            String n = etTaskName.getText().toString().trim();
            if (a.isEmpty()) { host.toast("请填写区域名称"); etAreaName.requestFocus(); return; }
            if (n.isEmpty()) { host.toast("请填写任务名称"); etTaskName.requestFocus(); return; }
            Model.Task t;
            boolean firstArea = host.site.tasks.size() == 1 && "site".equals(host.site.tasks.get(0).type);
            if (firstArea) {
                // 门头/全景就并进这第一个区域，成为「任务1」
                t = host.site.tasks.get(0);
                t.type = "area";
                t.area = a;
                t.name = n;
                host.currentTask = 0;
                host.toast("任务1 = 门头/全景 + " + a);
            } else {
                t = new Model.Task();
                t.type = "area";
                t.area = a;
                t.name = n;
                host.site.tasks.add(t);
                host.currentTask = host.site.tasks.size() - 1;
            }
            host.currentShot = Shots.firstUndone(host, host.currentTask);
            if (host.currentShot < 0) host.currentShot = 0;
            host.store.saveQueue(host.site);
            SiteFlow.syncTaskNamesAsync(host);
            try {
                host.showPage(2);
                ShotRenderer.renderShot(host);
            } catch (Exception e) {
                host.toast("打开拍摄页失败：" + e);
            }
            dlg.dismiss();
            } catch (Exception ex) {
                host.toast("添加区域失败：" + ex);
            }
        });
        if (presetArea != null && !presetArea.isEmpty()) etTaskName.requestFocus();
    }

    /** 上次崩溃过就弹出来，方便把堆栈截图发给管理员 */
    public static void showLastCrash(MainActivity host) {
        try {
            java.io.File dir = new java.io.File(host.getExternalFilesDir(null), "crash");
            if (!dir.exists()) return;
            java.io.File[] files = dir.listFiles();
            if (files == null || files.length == 0) return;
            java.io.File newest = files[0];
            for (java.io.File f : files) if (f.lastModified() > newest.lastModified()) newest = f;
            if (System.currentTimeMillis() - newest.lastModified() > 24L * 3600 * 1000) return;

            byte[] buf = new byte[(int) Math.min(newest.length(), 3000)];
            java.io.FileInputStream in = new java.io.FileInputStream(newest);
            int n = in.read(buf);
            in.close();
            String stack = new String(buf, 0, Math.max(n, 0), "UTF-8");
            String[] lines = stack.split("\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 12); i++) sb.append(lines[i]).append("\n");

            final java.io.File crashFile = newest;
            new AlertDialog.Builder(host)
                    .setTitle("上次运行出错了")
                    .setMessage(sb.toString())
                    .setPositiveButton("知道了", (d, w) -> crashFile.delete())
                    .show();
        } catch (Exception ignored) {
        }
    }
}
