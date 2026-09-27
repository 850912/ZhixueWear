package com.example.zhixuewear

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.os.Build
import android.view.View
import android.webkit.WebSettings
import android.os.Handler
import android.os.Looper
import okhttp3.Request
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.UUID
import java.security.MessageDigest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.example.zhixuewear.cache.ResultCache
import com.example.zhixuewear.model.Exam
import com.example.zhixuewear.model.ExamResult
import com.example.zhixuewear.model.ScoreItem
import com.example.zhixuewear.net.ZhixueClient
import com.example.zhixuewear.security.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZhixueWearApp() }
    }
}

private enum class Page { LOGIN, WEB_LOGIN, HOME, HISTORY, DETAIL }

private data class UiState(
    val page: Page = Page.LOGIN,
    val loading: Boolean = false,
    val userName: String = "",
    val result: ExamResult? = null,
    val exams: List<Exam> = emptyList(),
    val error: String? = null,
    val cacheTime: Long = 0L,
    val showingCache: Boolean = false
)

@Composable
private fun ZhixueWearApp() {
    val context = LocalContext.current
    val client = remember { ZhixueClient() }
    val store = remember { SecureStore(context) }
    val cache = remember { ResultCache(context) }
    var state by remember { mutableStateOf(UiState()) }
    val scope = rememberCoroutineScope()

    suspend fun loadLatest(user: String, fallbackToCache: Boolean = true) {
        state = state.copy(loading = true, error = null, page = Page.HOME)
        try {
            val result = withContext(Dispatchers.IO) { client.latestResult }
            withContext(Dispatchers.IO) { cache.save(result) }
            withContext(Dispatchers.IO) { store.saveCookie(client.cookieState) }
            state = state.copy(
                loading = false,
                result = result,
                userName = user,
                cacheTime = cache.updatedAt(),
                showingCache = false,
                error = null
            )
        } catch (e: Exception) {
            val cached = withContext(Dispatchers.IO) { cache.load() }
            if (fallbackToCache && cached != null) {
                state = state.copy(
                    loading = false,
                    result = cached,
                    userName = user,
                    cacheTime = cache.updatedAt(),
                    showingCache = true,
                    error = "刷新失败，正在显示上次成绩：${e.message ?: "网络异常"}"
                )
            } else {
                state = state.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) { store.loadCookie() }
        if (saved.isNullOrBlank()) {
            state = UiState(page = Page.LOGIN)
        } else {
            if (saved.startsWith("ZJ1:")) client.setCookieState(saved) else client.setCookie(saved)
            state = state.copy(page = Page.HOME, loading = true)
            try {
                val name = withContext(Dispatchers.IO) { client.validateSession() }
                loadLatest(name)
            } catch (e: Exception) {
                val cached = withContext(Dispatchers.IO) { cache.load() }
                if (cached != null) {
                    state = state.copy(
                        page = Page.HOME,
                        loading = false,
                        userName = "学生",
                        result = cached,
                        cacheTime = cache.updatedAt(),
                        showingCache = true,
                        error = "登录已失效，当前为离线缓存。重新登录后可刷新。"
                    )
                } else {
                    withContext(Dispatchers.IO) { store.clear() }
                    state = UiState(page = Page.LOGIN, error = "登录已失效，请重新导入 Cookie")
                }
            }
        }
    }

    MaterialTheme {
        AppScaffold {
            when (state.page) {
                Page.LOGIN -> LoginScreen(
                    loading = state.loading,
                    error = state.error,
                    onWebLogin = { state = state.copy(page = Page.WEB_LOGIN, error = null) },
                    onLogin = { raw ->
                        state = state.copy(loading = true, error = null)
                        // A new account must never inherit the previous account's offline data.
                        cache.clear()
                        client.setCookie(raw)
                        scope.launch {
                            try {
                                val name = withContext(Dispatchers.IO) { client.validateSession() }
                                withContext(Dispatchers.IO) { store.saveCookie(client.cookieState) }
                                loadLatest(name)
                            } catch (e: Exception) {
                                state = state.copy(loading = false, error = e.message ?: "登录失败")
                            }
                        }
                    }
                )
                Page.WEB_LOGIN -> WebLoginScreen(
                    onBack = { state = state.copy(page = Page.LOGIN, error = null) },
                    onSessionReady = { raw ->
                        state = state.copy(page = Page.LOGIN, loading = true, error = null)
                        client.setCookie(raw)
                        cache.clear()
                        scope.launch {
                            try {
                                val name = withContext(Dispatchers.IO) { client.validateSession() }
                                withContext(Dispatchers.IO) { store.saveCookie(client.cookieState) }
                                loadLatest(name)
                            } catch (e: Exception) {
                                state = state.copy(loading = false, error = e.message ?: "网页登录未完成")
                            }
                        }
                    }
                )
                Page.HOME -> HomeScreen(
                    state = state,
                    onRefresh = {
                        scope.launch { loadLatest(state.userName.ifBlank { "学生" }) }
                    },
                    onHistory = {
                        state = state.copy(loading = true, error = null)
                        scope.launch {
                            try {
                                val exams = withContext(Dispatchers.IO) { client.getRecentExams(30) }
                                withContext(Dispatchers.IO) { cache.saveExams(exams) }
                                state = state.copy(page = Page.HISTORY, loading = false, exams = exams)
                            } catch (e: Exception) {
                                val cachedExams = withContext(Dispatchers.IO) { cache.loadExams() }
                                if (cachedExams.isNotEmpty()) {
                                    state = state.copy(
                                        page = Page.HISTORY,
                                        loading = false,
                                        exams = cachedExams,
                                        error = "网络不可用，显示上次同步的历史考试"
                                    )
                                } else {
                                    state = state.copy(loading = false, error = "历史考试加载失败，请检查手表网络")
                                }
                            }
                        }
                    },
                    onLogin = { state = state.copy(page = Page.LOGIN, loading = false) },
                    onLogout = {
                        store.clear(); cache.clear(); client.clearCookies()
                        state = UiState(page = Page.LOGIN)
                    }
                )
                Page.HISTORY -> HistoryScreen(
                    loading = state.loading,
                    exams = state.exams,
                    error = state.error,
                    onExam = { exam ->
                        state = state.copy(page = Page.DETAIL, loading = true, error = null)
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { client.getResult(exam) }
                                state = state.copy(loading = false, result = result)
                            } catch (e: Exception) {
                                state = state.copy(loading = false, error = e.message ?: "成绩读取失败")
                            }
                        }
                    },
                    onBack = { state = state.copy(page = Page.HOME, error = null) }
                )
                Page.DETAIL -> DetailScreen(
                    loading = state.loading,
                    result = state.result,
                    error = state.error,
                    onBack = { state = state.copy(page = Page.HISTORY, error = null) }
                )
            }
        }
    }
}


