package com.sdinteractive.server

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Mp4DurationReaderTest {
    @Test
    fun `reads version zero movie header duration`() {
        val file = writeMp4(
            box("ftyp", byteArrayOf(0, 0, 0, 0)),
            box("moov", box("mvhd", versionZeroMovieHeader(timescale = 1_000, duration = 75_478)))
        )

        assertEquals(75_478L, Mp4DurationReader.readDurationMs(file))
    }

    @Test
    fun `reads version one movie header duration`() {
        val file = writeMp4(
            box(
                "moov",
                box("free", byteArrayOf(1, 2, 3, 4)),
                box("mvhd", versionOneMovieHeader(timescale = 90_000, duration = 27_809_370))
            )
        )

        assertEquals(308_993L, Mp4DurationReader.readDurationMs(file))
    }

    @Test
    fun `returns null for truncated movie header`() {
        val file = writeMp4(
            box("moov", box("mvhd", byteArrayOf(0, 0, 0, 0, 1, 2, 3)))
        )

        assertNull(Mp4DurationReader.readDurationMs(file))
    }

    @Test
    fun `returns null when movie header is absent`() {
        val file = writeMp4(box("ftyp", byteArrayOf(0, 0, 0, 0)))

        assertNull(Mp4DurationReader.readDurationMs(file))
    }

    private fun versionZeroMovieHeader(timescale: Int, duration: Int): ByteArray =
        ByteBuffer.allocate(20)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(timescale)
            .putInt(duration)
            .array()

    private fun versionOneMovieHeader(timescale: Int, duration: Long): ByteArray =
        ByteBuffer.allocate(32)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(1 shl 24)
            .putLong(0)
            .putLong(0)
            .putInt(timescale)
            .putLong(duration)
            .array()

    private fun box(type: String, vararg payloads: ByteArray): ByteArray {
        val payloadSize = payloads.sumOf(ByteArray::size)
        return ByteBuffer.allocate(8 + payloadSize)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(8 + payloadSize)
            .put(type.toByteArray(Charsets.US_ASCII))
            .apply { payloads.forEach(::put) }
            .array()
    }

    private fun writeMp4(vararg boxes: ByteArray): Path =
        Files.createTempFile("duration-reader", ".mp4").also { file ->
            Files.write(file, boxes.flatMap(ByteArray::asIterable).toByteArray())
            file.toFile().deleteOnExit()
        }
}
