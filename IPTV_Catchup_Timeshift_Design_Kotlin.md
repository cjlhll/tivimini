# IPTV 回放/回看（Catch-up / Timeshift）开发文档（Kotlin / Android）

> 这份文档专门回答：**回放是怎么实现的？你的 XMLTV EPG 是否“能回放”？如何在 Kotlin IPTV App 里落地？**  
> 结论先说：**EPG（XMLTV）只提供节目时间表，不等于回放能力。**回放是否可用，取决于你的直播源/服务端是否提供“时移/存档（timeshift / tv_archive）”能力；EPG 只是用来“定位要回放的时间段”。

---

## 1. 回放的本质：EPG + 可回看的流

### 1.1 EPG（XMLTV）提供什么？
- 节目属于哪个频道（programme/@channel）
- 节目开始结束时间（start/stop，通常带 offset，如 `+0800`）
- 节目标题/简介

### 1.2 回放能力来自哪里？
必须至少满足其一：

1) **播放源 URL 支持回看参数（最常见）**  
   例如在 URL 上拼接 `playseek=开始-结束`，服务端返回对应时间段的 HLS/DASH/TS 流。APTV 的文档给出了典型的 M3U 扩展配置示例：`catchup="append"` + `catchup-source="?playseek=${(b)yyyyMMddHHmmss:utc}-${(e)yyyyMMddHHmmss:utc}"`。 citeturn0search0

2) **面板/中间层提供“存档（tv_archive）”接口**  
   例如 Xtream 一类服务会配置每路流的 `tv_archive_duration`（可回看天数）并据此生成回放流。 citeturn0search2

> 只有 XMLTV 文件本身 **不能**“变出回放”。它最多让你知道应该回放哪段时间。

---

## 2. 你的 XMLTV EPG 文件能回放吗？

**EPG 能帮你“算回放时间段”，但能不能回放取决于你的源。**

### 2.1 你可以用 EPG 做到：
- 展示节目单
- 用户点击节目 → 得到 `start/stop` → 生成回放请求参数

### 2.2 你还必须具备：
- 直播源或服务端明确支持回看（timeshift / catchup / tv_archive）
- 且你知道如何把 `start/stop` 变成回放 URL（或调用回放 API）

---

## 3. 如何判断你的源是否支持回放（快速定位）

### 3.1 看 M3U 是否含 catchup 扩展字段（最快）
常见字段：
- `catchup="append"`：在直播 URL 后追加参数（常用于 `?playseek=...`）
- `catchup="default"`：`catchup-source` 是完整回放地址（直播地址可能完全不同）
- `catchup-source="..."`：回放模板（含开始/结束占位符）
-（有些壳子还会识别 PLTV/TVOD 等标记并自动启用回看）

APTV 文档给出标准写法与 `:utc` / 时间戳格式的配置方式。 citeturn0search0

### 3.2 看服务端是否有 tv_archive / timeshift 配置
例如 Xtream 文档/集成说明里提到每路流可配置 `tv_archive_duration`（回看天数）。 citeturn0search2

### 3.3 Kodi / IPTV Simple 的“自动按节目长度回看”
Kodi 相关讨论提到：某些 catchup 类型可以“自动按 programme 时长”设置回看时段（前提仍是源支持）。 citeturn0search6

---

## 4. 回放 URL 的常见拼接模式（你 App 需要适配的）

### 4.1 模式 A：append（在直播 URL 后追加）
M3U 示例（来自 APTV）：
```m3u
#EXTINF:-1 catchup="append" catchup-source="?playseek=${(b)yyyyMMddHHmmss:utc}-${(e)yyyyMMddHHmmss:utc}",CCTV1
http://hostname:port/live/xxx.m3u8
```
含义：
- `catchup-source` 里 `${(b)...}` 是开始时间，`${(e)...}` 是结束时间
- `:utc` 表示把时间转成 UTC 再格式化  
citeturn0search0

