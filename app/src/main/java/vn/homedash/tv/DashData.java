package vn.homedash.tv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The four network sources and their parsers. Parsers take the raw response and return plain numbers, so they run
 * in JVM tests without a TV. Every source can fail on its own; the activity keeps showing the last good values.
 */
final class DashData {
    /** PNJ's price API; it lists SJC bars next to PNJ's own products (sjc.com.vn sits behind a browser check). */
    static final String GOLD_URL = "https://edge-api.pnj.io/ecom-frontend/v1/get-gold-price?zone=00";
    static final String FX_URL = "https://portal.vietcombank.com.vn/Usercontrols/TVPortal.TyGia/pXML.aspx";
    /** Petrolimex retail prices, region 1, as webgia.tv republishes them in plain HTML. */
    static final String FUEL_URL = "https://webgia.tv/xang-dau/petrolimex";

    static String weatherUrl(double lat, double lon) {
        return String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day&timezone=Asia%%2FBangkok", lat, lon);
    }

    private DashData() {
    }

    static String get(String url) throws IOException {
        return request(url, null);
    }

    /** GET, or POST of a JSON body when one is given. */
    static String request(String url, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; TV) HomeDash/1.0");
        c.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9");
        if (json != null) {
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        }
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException(url + ": HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 2_000_000) throw new IOException(url + ": response too large");
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- gold ---------------------------------------------------------------------------------------------

    /** Selling price in million VND per lượng, by PNJ product code (SJC, N24K, 61...); PNJ quotes thousands per chỉ. */
    static Map<String, Double> parseGold(String json) {
        Map<String, Double> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"masp\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"giaban\"\\s*:\\s*([0-9.]+)").matcher(json);
        while (m.find()) {
            out.put(m.group(1), Double.parseDouble(m.group(2)) / 100.0);
        }
        return out;
    }

    // ---- USD / VND ----------------------------------------------------------------------------------------

    /** {buy, sell} in VND for one USD (cash buy and sell), or null when the row is missing. */
    static double[] parseUsd(String xml) {
        Matcher row = Pattern.compile("<Exrate[^>]*CurrencyCode=\"USD\"[^>]*/?>").matcher(xml);
        if (!row.find()) return null;
        String r = row.group();
        Double buy = attr(r, "Buy");
        Double sell = attr(r, "Sell");
        if (buy == null || sell == null) return null;
        return new double[]{buy, sell};
    }

    private static Double attr(String tag, String name) {
        Matcher m = Pattern.compile("\\b" + name + "=\"([0-9.,]+)\"").matcher(tag);
        if (!m.find()) return null;
        return Double.parseDouble(m.group(1).replace(",", ""));
    }

    // ---- fuel ---------------------------------------------------------------------------------------------

    static final class Fuel {
        final double price;
        final double change;
        final String since;

        Fuel(double price, double change, String since) {
            this.price = price;
            this.change = change;
            this.since = since;
        }
    }

    /** Region 1 price and the change of the last adjustment, by the product name in the table's first column. */
    static Map<String, Fuel> parseFuel(String html) {
        Map<String, Fuel> out = new LinkedHashMap<>();
        for (java.util.List<String> cells : table(html)) {
            if (cells.size() < 3) continue;
            Double price = number(cells.get(1));
            if (price == null) continue;
            Double change = signed(cells.get(2));
            String since = cells.size() >= 6 ? cells.get(5) : "";
            out.put(cells.get(0), new Fuel(price, change == null ? 0 : change, since));
        }
        return out;
    }

