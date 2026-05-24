package com.karaoke.poc.lyrics

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class LyricLine(val startTimeMs: Long, val endTimeMs: Long, val text: String)

class LyricsParser {
    fun parse(context: Context, filePath: String): List<LyricLine> {
        val lyricLines = mutableListOf<LyricLine>()
        val file = File(filePath)
        if (!file.exists()) {
            return emptyList()
        }

        var inputStream: InputStream? = null
        try {
            inputStream = FileInputStream(file)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var dStartTime: Double? = null
            var dEndTime: Double? = null
            var text: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "item") {
                            val startAttr = parser.getAttributeValue(null, "dStartTime")
                            val endAttr = parser.getAttributeValue(null, "dEndTime")
                            if (startAttr != null) {
                                dStartTime = startAttr.toDoubleOrNull()
                            }
                            if (endAttr != null) {
                                dEndTime = endAttr.toDoubleOrNull()
                            }
                        } else if (name == "text") {
                            text = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "item") {
                            if (dStartTime != null && dEndTime != null && text != null) {
                                val startMs = (dStartTime * 1000).toLong()
                                val endMs = (dEndTime * 1000).toLong()
                                lyricLines.add(LyricLine(startMs, endMs, text))
                            }
                            dStartTime = null
                            dEndTime = null
                            text = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return lyricLines
    }
}