@Composable
private fun LoginScreen(
    loading: Boolean,
    error: String?,
    onWebLogin: () -> Unit,
    onLogin: (String) -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    var input by remember { mutableStateOf("") }

    ScreenScaffold(scrollState = scrollState) { padding ->
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text("智学成绩") } }
            item {
                Button(
                    onClick = onWebLogin,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("在手表网页登录") },
                    secondaryLabel = { Text("无需手机 Cookie") }
                )
            }
            item {
                Text(
                    "Wear OS · Material 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!error.isNullOrBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Cookie-Editor 导出的 JSON 可以直接粘贴，也兼容普通 Cookie Header。")
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(86.dp),
                        factory = { ctx ->
                            EditText(ctx).apply {
                                hint = "粘贴 Cookie JSON"
                                setTextColor(android.graphics.Color.WHITE)
                                setHintTextColor(android.graphics.Color.GRAY)
                                setTextSize(12f)
                                gravity = Gravity.TOP
                                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setPadding(0, 0, 0, 0)
                                doAfterTextChanged { input = it?.toString().orEmpty() }
                            }
                        },
                        update = { edit ->
                            if (edit.text.toString() != input) {
                                edit.setText(input)
                                edit.setSelection(edit.text.length)
                            }
                        }
                    )
                }
            }
            item {
                Button(
                    onClick = { if (input.isNotBlank()) onLogin(input) },
                    enabled = !loading && input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (loading) "正在验证…" else "登录并查询") }
                )
            }
            if (loading) item { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun WebLoginScreen(onBack: () -> Unit, onSessionReady: (String) -> Unit) {
    val context = LocalContext.current
    val cookieManager = remember { CookieManager.getInstance() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    DisposableEffect(webView) {
        onDispose {
            webView?.let { view ->
                view.stopLoading()
                view.webChromeClient = null
                view.webViewClient = WebViewClient()
                view.destroy()
            }
            webView = null
        }
    }
    ScreenScaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = {
                    try {
                        CookieManager.getInstance().setAcceptCookie(true)
                        WebView(context).apply {
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus(View.FOCUS_DOWN)
                            setBackgroundColor(android.graphics.Color.WHITE)
                            settings.javaScriptEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = true
                            settings.setSupportMultipleWindows(false)
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.textZoom = 100
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.mediaPlaybackRequiresUserGesture = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }
                            // Keep the provider's real UA. Spoofing Chrome/Pixel while running WebView
                            // creates an inconsistent fingerprint that some WAFs reject.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (request.isForMainFrame) {
                                        unavailable = "网页加载失败：${error.description}"
                                    }
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    CookieManager.getInstance().flush()
                                }
                            }
                            loadUrl("https://www.zhixue.com/wap_login.html")
                            webView = this
                        }
                    } catch (e: Throwable) {
                        unavailable = "此手表没有可用的 WebView，请返回使用 Cookie-Editor 导入"
                        android.view.View(context)
                    }
                }
            )
            if (!unavailable.isNullOrBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) { Text(unavailable!!) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onBack, modifier = Modifier.weight(1f), label = { Text("返回") })
                Button(
                    enabled = unavailable == null && webView != null && !verifying,
                    onClick = {
                        cookieManager.flush()
                        val cookie = cookieManager.getCookie("https://www.zhixue.com/").orEmpty()
                        verifying = true
                        Thread {
                            val valid = verifyWebSession(cookie, WebSettings.getDefaultUserAgent(context))
                            android.os.Handler(Looper.getMainLooper()).post {
                                verifying = false
                                if (valid) onSessionReady(cookie)
                                else unavailable = "尚未登录成功，请完成极验滑块/点选后再点击“已登录”"
                            }
                        }.start()
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(if (verifying) "校验中" else "已登录") }
                )
            }
        }
    }
}

