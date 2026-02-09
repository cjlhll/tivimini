package com.cjlhll.iptv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object XmlTvParser {
    private val XMLTV_TIME: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("yyyyMMddHHmmss")
            .optionalStart()
            .appendLiteral(' ')
            .appendOffset("+HHmm", "+0000")
            .optionalEnd()
            .toFormatter(Locale.US)

    fun parse(inputStream: InputStream): EpgData {
        val programsByChannelId = HashMap<String, MutableList<EpgProgram>>()
        val displayNameToId = HashMap<String, String>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var currentChannelId: String? = null

        var programmeChannelId: String? = null
        var programmeStart: Instant? = null
        var programmeStop: Instant? = null
        var programmeTitle: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            currentChannelId = parser.getAttributeValue(null, "id")?.trim()
                        }

                        "display-name" -> {
                            val id = currentChannelId
                            if (!id.isNullOrBlank()) {
                                val name = readText(parser)
                                for (key in EpgNormalize.keys(name)) {
                                    if (key.isNotBlank()) displayNameToId[key] = id
                                }
                            }
                        }

                        "programme" -> {
                            programmeChannelId = parser.getAttributeValue(null, "channel")?.trim()
                            programmeStart = parseXmlTvInstant(parser.getAttributeValue(null, "start"))
                            programmeStop = parseXmlTvInstant(parser.getAttributeValue(null, "stop"))
                            programmeTitle = null
                        }

                        "title" -> {
                            if (!programmeChannelId.isNullOrBlank()) {
                                programmeTitle = readText(parser)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            currentChannelId = null
                        }

                        "programme" -> {
                            val channelId = programmeChannelId
                            var start = programmeStart
                            var stop = programmeStop
                            val title = programmeTitle?.trim().orEmpty()

                            if (!channelId.isNullOrBlank() && start != null && stop != null && title.isNotBlank()) {
                                // Fix cross-day issue: if stop <= start, assume it means next day (add 24h)
                                if (!stop.isAfter(start)) {
                                    stop = stop.plusSeconds(24 * 3600)
                                }

                                val list = programsByChannelId.getOrPut(channelId) { ArrayList() }
                                list.add(
                                    EpgProgram(
                                        channelId = channelId,
                                        startMillis = start.toEpochMilli(),
                                        endMillis = stop.toEpochMilli(),
                                        title = title
                                    )
                                )
                            }

                            programmeChannelId = null
                            programmeStart = null
                            programmeStop = null
                            programmeTitle = null
                        }
                    }
                }
            }

            eventType = parser.next()
        }

        val sorted = programsByChannelId.mapValues { (_, list) ->
            list.sortedBy { it.startMillis }
        }

        return EpgData(
            programsByChannelId = sorted,
            normalizedDisplayNameToChannelId = displayNameToId
        )
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result.trim()
    }

    private fun parseXmlTvInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            // Try to parse using the robust formatter
            // If offset is missing, XMLTV spec says assume UTC or local.
            // But common Chinese EPGs might omit it implying +0800.
            // The formatter handles optional offset.
            // However, OffsetDateTime.parse requires an offset in the string if the formatter doesn't supply a default.
            // Our formatter has optional offset. If it's missing, parse might fail or return a TemporalAccessor that needs default zone.
            
            // Actually, for simplicity and robustness as per doc:
            // "Use OffsetDateTime.parse(raw, XMLTV_TIME)" 
            // If the input string has no offset, XMLTV_TIME optional part won't match, and we might get a DateTimeParseException 
            // because OffsetDateTime requires an offset.
            // We should try to parse as OffsetDateTime first. 
            // If that fails (e.g. no offset), try LocalDateTime and attach system default or +0800.
            
            // Let's stick to the doc's recommended function structure but ensure it works.
            val odt = OffsetDateTime.parse(raw.trim(), XMLTV_TIME)
            odt.toInstant()
        } catch (e: Exception) {
            // Fallback: try parsing as LocalDateTime (no offset) and assume +08:00 (CN common) or System Default
            try {
                // Remove potential timezone garbage if any, just take first 14 chars
                val trimmed = raw.trim()
                val digits = trimmed.takeWhile { it.isDigit() }
                if (digits.length >= 12) {
                     val full = if (digits.length >= 14) digits.substring(0, 14) else digits.substring(0, 12) + "00"
                     // Use a simple formatter for pure digits
                     val ldt = java.time.LocalDateTime.parse(full, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                     // Assume +08:00 as per user requirement context (CN IPTV) or System Default
                     // The doc says: "Default +0800 if missing"
                     ldt.atOffset(java.time.ZoneOffset.ofHours(8)).toInstant()
                } else {
                    null
                }
            } catch (e2: Exception) {
                null
            }
        }
    }
}

