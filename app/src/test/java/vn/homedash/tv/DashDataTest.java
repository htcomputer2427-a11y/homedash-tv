package vn.homedash.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

/** Samples cut from the real responses on 2026-10-24. */
public class DashDataTest {
    @Test
    public void gold() {
        String json = "{\"data\":[{\"masp\":\"SJC\",\"tensp\":\"Vàng miếng SJC 999.9\",\"giaban\":14200,\"giamua\":13900},"
                + "{\"masp\":\"N24K\",\"tensp\":\"Nhẫn Trơn PNJ 999.9\",\"giaban\":14100,\"giamua\":13800},"
                + "{\"masp\":\"61\",\"tensp\":\"Vàng 610 (14.6K)\",\"giaban\":8479,\"giamua\":7489}]}";
        Map<String, Double> g = DashData.parseGold(json);
        assertEquals(142.0, g.get("SJC"), 1e-9);
        assertEquals(141.0, g.get("N24K"), 1e-9);
        assertEquals(84.79, g.get("61"), 1e-9);
    }

    @Test
    public void usd() {
        String xml = "<ExrateList><Exrate CurrencyCode=\"AUD\" Buy=\"16,000.00\" Transfer=\"16,100.00\" Sell=\"16,600.00\" />"
                + "<Exrate CurrencyCode=\"USD\" CurrencyName=\"US DOLLAR           \" Buy=\"25,610.00\" Transfer=\"25,640.00\" Sell=\"26,020.00\" />";
        double[] r = DashData.parseUsd(xml);
        assertNotNull(r);
        assertEquals(25610, r[0], 1e-9);
        assertEquals(26020, r[1], 1e-9);
        assertNull(DashData.parseUsd("<ExrateList/>"));
    }

    @Test
    public void fuel() {
        String html = "<table><tr><th>Mặt hàng</th><th>Vùng 1</th><th>+/- Vùng 1</th><th>Vùng 2</th><th>+/- Vùng 2</th><th>Hiệu lực từ</th></tr>"
                + "<tr>\n<th scope=\"row\"><span class=\"wg-name\"><span class=\"wg-name__text\"><span>Dầu diesel 0,05S-II</span></span></span></th>"
                + "<td class=\"\">\n 29.250 </td><td class=\"wg-up\"> ▲ 450 </td><td>29.830</td><td>▲ 460</td><td class=\"wg-meta\">22/10/2026</td></tr>"
                + "<tr><th><span>Xăng E10 RON 95-III</span></th><td>24.910</td><td class=\"wg-down\">▼ 1.810</td><td>25.400</td><td>▼ 1.850</td><td>22/10/2026</td></tr>"
                + "<tr><th><span>Xăng E5 RON 92-II</span></th><td>25.060</td><td>0</td><td>25.560</td><td>0</td><td>22/10/2026</td></tr></table>";
        Map<String, DashData.Fuel> f = DashData.parseFuel(html);
        assertEquals(29250, f.get("Dầu diesel 0,05S-II").price, 1e-9);
        assertEquals(450, f.get("Dầu diesel 0,05S-II").change, 1e-9);
        assertEquals(24910, f.get("Xăng E10 RON 95-III").price, 1e-9);
        assertEquals(-1810, f.get("Xăng E10 RON 95-III").change, 1e-9);
        assertEquals(0, f.get("Xăng E5 RON 92-II").change, 1e-9);
        assertEquals("22/10/2026", f.get("Xăng E5 RON 92-II").since);
    }

