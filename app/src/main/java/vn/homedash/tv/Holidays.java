package vn.homedash.tv;

/** Vietnamese public holidays and well-known days, solar and lunar, for the calendar card. */
final class Holidays {
    /** A named day; dayOff for the public holidays the labour code gives off. */
    static final class Day {
        final String name;
        final boolean dayOff;

        Day(String name, boolean dayOff) {
            this.name = name;
            this.dayOff = dayOff;
        }
    }

    private Holidays() {
    }

    /**
     * The holiday on this solar date, or null. lunar is the date's lunar day, tomorrow the next day's (for
     * Giao thừa, the last day of the lunar year, which is the 29th or the 30th).
     */
    static Day of(int d, int m, Lunar lunar, Lunar tomorrow) {
        if (!lunar.leap) {
            int ld = lunar.day;
            int lm = lunar.month;
            if (lm == 1 && ld <= 3) return new Day("Mùng " + ld + " Tết", true);
            if (tomorrow.month == 1 && tomorrow.day == 1 && !tomorrow.leap) return new Day("Giao thừa", true);
            if (lm == 3 && ld == 10) return new Day("Giỗ Tổ Hùng Vương", true);
            if (lm == 1 && ld == 15) return new Day("Rằm tháng Giêng", false);
            if (lm == 4 && ld == 15) return new Day("Lễ Phật Đản", false);
            if (lm == 5 && ld == 5) return new Day("Tết Đoan Ngọ", false);
            if (lm == 7 && ld == 15) return new Day("Lễ Vu Lan", false);
            if (lm == 8 && ld == 15) return new Day("Tết Trung Thu", false);
            if (lm == 12 && ld == 23) return new Day("Ông Công Ông Táo", false);
        }
        switch (m * 100 + d) {
            case 101:
                return new Day("Tết Dương lịch", true);
            case 308:
                return new Day("Quốc tế Phụ nữ", false);
            case 430:
                return new Day("Giải phóng miền Nam", true);
            case 501:
                return new Day("Quốc tế Lao động", true);
            case 601:
                return new Day("Quốc tế Thiếu nhi", false);
            case 902:
                return new Day("Quốc khánh", true);
            case 1020:
                return new Day("Phụ nữ Việt Nam", false);
            case 1120:
                return new Day("Nhà giáo Việt Nam", false);
            case 1224:
                return new Day("Đêm Giáng sinh", false);
            case 1225:
                return new Day("Giáng sinh", false);
            default:
                return null;
        }
    }

    /** "MÙNG 1" or "RẰM" for the first and fifteenth of a lunar month, else null. */
    static String lunarTag(Lunar lunar) {
        if (lunar.day == 1) return "MÙNG 1";
        if (lunar.day == 15) return "RẰM";
        return null;
    }
}
