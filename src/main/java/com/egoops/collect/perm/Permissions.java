package com.egoops.collect.perm;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 运行时权限申请（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class Permissions {

    private Permissions() {
    }

    /** 相机 / 录音 / 读媒体 / 通知（上传进度的通知要它） */
    public static void askAllPermissions(MainActivity host) {
        if (Build.VERSION.SDK_INT < 23) return;
        java.util.List<String> need = new java.util.ArrayList<>();
        if (host.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) {
            need.add("android.permission.READ_MEDIA_IMAGES");
            need.add("android.permission.READ_MEDIA_VIDEO");
        } else {
            need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            if (host.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
                need.add("android.permission.POST_NOTIFICATIONS");
            if (host.checkSelfPermission("android.permission.READ_MEDIA_IMAGES") != PackageManager.PERMISSION_GRANTED)
                need.add("android.permission.READ_MEDIA_IMAGES");
            if (host.checkSelfPermission("android.permission.READ_MEDIA_VIDEO") != PackageManager.PERMISSION_GRANTED)
                need.add("android.permission.READ_MEDIA_VIDEO");
        }
        if (!need.isEmpty()) host.requestPermissions(need.toArray(new String[0]), MainActivity.REQ_PERM_ALL);
    }
}
