package com.egoops.collect;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集点拍照 v9
 * 结构对齐「申请采集点」：采集点 → 任务（可多个）→ 每个任务 4 项拍摄。
 * 每屏：内容可滚动 + 主按钮固定在底部。
 */
public class MainActivity extends Activity {

    private static final int REQ_PHOTO = 1001;
    private static final int REQ_PERM_ALL = 1100;
    private static final int REQ_VIDEO = 1002;
    private static final int REQ_PICK_PHOTO = 1003;
    private static final int REQ_PICK_VIDEO = 1004;
    private static final int REQ_PICK_MULTI = 1006;
    private static final int REQ_PERM = 1005;

    private static final int BG = Color.parseColor("#F2F5FA");
    private static final int CARD = Color.WHITE;
    private static final int PRIMARY = Color.parseColor("#2F6FED");
    private static final int OK = Color.parseColor("#1F9D55");
    private static final int TEXT = Color.parseColor("#17202E");
    private static final int MUTED = Color.parseColor("#6B7A90");
    private static final int DANGER = Color.parseColor("#C0392B");

    /** 任务1（采集点级）：门头照 + 全景照片 + 全景视频 */
    private static final String[][] SHOTS_SITE = {
            {"door", "门头照", "正对门头，把招牌和门都拍进去（用广角）", "photo"},
            {"panophoto", "全景照片", "站在店中央，用广角把整个空间拍全", "photo"},
            {"panovideo", "全景视频", "原地慢慢转一圈，用广角（15 秒左右）", "video"},
    };

    /** 任务1：门头 3 项 + 第一个区域 6 项，合起来 9 项 */
    private static final String[][] SHOTS_TASK1 = {
            {"door", "门头照", "正对门头，把招牌和门都拍进去（用广角）", "photo"},
            {"panophoto", "全景照片", "站在店中央，用广角把整个空间拍全", "photo"},
            {"panovideo", "全景视频", "原地慢慢转一圈，用广角（15 秒左右）", "video"},
            {"corner1", "对角照片 1", "站在区域一个角，用广角斜着拍对角", "photo"},
            {"corner2", "对角照片 2", "走到对角，反方向再拍一张", "photo"},
            {"work1", "任务照片 1", "拍这个区域要采集的内容", "photo"},
            {"work2", "任务照片 2", "换个角度再拍一张", "photo"},
            {"clip1", "任务视频 1", "对准要采集的动作，用广角录 10 秒", "video"},
            {"clip2", "任务视频 2", "换个角度再录 10 秒", "video"},
    };

    /** 后续区域：2 张对角照 + 2 张任务照 + 2 个任务视频（门头/全景只在任务1 拍） */
    private static final String[][] SHOTS_AREA = {
            {"corner1", "对角照片 1", "站在区域一个角，用广角斜着拍对角", "photo"},
            {"corner2", "对角照片 2", "走到对角，反方向再拍一张", "photo"},
            {"work1", "任务照片 1", "拍这个区域要采集的内容", "photo"},
            {"work2", "任务照片 2", "换个角度再拍一张", "photo"},
            {"clip1", "任务视频 1", "对准要采集的动作，用广角录 10 秒", "video"},
            {"clip2", "任务视频 2", "换个角度再录 10 秒", "video"},
    };

    private static String[][] shotsOf(Model.Task t) {
        if (t == null) return SHOTS_AREA;
        if ("task1".equals(t.type)) return SHOTS_TASK1;
        if ("site".equals(t.type)) return SHOTS_SITE;
        return SHOTS_AREA;
    }

    private Store store;
    private Api api;
    private Model.Site site = new Model.Site();

    private LinearLayout pageStart, pageShoot, pageTaskDone, pageDone;
    private EditText etStoreName, etCollector, etArea, etStreet;
    private android.widget.Spinner spProv, spCity, spDist;
    private String[] provArr = new String[0];
    private String[][] cityArr = new String[0][];
    private String[][][] distArr = new String[0][][];
    private android.widget.Spinner spCat, spScene2;
    private EditText etSceneSearch;
    private final List<String[]> sceneItems = new ArrayList<String[]>();   // {path, label, category}
    private final List<String> categories = new ArrayList<String>();
    private final List<Integer> shownIndex = new ArrayList<Integer>();
    private LinearLayout shotArea;
    private TextView tvStep, tvDots, tvUpload, tvDoneStatus, tvTaskDoneTitle, tvTaskDoneSub, tvVendor;
    private EditText etInvite;
    private String lastSceneInvite = "";
    private volatile String gpsAddress = "";
    private volatile String gpsCoords = "";
    private volatile java.io.File mapSnapFile = null;
    private boolean awaitingWatermarkImport = false;
    private int slideDir = 1;   // 1=下一项 -1=上一项
    private long wmLaunchAt = 0;

    /** 每一项怎么拍（中老年用户看得懂的短句） */
    private static final String[][] GUIDE = {
        {"门头", "站在马路对面或门口正前方，把整块招牌拍进去，不要只拍一角；避免逆光和路人正脸。"},
        {"全景", "站在门店正中央，手机横着拿稳，原地慢慢转一整圈，货架和通道都要扫到。"},
        {"对角", "走到店铺的一个角，对着斜对面的角拍，让整个空间的对角线出现在画面里。"},
        {"任务照", "先拍你要动那个东西的全貌，再拍细节（手和物体都要清楚），别贴太近。"},
        {"任务视频", "从头到尾一遍做完，动作慢一点、稳一点，手和物体始终在画面里，不要中途停下。"},
        {"全景照片", "站在区域正中央，手机切到广角（0.5x），举到胸口高度，把四个角都框进画面，再按快门。"},
    };
    private boolean uploading = false;
    private int currentTask = 0;
    private int currentShot = 0;
    private String pendingShot = "";
    private Uri tempUri;

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        api = new Api(store.server());
        api.token = store.token();

        installCrashReporter();

        // 上次的采集点：只有没拍完或没传完才恢复，拍完传完的就当新的开始
        Model.Site saved = store.loadQueue();
        if (saved != null && !saved.tasks.isEmpty()) {
            int pending = 0, total = 0;
            for (Model.Task t : saved.tasks) {
                for (Model.Media m : t.media) {
                    total++;
                    if (!"uploaded".equals(m.state)) pending++;
                }
            }
            boolean finished = total > 0 && pending == 0;
            if (finished) {
                store.clearQueue();          // 已经传完：清掉，下次是干净的开始
                site = new Model.Site();
            } else {
                site = saved;
            }
        }

