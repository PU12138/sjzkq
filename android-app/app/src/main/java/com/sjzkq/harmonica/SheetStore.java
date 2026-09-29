package com.sjzkq.harmonica;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 琴谱库：保存多个命名琴谱
 */
public class SheetStore {

    private static final String PREFS_NAME = "harmonica_sheets";
    private static final String KEY_SHEETS = "sheets";

    public static class Sheet {
        public String name;
        public String content;
        public Sheet(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }

    private final SharedPreferences prefs;

    public SheetStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 获取所有琴谱 */
    public List<Sheet> getSheets() {
        List<Sheet> list = new ArrayList<>();
        String json = prefs.getString(KEY_SHEETS, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new Sheet(obj.getString("name"), obj.getString("content")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 保存琴谱列表 */
    public void saveSheets(List<Sheet> sheets) {
        try {
            JSONArray arr = new JSONArray();
            for (Sheet s : sheets) {
                JSONObject obj = new JSONObject();
                obj.put("name", s.name);
                obj.put("content", s.content);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_SHEETS, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 添加或更新琴谱 */
    public void addOrUpdate(Sheet sheet) {
        List<Sheet> list = getSheets();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(sheet.name)) {
                list.set(i, sheet);
                found = true;
                break;
            }
        }
        if (!found) list.add(sheet);
        saveSheets(list);
    }

    /** 删除琴谱 */
    public void delete(String name) {
        List<Sheet> list = getSheets();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equals(name)) list.remove(i);
        }
        saveSheets(list);
    }
}
