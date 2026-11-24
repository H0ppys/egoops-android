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

/** 任务完成屏构建（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class TaskDonePage {

    private TaskDonePage() {
    }

    public static LinearLayout buildTaskDonePage(MainActivity host) {
        LinearLayout p = host.pageShell();

        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(host.dp(24), host.dp(48), host.dp(24), host.dp(16));

        TextView tick = new TextView(host);
        tick.setText("✓");
        tick.setTextSize(76);
        tick.setTextColor(MainActivity.OK);
        tick.setGravity(Gravity.CENTER);
        content.addView(tick);

        host.tvTaskDoneTitle = new TextView(host);
        host.tvTaskDoneTitle.setText("任务拍完了");
        host.tvTaskDoneTitle.setTextSize(28);
        host.tvTaskDoneTitle.setTypeface(Typeface.DEFAULT_BOLD);
        host.tvTaskDoneTitle.setTextColor(MainActivity.TEXT);
        host.tvTaskDoneTitle.setGravity(Gravity.CENTER);
        host.tvTaskDoneTitle.setPadding(0, host.dp(6), 0, host.dp(8));
        content.addView(host.tvTaskDoneTitle);

        host.tvTaskDoneSub = new TextView(host);
        host.tvTaskDoneSub.setText("这家店还有别的任务要拍吗？");
        host.tvTaskDoneSub.setTextSize(17);
        host.tvTaskDoneSub.setTextColor(MainActivity.MUTED);
        host.tvTaskDoneSub.setGravity(Gravity.CENTER);
        content.addView(host.tvTaskDoneSub);

        p.addView(host.scrollWrap(content), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = host.bottomBar();
        Button sameArea = host.secondaryButton("同一区域，再拍一个任务", 56);
        sameArea.setOnClickListener(v -> {
            String area = "";
            if (host.currentTask >= 0 && host.currentTask < host.site.tasks.size()) {
                String a = host.site.tasks.get(host.currentTask).area;
                area = a == null ? "" : a;
            }
            Dialogs.askTask(host, area);
        });
        bottom.addView(sameArea);

        Button more = host.primaryButton("换一个区域拍", 68, MainActivity.PRIMARY);
        more.setOnClickListener(v -> Dialogs.askTask(host, ""));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(68));
        mlp.topMargin = host.dp(10);
        bottom.addView(more, mlp);

        Button finish = host.secondaryButton("没有了，全部拍完", 56);
        finish.setOnClickListener(v -> SiteFlow.finishAll(host));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(56));
        lp.topMargin = host.dp(10);
        bottom.addView(finish, lp);

        p.addView(bottom);
        return p;
    }
}
