/**
 * ============================================================
 *  三角洲行动 口琴自动演奏器 (Auto.js / Hamibot 脚本)
 *  作者: sjzkq
 *  使用: 在手机上安装 Auto.js 或 Hamibot，导入本脚本运行
 *  兼容: 经典 Auto.js (Rhino 引擎) - ES5 语法
 * ============================================================
 *
 *  前置要求:
 *  1. 安卓手机 7.0+
 *  2. 安装 Auto.js 或 Hamibot
 *  3. 开启无障碍服务 + 悬浮窗权限
 *  4. 手机横屏，三角洲游戏内打开口琴自动演奏界面
 * ============================================================
 */

"ui";

// =================== 工具函数 ===================

/** 谱子解析器 */
function parseSheet(text) {
    // 转成 JS 字符串（ui.getText 返回 Java String，不支持 trim/replace 正则）
    var s = String(text || "");
    if (!s || /^\s*$/.test(s)) return [];
    var tokens = s
        .replace(/[，、]/g, ' ')
        .replace(/[\n\r]+/g, ' ')
        .split(/\s+/)
        .filter(function(t) { return t.length > 0; });

    var notes = [];
    for (var i = 0; i < tokens.length; i++) {
        var raw = tokens[i];
        if (raw === '_' || raw === '-' || raw === '|') {
            notes.push({ type: 'rest' });
            continue;
        }

        var mode = 'natural';
        var lowPoint = false;

        if (raw.indexOf('.') === 0) { raw = raw.substring(1); }

        if (raw.indexOf('#') === 0) { mode = 'sharp'; raw = raw.substring(1); }
        else if (raw.indexOf('b') === 0 || raw.indexOf('B') === 0) { mode = 'flat'; raw = raw.substring(1); }
        else if (raw.indexOf('h') === 0 || raw.indexOf('H') === 0) { mode = 'half'; raw = raw.substring(1); }

        if (raw.lastIndexOf('.') === raw.length - 1 && raw.length > 1) {
            lowPoint = true;
            raw = raw.substring(0, raw.length - 1);
        }

        var key = null;
        if (/^[1-7]$/.test(raw)) key = raw;
        else if (raw.toLowerCase() === 'i') key = 'i';
        else continue;

        notes.push({ type: 'note', key: key, mode: mode, lowPoint: lowPoint });
    }
    return notes;
}

/** 根据屏幕百分比计算绝对坐标 (横屏) */
function calcPos(percentX, percentY) {
    var w = Math.max(device.width, device.height);
    var h = Math.min(device.width, device.height);
    return {
        x: Math.round(w * percentX),
        y: Math.round(h * percentY)
    };
}

// =================== 三角洲口琴坐标定义 ===================
var DEFAULT_COORDS = {
    modes: {
        half:    { px: 0.30, py: 0.41 },
        sharp:   { px: 0.40, py: 0.41 },
        natural: { px: 0.52, py: 0.41 },
        flat:    { px: 0.63, py: 0.41 },
    },
    notes: {
        '1': { px: 0.23, py: 0.60 },
        '2': { px: 0.31, py: 0.60 },
        '3': { px: 0.39, py: 0.60 },
        '4': { px: 0.47, py: 0.60 },
        '5': { px: 0.55, py: 0.60 },
        '6': { px: 0.63, py: 0.60 },
        '7': { px: 0.71, py: 0.60 },
        'i': { px: 0.80, py: 0.60 },
    },
    point: { px: 0.12, py: 0.73 },
};

// =================== 持久化存储 ===================
var STORAGE = null;
try {
    STORAGE = storages.create("sjzkq_harmonica");
} catch (e) {
    toast("存储初始化失败: " + e);
}

function loadCoords() {
    if (!STORAGE) return DEFAULT_COORDS;
    var saved = STORAGE.get("coords", null);
    if (saved) {
        return deepMerge(DEFAULT_COORDS, saved);
    }
    return DEFAULT_COORDS;
}

function saveCoords(coords) {
    if (STORAGE) STORAGE.put("coords", coords);
}

