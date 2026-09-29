package com.sjzkq.harmonica;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;
import java.util.Map;

/**
 * 无障碍服务：执行屏幕点击 + 自动演奏
 */
public class HarmonicaAccessibilityService extends AccessibilityService {

    private static HarmonicaAccessibilityService instance;
    private Handler handler;
    private boolean isPlaying = false;
    private boolean isPaused = false;

    // 当前模式和低音点状态
    private SheetParser.Mode currentMode = SheetParser.Mode.NATURAL;
    private boolean currentLowPoint = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        stopPlaying();
    }

    public static HarmonicaAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    /**
     * 点击屏幕指定坐标（像素）
     */
    public void tap(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        dispatchGesture(gesture, null, null);
    }

    /**
     * 获取屏幕尺寸（横屏时 width > height）
     */
    public int[] getScreenSize() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    /**
     * 相对比例转像素坐标
     */
    public int[] toPixels(float px, float py) {
        int[] size = getScreenSize();
        int x = (int) (px * size[0]);
        int y = (int) (py * size[1]);
        return new int[]{x, y};
    }

    /**
     * 开始演奏
     */
    public void play(final List<SheetParser.Note> notes,
                     final Map<String, float[]> coords,
                     final int speed,
                     final int switchDelay,
                     final int loopCount) {
        if (notes == null || notes.isEmpty()) return;
        stopPlaying();
        isPlaying = true;
        isPaused = false;
        currentMode = SheetParser.Mode.NATURAL;
        currentLowPoint = false;

        playLoop(notes, coords, speed, switchDelay, loopCount, 0, 0);
    }

    private void playLoop(final List<SheetParser.Note> notes,
                          final Map<String, float[]> coords,
                          final int speed,
                          final int switchDelay,
                          final int loopCount,
                          final int loopIdx,
                          final int noteIdx) {
        if (!isPlaying) return;
        if (isPaused) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playLoop(notes, coords, speed, switchDelay, loopCount, loopIdx, noteIdx);
                }
            }, 200);
            return;
        }

        // 检查循环结束
        if (noteIdx >= notes.size()) {
            if (loopIdx + 1 >= loopCount) {
                isPlaying = false;
                onPlayEnd();
                return;
            }
            playLoop(notes, coords, speed, switchDelay, loopCount, loopIdx + 1, 0);
            return;
        }

        final SheetParser.Note note = notes.get(noteIdx);
        onNotePlay(loopIdx, noteIdx, notes.size(), note);

        if (note.isRest) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    playLoop(notes, coords, speed, switchDelay, loopCount, loopIdx, noteIdx + 1);
                }
            }, speed);
            return;
        }

        // 计算需要执行的点击步骤
        // 用链表式的 Runnable 串联：模式 -> 低音点 -> 音符

        final Runnable afterPoint = new Runnable() {
            @Override
            public void run() {
                // 步骤3：点击音符
                String key = "note_" + note.key;
                float[] nt = coords.get(key);
                if (nt != null) {
                    int[] p = toPixels(nt[0], nt[1]);
                    tap(p[0], p[1]);
                }
                // 下一个音符
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        playLoop(notes, coords, speed, switchDelay, loopCount, loopIdx, noteIdx + 1);
                    }
                }, speed);
            }
        };

        final Runnable afterMode = new Runnable() {
            @Override
            public void run() {
                // 步骤2：低音点
                if (currentLowPoint != note.lowPoint) {
                    float[] pt = coords.get("point");
                    if (pt != null) {
                        int[] p = toPixels(pt[0], pt[1]);
                        tap(p[0], p[1]);
                    }
                    currentLowPoint = note.lowPoint;
                    handler.postDelayed(afterPoint, 150);
                } else {
                    afterPoint.run();
                }
            }
        };

        // 步骤1：切换模式
        if (currentMode != note.mode) {
            String modeKey = null;
            switch (note.mode) {
                case SHARP: modeKey = "mode_sharp"; break;
                case FLAT: modeKey = "mode_flat"; break;
                case HALF: modeKey = "mode_half"; break;
                default: modeKey = "mode_natural";
            }
            float[] md = coords.get(modeKey);
            if (md != null) {
                int[] p = toPixels(md[0], md[1]);
                tap(p[0], p[1]);
            }
            currentMode = note.mode;
            handler.postDelayed(afterMode, switchDelay);
        } else {
            afterMode.run();
        }
    }

    public void stopPlaying() {
        isPlaying = false;
        isPaused = false;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    public void togglePause() {
        isPaused = !isPaused;
    }

    public boolean isPlaying() { return isPlaying; }
    public boolean isPaused() { return isPaused; }

    // 回调通知（支持多个监听器）
    public interface PlayListener {
        void onNotePlay(int loop, int idx, int total, SheetParser.Note note);
        void onPlayEnd();
    }
    private final java.util.List<PlayListener> listeners = new java.util.ArrayList<>();
    public void addPlayListener(PlayListener l) { if (l != null) listeners.add(l); }
    public void removePlayListener(PlayListener l) { listeners.remove(l); }
    public void setPlayListener(PlayListener l) { listeners.clear(); if (l != null) listeners.add(l); }

    private void onNotePlay(int loop, int idx, int total, SheetParser.Note note) {
        for (PlayListener l : listeners) l.onNotePlay(loop, idx, total, note);
    }
    private void onPlayEnd() {
        for (PlayListener l : listeners) l.onPlayEnd();
    }
}
