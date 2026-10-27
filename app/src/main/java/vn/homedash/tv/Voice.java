package vn.homedash.tv;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Home Dash's spoken commands, from what Google's recogniser heard: "giá vàng", "thời tiết", "phát top 5",
 * "phát bài chờ chút", "bài tiếp theo", "mở Netflix"... Matched without accents, so "gia vang" works as well.
 */
final class Voice {
    enum Kind { GOLD, USD, FUEL, WEATHER, TIME, DATE, PLAY, PAUSE, NEXT, PREV, TOP, SONG, YOUTUBE, NETFLIX, STORE, APPS, UNKNOWN }

    static final class Command {
        final Kind kind;
        /** TOP: the chart position. */
        final int n;
        /** SONG: the name heard, without accents. */
        final String query;

        Command(Kind kind, int n, String query) {
            this.kind = kind;
            this.n = n;
            this.query = query;
        }
    }

    private static final String[] NUMBERS = {"khong", "mot", "hai", "ba", "bon", "nam", "sau", "bay", "tam", "chin", "muoi"};
    private static final Pattern TOP = Pattern.compile("\\b(?:top|hang|so|bai so|bai top)\\s*(\\d{1,3}|" + String.join("|", NUMBERS) + ")\\b");
    private static final Pattern SONG = Pattern.compile("^(?:phat|mo|nghe|bat|choi|cho nghe)\\s+(?:bai hat|bai|nhac|ca khuc)?\\s*(.+)$");
    /** Politeness and filler around a command: "giúp mình", "đi", "nhé"... */
    private static final Pattern FILLER = Pattern.compile(
            "\\b(?:ok google|hey google|xin|vui long|lam on|giup|dum|ho|cho minh|cho em|cho toi|cho anh|cho chi|minh|toi|em|anh|chi|vo|di|nao|nhe|nha|voi|a|ha|coi|xem|thu|hay|lai|duoc khong|duoc ko)\\b");

    private Voice() {
    }

    /** Lower case, accents dropped, đ as d, punctuation as spaces. */
    static String plain(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    static Command parse(String heard) {
        String s = plain(heard);
        String core = FILLER.matcher(" " + s + " ").replaceAll(" ").trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return of(Kind.UNKNOWN);

        if (has(s, "bai tiep", "bai sau", "qua bai", "chuyen bai", "doi bai", "tiep theo", "next", "bo qua")) return of(Kind.NEXT);
        if (has(s, "bai truoc", "quay lai bai", "previous")) return of(Kind.PREV);
        if (has(s, "tam dung", "dung nhac", "tat nhac", "ngung", "pause", "stop") || core.equals("dung")
                || core.equals("dung lai") || core.equals("im")) return of(Kind.PAUSE);

        if (has(s, "youtube")) return of(Kind.YOUTUBE);
        if (has(s, "netflix")) return of(Kind.NETFLIX);
        if (has(s, "cua hang", "play store", "cho play")) return of(Kind.STORE);
        if (has(s, "tat ca ung dung", "tat ca app", "danh sach ung dung", "cac ung dung")) return of(Kind.APPS);

        // "Mở nhạc" plays the song the card has ready; "phát tiếp" goes on from where "dừng nhạc" left it.
        if (has(s, "tiep tuc", "play", "phat tiep", "choi tiep", "nghe tiep", "mo tiep", "bat tiep", "phat lai nhac")
                || core.matches("(?:phat|mo|bat|nghe|choi)(?: nhac| bai hat| bai)?(?: top| thinh hanh| hot)*")
                || core.equals("nhac")) return of(Kind.PLAY);

        Matcher top = TOP.matcher(s);
        if (top.find() && (has(s, "top", "phat", "mo", "nghe", "bat", "bai so", "hang"))) {
            int n = number(top.group(1));
            if (n > 0) return new Command(Kind.TOP, n, null);
        }

        // Prices and the like before songs: "xem giá vàng" is not a song called "giá vàng".
        if (has(s, "vang", "sjc", "pnj")) return of(Kind.GOLD);
        if (has(s, "ty gia", "do la", "usd", "dola", "gia do", "do my", "ngoai te")) return of(Kind.USD);
        if (has(s, "xang", "dau diesel", "gia dau", "petrolimex")) return of(Kind.FUEL);
        if (has(s, "thoi tiet", "nhiet do", "may do", "troi mua", "co mua", "nang khong", "nong khong")) return of(Kind.WEATHER);
        if (has(s, "may gio", "bay gio la", "gio roi", "may h")) return of(Kind.TIME);
        if (has(s, "ngay may", "am lich", "hom nay la", "thu may", "ngay bao nhieu", "ngay gi", "lich")) return of(Kind.DATE);

        Matcher song = SONG.matcher(core);
        if (song.find()) {
            String q = song.group(1).trim();
            if (!q.isEmpty()) return new Command(Kind.SONG, 0, q);
        }
        return of(Kind.UNKNOWN);
    }

    /**
     * The chart song a spoken name means: its title heard in full ("phát bài chờ chút"), the title containing what was
     * heard, or the artist's name ("phát bài của Sơn Tùng" plays their highest-ranked song). Null if nothing fits.
     */
    static DashData.Track findSong(List<DashData.Track> chart, String query) {
        String q = plain(query).replaceFirst("^cua ", "");
        String qn = q.replace(" ", "");
        if (qn.length() < 2) return null;
        DashData.Track best = null;
        int bestScore = 0;
        for (DashData.Track t : chart) {
            String title = plain(t.title.replaceAll("[(\\[][^)\\]]*[)\\]]", " ")).replace(" ", "");
            String artists = plain(t.artists).replace(" ", "");
            int score = 0;
            if (!title.isEmpty() && (qn.equals(title))) score = 3000;
            else if (title.length() >= 3 && qn.contains(title)) score = 2000 + title.length();
            else if (qn.length() >= 3 && title.contains(qn)) score = 1500 + qn.length();
            else if (qn.length() >= 3 && artists.contains(qn)) score = 1000;
            // Higher in the chart wins a tie.
            if (score > 0) score = score * 1000 - t.position;
            if (score > bestScore) {
                best = t;
                bestScore = score;
            }
        }
        return best;
    }

    private static int number(String s) {
        for (int i = 0; i < NUMBERS.length; i++) if (NUMBERS[i].equals(s)) return i == 10 ? 10 : i;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean has(String s, String... words) {
        String padded = " " + s + " ";
        for (String w : words) if (padded.contains(" " + w + " ")) return true;
        return false;
    }

    private static Command of(Kind k) {
        return new Command(k, 0, null);
    }
}