function deepMerge(a, b) {
    var result = {};
    for (var k in a) {
        if (typeof a[k] === 'object' && !Array.isArray(a[k])) {
            result[k] = deepMerge(a[k], (b && b[k]) || {});
        } else {
            result[k] = a[k];
        }
    }
    if (b) {
        for (var k2 in b) {
            if (!(k2 in result)) result[k2] = b[k2];
        }
    }
    return result;
}

// =================== 状态 ===================
var state = {
    coords: loadCoords(),
    sheet: (STORAGE ? STORAGE.get("sheet", "1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 -") : "1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 -"),
    speed: (STORAGE ? STORAGE.get("speed", 400) : 400),
    switchDelay: (STORAGE ? STORAGE.get("switchDelay", 300) : 300),
    loopCount: (STORAGE ? STORAGE.get("loopCount", 1) : 1),
    isPlaying: false,
    isPaused: false,
    currentMode: 'natural',
    currentPoint: false,
};

// =================== 演奏引擎 ===================

function clickCoord(px, py, label) {
    var pos = calcPos(px, py);
    log("  -> 点击 [" + label + "] @ (" + pos.x + ", " + pos.y + ")");
    click(pos.x, pos.y);
    sleep(80);
}

function playNotes(notes) {
    for (var loop = 0; loop < state.loopCount; loop++) {
        if (!state.isPlaying) break;
        for (var i = 0; i < notes.length; i++) {
            while (state.isPaused && state.isPlaying) { sleep(200); }
            if (!state.isPlaying) break;

            var note = notes[i];
            updateStatus("[" + (i + 1) + "/" + notes.length + "] " + describeNote(note));

            if (note.type === 'rest') {
                sleep(state.speed);
                continue;
            }

            // 切换模式
            if (state.currentMode !== note.mode) {
                var target = state.coords.modes[note.mode];
                updateStatus("切换到 " + modeName(note.mode));
                clickCoord(target.px, target.py, modeName(note.mode));
                state.currentMode = note.mode;
                sleep(state.switchDelay);
            }

            // 切换低音点
            if (state.currentPoint !== note.lowPoint) {
                var pt = state.coords.point;
                updateStatus("低音点 " + (note.lowPoint ? '开' : '关'));
                clickCoord(pt.px, pt.py, '低音点');
                state.currentPoint = note.lowPoint;
                sleep(150);
            }

            // 点击音符
            var nt = state.coords.notes[note.key];
            if (nt) {
                clickCoord(nt.px, nt.py, note.key);
            }

            sleep(state.speed);
        }
    }
}

function describeNote(n) {
    if (n.type === 'rest') return '休止';
    return modeName(n.mode) + ' ' + n.key + (n.lowPoint ? '(低)' : '');
}

function modeName(m) {
    var names = { natural: '自然', sharp: '升调', flat: '降调', half: '半音' };
    return names[m] || m;
}

// =================== 悬浮窗 ===================
var floatyWindow = null;
var statusTextView = null;

function createFloaty() {
    if (floatyWindow) return;
    floatyWindow = floaty.window(
        <frame gravity="center">
            <card w="320" h="auto" cardCornerRadius="12"
                  cardBackgroundColor="#CC222222">
                <vertical padding="12">
                    <text text="口琴演奏器" textColor="#FFAA55" textSize="16sp" gravity="center"/>
                    <text id="ft_status" text="就绪" textColor="#CCCCCC" textSize="12sp"
                          marginTop="6" gravity="center"/>
                    <horizontal marginTop="10" gravity="center">
                        <button id="ft_play" text="演奏" w="80" h="36"/>
                        <button id="ft_stop" text="停止" w="80" h="36" marginLeft="6"/>
                        <button id="ft_hide" text="隐藏" w="60" h="36" marginLeft="6"/>
                    </horizontal>
                </vertical>
            </card>
        </frame>
    );

    statusTextView = floatyWindow.ft_status;

    floatyWindow.setTouchable(true);
    floatyWindow.addTouchListener(function(view, event) {
        if (event.getAction() === event.ACTION_MOVE) {
            floatyWindow.setPosition(parseInt(event.getRawX()) - 160, parseInt(event.getRawY()) - 40);
        }
        return true;
    });

    floatyWindow.ft_play.click(function() {
        if (state.isPlaying) {
            state.isPaused = !state.isPaused;
            floatyWindow.ft_play.setText(state.isPaused ? "继续" : "暂停");
        } else {
            startPlay();
        }
    });

    floatyWindow.ft_stop.click(function() {
        state.isPlaying = false;
        state.isPaused = false;
        state.currentMode = 'natural';
        state.currentPoint = false;
        toast("已停止");
        floatyWindow.ft_play.setText("演奏");
    });

    floatyWindow.ft_hide.click(function() {
        floatyWindow.close();
        floatyWindow = null;
    });
}

