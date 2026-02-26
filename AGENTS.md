# AGENTS.md - Android TV IPTV 项目开发指南

## 项目概述

这是一个基于 Jetpack Compose for TV 开发的 Android TV IPTV 播放器，支持 m3u 直播源播放、XMLTV EPG 节目单、回看/时移功能。

- **包名**: `com.cjlhll.iptv`
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 36

---

## 构建与测试命令

### 构建 APK

```bash
# 调试版本
./gradlew assembleDebug

# 发布版本（需要配置签名）
./gradlew assembleRelease

# 完整构建（含 lint 检查）
./gradlew build
```

### 测试命令

```bash
# 运行所有单元测试
./gradlew test

# 运行单个测试类
./gradlew test --tests "com.cjlhll.iptv.M3uParserTest"

# 运行单个测试方法
./gradlew test --tests "com.cjlhll.iptv.M3uParserTest.parseValidM3u"

# 运行 instrumented 测试（需连接设备或模拟器）
./gradlew connectedAndroidTest

# 在 TV 设备上运行 instrumented 测试
./gradlew testDebugAndroidTest
```

### Lint 检查

```bash
./gradlew lint
./gradlew lintRelease
```

---

## 代码规范

### 包与导入

- 包声明在文件顶部: `package com.cjlhll.iptv`
- 导入按以下顺序分组（无空行分隔）:
  1. Kotlin 标准库
  2. Android 系统包 (`android.*`)
  3. AndroidX (`androidx.*`)
  4. 第三方库 (`com.*`, `org.*`, `io.*`)
  5. Compose 相关（按字母排序）
- 避免使用通配符导入

### 命名约定

- **类/接口**: PascalCase (如 `MainActivity`, `M3uParser`)
- **函数/变量**: camelCase (如 `liveSource`, `parseContent`)
- **常量**: 全大写下划线分隔 (如 `KEY_LIVE_SOURCE`)
- **Compose 组件**: 以大写字母开头 (如 `MainScreen`, `VideoPlayerScreen`)
- **Object 单例**: 与类名同格式 (如 `object M3uParser`)

### 数据类

使用 `data class` 定义简单数据模型:

```kotlin
data class Channel(
    val title: String,
    val url: String,
    val group: String? = null,
    val logoUrl: String? = null,
    // ...
)
```

### 单例模式

使用 `object` 关键字创建工具类:

```kotlin
object Prefs {
    private const val PREF_NAME = "iptv_prefs"
    // ...
}
```

### 错误处理

- 使用 `try-catch` 块，异常类型从具体到通用:
  ```kotlin
  try {
      // code
  } catch (e: IOException) {
      // 处理网络错误
  } catch (e: Exception) {
      // 处理其他错误
  }
  ```
- 使用 `runCatching` 进行安全的表达式求值:
  ```kotlin
  runCatching { QrCode.toBitmap(url, 640) }.getOrNull()
  ```
- 使用 `?:`, `?.let`, `?.also` 链式调用处理空值

### 日志

- 使用 Android Log 类:
  ```kotlin
  private const val TAG = "M3uParser"
  Log.d(TAG, "message")
  Log.i(TAG, "message")
  Log.w(TAG, "message")
  Log.e(TAG, "message")
  ```

### Compose 规范

- 使用 `@OptIn` 注解标记实验性 API:
  ```kotlin
  @OptIn(ExperimentalTvMaterial3Api::class)
  @Composable
  fun MyComponent() { ... }
  ```
- 使用 `remember`, `mutableStateOf` 管理状态
- 使用 `LaunchedEffect` 处理副作用
- 使用 `rememberCoroutineScope` 在 Compose 中启动协程

### 协程与异步

- UI 线程操作使用 `Dispatchers.Main`（默认）
- IO 密集操作使用 `Dispatchers.IO`:
  ```kotlin
  withContext(Dispatchers.IO) {
      // 网络请求、文件读写
  }
  ```
- CPU 密集计算使用 `Dispatchers.Default`

### UI 颜色与样式

- 保持深色主题一致，使用代码中定义的 Color 值
- 优先使用系统默认变量保证一致性
- 使用 `darkColorScheme` 定义深色配色:
  ```kotlin
  val colorScheme = darkColorScheme(
      primary = Color(0xFFBDBDBD),
      background = Color(0xFF121212),
      // ...
  )
  ```

### 字符串与资源

- 避免硬编码中文字符串，使用字符串资源文件
- 国际化支持: `res/values/strings.xml`, `res/values-zh/strings.xml`

---

## 目录结构

```
app/src/main/java/com/cjlhll/iptv/
├── MainActivity.kt          # 应用入口，源配置界面
├── PlayerActivity.kt        # 播放器主界面
├── SplashActivity.kt        # 启动页
├── M3uParser.kt            # M3U 解析器
├── XmlTvParser.kt          # XMLTV 解析器
├── Channel.kt              # 频道数据模型
├── EpgModels.kt            # EPG 数据模型
├── EpgCache.kt             # EPG 缓存
├── EpgRepository.kt        # EPG 数据仓库
├── EpgNormalize.kt         # EPG 标准化
├── PlaylistCache.kt        # 播放列表缓存
├── Prefs.kt                # SharedPreferences 工具
├── CatchupHelper.kt        # 回看辅助
├── LogoLoader.kt           # Logo 加载器
├── SourceConfigWebPage.kt  # 配置网页
├── SourceConfigLanServer.kt # 局域网服务
├── QrCode.kt               # 二维码生成
├── PlayerDrawer.kt         # 频道列表抽屉
├── SettingsDrawer.kt       # 设置抽屉
└── ChannelInfoBanner.kt    # 频道信息横幅
```

---

## 常用依赖

| 库 | 用途 |
|---|---|
| `androidx.compose.*` | UI 框架 |
| `androidx.tv.material3` | TV 专用组件 |
| `androidx.media3.exoplayer` | 视频播放 |
| `okhttp` | HTTP 网络请求 |
| `nanohttpd` | 嵌入式 HTTP 服务器 |
| `zxing` | 二维码生成 |

---

## 注意事项

1. 每次修改 Kotlin 代码后执行 `./gradlew.bat assembleDebug` 验证编译成功
2. 避免在主线程进行网络请求和文件操作
3. 使用 `context.getSharedPreferences()` 时注意内存泄漏
4. 播放器生命周期需与 Activity/Composable 同步
