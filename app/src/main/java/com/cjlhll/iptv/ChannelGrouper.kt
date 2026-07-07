package com.cjlhll.iptv

data class ChannelVariant(
    val channel: Channel,
    val qualityLabel: String
)

data class ChannelGroup(
    val key: String,
    val displayTitle: String,
    val logoUrl: String?,
    val group: String?,
    val variants: List<ChannelVariant>,
    val defaultVariantIndex: Int
) {
    val defaultChannel: Channel get() = variants[defaultVariantIndex].channel
}

object ChannelGrouper {
    fun group(channels: List<Channel>): List<ChannelGroup> {
        if (channels.isEmpty()) return emptyList()

        val grouped = LinkedHashMap<String, MutableList<Channel>>()
        for (channel in channels) {
            val key = groupKey(channel)
            grouped.getOrPut(key) { mutableListOf() }.add(channel)
        }

        return grouped.map { (key, members) ->
            val variants = members.mapIndexed { index, channel ->
                ChannelVariant(channel, qualityLabel(channel, index))
            }
            val defaultIndex = variants.indexOfFirst { !it.channel.logoUrl.isNullOrBlank() }
                .takeIf { it >= 0 } ?: 0
            val defaultChannel = variants[defaultIndex].channel
            ChannelGroup(
                key = key,
                displayTitle = defaultChannel.tvgName?.takeIf { it.isNotBlank() }
                    ?: defaultChannel.title,
                logoUrl = defaultChannel.logoUrl,
                group = defaultChannel.group,
                variants = variants,
                defaultVariantIndex = defaultIndex
            )
        }
    }

    fun findBestGroupVariant(
        groups: List<ChannelGroup>,
        lastUrl: String?,
        lastTitle: String?
    ): Pair<Int, Int> {
        if (groups.isEmpty()) return 0 to 0

        if (!lastUrl.isNullOrBlank()) {
            groups.forEachIndexed { groupIndex, group ->
                group.variants.forEachIndexed { variantIndex, variant ->
                    if (variant.channel.url == lastUrl) return groupIndex to variantIndex
                }
            }
        }

        if (!lastTitle.isNullOrBlank()) {
            groups.forEachIndexed { groupIndex, group ->
                group.variants.forEachIndexed { variantIndex, variant ->
                    if (variant.channel.title == lastTitle) return groupIndex to variantIndex
                }
                if (group.displayTitle == lastTitle) {
                    return groupIndex to group.defaultVariantIndex
                }
            }
        }

        return 0 to groups.first().defaultVariantIndex
    }

    fun displayChannels(groups: List<ChannelGroup>): List<Channel> {
        return groups.map { it.defaultChannel }
    }

    fun allChannels(groups: List<ChannelGroup>): List<Channel> {
        return groups.flatMap { group -> group.variants.map { it.channel } }
    }

    private fun groupKey(channel: Channel): String {
        channel.tvgName?.takeIf { it.isNotBlank() }?.let { return EpgNormalize.key(it) }
        channel.tvgId?.takeIf { it.isNotBlank() }?.let { id ->
            val base = id.substringBefore('@').trim()
            if (base.isNotBlank()) return EpgNormalize.key(base)
        }
        return EpgNormalize.key(channel.title)
    }

    private fun qualityLabel(channel: Channel, index: Int): String {
        channel.tvgId?.let { id ->
            val suffix = id.substringAfterLast('@', "")
            if (suffix.isNotBlank() && suffix != id) {
                return suffix.uppercase()
            }
        }

        Regex("""\((\d+[pP]|4[Kk]|HD|SD|FHD|UHD|蓝光|超清|高清|标清)\)""")
            .find(channel.title)?.groupValues?.getOrNull(1)?.let { return it }

        val lowerUrl = channel.url.lowercase()
        when {
            lowerUrl.contains("/1080p/") || lowerUrl.contains("1080") -> return "1080p"
            lowerUrl.contains("/720p/") || lowerUrl.contains("720") -> return "720p"
            lowerUrl.contains("/480p/") || lowerUrl.contains("480") -> return "480p"
            lowerUrl.contains("hd.m3u8") || lowerUrl.contains("/hd/") -> return "HD"
            lowerUrl.contains("sd.m3u8") || lowerUrl.contains("/sd/") -> return "SD"
        }

        when {
            channel.title.contains("超清") || channel.title.contains("蓝光") -> return "超清"
            channel.title.contains("高清") -> return "高清"
            channel.title.contains("标清") -> return "标清"
        }

        return "线路${index + 1}"
    }
}
