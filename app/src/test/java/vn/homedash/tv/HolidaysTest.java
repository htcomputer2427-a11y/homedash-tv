package vn.homedash.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Test;

public class HolidaysTest {
    private static Holidays.Day on(int d, int m, int y) {
        Calendar next = Calendar.getInstance();
        next.set(y, m - 1, d);
        next.add(Calendar.DAY_OF_MONTH, 1);
        return Holidays.of(d, m, Lunar.of(d, m, y), Lunar.of(next.get(Calendar.DAY_OF_MONTH),
                next.get(Calendar.MONTH) + 1, next.get(Calendar.YEAR)));
    }

    @Test
    public void tet2026() {
        Holidays.Day eve = on(16, 2, 2026);
        assertEquals("Giao thừa", eve.name);
        assertTrue(eve.dayOff);
        assertEquals("Mùng 1 Tết", on(17, 2, 2026).name);
        assertEquals("Mùng 3 Tết", on(19, 2, 2026).name);
        assertNull(on(20, 2, 2026));
    }

    @Test
    public void solarDays() {
        assertTrue(on(2, 9, 2026).dayOff);
        assertEquals("Quốc khánh", on(2, 9, 2026).name);
        assertEquals("Giải phóng miền Nam", on(30, 4, 2026).name);
        Holidays.Day women = on(20, 10, 2026);
        assertEquals("Phụ nữ Việt Nam", women.name);
        assertFalse(women.dayOff);
    }

    @Test
    public void lunarDays() {
        // Trung Thu 2026 (15/8 lunar) is 25/09/2026; Giỗ Tổ (10/3 lunar) is 26/04/2026.
        assertEquals("Tết Trung Thu", on(25, 9, 2026).name);
        assertEquals("Giỗ Tổ Hùng Vương", on(26, 4, 2026).name);
        assertTrue(on(26, 4, 2026).dayOff);
    }

    @Test
    public void lunarTag() {
        assertEquals("RẰM", Holidays.lunarTag(Lunar.of(24, 10, 2026)));
        assertEquals("MÙNG 1", Holidays.lunarTag(Lunar.of(17, 2, 2026)));
        assertNull(Holidays.lunarTag(Lunar.of(23, 10, 2026)));
        assertNull(on(23, 10, 2026));
    }
}
