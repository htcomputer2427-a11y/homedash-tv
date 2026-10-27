package vn.homedash.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * What each household sets on its own TV (Settings on Home): the greeting and the place the weather is for. Stored in
 * the "dash" preferences; until something is saved, the defaults in config.xml apply.
 */
final class Config {
    static final String GREETING = "cfg_greeting";
    static final String CITY = "cfg_city";
    static final String LAT = "cfg_lat";
    static final String LON = "cfg_lon";

    /** A place for the weather card: the name shown under the temperature, and where Open-Meteo looks. */
    static final class Place {
        final String name;
        final String region;
        final double lat;
        final double lon;

        Place(String name, String region, double lat, double lon) {
            this.name = name;
            this.region = region;
            this.lat = lat;
            this.lon = lon;
        }

        String label() {
            return region == null || region.isEmpty() || region.equals(name) ? name : name + " (" + region + ")";
        }
    }

    /**
     * The 34 provinces and cities since 1 July 2025, each by the city its offices are in. Any other town is found with
     * "Tìm nơi khác" (Open-Meteo's geocoder).
     */
    static final Place[] PLACES = {
            new Place("Hà Nội", null, 21.0285, 105.8542),
            new Place("TP. Hồ Chí Minh", null, 10.7769, 106.7009),
            new Place("Hải Phòng", null, 20.8449, 106.6881),
            new Place("Đà Nẵng", null, 16.0544, 108.2022),
            new Place("Huế", null, 16.4637, 107.5909),
            new Place("Cần Thơ", null, 10.0452, 105.7469),
            new Place("Tuyên Quang", null, 21.8236, 105.2140),
            new Place("Cao Bằng", null, 22.6657, 106.2577),
            new Place("Lai Châu", null, 22.3964, 103.4582),
            new Place("Yên Bái", "Lào Cai", 21.7229, 104.9113),
            new Place("Thái Nguyên", null, 21.5942, 105.8482),
            new Place("Điện Biên Phủ", "Điện Biên", 21.3860, 103.0230),
            new Place("Lạng Sơn", null, 21.8537, 106.7615),
            new Place("Sơn La", null, 21.3256, 103.9188),
            new Place("Việt Trì", "Phú Thọ", 21.3227, 105.4019),
            new Place("Bắc Giang", "Bắc Ninh", 21.2731, 106.1946),
            new Place("Hạ Long", "Quảng Ninh", 20.9517, 107.0800),
            new Place("Hưng Yên", null, 20.6464, 106.0511),
            new Place("Ninh Bình", null, 20.2506, 105.9745),
            new Place("Thanh Hóa", null, 19.8067, 105.7852),
            new Place("Vinh", "Nghệ An", 18.6796, 105.6813),
            new Place("Hà Tĩnh", null, 18.3420, 105.9057),
            new Place("Đồng Hới", "Quảng Trị", 17.4689, 106.6223),
            new Place("Quảng Ngãi", null, 15.1205, 108.7923),
            new Place("Quy Nhơn", "Gia Lai", 13.7829, 109.2196),
            new Place("Nha Trang", "Khánh Hòa", 12.2388, 109.1967),
            new Place("Đà Lạt", "Lâm Đồng", 11.9404, 108.4583),
            new Place("Buôn Ma Thuột", "Đắk Lắk", 12.6667, 108.0500),
            new Place("Biên Hòa", "Đồng Nai", 10.9574, 106.8426),
            new Place("Tân An", "Tây Ninh", 10.5360, 106.4130),
            new Place("Vĩnh Long", null, 10.2537, 105.9722),
            new Place("Mỹ Tho", "Đồng Tháp", 10.3600, 106.3600),
            new Place("Cà Mau", null, 9.1769, 105.1524),
            new Place("Rạch Giá", "An Giang", 10.0125, 105.0809),
    };

    private final Context context;
    private final SharedPreferences prefs;

    Config(Context context, SharedPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
    }

    /** True until the household has saved a place once; Home opens Settings on its own the first time. */
    boolean firstRun() {
        return !prefs.contains(CITY);
    }

    String greeting() {
        return prefs.getString(GREETING, context.getString(R.string.greeting));
    }

    String city() {
        return prefs.getString(CITY, context.getString(R.string.city));
    }

    double lat() {
        return prefs.contains(LAT) ? Double.longBitsToDouble(prefs.getLong(LAT, 0))
                : Double.parseDouble(context.getString(R.string.latitude));
    }

    double lon() {
        return prefs.contains(LON) ? Double.longBitsToDouble(prefs.getLong(LON, 0))
                : Double.parseDouble(context.getString(R.string.longitude));
    }

    void setGreeting(String text) {
        prefs.edit().putString(GREETING, text.trim()).apply();
    }

    void setPlace(Place p) {
        prefs.edit()
                .putString(CITY, p.name)
                .putLong(LAT, Double.doubleToRawLongBits(p.lat))
                .putLong(LON, Double.doubleToRawLongBits(p.lon))
                // The old place's weather must not show under the new name.
                .remove("raw_weather")
                .remove("t_weather")
                .apply();
    }

    /** Open-Meteo's free geocoder, Vietnam only, names in Vietnamese. */
    static String searchUrl(String query) {
        try {
            return "https://geocoding-api.open-meteo.com/v1/search?count=10&language=vi&countryCode=VN&format=json&name="
                    + URLEncoder.encode(query.trim(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The geocoder's answer as places, best match first; empty when nothing matched or the answer is unreadable. */
    static List<Place> parseSearch(String json) {
        List<Place> out = new ArrayList<>();
        try {
            JSONArray results = new JSONObject(json).optJSONArray("results");
            if (results == null) return out;
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String name = r.optString("name", "");
                if (name.isEmpty() || !r.has("latitude") || !r.has("longitude")) continue;
                out.add(new Place(name, r.optString("admin1", null), r.getDouble("latitude"), r.getDouble("longitude")));
            }
        } catch (JSONException e) {
            out.clear();
        }
        return out;
    }
}
