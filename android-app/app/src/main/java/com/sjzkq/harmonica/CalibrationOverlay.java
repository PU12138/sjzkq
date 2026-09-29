package com.sjzkq.harmonica;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 坐标标定悬浮窗
 * 全屏半透明覆盖层 + 可拖动标记点
 */
public class CalibrationOverlay {

    public interface CalibrationListener {
        void onComplete(Map<String, float[]> coords);
        void onCancel();
        void onProgress(int current, int total, String label);
    }

    private final Context context;
    private final WindowManager windowManager;
    private FrameLayout overlayView;
    private WindowManager.LayoutParams params;
    private View marker;
    private TextView titleText;
    private int screenW, screenH;

    private List<CalibItem> items;
    private int currentIdx = 0;
    private Map<String, float[]> coords;
    private CalibrationListener listener;

    public CalibrationOverlay(Context context, Map<String, float[]> coords, CalibrationListener listener) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.coords = coords;
        this.listener = listener;

        // 获取屏幕尺寸（横屏）
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
    }

    /** 标定项 */
    public static class CalibItem {
        public String key;
        public String label;
        public CalibItem(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** 开始标定 */
    public void start(List<CalibItem> items) {
        this.items = new ArrayList<>(items);
        this.currentIdx = 0;
        show();
    }

    private void show() {
        if (overlayView != null) return;

        overlayView = new FrameLayout(context);
        overlayView.setBackgroundColor(0x66000000); // 半透明黑
        // 标记点
        marker = new View(context);
        marker.setBackgroundColor(0xFFFF0000);
        FrameLayout.LayoutParams markerParams = new FrameLayout.LayoutParams(80, 80);
        marker.setLayoutParams(markerParams);
        overlayView.addView(marker);

        // 标题
        titleText = new TextView(context);
        titleText.setTextColor(0xFFFFFFFF);
        titleText.setTextSize(18);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, 40, 0, 0);
        overlayView.addView(titleText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // 确认按钮
        Button confirmBtn = new Button(context);
        confirmBtn.setText("确认位置");
        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrent();
                currentIdx++;
                if (currentIdx >= items.size()) {
                    if (listener != null) listener.onComplete(coords);
                    dismiss();
                } else {
                    updateCurrent();
                }
            }
        });
        FrameLayout.LayoutParams confirmParams = new FrameLayout.LayoutParams(300, 120);
        confirmParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        confirmParams.bottomMargin = 60;
        overlayView.addView(confirmBtn, confirmParams);

        // 跳过按钮
        Button skipBtn = new Button(context);
        skipBtn.setText("跳过");
        skipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentIdx++;
                if (currentIdx >= items.size()) {
                    if (listener != null) listener.onComplete(coords);
                    dismiss();
                } else {
                    updateCurrent();
                }
            }
        });
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(200, 100);
        skipParams.gravity = Gravity.BOTTOM | Gravity.START;
        skipParams.bottomMargin = 70;
        skipParams.leftMargin = 30;
        overlayView.addView(skipBtn, skipParams);

        // 取消按钮
        Button cancelBtn = new Button(context);
        cancelBtn.setText("取消");
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onCancel();
                dismiss();
            }
        });
        FrameLayout.LayoutParams cancelParams = new FrameLayout.LayoutParams(200, 100);
        cancelParams.gravity = Gravity.BOTTOM | Gravity.END;
        cancelParams.bottomMargin = 70;
        cancelParams.rightMargin = 30;
        overlayView.addView(cancelBtn, cancelParams);

        // 标记点拖动
        marker.setOnTouchListener(new View.OnTouchListener() {
            private float offsetX, offsetY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        offsetX = event.getRawX() - v.getX();
                        offsetY = event.getRawY() - v.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float nx = event.getRawX() - offsetX;
                        float ny = event.getRawY() - offsetY;
                        // 限制在屏幕内
                        nx = Math.max(0, Math.min(nx, screenW - v.getWidth()));
                        ny = Math.max(0, Math.min(ny, screenH - v.getHeight()));
                        v.setX(nx);
                        v.setY(ny);
                        break;
                }
                return true;
            }
        });

        // 添加到窗口
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        updateCurrent();
    }

    private void updateCurrent() {
        if (currentIdx >= items.size()) return;
        CalibItem item = items.get(currentIdx);
        titleText.setText("标定 (" + (currentIdx + 1) + "/" + items.size() + "): " + item.label +
                "\n拖动红色方块到按钮中心");

        // 标记点移到当前坐标
        float[] c = coords.get(item.key);
        if (c != null) {
            marker.setX(c[0] * screenW - marker.getWidth() / 2f);
            marker.setY(c[1] * screenH - marker.getHeight() / 2f);
        }

        if (listener != null) {
            listener.onProgress(currentIdx + 1, items.size(), item.label);
        }
    }

    private void saveCurrent() {
        if (currentIdx >= items.size()) return;
        CalibItem item = items.get(currentIdx);
        // 标记点中心坐标 -> 相对比例
        float cx = (marker.getX() + marker.getWidth() / 2f) / screenW;
        float cy = (marker.getY() + marker.getHeight() / 2f) / screenH;
        coords.put(item.key, new float[]{cx, cy});
    }

    public void dismiss() {
        if (overlayView != null && overlayView.getParent() != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {}
            overlayView = null;
        }
    }
}
