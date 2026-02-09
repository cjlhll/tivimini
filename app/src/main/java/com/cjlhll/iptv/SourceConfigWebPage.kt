package com.cjlhll.iptv

import android.content.Context

object SourceConfigWebPage {
    fun html(context: Context): String {
        val appName = runCatching { context.getString(R.string.app_name) }.getOrElse { "IPTV" }
        return """
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover" />
  <title>$appName 源配置</title>
  <style>
    :root {
      --bg: #121212;
      --panel: #1e1e1e;
      --text: #f2f2f2;
      --muted: #a8a8a8;
      --border: #2f2f2f;
      --input: #2a2a2a;
      --focus: #3a3a3a;
      --btn: #2e7d32;
      --btnHover: #256428;
      --danger: #c62828;
    }

    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, "PingFang SC", "Noto Sans CJK SC", "Microsoft YaHei", sans-serif;
      background: var(--bg);
      color: var(--text);
      -webkit-text-size-adjust: 100%;
      text-size-adjust: 100%;
    }
    .wrap {
      max-width: 720px;
      margin: 0 auto;
      padding: 20px 16px 28px;
    }
    .card {
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 18px 16px;
    }
    h1 {
      margin: 0 0 6px;
      font-size: clamp(18px, 4.8vw, 20px);
      font-weight: 600;
      letter-spacing: .5px;
    }
    .sub {
      margin: 0 0 16px;
      color: var(--muted);
      font-size: clamp(14px, 3.8vw, 15px);
      line-height: 1.5;
    }
    label {
      display: block;
      margin: 14px 0 8px;
      color: var(--muted);
      font-size: 14px;
    }
    input {
      width: 100%;
      border: 1px solid var(--border);
      background: var(--input);
      color: var(--text);
      border-radius: 12px;
      padding: 12px 12px;
      font-size: 16px;
      outline: none;
    }
    input:focus {
      border-color: var(--focus);
      box-shadow: 0 0 0 3px rgba(255,255,255,.04);
    }
    .row {
      display: flex;
      flex-direction: column;
      gap: 12px;
      margin-top: 16px;
      align-items: stretch;
    }
    button {
      width: 100%;
      border: 0;
      border-radius: 12px;
      padding: 12px 14px;
      font-size: 16px;
      font-weight: 600;
      background: var(--btn);
      color: #fff;
      cursor: pointer;
    }
    button:hover { background: var(--btnHover); }
    button:disabled {
      opacity: .65;
      cursor: not-allowed;
    }
    .msg {
      font-size: 14px;
      color: var(--muted);
      min-height: 18px;
      text-align: right;
      width: 100%;
    }
    .err { color: #ffb4a9; }
    .ok { color: #b7f5c3; }

    @media (max-width: 420px) {
      .wrap { padding: 16px 14px 22px; }
      .card { border-radius: 14px; padding: 16px 14px; }
      .row { gap: 10px; }
    }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>源配置推送</h1>
      <p class="sub">与电视在同一局域网下，填写后点击“推送”即可同步到电视端输入框。</p>

      <label for="live">直播源地址</label>
      <input id="live" autocomplete="off" inputmode="url" placeholder="例如：https://example.com/live.m3u" />

      <label for="epg">EPG 地址</label>
      <input id="epg" autocomplete="off" inputmode="url" placeholder="例如：https://example.com/epg.xml" />

      <div class="row">
        <button id="btn">推送</button>
        <div id="msg" class="msg"></div>
      </div>
    </div>
  </div>
  <script>
    const $ = (id) => document.getElementById(id);
    const btn = $("btn");
    const msg = $("msg");
    function setMsg(text, cls) {
      msg.textContent = text || "";
      msg.className = "msg" + (cls ? " " + cls : "");
    }
    async function push() {
      const liveSource = $("live").value.trim();
      const epgSource = $("epg").value.trim();
      btn.disabled = true;
      setMsg("推送中...", "");
      try {
        const res = await fetch("/api/push", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ liveSource, epgSource })
        });
        const data = await res.json().catch(() => ({}));
        if (res.ok && data && data.ok) {
          setMsg("已推送到电视", "ok");
        } else {
          setMsg("推送失败", "err");
        }
      } catch (e) {
        setMsg("网络错误", "err");
      } finally {
        btn.disabled = false;
      }
    }
    btn.addEventListener("click", push);
  </script>
</body>
</html>
""".trimIndent()
    }
}
