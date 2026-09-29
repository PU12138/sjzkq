package com.sjzkq.harmonica;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private EditText sheetInput, nameInput;
    private TextView speedText, switchDelayText, loopText;
    private SeekBar speedBar, switchDelayBar, loopBar;
    private TextView logView;
    private ListView sheetListView;
    private CoordStore coordStore;
    private SheetStore sheetStore;
    private CalibrationOverlay calibrationOverlay;
    private FloatWindow floatWindow;
    private ArrayAdapter<String> sheetListAdapter;
    private List<String> sheetNames = new ArrayList<>();

    private int currentSpeed = 400;
    private int currentSwitchDelay = 300;
    private int currentLoop = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            buildUI();
            coordStore = new CoordStore(this);
            sheetStore = new SheetStore(this);
            refreshSheetList();
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
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.bottomMargin = 24;
        layout.addView(title, tp);

        // === 琴谱库 ===
        layout.addView(makeLabel("琴谱库"));

        // 名称输入
        nameInput = new EditText(this);
        nameInput.setHint("琴谱名称");
        nameInput.setBackgroundColor(0xFF252540);
        nameInput.setTextColor(0xFFFFFFFF);
        nameInput.setHintTextColor(0xFFAAAAAA);
        nameInput.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.bottomMargin = 12;
        layout.addView(nameInput, np);

        // 谱子内容
        sheetInput = new EditText(this);
        sheetInput.setHint("输入口琴谱，例如：1 1 5 5 6 6 5 -");
        sheetInput.setMinLines(4);
        sheetInput.setGravity(Gravity.TOP | Gravity.START);
        sheetInput.setBackgroundColor(0xFF252540);
        sheetInput.setTextColor(0xFFFFFFFF);
        sheetInput.setHintTextColor(0xFFAAAAAA);
        sheetInput.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 240);
        sp.bottomMargin = 12;
        layout.addView(sheetInput, sp);

        // 保存/导入按钮
        LinearLayout saveRow = new LinearLayout(this);
        saveRow.setOrientation(LinearLayout.HORIZONTAL);
        saveRow.addView(makeBtn("保存琴谱", v -> saveSheet()));
        saveRow.addView(makeBtn("导入曲库", v -> importBuiltinSheets()));
        saveRow.addView(makeBtn("浏览曲库", v -> showBuiltinPicker()));
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, -2);
        srp.bottomMargin = 16;
        layout.addView(saveRow, srp);

        // 已保存琴谱列表
        sheetListView = new ListView(this);
        sheetListView.setBackgroundColor(0xFF252540);
        sheetListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, sheetNames);
        sheetListView.setAdapter(sheetListAdapter);
        sheetListView.setOnItemClickListener((parent, view, position, id) -> loadSheet(position));
        sheetListView.setOnItemLongClickListener((parent, view, position, id) -> {
            deleteSheet(position);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 300);
        lp.bottomMargin = 24;
        layout.addView(sheetListView, lp);
        layout.addView(makeSmallLabel("点击载入琴谱，长按删除"));

        // === 演奏参数 ===
        layout.addView(makeLabel("演奏参数"));

        // 速度滑块
        speedText = makeSliderLabel("速度", currentSpeed, "ms");
        layout.addView(speedText);
        speedBar = new SeekBar(this);
        speedBar.setMax(980);
        speedBar.setProgress(currentSpeed - 20);
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean f) {
                currentSpeed = p + 20;
                speedText.setText("速度: " + currentSpeed + " ms");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        layout.addView(speedBar);

        // 变调延时滑块
        switchDelayText = makeSliderLabel("变调延时", currentSwitchDelay, "ms");
        layout.addView(switchDelayText);
        switchDelayBar = new SeekBar(this);
        switchDelayBar.setMax(980);
        switchDelayBar.setProgress(currentSwitchDelay - 20);
        switchDelayBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean f) {
                currentSwitchDelay = p + 20;
                switchDelayText.setText("变调延时: " + currentSwitchDelay + " ms");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        layout.addView(switchDelayBar);

        // 循环次数滑块
        loopText = makeSliderLabel("循环", currentLoop, "次");
        layout.addView(loopText);
        loopBar = new SeekBar(this);
        loopBar.setMax(19);
        loopBar.setProgress(currentLoop - 1);
        loopBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean f) {
                currentLoop = p + 1;
                loopText.setText("循环: " + currentLoop + " 次");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        layout.addView(loopBar);

        // === 操作按钮 ===
        LinearLayout opRow = new LinearLayout(this);
        opRow.setOrientation(LinearLayout.HORIZONTAL);
        opRow.addView(makeBtn("预览", v -> doPreview()));
        Button btnStart = makeBtn("开始演奏", v -> doStart());
        btnStart.setBackgroundColor(0xFF4CAF50);
        opRow.addView(btnStart);
        LinearLayout.LayoutParams opLp = new LinearLayout.LayoutParams(-1, -2);
        opLp.topMargin = 16;
        opLp.bottomMargin = 24;
        layout.addView(opRow, opLp);

        // === 悬浮窗开关 ===
        layout.addView(makeLabel("游戏悬浮窗"));
        Button floatBtn = makeBtn("显示悬浮窗", v -> toggleFloatWindow());
        floatBtn.setBackgroundColor(0xFFFF9800);
        LinearLayout.LayoutParams fbp = new LinearLayout.LayoutParams(-1, -2);
        fbp.bottomMargin = 24;
        layout.addView(floatBtn, fbp);

        // === 坐标标定 ===
        layout.addView(makeLabel("坐标标定"));
        layout.addView(makeSmallLabel("首次使用请点击「开始标定」校准坐标"));
        LinearLayout calRow = new LinearLayout(this);
        calRow.setOrientation(LinearLayout.HORIZONTAL);
        calRow.addView(makeBtn("开始标定", v -> doCalibrate()));
        calRow.addView(makeBtn("查看坐标", v -> doViewCoords()));
        calRow.addView(makeBtn("恢复默认", v -> {
            coordStore.resetCoords();
            log("坐标已恢复默认");
            Toast.makeText(this, "已恢复默认坐标", Toast.LENGTH_SHORT).show();
        }));
        LinearLayout.LayoutParams calLp = new LinearLayout.LayoutParams(-1, -2);
        calLp.bottomMargin = 24;
        layout.addView(calRow, calLp);

        // === 日志 ===
        layout.addView(makeLabel("运行日志"));
        logView = new TextView(this);
        logView.setBackgroundColor(0xFF252540);
        logView.setTextColor(0xFF66FF66);
        logView.setTextSize(12);
        logView.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 300);
        layout.addView(logView, llp);

        setContentView(root);
    }

    // ==================== 琴谱管理 ====================

    private void saveSheet() {
        String name = nameInput.getText().toString().trim();
        String content = sheetInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入琴谱名称", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.isEmpty()) {
            Toast.makeText(this, "琴谱内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        sheetStore.addOrUpdate(new SheetStore.Sheet(name, content));
        refreshSheetList();
        log("已保存琴谱: " + name);
        Toast.makeText(this, "已保存: " + name, Toast.LENGTH_SHORT).show();
    }

    private void loadSheet(int position) {
        List<SheetStore.Sheet> sheets = sheetStore.getSheets();
        if (position < 0 || position >= sheets.size()) return;
        SheetStore.Sheet s = sheets.get(position);
        nameInput.setText(s.name);
        sheetInput.setText(s.content);
        log("已载入琴谱: " + s.name);
    }

    private void deleteSheet(int position) {
        List<SheetStore.Sheet> sheets = sheetStore.getSheets();
        if (position < 0 || position >= sheets.size()) return;
        String name = sheets.get(position).name;
        new AlertDialog.Builder(this)
                .setTitle("删除琴谱")
                .setMessage("确定删除「" + name + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    sheetStore.delete(name);
                    refreshSheetList();
                    log("已删除琴谱: " + name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshSheetList() {
        List<SheetStore.Sheet> sheets = sheetStore.getSheets();
        sheetNames.clear();
        for (SheetStore.Sheet s : sheets) sheetNames.add(s.name);
        sheetListAdapter.notifyDataSetChanged();
    }

    /** 导入全部内置曲库 */
    private void importBuiltinSheets() {
        List<SheetStore.Sheet> builtin = BuiltinSheets.getAll();
        int added = 0;
        for (SheetStore.Sheet s : builtin) {
            sheetStore.addOrUpdate(s);
            added++;
        }
        refreshSheetList();
        log("已导入 " + added + " 首内置琴谱");
        Toast.makeText(this, "已导入 " + added + " 首琴谱", Toast.LENGTH_SHORT).show();
    }

    /** 浏览内置曲库，选择载入 */
    private void showBuiltinPicker() {
        List<SheetStore.Sheet> builtin = BuiltinSheets.getAll();
        String[] names = new String[builtin.size()];
        for (int i = 0; i < builtin.size(); i++) {
            names[i] = builtin.get(i).name;
        }
        new AlertDialog.Builder(this)
            .setTitle("内置曲库 (" + names.length + " 首)")
            .setItems(names, (d, which) -> {
                SheetStore.Sheet s = builtin.get(which);
                nameInput.setText(s.name);
                sheetInput.setText(s.content);
                log("已载入: " + s.name);
                Toast.makeText(this, "已载入: " + s.name, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // ==================== 悬浮窗 ====================

    private void toggleFloatWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
        }
        if (floatWindow == null) {
            floatWindow = new FloatWindow(this);
            floatWindow.setSpeed(currentSpeed);
            floatWindow.setSwitchDelay(currentSwitchDelay);
            floatWindow.setLoop(currentLoop);
            floatWindow.show();
            log("悬浮窗已显示，切到游戏后点击♪图标");
            Toast.makeText(this, "悬浮窗已显示", Toast.LENGTH_SHORT).show();
        } else {
            floatWindow.hide();
            floatWindow = null;
            log("悬浮窗已关闭");
            Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show();
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
                    runOnUiThread(() -> log("演奏结束"));
                }
            });
        }
        if (floatWindow != null) {
            floatWindow.setSpeed(currentSpeed);
            floatWindow.setSwitchDelay(currentSwitchDelay);
            floatWindow.setLoop(currentLoop);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (floatWindow != null) {
            floatWindow.hide();
            floatWindow = null;
        }
    }

    // ==================== 权限 ====================

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) log("需要悬浮窗权限");
        }
        HarmonicaAccessibilityService svc = HarmonicaAccessibilityService.getInstance();
        if (svc == null) {
            log("需要开启无障碍服务");
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            log("无障碍服务已就绪");
        }
    }

    // ==================== 演奏 ====================

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
        Map<String, float[]> coords = coordStore.getCoords();
        log("开始演奏: " + notes.size() + " 音符, 速度=" + currentSpeed + "ms, 循环=" + currentLoop);
        Toast.makeText(this, "3秒后开始，请切到三角洲游戏横屏口琴界面", Toast.LENGTH_LONG).show();

        new android.os.Handler().postDelayed(() -> {
            try { svc.play(notes, coords, currentSpeed, currentSwitchDelay, currentLoop); }
            catch (Exception e) { log("错误: " + e); }
        }, 3000);
    }

    // ==================== 标定 ====================

    private void doCalibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "需要先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
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
            @Override public void onComplete(Map<String, float[]> coords) {
                coordStore.saveCoords(coords);
                runOnUiThread(() -> {
                    log("标定完成，坐标已保存");
                    Toast.makeText(MainActivity.this, "标定完成！", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onCancel() { runOnUiThread(() -> log("标定已取消")); }
            @Override public void onProgress(int c, int t, String l) { runOnUiThread(() -> log("标定 " + c + "/" + t + ": " + l)); }
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

    // ==================== 工具方法 ====================

    private TextView makeLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFA940);
        t.setTextSize(16);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.bottomMargin = 12;
        t.setLayoutParams(p);
        return t;
    }

    private TextView makeSmallLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFAAAAAA);
        t.setTextSize(12);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.bottomMargin = 12;
        t.setLayoutParams(p);
        return t;
    }

    private TextView makeSliderLabel(String prefix, int val, String unit) {
        TextView t = new TextView(this);
        t.setText(prefix + ": " + val + " " + unit);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(14);
        return t;
    }

    private Button makeBtn(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(8, 0, 8, 0);
        b.setLayoutParams(p);
        return b;
    }

    private void log(String msg) {
        logView.append(msg + "\n");
    }
}