private fun verifyWebSession(cookie: String, userAgent: String): Boolean {
    if (cookie.isBlank()) return false
    return try {
        val http = OkHttpClient()
        fun get(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
            val b = Request.Builder().url(url).header("Cookie", cookie)
                .header("User-Agent", userAgent)
            headers.forEach { (k, v) -> b.header(k, v) }
            return http.newCall(b.build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException()
                JSONObject(response.body?.string().orEmpty())
            }
        }
        val user = get("https://www.zhixue.com/container/getCurrentUser")
        val result = user.optJSONObject("result")
        if (result == null || result.optString("role").isBlank()) return false
        val guid = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis().toString()
        val md5 = MessageDigest.getInstance("MD5").digest((guid + timestamp + "iflytek!@#123student").toByteArray())
            .joinToString("") { "%02x".format(it) }
        val token = get("https://www.zhixue.com/container/app/token/getToken", mapOf(
            "authbizcode" to "0001", "authguid" to guid, "authtimestamp" to timestamp, "authtoken" to md5
        ))
        token.optString("result").isNotBlank()
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onHistory: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = scrollState) { padding ->
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListHeader {
                    Text(if (state.userName.isBlank()) "智学成绩" else "${state.userName}的成绩")
                }
            }
            if (state.loading && state.result == null) item { CircularProgressIndicator() }
            if (!state.error.isNullOrBlank()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut()
                    ) {
                        Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                            Text(state.error.orEmpty(), color = if (state.showingCache) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            state.result?.let { result ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(result.exam.name, fontWeight = FontWeight.SemiBold)
                        if (result.exam.createTime.isNotBlank()) {
                            Text(
                                result.exam.createTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.cacheTime > 0) {
                            Text(
                                (if (state.showingCache) "离线缓存 · " else "更新于 ") + formatTime(state.cacheTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { SummaryCard(result) }
                items(result.scores) { score -> ScoreCard(score) }
            }
            item {
                Button(
                    onClick = onRefresh,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.loading) "刷新中…" else "刷新成绩") }
                )
            }
            item {
                Button(
                    onClick = onHistory,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("历史考试") },
                    secondaryLabel = { Text("最近 30 场") }
                )
            }
            if (state.showingCache) {
                item {
                    Button(
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("重新登录") }
                    )
                }
            }
            item {
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("退出并清除缓存") }
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    loading: Boolean,
    exams: List<Exam>,
    error: String?,
    onExam: (Exam) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = scrollState) { padding ->
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text("历史考试") } }
            if (loading) item { CircularProgressIndicator() }
            if (!error.isNullOrBlank()) item { Card { Text(error, color = MaterialTheme.colorScheme.error) } }
            items(exams) { exam ->
                Card(onClick = { onExam(exam) }, modifier = Modifier.fillMaxWidth()) {
                    Text(exam.name, fontWeight = FontWeight.Medium)
                    if (exam.createTime.isNotBlank()) {
                        Text(
                            exam.createTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), label = { Text("返回") })
            }
        }
    }
}