function updateStatus(text) {
    if (statusTextView) statusTextView.setText(text);
    else log(text);
}

// =================== 主 UI ===================
ui.layout(
    <vertical padding="16" bg="#1A1A2E">
        <text text="三角洲口琴自动演奏" textColor="#FFAA55" textSize="20sp"
              gravity="center" marginTop="8"/>

        <card w="*" h="auto" margin="0 10 10 0" cardCornerRadius="12"
              cardBackgroundColor="#222244">
            <vertical padding="12">
                <text text="口琴谱" textColor="#FFAA55" textSize="14sp"/>
                <input id="sheet" text="" w="*" h="100" textColor="#E0E0E0"
                       bg="#1A1A2E" textSize="14sp" marginTop="6"/>

                <horizontal marginTop="8">
                    <button id="ex_star" text="小星星" w="auto" h="30" textColor="#667EEA"/>
                    <button id="ex_twinkle" text="欢乐颂" w="auto" h="30" textColor="#667EEA" marginLeft="6"/>
                    <button id="ex_mix" text="升降调示例" w="auto" h="30" textColor="#667EEA" marginLeft="6"/>
                </horizontal>
            </vertical>
        </card>

        <card w="*" h="auto" margin="0 10 10 0" cardCornerRadius="12"
              cardBackgroundColor="#222244">
            <vertical padding="12">
                <text text="演奏参数" textColor="#FFAA55" textSize="14sp"/>

                <horizontal marginTop="8" gravity="center_vertical">
                    <text text="速度(ms)" w="70" textColor="#CCCCCC" textSize="13sp"/>
                    <input id="speed" text="400" w="60" h="36"
                           textColor="#E0E0E0" bg="#1A1A2E" textSize="13sp"/>
                    <text text="变调延时" w="70" textColor="#CCCCCC" textSize="13sp" marginLeft="10"/>
                    <input id="switchDelay" text="300" w="60" h="36"
                           textColor="#E0E0E0" bg="#1A1A2E" textSize="13sp"/>
                    <text text="循环" w="40" textColor="#CCCCCC" textSize="13sp" marginLeft="10"/>
                    <input id="loopCount" text="1" w="50" h="36"
                           textColor="#E0E0E0" bg="#1A1A2E" textSize="13sp"/>
                </horizontal>
            </vertical>
        </card>

        <horizontal marginTop="10" gravity="center">
            <button id="btn_test" text="预览" w="90" h="42"/>
            <button id="btn_start" text="开始演奏" w="110" h="42" marginLeft="20"/>
        </horizontal>

        <card w="*" h="auto" margin="0 10 10 0" cardCornerRadius="12"
              cardBackgroundColor="#222244" marginTop="12">
            <vertical padding="12">
                <text text="坐标标定" textColor="#FFAA55" textSize="14sp"/>
                <text text="建议先标定一次坐标，确保自动点击准确。"
                      textColor="#888888" textSize="12sp" marginTop="4"/>
                <horizontal marginTop="8">
                    <button id="btn_calibrate" text="开始标定" w="100" h="36"/>
                    <button id="btn_viewcoords" text="查看坐标" w="100" h="36" marginLeft="10"/>
                    <button id="btn_resetcoords" text="恢复默认" w="100" h="36" marginLeft="10"/>
                </horizontal>
            </vertical>
        </card>

        <card w="*" h="auto" margin="0 10 10 0" cardCornerRadius="12"
              cardBackgroundColor="#222244" marginTop="8">
            <vertical padding="12">
                <text text="运行日志" textColor="#FFAA55" textSize="14sp"/>
                <scroll h="120" marginTop="6">
                    <text id="log" text="" textColor="#00FF88" textSize="12sp"/>
                </scroll>
            </vertical>
        </card>
    </vertical>
);

