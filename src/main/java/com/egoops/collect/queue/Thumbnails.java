package com.egoops.collect.queue;

import java.io.File;
import java.io.FileOutputStream;

import com.egoops.collect.*;
import com.egoops.collect.capture.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 缩略图生成（照片/视频）（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class Thumbnails {

    private Thumbnails() {
    }

    public static void saveThumb(File src, File dst, int maxPx) {
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

    public static void saveVideoThumb(File src, File dst) {
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
}
