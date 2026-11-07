package app.parkingsms

import app.parkingsms.data.Session
import app.parkingsms.util.CsvExporter
import app.parkingsms.util.Formatters
import app.parkingsms.util.MessageTemplate
import app.parkingsms.util.PhoneNumbers
import app.parkingsms.util.TextCleaner
import app.parkingsms.util.TimerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanerTest {
    @Test
    fun plate_removesSpacesDashesAndUppercases() {
        assertEquals("BT1110AB", TextCleaner.cleanPlate("bt-1110 ab"))
        assertEquals("BT1110AB", TextCleaner.cleanPlate(" bt 1110-ab "))
    }

    @Test
    fun plate_removesSymbols() {
        assertEquals("AB123", TextCleaner.cleanPlate("a.b/1_2*3!"))
    }

    @Test
    fun plate_emptyStaysEmpty() {
        assertEquals("", TextCleaner.cleanPlate(" - - "))
    }

    @Test
    fun zone_uppercasedAndCleaned() {
        assertEquals("B9", TextCleaner.cleanZone("b 9"))
        assertEquals("A1", TextCleaner.cleanZone("a-1"))
    }

    @Test
    fun smsNumber_keepsDigitsAndLeadingPlus() {
        assertEquals("+38970123456", TextCleaner.cleanSmsNumber("+389 70-123 456"))
        assertEquals("1234", TextCleaner.cleanSmsNumber("12+34"))
    }
}

class MessageTemplateTest {
    @Test
    fun defaultStart_rendersZoneAndPlate() {
        assertEquals("B9 BT1110AB", MessageTemplate.render(MessageTemplate.DEFAULT_START, "B9", "BT1110AB"))
    }

    @Test
    fun stop_isJustS() {
        assertEquals("S", MessageTemplate.render(MessageTemplate.DEFAULT_STOP, "B9", "BT1110AB"))
    }

    @Test
    fun customTemplate_replacesAllPlaceholders() {
        assertEquals("BT1 B9 BT1", MessageTemplate.render("{plate} {zone} {plate}", "B9", "BT1"))
    }

    @Test
    fun blankTemplate_rendersEmpty() {
        assertEquals("", MessageTemplate.render("  ", "B9", "BT1"))
    }
}

class TimerMathTest {
    private val start = 1_000_000L

    @Test
    fun endTime_nullWithoutTimer() {
        assertNull(TimerMath.endTime(start, null))
        assertNull(TimerMath.endTime(start, 0))
    }

    @Test
    fun endTime_addsMinutes() {
        assertEquals(start + 30 * 60_000L, TimerMath.endTime(start, 30))
    }

    @Test
    fun reminder_isBeforeEnd() {
        assertEquals(start + 50 * 60_000L, TimerMath.reminderTime(start, 60, 10))
    }

    @Test
    fun reminder_nullWhenNotShorterThanTimer() {
        assertNull(TimerMath.reminderTime(start, 10, 10))
        assertNull(TimerMath.reminderTime(start, 10, 15))
        assertNull(TimerMath.reminderTime(start, null, 5))
        assertNull(TimerMath.reminderTime(start, 60, null))
    }

    @Test
    fun elapsedAndRemaining_neverNegative() {
        assertEquals(0L, TimerMath.elapsedMillis(start, start - 5))
        assertEquals(0L, TimerMath.remainingMillis(start, start + 5))
        assertEquals(5L, TimerMath.remainingMillis(start + 10, start + 5))
    }

    @Test
    fun graceAndSnooze() {
        assertEquals(start + 5 * 60_000L, TimerMath.graceDeadline(start, 5))
        assertEquals(start + 7 * 60_000L, TimerMath.snoozeTime(start, 7))
    }

    @Test
    fun clockFormat() {
        assertEquals("00:00:00", Formatters.clock(0))
        assertEquals("01:02:05", Formatters.clock(3_725_000L))
    }
}

class MiscTest {
    @Test
    fun phoneMatching() {
        assertTrue(PhoneNumbers.matches("1234", "1234"))
        assertTrue(PhoneNumbers.matches("38970123456", "+38970123456"))
        assertTrue(PhoneNumbers.matches("70123456", "+38970123456"))
        assertFalse(PhoneNumbers.matches("1234", "9999"))
        assertFalse(PhoneNumbers.matches("12", "12"))
    }

    @Test
    fun csv_escapesCommasAndQuotes() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
    }

    @Test
    fun csv_hasHeaderAndRow() {
        val s = Session(
            id = 1, cityId = 1, cityNameSnapshot = "Ohrid", smsNumberSnapshot = "1234",
            zoneCode = "B9", plateText = "BT1110AB", startedAt = 0L, endedAt = 60_000L, active = false
        )
        val lines = CsvExporter.toCsv(listOf(s)).trim().split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("Ohrid"))
        assertTrue(lines[1].contains(",60,"))
    }
}
