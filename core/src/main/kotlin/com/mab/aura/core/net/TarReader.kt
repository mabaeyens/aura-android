package com.mab.aura.core.net

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Pulls the member files out of AEMET's avisos payload, which is a plain (non-gzipped) `.tar` of CAP-XML
 * files. Neither the JDK nor Android ships a tar reader, so this wraps Apache Commons Compress — the
 * standard JVM library for the format.
 *
 * Replaces the hand-rolled `TarReader` in `TarReader.swift`: Foundation has no tar reader either, so the
 * Swift side parses the 512-byte ustar headers by hand; on Android the library is the documented, tested
 * path. Same shape and same leniency — a malformed archive yields the whole entries read so far rather
 * than throwing, so one corrupt tail can't lose every alert.
 */
object TarReader {
    /** Each member file as (name, bytes). Directories are skipped. */
    fun files(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val out = ArrayList<Pair<String, ByteArray>>()
        try {
            TarArchiveInputStream(ByteArrayInputStream(bytes)).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    // TarArchiveInputStream bounds read() to the current entry (returns -1 at its end), so
                    // readBytes() here reads exactly this file's body before nextEntry advances the stream.
                    if (!entry.isDirectory) out.add(entry.name to tar.readBytes())
                    entry = tar.nextEntry
                }
            }
        } catch (_: IOException) {
            // Malformed archive: keep the whole entries already read (the Swift reader is likewise lenient).
        }
        return out
    }
}