    @Test
    public void chart() {
        String json = "{\"contents\":{\"sectionListRenderer\":{\"contents\":[{\"musicAnalyticsSectionRenderer\":{\"content\":"
                + "{\"trackTypes\":[{\"trackViews\":["
                + "{\"id\":\"G:qcDJ2xbSRV4\",\"name\":\"chờ chút...\",\"thumbnail\":{\"thumbnails\":[{\"url\":"
                + "\"https://yt3.googleusercontent.com/abc=w180-h180-l90-rj\",\"width\":180}]},\"encryptedVideoId\":\"eedlpxUw8zg\","
                + "\"chartEntryMetadata\":{\"currentPosition\":1,\"previousPosition\":9},"
                + "\"artists\":[{\"name\":\"TINH HÀ \\\"SAY HI\\\"\"},{\"name\":\"CONGB\"}]},"
                + "{\"name\":\"LƯU NIÊN\",\"encryptedVideoId\":\"boKJ5XDs_mY\",\"chartEntryMetadata\":{\"currentPosition\":4},"
                + "\"artists\":[{\"name\":\"Jack - J97\"}]}"
                + "]}]}}}]}}}";
        java.util.List<DashData.Track> t = DashData.parseChart(json, 30);
        assertEquals(2, t.size());
        assertEquals("eedlpxUw8zg", t.get(0).videoId);
        assertEquals("chờ chút...", t.get(0).title);
        assertEquals("TINH HÀ \"SAY HI\", CONGB", t.get(0).artists);
        assertEquals(9, t.get(0).previous);
        assertEquals("https://yt3.googleusercontent.com/abc=w544-h544-l90-rj", t.get(0).largeThumb());
        assertEquals(0, t.get(1).previous);
        java.util.List<DashData.Track> back = DashData.decodeChart(DashData.encodeChart(t));
        assertEquals(4, back.get(1).position);
        assertEquals("Jack - J97", back.get(1).artists);
    }

    @Test
    public void matchPlayingTitle() {
        java.util.List<DashData.Track> chart = java.util.Arrays.asList(
                new DashData.Track("a", "chờ chút...", "CONGB", "", 1, 9),
                new DashData.Track("b", "NGƯỜI DƯNG", "Jack - J97", "", 10, 12),
                new DashData.Track("c", "Người", "X", "", 20, 0));
        assertEquals("a", DashData.match(chart, "CHỜ CHÚT (Official MV)").videoId);
        // The longer title wins over a shorter one it contains.
        assertEquals("b", DashData.match(chart, "Người Dưng | Jack - J97 | Lyrics").videoId);
        assertNull(DashData.match(chart, "Một bài khác hẳn"));
    }

    @Test
    public void matchRealYoutubeTitles() {
        // Titles YouTube reported on the TV for chart #1 and #2 on 2026-10-24.
        java.util.List<DashData.Track> chart = java.util.Arrays.asList(
                new DashData.Track("eedlpxUw8zg", "chờ chút...", "TINH HÀ \"SAY HI\", CONGB", "", 1, 9),
                new DashData.Track("WnTSrvALsXs", "Không Môn Đăng Hộ Đối (TD Remix)", "Nguyễn Vĩ", "", 2, 25));
        assertEquals("eedlpxUw8zg",
                DashData.match(chart, "CONGB - chờ chút... | TINH HÀ SAY HI [PERFORMANCE]").videoId);
        assertEquals("WnTSrvALsXs",
                DashData.match(chart, "KHÔNG MÔN ĐĂNG HỘ ĐỐI REMIX HOT TIKTOK - NGUYỄN VĨ X QZ REMIX").videoId);
    }

    @Test
    public void matchPrefersThePlayingList() {
        // #4's video title names its album, which is also #78 on the chart (seen on the TV, 2026-10-24).
        java.util.List<DashData.Track> chart = java.util.Arrays.asList(
                new DashData.Track("boKJ5XDs_mY", "LƯU NIÊN", "Jack - J97", "", 4, 2),
                new DashData.Track("bL5IuLDBuDQ", "TAM THÁI TỬ", "Jack - J97", "", 78, 70));
        String title = "JACK - J97 | LƯU NIÊN | Album TAM THÁI TỬ - Track No.4";
        java.util.Set<String> playing = new java.util.HashSet<>(java.util.Arrays.asList("boKJ5XDs_mY", "XqLsUne19Gs"));
        assertEquals("boKJ5XDs_mY", DashData.match(chart, title, playing).videoId);
    }

    @Test
    public void goldFromWebgia() {
        String html = "<table><tr><th>Sản phẩm</th><th>Mua vào</th><th>Bán ra</th><th>Thay đổi</th><th>Đổi lúc</th></tr>"
                + "<tr><td>Vàng Kim Bảo 999.9</td><td>138.000</td><td>141.000</td><td>±0</td><td>16 giờ trước</td></tr>"
                + "<tr><td><span>Vàng miếng SJC 999.9</span></td><td>139.000</td><td>142.000</td><td class=\"wg-up\">▲ 1.200</td><td>2 giờ trước</td></tr>"
                + "<tr><td>Nhẫn Trơn PNJ 999.9</td><td>138.000</td><td>141.000</td><td class=\"wg-down\">▼ 500</td><td>2 giờ trước</td></tr>"
                + "<tr><td>Vàng 610 (14.6K)</td><td>74.890</td><td>84.790</td><td>±0</td><td>16 giờ trước</td></tr></table>";
        Map<String, double[]> g = DashData.parseGoldWeb(html);
        assertEquals(142.0, g.get("SJC")[0], 1e-9);
        assertEquals(1.2, g.get("SJC")[1], 1e-9);
        assertEquals(141.0, g.get("N24K")[0], 1e-9);
        assertEquals(-0.5, g.get("N24K")[1], 1e-9);
        assertEquals(84.79, g.get("61")[0], 1e-9);
        assertEquals(0, g.get("61")[1], 1e-9);
    }

