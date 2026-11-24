package com.egoops.collect.capture;

import com.egoops.collect.*;
import com.egoops.collect.diag.*;
import com.egoops.collect.location.*;
import com.egoops.collect.perm.*;
import com.egoops.collect.queue.*;
import com.egoops.collect.settings.*;
import com.egoops.collect.state.*;
import com.egoops.collect.ui.*;

/** 素材质检（模糊/过暗/视频过短）（拆分自 MainActivity.java，见 docs/SPLIT.md） */
public final class QualityCheck {

    private QualityCheck() {
    }

    /** 质检：模糊 / 过暗 / 视频太短 返回问题描述，合格返回 null */
    public static String checkQuality(String path, boolean video) {
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
                    int c = QualityCheck.gray(px[y * w + x]);
                    int lap = 4 * c - QualityCheck.gray(px[(y - 1) * w + x]) - QualityCheck.gray(px[(y + 1) * w + x])
                            - QualityCheck.gray(px[y * w + x - 1]) - QualityCheck.gray(px[y * w + x + 1]);
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

    public static int gray(int c) {
        return (int) (((c >> 16) & 0xFF) * 0.299 + ((c >> 8) & 0xFF) * 0.587 + (c & 0xFF) * 0.114);
    }
}
