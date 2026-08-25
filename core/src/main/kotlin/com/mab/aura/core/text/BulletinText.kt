package com.mab.aura.core.text

/**
 * Presentation helper for the AEMET narrative bulletin, ported from `BulletinText.swift`. AEMET writes
 * the "PREDICCIÓN" section as one run-on paragraph with hard column wraps baked in (a newline
 * mid-sentence, often with a leading space). Rendered verbatim it reads as a wall of ragged text,
 * "descenso en el\nresto". This flows it back out and breaks it into one line per sentence, since each
 * sentence is its own topic (sky, rain, max temps, min temps, wind), which is far easier to scan.
 */
object BulletinText {
    /** The bulletin as one line per sentence, with every hard wrap collapsed to a single space first. */
    fun sentences(text: String): List<String> {
        // Collapse ALL whitespace runs (spaces, tabs and AEMET's mid-sentence newlines) to a single
        // space, so the text flows regardless of how it was wrapped upstream.
        val flowed = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        // One line per sentence: a break after each ". " sentence boundary. AEMET prose has no
        // mid-sentence abbreviations and writes decimals with commas, so "." reliably ends a sentence.
        return flowed
            .replace(". ", ".\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