    /** Every table row of a webgia.tv page as its cells' text (th and td alike). */
    static java.util.List<java.util.List<String>> table(String html) {
        java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();
        Matcher rows = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL).matcher(html);
        Pattern cell = Pattern.compile("<t[dh][^>]*>(.*?)</t[dh]>", Pattern.DOTALL);
        while (rows.find()) {
            Matcher c = cell.matcher(rows.group(1));
            java.util.List<String> cells = new java.util.ArrayList<>();
            while (c.find()) cells.add(text(c.group(1)));
            if (!cells.isEmpty()) out.add(cells);
        }
        return out;
    }

    /** Vietnamese thousands use dots: "24.910" is 24910. */
    static Double number(String s) {
        Matcher m = Pattern.compile("([0-9][0-9.]*)").matcher(s);
        if (!m.find()) return null;
        return Double.parseDouble(m.group(1).replace(".", ""));
    }

    /**
     * A change cell as webgia.tv writes it: "▲ 450", "▼ 1.810", "+500", "-1.000", "±0" or "0". Null when the cell holds
     * no number at all (a dash).
     */
    static Double signed(String s) {
        Double n = number(s);
        if (n == null) return null;
        boolean down = s.contains("▼") || s.contains("↓") || s.trim().startsWith("-") || s.contains("−");
        return down ? -n : n;
    }

    // ---- gold and USD with their changes, from webgia.tv ----------------------------------------------------

    /** PNJ's prices with the change since its previous adjustment, in thousand VND per lượng. */
    static final String GOLD_WEB_URL = "https://webgia.tv/gia-vang/pnj";
    /** Vietcombank's rates with the change of the transfer selling rate. */
    static final String FX_WEB_URL = "https://webgia.tv/ty-gia/vietcombank";

    /** The three gold rows by PNJ product code, as the PNJ API names them. */
    static final String[][] GOLD_ROWS = {
            {"SJC", "Vàng miếng SJC"},
            {"N24K", "Nhẫn Trơn PNJ 999.9"},
            {"61", "Vàng 610"},
    };

    /** code -> {selling price, change}, both in million VND per lượng. */
    static Map<String, double[]> parseGoldWeb(String html) {
        Map<String, double[]> out = new LinkedHashMap<>();
        for (java.util.List<String> cells : table(html)) {
            if (cells.size() < 4) continue;
            for (String[] row : GOLD_ROWS) {
                if (out.containsKey(row[0]) || !cells.get(0).startsWith(row[1])) continue;
                Double sell = number(cells.get(2));
                Double change = signed(cells.get(3));
                if (sell == null || sell < 1000) continue;
                out.put(row[0], new double[]{sell / 1000.0, change == null ? 0 : change / 1000.0});
            }
        }
        return out;
    }

    /** {cash buy, transfer sell, change of transfer sell} in VND for one USD, or null. */
    static double[] parseUsdWeb(String html) {
        for (java.util.List<String> cells : table(html)) {
            if (cells.size() < 6 || !cells.get(0).startsWith("USD")) continue;
            Double buy = number(cells.get(1));
            Double sell = number(cells.get(3));
            Double change = signed(cells.get(5));
            if (buy == null || sell == null) return null;
            return new double[]{buy, sell, change == null ? 0 : change};
        }
        return null;
    }

    private static String text(String html) {
        return html.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    // ---- trending music -----------------------------------------------------------------------------------

    /**
     * YouTube Music's weekly top songs for Vietnam, as charts.youtube.com loads them. Not a published API: if the
     * request stops working the card keeps the last chart it had.
     */
    static final String CHART_URL = "https://charts.youtube.com/youtubei/v1/browse?alt=json";
    static final String CHART_BODY = "{\"context\":{\"client\":{\"clientName\":\"WEB_MUSIC_ANALYTICS\",\"clientVersion\":\"2.0\","
            + "\"hl\":\"vi\",\"gl\":\"VN\",\"theme\":\"MUSIC\"}},\"browseId\":\"FEmusic_analytics_charts_home\","
            + "\"query\":\"perspective=CHART_DETAILS&chart_params_country_code=vn&chart_params_chart_type=TRACKS"
            + "&chart_params_period_type=WEEKLY\"}";

    static final class Track {
        final String videoId;
        final String title;
        final String artists;
        final String thumb;
        final int position;
        /** Last week's position; 0 for a new entry. */
        final int previous;

        Track(String videoId, String title, String artists, String thumb, int position, int previous) {
            this.videoId = videoId;
            this.title = title;
            this.artists = artists;
            this.thumb = thumb;
            this.position = position;
            this.previous = previous;
        }

        /** The cover at a size fit for the big art box (the chart lists 180px thumbnails). */
        String largeThumb() {
            return thumb.replaceAll("=w\\d+-h\\d+", "=w544-h544");
        }
    }

    static java.util.List<Track> parseChart(String json, int limit) {
        java.util.List<Track> out = new java.util.ArrayList<>();
        try {
            org.json.JSONArray views = findArray(new org.json.JSONObject(json), "trackViews");
            if (views == null) return out;
            for (int i = 0; i < views.length() && out.size() < limit; i++) {
                org.json.JSONObject t = views.getJSONObject(i);
                String id = t.optString("encryptedVideoId");
                String name = t.optString("name");
                if (id.isEmpty() || name.isEmpty()) continue;
                StringBuilder artists = new StringBuilder();
                org.json.JSONArray a = t.optJSONArray("artists");
                for (int j = 0; a != null && j < a.length(); j++) {
                    if (artists.length() > 0) artists.append(", ");
                    artists.append(a.getJSONObject(j).optString("name"));
                }
                String thumb = "";
                org.json.JSONObject th = t.optJSONObject("thumbnail");
                org.json.JSONArray list = th == null ? null : th.optJSONArray("thumbnails");
                if (list != null && list.length() > 0) thumb = list.getJSONObject(list.length() - 1).optString("url");
                org.json.JSONObject meta = t.optJSONObject("chartEntryMetadata");
                int pos = meta == null ? out.size() + 1 : meta.optInt("currentPosition", out.size() + 1);
                int prev = meta == null ? 0 : meta.optInt("previousPosition", 0);
                out.add(new Track(id, name, artists.toString(), thumb, pos, prev));
            }
        } catch (org.json.JSONException e) {
            out.clear();
        }
        return out;
    }

    private static org.json.JSONArray findArray(Object node, String key) {
        if (node instanceof org.json.JSONObject) {
            org.json.JSONObject o = (org.json.JSONObject) node;
            org.json.JSONArray hit = o.optJSONArray(key);
            if (hit != null) return hit;
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                org.json.JSONArray found = findArray(o.opt(keys.next()), key);
                if (found != null) return found;
            }
        } else if (node instanceof org.json.JSONArray) {
            org.json.JSONArray a = (org.json.JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONArray found = findArray(a.opt(i), key);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** One track per line, tab separated: the 400 KB response is not worth keeping. */
    static String encodeChart(java.util.List<Track> tracks) {
        StringBuilder sb = new StringBuilder();
        for (Track t : tracks) {
            sb.append(clean(t.videoId)).append('\t').append(clean(t.title)).append('\t').append(clean(t.artists))
                    .append('\t').append(clean(t.thumb)).append('\t').append(t.position).append('\t').append(t.previous)
                    .append('\n');
        }
        return sb.toString();
    }

    static java.util.List<Track> decodeChart(String s) {
        java.util.List<Track> out = new java.util.ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length < 6) continue;
            try {
                out.add(new Track(p[0], p[1], p[2], p[3], Integer.parseInt(p[4]), Integer.parseInt(p[5])));
            } catch (NumberFormatException ignored) {
                // A damaged line; skip it.
            }
        }
        return out;
    }

    /** Lower case letters and digits only, to match a video title against a chart entry. */
    static String normalize(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    /**
     * The chart entry a playing video belongs to, by title: "CONGB - chờ chút... | TINH HÀ SAY HI" is "chờ chút...",
     * and "KHÔNG MÔN ĐĂNG HỘ ĐỐI REMIX HOT TIKTOK" is "Không Môn Đăng Hộ Đối (TD Remix)" once the part in brackets is
     * left out. A song in the list being played wins over one that is not ("JACK - J97 | LƯU NIÊN | Album TAM THÁI
     * TỬ" is the playing #4, not #78 "TAM THÁI TỬ"); then the longest match, so "Người Dưng" is not taken for "Người".
     */
    static Track match(java.util.List<Track> chart, String videoTitle) {
        return match(chart, videoTitle, java.util.Collections.emptySet());
    }

    static Track match(java.util.List<Track> chart, String videoTitle, java.util.Set<String> playing) {
        String t = normalize(videoTitle);
        if (t.isEmpty()) return null;
        Track best = null;
        int bestScore = 0;
        for (Track tr : chart) {
            String full = normalize(tr.title);
            String core = normalize(tr.title.replaceAll("[(\\[][^)\\]]*[)\\]]", " "));
            int bonus = playing.contains(tr.videoId) ? 1000 : 0;
            for (String n : new String[]{full, core}) {
                if (n.length() >= 3 && t.contains(n) && n.length() + bonus > bestScore) {
                    best = tr;
                    bestScore = n.length() + bonus;
                }
            }
        }
        return best;
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ');
    }

    // ---- weather ------------------------------------------------------------------------------------------

    /** {temperature °C, WMO weather code, is_day 0/1}, or null. */
    static double[] parseWeather(String json) {
        int at = json.indexOf("\"current\"");
        if (at < 0) return null;
        String cur = json.substring(at);
        Double t = num(cur, "temperature_2m");
        Double code = num(cur, "weather_code");
        Double day = num(cur, "is_day");
        if (t == null || code == null) return null;
        return new double[]{t, code, day == null ? 1 : day};
    }

    private static Double num(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[0-9.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }

    /** Temperature colour stops, °C to ARGB: pale blue when cool, through mint and yellow, to orange red when hot. */
    private static final double[] TEMP_AT = {16, 20, 24, 27, 30, 33, 36};
    private static final int[] TEMP_COLOR = {0xFFC4E8FF, 0xFF8FD3FF, 0xFF9BE8D0, 0xFFFFE08A, 0xFFFFB35C, 0xFFFF8A4C,
            0xFFFF5A3C};

    /** The colour of a temperature, blended between the two nearest stops. */
    static int tempColor(double t) {
        if (t <= TEMP_AT[0]) return TEMP_COLOR[0];
        int last = TEMP_AT.length - 1;
        if (t >= TEMP_AT[last]) return TEMP_COLOR[last];
        int i = 0;
        while (t > TEMP_AT[i + 1]) i++;
        double f = (t - TEMP_AT[i]) / (TEMP_AT[i + 1] - TEMP_AT[i]);
        int a = TEMP_COLOR[i];
        int b = TEMP_COLOR[i + 1];
        int r = (int) Math.round(((a >> 16) & 0xFF) + f * (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)));
        int g = (int) Math.round(((a >> 8) & 0xFF) + f * (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)));
        int bl = (int) Math.round((a & 0xFF) + f * ((b & 0xFF) - (a & 0xFF)));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Vietnamese description of a WMO weather code. */
    static String weatherText(int code, boolean day) {
        if (code == 0) return day ? "Trời nắng" : "Trời quang";
        if (code == 1) return day ? "Trời nắng nhẹ" : "Ít mây";
        if (code == 2) return "Có mây";
        if (code == 3) return "Nhiều mây";
        if (code == 45 || code == 48) return "Sương mù";
        if (code >= 51 && code <= 57) return "Mưa phùn";
        if (code >= 61 && code <= 67) return code >= 65 ? "Mưa to" : "Mưa";
        if (code >= 80 && code <= 82) return "Mưa rào";
        if (code >= 95) return "Có dông";
        if (code >= 71 && code <= 86) return "Tuyết";
        return "—";
    }

    static int weatherIcon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.ic_w_sun : R.drawable.ic_w_moon;
        if (code == 1 || code == 2) return day ? R.drawable.ic_w_partly : R.drawable.ic_w_partly_night;
        if (code == 3 || code == 45 || code == 48) return R.drawable.ic_w_cloud;
        if (code >= 95) return R.drawable.ic_w_storm;
        if (code >= 51) return R.drawable.ic_w_rain;
        return R.drawable.ic_w_cloud;
    }
}
