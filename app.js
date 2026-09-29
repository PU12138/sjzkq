/**
 * 三角洲口琴自动演奏器
 * 解析口琴谱并自动模拟按键/点击
 */

// =============== 状态管理 ===============
const state = {
    currentMode: 'natural', // natural | sharp | flat | half
    currentPoint: false,    // 低音点 .
    isPlaying: false,
    isPaused: false,
    notes: [],
    currentIndex: 0,
    playTimer: null,
};

// =============== 音符解析器 ===============
// 支持的格式:
//   1-7 自然音
//   i    高音do (1·)
//   #1   升调
//   b3   降调
//   1.   低音点
//   .1   更高音点
//   _    休止符
//   -    延时/延长
class NoteParser {
    static parse(text) {
        if (!text || !text.trim()) return [];

        const tokens = text
            .replace(/[，、]/g, ' ')
            .replace(/[\n\r]+/g, ' ')
            .split(/\s+/)
            .filter(t => t.length > 0);

        const notes = [];

        for (const token of tokens) {
            const note = this._parseToken(token);
            if (note) notes.push(note);
        }

        return notes;
    }

    static _parseToken(token) {
        // 休止符 / 延长
        if (token === '_' || token === '-' || token === '|') {
            return { type: 'rest', raw: token };
        }

        // 匹配: [.#]? [#b]? [1-7i] [.]?
        // 先处理前缀和后缀
        let mode = 'natural';   // 变调
        let highPoint = false;  // 高音点 (. 前缀)
        let lowPoint = false;   // 低音点 (. 后缀)
        let noteKey = null;

        // 检查高音点前缀 (点在前面)
        if (token.startsWith('.')) {
            highPoint = true;
            token = token.substring(1);
        }

        // 检查升/降调前缀
        if (token.startsWith('#')) {
            mode = 'sharp';
            token = token.substring(1);
        } else if (token.startsWith('b') || token.startsWith('B')) {
            mode = 'flat';
            token = token.substring(1);
        } else if (token.startsWith('h') || token.startsWith('H')) {
            mode = 'half';
            token = token.substring(1);
        }

        // 检查低音点后缀
        if (token.endsWith('.')) {
            lowPoint = true;
            token = token.substring(0, token.length - 1);
        }

        // 提取主音符
        if (/^[1-7]$/.test(token)) {
            noteKey = token;
        } else if (token.toLowerCase() === 'i' || token === '·' || token === '1̇') {
            noteKey = 'i';
        } else {
            return null; // 无法识别
        }

        // 如果同时有高/低音点，高音点优先（互斥）
        if (highPoint && lowPoint) lowPoint = false;

        return {
            type: 'note',
            key: noteKey,          // 1-7 或 i
            mode,                  // natural | sharp | flat | half
            highPoint,             // 高音点 (更高音域)
            lowPoint,              // 低音点 (降低八度)
            raw: token + (mode === 'sharp' ? '#' : mode === 'flat' ? 'b' : ''),
        };
    }
}

// =============== 键盘映射 ===============
// 音符按键：1-7 对应音符1-7，8 对应高音i
const KEYMAP = {
    // 音符 -> 键盘按键
    '1': '1', '2': '2', '3': '3', '4': '4',
    '5': '5', '6': '6', '7': '7', 'i': '8',
    // 低音点
    'point': '`',
    // 模式切换
    'modes': {
        'natural': 'q',
        'sharp': 'w',
        'flat': 'e',
        'half': 'r',
    }
};

// =============== 模式切换逻辑 ===============
// 在游戏中，模式按钮是互斥的：点升调 -> 切换到升调，点自然音 -> 切回自然音
function needModeSwitch(targetMode) {
    return state.currentMode !== targetMode;
}

function needPointSwitch(targetPoint) {
    return state.currentPoint !== targetPoint;
}

// =============== 演奏引擎 ===============
class PlayEngine {
    constructor(onStep, onComplete, onError) {
        this.onStep = onStep;
        this.onComplete = onComplete;
        this.onError = onError;
    }

    async play(notes, loopCount, speed, switchDelay) {
        state.isPlaying = true;
        state.isPaused = false;
        state.currentIndex = 0;

        for (let loop = 0; loop < loopCount && state.isPlaying; loop++) {
            for (let i = 0; i < notes.length && state.isPlaying; i++) {
                // 暂停处理
                while (state.isPaused && state.isPlaying) {
                    await this._sleep(100);
                }
                if (!state.isPlaying) break;

                const note = notes[i];
                state.currentIndex = i;

                await this._playNote(note, speed, switchDelay);
            }
        }

        if (this.onComplete) this.onComplete();
        state.isPlaying = false;
    }

