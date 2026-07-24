package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.types.PrimitiveType

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Bucket.Date::class, name = "date"),
)
sealed class Bucket {
    abstract val name: String

    abstract fun apply(value: Any?): Any?

    abstract fun handleQueryValue(
        value: Any,
        ceil: Boolean,
    ): Any

    data class Date(
        override val name: String,
        val unit: ValueUnit,
        val timezone: String,
        val format: String,
    ) : Bucket() {
        @JsonIgnore
        private val zoneId = ZoneId.of(timezone)

        @JsonIgnore
        private val formatter = DateTimeFormatter.ofPattern(format)

        override fun apply(value: Any?): Any? {
            if (value == null) return null

            return try {
                val longValue = PrimitiveType.LONG.cast(value) as Long

                val instant =
                    when (unit) {
                        ValueUnit.NANOSECOND -> Instant.ofEpochSecond(0, longValue)
                        ValueUnit.MICROSECOND -> Instant.ofEpochSecond(longValue / 1_000_000, (longValue % 1_000_000) * 1000)
                        ValueUnit.MILLISECOND -> Instant.ofEpochMilli(longValue)
                        ValueUnit.SECOND -> Instant.ofEpochSecond(longValue)
                    }

                instant.atZone(zoneId).format(formatter)
            } catch (_: Exception) {
                null
            }
        }

        override fun handleQueryValue(
            value: Any,
            ceil: Boolean,
        ): Any {
            if (value !is String) return value

            val input = value.trim()

            val instant =
                if (input == "now") {
                    Instant.now()
                } else {
                    val matcher = pattern.matcher(input)

                    if (!matcher.matches()) {
                        return value
                    }

                    val operator = matcher.group(1) // + or -
                    val amount = matcher.group(2).toLong() // number
                    val timeUnit = matcher.group(3) // m, h, d

                    val now = if (ceil) ceilToFormatPrecision(Instant.now()) else Instant.now()

                    val duration =
                        when (timeUnit) {
                            "m" -> Duration.ofMinutes(amount)
                            "h" -> Duration.ofHours(amount)
                            "d" -> Duration.ofDays(amount)
                            else -> throw IllegalArgumentException("Unsupported time unit: $timeUnit")
                        }

                    when (operator) {
                        "+" -> now.plus(duration)
                        "-" -> now.minus(duration)
                        else -> throw IllegalArgumentException("Unsupported operator: $operator")
                    }
                }

            return instant.atZone(zoneId).format(formatter)
        }

        private fun ceilToFormatPrecision(instant: Instant): Instant {
            val zoned = instant.atZone(zoneId)

            return when {
                // Includes nanoseconds/microseconds (S, SSS, SSSSSS, SSSSSSSSS, etc.)
                format.contains('S') ->
                    throw IllegalArgumentException("Units below milliseconds are not supported: $format")

                // Up to seconds only (includes ss, no S)
                format.contains("ss") ->
                    throw IllegalArgumentException("Second units are not supported: $format")

                // Up to minutes only (includes mm, no ss)
                format.contains("mm") -> {
                    val truncated = zoned.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
                    if (truncated == zoned) {
                        instant
                    } else {
                        truncated.plusMinutes(1).toInstant()
                    }
                }

                // Up to hours only (includes HH, no mm)
                format.contains("HH") || format.contains("H") -> {
                    val truncated = zoned.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                    if (truncated == zoned) {
                        instant
                    } else {
                        truncated.plusHours(1).toInstant()
                    }
                }

                // Day units only (date only)
                else -> {
                    val truncated = zoned.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    if (truncated == zoned) {
                        instant
                    } else {
                        truncated.plusDays(1).toInstant()
                    }
                }
            }
        }

        companion object {
            private val pattern = Pattern.compile("^now([+-])(\\d+)([mhd])$")
        }
    }

    enum class ValueUnit {
        NANOSECOND,
        MICROSECOND,
        MILLISECOND,
        SECOND,
    }
}
