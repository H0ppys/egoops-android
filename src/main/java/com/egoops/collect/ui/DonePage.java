package com.egoops.collect.ui;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;

/** 结束屏构建（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class DonePage {

    private DonePage() {
    }

    public static LinearLayout buildDonePage(MainActivity host) {
        LinearLayout p = host.pageShell();

        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(host.dp(24), host.dp(48), host.dp(24), host.dp(16));

        TextView tick = new TextView(host);
        tick.setText("✓");
        tick.setTextSize(80);
        tick.setTextColor(MainActivity.OK);
        tick.setGravity(Gravity.CENTER);
        content.addView(tick);

        TextView title = new TextView(host);
        title.setText("拍摄完成");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(MainActivity.TEXT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, host.dp(8), 0, host.dp(10));
        content.addView(title);

        host.tvDoneStatus = new TextView(host);
        host.tvDoneStatus.setText("正在自动上传，请稍等…");
        host.tvDoneStatus.setTextSize(17);
        host.tvDoneStatus.setTextColor(MainActivity.MUTED);
        host.tvDoneStatus.setGravity(Gravity.CENTER);
        content.addView(host.tvDoneStatus);

        p.addView(host.scrollWrap(content), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = host.bottomBar();
        Button again = host.primaryButton("再拍一个采集点", 68, MainActivity.PRIMARY);
        again.setOnClickListener(v -> SiteFlow.resetForNext(host));
        bottom.addView(again);
        p.addView(bottom);

        return p;
    }
}
