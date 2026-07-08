package com.cjlhll.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelGrouperTest {
    @Test
    fun cnAllCctv1EntriesMergeIntoSingleGroup() {
        val channels = listOf(
            Channel(
                title = "CCTV-1 (1080p)",
                url = "http://69.30.245.50/live/cctv1.m3u8",
                tvgId = "CCTV1.cn@HD"
            ),
            Channel(
                title = "CCTV1",
                url = "http://118.193.115.2:9901/tsfile/live/0001_1.m3u8?key=txiptv",
                group = "央视频道",
                logoUrl = "https://gitee.com/mytv-android/myTVlogo/raw/main/img/CCTV1.png",
                tvgName = "CCTV1"
            ),
            Channel(
                title = "CCTV-1 (720p)",
                url = "http://74.91.26.218:82/live/cctv1hd.m3u8",
                logoUrl = "https://i.imgur.com/TpA3cUl.png",
                group = "General",
                tvgId = "CCTV1.cn@SD"
            ),
            Channel(
                title = "CCTV-1",
                url = "http://38.75.136.137:98/gslb/dsdqpub/cctv1hd.m3u8?auth=testpub",
                group = "央视频道",
                logoUrl = "https://gitee.com/mytv-android/myTVlogo/raw/main/img/CCTV1.png",
                tvgName = "CCTV1"
            ),
            Channel(
                title = "CCTV-1综合",
                url = "http://207.56.13.146:81/cdnlive/cctv1.m3u8",
                group = "央视频道",
                logoUrl = "https://gitee.com/mytv-android/myTVlogo/raw/main/img/CCTV1.png",
                tvgName = "CCTV1"
            ),
            Channel(
                title = "CCTV1",
                url = "http://124.228.160.210:9901/tsfile/live/0017_1.m3u8?key=txiptv&playlive=1&authid=0",
                group = "央视频道",
                logoUrl = "https://gitee.com/mytv-android/myTVlogo/raw/main/img/CCTV1.png",
                tvgName = "CCTV1"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
        assertEquals(6, groups.first().variants.size)
        assertEquals("CCTV1", groups.first().displayTitle)
    }

    @Test
    fun cnAllCctv2EntriesMergeIntoSingleGroup() {
        val channels = listOf(
            cctv2("CCTV2", "http://a/1.m3u8"),
            cctv2("CCTV-2", "http://a/2.m3u8"),
            cctv2("CCTV-2财经", "http://a/3.m3u8"),
            cctv2("CCTV2", "http://a/4.m3u8")
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
        assertEquals(4, groups.first().variants.size)
        assertEquals("CCTV2", groups.first().displayTitle)
    }

    @Test
    fun cctv5AndCctv5PlusStaySeparate() {
        val channels = listOf(
            Channel(
                title = "CCTV5+",
                url = "http://a/cctv5p.m3u8",
                logoUrl = "https://example.com/cctv5plus.png",
                tvgName = "CCTV5+"
            ),
            Channel(
                title = "CCTV5",
                url = "http://a/cctv5.m3u8",
                logoUrl = "https://example.com/cctv5.png",
                tvgName = "CCTV5"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(2, groups.size)
        assertEquals("CCTV5+", groups[0].displayTitle)
        assertEquals("CCTV5", groups[1].displayTitle)
    }

    @Test
    fun cctv1AndCctv2DoNotMerge() {
        val channels = listOf(
            Channel(
                title = "CCTV1",
                url = "http://a/cctv1.m3u8",
                logoUrl = "https://example.com/cctv1.png",
                tvgName = "CCTV1"
            ),
            Channel(
                title = "CCTV-1 (720p)",
                url = "http://b/cctv1hd.m3u8",
                logoUrl = "https://example.com/cctv1-720.png",
                tvgId = "CCTV1.cn@SD"
            ),
            Channel(
                title = "CCTV2",
                url = "http://c/cctv2.m3u8",
                logoUrl = "https://example.com/cctv2.png",
                tvgName = "CCTV2"
            ),
            Channel(
                title = "CCTV-2",
                url = "http://d/cctv2hd.m3u8",
                logoUrl = "https://example.com/cctv2-720.png",
                tvgId = "CCTV2.cn@SD"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(2, groups.size)
        assertEquals("cctv1", groups[0].key)
        assertEquals(2, groups[0].variants.size)
        assertEquals("cctv2", groups[1].key)
        assertEquals(2, groups[1].variants.size)
    }

    @Test
    fun bracketQualityTitlesMergeIntoSingleGroup() {
        val channels = listOf(
            Channel(
                title = "CCTV-1 (720p)",
                url = "http://a/cctv1-720.m3u8",
                logoUrl = "https://example.com/cctv1.png",
                group = "General",
                tvgId = "CCTV1.cn@SD"
            ),
            Channel(
                title = "CCTV1[1080][S]",
                url = "http://b/cctv1-1080.m3u8",
                logoUrl = "https://example.com/cctv1.png",
                group = "央视频道",
                tvgName = "CCTV1"
            ),
            Channel(
                title = "CCTV1[720][S]",
                url = "http://c/cctv1-720s.m3u8",
                logoUrl = "https://example.com/cctv1.png",
                group = "央视频道",
                tvgName = "CCTV1"
            ),
            Channel(
                title = "CCTV1[576][S]",
                url = "http://d/cctv1-576.m3u8",
                logoUrl = "https://example.com/cctv1.png",
                group = "央视频道",
                tvgName = "CCTV1"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
        assertEquals(4, groups.first().variants.size)
        assertEquals("CCTV1", groups.first().displayTitle)
        assertEquals("央视频道", groups.first().group)
    }

    @Test
    fun findGroupIndexByUrlReturnsCorrectGroup() {
        val channels = listOf(
            Channel(title = "CCTV1", url = "http://a/cctv1.m3u8", tvgName = "CCTV1"),
            Channel(title = "CCTV2", url = "http://b/cctv2.m3u8", tvgName = "CCTV2")
        )
        val groups = ChannelGrouper.group(channels)

        assertEquals(0, ChannelGrouper.findGroupIndexByUrl(groups, "http://a/cctv1.m3u8"))
        assertEquals(1, ChannelGrouper.findGroupIndexByUrl(groups, "http://b/cctv2.m3u8"))
    }

    @Test
    fun findBestGroupVariantMatchesUrlAcrossVariants() {
        val channels = listOf(
            Channel(title = "CCTV1", url = "http://a/cctv1.m3u8", tvgName = "CCTV1"),
            Channel(title = "CCTV1 HD", url = "http://b/cctv1hd.m3u8", tvgName = "CCTV1")
        )
        val groups = ChannelGrouper.group(channels)
        val (groupIndex, variantIndex) = ChannelGrouper.findBestGroupVariant(
            groups,
            lastUrl = "http://b/cctv1hd.m3u8",
            lastTitle = null
        )

        assertEquals(0, groupIndex)
        assertEquals(1, variantIndex)
    }

    @Test
    fun displayNameStripsQualityMarkers() {
        assertEquals("CCTV1", EpgNormalize.displayName("CCTV1[1080][S]"))
        assertEquals("CCTV-1", EpgNormalize.displayName("CCTV-1 (720p)"))
        assertEquals("CCTV2", EpgNormalize.displayName("CCTV2[720][S]"))
        assertEquals("贵州卫视", EpgNormalize.displayName("贵州卫视[F]"))
        assertEquals("天津卫视", EpgNormalize.displayName("天津卫视"))
    }

    @Test
    fun satelliteChannelsWithBracketFlagsMerge() {
        val channels = listOf(
            Channel(
                title = "贵州卫视",
                url = "http://a/guizhou.m3u8",
                logoUrl = "https://example.com/guizhou.png",
                tvgName = "贵州卫视"
            ),
            Channel(
                title = "贵州卫视[F]",
                url = "http://b/guizhou-f.m3u8",
                tvgName = "贵州卫视"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
        assertEquals("贵州卫视", groups.first().displayTitle)
        assertEquals(2, groups.first().variants.size)
    }

    @Test
    fun duplicateSatelliteChannelTitlesMerge() {
        val channels = listOf(
            Channel(
                title = "天津卫视",
                url = "http://a/tjws.m3u8",
                logoUrl = "https://example.com/tjws.png",
                tvgName = "天津卫视"
            ),
            Channel(
                title = "天津卫视",
                url = "http://b/tjws2.m3u8",
                tvgName = "天津卫视"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
        assertEquals(2, groups.first().variants.size)
    }

    @Test
    fun satelliteWithoutTvgNameMergesByTitle() {
        val channels = listOf(
            Channel(
                title = "天津卫视",
                url = "http://a/tjws.m3u8",
                logoUrl = "https://example.com/tjws.png",
                tvgName = "天津卫视"
            ),
            Channel(
                title = "天津卫视",
                url = "http://b/tjws2.m3u8"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
    }

    @Test
    fun primaryGroupKeyNormalizesTvgIdToCctvNumber() {
        val channel = Channel(
            title = "CCTV-1 (1080p)",
            url = "http://a/cctv1.m3u8",
            tvgId = "CCTV1.cn@HD"
        )

        assertEquals("cctv1", ChannelGrouper.primaryGroupKey(channel))
    }

    @Test
    fun primaryGroupKeyNormalizesBracketQualityTitle() {
        val channel = Channel(
            title = "CCTV1[1080][S]",
            url = "http://a/cctv1.m3u8"
        )

        assertEquals("cctv1", ChannelGrouper.primaryGroupKey(channel))
    }

    @Test
    fun canonicalKeysMergeTvgIdAndTvgName() {
        val withoutName = Channel(
            title = "CCTV-1 (1080p)",
            url = "http://a/cctv1.m3u8",
            tvgId = "CCTV1.cn@HD"
        )
        val withName = Channel(
            title = "CCTV1",
            url = "http://b/cctv1.m3u8",
            tvgName = "CCTV1"
        )

        val keysWithoutName = ChannelGrouper.canonicalKeys(withoutName)
        val keysWithName = ChannelGrouper.canonicalKeys(withName)

        assertTrue(keysWithoutName.intersect(keysWithName).isNotEmpty())
        assertTrue(keysWithoutName.contains("cctv1"))
        assertTrue(keysWithName.contains("cctv1"))
    }

    @Test
    fun findRecoveryVariantSkipsFailedUrl() {
        val channels = listOf(
            Channel(title = "天津卫视", url = "http://dead/tjws.m3u8", tvgName = "天津卫视"),
            Channel(title = "天津卫视", url = "http://live/tjws.m3u8", tvgName = "天津卫视")
        )
        val groups = ChannelGrouper.group(channels)
        val recovery = ChannelGrouper.findRecoveryVariant(groups, "http://dead/tjws.m3u8", "天津卫视")

        assertEquals(0 to 1, recovery)
    }

    @Test
    fun findBestGroupVariantFallsBackToPreviousGroupWhenUrlGone() {
        val oldChannels = listOf(
            Channel(title = "CCTV1", url = "http://old/cctv1.m3u8", tvgName = "CCTV1"),
            Channel(title = "CCTV2", url = "http://old/cctv2.m3u8", tvgName = "CCTV2"),
        )
        val oldGroups = ChannelGrouper.group(oldChannels)

        val newChannels = listOf(
            Channel(title = "CCTV1", url = "http://new/cctv1.m3u8", tvgName = "CCTV1"),
            Channel(title = "CCTV2", url = "http://new/cctv2.m3u8", tvgName = "CCTV2"),
        )
        val newGroups = ChannelGrouper.group(newChannels)

        val (groupIndex, variantIndex) = ChannelGrouper.findBestGroupVariant(
            newGroups,
            lastUrl = "http://old/cctv2.m3u8",
            lastTitle = null,
            previousGroups = oldGroups,
            previousGroupIndex = 1,
        )

        assertEquals(1, groupIndex)
        assertEquals(newGroups[1].defaultVariantIndex, variantIndex)
    }

    @Test
    fun resolveGroupInfersCctvWhenGroupTitleMissing() {
        val channel = Channel(
            title = "CCTV-16 (1080p)",
            url = "http://a/cctv16.m3u8",
            tvgId = "CCTV16.cn@HD",
        )

        assertEquals("央视频道", ChannelGrouper.resolveGroup(channel))
    }

    @Test
    fun resolveGroupNormalizesGeneralAndCctvAliasGroups() {
        val general = Channel(
            title = "CCTV-1 (720p)",
            url = "http://a/cctv1.m3u8",
            group = "General",
            tvgId = "CCTV1.cn@SD",
        )
        val cctvStation = Channel(
            title = "CCTV-4 中文国际",
            url = "http://a/cctv4.m3u8",
            group = "央视台",
            tvgId = "CCTV-4",
        )
        val sports = Channel(
            title = "CCTV5",
            url = "http://a/cctv5.m3u8",
            group = "体育频道",
            tvgName = "CCTV5",
        )

        assertEquals("央视频道", ChannelGrouper.resolveGroup(general))
        assertEquals("央视频道", ChannelGrouper.resolveGroup(cctvStation))
        assertEquals("央视频道", ChannelGrouper.resolveGroup(sports))
    }

    @Test
    fun groupAssignsCctv16ToCctvGroupWhenOnlySourceHasNoGroupTitle() {
        val channels = listOf(
            Channel(
                title = "CCTV-16 (1080p)",
                url = "http://74.91.26.218:82/live/cctv16hd.m3u8",
                tvgId = "CCTV16.cn@HD",
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
        assertEquals("央视频道", groups.first().group)
    }

    private fun cctv2(title: String, url: String): Channel {
        return Channel(
            title = title,
            url = url,
            group = "央视频道",
            logoUrl = "https://gitee.com/mytv-android/myTVlogo/raw/main/img/CCTV2.png",
            tvgName = "CCTV2"
        )
    }
}
