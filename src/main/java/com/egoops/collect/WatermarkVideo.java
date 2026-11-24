package com.egoops.collect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 视频烧录水印：解码 → OpenGL 叠加水印 → 重新编码 → 混音。
 *
 * 思路（Grafika 那套）：
 *   解码器输出到 SurfaceTexture → 当成 GL 纹理
 *   → 画到编码器的输入 Surface 上（同时把水印纹理叠上去）
 *   → 编码器出 H.264 → MediaMuxer 带上原音频写新文件
 */
public class WatermarkVideo {

    private static final int TIMEOUT_US = 10000;

    private final Bitmap overlay;
    private final int outW;
    private final int outH;

    private MediaCodec decoder;
    private MediaCodec encoder;
    private SurfaceTexture surfaceTexture;
    private Surface decoderSurface;
    private MediaMuxer muxer;
    private int videoTrack = -1;
    private int audioTrack = -1;
    private boolean muxerStarted;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    private int texProgram;
    private int texId = -1;
    private int overlayTexId = -1;
    private FloatBuffer vertexBuf;
    private int aPosition;
    private int aTexCoord;
    private int uTexture;
    private long frameTimeUs;

    /** 水印：按参考样式复刻（打卡徽章 + 渐变时间块 + 地址两行 + 日期 + 验证行） */
    public static void drawStamp(Canvas c, int w, int h, String addr, long whenMs, String antiCode, Bitmap map) {
        float u = w / 1080f;
        float F = 38 * u;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (whenMs > 0) cal.setTimeInMillis(whenMs);
        String date = String.format("%04d.%02d.%02d", cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH));
        char[] wk = "日一二三四五六".toCharArray();
        String week = "星期" + wk[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1];
        String hhmm = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE));

        float chipH = 2.15f * F;
        float gapChip = 0.93f * F;
        float lineH = 1.30f * F;
        float offDate = 1.62f * F;
        float offSmall = 1.72f * F;
        float margin = 0.55f * F;
        float barW = 0.40f * F;
        float maxW = Math.min(w - margin - 0.40f * F, 0.74f * w);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD));
        p.setTextSize(F);
        java.util.List<String> lines = splitAddress(p, addr, maxW);

        float blockH = chipH + gapChip + lines.size() * lineH + offDate + offSmall;
        float top = h - margin - blockH;

        // ---------- 打卡徽章 ----------
        p.setTextSize(1.12f * F);
        float tagPad = 0.16f * F;
        float tagW = p.measureText("打卡") + tagPad * 2;
        android.graphics.RectF r1 = new android.graphics.RectF(margin, top, margin + tagW, top + chipH);
        Paint yellow = new Paint(Paint.ANTI_ALIAS_FLAG);
        yellow.setColor(Color.parseColor("#F6C23C"));
        c.drawRoundRect(r1, 0.12f * F, 0.12f * F, yellow);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1, 0.04f * F));
        edge.setColor(0x78FFFFFF);
        c.drawRoundRect(r1, 0.12f * F, 0.12f * F, edge);

        p.setColor(Color.parseColor("#0F0F0F"));
        drawV(c, p, "打卡", margin + tagPad, top, chipH);

        // ---------- 时间块（上浅下深渐变 + 浅描边） ----------
        p.setTextSize(1.55f * F);
        float timePad = 0.08f * F;
        float timeW = p.measureText(hhmm) + timePad * 2;
        float bx = margin + tagW + 0.08f * F;
        android.graphics.RectF r2 = new android.graphics.RectF(bx, top, bx + timeW, top + chipH);
        Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
        chip.setShader(new android.graphics.LinearGradient(bx, top, bx, top + chipH,
                Color.parseColor("#B0BCCB"), Color.parseColor("#828EA3"),
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(r2, 0.14f * F, 0.14f * F, chip);
        Paint chipEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        chipEdge.setStyle(Paint.Style.STROKE);
        chipEdge.setStrokeWidth(Math.max(1, 0.05f * F));
        chipEdge.setColor(0xE6F0F5FA);
        c.drawRoundRect(r2, 0.14f * F, 0.14f * F, chipEdge);

        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        num.setTypeface(p.getTypeface());
        num.setTextSize(1.55f * F);
        num.setStyle(Paint.Style.STROKE);
        num.setStrokeWidth(Math.max(2, 0.03f * F));
        num.setColor(0xC81E2C3C);
        drawV(c, num, hhmm, bx + timePad, top, chipH);
        num.setStyle(Paint.Style.FILL);
        num.setColor(Color.WHITE);
        drawV(c, num, hhmm, bx + timePad, top, chipH);

        // ---------- 黄竖条 ----------
        float barTop = top + chipH + gapChip * 0.55f;
        float barBot = barTop + lines.size() * lineH + offDate * 0.62f;
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(Color.parseColor("#F6C23C"));
        c.drawRect(margin + 0.06f * F, barTop, margin + 0.06f * F + barW, barBot, bar);

        // ---------- 地址 ----------
        float x = margin + 0.06f * F + barW + 0.36f * F;
        float y = barTop + 0.10f * F;
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setTypeface(p.getTypeface());
        txt.setTextSize(F);
        txt.setShadowLayer(Math.max(1, 0.04f * F), 1, 1, 0xA5000000);
        txt.setColor(Color.WHITE);
        float off = Math.max(1.5f, 0.045f * F);
        Paint dark = new Paint(txt);
        dark.setColor(0xAA000000);
        dark.clearShadowLayer();
        for (String ln : lines) {
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    c.drawText(ln, x + dx * off, y + F * 0.86f + dy * off, dark);
                }
            }
            c.drawText(ln, x, y + F * 0.86f, txt);
            y += lineH;
        }

        // ---------- 日期 ----------
        y += offDate - lineH + 0.10f * F;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                c.drawText(date + " " + week, x + dx * off, y + F * 0.86f + dy * off, dark);
            }
        }
        c.drawText(date + " " + week, x, y + F * 0.86f, txt);

        // ---------- 验证行 ----------
        y += offSmall - 0.15f * F;
        float ic = 0.72f * F;
        shield(c, x, y, ic);
        Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        small.setTypeface(p.getTypeface());
        small.setTextSize(0.78f * F);
        small.setColor(Color.parseColor("#F2F2F2"));
        small.setShadowLayer(Math.max(1, 0.03f * F), 1, 1, 0x99000000);
        c.drawText("采集水印相机已验证 | 时间地点真实", x + ic + 0.30f * F,
                y + 0.78f * F * 0.9f, small);

        if (antiCode != null && !antiCode.isEmpty()) {
            Paint code = new Paint(Paint.ANTI_ALIAS_FLAG);
            code.setTypeface(p.getTypeface());
            code.setTextSize(0.62f * F);
            code.setColor(0xCCFFFFFF);
            code.setShadowLayer(Math.max(1, 0.03f * F), 1, 1, 0x99000000);
            String line = "防伪 " + antiCode;
            float cw = code.measureText(line);
            c.drawText(line, w - margin - cw, h - margin * 0.55f, code);
        }

        MapSnap.draw(c, map, w, h, margin);
    }

    /** 盾形勾（矢量画，不依赖字体） */
    private static void shield(Canvas c, float x, float y, float size) {
        float w = size;
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x + w * 0.5f, y);
        path.lineTo(x + w * 0.95f, y + w * 0.18f);
        path.lineTo(x + w * 0.95f, y + w * 0.55f);
        path.lineTo(x + w * 0.5f, y + w);
        path.lineTo(x + w * 0.05f, y + w * 0.55f);
        path.lineTo(x + w * 0.05f, y + w * 0.18f);
        path.close();

        Paint s1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        s1.setStyle(Paint.Style.STROKE);
        s1.setStrokeWidth(Math.max(2, w * 0.12f));
        s1.setStrokeJoin(Paint.Join.ROUND);
        s1.setColor(Color.parseColor("#F2F2F2"));
        c.drawPath(path, s1);

        android.graphics.Path tick = new android.graphics.Path();
        tick.moveTo(x + w * 0.30f, y + w * 0.50f);
        tick.lineTo(x + w * 0.45f, y + w * 0.66f);
        tick.lineTo(x + w * 0.72f, y + w * 0.33f);
        s1.setStrokeWidth(Math.max(2, w * 0.13f));
        c.drawPath(tick, s1);
    }

    /** 在给定高度内垂直居中绘制 */
    private static void drawV(Canvas c, Paint p, String text, float x, float top, float h) {
        Paint.FontMetrics fm = p.getFontMetrics();
        float baseline = top + (h - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        c.drawText(text, x, baseline, p);
    }

    /** 地址断行：第一行尽量排满，在 区/街道/路/号 等处断开 */
    private static java.util.List<String> splitAddress(Paint p, String text, float maxW) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null) text = "";
        text = text.trim();
        if (text.isEmpty()) {
            out.add("（未获取到定位）");
            return out;
        }
        if (p.measureText(text) <= maxW) {
            out.add(text);
            return out;
        }
        String boundary = "区街道路号镇乡村巷弄段";
        int cut = -1;
        for (int i = 0; i < text.length(); i++) {
            if (boundary.indexOf(text.charAt(i)) < 0) continue;
            String a = text.substring(0, i + 1);
            String b = text.substring(i + 1);
            if (b.isEmpty()) continue;
            if (p.measureText(a) <= maxW && p.measureText(b) <= maxW) cut = i + 1;
        }
        if (cut > 0) {
            out.add(text.substring(0, cut));
            out.add(text.substring(cut));
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (cur.length() > 0 && p.measureText(cur.toString() + ch) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder();
                if (out.size() == 1) break;
            }
            cur.append(ch);
        }
        String rest = text.substring(out.isEmpty() ? 0 : out.get(0).length());
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    /** 生成和视频同尺寸的透明水印图层 */
    public static Bitmap makeOverlay(String addr, String store, String who, int w, int h, long whenMs, String antiCode, Bitmap map) {
        try {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            drawStamp(c, w, h, addr, whenMs, antiCode, map);
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    public WatermarkVideo(Bitmap overlay, int width, int height) {
        this.overlay = overlay;
        this.outW = width & ~1;
        this.outH = height & ~1;
    }

    /** 返回 true 表示成功写出新文件 */
    public boolean burn(File in, File out) {
        MediaExtractor extractor = new MediaExtractor();
        long startMs = System.currentTimeMillis();
        try {
            extractor.setDataSource(in.getAbsolutePath());

            int videoTrackIndex = -1;
            int audioTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i;
                } else if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                }
            }
            if (videoTrackIndex < 0) return false;

            MediaFormat srcFormat = extractor.getTrackFormat(videoTrackIndex);
            String mime = srcFormat.getString(MediaFormat.KEY_MIME);
            int srcW = srcFormat.getInteger(MediaFormat.KEY_WIDTH);
            int srcH = srcFormat.getInteger(MediaFormat.KEY_HEIGHT);
            int rotation = 0;
            if (srcFormat.containsKey("rotation-degrees")) {
                rotation = srcFormat.getInteger("rotation-degrees");
            }
            int w = outW > 0 ? outW : (srcW & ~1);
            int h = outH > 0 ? outH : (srcH & ~1);
            int fps = srcFormat.containsKey(MediaFormat.KEY_FRAME_RATE)
                    ? srcFormat.getInteger(MediaFormat.KEY_FRAME_RATE) : 30;
            if (fps <= 0 || fps > 60) fps = 30;

            // ---------- 编码器 ----------
            MediaFormat encFormat = MediaFormat.createVideoFormat("video/avc", w, h);
            encFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            encFormat.setInteger(MediaFormat.KEY_BIT_RATE, Math.min(12_000_000, w * h * fps / 4));
            encFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            encFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            encoder = MediaCodec.createEncoderByType("video/avc");
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = encoder.createInputSurface();
            encoder.start();

            // ---------- 解码器 ----------
            decoder = MediaCodec.createDecoderByType(mime);
            surfaceTexture = new SurfaceTexture(0);
            surfaceTexture.setDefaultBufferSize(srcW, srcH);
            decoderSurface = new Surface(surfaceTexture);
            decoder.configure(srcFormat, decoderSurface, null, 0);
            decoder.start();

            // ---------- GL ----------
            eglSetup();
            makeCurrent();
            texId = createExternalTexture();
            surfaceTexture.attachToGLContext(texId);
            overlayTexId = createTextureFromBitmap(overlay);
            buildProgram();

            // ---------- Muxer ----------
            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            extractor.selectTrack(videoTrackIndex);
            boolean inputDone = false;
            boolean outputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long lastPts = 0;

            while (!outputDone) {
                if (!inputDone) {
                    int inIdx = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inIdx >= 0) {
                        ByteBuffer buf = decoder.getInputBuffer(inIdx);
                        int size = extractor.readSampleData(buf, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            decoder.queueInputBuffer(inIdx, 0, size, pts, 0);
                            extractor.advance();
                        }
                    }
                }

                // 解码出一帧就到 SurfaceTexture
                int outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outIdx >= 0) {
                    boolean render = info.size != 0;
                    decoder.releaseOutputBuffer(outIdx, render);
                    if (render) {
                        surfaceTexture.updateTexImage();
                        long pts = info.presentationTimeUs;
                        if (pts <= lastPts) pts = lastPts + 1000L;
                        lastPts = pts;
                        frameTimeUs = pts;
                        drawFrame(pts);
                        drainEncoder(false);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 忽略，编码器新格式才需要处理
                }
            }

            drainEncoder(true);

            // ---------- 音频直接搬 ----------
            if (audioTrackIndex >= 0) {
                copyAudio(extractor, audioTrackIndex, in);
            }
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            release();
            try { extractor.release(); } catch (Exception ignored) {}
        }
    }

    private void copyAudio(MediaExtractor extractor, int audioTrackIndex, File in) {
        MediaExtractor a = new MediaExtractor();
        try {
            a.setDataSource(in.getAbsolutePath());
            a.selectTrack(audioTrackIndex);
            MediaFormat fmt = a.getTrackFormat(audioTrackIndex);
            if (muxer == null) return;
            if (!muxerStarted) {
                muxer.start();
                muxerStarted = true;
            }
            int track = muxer.addTrack(fmt);
            ByteBuffer buf = ByteBuffer.allocateDirect(256 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int size = a.readSampleData(buf, 0);
                if (size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = a.getSampleTime();
                info.flags = a.getSampleFlags();
                muxer.writeSampleData(track, buf, info);
                a.advance();
            }
        } catch (Exception ignored) {
        } finally {
            try { a.release(); } catch (Exception ignored) {}
        }
    }

    // ------------------------------------------------------------------ GL

    private void eglSetup() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] ver = new int[2];
        EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1);

        int[] attrib = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] num = new int[1];
        EGL14.eglChooseConfig(eglDisplay, attrib, 0, configs, 0, 1, num, 0);

        int[] ctxAttrib = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);

        int[] surfAttrib = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], encoder.createInputSurface(), surfAttrib, 0);
    }

    private void makeCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
    }

    private int createExternalTexture() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    private int createTextureFromBitmap(Bitmap bmp) {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        return t[0];
    }

    private void buildProgram() {
        String vs = "attribute vec4 aPos; attribute vec2 aTex; varying vec2 vTex;" +
                "void main(){ gl_Position=aPos; vTex=aTex; }";
        String fs = "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float; varying vec2 vTex;" +
                "uniform samplerExternalOES uTex; uniform sampler2D uOverlay;" +
                "void main(){ vec4 c = texture2D(uTex, vTex); vec4 o = texture2D(uOverlay, vTex);" +
                "gl_FragColor = mix(c, o, o.a); }";
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        texProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(texProgram, v);
        GLES20.glAttachShader(texProgram, f);
        GLES20.glLinkProgram(texProgram);
        aPosition = GLES20.glGetAttribLocation(texProgram, "aPos");
        aTexCoord = GLES20.glGetAttribLocation(texProgram, "aTex");
        uTexture = GLES20.glGetUniformLocation(texProgram, "uTex");

        float[] quad = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
        float[] tex = {0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f};
        vertexBuf = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuf.put(quad).position(0);
        texBuf = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
        texBuf.put(tex).position(0);
    }

    private FloatBuffer texBuf;

    private int compile(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        return sh;
    }

    private void drawFrame(long ptsUs) {
        GLES20.glViewport(0, 0, outW, outH);
        GLES20.glUseProgram(texProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GLES20.glUniform1i(uTexture, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(texProgram, "uOverlay"), 1);

        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuf);
        GLES20.glEnableVertexAttribArray(aTexCoord);
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuf);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFinish();
    }

    private void drainEncoder(boolean endOfStream) {
        if (endOfStream) encoder.signalEndOfInputStream();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int idx = encoder.dequeueOutputBuffer(info, TIMEOUT_US);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break;
                continue;
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    videoTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                }
            } else if (idx >= 0) {
                ByteBuffer buf = encoder.getOutputBuffer(idx);
                if (buf != null && info.size > 0 && muxerStarted) {
                    muxer.writeSampleData(videoTrack, buf, info);
                }
                encoder.releaseOutputBuffer(idx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            } else {
                break;
            }
        }
    }

    private void release() {
        try { if (eglDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT); } catch (Throwable ignored) {}
        try { if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface); } catch (Throwable ignored) {}
        try { if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext); } catch (Throwable ignored) {}
        try { if (eglDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(eglDisplay); } catch (Throwable ignored) {}
        eglSurface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;

        try { if (decoder != null) { decoder.stop(); decoder.release(); } } catch (Throwable ignored) {}
        try { if (encoder != null) { encoder.stop(); encoder.release(); } } catch (Throwable ignored) {}
        try { if (decoderSurface != null) decoderSurface.release(); } catch (Throwable ignored) {}
        try { if (surfaceTexture != null) surfaceTexture.release(); } catch (Throwable ignored) {}
        try { if (muxer != null) { if (muxerStarted) muxer.stop(); muxer.release(); } } catch (Throwable ignored) {}
        decoder = null;
        encoder = null;
        muxer = null;
    }
}
