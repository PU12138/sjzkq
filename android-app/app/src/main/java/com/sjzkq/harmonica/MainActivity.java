package com.sjzkq.harmonica;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private EditText sheetInput, speedInput, switchDelayInput, loopInput;
    private TextView logView;
    private CoordStore coordStore;
    private CalibrationOverlay calibrationOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            buildUI();
            coordStore = new CoordStore(this);
            log("应用启动成功");
            checkPermissions();
        } catch (Throwable e) {
            e.printStackTrace();
            new AlertDialog.Builder(this)
                    .setTitle("启动失败")
                    .setMessage(e.toString())
                    .setPositiveButton("确定", null)
                    .show();
        }
    }

    /** 纯代码构建UI，避免XML布局问题 */
    private void buildUI() {
        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(0xFF1A1A2E);
        root.setPadding(32, 32, 32, 32);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        root.addView(layout);

        // 标题
        TextView title = new TextView(this);
        title.setText("三角洲口琴自动演奏");
        title.setTextColor(0xFFFFA940);
        title.setTextSize(24);
        title.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = 32;
        layout.addView(title, tp);

        // 谱子标签
        layout.addView(makeLabel("口琴谱"));

        // 谱子输入框
        sheetInput = new EditText(this);
        sheetInput.setHint("输入口琴谱，例如：1 1 5 5 6 6 5 -");
        sheetInput.setMinLines(4);
        sheetInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        sheetInput.setBackgroundColor(0xFF252540);
        sheetInput.setTextColor(0xFFFFFFFF);
        sheetInput.setHintTextColor(0xFFAAAAAA);
        sheetInput.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 240);
        sp.bottomMargin = 16;
        layout.addView(sheetInput, sp);

        // 示例按钮
        LinearLayout exRow = new LinearLayout(this);
        exRow.setOrientation(LinearLayout.HORIZONTAL);
        exRow.addView(makeBtn("小星星", v -> sheetInput.setText("1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 - 5 5 4 4 3 3 2 - 5 5 4 4 3 3 2 - 1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 -")));
        exRow.addView(makeBtn("欢乐颂", v -> sheetInput.setText("3 3 4 5 5 4 3 2 1 1 2 3 3 2 2 - 3 3 4 5 5 4 3 2 1 1 2 3 2 1 1 -")));
        exRow.addView(makeBtn("升降调", v -> sheetInput.setText("#1 #2 #3 #4 #5 - b3 b6 b7 - 1. 2. 3. 4. 5.")));
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        exLp.bottomMargin = 32;
        layout.addView(exRow, exLp);

        // 参数
        layout.addView(makeLabel("演奏参数"));
        LinearLayout paramRow = new LinearLayout(this);
        paramRow.setOrientation(LinearLayout.HORIZONTAL);
        speedInput = makeParamInput(paramRow, "速度(ms)", "400");
        switchDelayInput = makeParamInput(paramRow, "变调延时", "300");
        loopInput = makeParamInput(paramRow, "循环", "1");
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        prLp.bottomMargin = 32;
        layout.addView(paramRow, prLp);

        // 操作按钮
        LinearLayout opRow = new LinearLayout(this);
        opRow.setOrientation(LinearLayout.HORIZONTAL);
        opRow.addView(makeBtn("预览", v -> doPreview()));
        Button btnStart = makeBtn("开始演奏", v -> doStart());
        btnStart.setBackgroundColor(0xFF4CAF50);
        opRow.addView(btnStart);
        LinearLayout.LayoutParams opLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        opLp.bottomMargin = 32;
        layout.addView(opRow, opLp);

        // 标定
        layout.addView(makeLabel("坐标标定"));
        TextView tip = new TextView(this);
        tip.setText("首次使用请点击「开始标定」校准坐标");
        tip.setTextColor(0xFFAAAAAA);
        tip.setTextSize(12);
        layout.addView(tip);
        LinearLayout calRow = new LinearLayout(this);
        calRow.setOrientation(LinearLayout.HORIZONTAL);
        calRow.addView(makeBtn("开始标定", v -> doCalibrate()));
        calRow.addView(makeBtn("查看坐标", v -> doViewCoords()));
        calRow.addView(makeBtn("恢复默认", v -> {
            coordStore.resetCoords();
            log("坐标已恢复默认");
            Toast.makeText(this, "已恢复默认坐标", Toast.LENGTH_SHORT).show();
        }));
        LinearLayout.LayoutParams calLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        calLp.bottomMargin = 32;
        layout.addView(calRow, calLp);

        // 日志
        layout.addView(makeLabel("运行日志"));
        logView = new TextView(this);
        logView.setBackgroundColor(0xFF252540);
        logView.setTextColor(0xFF66FF66);
        logView.setTextSize(12);
        logView.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400);
        layout.addView(logView, lp);

        setContentView(root);
    }

    private TextView makeLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFA940);
        t.setTextSize(16);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = 16;
        t.setLayoutParams(p);
        return t;
    }

    private Button makeBtn(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        p.setMargins(8, 0, 8, 0);
        b.setLayoutParams(p);
        return b;
    }

    private EditText makeParamInput(LinearLayout parent, String label, String defVal) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cp.setMargins(8, 0, 8, 0);
        col.setLayoutParams(cp);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFAAAAAA);
        t.setTextSize(12);
        col.addView(t);

        EditText et = new EditText(this);
        et.setText(defVal);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setBackgroundColor(0xFF252540);
        et.setTextColor(0xFFFFFFFF);
        et.setPadding(16, 16, 16, 16);
        col.addView(et);

        parent.addView(col);
        return et;
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
                    runOnUiThread(() -> log("演奏结束"));
                }
            });
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                log("需要悬浮窗权限");
            }
        }
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

    private void doStart() {
        HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
        if (svc == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (svc.isPlaying()) {
            svc.stopPlaying();
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

        log("开始演奏: " + notes.size() + " 音符, 速度=" + speed + "ms, 循环=" + loop);
        Toast.makeText(this, "3秒后开始，请切到三角洲游戏横屏口琴界面", Toast.LENGTH_LONG).show();

        new android.os.Handler().postDelayed(() -> {
            try { svc.play(notes, coords, speed, switchDelay, loop); }
            catch (Exception e) { log("错误: " + e); }
        }, 3000);
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }

    private void doCalibrate() {
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
            public void onCancel() { runOnUiThread(() -> log("标定已取消")); }
            @Override
            public void onProgress(int current, int total, String label) {
                runOnUiThread(() -> log("标定 " + current + "/" + total + ": " + label));
            }
        });

        log("3秒后启动标定，请切到三角洲游戏横屏口琴界面");
        Toast.makeText(this, "3秒后启动标定，请切到游戏界面", Toast.LENGTH_LONG).show();
        new android.os.Handler().postDelayed(() -> calibrationOverlay.start(items), 3000);
    }

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
    }
}
