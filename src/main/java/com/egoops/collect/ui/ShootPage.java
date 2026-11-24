package com.egoops.collect.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;

/** 第 2 屏「拍摄」页面骨架构建（手势翻项 + 上传状态条）（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class ShootPage {

    private ShootPage() {
    }

    public static LinearLayout buildShootPage(MainActivity host) {
        LinearLayout p = host.pageShell();

        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(host.dp(20), host.dp(16), host.dp(20), host.dp(2));
        p.addView(head);

        host.tvStep = new TextView(host);
        host.tvStep.setTextSize(21);
        host.tvStep.setTypeface(Typeface.DEFAULT_BOLD);
        host.tvStep.setTextColor(MainActivity.TEXT);
        head.addView(host.tvStep, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView prev = new TextView(host);
        prev.setText("← 上一项");
        prev.setTextSize(14);
        prev.setTextColor(Color.parseColor("#1B63D6"));
        prev.setPadding(host.dp(8), host.dp(8), host.dp(10), host.dp(8));
        prev.setOnClickListener(v -> SiteFlow.stepShot(host, -1));
        head.addView(prev);

        TextView back = new TextView(host);
        back.setText("重新开始");
        back.setTextSize(13);
        back.setTextColor(MainActivity.MUTED);
        back.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8));
        back.setOnClickListener(v -> Dialogs.confirmRestart(host));
        head.addView(back);

        host.tvDots = new TextView(host);
        host.tvDots.setTextSize(18);
        host.tvDots.setTextColor(MainActivity.PRIMARY);
        host.tvDots.setGravity(Gravity.CENTER);
        host.tvDots.setPadding(0, host.dp(2), 0, host.dp(6));
        p.addView(host.tvDots);

        LinearLayout area = new LinearLayout(host);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(host.dp(20), 0, host.dp(20), host.dp(8));
        host.shotArea = area;
        android.widget.ScrollView sv = host.scrollWrap(area);
        final android.view.GestureDetector gd = new android.view.GestureDetector(host,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2,
                                           float vx, float vy) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) < host.dp(60) || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                        SiteFlow.stepShot(host, dx > 0 ? -1 : 1);
                        return true;
                    }
                });
        sv.setOnTouchListener((v, ev) -> {
            gd.onTouchEvent(ev);
            return false;
        });
        p.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = host.bottomBar();
        host.tvUpload = new TextView(host);
        host.tvUpload.setTextSize(13);
        host.tvUpload.setTextColor(MainActivity.MUTED);
        host.tvUpload.setGravity(Gravity.CENTER);
        host.tvUpload.setPadding(0, 0, 0, host.dp(8));
        host.tvUpload.setOnClickListener(v -> UploadCoordinator.startUpload(host));
        bottom.addView(host.tvUpload);
        p.addView(bottom);

        return p;
    }
}
