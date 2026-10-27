package vn.homedash.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** "Tất cả app" on a TV without the CleanYourTV launcher: every app with a TV (or phone) launcher entry, A to Z. */
final class AppsDialog {
    interface Launch {
        void launch(String pkg);
    }

    private static final class App {
        final String pkg;
        final String label;
        final ResolveInfo info;

        App(String pkg, String label, ResolveInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.info = info;
        }
    }

    private AppsDialog() {
    }

    static void show(Activity activity, Launch launch) {
        PackageManager pm = activity.getPackageManager();
        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(activity.getPackageName());
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = r.activityInfo.packageName;
                if (seen.add(pkg)) apps.add(new App(pkg, r.loadLabel(pm).toString(), r));
            }
        }
        Collator vi = Collator.getInstance(new Locale("vi", "VN"));
        apps.sort((a, b) -> vi.compare(a.label, b.label));

        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.dialog_top);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.45f);
        }
        d.<TextView>findViewById(R.id.title).setText("Tất cả app");
        d.<TextView>findViewById(R.id.hint).setText(apps.size() + " app · OK để mở, Back để đóng");
        ListView list = d.findViewById(R.id.list);
        list.setItemsCanFocus(false);
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return apps.size();
            }

            @Override
            public Object getItem(int i) {
                return apps.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View row, ViewGroup parent) {
                if (row == null) row = activity.getLayoutInflater().inflate(R.layout.row_pick, parent, false);
                App a = apps.get(i);
                row.findViewById(R.id.rank).setVisibility(View.GONE);
                ImageView thumb = row.findViewById(R.id.thumb);
                thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                thumb.setImageDrawable(a.info.loadIcon(pm));
                row.<TextView>findViewById(R.id.title).setText(a.label);
                row.<TextView>findViewById(R.id.subtitle).setText(a.pkg);
                row.findViewById(R.id.heard).setVisibility(View.GONE);
                return row;
            }
        });
        list.setOnItemClickListener((parent, view, i, id) -> {
            d.dismiss();
            launch.launch(apps.get(i).pkg);
        });
        d.show();
        list.requestFocus();
    }
}
