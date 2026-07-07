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
        assertEquals(
            "http://118.193.115.2:9901/tsfile/live/0001_1.m3u8?key=txiptv",
            groups.first().defaultChannel.url
        )
        assertEquals("CCTV1", ChannelGrouper.displayChannels(groups).single().title)
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
    fun primaryGroupKeyNormalizesTvgIdToCctvNumber() {
        val channel = Channel(
            title = "CCTV-1 (1080p)",
            url = "http://a/cctv1.m3u8",
            tvgId = "CCTV1.cn@HD"
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
