# ZhixueWear

> **免责声明**：本项目是非智学网官方的第三方 Wear OS 客户端，仅供学习和个人使用。项目通过公开网页行为访问智学网，接口、登录流程和服务可用性由智学网控制，改版后可能无法使用。请仅导入你本人账号的 Cookie，不要分享 Cookie 或将其提交到代码仓库。使用本项目产生的账号风险、数据错误、服务中断或其他损失由使用者自行承担。

一个 **完全独立的 Wear OS 智学网成绩查询 App**。

- 不需要手机伴侣 App。
- 手表直接访问 `https://www.zhixue.com`。
- 支持最近成绩、历史考试、刷新、退出登录。
- Cookie 使用 Android Keystore + AES/GCM 加密后保存在手表本地。
- GitHub Actions 可构建并使用你自己的 keystore 签名 Release APK。

> 非智学网官方项目。接口来自公开网页行为与开源项目分析，智学网改版后可能需要更新。

## 登录方式与设备兼容性

登录页优先提供“在手表网页登录”：手表如果安装了 WebView provider，可直接打开智学网、完成验证码和登录，然后由 App 读取当前站点 Cookie 并独立查询成绩。没有 WebView 的 Wear OS 设备会自动显示提示，仍可使用下面的 Cookie-Editor 导入方式；不会依赖手机伴侣 App。

每次请求都会接收并保存智学网返回的 `Set-Cookie` 更新，成绩刷新成功后会用 Android Keystore 加密保存最新会话，避免只保存初始 Cookie 导致短时间失效。

智学网学生网页登录可能触发浏览器端人机验证。若站点在手表 WebView 中无法完成验证，请在自己的浏览器登录后导入 Cookie。

## 为什么仍支持 Cookie

智学网学生网页登录目前可能触发浏览器端人机验证。现有维护中的 `zhixuewang-python`
也采用 Playwright 浏览器完成登录或直接 Cookie 登录。因此这个纯手表版本不尝试绕过验证码，
而是在登录页接收你自己的网页登录 Cookie。

登录完成后，成绩查询完全由手表独立进行。

## 获取 Cookie

1. 在电脑或手机浏览器打开 `https://www.zhixue.com` 并正常登录。
2. 打开开发者工具 -> Network。
3. 刷新智学网页面，点任意发往 `www.zhixue.com` 的请求。
4. 在 Request Headers 中复制完整的 `Cookie:` 值。
5. 在手表 App 登录页粘贴。

建议只在自己的设备上使用自己的账号。Cookie 等同登录凭证，不要发给别人，也不要提交到 GitHub。


## Cookie-Editor 直接导入

登录页现在支持两种格式：

1. Cookie-Editor 导出的完整 JSON 数组（推荐，直接原样粘贴）
2. 传统 Cookie Header：`name=value; name2=value2`

App 会自动识别 JSON、去除重复 Cookie，并优先采用 `www.zhixue.com` 的 hostOnly Cookie。

另外附带 Termux/电脑转换工具：

```bash
python tools/cookie-convert.py cookies.json
```

会输出可直接作为 HTTP `Cookie` 请求头使用的一整行字符串。

## GitHub Actions 编译

仓库包含两个 workflow：

- `Build debug APK`：无需签名配置，手动运行后获得 Debug APK。
- `Build signed Wear OS APK`：使用 GitHub Repository Secrets 中的 keystore 构建 Release APK。

### 1. 创建签名 keystore

只需创建一次，并妥善备份。**以后升级 APK 必须继续使用同一把签名密钥。**

示例：

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias zhixuewear \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

### 2. 转成 Base64

Linux:

```bash
base64 -w 0 release.keystore > keystore.txt
```

macOS:

```bash
base64 < release.keystore | tr -d '\n' > keystore.txt
```

Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Content -NoNewline keystore.txt
```

### 3. GitHub 仓库添加 Secrets

进入：

`Settings -> Secrets and variables -> Actions -> New repository secret`

添加：

| Secret | 内容 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | `keystore.txt` 的完整内容 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 例如 `zhixuewear` |
| `KEY_PASSWORD` | key 密码 |

### 4. 编译

进入 GitHub 仓库的 **Actions**：

`Build signed Wear OS APK -> Run workflow`

完成后在该次 Action 页面底部的 **Artifacts** 下载 `ZhixueWear-signed`。

APK 文件名：

```text
app-release.apk
```

## 修改包名

默认：

```text
com.example.zhixuewear
```

发布前建议改成自己的唯一包名。修改：

- `app/build.gradle` 中的 `namespace`
- `app/build.gradle` 中的 `applicationId`
- Java 源码目录及 `package` 声明

## 当前使用的智学网接口

```text
GET /container/getCurrentUser
GET /container/app/token/getToken
GET /zhixuebao/base/common/academicYear
GET /zhixuebao/report/exam/getRecentExam
GET /zhixuebao/report/exam/getUserExamList
GET /zhixuebao/report/exam/getReportMain
```

成绩 API 请求会生成以下认证头：

```text
authbizcode: 0001
authguid: <UUID>
authtimestamp: <毫秒时间戳>
authtoken: md5(authguid + authtimestamp + 固定业务字符串)
XToken: <服务器返回 token>
```

## Wear OS 要求

- `minSdk 30`
- `targetSdk 35`
- Manifest 声明 `android.hardware.type.watch`
- `com.google.android.wearable.standalone = true`
- `INTERNET` 权限

## 安全说明

- App 不会把账号、Cookie 上传到任何第三方服务器。
- Cookie 仅发给 `https://www.zhixue.com`。
- Cookie 在本地通过 Android Keystore 保护的 AES/GCM 密钥加密保存。
- GitHub Actions 的签名密钥必须放 Secrets，不要提交 `.jks` / `.keystore` 到仓库。

## v1.2.0 Wear Material 3

界面已迁移到官方 Wear Compose Material 3：

- `AppScaffold` + `ScreenScaffold`
- Wear M3 `Card` / `Button` / `ListHeader`
- Wear 专用滚动列表与滚动指示器
- 最近成绩离线缓存
- 刷新失败保留旧成绩
- 最后更新时间
- 各科得分率
- Cookie-Editor JSON 直接导入

更新源码后，在 Termux 直接运行已有的一键命令：

```bash
zxbuild
```

即可自动提交、推送并触发 GitHub Actions Debug 构建。
