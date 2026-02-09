# XMLTV EPG 解析与节目匹配开发文档（Kotlin / Android IPTV App）

> 用途：在 Kotlin IPTV App 中**稳定解析 XMLTV EPG**，并正确完成**频道匹配、按天节目表、当前节目**等功能。  
> 重点：**时区/偏移（+0800）处理**与**跨天节目**，避免“节目单无法匹配”的常见坑。

---

## 0. 现状说明（与你提供的数据源相关）

你之前上传的 `*.xml` EPG 文件与网页源码文件在当前会话环境中已过期，无法再次逐行引用原始内容做“逐字段对照”。  
但本文档按 **XMLTV 标准**与 IPTV App 常见实现编写，直接可用。若你希望我把示例字段/频道映射完全对齐你那份 EPG 与网页逻辑，请重新上传它们，我可以再补充“与你文件一致”的校验与适配部分。

---

## 1. 功能目标

1. **解析 XMLTV**
   - `channel`：读取 `id`、`display-name`、`icon`
   - `programme`：读取 `channel`、`start`、`stop`、`title`、`desc`
2. **统一时间模型**
   - 解析时将 `start/stop` 解析成 **Instant**
   - 比较/排序/过滤全部基于 Instant（或 epochMillis）
3. **频道匹配**
   - 优先使用 M3U 的 `tvg-id` 对齐 XMLTV 的 `channel/@id`
   - 若无法对齐，提供 name 归一化与映射表兜底
4. **节目查询**
   - 当前节目（正在播放）
   - 某天节目表（start-based / overlap-based 两种策略）
5. **性能与稳定性**
   - 流式解析（XmlPullParser）
   - 内存索引（按 channelId 分组并排序）
   - 可选：磁盘缓存/增量更新（可扩展）

---

## 2. XMLTV 数据结构（概念）

### 2.1 `<channel id="...">`
```xml
<channel id="1">
  <display-name>CCTV1</display-name>
  <icon src="https://.../logo.png"/>
</channel>
```

**字段说明**
- `channel/@id`：频道唯一标识（最建议用于匹配）
- `display-name`：频道名称（可多个）
- `icon/@src`：台标（可选）

### 2.2 `<programme start="..." stop="..." channel="...">`
```xml
<programme start="20260209012000 +0800" stop="20260209020400 +0800" channel="1">
  <title>新闻联播</title>
  <desc>...</desc>
</programme>
```

**字段说明**
- `programme/@channel`：关联的频道 id（必须命中 channel/@id）
- `programme/@start`、`programme/@stop`：XMLTV 时间（**带偏移**）
- `title/desc`：节目标题与简介

---

## 3. 最关键：时间与时区（必须读这一节）

### 3.1 XMLTV 时间格式
常见形态：`yyyyMMddHHmmss Z`  
示例：`20260209012000 +0800`

含义：  
> “在 +08:00 时区下的 2026-02-09 01:20:00”，对应一个唯一的绝对时间点（Instant）

### 3.2 正确策略（强制）
- 解析时：**用 OffsetDateTime → Instant**
- 存储/比较：**Instant / epochMillis**
- 展示时：根据 `ZoneId` 把 Instant 转回本地时间

### 3.3 典型错误（会导致节目单对不上）
- ❌ 把 `20260209012000 +0800` 当成本地时间（LocalDateTime）解析，忽略 `+0800`
- ❌ 解析成 Date 后又手动 `+8/-8` 二次偏移
- ❌ 筛选用 UTC，展示用本地（或反过来），导致“错天/错节目”

---

## 4. Kotlin 数据模型

```kotlin
data class EpgChannel(
    val id: String,
    val names: List<String>,
    val logo: String? = null
)

data class EpgProgramme(
    val channelId: String,
    val start: java.time.Instant,
    val stop: java.time.Instant,
    val title: String,
    val desc: String? = null
)
```

推荐索引：
- `channelsById: Map<String, EpgChannel>`
- `programmesByChannelId: Map<String, List<EpgProgramme>>`（按 start 升序）

---

## 5. 时间解析实现（核心代码）

> 这是你“节目单无法匹配”的高概率根因点，务必使用这套解析。

