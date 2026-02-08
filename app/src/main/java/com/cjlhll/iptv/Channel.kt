package com.cjlhll.iptv

data class Channel(
    val title: String,
    val url: String,
    val group: String? = null,
    val logoUrl: String? = null
)