// =================== UI 事件 ===================
ui.ex_star.click(function() {
    ui.sheet.setText("1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 -");
});
ui.ex_twinkle.click(function() {
    ui.sheet.setText("3 3 4 5 5 4 3 2 1 1 2 3 3 2 2 -");
});
ui.ex_mix.click(function() {
    ui.sheet.setText("#1 #2 #3 #4 #5 - b3 b6 b7 - 1. 2. 3. 4. 5.");
});

function log(msg) {
    try {
        ui.run(function() {
            if (ui.log) {
                var current = String(ui.log.text() || "");
                ui.log.setText(current + msg + "\n");
            }
        });
    } catch (e) {
        // UI 未就绪时忽略
    }
    console.log(msg);
}

ui.btn_test.click(function() {
    var notes = parseSheet(ui.sheet.getText());
    if (!notes.length) {
        toast("谱子为空或无法解析");
        return;
    }
    log("解析成功: " + notes.length + " 个音符");
    for (var i = 0; i < notes.length; i++) {
        var n = notes[i];
        if (n.type === 'rest') {
            log("  " + (i + 1) + ". 休止");
        } else {
            log("  " + (i + 1) + ". " + modeName(n.mode) + " " + n.key + (n.lowPoint ? '(低)' : ''));
        }
    }
    toast("预览完成，共 " + notes.length + " 个音符");
});

ui.btn_start.click(function() {
    if (!requestPermission()) return;

    state.sheet = String(ui.sheet.getText());
    state.speed = parseInt(String(ui.speed.getText())) || 400;
    state.switchDelay = parseInt(String(ui.switchDelay.getText())) || 300;
    state.loopCount = parseInt(String(ui.loopCount.getText())) || 1;

    if (STORAGE) {
        STORAGE.put("sheet", state.sheet);
        STORAGE.put("speed", state.speed);
        STORAGE.put("switchDelay", state.switchDelay);
        STORAGE.put("loopCount", state.loopCount);
    }

    var notes = parseSheet(state.sheet);
    if (!notes.length) {
        toast("谱子为空或无法解析");
        return;
    }

    toast("准备演奏：" + notes.length + " 个音符");
    log("=== 开始演奏 (速度=" + state.speed + "ms, 循环=" + state.loopCount + ") ===");

    createFloaty();
    state.isPlaying = true;
    state.isPaused = false;
    state.currentMode = 'natural';
    state.currentPoint = false;

    threads.start(function() {
        try {
            // 在子线程里倒计时，不阻塞UI
            toast("3秒后开始自动演奏，请切到三角洲游戏！");
            sleep(3000);
            playNotes(notes);
            log("=== 演奏结束 ===");
            state.isPlaying = false;
            ui.run(function() { ui.btn_start.setText("开始演奏"); });
            if (floatyWindow) floatyWindow.ft_play.setText("演奏");
            toast("演奏完成！");
        } catch (e) {
            log("错误: " + e);
            toast("出错了: " + e);
        }
    });
});

function startPlay() {
    state.sheet = String(ui.sheet.getText());
    state.speed = parseInt(String(ui.speed.getText())) || 400;
    state.switchDelay = parseInt(String(ui.switchDelay.getText())) || 300;
    state.loopCount = parseInt(String(ui.loopCount.getText())) || 1;

    var notes = parseSheet(state.sheet);
    if (!notes.length) { toast("谱子为空"); return; }

    state.isPlaying = true;
    state.isPaused = false;
    state.currentMode = 'natural';
    state.currentPoint = false;

    threads.start(function() {
        try { playNotes(notes); } catch (e) { log("错误: " + e); }
        state.isPlaying = false;
        if (floatyWindow) floatyWindow.ft_play.setText("演奏");
    });
}

