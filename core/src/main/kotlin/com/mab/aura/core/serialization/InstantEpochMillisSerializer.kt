package com.mab.aura.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * Serializes a [java.time.Instant] as epoch milliseconds (a JSON number).
 *
 * Android-specific note (this has no Swift equivalent): in Swift these timestamps ride along for free
 * because `Date` is `Codable` out of the box. kotlinx.serialization has no built-in serializer for
 * `java.time.Instant`, so any `@Serializable` type that stores an instant — the first ones are
 * `UVHourSlot`, `WeatherAlert`, `AirQuality`/`AirComponent`, and soon `WeatherSnapshot` — needs one.
 *
 * The wire format is deliberately our own. Aura only ever reads back its *own* cached snapshots (the
 * Android app never decodes an iOS cache and vice versa), so the encoding just has to round-trip with
 * itself. Epoch millis is the most Android-idiomatic choice ([Instant.toEpochMilli]/[Instant.ofEpochMilli]),
 * a plain integer, and lossless to the millisecond — finer than any of these feeds report.
 *
 * Applied per field with `@Serializable(with = InstantEpochMillisSerializer::class)`.
 */
object InstantEpochMillisSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.ofEpochMilli(decoder.decodeLong())
}
