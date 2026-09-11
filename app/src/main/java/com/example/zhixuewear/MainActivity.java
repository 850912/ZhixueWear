package com.example.zhixuewear;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.zhixuewear.model.Exam;
import com.example.zhixuewear.model.ExamResult;
import com.example.zhixuewear.model.ScoreItem;
import com.example.zhixuewear.net.ZhixueClient;
import com.example.zhixuewear.security.SecureStore;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ZhixueClient client;
    private SecureStore store;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        client = new ZhixueClient();
        store = new SecureStore(this);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(18);
        content.setPadding(pad, dp(22), pad, dp(32));
        scroll.addView(content);
        setContentView(scroll);

        String saved = store.loadCookie();
        if (saved == null || saved.isEmpty()) {
            showLogin();
        } else {
            client.setCookie(saved);
            showLoading("验证登录...");
            runAsync(() -> client.validateSession(),
                    name -> loadLatest(name),
                    e -> {
                        store.clear();
                        showLoginWithError("登录已失效：" + e.getMessage());
                    });
        }
    }

    private void showLogin() {
        showLoginWithError(null);
    }

    private void showLoginWithError(String error) {
        clear();
        title("智学成绩");
        subtitle("独立 Wear OS 版");

        if (error != null) {
            TextView err = text(error, 13);
            err.setTextColor(0xFFFF8A80);
            add(err);
        }

        TextView hint = text(
                "智学网当前登录可能触发人机验证，因此本版使用网页端 Cookie 登录。\n\n" +
                "在电脑/手机浏览器登录 www.zhixue.com 后，复制请求中的 Cookie 整串并粘贴到这里。",
                13);
        hint.setTextColor(0xFFBDBDBD);
        add(hint);

        EditText input = new EditText(this);
        input.setHint("loginUserName=...; JSESSIONID=...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF777777);
        input.setTextSize(13);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        add(input);

        Button login = button("登录并查询");
        login.setOnClickListener(v -> {
            String cookie = input.getText().toString().trim();
            if (cookie.isEmpty()) {
                toast("请输入 Cookie");
                return;
            }
            client.setCookie(cookie);
            showLoading("正在登录...");
            runAsync(() -> client.validateSession(),
                    name -> {
                        try {
                            store.saveCookie(client.getCookie());
                        } catch (Exception ignored) {}
                        loadLatest(name);
                    },
                    e -> showLoginWithError(e.getMessage()));
        });
        add(login);
    }

    private void loadLatest(String name) {
        showLoading("正在获取成绩...");
        runAsync(() -> client.getLatestResult(),
                result -> showResult(name, result),
                e -> showHomeError(name, e.getMessage()));
    }

    private void showHomeError(String name, String error) {
        clear();
        title("你好，" + name);
        TextView err = text(error, 13);
        err.setTextColor(0xFFFF8A80);
        add(err);

        Button retry = button("重新加载");
        retry.setOnClickListener(v -> loadLatest(name));
        add(retry);

        Button history = button("历史考试");
        history.setOnClickListener(v -> loadExamList(name));
        add(history);

        Button logout = button("退出登录");
        logout.setOnClickListener(v -> logout());
        add(logout);
    }

    private void showResult(String name, ExamResult result) {
        clear();
        TextView greeting = text("你好，" + name, 13);
        greeting.setTextColor(0xFFBDBDBD);
        add(greeting);

        title(result.exam.name);
        if (!result.exam.createTime.isEmpty()) {
            subtitle(result.exam.createTime);
        }

        for (ScoreItem item : result.scores) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView subject = text(item.subject, item.subject.contains("总") ? 17 : 15);
            subject.setGravity(Gravity.START);
            row.addView(subject, new LinearLayout.LayoutParams(0, dp(42), 1));

            String scoreText = item.fullScore > 0
                    ? fmt(item.score) + " / " + fmt(item.fullScore)
                    : fmt(item.score);
            TextView score = text(scoreText, item.subject.contains("总") ? 18 : 15);
            score.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(score, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)));
            add(row);
        }

        Button refresh = button("刷新");
        refresh.setOnClickListener(v -> loadLatest(name));
        add(refresh);

        Button history = button("历史考试");
        history.setOnClickListener(v -> loadExamList(name));
        add(history);

        Button logout = button("退出登录");
        logout.setOnClickListener(v -> logout());
        add(logout);
    }

    private void loadExamList(String name) {
        showLoading("加载考试列表...");
        runAsync(() -> client.getRecentExams(30),
                exams -> showExamList(name, exams),
                e -> showHomeError(name, e.getMessage()));
    }

    private void showExamList(String name, List<Exam> exams) {
        clear();
        title("历史考试");

        if (exams.isEmpty()) {
            subtitle("没有找到考试");
        }

        for (Exam exam : exams) {
            Button b = button(exam.name);
            b.setAllCaps(false);
            b.setOnClickListener(v -> {
                showLoading("读取 " + exam.name);
                runAsync(() -> client.getResult(exam),
                        result -> showResult(name, result),
                        e -> {
                            toast(e.getMessage());
                            showExamList(name, exams);
                        });
            });
            add(b);
            if (!exam.createTime.isEmpty()) {
                TextView date = text(exam.createTime, 11);
                date.setTextColor(0xFF888888);
                add(date);
            }
        }

        Button back = button("返回最近成绩");
        back.setOnClickListener(v -> loadLatest(name));
        add(back);
    }

    private void logout() {
        store.clear();
        client.setCookie("");
        showLogin();
    }

    private void showLoading(String message) {
        clear();
        ProgressBar p = new ProgressBar(this);
        add(p);
        TextView t = text(message, 14);
        t.setGravity(Gravity.CENTER);
        add(t);
    }

    private interface ThrowingSupplier<T> { T get() throws Exception; }
    private interface Success<T> { void accept(T value); }
    private interface Failure { void accept(Exception e); }

    private <T> void runAsync(ThrowingSupplier<T> task, Success<T> ok, Failure bad) {
        executor.submit(() -> {
            try {
                T result = task.get();
                runOnUiThread(() -> ok.accept(result));
            } catch (Exception e) {
                runOnUiThread(() -> bad.accept(e));
            }
        });
    }

    private void clear() {
        content.removeAllViews();
    }

    private void title(String s) {
        TextView t = text(s, 20);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(4), 0, dp(8));
        add(t);
    }

    private void subtitle(String s) {
        TextView t = text(s, 12);
        t.setTextColor(0xFF9E9E9E);
        t.setGravity(Gravity.CENTER);
        add(t);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setMinHeight(dp(44));
        return b;
    }

    private void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        content.addView(v, lp);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String fmt(double n) {
        if (Math.abs(n - Math.rint(n)) < 0.000001) {
            return String.valueOf((long) Math.rint(n));
        }
        return String.format(Locale.US, "%.1f", n);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
