package vn.homedash.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Home screen: calendar with the lunar date, clock and weather, gold / USD / fuel prices, a card for whatever
 * YouTube or YouTube Music is playing, and four big app buttons. The app grid and the Inputs menu belong to the
 * CleanYourTV launcher when it is installed; without it, Home Dash lists the apps itself (AppsDialog).
 */
public class DashboardActivity extends Activity {
    private static final String TAG = "HomeDash";
    private static final String TVHOME = "com.gathin.tvhome";
    private static final String TVHOME_MAIN = "com.gathin.tvhome.MainActivity";
    private static final Locale VI = new Locale("vi", "VN");
    private static final int UP = 0xFF3DDC84;
    private static final int DOWN = 0xFFFF4D4D;
    private static final int FLAT = 0xCCFFFFFF;
    // Calendar colours: the sunset gradient on weekdays, blue on Saturdays, red on Sundays and days off.
    private static final int SUNSET_TOP = 0xFFFFE08A;
    private static final int SUNSET_BOTTOM = 0xFFFF9A5B;
    private static final int WEEKDAY_GOLD = 0xFFFFD36E;
    private static final int SATURDAY = 0xFF7CC8FF;
    private static final int SUNDAY = 0xFFFF6B6B;
    private static final String[] MONTHS = {"MỘT", "HAI", "BA", "TƯ", "NĂM", "SÁU", "BẢY", "TÁM", "CHÍN", "MƯỜI",
            "MƯỜI MỘT", "MƯỜI HAI"};
    private static final String[] WEEKDAYS = {"CHỦ NHẬT", "THỨ HAI", "THỨ BA", "THỨ TƯ", "THỨ NĂM", "THỨ SÁU", "THỨ BẢY"};
    private static final String GOLD = "gold";
    private static final String FX = "fx";
    private static final String FUEL = "fuel";
    private static final String WEATHER = "weather";
    private static final String CHART = "chart";
    /** The chart is weekly; looking twice a day is plenty. */
    private static final long CHART_MS = 12 * 3600_000L;
    private static final int LIST_SIZE = 40;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService img = Executors.newSingleThreadExecutor();
    private final Set<String> inFlight = new HashSet<>();
    private SharedPreferences prefs;
    private Config config;
    private long refreshMs;
    private int lastDay = -1;
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", VI);
    private final SimpleDateFormat secondsFormat = new SimpleDateFormat(":ss", VI);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", VI);
    private final SimpleDateFormat dayTimeFormat = new SimpleDateFormat("dd/MM HH:mm", VI);

    private TextView clock;
    private TextView clockSec;
    private TextView calLabel;
    private TextView calLunarTag;
    private TextView calMonth;
    private TextView calDay;
    private TextView calWeekday;
    private TextView calLunar;
    private TextView calLunarYear;
    private TextView calLunarMonth;
    private TextView calLunarDay;
    private ImageButton wifi;

