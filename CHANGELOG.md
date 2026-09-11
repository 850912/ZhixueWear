# Changelog

## 1.2.0

- UI 重写为 Jetpack Compose for Wear OS + Wear Material 3。
- 使用 `AppScaffold` / `ScreenScaffold`，保留 Wear OS 时间与滚动指示器行为。
- 首页改为 M3 Card 成绩卡片。
- 历史考试使用可点击 Card。
- Cookie-Editor JSON 继续支持直接粘贴。
- 新增最近一次成功成绩本地缓存。
- 网络失败时自动显示离线缓存，不再清空已有成绩。
- 新增最后更新时间 / 离线缓存状态。
- 新增各科得分率显示。
- 登录失效且存在缓存时，允许先查看缓存并重新登录。
- 版本更新为 1.2.0 (versionCode 3)。