    async _playNote(note, speed, switchDelay) {
        if (!note || note.type === 'rest') {
            this.onStep && this.onStep({ rest: true });
            await this._sleep(speed);
            return;
        }

        const steps = [];

        // 1. 模式切换
        if (needModeSwitch(note.mode)) {
            steps.push({ type: 'mode', value: note.mode, delay: switchDelay });
            state.currentMode = note.mode;
        }

        // 2. 低音点切换（如果需要）
        if (needPointSwitch(note.lowPoint)) {
            steps.push({ type: 'point', value: note.lowPoint, delay: 50 });
            state.currentPoint = note.lowPoint;
        }

        // 3. 高音点处理（特殊标记，需切换到更高音域，某些谱用低音点代替）
        // 此处简化：高音点视为点切换状态

        // 4. 演奏音符
        steps.push({ type: 'note', value: note, delay: speed });

        // 执行步骤
        for (const step of steps) {
            this.onStep && this.onStep(step);
            await this._sleep(step.delay || speed);
        }
    }

    _sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    stop() {
        state.isPlaying = false;
        state.isPaused = false;
        state.currentMode = 'natural';
        state.currentPoint = false;
    }

    pause() {
        state.isPaused = true;
    }

    resume() {
        state.isPaused = false;
    }
}

// =============== UI 控制 ===============
document.addEventListener('DOMContentLoaded', () => {
    const $ = id => document.getElementById(id);

    const noteBtns = document.querySelectorAll('.note-btn');
    const modeBtns = document.querySelectorAll('.mode-btn');
    const parsedNotes = $('parsedNotes');
    const currentNoteDisplay = $('currentNote');
    const statusEl = $('status');
    const progressFill = $('progressFill');

    let engine = null;

    // --- Tab 切换 ---
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
            btn.classList.add('active');
            $(btn.dataset.tab + '-tab').classList.add('active');
        });
    });

    // --- 示例谱 ---
    const examples = {
        star: `1 1 5 5 6 6 5 - 4 4 3 3 2 2 1 -`,
        twinkle: `3 3 4 5 5 4 3 2 1 1 2 3 3 2 - 3 3 4 5 5 4 3 2 1 1 2 3 2 1 -`,
        ode: `3 3 4 5 5 4 3 2 1 1 2 3 3 2 2 -`,
        birthday: `5 5 6 5 i 7 - 5 5 6 5 .i 7 6 -`,
        mix: `#1 #2 #3 #4 #5 - b3 b6 b7 - 1. 2. 3. 4. 5.`,
    };

    document.querySelectorAll('.example-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            $('sheetMusic').value = examples[btn.dataset.ex];
            parseAndShow();
        });
    });

    // --- 文件上传 ---
    $('fileUpload').addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = (ev) => {
            $('sheetMusic').value = ev.target.result;
            // 切换到文本标签
            document.querySelector('.tab-btn[data-tab="text"]').click();
            parseAndShow();
        };
        reader.readAsText(file);
    });

    // --- 解析显示 ---
    function parseAndShow() {
        const text = $('sheetMusic').value;
        const notes = NoteParser.parse(text);
        state.notes = notes;
        renderParsed(notes);
        setStatus(`已解析 ${notes.length} 个音符`);
        return notes;
    }

    function renderParsed(notes) {
        if (!notes.length) {
            parsedNotes.innerHTML = '<span style="color:#888">暂无数据</span>';
            return;
        }
        parsedNotes.innerHTML = notes.map((n, i) => {
            if (n.type === 'rest') {
                return `<span class="note-item" style="color:#888">⟳</span>`;
            }
            const modeMark = { natural: '', sharp: '♯', flat: '♭', half: '½' }[n.mode];
            const pointMark = n.lowPoint ? '·↓' : n.highPoint ? '·↑' : '';
            return `<span class="note-item" data-idx="${i}">${modeMark}${n.key}${pointMark}</span>`;
        }).join('');
    }

    function highlightCurrent(idx) {
        parsedNotes.querySelectorAll('.note-item').forEach(el => {
            el.classList.toggle('current', parseInt(el.dataset.idx) === idx);
        });
    }

    // --- 模式按钮视觉 ---
    function setModeVisual(mode) {
        modeBtns.forEach(b => b.classList.toggle('active', b.dataset.mode === mode));
    }

    function setNoteVisual(noteKey, active) {
        noteBtns.forEach(b => {
            if (b.dataset.note === noteKey) {
                b.classList.toggle('active', active);
            }
        });
    }

    // --- 手动点击（测试用） ---
    noteBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const n = btn.dataset.note;
            triggerNotePlayback(n, state.currentMode, state.currentPoint);
        });
    });

    modeBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            state.currentMode = btn.dataset.mode;
            setModeVisual(state.currentMode);
            updatePointIndicator();
        });
    });

    $('pointState').addEventListener('click', () => {
        state.currentPoint = !state.currentPoint;
        updatePointIndicator();
    });

    function updatePointIndicator() {
        const label = state.currentPoint ? '✓ 低音点开启' : '· 低音点关闭';
        $('pointState').textContent = label;
    }

    // --- 模拟按键触发（可选：连接 AutoHotkey 等） ---
    function triggerNotePlayback(noteKey, mode, lowPoint) {
        // 界面反馈
        setNoteVisual(noteKey, true);
        currentNoteDisplay.textContent = noteKey;
        currentNoteDisplay.style.color = modeColor(mode);
        setTimeout(() => setNoteVisual(noteKey, false), 200);

        // 如果选择键盘模拟模式，发送键盘事件（模拟在游戏中按对应按键）
        const modeKey = KEYMAP.modes[mode];
        const noteKeyPress = KEYMAP[noteKey];

        // 这里可以用 Robocopy/AutoHotkey/Python 等来向游戏窗口发送真实按键
        // 我们输出到 console，用户可以自己配合脚本使用
        console.log(`[Key] mode=${modeKey}, note=${noteKeyPress}, point=${lowPoint ? KEYMAP.point : ''}`);
    }

    function modeColor(mode) {
        return {
            natural: '#2ecc71',
            sharp: '#e94560',
            flat: '#3498db',
            half: '#9b59b6',
        }[mode];
    }

    // --- 演奏按钮 ---
    $('btnParse').addEventListener('click', parseAndShow);

    $('btnPlay').addEventListener('click', async () => {
        if (!state.notes.length) parseAndShow();
        if (!state.notes.length) return;

        const speed = parseInt($('speed').value);
        const switchDelay = parseInt($('switchDelay').value);
        const loopCount = parseInt($('loopCount').value);

        state.currentMode = 'natural';
        state.currentPoint = false;
        setModeVisual('natural');
        updatePointIndicator();

        engine = new PlayEngine(
            (step) => handleStep(step),
            () => {
                setStatus('✅ 演奏完成');
                setNoteVisual();
                progressFill.style.width = '100%';
            }
        );

        setStatus('▶️ 演奏中...');
        await engine.play(state.notes, loopCount, speed, switchDelay);
    });

    function handleStep(step) {
        if (step.rest) {
            currentNoteDisplay.textContent = '⟳';
            return;
        }

        if (step.type === 'mode') {
            state.currentMode = step.value;
            setModeVisual(step.value);
            setStatus(`🔀 切换到 ${modeName(step.value)}`);
        } else if (step.type === 'point') {
            state.currentPoint = step.value;
            updatePointIndicator();
            setStatus(`🎵 低音点 ${step.value ? '开启' : '关闭'}`);
        } else if (step.type === 'note') {
            const n = step.value;
            triggerNotePlayback(n.key, n.mode, n.lowPoint);

            // 更新进度
            const total = state.notes.length;
            progressFill.style.width = `${(state.currentIndex / total) * 100}%`;
            highlightCurrent(state.currentIndex);

            setStatus(`🎶 正在演奏: ${modeName(n.mode)} ${n.key}${n.lowPoint ? '·' : ''}`);
        }
    }

    function modeName(mode) {
        return { natural: '自然音', sharp: '升调', flat: '降调', half: '半音' }[mode] || mode;
    }

    $('btnStop').addEventListener('click', () => {
        if (engine) engine.stop();
        state.currentMode = 'natural';
        state.currentPoint = false;
        setModeVisual('natural');
        updatePointIndicator();
        setStatus('⏹️ 已停止');
        progressFill.style.width = '0%';
        highlightCurrent(-1);
    });

    $('btnPause').addEventListener('click', () => {
        if (!engine) return;
        if (state.isPaused) {
            engine.resume();
            setStatus('▶️ 继续演奏');
            $('btnPause').textContent = '⏸️ 暂停';
        } else {
            engine.pause();
            setStatus('⏸️ 已暂停');
            $('btnPause').textContent = '▶️ 继续';
        }
    });

    function setStatus(text) {
        statusEl.textContent = text;
    }

    // 默认加载示例
    $('sheetMusic').value = examples.star;
    parseAndShow();
});
