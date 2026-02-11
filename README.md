# IPTV（Android TV）

一个基于 Jetpack Compose for TV 开发的深色风格电视端 IPTV 播放器。

## 功能特点

*   **直播播放**：支持通过 URL 加载 `m3u/m3u8` 直播源，流畅播放。
*   **节目单 (EPG)**：支持加载 XMLTV 格式的节目单，在频道列表中展示当前及后续节目信息。
*   **回看/时移**：支持节目回看功能（需直播源支持），不错过精彩内容。
*   **便捷配置**：
    *   **手机推送**：在电视端生成二维码，手机扫码或访问局域网地址，即可直接推送直播源与 EPG 地址，无需遥控器输入繁琐链接。
    *   **历史记录**：自动记忆上次播放的频道。
*   **遥控器交互**：专为电视遥控器设计的交互体验，操作简单直观。

## 使用指南

### 1. 首次配置
首次启动将进入“源配置”页面：
*   **直播源**：输入 `m3u` 列表地址。
*   **EPG 源**（可选）：输入 XMLTV 地址。

### 2. 手机推送配置
在“源配置”页面，系统会启动局域网服务并显示二维码及 IP 地址。
使用手机（连接同一局域网）扫描二维码或浏览器访问该地址，即可在手机上粘贴源地址并一键推送到电视。

### 3. 遥控器操作
*   **方向键 上/下**：切换频道
*   **方向键 左**：打开频道/节目列表
*   **方向键 右 / 菜单键**：打开设置/源配置
*   **确认键 (Center/Enter)**：显示当前频道信息
*   **返回键**：关闭菜单/退出应用

## 预览

| | | |
|:---:|:---:|:---:|
| <img src="./screenshot/Screenshot_20260211_090032.png" width="300" /> | <img src="./screenshot/Screenshot_20260211_090058.png" width="300" /> | <img src="./screenshot/Screenshot_20260211_090121.png" width="300" /> |
| <img src="./screenshot/Screenshot_20260211_090131.png" width="300" /> | <img src="./screenshot/Screenshot_20260211_090143.png" width="300" /> | <img src="./screenshot/Screenshot_20260211_090200.png" width="300" /> |
| <img src="./screenshot/Screenshot_20260211_090222.png" width="300" /> | <img src="./screenshot/Screenshot_20260211_090309.png" width="300" /> |