最终回放 URL：
```
http://hostname:port/live/xxx.m3u8?playseek=BEGIN-END
```

### 4.2 模式 B：default（回放 URL 与直播 URL 不同）
有些场景直播是组播转单播，回放是另一套单播/存档地址；不少壳子支持 `catchup="default"` + `catchup-source` 指定完整回放地址（直播地址与回放地址完全不同）。 citeturn0search3

最终回放 URL：
```
<catchup-source 模板填好开始/结束后得到的完整 URL>
```

### 4.3 模式 C：服务端 API（Xtream / Stalker / 自建存档）
通常流程：
1) 登录/鉴权（token）
2) 查询频道是否支持 archive、可回看天数
3) 按 channel + start/end 请求存档流 URL
4) 用播放器播放

Xtream 集成说明里明确提到 per-stream 的 `tv_archive_duration`（days）。 citeturn0search2

---

## 5. 时区：回放“最容易错”的点（务必照做）

### 5.1 XMLTV 时间通常带 offset（例如 +0800）
正确做法：
- `start/stop` **解析成 Instant**
- 需要 UTC 参数时：`instant.atOffset(UTC)` 格式化
- 需要本地参数时：按服务端要求的 zone 格式化（有些源要求服务器时区）

APTV 文档示例直接使用 `:utc`，说明不少源需要 UTC 口径。 citeturn0search0

### 5.2 常见故障表现
- 点击某节目回放 → 播放失败/404/跳回直播
- 回放出来的内容“偏移 8 小时”
- 只在海外/只在国内某些时区环境异常

这类问题几乎都来自：
- 忽略 XMLTV 的 `+0800`
- 或者把 UTC 与本地时区混用

---

## 6. Kotlin 落地设计（推荐结构）

### 6.1 数据模型（最小）
```kotlin
data class EpgProgramme(
    val channelId: String,
    val start: Instant,
    val stop: Instant,
    val title: String
)

data class CatchupSpec(
    val mode: CatchupMode,            // APPEND / DEFAULT / NONE / API
    val sourceTemplate: String?,       // catchup-source
    val supported: Boolean
)

enum class CatchupMode { APPEND, DEFAULT, API, NONE }
```

### 6.2 回放入口（用户点击节目）
输入：
- 频道直播 URL（from M3U）
- 该频道 catchup 配置（from M3U 或服务端能力）
- 该节目 start/stop（Instant，来自 XMLTV）

输出：
- 可播放的回放 URL（String）或 API 请求信息

---

## 7. Kotlin 实现：时间解析与格式化（回放必用）

### 7.1 XMLTV 时间解析为 Instant（带 offset）
```kotlin
private val XMLTV_TIME: DateTimeFormatter =
    DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("yyyyMMddHHmmss")
        .optionalStart()
        .appendLiteral(' ')
        .appendOffset("+HHmm", "+0000")   // +0800
        .optionalEnd()
        .toFormatter(Locale.US)

fun parseXmlTvInstant(raw: String): Instant =
    OffsetDateTime.parse(raw.trim(), XMLTV_TIME).toInstant()
```

### 7.2 把 Instant 格式化成 yyyMMddHHmmss（UTC 或指定时区）
```kotlin
private val YMDHMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

fun formatUtcYmdHms(i: Instant): String =
    i.atOffset(ZoneOffset.UTC).format(YMDHMS)

fun formatZoneYmdHms(i: Instant, zone: ZoneId): String =
    i.atZone(zone).format(YMDHMS)
```

---

## 8. Kotlin 实现：把 programme(start/stop) 填进 catchup-source

> 不同壳子/服务的模板语法不完全相同。建议你的 App 先支持一套“内部模板”，再兼容常见输入。

### 8.1 建议你 App 支持的最小模板占位符
- `{begin_utc}`：节目开始 UTC `yyyyMMddHHmmss`
- `{end_utc}`：节目结束 UTC `yyyyMMddHHmmss`
- `{begin_ts}`：开始 epochSeconds
- `{end_ts}`：结束 epochSeconds

