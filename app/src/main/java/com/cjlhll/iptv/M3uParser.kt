package com.cjlhll.iptv

import android.util.Log

object M3uParser {
    private const val TAG = "M3uParser"
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")
    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")
    private val tvgIdRegex = Regex("tvg-id=\"([^\"]*)\"")
    private val tvgNameRegex = Regex("tvg-name=\"([^\"]*)\"")
    private val catchupRegex = Regex("catchup=[\"']?([^\"'\\s]*)[\"']?")
    private val catchupSourceRegex = Regex("catchup-source=[\"']?([^\"']*)[\"']?")

    fun parse(content: String): List<Channel> {
        val channels = ArrayList<Channel>()

        var pendingTitle: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var pendingTvgId: String? = null
        var pendingTvgName: String? = null
        var pendingCatchup: String? = null
        var pendingCatchupSource: String? = null

        var logCount = 0

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                if (logCount < 10) {
                    Log.d(TAG, "Parsing line: $line")
                    logCount++
                }

                pendingGroup = groupRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingLogo = logoRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingTvgId = tvgIdRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingTvgName = tvgNameRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingCatchup = catchupRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingCatchupSource = catchupSourceRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingTitle = line.substringAfterLast(',', missingDelimiterValue = "").trim().takeIf { it.isNotBlank() }
                
                if (pendingCatchup != null || pendingCatchupSource != null) {
                    Log.d(TAG, "Found catchup info for ${pendingTitle}: mode=$pendingCatchup, source=$pendingCatchupSource")
                } else if (logCount < 10) {
                     Log.d(TAG, "No catchup info found in line for ${pendingTitle}")
                }
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
                    tvgName = pendingTvgName,
                    catchupMode = pendingCatchup ?: "append",
                    catchupSource = pendingCatchupSource ?: "?playseek=\${(b)yyyyMMddHHmmss}-\${(e)yyyyMMddHHmmss}"
                )
            )

            pendingTitle = null
            pendingGroup = null
            pendingLogo = null
            pendingTvgId = null
            pendingTvgName = null
            pendingCatchup = null
            pendingCatchupSource = null
        }

        return channels
    }
}
