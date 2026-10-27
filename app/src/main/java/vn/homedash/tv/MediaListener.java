package vn.homedash.tv;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification access is what lets the app read other apps' media sessions
 * (`cmd notification allow_listener vn.homedash.tv/vn.homedash.tv.MediaListener`). Android keeps this service
 * bound, so it is also where songs from the trending list are noted as heard while YouTube fills the screen and
 * Home Dash is not running in front.
 */
public class MediaListener extends NotificationListenerService {
    private static final String TAG = "HomeDash";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<MediaController> watched = new ArrayList<>();
    private MediaSessionManager sessions;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            DashData.Track t = Heard.mark(MediaListener.this, metadata);
            if (t != null) Log.i(TAG, "heard #" + t.position + " " + t.videoId);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::watch;

    @Override
    public void onListenerConnected() {
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, MediaListener.class);
        try {
            sessions.addOnActiveSessionsChangedListener(changed, me, main);
            watch(sessions.getActiveSessions(me));
        } catch (SecurityException e) {
            Log.w(TAG, "no media session access: " + e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        unwatch();
    }

    private void watch(List<MediaController> list) {
        unwatch();
        if (list == null) return;
        for (MediaController c : list) {
            // The card player's own session: DashboardActivity notes those songs itself.
            if (c.getPackageName().equals(getPackageName())) continue;
            c.registerCallback(callback, main);
            watched.add(c);
            Heard.mark(this, c.getMetadata());
        }
    }

    private void unwatch() {
        for (MediaController c : watched) c.unregisterCallback(callback);
        watched.clear();
    }
}
