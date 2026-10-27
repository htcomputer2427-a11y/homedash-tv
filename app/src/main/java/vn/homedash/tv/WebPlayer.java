package vn.homedash.tv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * YouTube's own embedded player (IFrame API, assets/player.html) in a WebView inside the music card, so the trending
 * list plays right there instead of opening the YouTube app full screen. YouTube's terms want the player visible,
 * so the video shows in the cover box while the sound plays. Created on the first Play only.
 */
final class WebPlayer {
    interface Listener {
        /** Anything changed: state, song, or the list ran out (active turns false). */
        void onChange(WebPlayer player);
    }

    private static final String TAG = "HomeDash";
    /** The page's origin, sent to YouTube as the embedding site. */
    private static final String ORIGIN = "https://vn.homedash.tv/";

    final WebView view;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** The page and YouTube's player have loaded; until then commands wait in pending. */
    private boolean ready;
    private String pending;

    /** A list is loaded and has songs left. */
    boolean active;
    boolean playing;
    String videoId = "";
    String title = "";
    String author = "";
    int index = -1;
    int size;
    double time;
    double duration;

    @SuppressLint("SetJavaScriptEnabled")
    WebPlayer(Context context, ViewGroup host, float cornerPx, Listener listener) {
        this.listener = listener;
        view = new WebView(context);
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        view.setBackgroundColor(Color.BLACK);
        // The remote's D-pad stays on Home Dash's own buttons.
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        // 1 CSS pixel = 1 screen pixel: the player sees 268x212, above YouTube's 200x200 minimum.
        view.setInitialScale(100);
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerPx);
            }
        });
        view.setWebViewClient(new WebViewClient());
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                Log.i(TAG, "web player console: " + m.message());
                return true;
            }
        });
        view.addJavascriptInterface(new Bridge(), "Dash");
        view.setVisibility(View.GONE);
        host.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.loadDataWithBaseURL(ORIGIN, page(context), "text/html", "utf-8", null);
    }

    private static String page(Context c) {
        try (InputStream in = c.getAssets().open("player.html")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("assets/player.html", e);
        }
    }

    void load(List<String> ids) {
        StringBuilder a = new StringBuilder("[");
        for (String id : ids) {
            if (!id.matches("[A-Za-z0-9_-]{6,20}")) continue;  // ids go into JavaScript
            if (a.length() > 1) a.append(',');
            a.append('\'').append(id).append('\'');
        }
        a.append(']');
        size = ids.size();
        index = 0;
        active = true;
        view.setVisibility(View.VISIBLE);
        String code = "load(" + a + ",0)";
        // On the first Play the page is still loading and load() does not exist yet: send it once the player is ready.
        if (ready) js(code);
        else pending = code;
    }

    void play() {
        js("play()");
    }

    void pause() {
        js("pause()");
    }

    void toggle() {
        if (playing) pause();
        else play();
    }

    void next() {
        js("next()");
    }

    void prev() {
        js("prev()");
    }

    void at(int i) {
        js("at(" + i + ")");
    }

    void stop() {
        pause();
        active = false;
        playing = false;
        view.setVisibility(View.GONE);
    }

    void destroy() {
        view.destroy();
    }

    private void js(String code) {
        view.evaluateJavascript(code, null);
    }

    /** Called by player.html on the WebView's own thread; everything is handed to the main thread. */
    private final class Bridge {
        @JavascriptInterface
        public void ready() {
            ui.post(() -> {
                Log.i(TAG, "web player ready");
                ready = true;
                if (pending != null) js(pending);
                pending = null;
            });
        }

        @JavascriptInterface
        public void state(int state, String id, String t, String a, int i) {
            ui.post(() -> {
                // YouTube states: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued.
                playing = state == 1 || state == 3;
                if (!id.isEmpty()) videoId = id;
                if (!t.isEmpty()) title = t;
                if (!a.isEmpty()) author = a;
                if (i >= 0) index = i;
                if (state == 0 && index >= size - 1) active = false;
                Log.i(TAG, "web player state " + state + " #" + index + " " + videoId);
                listener.onChange(WebPlayer.this);
            });
        }

        @JavascriptInterface
        public void progress(double t, double d) {
            ui.post(() -> {
                time = t;
                duration = d;
            });
        }

        @JavascriptInterface
        public void error(int code, int i) {
            ui.post(() -> {
                Log.w(TAG, "web player error " + code + " at #" + i);
                // A song the owner does not allow embedded, or that is gone: go on with the next one.
                if (i >= size - 1) {
                    active = false;
                    listener.onChange(WebPlayer.this);
                } else {
                    next();
                }
            });
        }
    }
}
