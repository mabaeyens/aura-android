package com.mab.aura.core.net

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A namespace-unaware [DocumentBuilderFactory] hardened against XXE, built to work on BOTH the JVM (Xerces,
 * which `:core`'s unit tests run on) and Android's platform XML parser (which the app runs on). Shared by
 * [CAPParser] and the RSS parser in [NewsFeed], the two DOM consumers in `:core`.
 *
 * The Android gotcha this exists to absorb: Android's `DocumentBuilderFactory` does **not** recognise the
 * Apache feature URI `http://apache.org/xml/features/disallow-doctype-decl` and throws
 * `ParserConfigurationException` when you set it, whereas Xerces on the JVM accepts it. Setting that feature
 * unguarded aborts the *entire* parse on-device — confirmed on the emulator, where every RSS feed and every
 * CAP alert silently returned zero items while the JVM tests stayed green (the exception was swallowed by the
 * parser's own catch). So each hardening feature is applied defensively via [trySetFeature]: honoured where
 * the platform supports it, skipped where it doesn't.
 *
 * The protections that hold on both platforms — `FEATURE_SECURE_PROCESSING` and `isExpandEntityReferences =
 * false` — carry the core defence (no entity expansion, no external fetches). The DOCTYPE / external-entity
 * features harden further on the JVM; on Android they're no-ops, which is safe here: these feeds and CAP
 * files are trusted sources fetched over HTTPS and none carries a DOCTYPE, and Android's Expat-based parser
 * doesn't resolve external entities over the network by default.
 */
internal fun hardenedXmlFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        // Supported by Xerces on the JVM, unrecognised by Android's parser (which throws on the first) — each
        // is set defensively so an unsupported feature is skipped, never fatal.
        trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        trySetFeature("http://xml.org/sax/features/external-general-entities", false)
        trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }

/** Set an XML parser feature if the platform recognises it; silently skip it if not (see [hardenedXmlFactory]). */
private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
    try {
        setFeature(name, value)
    } catch (_: Exception) {
        // The running platform doesn't support this feature URI (Android rejects several Apache/SAX ones).
        // Safe to skip: isExpandEntityReferences = false + FEATURE_SECURE_PROCESSING already block the
        // entity-expansion attacks that matter, and the sources here are trusted HTTPS feeds with no DOCTYPE.
    }
}
