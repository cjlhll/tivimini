package com.cjlhll.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelGrouperTest {
    @Test
    fun groupByTvgNameAndPickFirstLogoVariant() {
        val channels = listOf(
            Channel(
                title = "CCTV-1 (1080p)",
                url = "http://a/cctv1.m3u8",
                tvgId = "CCTV1.cn@HD"
            ),
            Channel(
                title = "CCTV1",
                url = "http://b/cctv1.m3u8",
                group = "央视频道",
                logoUrl = "https://example.com/cctv1.png",
                tvgName = "CCTV1"
            ),
            Channel(
                title = "CCTV-1 (720p)",
                url = "http://c/cctv1hd.m3u8",
                logoUrl = "https://example.com/cctv1-720.png",
                tvgId = "CCTV1.cn@SD",
                tvgName = "CCTV1"
            )
        )

        val groups = ChannelGrouper.group(channels)

        assertEquals(1, groups.size)
        assertEquals(3, groups.first().variants.size)
        assertEquals("http://b/cctv1.m3u8", groups.first().defaultChannel.url)
        assertEquals("CCTV1", groups.first().displayTitle)
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
    fun displayChannelsReturnsOnePerGroup() {
        val channels = listOf(
            Channel(title = "CCTV1", url = "http://a/cctv1.m3u8", tvgName = "CCTV1"),
            Channel(title = "CCTV1 HD", url = "http://b/cctv1hd.m3u8", tvgName = "CCTV1"),
            Channel(title = "CCTV2", url = "http://c/cctv2.m3u8", tvgName = "CCTV2")
        )
        val groups = ChannelGrouper.group(channels)
        val display = ChannelGrouper.displayChannels(groups)

        assertEquals(2, display.size)
        assertTrue(display.any { it.tvgName == "CCTV1" })
        assertTrue(display.any { it.tvgName == "CCTV2" })
    }
}
