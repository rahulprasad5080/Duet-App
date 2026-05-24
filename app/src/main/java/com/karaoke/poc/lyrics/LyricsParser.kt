package com.karaoke.poc.lyrics

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.Charset

data class LyricLine(val startTimeMs: Long, val endTimeMs: Long, val text: String)

class LyricsParser {
    fun parse(context: Context, filePath: String): List<LyricLine> {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("LYRICS", "File does not exist: $filePath")
            return emptyList()
        }

        val lyricLines = mutableListOf<LyricLine>()
        try {
            // Determine encoding (UTF-16, UTF-16LE, UTF-16BE, UTF-8)
            val bytes = file.readBytes()
            val hasUtf16Bom = if (bytes.size >= 2) {
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            } else {
                false
            }

            Log.d("LYRICS", "encoding=UTF16")
            val textContent = if (hasUtf16Bom) {
                String(bytes, Charsets.UTF_16)
            } else {
                // Try UTF-16 first, fall back to UTF-8
                try {
                    String(bytes, Charsets.UTF_16)
                } catch (e: Exception) {
                    try {
                        String(bytes, Charsets.UTF_16LE)
                    } catch (e2: Exception) {
                        try {
                            String(bytes, Charsets.UTF_16BE)
                        } catch (e3: Exception) {
                            String(bytes, Charsets.UTF_8)
                        }
                    }
                }
            }

            // Regex parsing: Find all <item ...> ... </item> blocks
            // This is completely immune to the invalid XML attributes like '3DMixColor'
            val itemRegex = "(?i)<item\\b[^>]*>[\\s\\S]*?</item>".toRegex()
            val startTimeRegex = "(?i)\\bdStartTime=\"([^\"]+)\"".toRegex()
            val endTimeRegex = "(?i)\\bdEndTime=\"([^\"]+)\"".toRegex()
            val textRegex = "(?i)<text>([\\s\\S]*?)</text>".toRegex()

            val matches = itemRegex.findAll(textContent)
            for (match in matches) {
                val block = match.value
                val startMatch = startTimeRegex.find(block)
                val endMatch = endTimeRegex.find(block)
                val textMatch = textRegex.find(block)

                val startStr = startMatch?.groupValues?.get(1)
                val endStr = endMatch?.groupValues?.get(1)
                val textValue = textMatch?.groupValues?.get(1) ?: ""

                if (startStr != null) {
                    val dStartTime = startStr.toDoubleOrNull()
                    val dEndTime = endStr?.toDoubleOrNull() ?: dStartTime
                    if (dStartTime != null) {
                        val startMs = (dStartTime * 1000).toLong()
                        val endMs = ((dEndTime ?: dStartTime) * 1000).toLong()
                        lyricLines.add(LyricLine(startMs, endMs, textValue))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LYRICS", "Regex parsing exception: ${e.message}", e)
        }

        Log.d("LYRICS", "items=${lyricLines.size}")
        return lyricLines
    }
}
