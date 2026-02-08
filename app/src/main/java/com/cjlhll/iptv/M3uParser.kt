package com.cjlhll.iptv

object M3uParser {
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")
    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")
    private val tvgIdRegex = Regex("tvg-id=\"([^\"]*)\"")
    private val tvgNameRegex = Regex("tvg-name=\"([^\"]*)\"")

    fun parse(content: String): List<Channel> {
        val channels = ArrayList<Channel>()

        var pendingTitle: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var pendingTvgId: String? = null
        var pendingTvgName: String? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingGroup = groupRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingLogo = logoRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingTvgId = tvgIdRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingTvgName = tvgNameRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingTitle = line.substringAfterLast(',', missingDelimiterValue = "").trim().takeIf { it.isNotBlank() }
                continue
            }

            if (line.startsWith('#')) continue

            val url = line
            val title = pendingTitle ?: url

            channels.add(
                Channel(
                    title = title,
                    url = url,
                    group = pendingGroup,
                    logoUrl = pendingLogo,
                    tvgId = pendingTvgId,
                    tvgName = pendingTvgName
                )
            )

            pendingTitle = null
            pendingGroup = null
            pendingLogo = null
            pendingTvgId = null
            pendingTvgName = null
        }

        return channels
    }
}
