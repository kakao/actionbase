package com.kakao.actionbase.core.metadata.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins what a date bucket writes down and how a range bound is read back, so that the arithmetic behind
 * both stays observable while it is refactored.
 */
class BucketTest {
    private val utc = ZoneId.of("UTC")

    private val day = Bucket.Date(name = "purchasedAt", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd")

    private val hour = Bucket.Date(name = "purchasedAt", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd HH")

    private val second = Bucket.Date(name = "purchasedAt", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd HH:mm:ss")

    @Test
    fun `a value is written down at the format's precision`() {
        val purchasedAt = Instant.parse("2026-01-01T14:32:07Z").toEpochMilli()

        assertEquals("2026-01-01", day.apply(purchasedAt))
        assertEquals("2026-01-01 14", hour.apply(purchasedAt))
    }

    @Test
    fun `a value that is not a time is dropped`() {
        assertNull(day.apply("not a time"))
        assertNull(day.apply(null))
    }

    @Test
    fun `a bound that names a fixed point is returned as it was written`() {
        assertEquals("2026-01-01", day.handleQueryValue("2026-01-01", ceil = true))
        assertEquals("2026-01-01", day.handleQueryValue("2026-01-01", ceil = false))
    }

    @Test
    fun `a bound that is not text is returned as it was written`() {
        assertEquals(42L, day.handleQueryValue(42L, ceil = true))
    }

    @Test
    fun `now is the bucket the clock is in`() {
        assertEquals(LocalDate.now(utc).toString(), day.handleQueryValue("now", ceil = false))
        assertEquals(LocalDate.now(utc).toString(), day.handleQueryValue("now", ceil = true))
    }

    @Test
    fun `a relative bound counts from the clock`() {
        assertEquals(LocalDate.now(utc).minusDays(1).toString(), day.handleQueryValue("now-1d", ceil = false))
        assertEquals(LocalDate.now(utc).plusDays(1).toString(), day.handleQueryValue("now+1d", ceil = false))
    }

    /** Raising the clock to the next bucket first is what keeps a truncated bound from widening the range. */
    @Test
    fun `ceil raises the clock to the next bucket before counting`() {
        assertEquals(LocalDate.now(utc).toString(), day.handleQueryValue("now-1d", ceil = true))

        val currentHour = LocalDateTime.now(utc).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH"))
        assertEquals(currentHour, hour.handleQueryValue("now-1h", ceil = true))
    }

    @Test
    fun `a bound is left alone when its unit is not supported`() {
        assertEquals("now-1y", day.handleQueryValue("now-1y", ceil = false))
        assertEquals("yesterday", day.handleQueryValue("yesterday", ceil = false))
    }

    @Test
    fun `a format finer than a bucket is refused when the clock has to be raised`() {
        assertThrows<IllegalArgumentException> { second.handleQueryValue("now-1h", ceil = true) }
    }
}