```kotlin
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

private val XMLTV_TIME: DateTimeFormatter =
    DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("yyyyMMddHHmmss")
        .optionalStart()
        .appendLiteral(' ')
        .appendOffset("+HHmm", "+0000") // 支持 +0800 / -0500
        .optionalEnd()
        .toFormatter(Locale.US)

fun parseXmlTvInstant(raw: String): Instant {
    val odt = OffsetDateTime.parse(raw.trim(), XMLTV_TIME)
    return odt.toInstant()
}
```

### 5.1 校验建议（开发期强烈推荐）
对任意 programme 的 start/stop：
- 解析为 Instant
- 再用 `ZoneOffset.ofHours(8)`（或 XML 中的 offset）转回本地时间打印
- 检查与 XML 原始时间是否一致（同一 offset 下）

---

## 6. XML 解析实现（XmlPullParser 推荐）

### 6.1 解析流程（高层）
1. 进入 `<tv>`
2. 遇到 `<channel>`：解析为 EpgChannel
3. 遇到 `<programme>`：解析为 EpgProgramme（start/stop 立刻转 Instant）
4. 完成后：
   - programmes 按 channelId 分组
   - 每组按 start 排序

### 6.2 伪代码（可直接落地实现）
```kotlin
fun parseXmlTv(input: InputStream): Pair<Map<String, EpgChannel>, Map<String, List<EpgProgramme>>> {
    val channels = mutableMapOf<String, EpgChannel>()
    val programmes = mutableListOf<EpgProgramme>()

    // XmlPullParser 初始化略
    // while (event != END_DOCUMENT) { ... }

    // 解析 channel:
    // - id = attr("id")
    // - display-names: collect <display-name> text
    // - icon src: <icon src="..."/>

    // 解析 programme:
    // - channelId = attr("channel")
    // - start = parseXmlTvInstant(attr("start"))
    // - stop = parseXmlTvInstant(attr("stop"))
    // - title, desc: read child text

    val programmesByChannel = programmes
        .groupBy { it.channelId }
        .mapValues { (_, list) -> list.sortedBy { it.start } }

    return channels to programmesByChannel
}
```

### 6.3 数据清洗建议
- `stop <= start`：丢弃或修正（取决于数据源质量）
- title 为空：可用占位文本
- programme 无对应 channelId：保留 programme 也可，但匹配将失败

---

## 7. 频道匹配（解决“频道对不上”）

### 7.1 最推荐（强一致，稳定）
让 M3U 里的 `tvg-id` = XMLTV 的 `channel/@id`。

例如：
- XMLTV：`<channel id="1">...CCTV1...</channel>`
- M3U：`#EXTINF:-1 tvg-id="1" tvg-name="CCTV1",CCTV1`

这样 programme 的 `@channel="1"` 能直接命中。

### 7.2 多级匹配（当你无法控制 M3U）
优先级：
1. `m3u.tvgId == epgChannel.id`
2. `m3u.tvgName` 精确命中任一 `display-name`
3. 归一化名称匹配（忽略空格/横杠/大小写/常见后缀）
4. 人工映射表（JSON/本地配置）兜底

#### 归一化示例
```kotlin
fun normalizeName(s: String): String =
    s.trim()
     .lowercase()
     .replace(" ", "")
     .replace("-", "")
     .replace("_", "")
     .replace("高清", "")
     .replace("hd", "")
```

#### 映射表（推荐）
```json
{
  "CCTV-1": "1",
  "CCTV1综合": "1",
  "湖南卫视": "23"
}
```

实现思路：
- 先查 `map[m3uNameNormalized]`
- 再走自动匹配

---

## 8. “按天节目表”与跨天处理（重要）

设：
- `day: LocalDate`（用户选择的日期）
- `viewZone: ZoneId`（展示时区，一般 `ZoneId.systemDefault()`）
- programmes 全部为 Instant

### 8.1 策略 A：按 start 落在哪天（常见网页做法）
```kotlin
fun programmesForDayStartBased(
    programmes: List<EpgProgramme>,
    day: LocalDate,
    viewZone: ZoneId
): List<EpgProgramme> =
    programmes.filter { it.start.atZone(viewZone).toLocalDate() == day }
```

### 8.2 策略 B：与当天有交集都算（推荐，更符合用户直觉）
当天范围：
- dayStart = day 00:00（viewZone）
- dayEnd = nextDay 00:00（viewZone）

交集条件：
- `p.start < dayEnd && p.stop > dayStart`

