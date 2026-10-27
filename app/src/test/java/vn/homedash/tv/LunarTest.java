package vn.homedash.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class LunarTest {
    @Test
    public void screenshotDate() {
        Lunar l = Lunar.of(23, 10, 2026);
        assertEquals(14, l.day);
        assertEquals(9, l.month);
        assertEquals(2026, l.year);
        assertFalse(l.leap);
        assertEquals("Bính Ngọ", l.yearName());
        assertEquals("Mậu Tuất", l.monthName());
        assertEquals("Canh Ngọ", l.dayName());
    }

    @Test
    public void tetAtYearBoundary() {
        // Tết Bính Ngọ: 17/02/2026 is 1/1; the day before is 29/12 of Ất Tỵ.
        Lunar tet = Lunar.of(17, 2, 2026);
        assertEquals(1, tet.day);
        assertEquals(1, tet.month);
        assertEquals("Bính Ngọ", tet.yearName());
        Lunar eve = Lunar.of(16, 2, 2026);
        assertEquals(12, eve.month);
        assertEquals("Ất Tỵ", eve.yearName());
    }

    @Test
    public void leapMonth2025() {
        // 2025 has a leap 6th month starting 25/07/2025.
        Lunar l = Lunar.of(25, 7, 2025);
        assertEquals(1, l.day);
        assertEquals(6, l.month);
        assertEquals(true, l.leap);
    }
}
