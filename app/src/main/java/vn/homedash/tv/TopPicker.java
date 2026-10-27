package vn.homedash.tv;

import android.app.Activity;
import android.app.Dialog;
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

import java.util.List;
import java.util.Set;

/** The whole weekly chart in a list, so any song from #1 on can be picked and played. */
final class TopPicker {
    interface Images {
        void load(String uri, ImageView target, int maxPx);
    }

    interface Pick {
        void onPick(DashData.Track track);
    }

    private TopPicker() {
    }

    static void show(Activity activity, List<DashData.Track> chart, Set<String> heard, String playingId, Images images,
                     Pick pick) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_top);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setDimAmount(0.45f);
        }
        ListView list = dialog.findViewById(R.id.list);
        list.setItemsCanFocus(false);
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return chart.size();
            }

            @Override
            public Object getItem(int i) {
                return chart.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View row, ViewGroup parent) {
                if (row == null) row = activity.getLayoutInflater().inflate(R.layout.row_pick, parent, false);
                DashData.Track t = chart.get(i);
                row.<TextView>findViewById(R.id.rank).setText(String.valueOf(t.position));
                row.<TextView>findViewById(R.id.title).setText(t.title);
                String move = t.previous == 0 ? "MỚI" : t.previous > t.position ? "▲" + (t.previous - t.position)
                        : t.previous < t.position ? "▼" + (t.position - t.previous) : "=";
                row.<TextView>findViewById(R.id.subtitle).setText(move + " · " + t.artists);
                row.findViewById(R.id.heard).setVisibility(heard.contains(t.videoId) ? View.VISIBLE : View.GONE);
                row.setActivated(t.videoId.equals(playingId));
                ImageView thumb = row.findViewById(R.id.thumb);
                thumb.setClipToOutline(true);
                thumb.setImageResource(R.drawable.ic_note);
                if (!t.thumb.isEmpty()) images.load(t.thumb, thumb, 120);
                return row;
            }
        });
        // The list's own highlight: the row under the cursor gets the focused look of the card's rows.
        list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private View last;

            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (last != null) last.setSelected(false);
                if (view != null) view.setSelected(true);
                last = view;
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            pick.onPick(chart.get(position));
        });
        int start = 0;
        for (int i = 0; i < chart.size(); i++) if (chart.get(i).videoId.equals(playingId)) start = i;
        dialog.show();
        list.requestFocus();
        list.setSelection(start);
    }
}
