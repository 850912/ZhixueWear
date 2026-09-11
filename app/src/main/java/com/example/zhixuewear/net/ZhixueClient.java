package com.example.zhixuewear.net;

import android.util.Base64;

import com.example.zhixuewear.model.Exam;
import com.example.zhixuewear.model.ExamResult;
import com.example.zhixuewear.model.ScoreItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ZhixueClient {
    private static final String BASE = "https://www.zhixue.com";
    private static final String TOKEN_URL = BASE + "/container/app/token/getToken";
    private static final String USER_URL = BASE + "/container/getCurrentUser";
    private static final String YEAR_URL = BASE + "/zhixuebao/base/common/academicYear";
    private static final String RECENT_EXAM_URL = BASE + "/zhixuebao/report/exam/getRecentExam";
    private static final String EXAM_LIST_URL = BASE + "/zhixuebao/report/exam/getUserExamList";
    private static final String REPORT_URL = BASE + "/zhixuebao/report/exam/getReportMain";

    private final OkHttpClient http;
    private String cookie;
    private String xToken;
    private long xTokenAt;

    public ZhixueClient() {
        http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public void setCookie(String rawCookie) {
        cookie = normalizeCookie(rawCookie);
        xToken = null;
        xTokenAt = 0L;
    }

    public String getCookie() {
        return cookie;
    }

    public String validateSession() throws Exception {
        JSONObject root = getJson(USER_URL, false);
        JSONObject result = root.optJSONObject("result");
        if (result == null) throw new Exception("Cookie 已失效或服务器返回异常");
        String role = result.optString("role", "");
        if (!"student".equalsIgnoreCase(role)) {
            throw new Exception("当前仅支持学生账号，服务器返回角色：" + role);
        }
        String name = result.optString("name",
                result.optString("userName", result.optString("loginName", "学生")));
        return name.isEmpty() ? "学生" : name;
    }

    public ExamResult getLatestResult() throws Exception {
        JSONArray years = getJson(YEAR_URL, true).optJSONArray("result");
        if (years == null || years.length() == 0) {
            throw new Exception("未获取到学年列表");
        }

        Exception last = null;
        for (int i = 0; i < years.length(); i++) {
            JSONObject y = years.getJSONObject(i);
            String begin = y.optString("beginTime");
            String end = y.optString("endTime");
            try {
                JSONObject root = getJson(
                        RECENT_EXAM_URL + "?startSchoolYear=" + enc(begin)
                                + "&endSchoolYear=" + enc(end),
                        true);
                Object resultObj = root.opt("result");
                if (!(resultObj instanceof JSONObject)) continue;
                JSONObject result = (JSONObject) resultObj;
                JSONObject info = result.optJSONObject("examInfo");
                if (info == null) continue;

                Exam exam = new Exam(
                        info.optString("examId"),
                        info.optString("examName", "最近考试"),
                        info.optString("examCreateDateTime"));
                return new ExamResult(exam, getScores(exam.id));
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new Exception("没有找到可用的考试");
    }

    public List<Exam> getRecentExams(int maxCount) throws Exception {
        JSONArray years = getJson(YEAR_URL, true).optJSONArray("result");
        if (years == null) throw new Exception("未获取到学年列表");

        List<Exam> out = new ArrayList<>();
        for (int yi = 0; yi < years.length() && out.size() < maxCount; yi++) {
            JSONObject y = years.getJSONObject(yi);
            String begin = y.optString("beginTime");
            String end = y.optString("endTime");

            for (int page = 1; page <= 5 && out.size() < maxCount; page++) {
                String url = EXAM_LIST_URL
                        + "?pageIndex=" + page
                        + "&pageSize=10"
                        + "&startSchoolYear=" + enc(begin)
                        + "&endSchoolYear=" + enc(end);
                JSONObject root = getJson(url, true);
                JSONObject result = root.optJSONObject("result");
                if (result == null) break;
                JSONArray list = result.optJSONArray("examList");
                if (list == null || list.length() == 0) break;

                for (int i = 0; i < list.length() && out.size() < maxCount; i++) {
                    JSONObject e = list.getJSONObject(i);
                    out.add(new Exam(
                            e.optString("examId"),
                            e.optString("examName", "考试"),
                            e.optString("examCreateDateTime")));
                }
                if (!result.optBoolean("hasNextPage", false)) break;
            }
        }
        return out;
    }

    public ExamResult getResult(Exam exam) throws Exception {
        return new ExamResult(exam, getScores(exam.id));
    }

    private List<ScoreItem> getScores(String examId) throws Exception {
        JSONObject root = getJson(REPORT_URL + "?examId=" + enc(examId), true);
        JSONObject result = root.optJSONObject("result");
        if (result == null) throw new Exception("成绩接口未返回 result");

        List<ScoreItem> scores = new ArrayList<>();
        JSONArray paperList = result.optJSONArray("paperList");
        if (paperList != null) {
            for (int i = 0; i < paperList.length(); i++) {
                JSONObject s = paperList.getJSONObject(i);
                scores.add(new ScoreItem(
                        s.optString("subjectName", "科目"),
                        s.optDouble("userScore", 0),
                        s.optDouble("standardScore", 0)));
            }
        }

        JSONObject total = result.optJSONObject("totalScore");
        if (total != null) {
            scores.add(0, new ScoreItem(
                    total.optString("subjectName", "总分"),
                    total.optDouble("userScore", 0),
                    total.optDouble("standardScore", 0)));
        }
        if (scores.isEmpty()) throw new Exception("这场考试暂时没有可显示的成绩");
        return scores;
    }

    private JSONObject getJson(String url, boolean auth) throws Exception {
        if (cookie == null || cookie.trim().isEmpty()) {
            throw new Exception("尚未登录");
        }

        Request.Builder b = new Request.Builder()
                .url(url)
                .get()
                .header("Cookie", cookie)
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Wear OS) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                .header("Referer", BASE + "/");

        if (auth) {
            AuthHeaders h = authHeaders();
            b.header("authbizcode", "0001")
                    .header("authguid", h.guid)
                    .header("authtimestamp", h.timestamp)
                    .header("authtoken", h.authToken)
                    .header("XToken", h.xToken);
        }

        try (Response response = http.newCall(b.build()).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code() + ": " + shorten(body));
            }
            if (body.trim().startsWith("<")) {
                throw new Exception("服务器返回了网页而不是 JSON，Cookie 可能已过期");
            }
            return new JSONObject(body);
        }
    }

    private AuthHeaders authHeaders() throws Exception {
        long now = System.currentTimeMillis();
        if (xToken == null || now - xTokenAt >= 9 * 60 * 1000L) {
            String guid = UUID.randomUUID().toString();
            String ts = String.valueOf(now);
            String authToken = md5(guid + ts + "iflytek!@#123student");

            Request req = new Request.Builder()
                    .url(TOKEN_URL)
                    .get()
                    .header("Cookie", cookie)
                    .header("authbizcode", "0001")
                    .header("authguid", guid)
                    .header("authtimestamp", ts)
                    .header("authtoken", authToken)
                    .header("Accept", "application/json, text/plain, */*")
                    .build();

            try (Response response = http.newCall(req).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new Exception("获取 XToken 失败，HTTP " + response.code());
                }
                JSONObject json = new JSONObject(body);
                xToken = json.optString("result", "");
                if (xToken.isEmpty()) {
                    throw new Exception("获取 XToken 失败：" + shorten(body));
                }
                xTokenAt = System.currentTimeMillis();
            }
        }

        String guid = UUID.randomUUID().toString();
        String ts = String.valueOf(System.currentTimeMillis());
        String authToken = md5(guid + ts + "iflytek!@#123student");
        return new AuthHeaders(guid, ts, authToken, xToken);
    }

    private static String normalizeCookie(String raw) {
        if (raw == null) return "";
        String input = raw.trim();
        String value;

        // Cookie-Editor 默认导出的是 JSON 数组。允许用户把整段 JSON 原样粘贴。
        if (input.startsWith("[")) {
            value = cookieEditorJsonToHeader(input);
        } else {
            value = input
                    .replace("\r", "")
                    .replace("\n", "")
                    .replaceFirst("(?i)^cookie:\\s*", "");
        }

        if (!containsCookie(value, "uname") && containsCookie(value, "loginUserName")) {
            String username = cookieValue(value, "loginUserName");
            if (username != null && !username.isEmpty()) {
                String uname = Base64.encodeToString(
                        username.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                if (!value.endsWith(";")) value += ";";
                value += " uname=" + uname;
            }
        }
        return value;
    }

    private static String cookieEditorJsonToHeader(String jsonText) {
        try {
            JSONArray array = new JSONArray(jsonText);
            // Cookie-Editor 可能同时导出 .zhixue.com 和 www.zhixue.com 的同名 Cookie。
            // 对直接请求 www.zhixue.com 来说优先使用 hostOnly/www.zhixue.com 项。
            Map<String, JSONObject> chosen = new LinkedHashMap<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "").trim();
                if (name.isEmpty() || !item.has("value")) continue;

                JSONObject old = chosen.get(name);
                if (old == null || cookiePriority(item) >= cookiePriority(old)) {
                    chosen.put(name, item);
                }
            }

            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, JSONObject> entry : chosen.entrySet()) {
                if (out.length() > 0) out.append("; ");
                out.append(entry.getKey()).append("=")
                        .append(entry.getValue().optString("value", ""));
            }
            if (out.length() == 0) {
                throw new IllegalArgumentException("JSON 中没有找到 Cookie name/value");
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cookie-Editor JSON 格式无法解析：" + e.getMessage(), e);
        }
    }

    private static int cookiePriority(JSONObject item) {
        String domain = item.optString("domain", "");
        boolean hostOnly = item.optBoolean("hostOnly", false);
        if (hostOnly && "www.zhixue.com".equalsIgnoreCase(domain)) return 3;
        if ("www.zhixue.com".equalsIgnoreCase(domain)) return 2;
        if (domain.endsWith("zhixue.com")) return 1;
        return 0;
    }

    private static boolean containsCookie(String cookie, String name) {
        return cookieValue(cookie, name) != null;
    }

    private static String cookieValue(String cookie, String name) {
        for (String part : cookie.split(";")) {
            String p = part.trim();
            int idx = p.indexOf('=');
            if (idx <= 0) continue;
            if (p.substring(0, idx).trim().equals(name)) {
                return p.substring(idx + 1).trim();
            }
        }
        return null;
    }

    private static String md5(String s) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] bytes = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String shorten(String s) {
        s = s == null ? "" : s.replace("\n", " ").trim();
        return s.length() > 180 ? s.substring(0, 180) + "…" : s;
    }

    private static class AuthHeaders {
        final String guid;
        final String timestamp;
        final String authToken;
        final String xToken;

        AuthHeaders(String guid, String timestamp, String authToken, String xToken) {
            this.guid = guid;
            this.timestamp = timestamp;
            this.authToken = authToken;
            this.xToken = xToken;
        }
    }
}