### 8.2 生成回放 URL（append / default）
```kotlin
fun buildCatchupUrl(
    liveUrl: String,
    mode: CatchupMode,
    template: String,
    start: Instant,
    stop: Instant
): String {
    val beginUtc = formatUtcYmdHms(start)
    val endUtc = formatUtcYmdHms(stop)
    val beginTs = start.epochSecond.toString()
    val endTs = stop.epochSecond.toString()

    val filled = template
        .replace("{begin_utc}", beginUtc)
        .replace("{end_utc}", endUtc)
        .replace("{begin_ts}", beginTs)
        .replace("{end_ts}", endTs)

    return when (mode) {
        CatchupMode.APPEND -> liveUrl + filled
        CatchupMode.DEFAULT -> filled
        else -> liveUrl
    }
}
```

### 8.3 如何对接 APTV 风格的 `${(b)...}` 模板
APTV 文档使用 `${(b)yyyyMMddHHmmss:utc}` / `${(e)...}` 这种语法。 citeturn0search0  
你可以把它“预处理”为你内部占位符：

- `${(b)yyyyMMddHHmmss:utc}` → `{begin_utc}`
- `${(e)yyyyMMddHHmmss:utc}` → `{end_utc}`

只要先兼容这两种，实际就能覆盖很多源。

---

## 9. 播放器层（ExoPlayer）注意事项

- 回放通常仍是 HLS（m3u8）或 TS 流，ExoPlayer 可直接播放
- 若回放 URL 与直播 URL 域名/证书不同，记得处理 HTTPS / 证书 / 重定向
- 若服务端要求 headers（token、UA），在 DataSourceFactory 中注入请求头

---

## 10. 业务策略建议（用户体验）

### 10.1 回放按钮显示条件
仅当：
- 频道支持 catchup（M3U 有 catchup 或服务端声明 tv_archive）
- 且节目已播（stop < now），或你明确支持“从正在播的时间点回看”
才显示回放入口。

### 10.2 可回看天数与超出处理
- Xtream 类通常用 `tv_archive_duration` 限制回看天数。 citeturn0search2
- 超出时提示“该节目已过期不可回看”，避免点了没反应。

---

## 11. 测试清单（建议写单测）

1) **时区解析**
- 输入：`20260209012000 +0800`
- 解析 Instant 后转回 `+08:00` 应得到 `2026-02-09 01:20:00`

2) **UTC 格式化**
- 将 start Instant 格式化为 UTC `yyyyMMddHHmmss`
- 与预期 UTC 时间一致（用于 `:utc` 回放参数）

3) **URL 拼接**
- append 模式：`liveUrl + "?playseek=begin-end"`
- default 模式：返回完整回放 URL

4) **回看范围**
- stop < now 才允许“从节目起止回看”
- 若允许“时移回看正在播”，则 end 可用 `now` 替代 stop（按服务端能力）

---

## 12. 你需要提供的最小信息（我可以进一步给你定制实现）
为了把你的 App 做成“点节目就回放”，你只要提供一项即可：

1) 你的 M3U 中任意一条频道行（含 `#EXTINF` 与 URL），看是否有 `catchup/catchup-source`  
2) 或你的服务端类型（Xtream / Stalker / 自建 HLS 存档）及回看参数规则  
3) 或一条你手动拼过、**能回放**的 URL 示例（域名可打码）

---

## 参考资料
- APTV：回看参数、catchup/catchup-source、UTC 与时间戳格式示例。 citeturn0search0  
- Xtream 集成/模式：提到 per-stream 的 `tv_archive_duration`（回看天数）。 citeturn0search2  
- Kodi 论坛：IPTV Simple 对 Catchup/Timeshifted Catchup 的讨论（按节目长度自动设置）。 citeturn0search6
