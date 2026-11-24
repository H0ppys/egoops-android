package com.egoops.collect.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

/** 当前拍摄项卡片渲染（预览、进度点、重拍入口）（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class ShotRenderer {

    private ShotRenderer() {
    }

    public static void renderShot(MainActivity host) {
        host.shotArea.removeAllViews();
        Model.Task task = SiteFlow.task(host, host.currentTask);
        String[][] shots = Shots.shotsOf(task);
        if (host.currentShot < 0 || host.currentShot >= shots.length) {
            host.currentShot = Shots.firstUndone(host, host.currentTask);
            if (host.currentShot >= shots.length) {
                host.tvTaskDoneTitle.setText(Shots.labelOf(task) + " 拍完了");
                host.tvTaskDoneSub.setText("site".equals(task.type) ? "这个采集点还有区域要拍吗？" : "还有区域或任务要拍吗？");
                host.showPage(3);
                return;
            }
        }
        if ("task1".equals(task.type) && host.currentShot >= 3
                && (task.area == null || task.area.isEmpty())) {
            Dialogs.showAreaForTask1(host);
            return;
        }

        int idx = host.currentShot;
        String key = shots[idx][0];
        String title = shots[idx][1];
        String hint = shots[idx][2];
        String kind = shots[idx][3];
        boolean done = Shots.hasShot(host, host.currentTask, idx);

        String prefix = "area".equals(task.type) ? "" : "";
        host.tvStep.setText(prefix + Shots.labelOf(task) + " · 第 " + (idx + 1) + "/" + shots.length + " 项 · " + title);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < shots.length; i++) {
            if (i > 0) dots.append("   ");
            if (i == host.currentShot) dots.append("◆");            // 当前位置
            else if (Shots.hasShot(host, host.currentTask, i)) dots.append("●"); // 已完成
            else dots.append("○");                              // 未拍
        }
        host.tvDots.setText(dots.toString());

        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(host.roundRect(MainActivity.CARD, 18));
        int pad = host.dp(20);
        card.setPadding(pad, pad, pad, pad);
        host.shotArea.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        try {
            int w = host.getResources().getDisplayMetrics().widthPixels;
            card.setTranslationX(host.slideDir > 0 ? w * 0.35f : -w * 0.35f);
            card.setAlpha(0.25f);
            card.animate().translationX(0f).alpha(1f).setDuration(200).start();
        } catch (Throwable ignored) {
        }

        if (done) {
            Model.Media mine = null;
            for (Model.Media mm : task.media) {
                if (key.equals(mm.shot)) mine = mm;
            }
            if (mine != null) {
                try {
                    java.io.File f = new java.io.File(mine.path);
                    if (f.exists() && "photo".equals(mine.kind)) {
                        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                        int sample = 1;
                        while (Math.max(o.outWidth, o.outHeight) / sample > 900) sample *= 2;
                        o.inJustDecodeBounds = false;
                        o.inSampleSize = sample;
                        android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                        if (bm != null) {
                            android.widget.ImageView iv = new android.widget.ImageView(host);
                            iv.setImageBitmap(bm);
                            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(220));
                            ilp.topMargin = host.dp(10);
                            card.addView(iv, ilp);
                        }
                    }
                } catch (Throwable ignored) {
                }

                TextView fn = new TextView(host);
                fn.setText("已用：" + new java.io.File(mine.path).getName());
                fn.setTextSize(13);
                fn.setTextColor(MainActivity.MUTED);
                fn.setGravity(Gravity.CENTER);
                fn.setPadding(0, host.dp(6), 0, 0);
                card.addView(fn);
            }
        }

        TextView num = new TextView(host);
        num.setText(done ? "这一项已经拍好了" : "现在拍这一项");
        num.setTextSize(15);
        num.setTextColor(done ? MainActivity.OK : MainActivity.MUTED);
        num.setGravity(Gravity.CENTER);
        card.addView(num);

        TextView t = new TextView(host);
        t.setText(title);
        t.setTextSize(32);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(MainActivity.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, host.dp(10), 0, host.dp(8));
        card.addView(t);

        TextView h = new TextView(host);
        h.setText(hint);
        h.setTextSize(18);
        h.setTextColor(MainActivity.MUTED);
        h.setGravity(Gravity.CENTER);
        h.setLineSpacing(host.dp(5), 1f);
        card.addView(h);

        boolean galleryOnly = "panophoto".equals(key);
        Button b = new Button(host);
        b.setAllCaps(false);
        b.setTextSize(23);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        TextView tip = new TextView(host);
        tip.setText("不会拍？点这里看要点");
        tip.setTextSize(13);
        tip.setTextColor(Color.parseColor("#1B63D6"));
        tip.setPadding(0, host.dp(8), 0, host.dp(4));
        b.setText(done ? "重新拍这一项" : (kind.equals("video") ? "开始录像" : "拍照"));
        b.setBackground(host.roundRect(done ? MainActivity.OK : MainActivity.PRIMARY, 16));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (BuildConfigVersion.NO_CAPTURE) {
            b.setVisibility(View.GONE);
        } else {
            b.setOnClickListener(v -> CaptureFlow.captureShot(host, key, kind));
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(76));
        blp.topMargin = host.dp(22);
        card.addView(b, blp);

        TextView next = new TextView(host);
        next.setText(galleryOnly ? "全景照片请用手机全景模式拍，再从相册选"
                : (done ? "拍完会自动跳到下一项" : "拍好之后会自动跳到下一项"));
        next.setTextSize(13);
        next.setTextColor(MainActivity.MUTED);
        next.setGravity(Gravity.CENTER);
        next.setPadding(0, host.dp(12), 0, 0);
        card.addView(next);

        String pickText = BuildConfigVersion.NO_CAPTURE
                ? (kind.equals("video") ? "从相册选水印视频" : "从相册选水印照片")
                : (kind.equals("video") ? "从相册选视频" : "从相册选照片");
        if (BuildConfigVersion.NO_CAPTURE && !galleryOnly) {
            Button openWm = host.secondaryButton("① 打开水印相机去拍", 60);
            openWm.setOnClickListener(v -> CaptureFlow.openWatermarkCamera(host, key, kind));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, host.dp(60));
            wlp.topMargin = host.dp(10);
            card.addView(openWm, wlp);

            TextView tip2 = new TextView(host);
            tip2.setText("拍完按返回，会自动让你从相册里选刚拍的那张");
            tip2.setTextSize(13);
            tip2.setTextColor(MainActivity.MUTED);
            tip2.setGravity(Gravity.CENTER);
            tip2.setPadding(0, host.dp(8), 0, host.dp(2));
            card.addView(tip2);
        }

        Button pick = host.secondaryButton(pickText, BuildConfigVersion.NO_CAPTURE ? 64 : 54);
        pick.setOnClickListener(v -> CaptureFlow.pickShot(host, key, kind));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(54));
        plp.topMargin = host.dp(10);
        card.addView(pick, plp);

        int n = Shots.doneCount(host, host.currentTask);
        if (n > 0) {
            LinearLayout list = new LinearLayout(host);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, host.dp(14), 0, 0);
            host.shotArea.addView(list);
            for (int i = 0; i < shots.length; i++) {
                if (!Shots.hasShot(host, host.currentTask, i)) continue;
                final int d = i;
                TextView row = new TextView(host);
                row.setText("✓ " + shots[d][1] + "（点这里重拍）");
                row.setTextSize(15);
                row.setTextColor(MainActivity.MUTED);
                row.setPadding(host.dp(6), host.dp(10), host.dp(6), host.dp(10));
                row.setOnClickListener(v -> { host.currentShot = d; ShotRenderer.renderShot(host); });
                list.addView(row);
            }
        }
    }
}
