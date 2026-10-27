package vn.homedash.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.junit.Test;

public class VoiceTest {
    private static Voice.Kind kind(String s) {
        return Voice.parse(s).kind;
    }

    @Test
    public void prices() {
        assertEquals(Voice.Kind.GOLD, kind("Giá vàng"));
        assertEquals(Voice.Kind.GOLD, kind("xem giá vàng hôm nay giúp mình"));
        assertEquals(Voice.Kind.USD, kind("tỷ giá đô la"));
        assertEquals(Voice.Kind.USD, kind("giá USD"));
        assertEquals(Voice.Kind.FUEL, kind("giá xăng"));
        assertEquals(Voice.Kind.WEATHER, kind("thời tiết hôm nay"));
        assertEquals(Voice.Kind.TIME, kind("mấy giờ rồi"));
        assertEquals(Voice.Kind.DATE, kind("hôm nay ngày mấy âm lịch"));
    }

    @Test
    public void music() {
        // The four words for the card's player.
        assertEquals(Voice.Kind.PLAY, kind("Mở nhạc"));
        assertEquals(Voice.Kind.PAUSE, kind("Dừng nhạc"));
        assertEquals(Voice.Kind.PLAY, kind("Phát tiếp"));
        assertEquals(Voice.Kind.NEXT, kind("Qua bài"));
        // "Mở nhạc top N": the song at #N of this week's chart.
        for (int n = 1; n <= 10; n++) {
            Voice.Command c = Voice.parse("Mở nhạc top " + n);
            assertEquals(Voice.Kind.TOP, c.kind);
            assertEquals(n, c.n);
        }
        assertEquals(3, Voice.parse("mở nhạc tốp ba").n);
        assertEquals(10, Voice.parse("Mở nhạc top mười").n);
        assertEquals(Voice.Kind.PAUSE, kind("tạm dừng"));
        assertEquals(Voice.Kind.PAUSE, kind("dừng lại"));
        assertEquals(Voice.Kind.PLAY, kind("tiếp tục"));
        assertEquals(Voice.Kind.PLAY, kind("phát nhạc đi"));
        assertEquals(Voice.Kind.NEXT, kind("bài tiếp theo"));
        assertEquals(Voice.Kind.PREV, kind("bài trước"));
        Voice.Command top = Voice.parse("phát top 5");
        assertEquals(Voice.Kind.TOP, top.kind);
        assertEquals(5, top.n);
        assertEquals(1, Voice.parse("mở top một").n);
        Voice.Command song = Voice.parse("phát bài Chờ Chút");
        assertEquals(Voice.Kind.SONG, song.kind);
        assertEquals("cho chut", song.query);
        assertEquals(Voice.Kind.NETFLIX, kind("mở Netflix"));
        assertEquals(Voice.Kind.UNKNOWN, kind("xin chào"));
    }

    @Test
    public void findSong() {
        List<DashData.Track> chart = Arrays.asList(
                new DashData.Track("a", "Người Dưng", "Sơn Tùng M-TP", "", 1, 1),
                new DashData.Track("b", "chờ chút... (Remix)", "CONGB", "", 2, 3),
                new DashData.Track("c", "Người", "Sơn Tùng M-TP", "", 3, 2));
        assertEquals("b", Voice.findSong(chart, "cho chut").videoId);
        assertEquals("c", Voice.findSong(chart, "nguoi").videoId);
        assertEquals("a", Voice.findSong(chart, "nguoi dung").videoId);
        assertEquals("a", Voice.findSong(chart, "cua son tung").videoId);
        assertNull(Voice.findSong(chart, "khong co bai nay"));
    }

    @Test
    public void speech() {
        assertEquals("150 triệu 500 nghìn đồng", Speech.money(150_500_000));
        assertEquals("26 nghìn 345 đồng", Speech.money(26_345));
        assertEquals("tăng 300 nghìn đồng", Speech.change(300_000.0));
        assertEquals("giảm 150 đồng", Speech.change(-150.0));
        assertEquals("không đổi", Speech.change(0.0));
        assertEquals("Giá vàng hôm nay. Vàng miếng SJC bán ra 150 triệu 500 nghìn đồng một lượng, tăng 300 nghìn đồng. "
                        + "Nhẫn trơn bốn số chín bán ra 148 triệu đồng một lượng. ",
                Speech.gold("SJC\t150.5\t0.3\nN24K\t148.0\tNaN\n"));
        assertEquals("Tỷ giá đô la Mỹ tại Vietcombank. Mua vào 26 nghìn 100 đồng, không đổi. "
                + "Bán ra 26 nghìn 345 đồng, tăng 20 đồng. ", Speech.usd("26100.0\t26345.0\t0.0\t20.0"));
        assertEquals("Giá xăng dầu Petrolimex. Xăng E 10 20 nghìn 730 đồng một lít, giảm 150 đồng. ",
                Speech.fuel("Xăng E10 RON 95-III\t20730\t-150\t13/10\n"));
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.OCTOBER, 23, 9, 5);
        assertEquals("Bây giờ là 9 giờ 5 phút.", Speech.time(c));
        assertEquals("Hôm nay thứ Sáu, ngày 23 tháng 10 năm 2026, tức ngày 14 tháng 9 âm lịch, năm Bính Ngọ.", Speech.date(c));
    }
}
