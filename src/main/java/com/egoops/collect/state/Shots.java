package com.egoops.collect.state;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.ui.*;

/** 拍摄项矩阵与进度判定：SHOTS_SITE / SHOTS_TASK1 / SHOTS_AREA、GUIDE、shotsOf、hasShot、firstUndone、doneCount（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class Shots {

    private Shots() {
    }

    /** 任务1（采集点级）：门头照 + 全景照片 + 全景视频 */
    public static final String[][] SHOTS_SITE = {
            {"door", "门头照", "正对门头，把招牌和门都拍进去（用广角）", "photo"},
            {"panophoto", "全景照片", "站在店中央，用广角把整个空间拍全", "photo"},
            {"panovideo", "全景视频", "原地慢慢转一圈，用广角（15 秒左右）", "video"},
    };

    /** 任务1：门头 3 项 + 第一个区域 6 项，合起来 9 项 */
    public static final String[][] SHOTS_TASK1 = {
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
    public static final String[][] SHOTS_AREA = {
            {"corner1", "对角照片 1", "站在区域一个角，用广角斜着拍对角", "photo"},
            {"corner2", "对角照片 2", "走到对角，反方向再拍一张", "photo"},
            {"work1", "任务照片 1", "拍这个区域要采集的内容", "photo"},
            {"work2", "任务照片 2", "换个角度再拍一张", "photo"},
            {"clip1", "任务视频 1", "对准要采集的动作，用广角录 10 秒", "video"},
            {"clip2", "任务视频 2", "换个角度再录 10 秒", "video"},
    };

    /** 每一项怎么拍（中老年用户看得懂的短句） */
    public static final String[][] GUIDE = {
        {"门头", "站在马路对面或门口正前方，把整块招牌拍进去，不要只拍一角；避免逆光和路人正脸。"},
        {"全景", "站在门店正中央，手机横着拿稳，原地慢慢转一整圈，货架和通道都要扫到。"},
        {"对角", "走到店铺的一个角，对着斜对面的角拍，让整个空间的对角线出现在画面里。"},
        {"任务照", "先拍你要动那个东西的全貌，再拍细节（手和物体都要清楚），别贴太近。"},
        {"任务视频", "从头到尾一遍做完，动作慢一点、稳一点，手和物体始终在画面里，不要中途停下。"},
        {"全景照片", "站在区域正中央，手机切到广角（0.5x），举到胸口高度，把四个角都框进画面，再按快门。"},
    };

    public static String[][] shotsOf(Model.Task t) {
        if (t == null) return SHOTS_AREA;
        if ("task1".equals(t.type)) return SHOTS_TASK1;
        if ("site".equals(t.type)) return SHOTS_SITE;
        return SHOTS_AREA;
    }

    public static String guideOf(String shot) {
        String s = shot == null ? "" : shot;
        for (String[] g : GUIDE) if (s.contains(g[0])) return g[1];
        return "对准要拍的东西，拿稳手机，光线充足再按快门。";
    }

    public static String labelOf(Model.Task t) {
        if (t.area != null && !t.area.isEmpty()) return t.area + "：" + t.name;
        return t.name;
    }

    public static boolean hasShot(MainActivity host, int taskIndex, int shotIndex) {
        if (taskIndex >= host.site.tasks.size()) return false;
        Model.Task t = host.site.tasks.get(taskIndex);
        String[][] shots = Shots.shotsOf(t);
        if (shotIndex >= shots.length) return false;
        String key = shots[shotIndex][0];
        for (Model.Media m : t.media) if (key.equals(m.shot)) return true;
        return false;
    }

    public static int firstUndone(MainActivity host, int taskIndex) {
        int n = Shots.shotsOf(host.site.tasks.get(taskIndex)).length;
        for (int i = 0; i < n; i++) if (!Shots.hasShot(host, taskIndex, i)) return i;
        return n;
    }

    public static int doneCount(MainActivity host, int taskIndex) {
        int n = 0;
        int total = Shots.shotsOf(host.site.tasks.get(taskIndex)).length;
        for (int i = 0; i < total; i++) if (Shots.hasShot(host, taskIndex, i)) n++;
        return n;
    }

    /** 第一个还没拍完的任务下标；全拍完返回 -1 */
    public static int firstIncompleteTask(MainActivity host) {
        for (int i = 0; i < host.site.tasks.size(); i++) {
            Model.Task t = host.site.tasks.get(i);
            if (Shots.firstUndone(host, i) < Shots.shotsOf(t).length) return i;
        }
        return -1;
    }

    /** 取该任务最后一拍的项（用于往回切） */
    public static int firstUndoneOrLast(MainActivity host, int taskIndex) {
        int last = Shots.shotsOf(SiteFlow.task(host, taskIndex)).length - 1;
        for (int i = 0; i <= last; i++) if (Shots.hasShot(host, taskIndex, i)) last = Math.max(last, last);
        return Math.max(0, last);
    }
}
