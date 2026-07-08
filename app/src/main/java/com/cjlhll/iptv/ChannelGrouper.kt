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
    private val cctvKeyRegex = Regex("""cctv\d{1,2}\+?""")
    private val cctvInferRegex = Regex("""(?i)cctv[\s\-]?(\d{1,2}\+?|4k?)""")
    private val cctvTvgIdRegex = Regex("""(?i)^cctv(\d{1,2}\+?)""")
    private val satelliteRegex = Regex("""卫视$""")
    private val cartoonNameRegex = Regex("""(?i)(金鹰卡通|卡酷|嘉佳|优漫|哈哈|炫动|CCTV14|CCTV-14)""")
    private val otherChannelRegex = Regex("""(?i)^凤凰""")
    private val cctvCartoonNumbers = setOf("14")
    private val bracketQualityRegex = Regex("""\[(\d{3,4})[pP]?\]""")
    private val bracketFlagRegex = Regex("""\[([A-Za-z]+)\]""")

    private val groupSortOrder = listOf("央视频道", "卫视频道", "卡通频道", "其他频道", "未分组")

    fun group(channels: List<Channel>): List<ChannelGroup> {
        if (channels.isEmpty()) return emptyList()

        val keyToGroupIndex = HashMap<String, Int>()
        val groupedMembers = mutableListOf<MutableList<Channel>>()

        for (channel in channels) {
            val keys = mergeKeys(channel)
            val existingIndex = keys.firstNotNullOfOrNull { keyToGroupIndex[it] }

            if (existingIndex != null) {
                groupedMembers[existingIndex].add(channel)
                for (key in keys) {
                    keyToGroupIndex[key] = existingIndex
                }
            } else {
                val newIndex = groupedMembers.size
                groupedMembers.add(mutableListOf(channel))
                for (key in keys) {
                    keyToGroupIndex[key] = newIndex
                }
            }
        }

        return groupedMembers.map { members ->
            val variants = members.mapIndexed { index, channel ->
                ChannelVariant(channel, qualityLabel(channel, index))
            }
            val defaultIndex = variants.indexOfFirst { !it.channel.logoUrl.isNullOrBlank() }
                .takeIf { it >= 0 } ?: 0
            val defaultChannel = variants[defaultIndex].channel
            val allKeys = members.flatMap { mergeKeys(it).asIterable() }.toSet()
            ChannelGroup(
                key = pickPrimaryKey(allKeys),
                displayTitle = displayTitleForGroup(members, defaultChannel),
                logoUrl = defaultChannel.logoUrl,
                group = pickGroupTitle(members) ?: resolveGroup(defaultChannel),
                variants = variants,
                defaultVariantIndex = defaultIndex
            )
        }
    }

    fun findGroupIndexByUrl(groups: List<ChannelGroup>, url: String?): Int? {
        if (url.isNullOrBlank()) return null
        return groups.indexOfFirst { group ->
            group.variants.any { it.channel.url == url }
        }.takeIf { it >= 0 }
    }

    fun findVariantIndexByUrl(group: ChannelGroup, url: String?): Int {
        if (url.isNullOrBlank()) return group.defaultVariantIndex
        return group.variants.indexOfFirst { it.channel.url == url }
            .takeIf { it >= 0 } ?: group.defaultVariantIndex
    }

    fun findBestGroupVariant(
        groups: List<ChannelGroup>,
        lastUrl: String?,
        lastTitle: String?,
        previousGroups: List<ChannelGroup>? = null,
        previousGroupIndex: Int? = null,
    ): Pair<Int, Int> {
        if (groups.isEmpty()) return 0 to 0

        if (!lastUrl.isNullOrBlank()) {
            findGroupIndexByUrl(groups, lastUrl)?.let { groupIndex ->
                val group = groups[groupIndex]
                return groupIndex to findVariantIndexByUrl(group, lastUrl)
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

        if (previousGroups != null && previousGroupIndex != null) {
            previousGroups.getOrNull(previousGroupIndex)?.let { oldGroup ->
                groups.indexOfFirst { group ->
                    group.key == oldGroup.key || group.displayTitle == oldGroup.displayTitle
                }.takeIf { it >= 0 }?.let { matchedIndex ->
                    return matchedIndex to groups[matchedIndex].defaultVariantIndex
                }
            }
        }

        return 0 to groups.first().defaultVariantIndex
    }

    fun containsUrl(groups: List<ChannelGroup>, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return groups.any { group -> group.variants.any { it.channel.url == url } }
    }

    fun findRecoveryVariant(
        groups: List<ChannelGroup>,
        failedUrl: String?,
        lastTitle: String?,
        previousGroups: List<ChannelGroup>? = null,
        previousGroupIndex: Int? = null,
    ): Pair<Int, Int>? {
        if (groups.isEmpty()) return null
        val (groupIndex, currentVariantIndex) = findBestGroupVariant(
            groups,
            failedUrl,
            lastTitle,
            previousGroups,
            previousGroupIndex,
        )
        val group = groups.getOrNull(groupIndex) ?: return null

        if (!failedUrl.isNullOrBlank()) {
            group.variants.forEachIndexed { index, variant ->
                if (variant.channel.url != failedUrl) {
                    return groupIndex to index
                }
            }
        }

        return groupIndex to group.defaultVariantIndex
    }

    fun displayGroupName(group: ChannelGroup): String {
        return group.group?.takeIf { it.isNotBlank() } ?: "未分组"
    }

    fun sortedDrawerGroups(channelGroups: List<ChannelGroup>): List<String> {
        val names = LinkedHashSet<String>()
        for (group in channelGroups) {
            names.add(displayGroupName(group))
        }
        val ordered = groupSortOrder.filter { it in names }
        val rest = names.filter { it !in groupSortOrder }.sorted()
        return listOf("全部") + ordered + rest
    }

    internal fun resolveGroup(channel: Channel): String? {
        val raw = channel.group?.trim()?.takeIf { it.isNotBlank() }
        raw?.let { normalizeGroupAlias(it, channel) }?.let { return it }
        return inferGroupFromMetadata(channel)
    }

    fun displayChannels(groups: List<ChannelGroup>): List<Channel> {
        return groups.map { group ->
            val channel = group.defaultChannel
            channel.copy(title = EpgNormalize.displayName(group.displayTitle))
        }
    }

    fun allChannels(groups: List<ChannelGroup>): List<Channel> {
        return groups.flatMap { group -> group.variants.map { it.channel } }
    }

    internal fun canonicalKeys(channel: Channel): Set<String> {
        val keys = LinkedHashSet<String>()
        channel.tvgName?.takeIf { it.isNotBlank() }?.let { keys.addAll(EpgNormalize.keys(it)) }
        channel.tvgId?.takeIf { it.isNotBlank() }?.let { id ->
            val base = id.substringBefore('@').trim()
            if (base.isNotBlank()) keys.addAll(EpgNormalize.keys(base))
            keys.addAll(EpgNormalize.keys(id))
        }
        keys.addAll(EpgNormalize.keys(channel.title))
        return keys
    }

    internal fun mergeKeys(channel: Channel): Set<String> {
        val keys = LinkedHashSet<String>()

        val label = EpgNormalize.displayName(
            channel.tvgName?.takeIf { it.isNotBlank() } ?: channel.title
        )
        keys.add(EpgNormalize.key(label))

        channel.tvgName?.takeIf { it.isNotBlank() }?.let { name ->
            keys.addAll(EpgNormalize.keys(name))
        }

        channel.tvgId?.takeIf { it.isNotBlank() }?.let { id ->
            val base = id.substringBefore('@').trim()
            if (base.isNotBlank()) {
                keys.add(EpgNormalize.key(base))
                keys.addAll(EpgNormalize.keys(base))
            }
        }

        keys.add(EpgNormalize.key(EpgNormalize.displayName(channel.title)))
        EpgNormalize.keys(channel.title)
            .filter { it.matches(cctvKeyRegex) }
            .forEach { keys.add(it) }

        return keys.filter { it.isNotBlank() && it != "unknown" }.toSet()
    }

    internal fun primaryGroupKey(channel: Channel): String {
        return pickPrimaryKey(mergeKeys(channel).ifEmpty { canonicalKeys(channel) })
    }

    private fun pickPrimaryKey(keys: Set<String>): String {
        if (keys.isEmpty()) return "unknown"
        keys.firstOrNull { it.matches(cctvKeyRegex) }?.let { return it }
        return keys.minByOrNull { it.length } ?: keys.first()
    }

    private fun displayTitleForGroup(members: List<Channel>, defaultChannel: Channel): String {
        val raw = members.firstNotNullOfOrNull { it.tvgName?.takeIf { name -> name.isNotBlank() } }
            ?: defaultChannel.title
        return EpgNormalize.displayName(raw)
    }

    private fun pickGroupTitle(members: List<Channel>): String? {
        val groups = members.mapNotNull { resolveGroup(it) }
        if (groups.isEmpty()) return null
        return groups
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { groupSortOrder.indexOf(it.key).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.key }
            )
            .firstOrNull()
            ?.key
    }

    private fun normalizeGroupAlias(raw: String, channel: Channel): String? {
        if (raw.equals("General", ignoreCase = true)) {
            return inferGroupFromMetadata(channel)
        }
        if (raw == "央视台") return "央视频道"
        if (raw == "体育频道" && isCctvChannel(channel)) return "央视频道"
        return raw
    }

    private fun inferGroupFromMetadata(channel: Channel): String? {
        val texts = listOfNotNull(
            channel.tvgId?.substringBefore('@')?.trim()?.takeIf { it.isNotBlank() },
            channel.tvgName?.trim()?.takeIf { it.isNotBlank() },
            channel.title.trim().takeIf { it.isNotBlank() },
        )
        for (text in texts) {
            if (cartoonNameRegex.containsMatchIn(text) || cctvCartoonNumber(text) != null) {
                return "卡通频道"
            }
            if (otherChannelRegex.containsMatchIn(text)) {
                return "其他频道"
            }
            if (isCctvText(text)) {
                return "央视频道"
            }
            val display = EpgNormalize.displayName(text)
            if (satelliteRegex.containsMatchIn(display)) {
                return "卫视频道"
            }
        }
        return null
    }

    private fun isCctvChannel(channel: Channel): Boolean {
        return listOfNotNull(channel.tvgId, channel.tvgName, channel.title).any { isCctvText(it) }
    }

    private fun isCctvText(text: String): Boolean {
        if (cctvTvgIdRegex.containsMatchIn(text.substringBefore('.'))) return true
        return cctvInferRegex.containsMatchIn(text)
    }

    private fun cctvCartoonNumber(text: String): String? {
        val number = cctvTvgIdRegex.find(text.substringBefore('.'))?.groupValues?.getOrNull(1)
            ?: cctvInferRegex.find(text)?.groupValues?.getOrNull(1)
        return number?.removeSuffix("+")?.takeIf { it in cctvCartoonNumbers }
    }

    private fun qualityLabel(channel: Channel, index: Int): String {
        channel.tvgId?.let { id ->
            val suffix = id.substringAfterLast('@', "")
            if (suffix.isNotBlank() && suffix != id) {
                return suffix.uppercase()
            }
        }

        bracketQualityRegex.find(channel.title)?.groupValues?.getOrNull(1)?.let { return "${it}p" }

        bracketFlagRegex.find(channel.title)?.groupValues?.getOrNull(1)?.let { return it.uppercase() }

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
