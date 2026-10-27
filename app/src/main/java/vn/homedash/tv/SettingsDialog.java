package vn.homedash.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Settings, from the gear on Home: the greeting, the place for the weather, and the TV's own settings. Everything a
 * household changes here is stored on the TV (Config), so nobody has to rebuild the app.
 */
final class SettingsDialog {
    interface Changed {
        void onChanged();
    }

    /** Rows shown before the list scrolls; each row is 48dp plus a 3dp gap. */
    private static final int VISIBLE_ROWS = 7;

    private final Activity activity;
    private final Config config;
    private final ExecutorService io;
    private final Changed changed;
    private final Handler ui = new Handler(Looper.getMainLooper());

    SettingsDialog(Activity activity, Config config, ExecutorService io, Changed changed) {
        this.activity = activity;
        this.config = config;
        this.io = io;
        this.changed = changed;
    }

    void show() {
        String[] titles = {"Lời chào", "Thời tiết ở", "Cài đặt của TV", "Phiên bản"};
        Dialog d = list("Cài đặt Home Dash", "Lên/xuống để chọn, OK để đổi, Back để đóng", titles.length,
                (i, title, value) -> {
                    title.setText(titles[i]);
                    switch (i) {
                        case 0:
                            value.setText(config.greeting());
                            break;
                        case 1:
                            value.setText(config.city());
                            break;
                        case 2:
                            value.setText("Wi-Fi, âm thanh, hình ảnh…");
                            break;
                        default:
                            // Open-Meteo's data is CC BY 4.0: credit it where the household can see it.
                            value.setText(version() + " · thời tiết: Open-Meteo.com");
                            break;
                    }
                }, null);
        ListView list = d.findViewById(R.id.list);
        list.setOnItemClickListener((parent, view, i, id) -> {
            switch (i) {
                case 0:
                    text("Lời chào trên Home", config.greeting(), "Ví dụ: Chào cả nhà!", s -> {
                        config.setGreeting(s);
                        changed.onChanged();
                        refresh(list);
                    });
                    break;
                case 1:
                    pickPlace(() -> refresh(list));
                    break;
                case 2:
                    d.dismiss();
                    start(new Intent(Settings.ACTION_SETTINGS));
                    break;
                default:
                    break;
            }
        });
        d.show();
    }

    private void pickPlace(Runnable done) {
        int n = Config.PLACES.length + 1;
        Dialog[] holder = new Dialog[1];
        holder[0] = list("Xem thời tiết ở đâu?", "34 tỉnh, thành theo nơi đặt trụ sở. Nơi khác: chọn dòng đầu tiên.", n,
                (i, title, value) -> {
                    if (i == 0) {
                        title.setText("Tìm nơi khác…");
                        value.setText("huyện, thị xã, phường…");
                    } else {
                        Config.Place p = Config.PLACES[i - 1];
                        title.setText(p.name);
                        value.setText(p.region == null ? "" : p.region);
                    }
                }, (i) -> {
                    if (i == 0) {
                        search(p -> {
                            holder[0].dismiss();
                            savePlace(p, done);
                        });
                    } else {
                        holder[0].dismiss();
                        savePlace(Config.PLACES[i - 1], done);
                    }
                });
        holder[0].show();
    }

    private void savePlace(Config.Place p, Runnable done) {
        config.setPlace(p);
        changed.onChanged();
        done.run();
        toast("Thời tiết: " + p.name);
    }

    private void search(PlacePick pick) {
        text("Tìm nơi xem thời tiết", "", "Ví dụ: Bảo Lộc, Phú Quốc, Sa Pa", q -> {
            if (q.trim().length() < 2) return;
            toast("Đang tìm " + q.trim() + "…");
            io.execute(() -> {
                List<Config.Place> found;
                try {
                    found = Config.parseSearch(DashData.get(Config.searchUrl(q)));
                } catch (IOException | RuntimeException e) {
                    found = null;
                }
                List<Config.Place> result = found;
                ui.post(() -> {
                    if (activity.isFinishing()) return;
                    if (result == null) toast("Không có mạng, thử lại sau");
                    else if (result.isEmpty()) toast("Không tìm thấy " + q.trim());
                    else choose(result, pick);
                });
            });
        });
    }

    private void choose(List<Config.Place> places, PlacePick pick) {
        Dialog[] holder = new Dialog[1];
        holder[0] = list("Chọn đúng nơi", "Cùng tên có thể ở nhiều tỉnh: xem tên tỉnh bên phải", places.size(),
                (i, title, value) -> {
                    title.setText(places.get(i).name);
                    value.setText(places.get(i).region == null ? "" : places.get(i).region);
                }, i -> {
                    holder[0].dismiss();
                    pick.onPick(places.get(i));
                });
        holder[0].show();
    }

    // ---- building blocks ---------------------------------------------------------------------------------------

    private interface Row {
        void bind(int i, TextView title, TextView value);
    }

    private interface Click {
        void onClick(int i);
    }

    private interface PlacePick {
        void onPick(Config.Place p);
    }

    private interface Typed {
        void onText(String s);
    }

    private Dialog list(String title, String hint, int count, Row row, Click click) {
        Dialog d = dialog(R.layout.dialog_settings);
        d.<TextView>findViewById(R.id.title).setText(title);
        d.<TextView>findViewById(R.id.hint).setText(hint);
        ListView list = d.findViewById(R.id.list);
        list.setItemsCanFocus(false);
        if (count > VISIBLE_ROWS) {
            float dp = activity.getResources().getDisplayMetrics().density;
            list.getLayoutParams().height = Math.round(VISIBLE_ROWS * 51 * dp);
        }
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return count;
            }

            @Override
            public Object getItem(int i) {
                return i;
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View view, ViewGroup parent) {
                if (view == null) view = activity.getLayoutInflater().inflate(R.layout.row_setting, parent, false);
                row.bind(i, view.findViewById(R.id.title), view.findViewById(R.id.value));
                return view;
            }
        });
        if (click != null) list.setOnItemClickListener((parent, view, i, id) -> click.onClick(i));
        list.requestFocus();
        return d;
    }

    private void text(String title, String current, String hint, Typed typed) {
        Dialog d = dialog(R.layout.dialog_text);
        d.<TextView>findViewById(R.id.title).setText(title);
        EditText input = d.findViewById(R.id.input);
        input.setText(current);
        input.setHint(hint);
        input.setSelection(input.getText().length());
        Runnable save = () -> {
            d.dismiss();
            typed.onText(input.getText().toString());
        };
        input.setOnEditorActionListener((v, action, e) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            save.run();
            return true;
        });
        d.findViewById(R.id.ok).setOnClickListener(v -> save.run());
        d.show();
        input.requestFocus();
    }

    private Dialog dialog(int layout) {
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(layout);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.6f);
        }
        return d;
    }

    private void refresh(ListView list) {
        ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
    }

    private String version() {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private void start(Intent i) {
        try {
            activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            toast("Không mở được");
        }
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }
}
