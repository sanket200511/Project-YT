package app.morphe.extension.shared.settings.preference.about;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;

class AboutRoutes {
    /**
     * Backup icon url if the API call fails.
     */
    public static volatile String aboutLogoUrl = "https://raw.githubusercontent.com/sanket200511/Project-YT/main/Vista.png";

    /**
     * Links to use if fetch links api call fails.
     */
    private static final List<MorpheAboutPreference.WebLink> NO_CONNECTION_STATIC_LINKS = List.of(
            new MorpheAboutPreference.WebLink(true, "Website", null, "https://linktr.ee/sanketkurve"),
            new MorpheAboutPreference.WebLink(true, "GitHub", null, "https://github.com/sanket200511/Project-YT"),
            MorpheAboutPreference.CREDITS_LINK
    );


    private static final String GITHUB_URL = "https://raw.githubusercontent.com";
    private static final Route.CompiledRoute GITHUB_ROUTE_PATCHES = new Route(Route.Method.GET,
            (Utils.isPreReleasePatches()
                    ? "/sanket200511/Project-YT/refs/heads/dev/patches-bundle.json"
                    : "/sanket200511/Project-YT/refs/heads/main/patches-bundle.json")
    ).compile();

    @Nullable
    private static volatile String latestPatchesVersion;
    private static volatile long latestPatchesVersionLastCheckedTime;

    static boolean hasFetchedPatchersVersion() {
        final long updateCheckFrequency = 5 * 60 * 1000; // 5 minutes.
        final long now = System.currentTimeMillis();

        return latestPatchesVersion != null && (now - latestPatchesVersionLastCheckedTime) < updateCheckFrequency;
    }

    @Nullable
    static String getLatestPatchesVersion() {
        String version = latestPatchesVersion;
        if (version != null) return version;

        if (!Utils.isNetworkConnected()) return null;

        try {
            HttpURLConnection connection = Requester.getConnectionFromCompiledRoute(
                    GITHUB_URL, GITHUB_ROUTE_PATCHES);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            Logger.printDebug(() -> "Fetching latest patches version links from: " + connection.getURL());

            // Do not show an exception toast if the server is down
            final int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.printDebug(() -> "Failed to get patches bundle. Response code: " + responseCode);
                return null;
            }

            JSONObject json = Requester.parseJSONObjectAndDisconnect(connection);
            version = json.getString("version");
            if (version.startsWith("v")) {
                version = version.substring(1);
            }
            latestPatchesVersion = version;
            latestPatchesVersionLastCheckedTime = System.currentTimeMillis();

            return version;
        } catch (SocketTimeoutException ex) {
            Logger.printInfo(() -> "Could not fetch patches version", ex); // No toast.
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse about information", ex);
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to get patches version", ex);
        }

        return null;
    }

    @Nullable
    private static volatile List<MorpheAboutPreference.WebLink> fetchedLinks;

    static boolean hasFetchedLinks() {
        return fetchedLinks != null;
    }

    static List<MorpheAboutPreference.WebLink> fetchAboutLinks() {
        return NO_CONNECTION_STATIC_LINKS;
    }
}
