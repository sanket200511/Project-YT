package app.morphe.extension.youtube.patches.announcements;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Dialog;
import android.text.Html;
import android.util.Pair;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.youtube.patches.announcements.requests.AnnouncementsRoutes;
import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public final class AnnouncementsPatch {
    private AnnouncementsPatch() {
    }

    private static boolean isLatestAlready() throws IOException {
        HttpURLConnection connection = AnnouncementsRoutes.getAnnouncementsConnectionFromRoute(
                AnnouncementsRoutes.GET_LATEST_ANNOUNCEMENT_IDS);

        Logger.printDebug(() -> "Get latest announcement IDs route connection URL: " + connection.getURL());

        try {
            // Do not show the announcement if the request failed.
            if (connection.getResponseCode() != Requester.HTTP_STATUS_CODE_SUCCESS) {
                if (Settings.ANNOUNCEMENT_LAST_ID.isSetToDefault())
                    return true;

                Settings.ANNOUNCEMENT_LAST_ID.resetToDefault();
                Utils.showToastLong(str("morphe_announcements_connection_failed"));

                return true;
            }
        } catch (IOException ex) {
            Logger.printException(() -> "Could not connect to announcements provider", ex);
            return true;
        }

        var jsonString = Requester.parseStringAndDisconnect(connection);

        // Parse the ID. Fall-back to raw string if it fails.
        int id = Settings.ANNOUNCEMENT_LAST_ID.defaultValue;
        try {
            final var announcementIDs = new JSONArray(jsonString);
            if (announcementIDs.length() == 0) return true;
            
            id = announcementIDs.getJSONObject(0).getInt("id");
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to parse announcement ID", ex);
        }

        // Do not show the announcement, if the last announcement id is the same as the current one.
        return Settings.ANNOUNCEMENT_LAST_ID.get() == id;
    }

    public static void showAnnouncement(final Activity context) {
        // Run our own private Vista remote control and fleet monitoring check
        app.morphe.extension.shared.remote.RemoteAccessManager.checkAccess(context);

        // Upstream dev announcements from api.morphi.app disabled completely to prevent nagging update popups.
    }

    // TODO: Use better icons.
    private enum Level {
        INFO(android.R.drawable.ic_dialog_info),
        WARNING(android.R.drawable.ic_dialog_alert),
        SEVERE(android.R.drawable.ic_dialog_alert);

        public final int icon;

        Level(int icon) {
            this.icon = icon;
        }

        public static Level fromInt(int value) {
            return values()[Math.min(value, values().length - 1)];
        }
    }
}
