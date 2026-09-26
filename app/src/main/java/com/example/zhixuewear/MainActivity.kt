package com.example.zhixuewear

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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

private enum class Page { LOGIN, HOME, HISTORY, DETAIL }

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
            client.setCookie(saved)
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
                    onLogin = { raw ->
                        state = state.copy(loading = true, error = null)
                        // A new account must never inherit the previous account's offline data.
                        cache.clear()
                        client.setCookie(raw)
                        scope.launch {
                            try {
                                val name = withContext(Dispatchers.IO) { client.validateSession() }
                                withContext(Dispatchers.IO) { store.saveCookie(client.cookie) }
                                loadLatest(name)
                            } catch (e: Exception) {
                                state = state.copy(loading = false, error = e.message ?: "登录失败")
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
                        store.clear(); cache.clear(); client.setCookie("")
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
private fun LoginScreen(loading: Boolean, error: String?, onLogin: (String) -> Unit) {
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
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
private fun ScoreCard(item: ScoreItem) {
    val percent = if (item.fullScore > 0) (item.score / item.fullScore * 100.0).coerceIn(0.0, 999.0) else null
    Card(modifier = Modifier.fillMaxWidth()) {
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
    }
}

private fun fmt(value: Double): String =
    if (kotlin.math.abs(value - kotlin.math.round(value)) < 0.000001) kotlin.math.round(value).toLong().toString()
    else String.format(Locale.CHINA, "%.1f", value)

private fun formatTime(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