        setContentView(buildUi());
        showPage(1);
        autoLogin();
        showLastCrash();
        resumePendingUpload();
        askAllPermissions();
        if (!BuildConfigVersion.NO_CAPTURE) {
            tvUpload.postDelayed(this::checkUpdate, 3000);
        }
        loadScenes();          // 公开接口，不需要登录就能拉
        loadRegions();         // 省市区数据（本地资源）
    }

    /** 采集场景分类（两级：大类 + 场景，带搜索） */
    // ---------------------------------------------------------------- 界面

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        pageStart = buildStartPage();
        pageShoot = buildShootPage();
        pageTaskDone = buildTaskDonePage();
        pageDone = buildDonePage();

        root.addView(pageStart, full());
        root.addView(pageShoot, full());
        root.addView(pageTaskDone, full());
        root.addView(pageDone, full());
        return root;
    }

    private FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int curPage = 1;

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View v = getCurrentFocus();
            if (imm != null && v != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                v.clearFocus();
            }
        } catch (Exception ignored) { }
    }

    private void showPage(int n) {
        curPage = n;
        if (n != 1) hideKeyboard();
        pageStart.setVisibility(n == 1 ? View.VISIBLE : View.GONE);
        pageShoot.setVisibility(n == 2 ? View.VISIBLE : View.GONE);
        pageTaskDone.setVisibility(n == 3 ? View.VISIBLE : View.GONE);
        pageDone.setVisibility(n == 4 ? View.VISIBLE : View.GONE);
    }

    private LinearLayout pageShell() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(BG);
        return p;
    }

    private ScrollView scrollWrap(LinearLayout content) {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    private LinearLayout bottomBar() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackgroundColor(BG);
        b.setPadding(dp(20), dp(10), dp(20), dp(20));
        return b;
    }

    private Button primaryButton(String text, int heightDp, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(23);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(roundRect(color, 16));
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return btn;
    }

    private Button secondaryButton(String text, int heightDp) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(19);
        btn.setTextColor(PRIMARY);
        btn.setBackground(roundRect(Color.parseColor("#E8EFFC"), 14));
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return btn;
    }

    // ---------------------------------------------------------------- 第 1 屏

    private LinearLayout buildStartPage() {
        LinearLayout p = pageShell();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(16));

        TextView title = new TextView(this);
        title.setText(BuildConfigVersion.NO_CAPTURE ? "不拍摄收集" : "采集点拍照");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView sub = new TextView(this);
        sub.setText("任务1：门头照 + 全景照片 + 全景视频\n后续区域：对角2 + 任务照2 + 任务视频2（6 项）");
        sub.setTextSize(16);
        sub.setTextColor(MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(dp(6), 1f);
        sub.setPadding(0, dp(8), 0, 0);
        content.addView(sub);

        // 邀请码状态行（点一下可改）
        tvVendor = new TextView(this);
        tvVendor.setTextSize(14);
        tvVendor.setTextColor(MUTED);
        tvVendor.setPadding(dp(6), dp(14), dp(6), dp(2));
        tvVendor.setOnClickListener(v -> askInvite());
        content.addView(tvVendor);
        refreshVendorLabel();

        TextView lInv = new TextView(this);
        lInv.setText("邀请码（必填，管理员事先约定，大小写都可以）");
        lInv.setTextSize(15);
        lInv.setTextColor(MUTED);
        lInv.setPadding(dp(4), dp(2), 0, dp(6));
        content.addView(lInv);

        etInvite = new EditText(this);
        etInvite.setText(store.invite());
        etInvite.setHint("例如 ZJMEIYI");
        etInvite.setTextSize(20);
        etInvite.setSingleLine(true);
        etInvite.setInputType(InputType.TYPE_CLASS_TEXT);
        etInvite.setBackground(roundRect(CARD, 12));
        etInvite.setPadding(dp(16), dp(10), dp(16), dp(10));
        content.addView(etInvite, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        // 邀请码一变（停顿 0.8 秒）就自动按这个码拉分类
        etInvite.addTextChangedListener(new android.text.TextWatcher() {
            private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            private Runnable pending;

            private void schedule() {
                if (pending != null) h.removeCallbacks(pending);
                pending = () -> autoLoadScenes();
                h.postDelayed(pending, 800);
            }

            @Override public void beforeTextChanged(CharSequence s2, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s2, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e2) { schedule(); }
        });



        TextView l1 = new TextView(this);
        l1.setText("采集员姓名（必填）");
        l1.setTextSize(16);
        l1.setTextColor(MUTED);
        l1.setPadding(dp(4), dp(18), 0, dp(8));
        content.addView(l1);

        etCollector = new EditText(this);
        etCollector.setHint("你的名字");
        etCollector.setText(store.name());
        etCollector.setTextSize(21);
        etCollector.setTextColor(TEXT);
        etCollector.setHintTextColor(Color.parseColor("#A8B4C6"));
        etCollector.setBackground(roundRect(CARD, 12));
        etCollector.setPadding(dp(16), dp(12), dp(16), dp(12));
        content.addView(etCollector, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        TextView l2 = new TextView(this);
        l2.setText("门店名称（必填）");
        l2.setTextSize(16);
        l2.setTextColor(MUTED);
        l2.setPadding(dp(4), dp(18), 0, dp(8));
        content.addView(l2);

        etStoreName = new EditText(this);
        etStoreName.setHint("例如：XX 超市");
        etStoreName.setTextSize(21);
        etStoreName.setTextColor(TEXT);
        etStoreName.setHintTextColor(Color.parseColor("#A8B4C6"));
        etStoreName.setBackground(roundRect(CARD, 12));
        etStoreName.setPadding(dp(16), dp(12), dp(16), dp(12));
        etStoreName.setText(site.name);
        content.addView(etStoreName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        // 现场地址：省 + 市 一行，区 + 街道 一行
        TextView lAddr = new TextView(this);
        lAddr.setText("现场地址（必填：先选省市区，再填街道门牌号）");
        lAddr.setTextSize(16);
        lAddr.setTextColor(MUTED);
        lAddr.setPadding(dp(4), dp(18), 0, dp(8));
        content.addView(lAddr);

        LinearLayout addrRow1 = new LinearLayout(this);
        addrRow1.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(addrRow1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        spProv = new android.widget.Spinner(this);
        spProv.setBackground(roundRect(CARD, 12));
        spProv.setAdapter(new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"省份"}));
        addrRow1.addView(spProv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        spCity = new android.widget.Spinner(this);
        spCity.setBackground(roundRect(CARD, 12));
        spCity.setAdapter(new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"城市"}));
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp2.leftMargin = dp(8);
        addrRow1.addView(spCity, clp2);

        LinearLayout addrRow2 = new LinearLayout(this);
        addrRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        r2lp.topMargin = dp(8);
        content.addView(addrRow2, r2lp);

        spDist = new android.widget.Spinner(this);
        spDist.setBackground(roundRect(CARD, 12));
        spDist.setAdapter(new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"区 / 县"}));
        addrRow2.addView(spDist, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f));

        etStreet = new EditText(this);
        etStreet.setHint("街道、门牌号");
        etStreet.setTextSize(18);
        etStreet.setTextColor(TEXT);
        etStreet.setHintTextColor(Color.parseColor("#A8B4C6"));
        etStreet.setBackground(roundRect(CARD, 12));
        etStreet.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.38f);
        slp2.leftMargin = dp(8);
        addrRow2.addView(etStreet, slp2);

        spProv.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                updateCities(pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        spCity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                updateDistricts(pos);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });

        TextView l3 = new TextView(this);
        l3.setText("采集场景分类（必选）");
        l3.setTextSize(16);
        l3.setTextColor(MUTED);
        l3.setPadding(dp(4), dp(18), 0, dp(8));
        content.addView(l3);

        spCat = new android.widget.Spinner(this);
        spCat.setBackground(roundRect(CARD, 12));
        List<String> catLabels = new ArrayList<String>();
        catLabels.add("全部分类");
        spCat.setAdapter(new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, catLabels));
        content.addView(spCat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        spScene2 = new android.widget.Spinner(this);
        spScene2.setBackground(roundRect(CARD, 12));
        List<String> sceneHint = new ArrayList<String>();
        sceneHint.add("请选择具体场景");
        spScene2.setAdapter(new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, sceneHint));
        LinearLayout.LayoutParams s2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        s2lp.topMargin = dp(8);
        content.addView(spScene2, s2lp);

        TextView l4 = new TextView(this);
        l4.setText("场地面积 ㎡（必填）");
        l4.setTextSize(16);
        l4.setTextColor(MUTED);
        l4.setPadding(dp(4), dp(18), 0, dp(8));
        content.addView(l4);

        etArea = new EditText(this);
        etArea.setHint("例如：85");
        etArea.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etArea.setTextSize(21);
        etArea.setTextColor(TEXT);
        etArea.setHintTextColor(Color.parseColor("#A8B4C6"));
        etArea.setBackground(roundRect(CARD, 12));
        etArea.setPadding(dp(16), dp(12), dp(16), dp(12));
        if (site.area > 0) etArea.setText(String.valueOf((int) site.area));
        content.addView(etArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        p.addView(scrollWrap(content), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = bottomBar();

        Button resume = secondaryButton("继续上次的采集点（补拍/续传）", 52);
        resume.setOnClickListener(v -> resumeLastSite());
        bottom.addView(resume);

        Button start = primaryButton("开始拍摄", 72, PRIMARY);
        start.setOnClickListener(v -> beginShooting());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        slp.topMargin = dp(10);
        bottom.addView(start, slp);

        TextView version = new TextView(this);
        version.setText("当前版本 " + BuildConfigVersion.NAME + "（" + BuildConfigVersion.CODE + "）");
        version.setTextSize(12);
        version.setTextColor(MUTED);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(8), 0, 0);
        bottom.addView(version);

        TextView settings = new TextView(this);
        settings.setText("设置服务器");
        settings.setTextSize(13);
        settings.setTextColor(MUTED);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(0, dp(10), 0, 0);
        settings.setOnClickListener(v -> showSettings());
        bottom.addView(settings);

        p.addView(bottom);
        return p;
    }

    // ---------------------------------------------------------------- 第 2 屏

    private LinearLayout buildShootPage() {
        LinearLayout p = pageShell();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(16), dp(20), dp(2));
        p.addView(head);

        tvStep = new TextView(this);
        tvStep.setTextSize(21);
        tvStep.setTypeface(Typeface.DEFAULT_BOLD);
        tvStep.setTextColor(TEXT);
        head.addView(tvStep, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView prev = new TextView(this);
        prev.setText("← 上一项");
        prev.setTextSize(14);
        prev.setTextColor(Color.parseColor("#1B63D6"));
        prev.setPadding(dp(8), dp(8), dp(10), dp(8));
        prev.setOnClickListener(v -> stepShot(-1));
        head.addView(prev);

        TextView back = new TextView(this);
        back.setText("重新开始");
        back.setTextSize(13);
        back.setTextColor(MUTED);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> confirmRestart());
        head.addView(back);

        tvDots = new TextView(this);
        tvDots.setTextSize(18);
        tvDots.setTextColor(PRIMARY);
        tvDots.setGravity(Gravity.CENTER);
        tvDots.setPadding(0, dp(2), 0, dp(6));
        p.addView(tvDots);

        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(20), 0, dp(20), dp(8));
        shotArea = area;
        android.widget.ScrollView sv = scrollWrap(area);
        final android.view.GestureDetector gd = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2,
                                           float vx, float vy) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) < dp(60) || Math.abs(dx) < Math.abs(dy) * 1.5f) return false;
                        stepShot(dx > 0 ? -1 : 1);
                        return true;
                    }
                });
        sv.setOnTouchListener((v, ev) -> {
            gd.onTouchEvent(ev);
            return false;
        });
        p.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = bottomBar();
        tvUpload = new TextView(this);
        tvUpload.setTextSize(13);
        tvUpload.setTextColor(MUTED);
        tvUpload.setGravity(Gravity.CENTER);
        tvUpload.setPadding(0, 0, 0, dp(8));
        tvUpload.setOnClickListener(v -> startUpload());
        bottom.addView(tvUpload);
        p.addView(bottom);

        return p;
    }

    // ---------------------------------------------------------------- 任务完成屏

    private LinearLayout buildTaskDonePage() {
        LinearLayout p = pageShell();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(48), dp(24), dp(16));

        TextView tick = new TextView(this);
        tick.setText("✓");
        tick.setTextSize(76);
        tick.setTextColor(OK);
        tick.setGravity(Gravity.CENTER);
        content.addView(tick);

        tvTaskDoneTitle = new TextView(this);
        tvTaskDoneTitle.setText("任务拍完了");
        tvTaskDoneTitle.setTextSize(28);
        tvTaskDoneTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTaskDoneTitle.setTextColor(TEXT);
        tvTaskDoneTitle.setGravity(Gravity.CENTER);
        tvTaskDoneTitle.setPadding(0, dp(6), 0, dp(8));
        content.addView(tvTaskDoneTitle);

        tvTaskDoneSub = new TextView(this);
        tvTaskDoneSub.setText("这家店还有别的任务要拍吗？");
        tvTaskDoneSub.setTextSize(17);
        tvTaskDoneSub.setTextColor(MUTED);
        tvTaskDoneSub.setGravity(Gravity.CENTER);
        content.addView(tvTaskDoneSub);

        p.addView(scrollWrap(content), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = bottomBar();
        Button sameArea = secondaryButton("同一区域，再拍一个任务", 56);
        sameArea.setOnClickListener(v -> {
            String area = "";
            if (currentTask >= 0 && currentTask < site.tasks.size()) {
                String a = site.tasks.get(currentTask).area;
                area = a == null ? "" : a;
            }
            askTask(area);
        });
        bottom.addView(sameArea);

        Button more = primaryButton("换一个区域拍", 68, PRIMARY);
        more.setOnClickListener(v -> askTask(""));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        mlp.topMargin = dp(10);
        bottom.addView(more, mlp);

        Button finish = secondaryButton("没有了，全部拍完", 56);
        finish.setOnClickListener(v -> finishAll());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(10);
        bottom.addView(finish, lp);

        p.addView(bottom);
        return p;
    }

    // ---------------------------------------------------------------- 结束屏

    private LinearLayout buildDonePage() {
        LinearLayout p = pageShell();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(48), dp(24), dp(16));

        TextView tick = new TextView(this);
        tick.setText("✓");
        tick.setTextSize(80);
        tick.setTextColor(OK);
        tick.setGravity(Gravity.CENTER);
        content.addView(tick);

        TextView title = new TextView(this);
        title.setText("拍摄完成");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(10));
        content.addView(title);

        tvDoneStatus = new TextView(this);
        tvDoneStatus.setText("正在自动上传，请稍等…");
        tvDoneStatus.setTextSize(17);
        tvDoneStatus.setTextColor(MUTED);
        tvDoneStatus.setGravity(Gravity.CENTER);
        content.addView(tvDoneStatus);

        p.addView(scrollWrap(content), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = bottomBar();
        Button again = primaryButton("再拍一个采集点", 68, PRIMARY);
        again.setOnClickListener(v -> resetForNext());
        bottom.addView(again);
        p.addView(bottom);

        return p;
    }

    // ---------------------------------------------------------------- 场景二级联动

    private void refreshSceneSpinner() {
        int catPos = spCat == null ? 0 : spCat.getSelectedItemPosition();
        shownIndex.clear();
        List<String> labels = new ArrayList<String>();
        if (catPos <= 0) {
            labels.add("① 先选上面的大类");
        } else {
            String cat = categories.get(catPos - 1);
            for (int i = 0; i < sceneItems.size(); i++) {
                if (cat.equals(sceneItems.get(i)[2])) {
                    String[] row = sceneItems.get(i);
                    String text = row.length > 3 && row[3] != null && !row[3].isEmpty() ? row[3] : row[1];
                    labels.add(text);
                    shownIndex.add(i);
                }
            }
            if (labels.isEmpty()) labels.add("这个大类下没有场景");
        }
        if (spScene2 != null) {
            spScene2.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, labels));
        }
    }
    /** 输入框里的邀请码变了：自动按它加载分类（同码不重复加载） */
    private void autoLoadScenes() {
        if (etInvite == null) return;
        String code = etInvite.getText().toString().trim().toUpperCase();
        if (code.length() < 4) return;
        if (code.equals(lastSceneInvite)) return;
        store.setInvite(code);
        loadScenes(code);
    }

    private void loadScenes() {
        loadScenes(store.invite());
    }

    /** 先用这个邀请码问服务器「你能看到哪些大类」，拿不到就用安装包里的全量 */
    private void loadScenes(final String invite) {
        new Thread(() -> {
            String code = invite == null ? "" : invite.trim().toUpperCase();
            org.json.JSONArray items = null;
            String note = "";
            if (!code.isEmpty()) {
                try {
                    org.json.JSONObject r = api.request("GET",
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
                    java.io.InputStream in = getResources().openRawResource(R.raw.scenes);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    in.close();
                    items = new org.json.JSONObject(bos.toString("UTF-8")).optJSONArray("items");
                    note = "离线分类表";
                } catch (Exception e2) {
                    final String msg = e2.getMessage() == null ? e2.toString() : e2.getMessage();
                    runOnUiThread(() -> toast("场景分类读取失败：" + msg));
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
            lastSceneInvite = code;
            runOnUiThread(() -> {
                sceneItems.clear();
                sceneItems.addAll(list);
                categories.clear();
                categories.addAll(cats);

                List<String> catLabels = new ArrayList<String>();
                catLabels.add("① 请选择大类");
                catLabels.addAll(categories);
                if (spCat != null) {
                    spCat.setAdapter(new android.widget.ArrayAdapter<String>(this,
                            android.R.layout.simple_spinner_dropdown_item, catLabels));
                    spCat.setSelection(0);
                    spCat.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                            refreshSceneSpinner();
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
                    });
                }
                refreshSceneSpinner();
                if (tvVendor != null && !tip.isEmpty()) tvVendor.setText("分类：" + tip + "（点这里可改邀请码）");
            });
        }).start();
    }

    /** 从 res/raw/pca.json 读省市区（31 省 / 300+ 市 / 2900+ 区县） */
    private void loadRegions() {
        if (provArr.length > 0) return;
        new Thread(() -> {
            try {
                java.io.InputStream in = getResources().openRawResource(R.raw.pca);
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
                runOnUiThread(() -> {
                    provArr = provs;
                    cityArr = cities;
                    distArr = dists;
                    spProv.setAdapter(new android.widget.ArrayAdapter<String>(MainActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, provArr));
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("地址数据加载失败：" + e.getMessage()));
            }
        }).start();
    }

    private void updateCities(int provPos) {
        if (provPos <= 0 || provPos - 1 >= cityArr.length) {
            spCity.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"城市"}));
            return;
        }
        String[] cs = cityArr[provPos - 1];
        spCity.setAdapter(new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, cs.length == 0 ? new String[]{"城市"} : cs));
        updateDistricts(0);
    }

    private void updateDistricts(int cityPos) {
        int provPos = spProv.getSelectedItemPosition();
        if (provPos <= 0 || provPos - 1 >= distArr.length) return;
        String[][] ds = distArr[provPos - 1];
        if (cityPos < 0 || cityPos >= ds.length) {
            spDist.setAdapter(new android.widget.ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_dropdown_item, new String[]{"区 / 县"}));
            return;
        }
        spDist.setAdapter(new android.widget.ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, ds[cityPos]));
    }

    private String guideOf(String shot) {
        String s = shot == null ? "" : shot;
        for (String[] g : GUIDE) if (s.contains(g[0])) return g[1];
        return "对准要拍的东西，拿稳手机，光线充足再按快门。";
    }

    private void showGuide(String shot) {
        new AlertDialog.Builder(this)
                .setTitle("怎么拍：「" + shot + "」")
                .setMessage(guideOf(shot))
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 质检：模糊 / 过暗 / 视频太短 返回问题描述，合格返回 null */
    private String checkQuality(String path, boolean video) {
        try {
            if (video) {
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                r.setDataSource(path);
                String dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                r.release();
                long ms = dur == null ? 0 : Long.parseLong(dur);
                if (ms < 2000) return "视频只有 " + (ms / 1000.0) + " 秒，太短了（至少 2 秒）";
                return null;
            }
            android.graphics.BitmapFactory.Options opt = new android.graphics.BitmapFactory.Options();
            opt.inSampleSize = 4;
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(path, opt);
            if (bmp == null) return null;
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            bmp.recycle();
            if (w < 8 || h < 8) return null;
            long sum = 0;
            for (int v : px) {
                int g = (int) (((v >> 16) & 0xFF) * 0.299 + ((v >> 8) & 0xFF) * 0.587 + (v & 0xFF) * 0.114);
                sum += g;
            }
            double mean = sum / (double) px.length;
            double lapSum = 0, lapSq = 0;
            int cnt = 0;
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int c = gray(px[y * w + x]);
                    int lap = 4 * c - gray(px[(y - 1) * w + x]) - gray(px[(y + 1) * w + x])
                            - gray(px[y * w + x - 1]) - gray(px[y * w + x + 1]);
                    lapSum += lap;
                    lapSq += (double) lap * lap;
                    cnt++;
                }
            }
            double var = cnt == 0 ? 0 : (lapSq / cnt - Math.pow(lapSum / cnt, 2));
            if (var < 25) return String.format("画面偏模糊（清晰度 %.0f）", var);
            if (mean < 40) return String.format("画面偏暗（亮度 %.0f）", mean);
            if (mean > 225) return String.format("画面过亮（亮度 %.0f）", mean);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private int gray(int c) {
        return (int) (((c >> 16) & 0xFF) * 0.299 + ((c >> 8) & 0xFF) * 0.587 + (c & 0xFF) * 0.114);
    }

    /** 质检通过后收下这份素材（原逻辑） */
    private void acceptCapture(File dest, boolean video) {
        Model.Media m = new Model.Media(dest.getAbsolutePath(), video ? "video" : "photo",
                video ? "video/mp4" : "image/jpeg", dest.length());
        m.shot = pendingShot;
        m.capturedAt = dest.lastModified() > 0 ? dest.lastModified() : System.currentTimeMillis();
        if (m.antiCode == null || m.antiCode.isEmpty()) m.antiCode = antiCodeOf(dest);
        Model.Task task = task(currentTask);
        for (int i = task.media.size() - 1; i >= 0; i--) {
            Model.Media old = task.media.get(i);
            if (pendingShot.equals(old.shot)) {
                if ("uploaded".equals(old.state) && old.fileId != null && !old.fileId.isEmpty()) {
                    deleteFileRemote(old.fileId);
                }
                task.media.remove(i);
            }
        }
        task.media.add(m);
        store.saveQueue(site);
        if (doneCount(currentTask) >= shotsOf(task).length) {
            if ("task1".equals(task.type) && (task.area == null || task.area.isEmpty())
                    && doneCount(currentTask) >= 3) {
                toast("门头/全景拍完了，接着填区域和动作");
                showAreaForTask1();
            } else if ("site".equals(task.type)) {
                // 门头 + 全景照片 + 全景视频拍完：直接让填区域，不用再点一次
                toast("门头/全景拍完了，接着拍区域");
                tvTaskDoneTitle.setText("门头与全景 拍完了");
                tvTaskDoneSub.setText("接着填这次要拍的区域和动作");
                showPage(3);
                tvTaskDoneTitle.postDelayed(() -> askTask(""), 400);
            } else {
                toast("这个任务拍完了");
                tvTaskDoneTitle.setText(labelOf(task) + " 拍完了");
                tvTaskDoneSub.setText("还有区域或任务要拍吗？");
                showPage(3);
            }
            if (!uploading) startUpload();
        } else {
            currentShot = firstUndone(currentTask);
            toast("再拍下一项");
            renderShot();
        }
    }

    private void beginShooting() {
        String collector = etCollector.getText().toString().trim();
        if (collector.isEmpty()) {
            toast("请先填写采集员姓名");
            etCollector.requestFocus();
            return;
        }

        String invite = etInvite == null ? store.invite() : etInvite.getText().toString().trim();
        invite = invite == null ? "" : invite.toUpperCase();
        if (invite.isEmpty()) {
            toast("请填写邀请码");
            if (etInvite != null) etInvite.requestFocus();
            return;
        }
        store.setInvite(invite);

        String typed = etStoreName.getText().toString().trim();
        if (typed.isEmpty()) {
            toast("请先填写门店名称");
            etStoreName.requestFocus();
            return;
        }

        int pi = spProv.getSelectedItemPosition();
        int ci = spCity.getSelectedItemPosition();
        int di = spDist.getSelectedItemPosition();
        if (pi <= 0 || ci < 0 || di < 0) {
            toast("请先选择省 / 市 / 区");
            return;
        }
        String street = etStreet.getText().toString().trim();
        if (street.isEmpty()) {
            toast("请填写街道门牌号");
            etStreet.requestFocus();
            return;
        }
        String provName = provArr[pi];
        String cityName = cityArr[pi - 1][ci];
        String distName = distArr[pi - 1][ci][di];
        String addr = provName + ("市辖区".equals(cityName) ? "" : cityName) + distName + street;

        if (!invite.equals(lastSceneInvite)) {
            toast("邀请码变了，正在加载该厂商的分类，稍等再点「开始拍摄」");
            loadScenes(invite);
            return;
        }
        if (sceneItems.isEmpty()) {
            toast("场景分类还没准备好，请稍等");
            loadScenes(invite);
            return;
        }
        if (spCat.getSelectedItemPosition() <= 0) {
            toast("请先选大类（上面第一个）");
            spCat.performClick();
            return;
        }
        int p2 = spScene2.getSelectedItemPosition();
        if (p2 < 0 || p2 >= shownIndex.size()) {
            toast("请再选具体场景（下面第二个）");
            spScene2.performClick();
            return;
        }

        String areaTxt = etArea.getText().toString().trim();
        double areaVal = 0;
        try { areaVal = areaTxt.isEmpty() ? 0 : Double.parseDouble(areaTxt); } catch (Exception e) { areaVal = 0; }
        if (!(areaVal > 0)) {
            toast("请填写场地面积（平方米）");
            etArea.requestFocus();
            return;
        }

        if (!collector.equals(store.name())) {
            store.setName(collector);
            store.setToken("");
            api.token = "";
        }
        if (!site.name.isEmpty() && !site.name.equals(typed)) {
            site = new Model.Site();
            store.clearQueue();
        }
        site.name = typed;
        site.address = addr;
        site.invite = invite;
        String[] chosen = sceneItems.get(shownIndex.get(p2));
        site.scene = chosen[0];
        site.sceneLabel = chosen[1];
        site.area = areaVal;
        if (site.tasks.isEmpty()) {
            Model.Task t = new Model.Task();
            t.type = "task1";
            t.name = "任务1";
            site.tasks.add(t);
        }
        try {
            int t = firstIncompleteTask();
            if (t < 0) {
                showPage(4);
                if (!uploading) startUpload();
                return;
            }
            currentTask = t;
            currentShot = firstUndone(t);
            showPage(2);
            renderShot();
        } catch (Exception e) {
            toast("开始拍摄失败：" + e);
            return;
        }
        startGps();
        if (api.token.isEmpty()) autoLogin();
    }

    /** 第一个还没拍完的任务下标；全拍完返回 -1 */
    private int firstIncompleteTask() {
        for (int i = 0; i < site.tasks.size(); i++) {
            Model.Task t = site.tasks.get(i);
            if (firstUndone(i) < shotsOf(t).length) return i;
        }
        return -1;
    }

    /** 闪退时把堆栈写到本地并上报服务器 */
    private void installCrashReporter() {
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String stack = sw.toString();
                if (stack.length() > 6000) stack = stack.substring(0, 6000);
                try {
                    java.io.File dir = new java.io.File(getExternalFilesDir(null), "crash");
                    dir.mkdirs();
                    java.io.File f = new java.io.File(dir, "crash-" + System.currentTimeMillis() + ".txt");
                    java.io.FileWriter w = new java.io.FileWriter(f);
                    w.write(stack);
                    w.close();
                } catch (Throwable ignored) { }
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("name", store == null ? "" : store.name());
                    o.put("message", String.valueOf(e));
                    o.put("stack", stack);
                    o.put("task", currentTask + "/" + currentShot);
                    byte[] body = o.toString().getBytes("UTF-8");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(api.base + "/api/client-log").openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(3000);
                    c.setReadTimeout(3000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    c.setFixedLengthStreamingMode(body.length);
                    c.getOutputStream().write(body);
                    c.getResponseCode();
                    c.disconnect();
                } catch (Throwable ignored) { }
            } catch (Throwable ignored) { }
            if (def != null) def.uncaughtException(t, e);
        });
    }

    private void confirmRestart() {
        final String oldSiteId = site.siteId;
        new AlertDialog.Builder(this)
                .setTitle("重新开始？")
                .setMessage("这一家会从头再拍一遍。\n已经传到服务器上的照片/视频会一起删掉，不会留下旧的。")
                .setPositiveButton("重新开始", (d, w) -> {
                    if (oldSiteId != null && !oldSiteId.isEmpty()) deleteSiteRemote(oldSiteId);
                    site = new Model.Site();
                    site.name = etStoreName.getText().toString().trim();
                    Model.Task t = new Model.Task();
                    t.type = "site";
                    t.name = "任务1·门头与全景";
                    site.tasks.add(t);
                    currentTask = 0;
                    currentShot = 0;
                    store.saveQueue(site);
                    showPage(2);
                    renderShot();
                })
                .setNegativeButton("不了", null)
                .show();
    }

    /** 后台删掉服务器上的采集点（含文件） */
    private void deleteSiteRemote(final String siteId) {
        new Thread(() -> {
            try { api.request("DELETE", "/api/sites/" + siteId, null); } catch (Exception ignored) { }
        }).start();
    }

    /** 后台删掉服务器上的单个文件（重拍时替换旧文件用） */
    private void deleteFileRemote(final String fileId) {
        new Thread(() -> {
            try { api.request("DELETE", "/api/files/" + fileId, null); } catch (Exception ignored) { }
        }).start();
    }

    private void keepName() {
        if (etStoreName != null) {
            String n = etStoreName.getText().toString().trim();
            if (!n.isEmpty()) site.name = n;
        }
    }

    private Model.Task task(int i) {
        return site.tasks.get(i);
    }

    private String labelOf(Model.Task t) {
        if (t.area != null && !t.area.isEmpty()) return t.area + "：" + t.name;
        return t.name;
    }

    private boolean hasShot(int taskIndex, int shotIndex) {
        if (taskIndex >= site.tasks.size()) return false;
        Model.Task t = site.tasks.get(taskIndex);
        String[][] shots = shotsOf(t);
        if (shotIndex >= shots.length) return false;
        String key = shots[shotIndex][0];
        for (Model.Media m : t.media) if (key.equals(m.shot)) return true;
        return false;
    }

    private int firstUndone(int taskIndex) {
        int n = shotsOf(site.tasks.get(taskIndex)).length;
        for (int i = 0; i < n; i++) if (!hasShot(taskIndex, i)) return i;
        return n;
    }

    private int doneCount(int taskIndex) {
        int n = 0;
        int total = shotsOf(site.tasks.get(taskIndex)).length;
        for (int i = 0; i < total; i++) if (hasShot(taskIndex, i)) n++;
        return n;
    }

    /** 左右滑动切换拍摄项：delta = -1 上一项，+1 下一项 */
    private void stepShot(int delta) {
        if (site == null || site.tasks.isEmpty()) return;
        Model.Task task = task(currentTask);
        String[][] shots = shotsOf(task);
        int target = currentShot + delta;

        // 越界就跨任务
        if (target < 0) {
            if (currentTask > 0) {
                currentTask--;
                currentShot = shotsOf(task(currentTask)).length - 1;
                currentShot = firstUndoneOrLast(currentTask);
                renderShot();
                toast("回到上一项");
            } else {
                toast("已经是第一项了");
            }
            return;
        }
        if (target >= shots.length) {
            if (currentTask + 1 < site.tasks.size()) {
                currentTask++;
                currentShot = 0;
                renderShot();
                toast("下一项");
            } else {
                toast("已经是最后一项了");
            }
            return;
        }

        currentShot = target;
        slideDir = delta;
        renderShot();
        toast(delta < 0 ? "上一项" : "下一项");
    }

    /** 取该任务最后一拍的项（用于往回切） */
    private int firstUndoneOrLast(int taskIndex) {
        int last = shotsOf(task(taskIndex)).length - 1;
        for (int i = 0; i <= last; i++) if (hasShot(taskIndex, i)) last = Math.max(last, last);
        return Math.max(0, last);
    }

    private void renderShot() {
        shotArea.removeAllViews();
        Model.Task task = task(currentTask);
        String[][] shots = shotsOf(task);
        if (currentShot < 0 || currentShot >= shots.length) {
            currentShot = firstUndone(currentTask);
            if (currentShot >= shots.length) {
                tvTaskDoneTitle.setText(labelOf(task) + " 拍完了");
                tvTaskDoneSub.setText("site".equals(task.type) ? "这个采集点还有区域要拍吗？" : "还有区域或任务要拍吗？");
                showPage(3);
                return;
            }
        }
        if ("task1".equals(task.type) && currentShot >= 3
                && (task.area == null || task.area.isEmpty())) {
            showAreaForTask1();
            return;
        }

        int idx = currentShot;
        String key = shots[idx][0];
        String title = shots[idx][1];
        String hint = shots[idx][2];
        String kind = shots[idx][3];
        boolean done = hasShot(currentTask, idx);

        String prefix = "area".equals(task.type) ? "" : "";
        tvStep.setText(prefix + labelOf(task) + " · 第 " + (idx + 1) + "/" + shots.length + " 项 · " + title);
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < shots.length; i++) {
            if (i > 0) dots.append("   ");
            if (i == currentShot) dots.append("◆");            // 当前位置
            else if (hasShot(currentTask, i)) dots.append("●"); // 已完成
            else dots.append("○");                              // 未拍
        }
        tvDots.setText(dots.toString());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(CARD, 18));
        int pad = dp(20);
        card.setPadding(pad, pad, pad, pad);
        shotArea.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        try {
            int w = getResources().getDisplayMetrics().widthPixels;
            card.setTranslationX(slideDir > 0 ? w * 0.35f : -w * 0.35f);
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
                            android.widget.ImageView iv = new android.widget.ImageView(this);
                            iv.setImageBitmap(bm);
                            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
                            ilp.topMargin = dp(10);
                            card.addView(iv, ilp);
                        }
                    }
                } catch (Throwable ignored) {
                }

                TextView fn = new TextView(this);
                fn.setText("已用：" + new java.io.File(mine.path).getName());
                fn.setTextSize(13);
                fn.setTextColor(MUTED);
                fn.setGravity(Gravity.CENTER);
                fn.setPadding(0, dp(6), 0, 0);
                card.addView(fn);
            }
        }

        TextView num = new TextView(this);
        num.setText(done ? "这一项已经拍好了" : "现在拍这一项");
        num.setTextSize(15);
        num.setTextColor(done ? OK : MUTED);
        num.setGravity(Gravity.CENTER);
        card.addView(num);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(32);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, dp(8));
        card.addView(t);

        TextView h = new TextView(this);
        h.setText(hint);
        h.setTextSize(18);
        h.setTextColor(MUTED);
        h.setGravity(Gravity.CENTER);
        h.setLineSpacing(dp(5), 1f);
        card.addView(h);

        boolean galleryOnly = "panophoto".equals(key);
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(23);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        TextView tip = new TextView(this);
        tip.setText("不会拍？点这里看要点");
        tip.setTextSize(13);
        tip.setTextColor(Color.parseColor("#1B63D6"));
        tip.setPadding(0, dp(8), 0, dp(4));
        b.setText(done ? "重新拍这一项" : (kind.equals("video") ? "开始录像" : "拍照"));
        b.setBackground(roundRect(done ? OK : PRIMARY, 16));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        if (BuildConfigVersion.NO_CAPTURE) {
            b.setVisibility(View.GONE);
        } else {
            b.setOnClickListener(v -> captureShot(key, kind));
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        blp.topMargin = dp(22);
        card.addView(b, blp);

        TextView next = new TextView(this);
        next.setText(galleryOnly ? "全景照片请用手机全景模式拍，再从相册选"
                : (done ? "拍完会自动跳到下一项" : "拍好之后会自动跳到下一项"));
        next.setTextSize(13);
        next.setTextColor(MUTED);
        next.setGravity(Gravity.CENTER);
        next.setPadding(0, dp(12), 0, 0);
        card.addView(next);

        String pickText = BuildConfigVersion.NO_CAPTURE
                ? (kind.equals("video") ? "从相册选水印视频" : "从相册选水印照片")
                : (kind.equals("video") ? "从相册选视频" : "从相册选照片");
        if (BuildConfigVersion.NO_CAPTURE && !galleryOnly) {
            Button openWm = secondaryButton("① 打开水印相机去拍", 60);
            openWm.setOnClickListener(v -> openWatermarkCamera(key, kind));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
            wlp.topMargin = dp(10);
            card.addView(openWm, wlp);

            TextView tip2 = new TextView(this);
            tip2.setText("拍完按返回，会自动让你从相册里选刚拍的那张");
            tip2.setTextSize(13);
            tip2.setTextColor(MUTED);
            tip2.setGravity(Gravity.CENTER);
            tip2.setPadding(0, dp(8), 0, dp(2));
            card.addView(tip2);
        }

        Button pick = secondaryButton(pickText, BuildConfigVersion.NO_CAPTURE ? 64 : 54);
        pick.setOnClickListener(v -> pickShot(key, kind));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        plp.topMargin = dp(10);
        card.addView(pick, plp);

        int n = doneCount(currentTask);
        if (n > 0) {
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, dp(14), 0, 0);
            shotArea.addView(list);
            for (int i = 0; i < shots.length; i++) {
                if (!hasShot(currentTask, i)) continue;
                final int d = i;
                TextView row = new TextView(this);
                row.setText("✓ " + shots[d][1] + "（点这里重拍）");
                row.setTextSize(15);
                row.setTextColor(MUTED);
                row.setPadding(dp(6), dp(10), dp(6), dp(10));
                row.setOnClickListener(v -> { currentShot = d; renderShot(); });
                list.addView(row);
            }
        }
    }

    private void captureShot(String shot, String kind) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingShot = shot;
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                    && getSharedPreferences("egoops", MODE_PRIVATE).getBoolean("asked_camera", false)) {
                // 被「拒绝且不再询问」了，只能去设置里开
                new AlertDialog.Builder(this)
                        .setTitle("需要相机权限")
                        .setMessage("系统里已经把相机权限关掉了，需要到「设置 → 应用 → 采集点拍照 → 权限」里打开相机。\n\n要现在去设置吗？")
                        .setPositiveButton("去设置", (d, w) -> {
                            try {
                                startActivity(new android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:" + getPackageName())));
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton("先不拍", null)
                        .show();
                return;
            }
            getSharedPreferences("egoops", MODE_PRIVATE).edit().putBoolean("asked_camera", true).apply();
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERM);
            toast("请点“允许”，才能用相机");
            return;
        }
        pendingShot = shot;
        String name = (kind.equals("video") ? "VID_" : "IMG_") + System.currentTimeMillis() + (kind.equals("video") ? ".mp4" : ".jpg");
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        if (kind.equals("video")) {
            v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) { toast("无法创建文件，请检查存储权限"); return; }
            tempUri = uri;
            Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                startActivityForResult(i, REQ_VIDEO);
            } catch (Exception e) {
                toast("这台手机没有可用的录像界面，请改用「从相册选视频」");
                pickShot(shot, kind);
            }
        } else {
            v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) { toast("无法创建文件，请检查存储权限"); return; }
            tempUri = uri;
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                startActivityForResult(i, REQ_PHOTO);
            } catch (Exception e) {
                toast("这台手机没有可用的相机界面，请改用「从相册选照片」");
                pickShot(shot, kind);
            }
        }
    }

    /** 已知的水印相机包名（按顺序尝试） */
    private static final String[] WM_PACKAGES = {
            "com.chanjikeji.watermarkcamera",
            "com.chanjikeji.todaywatermark",
            "com.chanjikeji.watermark",
            "com.todaywatermark.camera",
            "com.watermark.camera",
            "com.tencent.watermark",
            "com.yuandao.camera",
            "com.kkmh.watermark",
    };

    /** 打开水印相机：已知包名 → 名字含「水印」 → 让用户从相机类 App 里选一次 */
    private void openWatermarkCamera(String shot, String kind) {
        pendingShot = shot;
        android.content.pm.PackageManager pm = getPackageManager();

        // 0) 上次选过的
        String saved = store.get("wm_pkg", "");
        if (!saved.isEmpty()) {
            try {
                android.content.Intent it = pm.getLaunchIntentForPackage(saved);
                if (it != null) {
                    launchWm(it);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // 1) 已知包名
        for (String pkg : WM_PACKAGES) {
            try {
                android.content.Intent it = pm.getLaunchIntentForPackage(pkg);
                if (it != null) {
                    store.put("wm_pkg", pkg);
                    launchWm(it);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2) 名字含「水印 / Watermark」
        java.util.List<String[]> cams = new java.util.ArrayList<>();
        java.util.List<String[]> named = new java.util.ArrayList<>();
        try {
            java.util.List<android.content.pm.ApplicationInfo> apps =
                    pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);
            for (android.content.pm.ApplicationInfo ai : apps) {
                String label = String.valueOf(pm.getApplicationLabel(ai));
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                boolean isCam = label.contains("相机") || label.contains("摄像") || label.contains("Watermark")
                        || label.contains("水印") || label.toLowerCase().contains("camera");
                if (!isCam) continue;
                cams.add(new String[]{label, ai.packageName});
                if (label.contains("水印") || label.contains("Watermark")) {
                    named.add(new String[]{label, ai.packageName});
                }
            }
        } catch (Throwable ignored) {
        }

        if (named.size() == 1) {
            store.put("wm_pkg", named.get(0)[1]);
            android.content.Intent it = pm.getLaunchIntentForPackage(named.get(0)[1]);
            if (it != null) {
                launchWm(it);
                return;
            }
        }

        // 3) 让用户选一次（水印类排前面）
        java.util.List<String[]> show = new java.util.ArrayList<>();
        show.addAll(named);
        for (String[] c : cams) {
            boolean dup = false;
            for (String[] x : show) if (x[1].equals(c[1])) dup = true;
            if (!dup) show.add(c);
        }

        if (show.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("没找到水印相机")
                    .setMessage("手机里没装名字带「水印」的相机 App。\n\n可以先去应用商店装「今日水印相机」，再回来点这个按钮。")
                    .setPositiveButton("去应用商店", (d, w) -> {
                        try {
                            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("market://search?q=今日水印相机")));
                        } catch (Throwable e) {
                            try {
                                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://sj.qq.com/appdetail/com.tencent.tmgp.sgame")));
                            } catch (Throwable ignored) {
                                toast("请手动打开应用商店搜索「今日水印相机」");
                            }
                        }
                    })
                    .setNegativeButton("知道了", null)
                    .show();
            return;
        }

        String[] items = new String[show.size()];
        for (int i = 0; i < show.size(); i++) {
            items[i] = show.get(i)[0];
        }
        new AlertDialog.Builder(this)
                .setTitle("用哪个 App 拍？")
                .setItems(items, (d, which) -> {
                    String pkg = show.get(which)[1];
                    store.put("wm_pkg", pkg);
                    try {
                        android.content.Intent it = pm.getLaunchIntentForPackage(pkg);
                        if (it != null) launchWm(it);
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 真正跳转，并记下开始时间（回来后自动抓文件） */
    private void launchWm(android.content.Intent it) {
        try {
            startActivity(it);
            wmLaunchAt = System.currentTimeMillis();
            awaitingWatermarkImport = true;
            toast("拍完按返回，会自动取回刚拍的");
        } catch (Throwable e) {
            toast("打不开这个应用：" + e);
        }
    }

    /** 一次选多张，按顺序填进当前任务剩下的项 */
    private void pickMultiple() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            String kind = (pendingShot != null && pendingShot.toLowerCase().contains("video")) ? "video" : "photo";
            i.setType(kind.equals("video") ? "video/*" : "image/*");
            i.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, REQ_PICK_MULTI);
        } catch (Throwable e) {
            toast("这台手机不支持批量选择：" + e);
        }
    }

    /** 批量导入：把选中的素材按顺序塞进未完成的项 */
    private void ingestMultiple(android.content.ClipData clip) {
        Model.Task task = task(currentTask);
        String[][] shots = shotsOf(task);
        int taken = 0;
        for (int i = 0; i < clip.getItemCount(); i++) {
            if (taken >= 30) break;
            Uri uri = clip.getItemAt(i).getUri();
            // 找下一个还没拍的项
            int idx = -1;
            for (int k = 0; k < shots.length; k++) {
                if (!hasShot(currentTask, k)) {
                    idx = k;
                    break;
                }
            }
            if (idx < 0) break;
            pendingShot = shots[idx][0];
            String kind = shots[idx][3];
            boolean isVideo = kind.equals("video");
            // 类型不匹配就跳过
            String type = "";
            try {
                type = String.valueOf(getContentResolver().getType(uri));
            } catch (Throwable ignored) {
            }
            if (isVideo && !type.startsWith("video")) continue;
            if (!isVideo && !type.startsWith("image")) continue;
            ingest(uri, isVideo);
            taken++;
        }
        if (taken == 0) {
            toast("选中的素材没对上：请确认照片/视频类型");
        } else {
            toast("已导入 " + taken + " 个素材");
        }
    }

    /** 从水印相机回来：自动抓「比打开时刻更新」的最新一张/一段 */
    private void autoGrabLatest(String shot, String kind) {
        boolean video = kind.equals("video");
        Uri found = null;
        String name = "";
        try {
            long since = wmLaunchAt > 0 ? wmLaunchAt - 3000 : System.currentTimeMillis() - 300000;
            android.database.Cursor c;
            if (video) {
                c = getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME},
                        MediaStore.Video.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since / 1000)},
                        MediaStore.Video.Media.DATE_ADDED + " DESC");
            } else {
                c = getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                        MediaStore.Images.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since / 1000)},
                        MediaStore.Images.Media.DATE_ADDED + " DESC");
            }
            if (c == null || !c.moveToFirst()) {
                // 放宽到 30 分钟 + 文件名前缀（水印相机常见命名）
                long since2 = System.currentTimeMillis() - 30L * 60 * 1000;
                if (c != null) c.close();
                c = video
                        ? getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME},
                        MediaStore.Video.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since2 / 1000)},
                        MediaStore.Video.Media.DATE_ADDED + " DESC")
                        : getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                        MediaStore.Images.Media.DATE_ADDED + " >= ?",
                        new String[]{String.valueOf(since2 / 1000)},
                        MediaStore.Images.Media.DATE_ADDED + " DESC");
            }
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                name = c.getString(1);
                Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                found = android.content.ContentUris.withAppendedId(base, id);
            }
            if (c != null) c.close();
        } catch (Throwable ignored) {
        }

        if (found == null) {
            toast("没找到刚拍的，请手动选一下");
            pickShot(shot, kind);
            return;
        }
        toast("已自动取回：" + name);
        ingest(found, video);
    }

    /** 从相册选已有照片/视频 */
    private void pickShot(String shot, String kind) {
        pendingShot = shot;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(kind.equals("video") ? "video/*" : "image/*");
        startActivityForResult(i, kind.equals("video") ? REQ_PICK_VIDEO : REQ_PICK_PHOTO);
    }

    /** 相机 / 录音 / 读媒体 / 通知（上传进度的通知要它） */
    private void askAllPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        java.util.List<String> need = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) {
            need.add("android.permission.READ_MEDIA_IMAGES");
            need.add("android.permission.READ_MEDIA_VIDEO");
        } else {
            need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
                need.add("android.permission.POST_NOTIFICATIONS");
            if (checkSelfPermission("android.permission.READ_MEDIA_IMAGES") != PackageManager.PERMISSION_GRANTED)
                need.add("android.permission.READ_MEDIA_IMAGES");
            if (checkSelfPermission("android.permission.READ_MEDIA_VIDEO") != PackageManager.PERMISSION_GRANTED)
                need.add("android.permission.READ_MEDIA_VIDEO");
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM_ALL);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERM && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && !pendingShot.isEmpty()) {
            String shot = pendingShot;
            pendingShot = "";
            for (String[] s : shotsOf(site.tasks.get(currentTask))) if (s[0].equals(shot)) { captureShot(shot, s[3]); return; }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (awaitingWatermarkImport) {
            awaitingWatermarkImport = false;
            String kind = (pendingShot != null && pendingShot.toLowerCase().contains("video")) ? "video" : "photo";
            final String shot = pendingShot;
            if (shot != null && !shot.isEmpty()) {
                tvStep.postDelayed(() -> autoGrabLatest(shot, kind), 500);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            tempUri = null;
            toast("没有拍，点下面按钮再来一次");
            return;
        }
        if (requestCode == REQ_PICK_MULTI) {
            android.content.ClipData clip = data != null ? data.getClipData() : null;
            if (clip == null && data != null && data.getData() != null) {
                // 只选了一个
                String kind = (pendingShot != null && pendingShot.toLowerCase().contains("video")) ? "video" : "photo";
                ingest(data.getData(), kind.equals("video"));
                return;
            }
            if (clip != null) ingestMultiple(clip);
            return;
        }

        boolean fromPick = requestCode == REQ_PICK_PHOTO || requestCode == REQ_PICK_VIDEO;
        boolean video = requestCode == REQ_VIDEO || requestCode == REQ_PICK_VIDEO;
        Uri src;
        if (fromPick) {
            src = data != null ? data.getData() : null;
        } else {
            src = (data != null && data.getData() != null) ? data.getData() : tempUri;
        }
        if (src == null) { toast("没有拿到画面，请再拍一次"); return; }
        ingest(src, video);
    }

    /** 把一份素材收进当前拍摄项（相机、相册、水印相机自动取回都走这里） */
    private void ingest(Uri src, boolean video) {
        try {
            File dir = new File(getExternalFilesDir(null), "captures");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, (video ? "VID_" : "IMG_") + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"));
            InputStream in = getContentResolver().openInputStream(src);
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            try { getContentResolver().delete(src, null, null); } catch (Exception ignored) {}

            if (gpsAddress != null && !gpsAddress.trim().isEmpty()) {
                site.wmAddress = gpsAddress.trim();
                store.saveQueue(site);
            }
            if (!video && !BuildConfigVersion.NO_CAPTURE) {
                stampWatermark(dest, dest.lastModified() > 0 ? dest.lastModified() : System.currentTimeMillis(), antiCodeOf(dest));
            }

            String problem = checkQuality(dest.getAbsolutePath(), video);
            if (problem != null) {
                final File tmpFile = dest;
                final Uri oldUri = src;
                new AlertDialog.Builder(this)
                        .setTitle("这张可能不合格")
                        .setMessage(problem + "\n\n建议重拍，也可以先留着。")
                        .setPositiveButton("重拍", (d, w) -> {
                            try { tmpFile.delete(); } catch (Exception ignored) {}
                            String kind2 = (pendingShot != null && pendingShot.toLowerCase().contains("video")) ? "video" : "photo";
                            captureShot(pendingShot, kind2);
                        })
                        .setNegativeButton("仍然使用", (d, w) -> acceptCapture(tmpFile, video))
                        .setCancelable(false)
                        .show();
                return;
            }

            Model.Media m = new Model.Media(dest.getAbsolutePath(), video ? "video" : "photo",
                    video ? "video/mp4" : "image/jpeg", dest.length());
            m.shot = pendingShot;
            Model.Task task = task(currentTask);
            for (int i = task.media.size() - 1; i >= 0; i--) {
                Model.Media old = task.media.get(i);
                if (pendingShot.equals(old.shot)) {
                    // 重拍就是要替换：服务器上已经传过的旧文件也删掉
                    if ("uploaded".equals(old.state) && old.fileId != null && !old.fileId.isEmpty()) {
                        deleteFileRemote(old.fileId);
                    }
                    task.media.remove(i);
                }
            }
            task.media.add(m);
            store.saveQueue(site);

            if (doneCount(currentTask) >= shotsOf(task).length) {
                toast("这个任务拍完了");
                tvTaskDoneTitle.setText(labelOf(task) + " 拍完了");
                tvTaskDoneSub.setText("site".equals(task.type) ? "这个采集点还有区域要拍吗？" : "还有区域或任务要拍吗？");
                showPage(3);
                if (!uploading) startUpload();
            } else {
                currentShot = firstUndone(currentTask);
                toast("拍好了，接着拍第 " + (currentShot + 1) + " 项");
                renderShot();
                if (!uploading) startUpload();
            }
        } catch (Exception e) {
            toast("保存失败：" + e.getMessage());
        } finally {
            tempUri = null;
            pendingShot = "";
        }
    }

    private void addTask() {
        String preset = "";
        if (currentTask >= 0 && currentTask < site.tasks.size()) {
            preset = site.tasks.get(currentTask).area;
        }
        askTask(preset);
    }

    /** 填「区域 + 任务」再开拍；presetArea 非空时预填区域，只让填任务 */
    /** 任务名/区域改动后同步到服务器（后台，不打断拍摄） */
    private void syncTaskNamesAsync() {
        try {
            if (site.siteId == null || site.siteId.isEmpty()) return;
            new Thread(() -> {
                try {
                    new Uploader(api, site, store, null).syncTaskNames();
                } catch (Exception ignored) {
                }
            }).start();
        } catch (Exception ignored) {
        }
    }

    /** 任务1 的区域填写：写回当前任务，不新建 */
    private void showAreaForTask1() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        TextView tip = new TextView(this);
        tip.setText("门头照 + 全景照片 + 全景视频 已拍好。\n现在填这次要拍的第一个区域：");
        tip.setTextSize(14);
        tip.setTextColor(MUTED);
        tip.setPadding(0, 0, 0, dp(8));
        box.addView(tip);

        TextView l1 = new TextView(this);
        l1.setText("1. 这是哪个区域？（比如：饮料区 / 后厨）");
        l1.setTextSize(13);
        l1.setTextColor(MUTED);
        box.addView(l1);

        final EditText etAreaName = new EditText(this);
        etAreaName.setHint("饮料区");
        etAreaName.setTextSize(20);
        box.addView(etAreaName);

        TextView l2 = new TextView(this);
        l2.setText("2. 在这个区域要做什么动作？（比如：捡垃圾 / 擦拭桌面）");
        l2.setTextSize(13);
        l2.setTextColor(MUTED);
        l2.setPadding(0, dp(12), 0, 4);
        box.addView(l2);

        final EditText etTaskName = new EditText(this);
        etTaskName.setHint("捡垃圾");
        etTaskName.setTextSize(20);
        box.addView(etTaskName);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("填写区域和动作")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("开始拍摄这个区域", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = etAreaName.getText().toString().trim();
            String n = etTaskName.getText().toString().trim();
            if (a.isEmpty()) { toast("请填写区域名称"); etAreaName.requestFocus(); return; }
            if (n.isEmpty()) { toast("请填写动作名称"); etTaskName.requestFocus(); return; }
            Model.Task t = site.tasks.get(0);
            t.area = a;
            t.name = n + " · 门头与全景";
            store.saveQueue(site);
            syncTaskNamesAsync();
            currentTask = 0;
            currentShot = firstUndone(0);
            if (currentShot < 0) currentShot = 3;
            dlg.dismiss();
            renderShot();
        });
    }



    /** 素材防伪码：文件 SHA1 的前 12 位（大写） */
    private static String antiCodeOf(File f) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02X", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 把地址/日期/时间/门店/采集员烧进照片（和打卡水印相机一个意思） */
    private void stampWatermark(File f, long whenMs, String antiCode) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            int maxSide = Math.max(o.outWidth, o.outHeight);
            while (maxSide / sample > 2048) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (src == null) return;

            android.graphics.Bitmap bmp = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true);
            src.recycle();
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            int W = bmp.getWidth();
            int H = bmp.getHeight();
            float u = W / 1080f;                    // 以 1080 宽为基准

            String gps = gpsAddress == null ? "" : gpsAddress.trim();
            String addr = gps.isEmpty()
                    ? ((site.address == null || site.address.trim().isEmpty()) ? "（未填地址）" : site.address.trim() + "（自填）")
                    : gps;

            java.util.Calendar cal = java.util.Calendar.getInstance();
            String date = String.format("%04d.%02d.%02d", cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH));
            char[] wk = "日一二三四五六".toCharArray();
            String week = "星期" + wk[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
            String hhmm = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE));

            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

            // 文字最大宽度，地址按宽度折行
            float pad = 34 * u;
            float maxW = W - pad * 2 - 120 * u;
            p.setTextSize(38 * u);
            java.util.List<String> lines = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (char ch : addr.toCharArray()) {
                cur.append(ch);
                if (p.measureText(cur.toString()) > maxW) {
                    cur.deleteCharAt(cur.length() - 1);
                    if (cur.length() > 0) lines.add(cur.toString());
                    cur = new StringBuilder().append(ch);
                    if (lines.size() >= 2) break;
                }
            }
            if (lines.size() < 2 && cur.length() > 0) lines.add(cur.toString());

            // 背景块高度
            float lineH = 46 * u;
            float blockH = pad * 2 + 70 * u + lines.size() * lineH + 44 * u;
            float top = H - pad - blockH;

            android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            bg.setColor(android.graphics.Color.parseColor("#99000000"));
            android.graphics.RectF rf = new android.graphics.RectF(pad * 0.4f, top, W - pad * 0.4f, H - pad * 0.4f);
            c.drawRoundRect(rf, 18 * u, 18 * u, bg);

            // 左侧黄条
            android.graphics.Paint bar = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            bar.setColor(android.graphics.Color.parseColor("#FFC400"));
            c.drawRect(pad * 0.4f, top + 18 * u, pad * 0.4f + 8 * u, H - pad * 0.4f - 18 * u, bar);

            float x = pad * 0.4f + 26 * u;
            float y = top + pad + 30 * u;

            // 打卡 + 时间
            p.setColor(android.graphics.Color.parseColor("#FFC400"));
            p.setTextSize(46 * u);
            c.drawText("打卡", x, y, p);
            float tw = p.measureText("打卡");
            p.setColor(android.graphics.Color.WHITE);
            c.drawText(" " + hhmm, x + tw, y, p);

            // 地址
            p.setColor(android.graphics.Color.WHITE);
            p.setTextSize(38 * u);
            y += 62 * u;
            for (String ln : lines) {
                c.drawText(ln, x, y, p);
                y += lineH;
            }

            // 日期 + 星期（+ 门店）
            p.setTextSize(34 * u);
            String bottom = date + " " + week;
            c.drawText(bottom, x, y + 8 * u, p);

            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out);
            out.close();
            bmp.recycle();
        } catch (Exception e) {
            // 水印失败不影响拍摄
        }
    }

    /** 取一次真实定位，反查成文字地址（水印用） */
    private void startGps() {
        try {
            final android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return;
            android.location.Location best = null;
            for (String pv : new String[]{android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER}) {
                try {
                    android.location.Location l = lm.getLastKnownLocation(pv);
                    if (l == null) continue;
                    if (best == null || l.getTime() > best.getTime()) best = l;
                } catch (Exception ignored) {}
            }
            if (best != null) useLocation(best);

            final android.location.LocationListener[] holder = new android.location.LocationListener[1];
            android.location.LocationListener listener = new android.location.LocationListener() {
                @Override public void onLocationChanged(android.location.Location l) {
                    if (l.getAccuracy() > 80 && gpsAddress != null && !gpsAddress.isEmpty()) return;
                    useLocation(l);
                    try { lm.removeUpdates(holder[0]); } catch (Exception ignored) {}
                }
                @Override public void onStatusChanged(String p, int s, android.os.Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            holder[0] = listener;
            try {
                lm.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 0, 0, listener);
            } catch (Exception ignored) {}
            try {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 0, 0, listener);
            } catch (Exception ignored) {}
        } catch (Exception ignored) {
        }
    }

    /** 把坐标反查成「省市区街道」，失败就退成经纬度 */
    /** 坐标 → GCJ02 → 高德逆地理 → 真实地址；同时抓一张对应地图 */
    private void useLocation(android.location.Location l) {
        try {
            final double lat = l.getLatitude();
            final double lon = l.getLongitude();
            final float acc = l.getAccuracy();
            gpsCoords = String.format("%.6f, %.6f", lat, lon);

            // 手机给的是 WGS84，高德用 GCJ02，必须转换，否则红点会偏几百米
            final double[] gcj = Geo.wgs84ToGcj02(lat, lon);
            final String key = MapSnap.DEFAULT_KEY;

            new Thread(() -> {
                String text = Geo.regeo(key, gcj[0], gcj[1]);
                if (text == null || text.isEmpty()) {
                    try {
                        android.location.Geocoder gc = new android.location.Geocoder(
                                MainActivity.this, java.util.Locale.CHINA);
                        java.util.List<android.location.Address> list = gc.getFromLocation(lat, lon, 1);
                        if (list != null && !list.isEmpty()) {
                            android.location.Address a = list.get(0);
                            StringBuilder sb = new StringBuilder();
                            if (a.getAdminArea() != null) sb.append(a.getAdminArea());
                            if (a.getLocality() != null) sb.append(a.getLocality());
                            if (a.getSubLocality() != null) sb.append(a.getSubLocality());
                            if (a.getThoroughfare() != null) sb.append(a.getThoroughfare());
                            if (a.getSubThoroughfare() != null) sb.append(a.getSubThoroughfare());
                            text = sb.toString().replace(" ", "");
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (text == null || text.isEmpty()) {
                    text = "定位 " + gpsCoords;
                }
                if (acc > 100) {
                    text = text + "（精度约" + Math.round(acc) + "米）";
                }
                gpsAddress = text;

                try {
                    android.graphics.Bitmap bmp = Geo.staticMap(key, gcj[0], gcj[1], 320);
                    if (bmp != null) {
                        java.io.File dir = new java.io.File(getExternalFilesDir(null), "maps");
                        if (!dir.exists()) dir.mkdirs();
                        java.io.File out = new java.io.File(dir, "map_" + System.currentTimeMillis() + ".png");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                        bmp.recycle();
                        mapSnapFile = out;
                    }
                } catch (Throwable ignored) {
                }
            }).start();
        } catch (Throwable ignored) {
        }
    }

    private void askTask(String presetArea) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        TextView l1 = new TextView(this);
        l1.setText("1. 这是哪个区域？（比如：客厅 / 后厨 / 3号包间）");
        l1.setTextSize(13);
        l1.setTextColor(MUTED);
        l1.setPadding(0, dp(8), 0, dp(4));
        box.addView(l1);

        final EditText etAreaName = new EditText(this);
        etAreaName.setHint("客厅");
        etAreaName.setTextSize(20);
        etAreaName.setText(presetArea == null ? "" : presetArea);
        box.addView(etAreaName);

        TextView l2 = new TextView(this);
        l2.setText("2. 在这个区域要做什么动作？（比如：擦拭桌面 / 整理货架）");
        l2.setTextSize(13);
        l2.setTextColor(MUTED);
        l2.setPadding(0, dp(14), 0, dp(4));
        box.addView(l2);

        final EditText etTaskName = new EditText(this);
        etTaskName.setHint("擦拭桌面");
        etTaskName.setTextSize(20);
        box.addView(etTaskName);

        final AlertDialog dlg = new AlertDialog.Builder(this)
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
            if (a.isEmpty()) { toast("请填写区域名称"); etAreaName.requestFocus(); return; }
            if (n.isEmpty()) { toast("请填写任务名称"); etTaskName.requestFocus(); return; }
            Model.Task t;
            boolean firstArea = site.tasks.size() == 1 && "site".equals(site.tasks.get(0).type);
            if (firstArea) {
                // 门头/全景就并进这第一个区域，成为「任务1」
                t = site.tasks.get(0);
                t.type = "area";
                t.area = a;
                t.name = n;
                currentTask = 0;
                toast("任务1 = 门头/全景 + " + a);
            } else {
                t = new Model.Task();
                t.type = "area";
                t.area = a;
                t.name = n;
                site.tasks.add(t);
                currentTask = site.tasks.size() - 1;
            }
            currentShot = firstUndone(currentTask);
            if (currentShot < 0) currentShot = 0;
            store.saveQueue(site);
            syncTaskNamesAsync();
            try {
                showPage(2);
                renderShot();
            } catch (Exception e) {
                toast("打开拍摄页失败：" + e);
            }
            dlg.dismiss();
            } catch (Exception ex) {
                toast("添加区域失败：" + ex);
            }
        });
        if (presetArea != null && !presetArea.isEmpty()) etTaskName.requestFocus();
    }

    private void finishAll() {
        // 任务1 还缺区域/动作：先补填，否则 EgoOps 里会是一条没名字的任务
        if (!site.tasks.isEmpty()) {
            Model.Task t0 = site.tasks.get(0);
            boolean missing = (t0.area == null || t0.area.trim().isEmpty())
                    || (t0.name == null || t0.name.trim().isEmpty() || "任务1".equals(t0.name.trim()));
            if (missing) {
                toast("任务1 还没填区域和动作，请先补填");
                showAreaForTask1();
                return;
            }
        }
        for (int i = 1; i < site.tasks.size(); i++) {
            Model.Task t = site.tasks.get(i);
            if (t.name == null || t.name.trim().isEmpty() || ("任务" + (i + 1)).equals(t.name.trim())) {
                toast("还有任务没填动作名称，请补填");
                return;
            }
        }
        showPage(4);
        if (!uploading) startUpload();
    }

    // ---------------------------------------------------------------- 上传

    /** 传完的素材：生成缩略图留本地，原文件删掉（服务器已有） */
    private void archiveUploaded() {
        new Thread(() -> {
            try {
                File thumbDir = new File(getExternalFilesDir(null), "thumbs");
                if (!thumbDir.exists()) thumbDir.mkdirs();
                for (Model.Task t : site.tasks) {
                    for (Model.Media m : t.media) {
                        if (!"uploaded".equals(m.state)) continue;
                        File src = new File(m.path);
                        if (!src.exists()) continue;
                        File th = new File(thumbDir, src.getName() + ".jpg");
                        if (!th.exists()) {
                            if ("photo".equals(m.kind)) {
                                saveThumb(src, th, 320);
                            } else {
                                saveVideoThumb(src, th);
                            }
                        }
                        if (th.exists() && th.length() > 0) src.delete();
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void saveThumb(File src, File dst, int maxPx) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(src.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / sample > maxPx * 2) sample *= 2;
            android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = sample;
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(src.getAbsolutePath(), o2);
            if (bmp == null) return;
            FileOutputStream out = new FileOutputStream(dst);
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
            out.close();
            bmp.recycle();
        } catch (Exception ignored) {
        }
    }

    private void saveVideoThumb(File src, File dst) {
        try {
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            r.setDataSource(src.getAbsolutePath());
            android.graphics.Bitmap bmp = r.getFrameAtTime(0);
            r.release();
            if (bmp == null) return;
            FileOutputStream out = new FileOutputStream(dst);
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
            out.close();
            bmp.recycle();
        } catch (Exception ignored) {
        }
    }

    /** 回到上次没拍完/没传完的采集点 */
    private void resumeLastSite() {
        Model.Site saved = store.loadQueue();
        if (saved == null || (saved.tasks.isEmpty())) {
            toast("没有未完成的采集点");
            return;
        }
        site = saved;
        int firstTask = 0;
        for (int i = 0; i < site.tasks.size(); i++) {
            if (doneCount(i) < shotsOf(site.tasks.get(i)).length) { firstTask = i; break; }
        }
        currentTask = firstTask;
        currentShot = firstUndone(currentTask);
        if (currentShot < 0) {
            toast("这个采集点已经拍完，可直接上传");
            showPage(3);
            startUpload();
            return;
        }
        toast("继续：" + labelOf(site.tasks.get(currentTask)));
        renderShot();
        showPage(2);
    }

    /** 上次没传完：打开 App 自动接着传 */
    private void resumePendingUpload() {
        try {
            Model.Site saved = store.loadQueue();
            if (saved == null || saved.fileCount() == 0) return;
            int pending = 0;
            for (Model.Task t : saved.tasks) {
                for (Model.Media m : t.media) {
                    if (!"uploaded".equals(m.state)) pending++;
                }
            }
            if (pending == 0) return;
            final int n = pending;
            toast("上次还有 " + n + " 个文件没传完，自动继续");
            tvUpload.postDelayed(this::startUpload, 1500);
        } catch (Exception ignored) {
        }
    }

    /** 上次崩溃过就弹出来，方便把堆栈截图发给管理员 */
    private void showLastCrash() {
        try {
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "crash");
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
            new AlertDialog.Builder(this)
                    .setTitle("上次运行出错了")
                    .setMessage(sb.toString())
                    .setPositiveButton("知道了", (d, w) -> crashFile.delete())
                    .show();
        } catch (Exception ignored) {
        }
    }

    /** 启动时检查有没有新版本 */
    private void checkUpdate() {
        new Thread(() -> {
            try {
                org.json.JSONObject v = api.request("GET", "/api/app/version", null);
                final int code = v.optInt("versionCode", 0);
                final String name = v.optString("versionName", "");
                final String url = v.optString("url", "");
                final String notes = v.optString("notes", "");
                final long size = v.optLong("size", 0);
                if (code <= BuildConfigVersion.CODE || url.isEmpty()) return;
                if (code <= store.skippedVersion()) return;      // 用户选过「以后再说」
                runOnUiThread(() -> {
                    String msg = "发现新版本 " + name
                            + (size > 0 ? "（" + Math.round(size / 1048576.0 * 10) / 10.0 + "MB）" : "")
                            + (notes.isEmpty() ? "" : "\n\n" + notes)
                            + "\n\n要现在更新吗？";
                    new AlertDialog.Builder(this)
                            .setTitle("有新版本")
                            .setMessage(msg)
                            .setPositiveButton("立即更新", (d, w) -> downloadAndInstall(url, size))
                            .setNegativeButton("以后再说", (d, w) -> store.setSkippedVersion(code))
                            .show();
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    /** 下载 APK 并交给系统安装器 */
    private void downloadAndInstall(final String url, final long expectSize) {
        toast("开始下载新版本…");
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(120000);
                c.connect();
                if (c.getResponseCode() >= 300) throw new Exception("HTTP " + c.getResponseCode());
                int total = c.getContentLength();
                java.io.File out = new java.io.File(getCacheDir(), "egoops-update.apk");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                java.io.InputStream in = c.getInputStream();
                byte[] buf = new byte[64 * 1024];
                int n;
                long done = 0;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        final int pct = (int) (done * 100 / total);
                        runOnUiThread(() -> {
                            if (tvUpload != null) tvUpload.setText("正在下载新版本 " + pct + "%");
                        });
                    }
                }
                fos.close();
                in.close();
                long got = out.length();
                if (expectSize > 0 && got != expectSize) {
                    throw new Exception("大小不一致（收到 " + got + "，应为 " + expectSize + "）");
                }
                if (got < 20000) throw new Exception("文件太小（" + got + " 字节），可能没下全");
                byte[] head = new byte[4];
                java.io.FileInputStream fis = new java.io.FileInputStream(out);
                int hn = fis.read(head);
                fis.close();
                if (hn < 2 || head[0] != 'P' || head[1] != 'K') {
                    throw new Exception("下载到的不是 APK（可能被网关拦截）");
                }
                installApk(out);
            } catch (final Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> {
                    toast("下载失败：" + msg);
                    if (tvUpload != null) tvUpload.setText("下载失败，点这里重试");
                });
            }
        }).start();
    }

    private void installApk(java.io.File apk) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                runOnUiThread(() -> {
                    toast("请允许安装未知来源应用");
                    try {
                        startActivity(new android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) { }
                });
                return;
            }
            android.net.Uri uri = ApkProvider.uriFor(apk.getName());
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> {
                try {
                    startActivity(i);
                } catch (Exception e) {
                    toast("打不开安装器：" + e.getMessage());
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> toast("安装失败：" + e.getMessage()));
        }
    }

    private void startUpload() {
        if (uploading) return;
        if (site.fileCount() == 0) return;
        if (api.token.isEmpty()) {
            final String name = etCollector != null ? etCollector.getText().toString().trim() : store.name();
            if (name.isEmpty()) { tvUpload.setText("请先填写采集员姓名"); tvUpload.setTextColor(DANGER); return; }
            tvUpload.setText("正在连接服务器…");
            tvUpload.setTextColor(MUTED);
            new Thread(() -> {
                try {
                    loginNow(name);
                    runOnUiThread(this::startUpload);
                } catch (final Exception e) {
                    if (e instanceof Api.ApiException && "INVITE_REQUIRED".equals(((Api.ApiException) e).errorCode)) {
                        runOnUiThread(this::askInvite);
                        return;
                    }
                    final String reason2 = (e.getMessage() == null ? e.toString() : e.getMessage());
                    runOnUiThread(() -> {
                        tvUpload.setText("没连上服务器：" + reason2 + "（点这里重试）");
                        tvUpload.setTextColor(DANGER);
                    });
                }
            }).start();
            return;
        }
        uploading = true;
        tvUpload.setText("正在上传…");
        tvUpload.setTextColor(MUTED);
        UploadService.start(this, "准备上传…");
        new Thread(new Uploader(api, site, store, new Uploader.Listener() {
            @Override public void onStage(String msg) {
                runOnUiThread(() -> UploadService.update(MainActivity.this, msg, -1, 0, 0));
            }

            @Override public void onProgress(int done, int total, long sent, long totalBytes) {
                int pct = totalBytes > 0 ? (int) (sent * 100 / totalBytes) : 0;
                String s = "已上传 " + done + "/" + total + " 个文件 · " + pct + "%";
                UploadService.update(MainActivity.this, s, pct, done, total);
                tvUpload.setText(s);
                tvUpload.setTextColor(MUTED);
                if (tvDoneStatus != null) {
                    tvDoneStatus.setText(s);
                    tvDoneStatus.setTextColor(MUTED);
                }
            }

            @Override public void onDone(int files) {
                uploading = false;
                archiveUploaded();
                UploadService.stop(MainActivity.this, "上传完成，共 " + files + " 个文件");
                tvUpload.setText("全部上传完成 ✓  " + files + " 个文件");
                tvUpload.setTextColor(OK);
                if (tvDoneStatus != null) {
                    tvDoneStatus.setText("全部上传完成，共 " + files + " 个文件");
                    tvDoneStatus.setTextColor(OK);
                }
            }

            @Override public void onError(String message, String detail) {
                uploading = false;
                UploadService.stop(MainActivity.this, "上传中断，打开 App 可继续");
                tvUpload.setText("上传失败，点这里重试");
                tvUpload.setTextColor(DANGER);
                if (tvDoneStatus != null) {
                    tvDoneStatus.setText("还有文件没传完，点“上传失败，点这里重试”");
                    tvDoneStatus.setTextColor(DANGER);
                }
            }
        })).start();
    }

    private void resetForNext() {
        if (uploading) { toast("还在上传，等进度跑完再拍下一个"); return; }
        site = new Model.Site();
        store.clearQueue();
        etStoreName.setText("");
        currentTask = 0;
        currentShot = 0;
        tvDoneStatus.setText("正在自动上传，请稍等…");
        tvDoneStatus.setTextColor(MUTED);
        showPage(1);
    }

    // ---------------------------------------------------------------- 账号

    private void autoLogin() {
        if (!api.token.isEmpty()) return;
        final String name = store.name();
        if (name.isEmpty()) return;
        new Thread(() -> {
            try {
                loginNow(name);
                runOnUiThread(() -> {
                    if (tvUpload != null) tvUpload.setText("已连接服务器");
                    loadScenes();
                });
            } catch (final Exception e) {
                if (e instanceof Api.ApiException && "INVITE_REQUIRED".equals(((Api.ApiException) e).errorCode)) {
                    runOnUiThread(this::askInvite);
                    return;
                }
                final String reason = (e.getMessage() == null ? e.toString() : e.getMessage());
                runOnUiThread(() -> {
                    if (tvUpload != null) {
                        tvUpload.setText("没连上服务器：" + reason + "（点这里重试）");
                        tvUpload.setTextColor(DANGER);
                    }
                });
            }
        }).start();
    }

    /** 邀请码：填对之后就知道拍的是哪个厂商的店 */
    private void askInvite() {
        final EditText et = new EditText(this);
        et.setText(store.invite());
        et.setHint("管理员给的邀请码，例如 ZJMEIYI");
        et.setTextSize(20);
        et.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("填写邀请码")
                .setMessage("邀请码决定这批照片属于哪个厂商。\n填对后会在拍摄页显示厂商名称。")
                .setView(et)
                .setCancelable(false)
                .setPositiveButton("确定", (d, w) -> {
                    final String code = et.getText().toString().trim().toUpperCase();
                    if (code.isEmpty()) {
                        toast("请填邀请码");
                        return;
                    }
                    checkInviteThen(code);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 邀请码由管理员和采集员事先约定，服务器不校验，这里只检查格式并记住 */
    private void checkInviteThen(final String code) {
        if (code.length() < 4) {
            toast("邀请码太短，至少 4 位");
            askInvite();
            return;
        }
        store.setInvite(code);
        refreshVendorLabel();
        loadScenes(code);
        api.token = "";
        store.setToken("");
        toast("邀请码已记录：" + code);
        startUpload();
    }

    /** 拍摄页顶部显示当前邀请码 */
    private void refreshVendorLabel() {
        if (tvVendor == null) return;
        String code = store.invite();
        if (code == null || code.isEmpty()) {
            tvVendor.setText("邀请码：未填写（点这里填）");
            tvVendor.setTextColor(DANGER);
        } else {
            tvVendor.setText("邀请码：" + code + "　（点这里可改）");
            tvVendor.setTextColor(MUTED);
        }
        if (etInvite != null && !code.isEmpty() && etInvite.getText().toString().trim().isEmpty()) {
            etInvite.setText(code);
        }
    }

    private void loginNow(String name) throws Exception {
        org.json.JSONObject body = new org.json.JSONObject();
        body.put("name", name);
        body.put("phone", store.phone());
        body.put("invite", store.invite());
        org.json.JSONObject r = api.request("POST", "/api/auth/login", body);
        api.token = r.optString("token");
        store.setToken(api.token);
        store.setName(name);
        runOnUiThread(this::refreshVendorLabel);
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);

        final EditText srv = new EditText(this);
        srv.setText(store.server());
        srv.setTextSize(15);
        box.addView(labelText("服务器地址"));
        box.addView(srv);

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    store.setServer(srv.getText().toString().trim());
                    api.base = store.server();
                    api.token = "";
                    store.setToken("");
                    autoLogin();
                    startGps();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private TextView labelText(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    // ---------------------------------------------------------------- 工具

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
