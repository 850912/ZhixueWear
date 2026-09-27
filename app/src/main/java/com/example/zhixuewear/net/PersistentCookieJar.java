package com.example.zhixuewear.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/** Encrypted-state friendly CookieJar. It keeps expiry, domain and path. */
public final class PersistentCookieJar implements CookieJar {
    public static final String PREFIX = "ZJ1:";
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private final Runnable onChanged;

    public PersistentCookieJar() { this(null); }
    public PersistentCookieJar(Runnable onChanged) { this.onChanged = onChanged; }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        removeExpired();
        List<Cookie> result = new ArrayList<>();
        for (Cookie cookie : cookies.values()) {
            if (cookie.matches(url)) result.add(cookie);
        }
        return result;
    }

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> received) {
        boolean changed = false;
        for (Cookie cookie : received) {
            String key = key(cookie);
            if (cookie.persistent() && cookie.expiresAt() <= System.currentTimeMillis()) {
                changed |= cookies.remove(key) != null;
            } else {
                cookies.put(key, cookie);
                changed = true;
            }
        }
        changed |= removeExpired();
        if (changed) notifyChanged();
    }

    public synchronized void clear() {
        if (!cookies.isEmpty()) {
            cookies.clear();
            notifyChanged();
        }
    }

    public synchronized void loadState(String state) {
        cookies.clear();
        if (state == null || state.trim().isEmpty()) return;
        try {
            String json = state.startsWith(PREFIX) ? state.substring(PREFIX.length()) : state;
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Cookie.Builder b = new Cookie.Builder()
                        .name(o.optString("name"))
                        .value(o.optString("value"))
                        .path(o.optString("path", "/"));
                String domain = o.optString("domain", "www.zhixue.com");
                if (o.optBoolean("hostOnly", false)) b.hostOnlyDomain(domain);
                else b.domain(domain);
                long expires = o.optLong("expiresAt", Long.MAX_VALUE);
                if (expires != Long.MAX_VALUE) b.expiresAt(expires);
                if (o.optBoolean("secure", false)) b.secure();
                if (o.optBoolean("httpOnly", false)) b.httpOnly();
                Cookie c = b.build();
                if (c.expiresAt() > System.currentTimeMillis()) cookies.put(key(c), c);
            }
        } catch (Exception ignored) {
            cookies.clear();
        }
    }

    public synchronized void importHeader(String header) {
        if (header == null) return;
        HttpUrl url = HttpUrl.get("https://www.zhixue.com/");
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.isEmpty() || p.indexOf('=') <= 0) continue;
            Cookie c = Cookie.parse(url, p + "; Domain=www.zhixue.com; Path=/");
            if (c != null) cookies.put(key(c), c);
        }
        removeExpired();
    }

    public synchronized String toState() {
        JSONArray array = new JSONArray();
        for (Cookie c : cookies.values()) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", c.name());
                o.put("value", c.value());
                o.put("domain", c.domain());
                o.put("path", c.path());
                o.put("expiresAt", c.expiresAt());
                o.put("hostOnly", c.hostOnly());
                o.put("secure", c.secure());
                o.put("httpOnly", c.httpOnly());
                array.put(o);
            } catch (Exception ignored) { }
        }
        return PREFIX + array.toString();
    }

    public synchronized String toHeader() {
        removeExpired();
        StringBuilder out = new StringBuilder();
        for (Cookie c : loadForRequest(HttpUrl.get("https://www.zhixue.com/"))) {
            if (out.length() > 0) out.append("; ");
            out.append(c.name()).append('=').append(c.value());
        }
        return out.toString();
    }

    private boolean removeExpired() {
        boolean changed = false;
        Iterator<Map.Entry<String, Cookie>> it = cookies.entrySet().iterator();
        long now = System.currentTimeMillis();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt() <= now) { it.remove(); changed = true; }
        }
        return changed;
    }

    private static String key(Cookie c) { return c.domain() + "\n" + c.path() + "\n" + c.name(); }
    private void notifyChanged() { if (onChanged != null) onChanged.run(); }
}
