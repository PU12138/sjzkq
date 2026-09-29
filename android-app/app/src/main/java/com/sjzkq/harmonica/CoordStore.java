package com.sjzkq.harmonica;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * 坐标存储管理
 * 坐标用相对比例 (0~1) 存储，适配不同分辨率
 */
public class CoordStore {

    private static final String PREFS_NAME = "harmonica_coords";
    private static final String KEY_COORDS = "coords";

    // 默认坐标（相对比例，按常见横屏布局估算）
    public static final Map<String, float[]> DEFAULT_COORDS = new HashMap<>();
    static {
        // 模式按钮：半音、升调、自然音、降调
        DEFAULT_COORDS.put("mode_half",   new float[]{0.30f, 0.30f});
        DEFAULT_COORDS.put("mode_sharp",  new float[]{0.42f, 0.30f});
        DEFAULT_COORDS.put("mode_natural",new float[]{0.54f, 0.30f});
        DEFAULT_COORDS.put("mode_flat",   new float[]{0.66f, 0.30f});
        // 音符按钮：1 2 3 4 5 6 7 i
        DEFAULT_COORDS.put("note_1", new float[]{0.18f, 0.62f});
        DEFAULT_COORDS.put("note_2", new float[]{0.28f, 0.62f});
        DEFAULT_COORDS.put("note_3", new float[]{0.38f, 0.62f});
        DEFAULT_COORDS.put("note_4", new float[]{0.48f, 0.62f});
        DEFAULT_COORDS.put("note_5", new float[]{0.58f, 0.62f});
        DEFAULT_COORDS.put("note_6", new float[]{0.68f, 0.62f});
        DEFAULT_COORDS.put("note_7", new float[]{0.78f, 0.62f});
        DEFAULT_COORDS.put("note_i", new float[]{0.88f, 0.62f});
        // 低音点
        DEFAULT_COORDS.put("point", new float[]{0.50f, 0.82f});
    }

    private final SharedPreferences prefs;

    public CoordStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 获取所有坐标（相对比例） */
    public Map<String, float[]> getCoords() {
        String json = prefs.getString(KEY_COORDS, null);
        Map<String, float[]> coords = new HashMap<>();
        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                for (String key : DEFAULT_COORDS.keySet()) {
                    if (obj.has(key)) {
                        JSONObject p = obj.getJSONObject(key);
                        coords.put(key, new float[]{(float)p.getDouble("x"), (float)p.getDouble("y")});
                    }
                }
            } catch (Exception e) {
                // 解析失败用默认
            }
        }
        // 补齐缺失的默认坐标
        for (String key : DEFAULT_COORDS.keySet()) {
            if (!coords.containsKey(key)) {
                coords.put(key, DEFAULT_COORDS.get(key).clone());
            }
        }
        return coords;
    }

    /** 保存坐标 */
    public void saveCoords(Map<String, float[]> coords) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, float[]> e : coords.entrySet()) {
                JSONObject p = new JSONObject();
                p.put("x", e.getValue()[0]);
                p.put("y", e.getValue()[1]);
                obj.put(e.getKey(), p);
            }
            prefs.edit().putString(KEY_COORDS, obj.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 重置为默认坐标 */
    public void resetCoords() {
        Map<String, float[]> defaults = new HashMap<>();
        for (Map.Entry<String, float[]> e : DEFAULT_COORDS.entrySet()) {
            defaults.put(e.getKey(), e.getValue().clone());
        }
        saveCoords(defaults);
    }
}
