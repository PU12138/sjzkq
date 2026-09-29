package com.sjzkq.harmonica;

import java.util.ArrayList;
import java.util.List;

/**
 * 口琴谱解析器
 * 支持：
 *   1-7 自然音
 *   #1 #2 等升调
 *   b3 b6 等降调
 *   5. 低音点（数字后面跟点）
 *   i 高音
 *   - 或 _ 休止
 *   + 半音
 */
public class SheetParser {

    public enum Mode { NATURAL, SHARP, FLAT, HALF }

    public static class Note {
        public boolean isRest;
        public Mode mode;
        public String key;     // "1"-"7", "i"
        public boolean lowPoint;

        public static Note rest() {
            Note n = new Note();
            n.isRest = true;
            return n;
        }

        public static Note note(Mode mode, String key, boolean lowPoint) {
            Note n = new Note();
            n.isRest = false;
            n.mode = mode;
            n.key = key;
            n.lowPoint = lowPoint;
            return n;
        }
    }

    /**
     * 解析谱子文本
     */
    public static List<Note> parse(String sheet) {
        List<Note> notes = new ArrayList<>();
        if (sheet == null || sheet.trim().isEmpty()) return notes;

        // 统一空白符
        String s = sheet.replaceAll("[\\s,，、]+", " ");
        String[] tokens = s.split(" ");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            // 休止符
            if (token.equals("-") || token.equals("_")) {
                notes.add(Note.rest());
                continue;
            }

            Mode mode = Mode.NATURAL;
            boolean lowPoint = false;
            String key = null;

            // 解析前缀（升降半音）
            String body = token;
            while (body.length() > 0) {
                char c = body.charAt(0);
                if (c == '#') {
                    mode = Mode.SHARP;
                    body = body.substring(1);
                } else if (c == 'b' || c == 'B') {
                    mode = Mode.FLAT;
                    body = body.substring(1);
                } else if (c == '+') {
                    mode = Mode.HALF;
                    body = body.substring(1);
                } else {
                    break;
                }
            }

            // 解析低音点（后缀）
            if (body.endsWith(".")) {
                lowPoint = true;
                body = body.substring(0, body.length() - 1);
            }

            // 解析音符
            if (body.length() == 1) {
                char c = body.charAt(0);
                if (c >= '1' && c <= '7') {
                    key = String.valueOf(c);
                } else if (c == 'i' || c == 'I' || c == '8') {
                    key = "i";
                }
            }

            if (key != null) {
                notes.add(Note.note(mode, key, lowPoint));
            }
        }
        return notes;
    }

    public static String describe(Note n) {
        if (n.isRest) return "休止";
        String m = "";
        switch (n.mode) {
            case SHARP: m = "升调"; break;
            case FLAT: m = "降调"; break;
            case HALF: m = "半音"; break;
            default: m = "自然音";
        }
        return m + " " + n.key + (n.lowPoint ? "(低)" : "");
    }
}
