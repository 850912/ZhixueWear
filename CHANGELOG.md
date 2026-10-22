# Changelog

## 0.3.1-m3e
- **界面重排，解决“圆形表盘显示不全”**
  - 拆成三个分页（测量 / 趋势 / 设置），每页都能在一屏内读完，不再需要连续滚动好几屏。
  - 列表内容改为**垂直居中**（`Arrangement.spacedBy(6.dp, Alignment.CenterVertically)`）。此前是顶部对齐，
    而圆形表盘越靠近顶部可用宽度越窄，第一排控件会被圆弧裁掉。
  - 在 `ScreenScaffold` 自带的 5.2% 安全区之外再留 4% 的横向安全区。
  - 切换分页后自动回到列表顶部，避免沿用上一页的滚动位置看到空白页。
  - 卡片、图表、统计文本都加上 `maxLines` + 省略号，长文本不再把布局撑破。
  - 测量卡片里的读数行改为带权重，可自适应宽度，不会挤在一起。

- **修复“测量动画被卡片盖住”**
  - 表盘进度环之前在列表之前绘制，会被所有卡片完全遮住；现在改到内容**之后**绘制。
  - 测量卡片内新增 `LinearProgressIndicator`，即使进度环被裁掉也能看到采样进度。

- **可用性**
  - 所有纯展示用的卡片改用 Wear M3 的**不可点击** `Card`/`OutlinedCard` 重载。
    之前写的是 `Card(onClick = {})`：这些卡片会进入焦点链，旋钮导航会停在“按了没反应”的卡片上，
    焦点放大还会让内容看起来溢出屏幕。

## 0.3.0-m3e
- **后台自动测量**
  - 新增 `BackgroundMeasureJobService` + `BackgroundMeasureScheduler`，用系统 `JobScheduler` 周期任务实现后台自动采样（15/30/60 分钟三档）。
  - 不使用前台服务：无常驻通知、无 wakelock；单次任务只取 **一个** 样本就断开传感器，采样结束线程退出、进程可被回收。
  - 任务带 `setRequiresBatteryNotLow`、`setPersisted(true)`（重启后恢复）与 flex 窗口，由系统统一编排唤醒次数。
  - 后台任务不启用运动检测，进一步降低传感器占用。
  - “静息自动测量”开关与采样间隔持久化到 SharedPreferences，界面可随时调整。
- **趋势图**
  - 用真正的趋势图替换原来的折线火花图：时间横轴、最高/均值/最低参考线、渐变面积、数据点，并按时间分桶（最多 24 点）保证长周期视图可读。
  - 新增 `TrendStats`（纯逻辑）与趋势统计卡片（样本数 / 均值 / 高低）。
  - 周/月视图的样本列表补充日期。
- **修复的缺陷**
  - `ValueKey.SkinTemperatureSet` 只有 `OBJECT_TEMPERATURE` / `AMBIENT_TEMPERATURE` / `STATUS`。旧代码把 `OBJECT_TEMPERATURE` 同时当作“皮肤温度”和“物体温度”，界面出现两个完全相同的数字；现在只保留腕部皮肤温度一个通道，并按 SDK 文档处理 `STATUS`（0 正常 / -1 错误）。
  - `motionTracker` 从未创建、`HealthTrackerType.ACCELEROMETER` 从未使用，运动检测是死代码；改用系统加速度计（5Hz）实现，且只在测量期间注册。
  - 佩戴状态算出来后从未回传界面，“佩戴检测”永远显示“未佩戴手表”；现在通过 `onWearStateChanged` 实时回传。
  - `disconnectService()` 异步回调可能在新测量开始后到达，把新测量误判为“连接中断”；引入会话令牌（sessionId），所有回调先校验令牌。
  - 界面切换测量模式时旧 reader 的迟到回调会覆盖新结果；引入测量代号（generation）丢弃过期回调。
  - 权限回调用 `grants.values.all{}` 判断，空 map 会被当成“全部已授权”；改为复查实际授权状态。
  - 3 秒节流直接丢弃整批数据点（连带丢掉错误状态与佩戴判定），并使用墙上时钟，NTP 校时回拨会长时间抑制采样；改为逐点过滤 + `elapsedRealtime()`。
  - 有效温度区间过窄（环境温度 15–45°C），冬天室内会被整体判为无效；放宽到 −20–60°C，并修正佩戴判定阈值。
  - 离开前台后传感器仍然保持唤醒；现在 `onStop` 立即释放传感器，持续采样交给后台任务。
  - 历史记录读写没有同步，界面与后台并发写入会丢数据；改为单一入口加锁的紧凑存储。
  - `BodyTemperatureWearTheme` 之前是死代码，现在真正被界面使用，并加上动态取色失败时的降级保护。
  - “静息自动测量”开关的 `if/else` 两个分支完全等价，已合并。

## 0.2.0-m3e
- Upgraded Wear OS UI and motion to Google's **Material 3 Expressive (M3E)** (`androidx.wear.compose.material3` 1.6.2):
  - Added `BodyTemperatureWearTheme` with `dynamicColorScheme(LocalContext.current)` watch-face dynamic color extraction, a 29-token biothermal `ColorScheme` fallback, and `MotionScheme.expressive()` spring physics.
  - Added `ScreenScaffold` bottom-hugging `EdgeButton` (`EdgeButtonSize.Medium`) and perimeter bezel `CircularProgressIndicator` with spring-interpolated sample progress.
  - Upgraded `TransformingLazyColumn` with `rememberTransformationSpec()`, `Modifier.transformedHeight(this, transformationSpec)`, `SurfaceTransformation(transformationSpec)`, and rotary crown snap fling (`RotaryScrollableDefaults.snapBehavior`).
  - Added `ExpressiveThermalHeroCard` with corner-shape morphing (`animateDpAsState`), breathing radial thermal aura (`rememberInfiniteTransition`), spring-animated numeral readout (`MaterialTheme.typography.numeralExtraLarge`), and morphing status pill (`AnimatedContent`).
  - Replaced static mode and trend range rows with M3E `ButtonGroup` using `Modifier.animateWidth(interactionSource)` and spring-driven corner/weight morphing.
  - Added `TitleCard` personal trend summary with a custom Bezier sparkline (`ExpressiveTrendSparkline`) and per-reading delta badges.

## 0.1.0
- Replaced the retired score viewer with a Wear OS wrist skin-temperature research prototype.
- Added sample quality checks and an opt-in local paired-observation log.
- No core body-temperature estimate is produced.
