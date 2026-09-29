package com.sjzkq.harmonica;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText sheetInput, speedInput, switchDelayInput, loopInput;
    private TextView logView;
    private Button btnStart, btnPreview, btnCalibrate, btnViewCoords, btnResetCoords;
    private CoordStore coordStore;
    private CalibrationOverlay calibrationOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        coordStore = new CoordStore(this);

        sheetInput = findViewById(R.id.sheetInput);
        speedInput = findViewById(R.id.speedInput);
        switchDelayInput = findViewById(R.id.switchDelayInput);
        loopInput = findViewById(R.id.loopInput);
        logView = findViewById(R.id.logView);
        logView.setMovementMethod(new ScrollingMovementMethod());

        btnStart = findViewById(R.id.btnStart);
        btnPreview = findViewById(R.id.btnPreview);
        btnCalibrate = findViewById(R.id.btnCalibrate);
        btnViewCoords = findViewById(R.id.btnViewCoords);
        btnResetCoords = findViewById(R.id.btnResetCoords);

        // 示例谱子
        findViewById(R.id.btnExample1).setOnClickListener(v ->
                sheetInput.setText("1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 - 5 5 4 4 3 3 2 - 5 5 4 4 3 3 2 - 1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 -"));
        findViewById(R.id.btnExample2).setOnClickListener(v ->
                sheetInput.setText("3 3 4 5 5 4 3 2 1 1 2 3 3 2 2 - 3 3 4 5 5 4 3 2 1 1 2 3 2 1 1 -"));
        findViewById(R.id.btnExample3).setOnClickListener(v ->
                sheetInput.setText("#1 #2 #3 #4 #5 - b3 b6 b7 - 1. 2. 3. 4. 5."));

        btnPreview.setOnClickListener(v -> doPreview());
        btnStart.setOnClickListener(v -> doStart());
        btnCalibrate.setOnClickListener(v -> doCalibrate());
        btnViewCoords.setOnClickListener(v -> doViewCoords());
        btnResetCoords.setOnClickListener(v -> {
            coordStore.resetCoords();
            log("坐标已恢复默认");
            Toast.makeText(this, "已恢复默认坐标", Toast.LENGTH_SHORT).show();
        });

        log("应用启动成功");
        checkPermissions();
    }

    /** 检查权限 */
    private void checkPermissions() {
        // 悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                log("需要悬浮窗权限");
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
        // 无障碍服务
        HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
        if (svc == null) {
            log("需要开启无障碍服务");
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        } else {
            log("无障碍服务已就绪");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
        if (svc != null) {
            svc.setPlayListener(new HarmonicaAccessibilityService.PlayListener() {
                @Override
                public void onNotePlay(int loop, int idx, int total, SheetParser.Note note) {
                    runOnUiThread(() -> log("[" + (idx + 1) + "/" + total + "] " + SheetParser.describe(note)));
                }
                @Override
                public void onPlayEnd() {
                    runOnUiThread(() -> {
                        log("演奏结束");
                        btnStart.setText("开始演奏");
                    });
                }
            });
        }
    }

    /** 预览谱子 */
    private void doPreview() {
        String sheet = sheetInput.getText().toString();
        List<SheetParser.Note> notes = SheetParser.parse(sheet);
        if (notes.isEmpty()) {
            Toast.makeText(this, "谱子为空或无法解析", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(notes.size()).append(" 个音符:\n");
        for (int i = 0; i < Math.min(notes.size(), 30); i++) {
            sb.append(i + 1).append(". ").append(SheetParser.describe(notes.get(i))).append("\n");
        }
        if (notes.size() > 30) sb.append("... 共 ").append(notes.size()).append(" 个");
        log(sb.toString());
        Toast.makeText(this, "预览完成，共 " + notes.size() + " 个音符", Toast.LENGTH_SHORT).show();
    }

    /** 开始演奏 */
    private void doStart() {
        HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
        if (svc == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            return;
        }

        if (svc.isPlaying()) {
            svc.stopPlaying();
            btnStart.setText("开始演奏");
            log("已停止");
            return;
        }

        String sheet = sheetInput.getText().toString();
        List<SheetParser.Note> notes = SheetParser.parse(sheet);
        if (notes.isEmpty()) {
            Toast.makeText(this, "谱子为空或无法解析", Toast.LENGTH_SHORT).show();
            return;
        }

        int speed = parseInt(speedInput.getText().toString(), 400);
        int switchDelay = parseInt(switchDelayInput.getText().toString(), 300);
        int loop = parseInt(loopInput.getText().toString(), 1);

        Map<String, float[]> coords = coordStore.getCoords();

        btnStart.setText("停止");
        log("开始演奏: " + notes.size() + " 音符, 速度=" + speed + "ms, 循环=" + loop);
        Toast.makeText(this, "3秒后开始，请切到三角洲游戏横屏口琴界面", Toast.LENGTH_LONG).show();

        new android.os.Handler().postDelayed(() -> {
            svc.play(notes, coords, speed, switchDelay, loop);
        }, 3000);
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }

    /** 开始标定 */
    private void doCalibrate() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "需要先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        }

        Map<String, float[]> coords = coordStore.getCoords();
        List<CalibrationOverlay.CalibItem> items = new ArrayList<>();
        items.add(new CalibrationOverlay.CalibItem("mode_natural", "自然音"));
        items.add(new CalibrationOverlay.CalibItem("mode_sharp", "升调"));
        items.add(new CalibrationOverlay.CalibItem("mode_flat", "降调"));
        items.add(new CalibrationOverlay.CalibItem("mode_half", "半音"));
        items.add(new CalibrationOverlay.CalibItem("note_1", "音符 1"));
        items.add(new CalibrationOverlay.CalibItem("note_2", "音符 2"));
        items.add(new CalibrationOverlay.CalibItem("note_3", "音符 3"));
        items.add(new CalibrationOverlay.CalibItem("note_4", "音符 4"));
        items.add(new CalibrationOverlay.CalibItem("note_5", "音符 5"));
        items.add(new CalibrationOverlay.CalibItem("note_6", "音符 6"));
        items.add(new CalibrationOverlay.CalibItem("note_7", "音符 7"));
        items.add(new CalibrationOverlay.CalibItem("note_i", "音符 i(高音1)"));
        items.add(new CalibrationOverlay.CalibItem("point", "低音点"));

        calibrationOverlay = new CalibrationOverlay(this, coords, new CalibrationOverlay.CalibrationListener() {
            @Override
            public void onComplete(Map<String, float[]> coords) {
                coordStore.saveCoords(coords);
                runOnUiThread(() -> {
                    log("标定完成，坐标已保存");
                    Toast.makeText(MainActivity.this, "标定完成！", Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onCancel() {
                runOnUiThread(() -> log("标定已取消"));
            }
            @Override
            public void onProgress(int current, int total, String label) {
                runOnUiThread(() -> log("标定 " + current + "/" + total + ": " + label));
            }
        });

        log("3秒后启动标定，请切到三角洲游戏横屏口琴界面");
        Toast.makeText(this, "3秒后启动标定，请切到游戏界面", Toast.LENGTH_LONG).show();
        new android.os.Handler().postDelayed(() -> calibrationOverlay.start(items), 3000);
    }

    /** 查看坐标 */
    private void doViewCoords() {
        Map<String, float[]> coords = coordStore.getCoords();
        StringBuilder sb = new StringBuilder("当前坐标:\n");
        for (Map.Entry<String, float[]> e : coords.entrySet()) {
            sb.append(e.getKey()).append(": (")
                    .append(String.format("%.3f", e.getValue()[0])).append(", ")
                    .append(String.format("%.3f", e.getValue()[1])).append(")\n");
        }
        log(sb.toString());
    }

    private void log(String msg) {
        logView.append(msg + "\n");
        // 自动滚动到底部
        int scrollAmount = logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
        if (scrollAmount > 0) logView.scrollTo(0, scrollAmount);
    }
}
