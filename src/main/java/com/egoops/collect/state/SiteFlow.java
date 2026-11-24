package com.egoops.collect.state;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.ui.*;

/** 采集主流程：开始拍摄、切换拍摄项、恢复现场、收尾、重置（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class SiteFlow {

    private SiteFlow() {
    }

    public static void beginShooting(MainActivity host) {
        String collector = host.etCollector.getText().toString().trim();
        if (collector.isEmpty()) {
            host.toast("请先填写采集员姓名");
            host.etCollector.requestFocus();
            return;
        }

        String invite = host.etInvite == null ? host.store.invite() : host.etInvite.getText().toString().trim();
        invite = invite == null ? "" : invite.toUpperCase();
        if (invite.isEmpty()) {
            host.toast("请填写邀请码");
            if (host.etInvite != null) host.etInvite.requestFocus();
            return;
        }
        host.store.setInvite(invite);

        String typed = host.etStoreName.getText().toString().trim();
        if (typed.isEmpty()) {
            host.toast("请先填写门店名称");
            host.etStoreName.requestFocus();
            return;
        }

        int pi = host.spProv.getSelectedItemPosition();
        int ci = host.spCity.getSelectedItemPosition();
        int di = host.spDist.getSelectedItemPosition();
        if (pi <= 0 || ci < 0 || di < 0) {
            host.toast("请先选择省 / 市 / 区");
            return;
        }
        String street = host.etStreet.getText().toString().trim();
        if (street.isEmpty()) {
            host.toast("请填写街道门牌号");
            host.etStreet.requestFocus();
            return;
        }
        String provName = host.provArr[pi];
        String cityName = host.cityArr[pi - 1][ci];
        String distName = host.distArr[pi - 1][ci][di];
        String addr = provName + ("市辖区".equals(cityName) ? "" : cityName) + distName + street;

        if (!invite.equals(host.lastSceneInvite)) {
            host.toast("邀请码变了，正在加载该厂商的分类，稍等再点「开始拍摄」");
            SceneCatalog.loadScenes(host, invite);
            return;
        }
        if (host.sceneItems.isEmpty()) {
            host.toast("场景分类还没准备好，请稍等");
            SceneCatalog.loadScenes(host, invite);
            return;
        }
        if (host.spCat.getSelectedItemPosition() <= 0) {
            host.toast("请先选大类（上面第一个）");
            host.spCat.performClick();
            return;
        }
        int p2 = host.spScene2.getSelectedItemPosition();
        if (p2 < 0 || p2 >= host.shownIndex.size()) {
            host.toast("请再选具体场景（下面第二个）");
            host.spScene2.performClick();
            return;
        }

        String areaTxt = host.etArea.getText().toString().trim();
        double areaVal = 0;
        try { areaVal = areaTxt.isEmpty() ? 0 : Double.parseDouble(areaTxt); } catch (Exception e) { areaVal = 0; }
        if (!(areaVal > 0)) {
            host.toast("请填写场地面积（平方米）");
            host.etArea.requestFocus();
            return;
        }

        if (!collector.equals(host.store.name())) {
            host.store.setName(collector);
            host.store.setToken("");
            host.api.token = "";
        }
        if (!host.site.name.isEmpty() && !host.site.name.equals(typed)) {
            host.site = new Model.Site();
            host.store.clearQueue();
        }
        host.site.name = typed;
        host.site.address = addr;
        host.site.invite = invite;
        String[] chosen = host.sceneItems.get(host.shownIndex.get(p2));
        host.site.scene = chosen[0];
        host.site.sceneLabel = chosen[1];
        host.site.area = areaVal;
        if (host.site.tasks.isEmpty()) {
            Model.Task t = new Model.Task();
            t.type = "task1";
            t.name = "任务1";
            host.site.tasks.add(t);
        }
        try {
            int t = Shots.firstIncompleteTask(host);
            if (t < 0) {
                host.showPage(4);
                if (!host.uploading) UploadCoordinator.startUpload(host);
                return;
            }
            host.currentTask = t;
            host.currentShot = Shots.firstUndone(host, t);
            host.showPage(2);
            ShotRenderer.renderShot(host);
        } catch (Exception e) {
            host.toast("开始拍摄失败：" + e);
            return;
        }
        LocationTracker.startGps(host);
        if (host.api.token.isEmpty()) AccountManager.autoLogin(host);
    }

    /** 左右滑动切换拍摄项：delta = -1 上一项，+1 下一项 */
    public static void stepShot(MainActivity host, int delta) {
        if (host.site == null || host.site.tasks.isEmpty()) return;
        Model.Task task = SiteFlow.task(host, host.currentTask);
        String[][] shots = Shots.shotsOf(task);
        int target = host.currentShot + delta;

        // 越界就跨任务
        if (target < 0) {
            if (host.currentTask > 0) {
                host.currentTask--;
                host.currentShot = Shots.shotsOf(SiteFlow.task(host, host.currentTask)).length - 1;
                host.currentShot = Shots.firstUndoneOrLast(host, host.currentTask);
                ShotRenderer.renderShot(host);
                host.toast("回到上一项");
            } else {
                host.toast("已经是第一项了");
            }
            return;
        }
        if (target >= shots.length) {
            if (host.currentTask + 1 < host.site.tasks.size()) {
                host.currentTask++;
                host.currentShot = 0;
                ShotRenderer.renderShot(host);
                host.toast("下一项");
            } else {
                host.toast("已经是最后一项了");
            }
            return;
        }

        host.currentShot = target;
        host.slideDir = delta;
        ShotRenderer.renderShot(host);
        host.toast(delta < 0 ? "上一项" : "下一项");
    }

    /** 回到上次没拍完/没传完的采集点 */
    public static void resumeLastSite(MainActivity host) {
        Model.Site saved = host.store.loadQueue();
        if (saved == null || (saved.tasks.isEmpty())) {
            host.toast("没有未完成的采集点");
            return;
        }
        host.site = saved;
        int firstTask = 0;
        for (int i = 0; i < host.site.tasks.size(); i++) {
            if (Shots.doneCount(host, i) < Shots.shotsOf(host.site.tasks.get(i)).length) { firstTask = i; break; }
        }
        host.currentTask = firstTask;
        host.currentShot = Shots.firstUndone(host, host.currentTask);
        if (host.currentShot < 0) {
            host.toast("这个采集点已经拍完，可直接上传");
            host.showPage(3);
            UploadCoordinator.startUpload(host);
            return;
        }
        host.toast("继续：" + Shots.labelOf(host.site.tasks.get(host.currentTask)));
        ShotRenderer.renderShot(host);
        host.showPage(2);
    }

    public static void finishAll(MainActivity host) {
        // 任务1 还缺区域/动作：先补填，否则 EgoOps 里会是一条没名字的任务
        if (!host.site.tasks.isEmpty()) {
            Model.Task t0 = host.site.tasks.get(0);
            boolean missing = (t0.area == null || t0.area.trim().isEmpty())
                    || (t0.name == null || t0.name.trim().isEmpty() || "任务1".equals(t0.name.trim()));
            if (missing) {
                host.toast("任务1 还没填区域和动作，请先补填");
                Dialogs.showAreaForTask1(host);
                return;
            }
        }
        for (int i = 1; i < host.site.tasks.size(); i++) {
            Model.Task t = host.site.tasks.get(i);
            if (t.name == null || t.name.trim().isEmpty() || ("任务" + (i + 1)).equals(t.name.trim())) {
                host.toast("还有任务没填动作名称，请补填");
                return;
            }
        }
        host.showPage(4);
        if (!host.uploading) UploadCoordinator.startUpload(host);
    }

    public static void resetForNext(MainActivity host) {
        if (host.uploading) { host.toast("还在上传，等进度跑完再拍下一个"); return; }
        host.site = new Model.Site();
        host.store.clearQueue();
        host.etStoreName.setText("");
        host.currentTask = 0;
        host.currentShot = 0;
        host.tvDoneStatus.setText("正在自动上传，请稍等…");
        host.tvDoneStatus.setTextColor(MainActivity.MUTED);
        host.showPage(1);
    }

    public static Model.Task task(MainActivity host, int i) {
        return host.site.tasks.get(i);
    }

    public static void keepName(MainActivity host) {
        if (host.etStoreName != null) {
            String n = host.etStoreName.getText().toString().trim();
            if (!n.isEmpty()) host.site.name = n;
        }
    }

    /** 任务名/区域改动后同步到服务器（后台，不打断拍摄） */
    public static void syncTaskNamesAsync(MainActivity host) {
        try {
            if (host.site.siteId == null || host.site.siteId.isEmpty()) return;
            new Thread(() -> {
                try {
                    new Uploader(host.api, host.site, host.store, null).syncTaskNames();
                } catch (Exception ignored) {
                }
            }).start();
        } catch (Exception ignored) {
        }
    }
}