    // Music
    private MediaSessionManager sessions;
    private ComponentName listener;
    private MediaController controller;
    private boolean mediaAllowed;
    private TextView musicTitle;
    private TextView musicArtist;
    private TextView musicPosition;
    private TextView musicDuration;
    private ProgressBar musicProgress;
    private ImageView musicArt;
    private ImageButton musicPlay;
    private final View[] queueRows = new View[3];
    private String artKey;
    /** A cover failed to load (no network yet right after boot): the refresher tries the covers again. */
    private boolean coverFailed;
    // Trending: YouTube Music's weekly Vietnam chart, shown while nothing is playing and as the list on the right.
    private List<DashData.Track> chart = new java.util.ArrayList<>();
    private int chartPage;
    private int chartSelected;
    /** The card's own YouTube player; null until the first Play. */
    private WebPlayer web;
    /**
     * The card player's media session, so "Ok Google, tạm dừng / bài tiếp theo" and the media keys reach it the way
     * they reach YouTube. Active while a list is loaded.
     */
    private MediaSession session;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            Date date = new Date(now);
            clock.setText(clockFormat.format(date));
            clockSec.setText(secondsFormat.format(date));
            Calendar c = Calendar.getInstance();
            if (c.get(Calendar.DAY_OF_YEAR) != lastDay) renderCalendar(c);
            updateProgress();
            ui.postDelayed(this, 1000 - now % 1000);
        }
    };

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refreshStale(false);
            updateWifi();
            if (coverFailed) {
                coverFailed = false;
                artKey = null;
                renderMetadata();
                renderQueue();
            }
            ui.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        prefs = getSharedPreferences("dash", MODE_PRIVATE);
        config = new Config(this, prefs);
        refreshMs = getResources().getInteger(R.integer.refresh_minutes) * 60_000L;
        listener = new ComponentName(this, MediaListener.class);

        clock = findViewById(R.id.clock);
        clockSec = findViewById(R.id.clock_sec);
        calLabel = findViewById(R.id.cal_label);
        calLunarTag = findViewById(R.id.cal_lunar_tag);
        calMonth = findViewById(R.id.cal_month);
        calDay = findViewById(R.id.cal_day);
        calWeekday = findViewById(R.id.cal_weekday);
        calLunar = findViewById(R.id.cal_lunar);
        calLunarYear = findViewById(R.id.cal_lunar_year);
        calLunarMonth = findViewById(R.id.cal_lunar_month);
        calLunarDay = findViewById(R.id.cal_lunar_day);
        keepSunset(clock);
        keepSunset(calDay);
        tint(clock, 0);
        showConfig();

        header(R.id.gold_header, R.drawable.ic_gold, "Giá vàng hôm nay");
        header(R.id.fx_header, R.drawable.ic_flag_us, "Tỷ giá USD");
        header(R.id.fuel_header, R.drawable.ic_fuel, "Giá xăng dầu");
        bindCard(R.id.card_gold, GOLD);
        bindCard(R.id.card_fx, FX);
        bindCard(R.id.card_fuel, FUEL);

        wifi = findViewById(R.id.btn_wifi);
        iconButton(R.id.btn_voice, v -> listen());
        iconButton(R.id.btn_inputs, v -> openInputs());
        iconButton(R.id.btn_settings, v -> openSettings());
        iconButton(R.id.btn_wifi, v -> start(new Intent(Settings.ACTION_WIFI_SETTINGS)));

        appButton(R.id.app_youtube, R.drawable.btn_youtube, R.drawable.ic_youtube, "YouTube",
                v -> launch(getString(R.string.pkg_youtube)));
        appButton(R.id.app_netflix, R.drawable.btn_netflix, R.drawable.ic_netflix, "Netflix",
                v -> launch(getString(R.string.pkg_netflix)));
        appButton(R.id.app_store, R.drawable.btn_store, R.drawable.ic_store, "Cửa hàng",
                v -> launch(getString(R.string.pkg_store)));
        appButton(R.id.app_all, R.drawable.btn_apps, R.drawable.ic_apps, "Tất cả app", v -> openAppGrid());

        setupMusic();
        setupSession();
        loadWallpaper();
        render(GOLD);
        render(FX);
        render(FUEL);
        render(WEATHER);
        render(CHART);
        findViewById(R.id.app_youtube).findViewById(R.id.button).requestFocus();
        handleSearch(getIntent());
        // A new TV: ask for the greeting and the place first, instead of showing the defaults.
        if (config.firstRun()) ui.post(this::openSettings);
    }

    private void openSettings() {
        new SettingsDialog(this, config, io, () -> {
            showConfig();
            render(WEATHER);
            fetch(WEATHER, true);
        }).show();
    }

    private void showConfig() {
        this.<TextView>findViewById(R.id.greeting).setText(config.greeting());
        this.<TextView>findViewById(R.id.city).setText(config.city());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(tick);
        tick.run();
        ui.removeCallbacks(refresher);
        refresher.run();
        connectMedia();
        selectFirstUnheard();
        renderMetadata();
        renderQueue();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        ui.removeCallbacks(refresher);
        disconnectMedia();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Another app in front: the card's music stops with Home Dash, as YouTube's would. Not on pause: the Google
        // Assistant panel only covers part of the screen, and the music goes on under it.
        if (listening) giveUp(false);
        resumeAfter = false;
        if (web != null) web.pause();
        if (tts != null) tts.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        img.shutdownNow();
        if (web != null) web.destroy();
        if (tts != null) tts.shutdown();
        if (recognizer != null) recognizer.destroy();
        session.release();
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- calendar --------------------------------------------------------------------------------------------

    private void renderCalendar(Calendar c) {
        lastDay = c.get(Calendar.DAY_OF_YEAR);
        int d = c.get(Calendar.DAY_OF_MONTH);
        int m = c.get(Calendar.MONTH) + 1;
        int y = c.get(Calendar.YEAR);
        calMonth.setText("THÁNG " + MONTHS[m - 1] + " · " + y);
        calDay.setText(String.valueOf(d));
        int dow = c.get(Calendar.DAY_OF_WEEK);
        calWeekday.setText(WEEKDAYS[dow - 1]);
        Lunar l = Lunar.of(d, m, y);
        Calendar next = (Calendar) c.clone();
        next.add(Calendar.DAY_OF_MONTH, 1);
        Lunar tomorrow = Lunar.of(next.get(Calendar.DAY_OF_MONTH), next.get(Calendar.MONTH) + 1, next.get(Calendar.YEAR));
        Holidays.Day holiday = Holidays.of(d, m, l, tomorrow);

        // Weekdays keep the sunset look; Saturday is blue and Sundays and days off red, as on a wall calendar.
        boolean red = dow == Calendar.SUNDAY || (holiday != null && holiday.dayOff);
        int color = red ? SUNDAY : dow == Calendar.SATURDAY ? SATURDAY : 0;
        tint(calDay, color);
        calWeekday.setTextColor(color == 0 ? WEEKDAY_GOLD : color);
        if (holiday != null) {
            calLabel.setText(holiday.name);
            calLabel.setTextColor(holiday.dayOff ? SUNDAY : WEEKDAY_GOLD);
            calLabel.setTypeface(calWeekday.getTypeface());
        } else {
            calLabel.setText("Âm lịch");
            calLabel.setTextColor(Color.WHITE);
            calLabel.setTypeface(calLunarYear.getTypeface());
        }
        String tag = Holidays.lunarTag(l);
        calLunarTag.setVisibility(tag == null ? View.GONE : View.VISIBLE);
        if (tag != null) calLunarTag.setText(tag);
        calLunar.setText(l.day + " / " + l.month);
        calLunarYear.setText("Năm " + l.yearName());
        calLunarMonth.setText("Tháng " + l.monthName() + (l.leap ? " (nhuận)" : ""));
        calLunarDay.setText("Ngày " + l.dayName());
    }

    /** 0 paints the text in the sunset gradient; any other colour paints it flat in that colour. */
    private static void tint(TextView v, int color) {
        v.setTag(R.id.cal_day, color == 0 ? Boolean.TRUE : Boolean.FALSE);
        if (color == 0) {
            v.setTextColor(Color.WHITE);
            applySunset(v);
        } else {
            v.getPaint().setShader(null);
            v.setTextColor(color);
        }
        v.invalidate();
    }

    /** The gradient spans the view's height, so it is rebuilt whenever the view is laid out again. */
    private static void keepSunset(TextView v) {
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (Boolean.TRUE.equals(view.getTag(R.id.cal_day)) && b - t != ob - ot) applySunset((TextView) view);
        });
    }

    private static void applySunset(TextView v) {
        int h = v.getHeight();
        if (h == 0) return;
        v.getPaint().setShader(new android.graphics.LinearGradient(0, h * 0.1f, 0, h * 0.9f, SUNSET_TOP, SUNSET_BOTTOM,
                android.graphics.Shader.TileMode.CLAMP));
        v.invalidate();
    }

    // ---- prices and weather ----------------------------------------------------------------------------------

    private void header(int id, int icon, String title) {
        View h = findViewById(id);
        h.<ImageView>findViewById(R.id.icon).setImageResource(icon);
        h.<TextView>findViewById(R.id.title).setText(title);
    }

    private void bindCard(int id, String key) {
        View card = findViewById(id);
        card.setOnClickListener(v -> {
            // OK on a price card reads it aloud, and fetches it again meanwhile.
            switch (key) {
                case GOLD:
                    say(Speech.gold(prefs.getString("raw_" + key, null)));
                    break;
                case FX:
                    say(Speech.usd(prefs.getString("raw_" + key, null)));
                    break;
                default:
                    say(Speech.fuel(prefs.getString("raw_" + key, null)));
                    break;
            }
            fetch(key, true);
        });
        card.setOnFocusChangeListener((v, focused) -> scale(v, focused ? 1.02f : 1f));
    }

    private String weatherUrl() {
        try {
            return DashData.weatherUrl(config.lat(), config.lon());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void refreshStale(boolean force) {
        long now = System.currentTimeMillis();
        for (String key : new String[]{GOLD, FX, FUEL, WEATHER}) {
            if (force || now - prefs.getLong("t_" + key, 0) >= refreshMs) fetch(key, false);
        }
        if (force || now - prefs.getLong("t_" + CHART, 0) >= CHART_MS) fetch(CHART, false);
    }

    private void fetch(String key, boolean manual) {
        if (!inFlight.add(key)) return;
        io.execute(() -> {
            String result;
            try {
                result = load(key);
            } catch (RuntimeException e) {
                Log.w(TAG, key + ": " + e);
                result = null;
            }
            String stored = result;
            ui.post(() -> {
                inFlight.remove(key);
                if (stored != null) {
                    prefs.edit().putString("raw_" + key, stored).putLong("t_" + key, System.currentTimeMillis()).apply();
                    render(key);
                } else {
                    // Retry in 5 minutes rather than waiting a full refresh period.
                    prefs.edit().putLong("t_" + key, System.currentTimeMillis() - refreshMs + 5 * 60_000L).apply();
                    if (manual) toast("Không tải được dữ liệu, thử lại sau");
                }
            });
        });
    }

    private static String body(String url, String post) {
        if (url == null) return null;
        try {
            return DashData.request(url, post);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, url + ": " + e);
            return null;
        }
    }

    /**
     * Fetches one source on the io thread and returns what to store for it, or null when nothing usable came back.
     * Gold and USD come from webgia.tv, which publishes each price with its change; when it fails, PNJ's API and
     * Vietcombank's XML give the prices and the change is against the last value seen on an earlier day.
     */
    private String load(String key) {
        switch (key) {
            case GOLD:
                return loadGold();
            case FX:
                return loadFx();
            case FUEL: {
                String html = body(DashData.FUEL_URL, null);
                if (html == null) return null;
                Map<String, DashData.Fuel> f = DashData.parseFuel(html);
                if (f.isEmpty()) return null;
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, DashData.Fuel> e : f.entrySet()) {
                    sb.append(e.getKey()).append('\t').append(e.getValue().price).append('\t')
                            .append(e.getValue().change).append('\t').append(e.getValue().since).append('\n');
                }
                return sb.toString();
            }
            case WEATHER: {
                String json = body(weatherUrl(), null);
                return json == null || DashData.parseWeather(json) == null ? null : json;
            }
            case CHART: {
                String json = body(DashData.CHART_URL, DashData.CHART_BODY);
                List<DashData.Track> t = json == null ? null : DashData.parseChart(json, 100);
                return t == null || t.isEmpty() ? null : DashData.encodeChart(t);
            }
            default:
                return null;
        }
    }

    /** Stored as "code\tsell\tchange" lines, million VND per lượng; NaN for a change nobody knows yet. */
    private String loadGold() {
        Map<String, double[]> g = null;
        String html = body(DashData.GOLD_WEB_URL, null);
        if (html != null) {
            g = DashData.parseGoldWeb(html);
            if (!g.containsKey("SJC")) g = null;
        }
        if (g != null) {
            for (Map.Entry<String, double[]> e : g.entrySet()) roll("gold_" + e.getKey(), e.getValue()[0]);
        } else {
            String json = body(DashData.GOLD_URL, null);
            Map<String, Double> p = json == null ? null : DashData.parseGold(json);
            if (p == null || !p.containsKey("SJC")) return null;
            g = new java.util.LinkedHashMap<>();
            for (String[] row : DashData.GOLD_ROWS) {
                Double v = p.get(row[0]);
                if (v == null) continue;
                roll("gold_" + row[0], v);
                Double c = change("gold_" + row[0], v);
                g.put(row[0], new double[]{v, c == null ? Double.NaN : c});
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, double[]> e : g.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t').append(e.getValue()[1]).append('\n');
        }
        return sb.toString();
    }

    /**
     * Stored as "buy\tsell\tbuyChange\tsellChange", VND. webgia.tv gives the change of the selling rate only; the
     * buying rate's change is against the previous day's last rate, and until there is one it is taken to be the
     * selling rate's (Vietcombank moves both by the same step).
     */
    private String loadFx() {
        String html = body(DashData.FX_WEB_URL, null);
        double[] web = html == null ? null : DashData.parseUsdWeb(html);
        double buy;
        double sell;
        double sellChange;
        if (web != null) {
            buy = web[0];
            sell = web[1];
            sellChange = web[2];
        } else {
            String xml = body(DashData.FX_URL, null);
            double[] r = xml == null ? null : DashData.parseUsd(xml);
            if (r == null) return null;
            buy = r[0];
            sell = r[1];
            sellChange = Double.NaN;
        }
        roll("fx_buy", buy);
        roll("fx_sell", sell);
        if (Double.isNaN(sellChange)) {
            Double own = change("fx_sell", sell);
            sellChange = own == null ? Double.NaN : own;
        }
        Double ownBuy = change("fx_buy", buy);
        double buyChange = ownBuy != null ? ownBuy : sellChange;
        return buy + "\t" + sell + "\t" + buyChange + "\t" + sellChange;
    }

    /** NaN, or a field that does not parse, means "no change known". */
    private static Double field(String[] p, int i) {
        if (i >= p.length) return null;
        try {
            double d = Double.parseDouble(p[i]);
            return Double.isNaN(d) ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The change shown next to a price is against the last value seen on an earlier day. */
    private void roll(String key, double now) {
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String day = prefs.getString("d_" + key, null);
        SharedPreferences.Editor e = prefs.edit();
        if (day != null && !day.equals(today) && prefs.contains("v_" + key)) {
            e.putString("b_" + key, prefs.getString("v_" + key, null));
        }
        e.putString("d_" + key, today).putString("v_" + key, Double.toString(now)).apply();
    }

    private Double change(String key, Double now) {
        String base = prefs.getString("b_" + key, null);
        if (now == null || base == null) return null;
        return now - Double.parseDouble(base);
    }

    private void render(String key) {
        String raw = prefs.getString("raw_" + key, null);
        long t = prefs.getLong("t_" + key, 0);
        switch (key) {
            case GOLD: {
                Map<String, String[]> g = new java.util.HashMap<>();
                if (raw != null) {
                    for (String line : raw.split("\n")) {
                        String[] p = line.split("\t");
                        if (p.length >= 2) g.put(p[0], p);
                    }
                }
                goldRow(R.id.gold_sjc, "Vàng miếng SJC", g.get("SJC"));
                goldRow(R.id.gold_ring, "Nhẫn 999.9", g.get("N24K"));
                goldRow(R.id.gold_610, "Vàng 610", g.get("61"));
                updated(R.id.gold_header, raw == null ? 0 : t);
                break;
            }
            case FX: {
                String[] p = raw == null ? new String[0] : raw.split("\t");
                Double buy = field(p, 0);
                Double sell = field(p, 1);
                this.<TextView>findViewById(R.id.fx_buy).setText(buy == null ? "—" : vnd(buy));
                this.<TextView>findViewById(R.id.fx_sell).setText(sell == null ? "—" : vnd(sell));
                setChange(findViewById(R.id.fx_buy_change), buy == null ? null : field(p, 2), false);
                setChange(findViewById(R.id.fx_sell_change), sell == null ? null : field(p, 3), false);
                updated(R.id.fx_header, buy == null ? 0 : t);
                break;
            }
            case FUEL: {
                fuelRow(R.id.fuel_e10, "Xăng E10", raw, "Xăng E10 RON 95-III", 0xFF8EF0A0);
                fuelRow(R.id.fuel_e5, "Xăng E5", raw, "Xăng E5 RON 92-II", 0xFF7DD3FF);
                fuelRow(R.id.fuel_do, "Dầu DO", raw, "Dầu diesel 0,05S-II", 0xFFFF8FA0);
                updated(R.id.fuel_header, raw == null ? 0 : t);
                break;
            }
            case WEATHER: {
                double[] w = raw == null ? null : DashData.parseWeather(raw);
                TextView temp = findViewById(R.id.temp);
                TextView text = findViewById(R.id.weather_text);
                ImageView icon = findViewById(R.id.weather_icon);
                if (w == null) {
                    temp.setText("—°");
                    temp.setTextColor(Color.WHITE);
                    text.setText("Đang tải thời tiết");
                } else {
                    boolean day = w[2] > 0;
                    temp.setText(Math.round(w[0]) + "°");
                    // Pale blue when cool, deepening to orange red as it gets hot.
                    temp.setTextColor(DashData.tempColor(w[0]));
                    text.setText(DashData.weatherText((int) w[1], day));
                    icon.setImageResource(DashData.weatherIcon((int) w[1], day));
                }
                break;
            }
            case CHART: {
                chart = DashData.decodeChart(raw);
                chartSelected = Math.min(chartSelected, Math.max(0, chart.size() - 1));
                selectFirstUnheard();
                if (musicTitle != null) {
                    renderMetadata();
                    renderQueue();
                }
                break;
            }
            default:
                break;
        }
    }

    private void goldRow(int id, String label, String[] p) {
        Double v = p == null ? null : field(p, 1);
        priceRow(id, label, v == null ? "—" : String.format(VI, "%.1f", v), 0xFFFFD54A,
                v == null ? null : field(p, 2), true, "triệu/lượng");
    }

    private void fuelRow(int id, String label, String raw, String product, int color) {
        String value = "—";
        Double delta = null;
        if (raw != null) {
            for (String line : raw.split("\n")) {
                String[] p = line.split("\t");
                if (p.length >= 3 && p[0].equals(product)) {
                    value = vnd(Double.parseDouble(p[1]));
                    delta = Double.parseDouble(p[2]);
                }
            }
        }
        priceRow(id, label, value, color, delta, false, "đồng/lít");
    }

    private void priceRow(int id, String label, String value, int color, Double delta, boolean decimal, String unit) {
        View row = findViewById(id);
        row.<TextView>findViewById(R.id.label).setText(label);
        TextView v = row.findViewById(R.id.value);
        v.setText(value);
        v.setTextColor(color);
        setChange(row.findViewById(R.id.change), delta, decimal);
        row.<TextView>findViewById(R.id.unit).setText(unit);
    }

    private void setChange(TextView view, Double delta, boolean decimal) {
        if (delta == null) {
            view.setText("—");
            view.setTextColor(FLAT);
            return;
        }
        boolean up = delta > 0.004;
        boolean down = delta < -0.004;
        if (!up && !down) {
            view.setText("±0");
            view.setTextColor(FLAT);
            return;
        }
        String arrow = up ? "▲ " : "▼ ";
        // Gold moves in steps of 50 thousand VND (0,05 triệu): keep up to two decimals so +0,05 is not +0,1.
        String number = decimal
                ? new DecimalFormat("+#,##0.0#;-#,##0.0#", DecimalFormatSymbols.getInstance(VI)).format(delta)
                : (up ? "+" : "-") + vnd(Math.abs(delta));
        SpannableStringBuilder s = new SpannableStringBuilder(arrow + number);
        s.setSpan(new ForegroundColorSpan(up ? UP : down ? DOWN : FLAT), 0, arrow.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setTextColor(Color.WHITE);
        view.setText(s);
    }

    private void updated(int headerId, long t) {
        TextView v = findViewById(headerId).findViewById(R.id.updated);
        if (t == 0) {
            v.setText("Chưa có dữ liệu");
            return;
        }
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(t);
        boolean today = then.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        v.setText("Cập nhật " + (today ? timeFormat : dayTimeFormat).format(new Date(t)));
    }

    private static String vnd(double v) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(VI)).format(Math.round(v));
    }

    // ---- music -----------------------------------------------------------------------------------------------

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            // When the player stops, the card moves on to the next song not heard yet.
            if (live() == null) selectFirstUnheard();
            renderMetadata();
            renderPlayback();
            renderQueue();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            Heard.mark(DashboardActivity.this, metadata);
            renderMetadata();
            renderQueue();
        }

        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            renderQueue();
        }

        @Override
        public void onSessionDestroyed() {
            connectMedia();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickSession;

    private void setupMusic() {
        sessions = getSystemService(MediaSessionManager.class);
        musicTitle = findViewById(R.id.music_title);
        musicArtist = findViewById(R.id.music_artist);
        musicPosition = findViewById(R.id.music_position);
        musicDuration = findViewById(R.id.music_duration);
        musicProgress = findViewById(R.id.music_progress);
        musicArt = findViewById(R.id.music_art);
        musicPlay = findViewById(R.id.music_play);
        musicArt.setClipToOutline(true);
        musicArt.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 10 * getResources().getDisplayMetrics().density);
            }
        });
        queueRows[0] = findViewById(R.id.queue_0);
        queueRows[1] = findViewById(R.id.queue_1);
        queueRows[2] = findViewById(R.id.queue_2);

        musicPlay.setOnClickListener(v -> {
            if (webOn()) {
                web.toggle();
                return;
            }
            if (live() == null) {
                // Nothing playing: start the trending list from the song on the card.
                if (!chart.isEmpty()) playChart(chart.get(chartSelected));
                else openMusicApp();
                return;
            }
            PlaybackState s = controller.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        });
        findViewById(R.id.music_prev).setOnClickListener(v -> {
            if (webOn()) web.prev();
            else if (live() != null) controller.getTransportControls().skipToPrevious();
            else stepChart(-1);
        });
        findViewById(R.id.music_next).setOnClickListener(v -> {
            if (webOn()) web.next();
            else if (live() != null) controller.getTransportControls().skipToNext();
            else stepChart(1);
        });
        findViewById(R.id.music_art_frame).setOnClickListener(v -> {
            if (webOn()) web.toggle();
            else if ((live() == null) && !chart.isEmpty()) playChart(chart.get(chartSelected));
            else openMusicApp();
        });
        View playlist = findViewById(R.id.music_playlist);
        playlist.setOnClickListener(v -> {
            if (showingChart()) {
                // The whole chart, to pick any song from #1 on.
                TopPicker.show(this, chart, Heard.ids(this), webOn() ? web.videoId : "", this::loadImage,
                        t -> {
                            chartSelected = chart.indexOf(t);
                            playChart(t, false);
                        });
            } else {
                openMusicApp();
            }
        });
        playlist.setOnFocusChangeListener((v, focused) -> {
            int c = focused ? 0xFF101820 : Color.WHITE;
            this.<TextView>findViewById(R.id.music_playlist_text).setTextColor(c);
            this.<ImageView>findViewById(R.id.music_playlist_icon).setColorFilter(c);
        });
        int[] controls = {R.id.music_prev, R.id.music_play, R.id.music_next};
        for (int id : controls) {
            View c = findViewById(id);
            c.setOnFocusChangeListener((v, f) -> scale(v, f ? 1.12f : 1f));
            // Up from the controls is the Top pill (left to itself, focus jumped to the Inputs button at the top).
            c.setNextFocusUpId(R.id.music_playlist);
        }
        playlist.setNextFocusDownId(R.id.music_play);
        renderMetadata();
        renderPlayback();
        renderQueue();
    }

    private void connectMedia() {
        disconnectMedia();
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, ui);
            mediaAllowed = true;
            pickSession(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            // Notification access not granted yet (see MediaListener).
            mediaAllowed = false;
            pickSession(null);
        }
    }

    private void disconnectMedia() {
        if (mediaAllowed) {
            try {
                sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (RuntimeException ignored) {
                // Already removed.
            }
        }
        if (controller != null) controller.unregisterCallback(mediaCallback);
        controller = null;
    }

    private void pickSession(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            // The card's own session is shown from the player itself.
            list = new java.util.ArrayList<>(list);
            list.removeIf(c -> c.getPackageName().equals(getPackageName()));
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    pick = c;
                    break;
                }
            }
            if (pick == null && !list.isEmpty()) pick = list.get(0);
        }
        if (controller != null && pick != null && controller.getSessionToken().equals(pick.getSessionToken())) return;
        if (controller != null) controller.unregisterCallback(mediaCallback);
        controller = pick;
        if (controller != null) controller.registerCallback(mediaCallback, ui);
        renderMetadata();
        renderPlayback();
        renderQueue();
    }

    private void renderMetadata() {
        if (webOn()) {
            DashData.Track t = track(web.videoId);
            musicTitle.setText(t != null ? t.title : web.title);
            musicArtist.setText(t != null ? "#" + t.position + " Thịnh hành tuần này · " + t.artists : web.author);
            musicDuration.setText(web.duration > 0 ? time((long) (web.duration * 1000)) : "--:--");
            musicArt.setVisibility(View.INVISIBLE);
            return;
        }
        musicArt.setVisibility(View.VISIBLE);
        MediaMetadata m = live();
        if (m == null && !chart.isEmpty()) {
            // Nothing playing: offer the trending song under the cursor (◀ ▶ move through the chart).
            DashData.Track t = chart.get(chartSelected);
            musicTitle.setText(t.title);
            musicArtist.setText("#" + t.position + " Thịnh hành tuần này · " + t.artists);
            musicDuration.setText("--:--");
            setArt(null, t.thumb.isEmpty() ? null : t.largeThumb());
            return;
        }
        if (m == null) {
            musicTitle.setText(mediaAllowed ? "Chưa phát nhạc" : "Cần quyền đọc nhạc đang phát");
            musicArtist.setText(mediaAllowed ? "Bấm ▶ để mở YouTube Music"
                    : "Bật quyền truy cập thông báo cho Home Dash");
            musicDuration.setText("00:00");
            setArt(null, null);
            return;
        }
        String title = text(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = text(m, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        String album = text(m, MediaMetadata.METADATA_KEY_ALBUM, null);
        musicTitle.setText(title.isEmpty() ? appLabel(controller.getPackageName()) : title);
        musicArtist.setText(album.isEmpty() || album.equals(artist) ? artist
                : artist.isEmpty() ? album : artist + " · " + album);
        long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        musicDuration.setText(dur > 0 ? time(dur) : "--:--");
        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        String uri = text(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (uri.isEmpty()) uri = text(m, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, null);
        setArt(art, uri.isEmpty() ? null : uri);
    }

    private void setArt(Bitmap bitmap, String uri) {
        if (bitmap != null) {
            artKey = null;
            musicArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            musicArt.setImageBitmap(bitmap);
        } else if (uri != null) {
            if (uri.equals(artKey)) return;
            artKey = uri;
            loadImage(uri, musicArt, 400);
        } else {
            artKey = null;
            musicArt.setScaleType(ImageView.ScaleType.CENTER);
            musicArt.setImageResource(R.drawable.ic_note);
        }
    }

    private void renderPlayback() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        boolean playing = webOn() ? web.playing : s != null && s.getState() == PlaybackState.STATE_PLAYING;
        musicPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        updateProgress();
    }

    private void updateProgress() {
        if (musicProgress == null) return;
        if (webOn()) {
            long pos = (long) (web.time * 1000);
            long dur = (long) (web.duration * 1000);
            musicPosition.setText(time(pos));
            musicDuration.setText(dur > 0 ? time(dur) : "--:--");
            musicProgress.setProgress(dur > 0 ? (int) (pos * 1000 / dur) : 0);
            return;
        }
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (s == null || s.getPosition() < 0) {
            musicPosition.setText("00:00");
            musicProgress.setProgress(0);
            return;
        }
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        if (dur > 0) pos = Math.min(pos, dur);
        musicPosition.setText(time(pos));
        musicProgress.setProgress(dur > 0 ? (int) (pos * 1000 / dur) : 0);
    }

    /** The right-hand list shows the trending chart unless the playing app shares a queue of its own. */
    private boolean showingChart() {
        List<MediaSession.QueueItem> q = controller == null ? null : controller.getQueue();
        return (q == null || q.isEmpty()) && !chart.isEmpty();
    }

    /** The song the list last turned to, so it turns only when the song changes and the Top pill still pages. */
    private String followedId = "";

    private void renderChartRows() {
        if (webOn() && !web.videoId.equals(followedId)) {
            followedId = web.videoId;
            DashData.Track cur = track(web.videoId);
            if (cur != null) chartPage = chart.indexOf(cur) / queueRows.length;
        }
        int base = chartPage * queueRows.length;
        MediaMetadata m = live();
        DashData.Track now = webOn() ? track(web.videoId) : m == null ? null
                : DashData.match(chart, text(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        this.<TextView>findViewById(R.id.music_playlist_text).setText("Top " + chart.size());
        for (int i = 0; i < queueRows.length; i++) {
            View row = queueRows[i];
            int idx = base + i;
            if (idx >= chart.size()) {
                row.setVisibility(View.INVISIBLE);
                continue;
            }
            DashData.Track t = chart.get(idx);
            TextView rank = row.findViewById(R.id.rank);
            rank.setVisibility(View.VISIBLE);
            rank.setText(String.valueOf(t.position));
            row.<TextView>findViewById(R.id.title).setText(t.title);
            String move = t.previous == 0 ? "MỚI" : t.previous > t.position ? "▲" + (t.previous - t.position)
                    : t.previous < t.position ? "▼" + (t.position - t.previous) : "=";
            row.<TextView>findViewById(R.id.subtitle).setText(move + " · " + t.artists);
            ImageView thumb = row.findViewById(R.id.thumb);
            thumb.setClipToOutline(true);
            if (!t.thumb.isEmpty()) loadImage(t.thumb, thumb, 96);
            row.setActivated(now != null ? now == t : m == null && !webOn() && idx == chartSelected);
            row.setOnClickListener(v -> {
                chartSelected = idx;
                int at = playIds.indexOf(t.videoId);
                if (webOn() && at >= 0) web.at(at);
                else playChart(t, false);
            });
            row.setVisibility(View.VISIBLE);
        }
    }

    private void renderQueue() {
        if (showingChart()) {
            renderChartRows();
            return;
        }
        this.<TextView>findViewById(R.id.music_playlist_text).setText("Danh sách phát");
        for (View row : queueRows) row.findViewById(R.id.rank).setVisibility(View.GONE);
        List<MediaSession.QueueItem> queue = controller == null ? null : controller.getQueue();
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        long active = s == null ? MediaSession.QueueItem.UNKNOWN_ID : s.getActiveQueueItemId();
        int start = 0;
        if (queue != null) {
            for (int i = 0; i < queue.size(); i++) if (queue.get(i).getQueueId() == active) start = i;
        }
        for (int i = 0; i < queueRows.length; i++) {
            View row = queueRows[i];
            TextView title = row.findViewById(R.id.title);
            TextView sub = row.findViewById(R.id.subtitle);
            ImageView thumb = row.findViewById(R.id.thumb);
            thumb.setClipToOutline(true);
            if (queue != null && start + i < queue.size()) {
                MediaSession.QueueItem item = queue.get(start + i);
                title.setText(cs(item.getDescription().getTitle()));
                sub.setText(cs(item.getDescription().getSubtitle()));
                Bitmap icon = item.getDescription().getIconBitmap();
                Uri iconUri = item.getDescription().getIconUri();
                if (icon != null) thumb.setImageBitmap(icon);
                else if (iconUri != null) loadImage(iconUri.toString(), thumb, 96);
                else thumb.setImageResource(R.drawable.ic_note);
                row.setActivated(item.getQueueId() == active);
                long id = item.getQueueId();
                row.setOnClickListener(v -> {
                    if (controller != null) controller.getTransportControls().skipToQueueItem(id);
                });
                row.setVisibility(View.VISIBLE);
            } else if (i == 0) {
                // No shared queue: the first row shows what is playing, or how to start.
                MediaMetadata m = live();
                title.setText(m == null ? "Danh sách phát" : musicTitle.getText());
                sub.setText(m == null ? "Hiện khi app có chia sẻ" : appLabel(controller.getPackageName()));
                thumb.setImageDrawable(musicArt.getDrawable());
                row.setActivated(m != null);
                row.setOnClickListener(v -> openMusicApp());
                row.setVisibility(View.VISIBLE);
            } else {
                row.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void openMusicApp() {
        launch(controller != null ? controller.getPackageName() : getString(R.string.pkg_music));
    }

    private void stepChart(int delta) {
        if (chart.isEmpty()) return;
        int n = chart.size();
        chartSelected = ((chartSelected + delta) % n + n) % n;
        chartPage = chartSelected / queueRows.length;
        renderMetadata();
        renderQueue();
    }

    /**
     * Plays the chart from this song on in the card's own player: up to LIST_SIZE songs in chart order, one after
     * another without a gap. Songs heard from the chart in the last week are left out; once nearly all of them are,
     * the history starts over. Only ever started by a press (on-screen ▶ or the remote's Play key): nothing plays on
     * its own when the TV comes on.
     */
    private void playChart(DashData.Track start) {
        playChart(start, true);
    }

    /**
     * skipHeard: the Play key and the end of a list leave out songs heard this week; a song picked from the list
     * plays on in plain chart order, heard or not.
     */
    private void playChart(DashData.Track start, boolean skipHeard) {
        if (chart.isEmpty()) return;
        Set<String> heard = skipHeard ? Heard.ids(this) : new HashSet<>();
        List<String> ids = chartList(start, heard);
        if (skipHeard && ids.size() < 10) {
            Heard.clear(this);
            ids = chartList(start, new HashSet<>());
        }
        Log.i(TAG, "trending list from #" + start.position + ": " + ids.size() + " songs, " + heard.size() + " heard skipped");
        Heard.playing(this, ids);
        playIds = ids;
        if (web == null) {
            web = new WebPlayer(this, findViewById(R.id.music_art_frame), 10 * getResources().getDisplayMetrics().density,
                    this::onWebChange);
        }
        web.load(ids);
        toast("Đang phát " + ids.size() + " bài thịnh hành");
        renderMetadata();
        renderPlayback();
        renderQueue();
    }

    /** The list loaded into the card's player, for the row clicks to jump within it. */
    private List<String> playIds = new java.util.ArrayList<>();

    private boolean webOn() {
        return web != null && web.active;
    }

    private DashData.Track track(String videoId) {
        for (DashData.Track t : chart) if (t.videoId.equals(videoId)) return t;
        return null;
    }

    /** The card's player changed: note the song, keep the TV awake while it plays, carry on when the list ends. */
    private void onWebChange(WebPlayer p) {
        if (p.playing) {
            DashData.Track t = Heard.markId(this, track(p.videoId));
            if (t != null) Log.i(TAG, "heard #" + t.position + " " + t.videoId + " (card player)");
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (!p.active) {
            // The list ran out: the next songs not heard yet, so the music neither stops nor repeats.
            p.stop();
            selectFirstUnheard();
            if (!chart.isEmpty()) playChart(chart.get(chartSelected));
            updateSession();
            return;
        }
        renderMetadata();
        renderPlayback();
        renderQueue();
        updateSession();
    }

    private void setupSession() {
        session = new MediaSession(this, "HomeDash");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                Log.i(TAG, "session: play");
                if (webOn()) web.play();
                else if (!chart.isEmpty()) playChart(chart.get(chartSelected));
            }

            @Override
            public void onPause() {
                Log.i(TAG, "session: pause");
                if (webOn()) web.pause();
            }

            @Override
            public void onStop() {
                onPause();
            }

            @Override
            public void onSkipToNext() {
                Log.i(TAG, "session: next");
                if (webOn()) web.next();
            }

            @Override
            public void onSkipToPrevious() {
                Log.i(TAG, "session: previous");
                if (webOn()) web.prev();
            }
        }, ui);
    }

    /** Tells Android (and so the Assistant) what the card plays: song, artist, playing or paused. */
    private void updateSession() {
        if (!webOn()) {
            if (session.isActive()) session.setActive(false);
            return;
        }
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, web.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, web.author)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, (long) (web.duration * 1000))
                .build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(web.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        (long) (web.time * 1000), web.playing ? 1f : 0f, SystemClock.elapsedRealtime())
                .build());
        if (!session.isActive()) session.setActive(true);
    }

    // ---- voice ---------------------------------------------------------------------------------------------

    private static final int MIC_PERMISSION = 8;
    /** Google TV's own recogniser, the one its search uses: it switches the remote's microphone on. */
    private static final ComponentName KATNISS_RECOGNIZER = new ComponentName("com.google.android.katniss",
            "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService");
    private static final String HINT = "Nói: mở nhạc, dừng nhạc, phát tiếp, qua bài, mở nhạc top 1, giá vàng…";
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSay;
    /** The card was playing when Home Dash began to listen or speak: it plays on afterwards. */
    private boolean resumeAfter;
    /** The recogniser is listening. */
    private boolean listening;
    /** OK is being held to listen: the rest of that press is not a click. */
    private boolean okHeld;
    private android.speech.SpeechRecognizer recognizer;
    /** What is being heard, over the top of the screen while listening. */
    private TextView voiceBubble;

    /**
     * Listens in Home Dash itself, with Google TV's recogniser. Google's voice screen (RECOGNIZE_SPEECH) was tried
     * first: on the MiTV-MOEU0 it heard the words but never handed them back.
     */
    private void listen() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            return;
        }
        if (listening) {
            recognizer.stopListening();
            return;
        }
        if (recognizer == null) {
            boolean katniss = !getPackageManager().queryIntentServices(
                    new Intent(android.speech.RecognitionService.SERVICE_INTERFACE).setComponent(KATNISS_RECOGNIZER), 0).isEmpty();
            recognizer = katniss ? android.speech.SpeechRecognizer.createSpeechRecognizer(this, KATNISS_RECOGNIZER)
                    : android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            Log.i(TAG, "recogniser: " + (katniss ? "katniss" : "default"));
            recognizer.setRecognitionListener(new VoiceListener());
        }
        holdMusic();
        listening = true;
        lastPartial = null;
        ui.removeCallbacks(giveUp);
        ui.removeCallbacks(quiet);
        ui.postDelayed(quiet, 15_000);
        bubble("🎤 Đang mở mic…\n" + HINT);
        recognizer.startListening(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == MIC_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            listen();
        } else if (requestCode == MIC_PERMISSION) {
            toast("Cần quyền micro để ra lệnh bằng giọng nói");
        }
    }

    /** The words heard so far, from the recogniser's partial results. */
    private String lastPartial;

    /**
     * Nothing new heard for a while: the recogniser is asked to finish. Google TV's recogniser does not always end on
     * its own, so if no final result follows, what was heard so far is taken as the command.
     */
    private final Runnable quiet = () -> {
        if (!listening) return;
        if (lastPartial == null) {
            giveUp(true);
            return;
        }
        Log.i(TAG, "voice: quiet, finishing with \"" + lastPartial + "\"");
        recognizer.stopListening();
        ui.postDelayed(this.giveUp, 3_000);
    };

    private final Runnable giveUp = () -> giveUp(true);

    private void giveUp(boolean act) {
        if (!listening) return;
        ui.removeCallbacks(quiet);
        ui.removeCallbacks(giveUp);
        recognizer.cancel();
        listening = false;
        bubble(null);
        if (!act) return;
        if (lastPartial != null) {
            command(java.util.Collections.singletonList(lastPartial));
        } else {
            toast("Mình chưa nghe rõ, bạn giữ OK rồi nói lại nhé");
            releaseMusic();
        }
    }

    private final class VoiceListener implements android.speech.RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.i(TAG, "voice: ready");
            bubble("🎤 Mời bạn nói…\n" + HINT);
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            Log.i(TAG, "voice: end of speech");
        }

        @Override
        public void onError(int error) {
            Log.w(TAG, "voice: error " + error);
            if (!listening) return;
            if (lastPartial != null) {
                // Words were heard before the error: act on them.
                giveUp(true);
                return;
            }
            ui.removeCallbacks(quiet);
            ui.removeCallbacks(giveUp);
            listening = false;
            bubble(null);
            boolean silent = error == android.speech.SpeechRecognizer.ERROR_NO_MATCH
                    || error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
            toast(silent ? "Mình chưa nghe rõ, bạn giữ OK rồi nói lại nhé" : "Không nhận được giọng nói (lỗi " + error + ")");
            releaseMusic();
        }

        @Override
        public void onResults(Bundle results) {
            if (!listening) return;
            ui.removeCallbacks(quiet);
            ui.removeCallbacks(giveUp);
            listening = false;
            bubble(null);
            List<String> heard = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            Log.i(TAG, "heard: " + heard);
            if ((heard == null || heard.isEmpty()) && lastPartial != null) heard = java.util.Collections.singletonList(lastPartial);
            if (heard == null || heard.isEmpty()) releaseMusic();
            else command(heard);
        }

        @Override
        public void onPartialResults(Bundle partial) {
            List<String> p = partial.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            if (p == null || p.isEmpty() || p.get(0).trim().isEmpty()) return;
            lastPartial = p.get(0);
            Log.i(TAG, "voice: partial \"" + lastPartial + "\"");
            bubble("🎤 " + lastPartial);
            // Finish once the words stop coming.
            ui.removeCallbacks(quiet);
            ui.postDelayed(quiet, 2_500);
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }

    /** Shows text in a bubble at the top of the screen; null hides it. */
    private void bubble(String text) {
        if (voiceBubble == null) {
            voiceBubble = new TextView(this);
            voiceBubble.setBackgroundResource(R.drawable.picker_bg);
            voiceBubble.setTextColor(Color.WHITE);
            voiceBubble.setTextSize(18);
            voiceBubble.setGravity(android.view.Gravity.CENTER);
            int pad = (int) (14 * getResources().getDisplayMetrics().density);
            voiceBubble.setPadding(pad * 2, pad, pad * 2, pad);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
            lp.topMargin = pad * 4;
            this.<android.widget.FrameLayout>findViewById(R.id.root).addView(voiceBubble, lp);
        }
        voiceBubble.setVisibility(text == null ? View.GONE : View.VISIBLE);
        if (text != null) voiceBubble.setText(text);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSearch(intent);
    }

    /**
     * A query sent to Home Dash: by the Assistant ("tìm giá vàng trên Home Dash"), or for testing with
     * `am start -a android.intent.action.SEARCH -n vn.homedash.tv/.DashboardActivity --es query "giá vàng"`.
     */
    private void handleSearch(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String q = intent.getStringExtra(android.app.SearchManager.QUERY);
        Log.i(TAG, "search intent " + intent.getAction() + ": " + q);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) return;
        // "Play something" with no name: the song the card has ready.
        if ((q == null || q.trim().isEmpty()) && "android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(intent.getAction())) {
            q = "mở nhạc";
        }
        if (q == null || q.trim().isEmpty()) return;
        intent.removeExtra(android.app.SearchManager.QUERY);
        if (webOn() && web.playing) resumeAfter = true;
        command(java.util.Collections.singletonList(q));
    }

    /** Runs the first of the recogniser's guesses that is a command. */
    private void command(List<String> heard) {
        Voice.Command c = null;
        String said = heard.get(0);
        for (String h : heard) {
            Voice.Command k = Voice.parse(h);
            if (k.kind != Voice.Kind.UNKNOWN) {
                c = k;
                said = h;
                break;
            }
        }
        toast("🎤 " + said);
        if (c == null) {
            say("Mình chưa hiểu: " + said + ". Bạn thử nói giá vàng, tỷ giá, giá xăng, thời tiết, hoặc phát top 1.");
            return;
        }
        Log.i(TAG, "voice command " + c.kind + " from \"" + said + "\"");
        Calendar now = Calendar.getInstance();
        switch (c.kind) {
            case GOLD:
                say(Speech.gold(prefs.getString("raw_" + GOLD, null)));
                fetch(GOLD, false);
                break;
            case USD:
                say(Speech.usd(prefs.getString("raw_" + FX, null)));
                fetch(FX, false);
                break;
            case FUEL:
                say(Speech.fuel(prefs.getString("raw_" + FUEL, null)));
                fetch(FUEL, false);
                break;
            case WEATHER: {
                String raw = prefs.getString("raw_" + WEATHER, null);
                say(Speech.weather(config.city(), raw == null ? null : DashData.parseWeather(raw)));
                break;
            }
            case TIME:
                say(Speech.time(now));
                break;
            case DATE:
                say(Speech.date(now));
                break;
            case PAUSE:
                resumeAfter = false;
                if (webOn()) web.pause();
                break;
            case PLAY:
                // "Mở nhạc" / "phát tiếp": a paused song goes on where it stopped; with nothing loaded, the song the
                // card has ready starts.
                resumeAfter = false;
                if (webOn()) web.play();
                else if (!chart.isEmpty()) playChart(chart.get(chartSelected));
                break;
            case NEXT:
                resumeAfter = false;
                if (webOn()) web.next();
                else if (!chart.isEmpty()) playChart(chart.get(chartSelected));
                break;
            case PREV:
                resumeAfter = false;
                if (webOn()) web.prev();
                break;
            case TOP:
                if (c.n > chart.size()) {
                    say("Top thịnh hành chỉ có " + chart.size() + " bài.");
                } else {
                    // "Mở nhạc top 3": whatever song is #3 in this week's chart, then on down the chart.
                    resumeAfter = false;
                    DashData.Track t = chart.get(c.n - 1);
                    playChart(t, false);
                    toast("Top " + c.n + ": " + t.title + " · " + t.artists);
                }
                break;
            case SONG: {
                DashData.Track t = Voice.findSong(chart, c.query);
                if (t == null) {
                    say("Không thấy bài " + c.query + " trong top " + chart.size() + " thịnh hành.");
                } else {
                    resumeAfter = false;
                    playChart(t, false);
                }
                break;
            }
            case YOUTUBE:
                resumeAfter = false;
                launch(getString(R.string.pkg_youtube));
                break;
            case NETFLIX:
                resumeAfter = false;
                launch(getString(R.string.pkg_netflix));
                break;
            case STORE:
                resumeAfter = false;
                launch(getString(R.string.pkg_store));
                break;
            case APPS:
                resumeAfter = false;
                openAppGrid();
                break;
            default:
                break;
        }
        releaseMusic();
    }

    /** Reads text aloud in Vietnamese with Google's voice; the card's music pauses meanwhile. */
    private void say(String text) {
        Log.i(TAG, "say: " + text);
        pendingSay = text;
        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                if (status != TextToSpeech.SUCCESS) {
                    toast("Không mở được giọng đọc");
                    return;
                }
                int r = tts.setLanguage(VI);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    toast("TV chưa có giọng đọc tiếng Việt");
                }
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        ui.post(DashboardActivity.this::releaseMusic);
                    }

                    @Override
                    public void onError(String id) {
                        ui.post(DashboardActivity.this::releaseMusic);
                    }
                });
                ttsReady = true;
                speakPending();
            }, "com.google.android.tts");
            return;
        }
        if (ttsReady) speakPending();
    }

    private void speakPending() {
        if (pendingSay == null) return;
        holdMusic();
        tts.speak(pendingSay, TextToSpeech.QUEUE_FLUSH, null, "dash");
        pendingSay = null;
    }

    private boolean speaking() {
        return pendingSay != null || (tts != null && tts.isSpeaking());
    }

    private void holdMusic() {
        if (webOn() && web.playing) {
            resumeAfter = true;
            web.pause();
        }
    }

    /** Once nothing is listening or speaking any more, the music that was playing goes on. */
    private void releaseMusic() {
        if (listening || speaking() || !resumeAfter) return;
        resumeAfter = false;
        if (webOn()) web.play();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        int k = e.getKeyCode();
        if (k == android.view.KeyEvent.KEYCODE_DPAD_CENTER || k == android.view.KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) okHeld = false;
            if (!okHeld && e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.isLongPress()) {
                // Holding OK opens the voice screen; the button under focus is not clicked.
                okHeld = true;
                View f = getCurrentFocus();
                if (f != null) f.setPressed(false);
                listen();
                return true;
            }
            if (okHeld) return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private List<String> chartList(DashData.Track start, Set<String> skip) {
        List<String> ids = new java.util.ArrayList<>();
        ids.add(start.videoId);
        int at = Math.max(0, chart.indexOf(start));
        for (int k = 1; k < chart.size() && ids.size() < LIST_SIZE; k++) {
            DashData.Track t = chart.get((at + k) % chart.size());
            if (!skip.contains(t.videoId) && !ids.contains(t.videoId)) ids.add(t.videoId);
        }
        return ids;
    }

    /**
     * What is playing, or null: a player that stopped (YouTube keeps its last song's metadata with state NONE after
     * Home) counts as nothing playing, so the card goes back to the trending list.
     */
    private MediaMetadata live() {
        if (controller == null) return null;
        PlaybackState s = controller.getPlaybackState();
        if (s == null) return null;
        switch (s.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return controller.getMetadata();
            default:
                return null;
        }
    }

    /** While nothing plays, the card offers the first chart song not heard this week. */
    private void selectFirstUnheard() {
        if (chart.isEmpty() || live() != null || webOn()) return;
        Set<String> heard = Heard.ids(this);
        for (int i = 0; i < chart.size(); i++) {
            if (!heard.contains(chart.get(i).videoId)) {
                chartSelected = i;
                chartPage = i / queueRows.length;
                return;
            }
        }
        chartSelected = 0;
        chartPage = 0;
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        // The remote's Play key while nothing is playing: start the trending list. While a player has a session,
        // Android sends media keys to it, not here.
        boolean play = keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if (webOn()) {
            switch (keyCode) {
                case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    web.toggle();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:
                    web.play();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:
                case android.view.KeyEvent.KEYCODE_MEDIA_STOP:
                    web.pause();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:
                    web.next();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    web.prev();
                    return true;
                default:
                    break;
            }
        }
        if (play && (live() == null) && !chart.isEmpty()) {
            playChart(chart.get(chartSelected));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private static String text(MediaMetadata m, String key, String fallback) {
        CharSequence v = m.getText(key);
        if ((v == null || v.length() == 0) && fallback != null) v = m.getText(fallback);
        return v == null ? "" : v.toString();
    }

    private static String cs(CharSequence c) {
        return c == null ? "" : c.toString();
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private void loadImage(String uri, ImageView target, int maxPx) {
        target.setTag(uri);
        img.execute(() -> {
            Bitmap b = decode(uri, maxPx);
            ui.post(() -> {
                if (!uri.equals(target.getTag())) return;
                if (b != null) {
                    target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    target.setImageBitmap(b);
                } else {
                    target.setScaleType(ImageView.ScaleType.CENTER);
                    target.setImageResource(R.drawable.ic_note);
                    coverFailed = true;
                }
                if (target == musicArt) renderQueue();
            });
        });
    }

    private Bitmap decode(String uri, int maxPx) {
        try {
            byte[] data;
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                try (InputStream in = c.getInputStream()) {
                    data = readAll(in);
                } finally {
                    c.disconnect();
                }
            } else {
                try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
                    if (in == null) return null;
                    data = readAll(in);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            o.inSampleSize = 1;
            while (Math.max(o.outWidth, o.outHeight) / (o.inSampleSize * 2) >= maxPx) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "art: " + e);
            return null;
        }
    }

    /** InputStream.readAllBytes is Android 13+; covers can be large, so cap them at 4 MB. */
    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > 4_000_000) throw new IOException("image too large");
        }
        return out.toByteArray();
    }

    // ---- buttons, wallpaper, helpers -------------------------------------------------------------------------

    private void iconButton(int id, View.OnClickListener click) {
        ImageButton b = findViewById(id);
        b.setOnClickListener(click);
        b.setOnFocusChangeListener((v, focused) -> b.setColorFilter(focused ? 0xFF101820 : Color.WHITE));
    }

    private void appButton(int id, int background, int icon, String label, View.OnClickListener click) {
        View root = findViewById(id);
        View button = root.findViewById(R.id.button);
        Reflection reflection = root.findViewById(R.id.reflection);
        button.setBackgroundResource(background);
        root.<ImageView>findViewById(R.id.icon).setImageResource(icon);
        root.<TextView>findViewById(R.id.label).setText(label);
        button.setOnClickListener(click);
        button.setOnFocusChangeListener((v, focused) -> {
            scale(v, focused ? 1.05f : 1f);
            reflection.invalidate();
        });
        button.setNextFocusUpId(R.id.music_play);
        reflection.setTarget(button);
        button.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> reflection.invalidate());
    }

    private static void scale(View v, float s) {
        // A view grown past its parent is cut off at the parent's edge (the focus ring lost its sides and top), so
        // every ancestor lets its children draw outside.
        for (android.view.ViewParent p = v.getParent(); p instanceof android.view.ViewGroup; p = p.getParent()) {
            ((android.view.ViewGroup) p).setClipChildren(false);
            ((android.view.ViewGroup) p).setClipToPadding(false);
        }
        v.animate().scaleX(s).scaleY(s).setDuration(120).start();
    }

    private void loadWallpaper() {
        File dir = getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, "wallpaper.jpg");
        if (f == null || !f.isFile()) return;
        int width = getResources().getDisplayMetrics().widthPixels;
        ImageView view = findViewById(R.id.wallpaper);
        img.execute(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= width) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b != null) ui.post(() -> view.setImageBitmap(b));
        });
    }

    private void updateWifi() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        wifi.setAlpha(online ? 1f : 0.4f);
    }

    private void openInputs() {
        Intent inputs = new Intent("com.android.tv.action.VIEW_INPUTS");
        if (installed(TVHOME) && start(new Intent(inputs).setComponent(new ComponentName(TVHOME, TVHOME_MAIN)))) return;
        // Without CleanYourTV: whatever on this TV answers the standard Inputs action (the stock launcher on most
        // Google TV builds), else the TV's settings.
        if (inputs.resolveActivity(getPackageManager()) == null || !start(inputs)) start(new Intent(Settings.ACTION_SETTINGS));
    }

    private void openAppGrid() {
        if (installed(TVHOME) && start(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(TVHOME, TVHOME_MAIN)))) {
            return;
        }
        AppsDialog.show(this, this::launch);
    }

    private boolean installed(String pkg) {
        try {
            return getPackageManager().getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void launch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            toast("Chưa cài hoặc đang tắt: " + appLabel(pkg));
            return;
        }
        start(i);
    }

    private boolean start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("Không mở được");
            return false;
        }
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