    @Test
    public void usdFromWebgia() {
        String html = "<table><tr><th>Ngoại tệ</th><th>Mua TM</th><th>Mua CK</th><th>Bán CK</th><th>Bán TM</th><th>± Bán CK</th></tr>"
                + "<tr><td>JPY Yên Nhật</td><td>158,33</td><td>159,93</td><td>169,24</td><td>-</td><td>±0</td></tr>"
                + "<tr><td><b>USD</b> Đô la Mỹ</td><td>25.610</td><td>25.640</td><td>26.020</td><td>-</td><td>▼ 20</td></tr></table>";
        double[] r = DashData.parseUsdWeb(html);
        assertNotNull(r);
        assertEquals(25610, r[0], 1e-9);
        assertEquals(26020, r[1], 1e-9);
        assertEquals(-20, r[2], 1e-9);
    }

    @Test
    public void signedChanges() {
        assertEquals(450, DashData.signed("▲ 450"), 1e-9);
        assertEquals(-1810, DashData.signed("▼ 1.810"), 1e-9);
        assertEquals(500, DashData.signed("+500"), 1e-9);
        assertEquals(-1000, DashData.signed("-1.000"), 1e-9);
        assertEquals(0, DashData.signed("±0"), 1e-9);
        assertNull(DashData.signed("-"));
    }

    @Test
    public void temperatureColours() {
        assertEquals(0xFFC4E8FF, DashData.tempColor(10));
        assertEquals(0xFF8FD3FF, DashData.tempColor(20));
        assertEquals(0xFFFFB35C, DashData.tempColor(30));
        assertEquals(0xFFFF5A3C, DashData.tempColor(40));
        // Halfway between 30 (0xFFB35C) and 33 (0xFF8A4C).
        assertEquals(0xFFFF9F54, DashData.tempColor(31.5));
        // Warmer is redder: green falls from 27 degrees up.
        int prevGreen = 256;
        for (double t = 27; t <= 36; t += 0.5) {
            int g = (DashData.tempColor(t) >> 8) & 0xFF;
            org.junit.Assert.assertTrue(t + "°", g <= prevGreen);
            prevGreen = g;
        }
    }

    @Test
    public void weatherIcons() {
        assertEquals(R.drawable.ic_w_sun, DashData.weatherIcon(0, true));
        assertEquals(R.drawable.ic_w_moon, DashData.weatherIcon(0, false));
        assertEquals(R.drawable.ic_w_partly, DashData.weatherIcon(2, true));
        // A cloudy night shows the moon behind a cloud, not the bare moon.
        assertEquals(R.drawable.ic_w_partly_night, DashData.weatherIcon(1, false));
        assertEquals(R.drawable.ic_w_partly_night, DashData.weatherIcon(2, false));
        assertEquals(R.drawable.ic_w_cloud, DashData.weatherIcon(3, false));
        assertEquals(R.drawable.ic_w_rain, DashData.weatherIcon(63, true));
        assertEquals(R.drawable.ic_w_storm, DashData.weatherIcon(95, false));
    }

    @Test
    public void weather() {
        String json = "{\"current_units\":{\"time\":\"iso8601\",\"temperature_2m\":\"°C\",\"weather_code\":\"wmo code\"},"
                + "\"current\":{\"time\":\"2026-10-24T15:30\",\"interval\":900,\"temperature_2m\":31.4,\"weather_code\":1,\"is_day\":1}}";
        double[] w = DashData.parseWeather(json);
        assertNotNull(w);
        assertEquals(31.4, w[0], 1e-9);
        assertEquals(1, (int) w[1]);
        assertEquals("Trời nắng nhẹ", DashData.weatherText((int) w[1], w[2] > 0));
    }
}
