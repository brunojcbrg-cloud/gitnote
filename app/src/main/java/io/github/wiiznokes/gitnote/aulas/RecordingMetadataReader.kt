package io.github.wiiznokes.gitnote.aulas

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class RecordingTimestamp(val value: String, val source: String)

object RecordingMetadataReader {
    fun read(fileName: String, input: InputStream): RecordingTimestamp? = when {
        fileName.endsWith(".mp3", ignoreCase = true) -> readMp3(input)
        fileName.endsWith(".m4a", ignoreCase = true) || fileName.endsWith(".mp4", ignoreCase = true) -> readMvhd(input)
        else -> null
    }

    private fun readMp3(input: InputStream): RecordingTimestamp? {
        val header = input.readExactlyOrNull(10) ?: return null
        if (!header.copyOfRange(0, 3).contentEquals("ID3".toByteArray())) return null
        val version = header[3].toInt() and 0xff
        val tagSize = synchsafe(header, 6)
        if (tagSize <= 0 || tagSize > 16 * 1024 * 1024) return null
        val data = input.readExactlyOrNull(tagSize) ?: return null
        var offset = 0
        val values = mutableMapOf<String, String>()
        while (offset + 10 <= data.size) {
            val id = data.copyOfRange(offset, offset + 4).toString(Charsets.ISO_8859_1)
            if (id.all { it == '\u0000' }) break
            val size = if (version == 4) synchsafe(data, offset + 4) else ByteBuffer
                .wrap(data, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            if (size <= 0 || offset + 10 + size > data.size) break
            if (id in setOf("TDRC", "TYER", "TDAT", "TIME")) {
                values[id] = decodeText(data.copyOfRange(offset + 10, offset + 10 + size))
            }
            offset += 10 + size
        }
        normalizeDate(values["TDRC"])?.let { return RecordingTimestamp(it, "ID3:TDRC") }
        val year = values["TYER"]?.filter(Char::isDigit)?.take(4) ?: return null
        val ddat = values["TDAT"]?.filter(Char::isDigit).orEmpty().padStart(4, '0')
        val time = values["TIME"]?.filter(Char::isDigit).orEmpty().padEnd(4, '0')
        if (ddat.length < 4) return null
        val composed = "$year-${ddat.substring(2, 4)}-${ddat.substring(0, 2)}T${time.substring(0, 2)}:${time.substring(2, 4)}:00"
        return normalizeDate(composed)?.let { RecordingTimestamp(it, "ID3:TDAT/TIME/TYER") }
    }

    private fun readMvhd(input: InputStream): RecordingTimestamp? {
        val bytes = input.readAtMost(4 * 1024 * 1024)
        var index = 0
        while (index + 16 <= bytes.size) {
            if (bytes[index + 4] == 'm'.code.toByte() && bytes[index + 5] == 'v'.code.toByte() &&
                bytes[index + 6] == 'h'.code.toByte() && bytes[index + 7] == 'd'.code.toByte()
            ) {
                val version = bytes[index + 8].toInt() and 0xff
                val creation = if (version == 1 && index + 20 <= bytes.size) {
                    ByteBuffer.wrap(bytes, index + 12, 8).order(ByteOrder.BIG_ENDIAN).long
                } else if (version == 0 && index + 16 <= bytes.size) {
                    ByteBuffer.wrap(bytes, index + 12, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
                } else return null
                val unixSeconds = creation - 2_082_844_800L
                return runCatching {
                    RecordingTimestamp(
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds), ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "M4A:mvhd",
                    )
                }.getOrNull()
            }
            index++
        }
        return null
    }

    private fun decodeText(frame: ByteArray): String {
        if (frame.isEmpty()) return ""
        val charset = when (frame[0].toInt() and 0xff) {
            1 -> Charsets.UTF_16
            2 -> Charset.forName("UTF-16BE")
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return frame.copyOfRange(1, frame.size).toString(charset).trim('\u0000', ' ', '\r', '\n')
    }

    private fun normalizeDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("(\\d{4})[-:/]?(\\d{2})[-:/]?(\\d{2})(?:[T ](\\d{2})[:.]?(\\d{2})?(?:[:.]?(\\d{2}))?)?")
            .find(raw) ?: return null
        val (year, month, day, hour, minute, second) = match.destructured
        return runCatching {
            LocalDateTime.of(
                year.toInt(), month.toInt(), day.toInt(),
                hour.ifBlank { "0" }.toInt(), minute.ifBlank { "0" }.toInt(), second.ifBlank { "0" }.toInt(),
            ).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }.getOrNull()
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0x7f) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
            (bytes[offset + 3].toInt() and 0x7f)

    private fun InputStream.readExactlyOrNull(size: Int): ByteArray? {
        val result = ByteArray(size)
        var read = 0
        while (read < size) {
            val count = this.read(result, read, size - read)
            if (count < 0) return null
            read += count
        }
        return result
    }

    private fun InputStream.readAtMost(max: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (total < max) {
            val count = read(buffer, 0, minOf(buffer.size, max - total))
            if (count < 0) break
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }
}