```kotlin
fun programmesForDayOverlapBased(
    programmes: List<EpgProgramme>,
    day: LocalDate,
    viewZone: ZoneId
): List<EpgProgramme> {
    val dayStart = day.atStartOfDay(viewZone).toInstant()
    val dayEnd = day.plusDays(1).atStartOfDay(viewZone).toInstant()
    return programmes.filter { it.start < dayEnd && it.stop > dayStart }
}
```

**推荐采用策略 B**：可避免“跨天节目在第二天看不到”导致的用户感知“节目单不匹配”。

---

## 9. 当前节目（正在播放）与状态

```kotlin
import java.time.Instant

fun currentProgramme(
    programmesSorted: List<EpgProgramme>,
    now: Instant = Instant.now()
): EpgProgramme? =
    programmesSorted.firstOrNull { now >= it.start && now < it.stop }
```

状态：
- `LIVE`：`start <= now < stop`
- `UPCOMING`：`now < start`
- `ENDED`：`stop <= now`

---

## 10. UI 展示：时间格式化（只在展示层做时区）

```kotlin
import java.time.Instant
import java.time.ZoneId

fun formatHHmm(instant: Instant, zone: ZoneId): String {
    val zdt = instant.atZone(zone)
    val hh = zdt.hour.toString().padStart(2, '0')
    val mm = zdt.minute.toString().padStart(2, '0')
    return "$hh:$mm"
}
```

---

## 11. 组件建议（项目结构）

- `XmlTvEpgParser.kt`
  - `parse(input: InputStream): EpgData`
- `EpgData.kt`
  - `channelsById`, `programmesByChannelId`
- `ChannelMatcher.kt`
  - `match(m3uChannel): String?`
- `EpgRepository.kt`
  - `getDayPrograms(channelId, day, zone, mode)`
  - `getCurrentProgram(channelId, now)`
- `EpgCache.kt`（可选）
  - 缓存 raw xml 或解析结果（Room/文件）

---

## 12. 性能建议

1. **解析**
   - 使用流式解析（XmlPullParser）避免 DOM 占用内存
2. **索引**
   - programmes 按 channelId 分组 + 排序
3. **查询**
   - 当前节目可用二分查找（start 升序）
4. **更新策略**
   - EPG 通常每天更新一次即可（可按你的数据源频率）

---

## 13. 测试用例（强烈推荐加单测）

### 13.1 时间偏移测试
输入：`20260209012000 +0800`
- 解析为 Instant
- 再转 `ZoneOffset.ofHours(8)` 应得到 `2026-02-09 01:20`

### 13.2 跨天节目测试
节目：`23:30` → `00:30`（同一 offset）
- start-based：归到前一天
- overlap-based：两天都应包含（或至少第二天包含，取决于你定义）

### 13.3 当前节目测试
构造 `now` 落在 start/stop 区间，断言 currentProgramme 返回正确节目。

---

## 14. 排查清单（节目单对不上时按这个打印）

1. **频道映射是否正确**
   - IPTV 频道 -> epgChannelId 是否命中 `channelsById`
2. **时间是否正确**
   - 打印某节目 start/stop 的：
     - raw 字符串
     - Instant
     - Instant.atZone(viewZone) 格式化后的本地时间
3. **当天列表策略**
   - 当前使用 start-based 还是 overlap-based？
4. **now 的来源**
   - 是否使用 `Instant.now()`？
   - 是否错误使用 LocalDateTime/系统时区字符串？

---

## 15. 常见坑总结（一句话版）
- **永远把 XMLTV 时间解析成 Instant**（OffsetDateTime.parse → toInstant）
- **比较/排序/过滤都用 Instant**
- **展示才转 ZoneId**
- **频道匹配优先 tvg-id = channel/@id**
- **按天列表推荐 overlap-based，跨天更稳**

---

## 附：推荐的“最小落地流程图”
1. 下载/读取 EPG XML（InputStream）
2. `XmlTvEpgParser.parse()` -> `EpgData`
3. 解析 M3U -> `List<IptvChannel>`
4. `ChannelMatcher` 为每个 IPTV 频道确定 `epgChannelId`
5. UI：
   - EPG Day List：`getDayPrograms(epgChannelId, day, zone, overlapBased)`
   - Now Playing：`getCurrentProgram(epgChannelId)`

