# 页面设计（Android TV IPTV）

## 全局设计规范

* Layout：以 10-foot UI 为核心，主要使用 Compose 的 Column/Row + LazyColumn/LazyRow；抽屉使用“左侧覆盖层 + 两列布局（Row）”；间距使用统一 spacing token。

* Global Styles：

  * 主题：深色为主，优先使用 MaterialTheme.colorScheme 的系统变量（background/surface/onSurface 等）。

  * 强调色：默认跟随系统 colorScheme.primary；若系统 primary 呈蓝紫倾向，应用应以中性深灰 + 绿色/青绿色强调色覆盖 primary（避免蓝紫观感）。

  * 字体：使用 TV 端可读性更强的字号梯度（标题/列表项/辅助信息分层）。

  * 焦点态：焦点框/放大/阴影必须清晰；列表项获得焦点时提升对比度（surface → surfaceVariant），并显示选中态。

  * 交互：动画以 120–180ms 的淡入淡出/轻微缩放为主，避免花哨。

***

## 1）首页（频道浏览）

### Layout

* 左侧（可选）：当前直播源信息与入口（按钮）。

* 主区域：上方为“分组/搜索（可选）”与提示信息；下方为分组与频道浏览区。

### Meta Information

* Android：Activity/Screen Title = “频道浏览”；Description = “选择分组并进入频道播放”。

### Page Structure

* 顶部栏：应用名 + 当前源名称 + 进入源管理按钮。

* 内容区：

  * 分组列表：垂直列表（LazyColumn），每项显示分组名。

  * 频道列表：垂直列表（LazyColumn），每项显示频道名（可预留台标位置，但台标核心在播放器抽屉页）。

### Sections & Components

* 源导入/选择：按钮（导入本地 m3u 或输入 URL）。

* 列表交互：遥控器上下移动，右方向切到频道列表，确定键进入播放器。

***

## 2）播放器页

### Layout

* 基础：全屏视频区域（Box 作为容器，底层为 PlayerView/ComposeVideoSurface）。

* 叠加层：

  * 顶部信息条（可自动隐藏）：频道名、分组名（可选）。

  * 左侧抽屉覆盖层：按左键出现，覆盖在视频之上。

### Meta Information

* Android：Activity/Screen Title = “播放”；Description = “观看频道并通过左侧抽屉快速切换”。

### Page Structure

* Video Layer：占满屏幕。

* Drawer Layer（按左键打开）：

  * Drawer 容器：从屏幕左侧滑入，宽度约 55%–70%（视字号而定），背景使用 colorScheme.surface + 透明度。

  * Drawer 内部为 Row：左列“分组列”，右列“频道列”。

### Sections & Components

1. 视频播放

* 默认进入播放器后立即播放当前频道。

* 网络抖动/加载时显示简洁 loading（避免遮挡过大）。

1. 左键抽屉（核心）

* 打开：在播放器界面按 DPAD\_LEFT 打开抽屉。

* 关闭：

  * 抽屉内按 DPAD\_RIGHT 多次可回到视频区域；或按返回键关闭（具体以产品约定为准）。

* 焦点策略：

  * 抽屉打开后，焦点优先落在“频道列当前频道”或“分组列当前分组”（二选一，但需一致）。

  * 左右切换：分组列 <-> 频道列；从频道列继续右移可退出抽屉回到视频。

1. 分组列

* 组件：LazyColumn。

* 内容：分组名；高亮当前分组。

* 交互：选中分组后刷新右侧频道列并保持焦点在频道列第一项或上次频道位置。

1. 频道列（含台标）

* 组件：LazyColumn。

* 单项结构（Row）：

  * 左侧台标：固定尺寸（建议 48–56dp 正方形或 64x36dp 近似 16:9），使用圆角矩形裁切；

  * 右侧信息：频道名（主）、可选的副信息（如分辨率/来源标记，非必需）。

* 台标加载：

  * 有 logoUrl：异步加载并缓存；

  * 无/失败：展示占位（单色底 + 首字母/通用电视图标）。

* 交互：确定键切换到该频道并立即开始播放；抽屉保持打开以便连续切台（或切台后自动收起，二选一，需产品统一）。

***

## 3）源管理页

### Layout

* 单列卡片列表：源条目列表（名称+地址简略）。

* 顶部操作：新增源、刷新。

### Meta Information

* Android：Activity/Screen Title = “源管理”；Description = “管理 m3u 源并刷新解析结果”。

### Sections & Components

* 源列表：每项支持进入编辑、删除。

* 新增/编辑表单：名称、类型（本地/URL）、地址/文件选择。

* 刷新：对当前源执行重新拉取与解析。

