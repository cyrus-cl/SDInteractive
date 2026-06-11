package com.sdinteractive.server

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal object Mp4DurationReader {
    fun readDurationMs(path: Path): Long? = runCatching {
        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            findMovieHeader(channel, start = 0L, endExclusive = channel.size())
        }
    }.getOrNull()

    private fun findMovieHeader(
        channel: FileChannel,
        start: Long,
        endExclusive: Long
    ): Long? {
        var offset = start
        while (offset < endExclusive) {
            val box = readBox(channel, offset, endExclusive) ?: return null
            when (box.type) {
                "mvhd" -> return readMovieHeaderDurationMs(channel, box)
                "moov" -> {
                    val duration = findMovieHeader(channel, box.dataStart, box.endExclusive)
                    if (duration != null) return duration
                }
            }
            offset = box.endExclusive
        }
        return null
    }

    private fun readBox(
        channel: FileChannel,
        offset: Long,
        parentEndExclusive: Long
    ): Box? {
        if (parentEndExclusive - offset < STANDARD_HEADER_BYTES) return null
        val header = readBytes(channel, offset, STANDARD_HEADER_BYTES) ?: return null
        val declaredSize = Integer.toUnsignedLong(header.int)
        val typeBytes = ByteArray(4)
        header.get(typeBytes)
        val type = typeBytes.toString(Charsets.US_ASCII)

        val headerSize: Long
        val boxSize: Long
        when (declaredSize) {
            0L -> {
                headerSize = STANDARD_HEADER_BYTES.toLong()
                boxSize = parentEndExclusive - offset
            }

            1L -> {
                val extended = readBytes(
                    channel,
                    offset + STANDARD_HEADER_BYTES,
                    EXTENDED_SIZE_BYTES
                ) ?: return null
                val extendedSize = extended.long
                if (extendedSize <= 0L) return null
                headerSize = EXTENDED_HEADER_BYTES.toLong()
                boxSize = extendedSize
            }

            else -> {
                headerSize = STANDARD_HEADER_BYTES.toLong()
                boxSize = declaredSize
            }
        }

        if (boxSize < headerSize || boxSize > parentEndExclusive - offset) return null
        return Box(
            type = type,
            dataStart = offset + headerSize,
            endExclusive = offset + boxSize
        )
    }

    private fun readMovieHeaderDurationMs(
        channel: FileChannel,
        box: Box
    ): Long? {
        if (box.endExclusive - box.dataStart < VERSION_AND_FLAGS_BYTES) return null
        val versionBuffer = readBytes(channel, box.dataStart, VERSION_AND_FLAGS_BYTES) ?: return null
        val version = versionBuffer.get(0).toInt() and 0xff
        val fieldsOffset = when (version) {
            0 -> VERSION_ZERO_FIELDS_OFFSET
            1 -> VERSION_ONE_FIELDS_OFFSET
            else -> return null
        }
        val fieldsSize = when (version) {
            0 -> VERSION_ZERO_FIELDS_BYTES
            else -> VERSION_ONE_FIELDS_BYTES
        }
        if (box.endExclusive - box.dataStart < fieldsOffset + fieldsSize) return null

        val fields = readBytes(channel, box.dataStart + fieldsOffset, fieldsSize) ?: return null
        val timescale = Integer.toUnsignedLong(fields.int)
        if (timescale == 0L) return null
        val duration = if (version == 0) {
            BigInteger.valueOf(Integer.toUnsignedLong(fields.int))
        } else {
            val bytes = ByteArray(Long.SIZE_BYTES)
            fields.get(bytes)
            BigInteger(1, bytes)
        }
        if (duration.signum() <= 0) return null

        val durationMs = duration
            .multiply(THOUSAND)
            .divide(BigInteger.valueOf(timescale))
        return durationMs
            .takeIf { it.signum() > 0 && it <= LONG_MAX }
            ?.toLong()
    }

    private fun readBytes(
        channel: FileChannel,
        offset: Long,
        byteCount: Int
    ): ByteBuffer? {
        if (offset < 0L || byteCount < 0 || offset > channel.size() - byteCount) return null
        val buffer = ByteBuffer.allocate(byteCount).order(ByteOrder.BIG_ENDIAN)
        var position = offset
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, position)
            if (read <= 0) return null
            position += read
        }
        return buffer.flip()
    }

    private data class Box(
        val type: String,
        val dataStart: Long,
        val endExclusive: Long
    )

    private const val STANDARD_HEADER_BYTES = 8
    private const val EXTENDED_SIZE_BYTES = 8
    private const val EXTENDED_HEADER_BYTES = 16
    private const val VERSION_AND_FLAGS_BYTES = 4
    private const val VERSION_ZERO_FIELDS_OFFSET = 12L
    private const val VERSION_ONE_FIELDS_OFFSET = 20L
    private const val VERSION_ZERO_FIELDS_BYTES = 8
    private const val VERSION_ONE_FIELDS_BYTES = 12
    private val THOUSAND = BigInteger.valueOf(1_000L)
    private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
}
