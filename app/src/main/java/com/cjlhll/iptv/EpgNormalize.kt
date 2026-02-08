package com.cjlhll.iptv

object EpgNormalize {
    private val removableTokens = listOf(
        "高清",
        "超清",
        "标清",
        "蓝光",
        "频道",
        "综合",
        "财经",
        "综艺",
        "国际",
        "体育",
        "电影",
        "电视剧",
        "纪录",
        "记录",
        "科教",
        "戏曲",
        "新闻",
        "少儿",
        "音乐",
        "法制",
        "社会",
        "生活",
        "教育",
        "军事",
        "农业",
        "hevc",
        "h265",
        "h.265",
        "h264",
        "h.264",
        "hdr",
        "uhd",
        "fhd",
        "hd",
        "4k"
    )

    fun keys(value: String): Set<String> {
        val base = key(value)
        if (base.isBlank()) return emptySet()

        val result = LinkedHashSet<String>()
        result.add(base)

        val cctv = extractCctvKey(base)
        if (cctv != null) result.add(cctv)

        return result
    }

    fun key(value: String): String {
        val lowered = value
            .replace('＋', '+')
            .trim()
            .lowercase()

        val normalizedBranding = lowered
            .replace("中央电视台", "cctv")
            .replace("中央电视", "cctv")
            .replace("央视", "cctv")
            .replace("凤凰卫视", "凤凰")

        val normalizedNumbering = normalizedBranding
            .replace("十七套", "17")
            .replace("十六套", "16")
            .replace("十五套", "15")
            .replace("十四套", "14")
            .replace("十三套", "13")
            .replace("十二套", "12")
            .replace("十一套", "11")
            .replace("十套", "10")
            .replace("九套", "9")
            .replace("八套", "8")
            .replace("七套", "7")
            .replace("六套", "6")
            .replace("五套", "5")
            .replace("四套", "4")
            .replace("三套", "3")
            .replace("二套", "2")
            .replace("一套", "1")
            .replace("套", "")

        val noBrackets = normalizedNumbering
            .replace(Regex("""[【】\[\]（）(){}<>《》]"""), "")
            .replace(Regex("（[^）]*）"), "")
            .replace(Regex("\\([^)]*\\)"), "")

        var s = noBrackets
            .replace("cctv-", "cctv")
            .replace("cctv_", "cctv")
            .replace("cctv ", "cctv")

        for (t in removableTokens) {
            s = s.replace(t, "")
        }

        return buildString(s.length) {
            for (ch in s) {
                if (ch.isWhitespace()) continue
                if (ch == '-' || ch == '_' || ch == '.' || ch == '·') continue
                append(ch)
            }
        }.trim()
    }

    private fun extractCctvKey(normalized: String): String? {
        val m = Regex("cctv(\\d{1,2})(\\+)?").find(normalized) ?: return null
        val num = m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val plus = m.groupValues.getOrNull(2)?.takeIf { it == "+" } ?: ""
        return "cctv" + num + plus
    }
}

