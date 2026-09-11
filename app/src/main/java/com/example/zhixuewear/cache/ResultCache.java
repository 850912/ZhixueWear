package com.example.zhixuewear.cache;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.zhixuewear.model.Exam;
import com.example.zhixuewear.model.ExamResult;
import com.example.zhixuewear.model.ScoreItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ResultCache {
    private static final String PREF = "result_cache";
    private static final String KEY_JSON = "latest_result";
    private static final String KEY_UPDATED = "updated_at";
    private final SharedPreferences prefs;

    public ResultCache(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void save(ExamResult result) {
        try {
            JSONObject root = new JSONObject();
            root.put("examId", result.exam.id);
            root.put("examName", result.exam.name);
            root.put("createTime", result.exam.createTime);
            JSONArray arr = new JSONArray();
            for (ScoreItem score : result.scores) {
                JSONObject o = new JSONObject();
                o.put("subject", score.subject);
                o.put("score", score.score);
                o.put("fullScore", score.fullScore);
                arr.put(o);
            }
            root.put("scores", arr);
            prefs.edit()
                    .putString(KEY_JSON, root.toString())
                    .putLong(KEY_UPDATED, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) { }
    }

    public ExamResult load() {
        try {
            String raw = prefs.getString(KEY_JSON, null);
            if (raw == null) return null;
            JSONObject root = new JSONObject(raw);
            Exam exam = new Exam(
                    root.optString("examId"),
                    root.optString("examName"),
                    root.optString("createTime"));
            JSONArray arr = root.optJSONArray("scores");
            List<ScoreItem> scores = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    scores.add(new ScoreItem(
                            o.optString("subject"),
                            o.optDouble("score"),
                            o.optDouble("fullScore")));
                }
            }
            return scores.isEmpty() ? null : new ExamResult(exam, scores);
        } catch (Exception e) {
            return null;
        }
    }

    public long updatedAt() {
        return prefs.getLong(KEY_UPDATED, 0L);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
