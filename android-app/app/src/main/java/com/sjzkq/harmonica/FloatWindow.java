package com.sjzkq.harmonica;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏内悬浮控制窗
 * - 小悬浮球，点击展开控制面板
 * - 选择琴谱、播放/暂停、速度调节
 */
public class FloatWindow {

    private final Context context;
    private final WindowManager windowManager;
    private View ballView;       // 悬浮球
    private View panelView;      // 展开面板
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;

    private Spinner sheetSpinner;
    private Button playBtn;
    private SeekBar speedBar;
    private TextView speedText;

    private List<SheetStore.Sheet> sheets = new ArrayList<>();
    private int currentSpeed = 400;
    private int currentSwitchDelay = 300;
    private int currentLoop = 1;
    private HarmonicaAccessibilityService.PlayListener playListener;

    public FloatWindow(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /** 显示悬浮球 */
    public void show() {
        if (ballView != null) return;
        createBall();
        try {
            windowManager.addView(ballView, ballParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 注册演奏监听器，演奏结束时更新按钮
        HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
        if (svc != null) {
            playListener = new HarmonicaAccessibilityService.PlayListener() {
                @Override public void onNotePlay(int loop, int idx, int total, SheetParser.Note note) {}
                @Override public void onPlayEnd() {
                    if (playBtn != null) playBtn.post(() -> playBtn.setText("开始演奏"));
                }
            };
            svc.addPlayListener(playListener);
        }
    }

    /** 隐藏全部 */
    public void hide() {
        hidePanel();
        if (playListener != null) {
            HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
            if (svc != null) svc.removePlayListener(playListener);
            playListener = null;
        }
        if (ballView != null && ballView.getParent() != null) {
            try { windowManager.removeView(ballView); } catch (Exception e) {}
            ballView = null;
        }
    }

    private void createBall() {
        ballView = new TextView(context);
        ((TextView) ballView).setText("♪");
        ((TextView) ballView).setTextSize(28);
        ((TextView) ballView).setGravity(Gravity.CENTER);
        ((TextView) ballView).setTextColor(0xFFFFFFFF);
        ballView.setBackgroundColor(0xFF4CAF50);

        int size = 120;
        ballView.setLayoutParams(new LinearLayout.LayoutParams(size, size));

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        ballParams = new WindowManager.LayoutParams(
                size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        ballParams.gravity = Gravity.TOP | Gravity.START;
        ballParams.x = 50;
        ballParams.y = 300;

        // 拖动 + 点击
        ballView.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean moved = false;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = ballParams.x;
                        startY = ballParams.y;
                        moved = false;
                        downTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;
                        ballParams.x = startX + (int) dx;
                        ballParams.y = startY + (int) dy;
                        windowManager.updateViewLayout(ballView, ballParams);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!moved && System.currentTimeMillis() - downTime < 300) {
                            togglePanel();
                        }
                        break;
                }
                return true;
            }
        });
    }

    private void togglePanel() {
        if (panelView == null) {
            showPanel();
        } else {
            hidePanel();
        }
    }

    private void showPanel() {
        if (panelView != null) return;
        panelView = buildPanel();
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.CENTER;
        try {
            windowManager.addView(panelView, panelParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        refreshSheetList();
    }

    private void hidePanel() {
        if (panelView != null && panelView.getParent() != null) {
            try { windowManager.removeView(panelView); } catch (Exception e) {}
            panelView = null;
        }
    }

    private View buildPanel() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE1A1A2E);
        panel.setPadding(32, 32, 32, 32);

        // 标题
        TextView title = new TextView(context);
        title.setText("口琴演奏控制");
        title.setTextColor(0xFFFFA940);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = 24;
        panel.addView(title, tp);

        // 琴谱选择
        sheetSpinner = new Spinner(context);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.bottomMargin = 16;
        panel.addView(sheetSpinner, sp);

        // 速度滑块
        TextView speedLabel = new TextView(context);
        speedLabel.setText("演奏速度");
        speedLabel.setTextColor(0xFFFFFFFF);
        speedLabel.setTextSize(14);
        panel.addView(speedLabel);

        speedBar = new SeekBar(context);
        speedBar.setMax(980); // 20~1000
        speedBar.setProgress(currentSpeed - 20);
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSpeed = progress + 20;
                speedText.setText(currentSpeed + " ms");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(speedBar);

        speedText = new TextView(context);
        speedText.setText(currentSpeed + " ms");
        speedText.setTextColor(0xFFAAAAAA);
        speedText.setTextSize(12);
        speedText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        stp.bottomMargin = 16;
        panel.addView(speedText, stp);

        // 播放/暂停按钮
        playBtn = new Button(context);
        playBtn.setText("开始演奏");
        playBtn.setBackgroundColor(0xFF4CAF50);
        playBtn.setTextColor(0xFFFFFFFF);
        playBtn.setOnClickListener(v -> onPlayClick());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.bottomMargin = 12;
        panel.addView(playBtn, pp);

        // 关闭按钮
        Button closeBtn = new Button(context);
        closeBtn.setText("关闭面板");
        closeBtn.setOnClickListener(v -> hidePanel());
        panel.addView(closeBtn);

        return panel;
    }

    private void onPlayClick() {
        HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
        if (svc == null) return;

        if (svc.isPlaying()) {
            svc.stopPlaying();
            playBtn.setText("开始演奏");
            return;
        }

        int idx = sheetSpinner.getSelectedItemPosition();
        if (idx < 0 || idx >= sheets.size()) return;
        SheetStore.Sheet sheet = sheets.get(idx);

        List<SheetParser.Note> notes = SheetParser.parse(sheet.content);
        if (notes.isEmpty()) return;

        // 用当前APP的坐标存储
        CoordStore coordStore = new CoordStore(context);
        svc.play(notes, coordStore.getCoords(), currentSpeed, currentSwitchDelay, currentLoop);
        playBtn.setText("停止演奏");
    }

    /** 刷新琴谱列表 */
    public void refreshSheetList() {
        SheetStore store = new SheetStore(context);
        sheets = store.getSheets();
        List<String> names = new ArrayList<>();
        for (SheetStore.Sheet s : sheets) names.add(s.name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (sheetSpinner != null) {
            sheetSpinner.setAdapter(adapter);
        }
    }

    public void setSpeed(int speed) { this.currentSpeed = speed; }
    public void setSwitchDelay(int d) { this.currentSwitchDelay = d; }
    public void setLoop(int loop) { this.currentLoop = loop; }
}
