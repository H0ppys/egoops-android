package com.egoops.collect.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;

/** 第 1 屏「采集点信息」页面构建（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class StartPage {

    private StartPage() {
    }

    public static LinearLayout buildStartPage(MainActivity host) {
        LinearLayout p = host.pageShell();

        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(host.dp(24), host.dp(20), host.dp(24), host.dp(16));

        TextView title = new TextView(host);
        title.setText(BuildConfigVersion.NO_CAPTURE ? "不拍摄收集" : "采集点拍照");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(MainActivity.TEXT);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView sub = new TextView(host);
        sub.setText("任务1：门头照 + 全景照片 + 全景视频\n后续区域：对角2 + 任务照2 + 任务视频2（6 项）");
        sub.setTextSize(16);
        sub.setTextColor(MainActivity.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(host.dp(6), 1f);
        sub.setPadding(0, host.dp(8), 0, 0);
        content.addView(sub);

        // 邀请码状态行（点一下可改）
        host.tvVendor = new TextView(host);
        host.tvVendor.setTextSize(14);
        host.tvVendor.setTextColor(MainActivity.MUTED);
        host.tvVendor.setPadding(host.dp(6), host.dp(14), host.dp(6), host.dp(2));
        host.tvVendor.setOnClickListener(v -> AccountManager.askInvite(host));
        content.addView(host.tvVendor);
        AccountManager.refreshVendorLabel(host);

        TextView lInv = new TextView(host);
        lInv.setText("邀请码（必填，管理员事先约定，大小写都可以）");
        lInv.setTextSize(15);
        lInv.setTextColor(MainActivity.MUTED);
        lInv.setPadding(host.dp(4), host.dp(2), 0, host.dp(6));
        content.addView(lInv);

        host.etInvite = new EditText(host);
        host.etInvite.setText(host.store.invite());
        host.etInvite.setHint("例如 ZJMEIYI");
        host.etInvite.setTextSize(20);
        host.etInvite.setSingleLine(true);
        host.etInvite.setInputType(InputType.TYPE_CLASS_TEXT);
        host.etInvite.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.etInvite.setPadding(host.dp(16), host.dp(10), host.dp(16), host.dp(10));
        content.addView(host.etInvite, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(56)));

        // 邀请码一变（停顿 0.8 秒）就自动按这个码拉分类
        host.etInvite.addTextChangedListener(new android.text.TextWatcher() {
            private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            private Runnable pending;

            private void schedule() {
                if (pending != null) h.removeCallbacks(pending);
                pending = () -> SceneCatalog.autoLoadScenes(host);
                h.postDelayed(pending, 800);
            }

            @Override public void beforeTextChanged(CharSequence s2, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s2, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e2) { schedule(); }
        });



        TextView l1 = new TextView(host);
        l1.setText("采集员姓名（必填）");
        l1.setTextSize(16);
        l1.setTextColor(MainActivity.MUTED);
        l1.setPadding(host.dp(4), host.dp(18), 0, host.dp(8));
        content.addView(l1);

        host.etCollector = new EditText(host);
        host.etCollector.setHint("你的名字");
        host.etCollector.setText(host.store.name());
        host.etCollector.setTextSize(21);
        host.etCollector.setTextColor(MainActivity.TEXT);
        host.etCollector.setHintTextColor(Color.parseColor("#A8B4C6"));
        host.etCollector.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.etCollector.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12));
        content.addView(host.etCollector, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(60)));

        TextView l2 = new TextView(host);
        l2.setText("门店名称（必填）");
        l2.setTextSize(16);
        l2.setTextColor(MainActivity.MUTED);
        l2.setPadding(host.dp(4), host.dp(18), 0, host.dp(8));
        content.addView(l2);

        host.etStoreName = new EditText(host);
        host.etStoreName.setHint("例如：XX 超市");
        host.etStoreName.setTextSize(21);
        host.etStoreName.setTextColor(MainActivity.TEXT);
        host.etStoreName.setHintTextColor(Color.parseColor("#A8B4C6"));
        host.etStoreName.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.etStoreName.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12));
        host.etStoreName.setText(host.site.name);
        content.addView(host.etStoreName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(60)));

        // 现场地址：省 + 市 一行，区 + 街道 一行
        TextView lAddr = new TextView(host);
        lAddr.setText("现场地址（必填：先选省市区，再填街道门牌号）");
        lAddr.setTextSize(16);
        lAddr.setTextColor(MainActivity.MUTED);
        lAddr.setPadding(host.dp(4), host.dp(18), 0, host.dp(8));
        content.addView(lAddr);

        LinearLayout addrRow1 = new LinearLayout(host);
        addrRow1.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(addrRow1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(56)));

        host.spProv = new android.widget.Spinner(host);
        host.spProv.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.spProv.setAdapter(new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"省份"}));
        addrRow1.addView(host.spProv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        host.spCity = new android.widget.Spinner(host);
        host.spCity.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.spCity.setAdapter(new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"城市"}));
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp2.leftMargin = host.dp(8);
        addrRow1.addView(host.spCity, clp2);

        LinearLayout addrRow2 = new LinearLayout(host);
        addrRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(56));
        r2lp.topMargin = host.dp(8);
        content.addView(addrRow2, r2lp);

        host.spDist = new android.widget.Spinner(host);
        host.spDist.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.spDist.setAdapter(new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"区 / 县"}));
        addrRow2.addView(host.spDist, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f));

        host.etStreet = new EditText(host);
        host.etStreet.setHint("街道、门牌号");
        host.etStreet.setTextSize(18);
        host.etStreet.setTextColor(MainActivity.TEXT);
        host.etStreet.setHintTextColor(Color.parseColor("#A8B4C6"));
        host.etStreet.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.etStreet.setPadding(host.dp(14), host.dp(10), host.dp(14), host.dp(10));
        LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.38f);
        slp2.leftMargin = host.dp(8);
        addrRow2.addView(host.etStreet, slp2);

        host.spProv.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                RegionCatalog.updateCities(host, pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        host.spCity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                RegionCatalog.updateDistricts(host, pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        TextView l3 = new TextView(host);
        l3.setText("采集场景分类（必选）");
        l3.setTextSize(16);
        l3.setTextColor(MainActivity.MUTED);
        l3.setPadding(host.dp(4), host.dp(18), 0, host.dp(8));
        content.addView(l3);

        host.spCat = new android.widget.Spinner(host);
        host.spCat.setBackground(host.roundRect(MainActivity.CARD, 12));
        List<String> catLabels = new ArrayList<String>();
        catLabels.add("全部分类");
        host.spCat.setAdapter(new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_dropdown_item, catLabels));
        content.addView(host.spCat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(56)));

        host.spScene2 = new android.widget.Spinner(host);
        host.spScene2.setBackground(host.roundRect(MainActivity.CARD, 12));
        List<String> sceneHint = new ArrayList<String>();
        sceneHint.add("请选择具体场景");
        host.spScene2.setAdapter(new android.widget.ArrayAdapter<String>(host,
                android.R.layout.simple_spinner_dropdown_item, sceneHint));
        LinearLayout.LayoutParams s2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(56));
        s2lp.topMargin = host.dp(8);
        content.addView(host.spScene2, s2lp);

        TextView l4 = new TextView(host);
        l4.setText("场地面积 ㎡（必填）");
        l4.setTextSize(16);
        l4.setTextColor(MainActivity.MUTED);
        l4.setPadding(host.dp(4), host.dp(18), 0, host.dp(8));
        content.addView(l4);

        host.etArea = new EditText(host);
        host.etArea.setHint("例如：85");
        host.etArea.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        host.etArea.setTextSize(21);
        host.etArea.setTextColor(MainActivity.TEXT);
        host.etArea.setHintTextColor(Color.parseColor("#A8B4C6"));
        host.etArea.setBackground(host.roundRect(MainActivity.CARD, 12));
        host.etArea.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12));
        if (host.site.area > 0) host.etArea.setText(String.valueOf((int) host.site.area));
        content.addView(host.etArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(60)));

        p.addView(host.scrollWrap(content), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = host.bottomBar();

        Button resume = host.secondaryButton("继续上次的采集点（补拍/续传）", 52);
        resume.setOnClickListener(v -> SiteFlow.resumeLastSite(host));
        bottom.addView(resume);

        Button start = host.primaryButton("开始拍摄", 72, MainActivity.PRIMARY);
        start.setOnClickListener(v -> SiteFlow.beginShooting(host));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, host.dp(72));
        slp.topMargin = host.dp(10);
        bottom.addView(start, slp);

        TextView version = new TextView(host);
        version.setText("当前版本 " + BuildConfigVersion.NAME + "（" + BuildConfigVersion.CODE + "）");
        version.setTextSize(12);
        version.setTextColor(MainActivity.MUTED);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, host.dp(8), 0, 0);
        bottom.addView(version);

        TextView settings = new TextView(host);
        settings.setText("设置服务器");
        settings.setTextSize(13);
        settings.setTextColor(MainActivity.MUTED);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(0, host.dp(10), 0, 0);
        settings.setOnClickListener(v -> SettingsDialog.showSettings(host));
        bottom.addView(settings);

        p.addView(bottom);
        return p;
    }
}
