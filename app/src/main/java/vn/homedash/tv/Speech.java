package vn.homedash.tv;

import java.util.Calendar;
import java.util.Locale;

/**
 * What Home Dash reads aloud, built from the same stored lines the cards show. Amounts are spelled the way people say
 * them ("150 triệu 500 nghìn đồng"), since the Vietnamese voice reads "150,5" as "một trăm năm mươi phẩy năm".
 */
final class Speech {
    private static final String[] WEEKDAYS = {"Chủ nhật", "thứ Hai", "thứ Ba", "thứ Tư", "thứ Năm", "thứ Sáu", "thứ Bảy"};

    private Speech() {
    }

    /** 150500000 → "150 triệu 500 nghìn đồng", 26345 → "26 nghìn 345 đồng". */
    static String money(double vnd) {
        long v = Math.round(Math.abs(vnd));
        if (v == 0) return "0 đồng";
        StringBuilder sb = new StringBuilder();
        long[] parts = {v / 1_000_000_000L, v / 1_000_000 % 1000, v / 1000 % 1000, v % 1000};
        String[] units = {" tỷ", " triệu", " nghìn", ""};
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts[i]).append(units[i]);
        }
        return sb.append(" đồng").toString();
    }

    /** "tăng 300 nghìn đồng", "giảm 150 đồng", "không đổi"; empty when the change is not known. */
    static String change(Double vnd) {
        if (vnd == null || Double.isNaN(vnd)) return "";
        if (Math.abs(vnd) < 0.5) return "không đổi";
        return (vnd > 0 ? "tăng " : "giảm ") + money(vnd);
    }

    /** Lines "code\tsell\tchange", million VND per lượng. */
    static String gold(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : new String[][]{{"SJC", "Vàng miếng SJC"}, {"N24K", "Nhẫn trơn bốn số chín"}, {"61", "Vàng 610"}}) {
            String[] p = line(raw, row[0]);
            Double sell = p == null ? null : number(p, 1);
            if (sell == null) continue;
            Double c = number(p, 2);
            sb.append(row[1]).append(" bán ra ").append(money(sell * 1_000_000)).append(" một lượng");
            appendChange(sb, c == null ? null : c * 1_000_000);
        }
        if (sb.length() == 0) return "Chưa có giá vàng, mình đang cập nhật.";
        return "Giá vàng hôm nay. " + sb;
    }

    /** "buy\tsell\tbuyChange\tsellChange", VND. */
    static String usd(String raw) {
        String[] p = raw == null ? new String[0] : raw.split("\t");
        Double buy = number(p, 0);
        Double sell = number(p, 1);
        if (buy == null || sell == null) return "Chưa có tỷ giá đô la, mình đang cập nhật.";
        StringBuilder sb = new StringBuilder("Tỷ giá đô la Mỹ tại Vietcombank. Mua vào ").append(money(buy));
        appendChange(sb, number(p, 2));
        sb.append("Bán ra ").append(money(sell));
        appendChange(sb, number(p, 3));
        return sb.toString();
    }

    /** Lines "product\tprice\tchange\tsince", VND per litre. */
    static String fuel(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : new String[][]{{"Xăng E10 RON 95-III", "Xăng E 10"}, {"Xăng E5 RON 92-II", "Xăng E 5"},
                {"Dầu diesel 0,05S-II", "Dầu D O"}}) {
            String[] p = line(raw, row[0]);
            Double price = p == null ? null : number(p, 1);
            if (price == null) continue;
            sb.append(row[1]).append(' ').append(money(price)).append(" một lít");
            appendChange(sb, number(p, 2));
        }
        if (sb.length() == 0) return "Chưa có giá xăng dầu, mình đang cập nhật.";
        return "Giá xăng dầu Petrolimex. " + sb;
    }

    static String weather(String city, double[] w) {
        if (w == null) return "Chưa có thời tiết, mình đang cập nhật.";
        String sky = DashData.weatherText((int) w[1], w[2] > 0);
        return city + " hiện " + Math.round(w[0]) + " độ" + (sky.equals("—") ? "." : ", " + sky.toLowerCase(Locale.ROOT) + ".");
    }

    static String time(Calendar c) {
        int m = c.get(Calendar.MINUTE);
        return "Bây giờ là " + c.get(Calendar.HOUR_OF_DAY) + " giờ" + (m == 0 ? " đúng." : " " + m + " phút.");
    }

    static String date(Calendar c) {
        int d = c.get(Calendar.DAY_OF_MONTH);
        int mo = c.get(Calendar.MONTH) + 1;
        int y = c.get(Calendar.YEAR);
        Lunar l = Lunar.of(d, mo, y);
        return "Hôm nay " + WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", ngày " + d + " tháng " + mo + " năm " + y
                + ", tức ngày " + l.day + " tháng " + (l.leap ? "nhuận " : "") + l.month + " âm lịch, năm " + l.yearName() + ".";
    }

    private static void appendChange(StringBuilder sb, Double vnd) {
        String c = change(vnd);
        sb.append(c.isEmpty() ? ". " : ", " + c + ". ");
    }

    private static String[] line(String raw, String key) {
        if (raw == null) return null;
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t");
            if (p.length >= 2 && p[0].equals(key)) return p;
        }
        return null;
    }

    private static Double number(String[] p, int i) {
        if (i >= p.length) return null;
        try {
            double d = Double.parseDouble(p[i]);
            return Double.isNaN(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