@Composable
private fun DetailScreen(loading: Boolean, result: ExamResult?, error: String?, onBack: () -> Unit) {
    val scrollState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = scrollState) { padding ->
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text(result?.exam?.name ?: "考试详情") } }
            if (loading) item { CircularProgressIndicator() }
            if (!error.isNullOrBlank()) item { Card { Text(error, color = MaterialTheme.colorScheme.error) } }
            result?.scores?.let { scores -> items(scores) { ScoreCard(it) } }
            item { Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), label = { Text("返回历史考试") }) }
        }
    }
}

@Composable
private fun SummaryCard(result: ExamResult) {
    val scored = result.scores.filter { !it.subject.contains("总") }
    val total = result.scores.firstOrNull { it.subject.contains("总") }
    val average = if (scored.isNotEmpty()) scored.map { it.score }.average() else null
    Card(modifier = Modifier.fillMaxWidth()) {
        Text("本场概览", fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryValue("科目", "${scored.size}")
            SummaryValue("总分", total?.score?.let(::fmt) ?: "--")
            SummaryValue("平均", average?.let(::fmt) ?: "--")
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoreCard(item: ScoreItem) {
    val percent = if (item.fullScore > 0) (item.score / item.fullScore * 100.0).coerceIn(0.0, 999.0) else null
    val progress = ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val barColor = when {
        percent == null -> MaterialTheme.colorScheme.primary
        percent >= 90 -> Color(0xFF65C18C)
        percent >= 60 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.subject, fontWeight = if (item.subject.contains("总")) FontWeight.Bold else FontWeight.Medium)
                if (percent != null) {
                    Text(
                        String.format(Locale.CHINA, "%.1f%%", percent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (item.fullScore > 0) "${fmt(item.score)} / ${fmt(item.fullScore)}" else fmt(item.score),
                fontSize = if (item.subject.contains("总")) 18.sp else 15.sp,
                fontWeight = if (item.subject.contains("总")) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.End
            )
        }
        if (percent != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(progress).height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(barColor)
                )
            }
        }
    }
}

private fun fmt(value: Double): String =
    if (kotlin.math.abs(value - kotlin.math.round(value)) < 0.000001) kotlin.math.round(value).toLong().toString()
    else String.format(Locale.CHINA, "%.1f", value)

private fun formatTime(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))

