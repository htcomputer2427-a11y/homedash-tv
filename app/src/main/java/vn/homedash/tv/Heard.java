package vn.homedash.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadata;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chart songs played in the last week, so the next trending list skips them. Kept as "id:time" pairs in one
 * preference, shared by DashboardActivity and MediaListener (which sees the songs while YouTube is on screen).
 */
final class Heard {
    static final long KEEP_MS = 7 * 24 * 3600_000L;
    private static final String KEY = "heard";

    private Heard() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dash", Context.MODE_PRIVATE);
    }

    static Set<String> ids(Context c) {
        Set<String> out = new HashSet<>();
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        for (String e : prefs(c).getString(KEY, "").split(",")) {
            int colon = e.indexOf(':');
            if (colon <= 0) continue;
            try {
                if (Long.parseLong(e.substring(colon + 1)) >= cutoff) out.add(e.substring(0, colon));
            } catch (NumberFormatException ignored) {
                // Damaged entry.
            }
        }
        return out;
    }

    static void clear(Context c) {
        prefs(c).edit().remove(KEY).apply();
    }

    /** The list handed to YouTube, so a title that names two chart songs resolves to the one being played. */
    static void playing(Context c, List<String> ids) {
        prefs(c).edit().putString("playing", String.join(",", ids)).apply();
    }

    private static Set<String> playingIds(Context c) {
        Set<String> out = new HashSet<>();
        for (String id : prefs(c).getString("playing", "").split(",")) if (!id.isEmpty()) out.add(id);
        return out;
    }

    /**
     * Notes the chart song this metadata belongs to, if any; returns it. Once a list has been played, only its songs
     * count, so a video watched on its own whose title names a chart song does not.
     */
    static DashData.Track mark(Context c, MediaMetadata m) {
        if (m == null) return null;
        List<DashData.Track> chart = DashData.decodeChart(prefs(c).getString("raw_chart", null));
        CharSequence title = m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.length() == 0) title = m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        Set<String> playing = playingIds(c);
        DashData.Track t = DashData.match(chart, title == null ? null : title.toString(), playing);
        if (t == null || (!playing.isEmpty() && !playing.contains(t.videoId))) return null;
        return markId(c, t);
    }

    /** Notes a chart song by its video id (the card's own player knows exactly what it plays). */
    static DashData.Track markId(Context c, DashData.Track t) {
        if (t == null) return null;
        if (ids(c).contains(t.videoId)) return t;
        // Rewrite the list without entries older than KEEP_MS, so it never grows past a week of songs.
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        StringBuilder sb = new StringBuilder();
        for (String e : prefs(c).getString(KEY, "").split(",")) {
            int colon = e.indexOf(':');
            if (colon <= 0) continue;
            try {
                if (Long.parseLong(e.substring(colon + 1)) < cutoff) continue;
            } catch (NumberFormatException bad) {
                continue;
            }
            sb.append(e).append(',');
        }
        sb.append(t.videoId).append(':').append(System.currentTimeMillis());
        prefs(c).edit().putString(KEY, sb.toString()).apply();
        return t;
    }
}