// =================== 权限检查 ===================
function requestPermission() {
    // 检查无障碍服务
    var hasAccessibility = false;
    try {
        if (typeof auto !== 'undefined' && auto.service) {
            if (typeof auto.service.connected === 'boolean') {
                hasAccessibility = auto.service.connected;
            } else if (typeof auto.service.enabled === 'boolean') {
                hasAccessibility = auto.service.enabled;
            } else {
                hasAccessibility = true; // 假设已连接
            }
        }
    } catch (e) {
        log("无障碍检查异常: " + e);
    }

    if (!hasAccessibility) {
        dialogs.confirm("需要无障碍服务",
            "请先在系统设置中开启 Auto.js 的无障碍服务，然后返回本脚本点击确定继续。",
            function(ok) {
                if (ok) {
                    try {
                        if (typeof auto !== 'undefined' && auto.settings) {
                            auto.settings();
                        } else if (typeof settings !== 'undefined') {
                            settings.openAccessibilitySettings();
                        }
                    } catch (e) {
                        toast("请手动到设置里开启无障碍服务");
                    }
                }
            });
        return false;
    }

    // 检查悬浮窗权限
    try {
        if (!floaty.hasPermission()) {
            toast("需要悬浮窗权限");
            floaty.requestPermission();
            return false;
        }
    } catch (e) {
        log("悬浮窗检查异常: " + e);
    }

    return true;
}

// =================== 坐标标定 ===================
ui.btn_calibrate.click(function() { runCalibration(); });

ui.btn_viewcoords.click(function() {
    var c = state.coords;
    var lines = [];
    lines.push("=== 当前坐标 (百分比) ===");
    lines.push("【模式按钮】");
    for (var k in c.modes) {
        lines.push("  " + modeName(k) + ": (" + (c.modes[k].px * 100).toFixed(1) + "%, " + (c.modes[k].py * 100).toFixed(1) + "%)");
    }
    lines.push("【音符按钮】");
    for (var k2 in c.notes) {
        lines.push("  " + k2 + ": (" + (c.notes[k2].px * 100).toFixed(1) + "%, " + (c.notes[k2].py * 100).toFixed(1) + "%)");
    }
    lines.push("【低音点】");
    lines.push("  point: (" + (c.point.px * 100).toFixed(1) + "%, " + (c.point.py * 100).toFixed(1) + "%)");

    dialogs.alert("当前坐标", lines.join("\n"));
});

ui.btn_resetcoords.click(function() {
    state.coords = DEFAULT_COORDS;
    saveCoords(state.coords);
    toast("已恢复默认坐标");
});

function runCalibration() {
    var target = dialogs.select(
        "标定哪个按钮？",
        ["模式按钮组", "音符按钮组(1-7+i)", "低音点按钮", "全部重新标定"]
    );
    if (target < 0) return;

    // 标定流程里有 sleep，必须在子线程运行，不能阻塞 UI 线程
    threads.start(function() {
        startCalibrationOverlay(target);
    });
}

/**
 * 全屏半透明标定覆盖层
 * 用户对照游戏界面，拖动浮层上的标记点对齐到按钮中心
 */
