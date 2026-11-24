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

import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/**
 * 采集点拍照 v9
 * 结构对齐「申请采集点」：采集点 → 任务（可多个）→ 每个任务 4 项拍摄。
 * 每屏：内容可滚动 + 主按钮固定在底部。
 */
public class MainActivity extends Activity {

    public static final int REQ_PHOTO = 1001;
    public static final int REQ_PERM_ALL = 1100;
    public static final int REQ_VIDEO = 1002;
    public static final int REQ_PICK_PHOTO = 1003;
    public static final int REQ_PICK_VIDEO = 1004;
    public static final int REQ_PICK_MULTI = 1006;
    public static final int REQ_PERM = 1005;

    public static final int BG = Color.parseColor("#F2F5FA");
    public static final int CARD = Color.WHITE;
    public static final int PRIMARY = Color.parseColor("#2F6FED");
    public static final int OK = Color.parseColor("#1F9D55");
    public static final int TEXT = Color.parseColor("#17202E");
    public static final int MUTED = Color.parseColor("#6B7A90");
    public static final int DANGER = Color.parseColor("#C0392B");

    public Store store;
    public Api api;
    public Model.Site site = new Model.Site();

    public LinearLayout pageStart, pageShoot, pageTaskDone, pageDone;
    public EditText etStoreName, etCollector, etArea, etStreet;
    public android.widget.Spinner spProv, spCity, spDist;
    public String[] provArr = new String[0];
    public String[][] cityArr = new String[0][];
    public String[][][] distArr = new String[0][][];
    public android.widget.Spinner spCat, spScene2;
    public EditText etSceneSearch;
    public final List<String[]> sceneItems = new ArrayList<String[]>();   // {path, label, category}
    public final List<String> categories = new ArrayList<String>();
    public final List<Integer> shownIndex = new ArrayList<Integer>();
    public LinearLayout shotArea;
    public TextView tvStep, tvDots, tvUpload, tvDoneStatus, tvTaskDoneTitle, tvTaskDoneSub, tvVendor;
    public EditText etInvite;
    public String lastSceneInvite = "";
    public volatile String gpsAddress = "";
    public volatile String gpsCoords = "";
    public volatile java.io.File mapSnapFile = null;
    public boolean awaitingWatermarkImport = false;
    public int slideDir = 1;   // 1=下一项 -1=上一项
    public long wmLaunchAt = 0;

    public boolean uploading = false;
    public int currentTask = 0;
    public int currentShot = 0;
    public String pendingShot = "";
    public Uri tempUri;

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        api = new Api(store.server());
        api.token = store.token();

        CrashReporter.installCrashReporter(this);

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
        AccountManager.autoLogin(this);
        Dialogs.showLastCrash(this);
        UploadCoordinator.resumePendingUpload(this);
        Permissions.askAllPermissions(this);
        if (!BuildConfigVersion.NO_CAPTURE) {
            tvUpload.postDelayed(() -> AutoUpdate.checkUpdate(this), 3000);
        }
        SceneCatalog.loadScenes(this);          // 公开接口，不需要登录就能拉
        RegionCatalog.loadRegions(this);         // 省市区数据（本地资源）
    }

    // ---------------------------------------------------------------- 界面

    public View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        pageStart = StartPage.buildStartPage(this);
        pageShoot = ShootPage.buildShootPage(this);
        pageTaskDone = TaskDonePage.buildTaskDonePage(this);
        pageDone = DonePage.buildDonePage(this);

        root.addView(pageStart, full());
        root.addView(pageShoot, full());
        root.addView(pageTaskDone, full());
        root.addView(pageDone, full());
        return root;
    }

    public FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public int curPage = 1;

    public void hideKeyboard() {
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

    public void showPage(int n) {
        curPage = n;
        if (n != 1) hideKeyboard();
        pageStart.setVisibility(n == 1 ? View.VISIBLE : View.GONE);
        pageShoot.setVisibility(n == 2 ? View.VISIBLE : View.GONE);
        pageTaskDone.setVisibility(n == 3 ? View.VISIBLE : View.GONE);
        pageDone.setVisibility(n == 4 ? View.VISIBLE : View.GONE);
    }

    public LinearLayout pageShell() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(BG);
        return p;
    }

    public ScrollView scrollWrap(LinearLayout content) {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sc;
    }

    public LinearLayout bottomBar() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackgroundColor(BG);
        b.setPadding(dp(20), dp(10), dp(20), dp(20));
        return b;
    }

    public Button primaryButton(String text, int heightDp, int color) {
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

    public Button secondaryButton(String text, int heightDp) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERM && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && !pendingShot.isEmpty()) {
            String shot = pendingShot;
            pendingShot = "";
            for (String[] s : Shots.shotsOf(site.tasks.get(currentTask))) if (s[0].equals(shot)) { CaptureFlow.captureShot(this, shot, s[3]); return; }
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
                tvStep.postDelayed(() -> CaptureFlow.autoGrabLatest(this, shot, kind), 500);
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
                CaptureFlow.ingest(this, data.getData(), kind.equals("video"));
                return;
            }
            if (clip != null) CaptureFlow.ingestMultiple(this, clip);
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
        CaptureFlow.ingest(this, src, video);
    }

    // ---------------------------------------------------------------- 工具

    public GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    public void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
