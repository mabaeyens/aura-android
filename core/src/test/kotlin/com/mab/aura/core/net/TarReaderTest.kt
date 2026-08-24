package com.mab.aura.core.net

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Pins [TarReader] against tar archives built in-memory with the same Apache Commons Compress library.
 * There is no Swift test for the hand-rolled `TarReader.swift` to port, so these pin the ported behaviour:
 * every member file comes back as (name, bytes), directories are skipped, and a non-tar byte blob yields
 * an empty list rather than throwing.
 */
class TarReaderTest {

    private fun tarOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        TarArchiveOutputStream(bos).use { tos ->
            for ((name, body) in entries) {
                val entry = TarArchiveEntry(name)
                entry.size = body.size.toLong()
                tos.putArchiveEntry(entry)
                tos.write(body)
                tos.closeArchiveEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun files_returnsEveryMemberAsNameAndBytes() {
        val tar = tarOf(
            "Z_CAP_C_LEMM_001.xml" to "<alert/>".toByteArray(),
            "Z_CAP_C_LEMM_002.xml" to "<alert2/>".toByteArray(),
        )
        val files = TarReader.files(tar)
        assertEquals(2, files.size)
        assertEquals("Z_CAP_C_LEMM_001.xml", files[0].first)
        assertEquals("<alert/>", String(files[0].second, Charsets.UTF_8))
        assertEquals("<alert2/>", String(files[1].second, Charsets.UTF_8))
    }

    @Test
    fun files_skipsDirectories() {
        val bos = ByteArrayOutputStream()
        TarArchiveOutputStream(bos).use { tos ->
            tos.putArchiveEntry(TarArchiveEntry("subdir/"))   // trailing slash = directory entry
            tos.closeArchiveEntry()
            val body = "<alert/>".toByteArray()
            val file = TarArchiveEntry("subdir/a.xml").apply { size = body.size.toLong() }
            tos.putArchiveEntry(file)
            tos.write(body)
            tos.closeArchiveEntry()
        }
        val files = TarReader.files(bos.toByteArray())
        assertEquals(1, files.size)
        assertEquals("subdir/a.xml", files[0].first)
    }

    @Test
    fun files_emptyOnNonTarBytes() {
        assertTrue(TarReader.files("this is not a tar archive".toByteArray()).isEmpty())
        assertTrue(TarReader.files(ByteArray(0)).isEmpty())
    }
}