function startCalibrationOverlay(target) {
    toast("即将启动标定覆盖层");

    var items = [];
    if (target === 0 || target === 3) {
        items.push({ group: 'modes', key: 'natural', label: '自然音' });
        items.push({ group: 'modes', key: 'sharp', label: '升调' });
        items.push({ group: 'modes', key: 'flat', label: '降调' });
        items.push({ group: 'modes', key: 'half', label: '半音' });
    }
    if (target === 1 || target === 3) {
        var noteKeys = ['1', '2', '3', '4', '5', '6', '7', 'i'];
        for (var i = 0; i < noteKeys.length; i++) {
            items.push({ group: 'notes', key: noteKeys[i], label: '音符 ' + noteKeys[i] });
        }
    }
    if (target === 2 || target === 3) {
        items.push({ group: 'point', key: 'point', label: '低音点' });
    }

    toast("3秒后请切到三角洲游戏界面，横屏打开口琴演奏界面");
    sleep(3000);

    var idx = 0;

    function showNext() {
        if (idx >= items.length) {
            saveCoords(state.coords);
            toast("标定完成！");
            ui.run(function() { log("坐标已保存"); });
            return;
        }

        var item = items[idx];
        var cur = calcPos(
            state.coords[item.group][item.key].px,
            state.coords[item.group][item.key].py
        );

        var overlay = floaty.rawWindow(
            <frame bg="#66FF0000">
                <vertical w="*" h="*" gravity="center">
                    <card w="auto" h="auto" cardCornerRadius="10"
                          cardBackgroundColor="#CC222222" padding="12">
                        <vertical>
                            <text text={"标定 (" + (idx + 1) + "/" + items.length + ")"}
                                  textColor="#FFAA55" textSize="14sp" gravity="center"/>
                            <text text={item.label} textColor="#FF8888"
                                  textSize="22sp" gravity="center" marginTop="4"/>
                            <text text="拖动标记点对齐到按钮中心"
                                  textColor="#AAAAAA" textSize="11sp"
                                  gravity="center" marginTop="4"/>
                        </vertical>
                    </card>
                </vertical>

                <frame id="marker" w="60" h="60" bg="#88FF0000">
                    <text w="*" h="*" text="++" textColor="#FFFFFF" textSize="20sp"
                          gravity="center"/>
                </frame>

                <frame id="confirmBtn" w="120" h="44" bg="#FFAA2255">
                    <text w="*" h="*" text="确认位置" textColor="#FFFFFF"
                          textSize="14sp" gravity="center"/>
                </frame>
                <frame id="skipBtn" w="80" h="44" bg="#888888">
                    <text w="*" h="*" text="跳过" textColor="#FFFFFF"
                          textSize="14sp" gravity="center"/>
                </frame>
                <frame id="cancelBtn" w="80" h="44" bg="#FF4444">
                    <text w="*" h="*" text="取消" textColor="#FFFFFF"
                          textSize="14sp" gravity="center"/>
                </frame>
            </frame>
        );

        var w = Math.max(device.width, device.height);
        var h = Math.min(device.width, device.height);

        overlay.setTouchable(true);
        overlay.setPosition(0, 0);
        overlay.setSize(w, h);

        overlay.marker.setPosition(cur.x - 30, cur.y - 30);
        overlay.confirmBtn.setPosition(w - 260, h - 80);
        overlay.skipBtn.setPosition(w - 140, h - 80);
        overlay.cancelBtn.setPosition(w - 50, h - 80);

        var markerOffset = null;
        overlay.marker.setOnTouchListener(function(view, event) {
            switch (event.getAction()) {
                case event.ACTION_DOWN:
                    markerOffset = {
                        dx: event.getRawX() - view.getX(),
                        dy: event.getRawY() - view.getY()
                    };
                    break;
                case event.ACTION_MOVE:
                    if (markerOffset) {
                        view.setX(event.getRawX() - markerOffset.dx);
                        view.setY(event.getRawY() - markerOffset.dy);
                    }
                    break;
            }
            return true;
        });

        overlay.confirmBtn.setOnTouchListener(function(view, event) {
            if (event.getAction() === event.ACTION_UP) {
                var mx = overlay.marker.getX() + 30;
                var my = overlay.marker.getY() + 30;
                var px = mx / w;
                var py = my / h;
                state.coords[item.group][item.key] = { px: px, py: py };
                overlay.close();
                idx++;
                showNext();
            }
            return true;
        });

        overlay.skipBtn.setOnTouchListener(function(view, event) {
            if (event.getAction() === event.ACTION_UP) {
                overlay.close();
                idx++;
                showNext();
            }
            return true;
        });

        overlay.cancelBtn.setOnTouchListener(function(view, event) {
            if (event.getAction() === event.ACTION_UP) {
                overlay.close();
                toast("标定已取消");
            }
            return true;
        });
    }

    showNext();
}

// =================== 启动初始化 ===================
// 注意：在 "ui" 模式下，UI 线程正在渲染界面，
// 不能在这里启动新线程立即调用 ui.run（会导致白屏）
// 用 setTimeout 延迟到 UI 渲染完成后再执行
setTimeout(function() {
    try {
        log("脚本启动成功！");
        log("请先开启：无障碍服务 + 悬浮窗权限");
        log("首次使用请点击「开始标定」校准坐标");
    } catch (e) {
        // 忽略，UI 可能还没完全就绪
    }
}, 800);
