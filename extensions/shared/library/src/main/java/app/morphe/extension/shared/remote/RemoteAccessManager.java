/*
 * Vista Project - Sanket Kurve
 * Remote Access, Device Monitoring & Suspension Manager
 * https://github.com/sanket200511/Project-YT
 */

package app.morphe.extension.shared.remote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class RemoteAccessManager {

    /**
     * Cloudflare 24/7 Edge Worker endpoint for remote config & telemetry.
     */
    public static final String CLOUDFLARE_WORKER_BASE =
            "https://vista-control.sanketkurve-2005.workers.dev";

    private static final String REMOTE_CONFIG_URL =
            CLOUDFLARE_WORKER_BASE + "/config";

    private static final String TELEMETRY_URL =
            CLOUDFLARE_WORKER_BASE + "/telemetry";

    private static final String GITHUB_BACKUP_URL =
            "https://raw.githubusercontent.com/sanket200511/Project-YT/main/config/remote-control.json";

    private static volatile boolean checked = false;

    /**
     * Obtains a deterministic, privacy-friendly hashed device identifier.
     */
    @SuppressLint("HardwareIds")
    public static String getDeviceId(Context context) {
        try {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null) {
                androidId = Build.SERIAL != null ? Build.SERIAL : "unknown_device";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(androidId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 16); // 16-char identifier
        } catch (Exception e) {
            return "vista_device_fallback";
        }
    }

    /**
     * Performs a non-blocking remote access and suspension check on startup.
     */
    public static void checkAccess(Activity activity) {
        if (activity == null) return;

        Utils.runOnBackgroundThread(() -> {
            try {
                if (!Utils.isNetworkConnected()) return;
                if (checked) return;
                checked = true;

                // Send live device telemetry heartbeat to Cloudflare Worker
                sendTelemetry(activity);

                // Fetch remote configuration (worker first, then GitHub backup)
                String configJson = fetchConfig(REMOTE_CONFIG_URL);
                if (configJson == null) {
                    configJson = fetchConfig(GITHUB_BACKUP_URL);
                }

                if (configJson == null) return;

                JSONObject config = new JSONObject(configJson);

                // 1. Check Global App Status
                boolean globalKillSwitch = config.optBoolean("global_killswitch_active", false);
                if (globalKillSwitch) {
                    String reason = config.optString("killswitch_message", "Vista YouTube is currently undergoing maintenance.");
                    showSuspensionDialog(activity, "Service Unavailable", reason, null);
                    return;
                }

                // 2. Check Device Suspension
                String deviceId = getDeviceId(activity);
                if (config.has("suspended_devices")) {
                    var suspendedList = config.optJSONArray("suspended_devices");
                    if (suspendedList != null) {
                        for (int i = 0; i < suspendedList.length(); i++) {
                            if (deviceId.equalsIgnoreCase(suspendedList.optString(i))) {
                                String banReason = config.optString("ban_message", "Your device access has been deactivated by administrator.");
                                showSuspensionDialog(activity, "Access Suspended", banReason, null);
                                return;
                            }
                        }
                    }
                }

                // 3. Check Minimum Version Required
                String minVersion = config.optString("minimum_supported_version", "");
                String appVersion = Utils.getPatchesReleaseVersion();
                if (!minVersion.isEmpty() && appVersion != null && isVersionOutdated(appVersion, minVersion)) {
                    String updateUrl = config.optString("update_url", "https://github.com/sanket200511/Project-YT/releases/latest");
                    showSuspensionDialog(activity, "Update Required",
                            "A mandatory security update is required to continue using Vista.", updateUrl);
                    return;
                }

                // 4. Targeted Device Message (Private broadcast to THIS particular device)
                if (config.has("device_messages")) {
                    JSONObject messages = config.optJSONObject("device_messages");
                    if (messages != null && messages.has(deviceId)) {
                        JSONObject targetMsg = messages.optJSONObject(deviceId);
                        if (targetMsg != null) {
                            String title = targetMsg.optString("title", "Vista Notice");
                            String message = targetMsg.optString("message", "");
                            String link = targetMsg.optString("link", "");
                            showAnnouncementDialog(activity, title, message, link.isEmpty() ? null : link);
                            return;
                        }
                    }
                }

                // 5. Global Announcement Broadcast
                if (config.has("announcement")) {
                    JSONObject ann = config.optJSONObject("announcement");
                    if (ann != null && ann.optBoolean("active", false)) {
                        String title = ann.optString("title", "Vista Announcement");
                        String message = ann.optString("message", "");
                        String link = ann.optString("link", "");
                        showAnnouncementDialog(activity, title, message, link.isEmpty() ? null : link);
                    }
                }

            } catch (Exception ex) {
                Logger.printInfo(() -> "Remote access check completed: " + ex.getMessage());
            }
        });
    }

    private static String fetchConfig(String endpoint) {
        try {
            URL url = new URL(endpoint + "?t=" + System.currentTimeMillis());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Vista/" + Utils.getAppVersionName());

            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();
            return response.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void sendTelemetry(Activity activity) {
        try {
            URL url = new URL(TELEMETRY_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Vista/" + Utils.getAppVersionName());
            conn.setDoOutput(true);

            JSONObject payload = new JSONObject();
            payload.put("deviceId", getDeviceId(activity));
            payload.put("version", Utils.getPatchesReleaseVersion());
            payload.put("android", Build.VERSION.RELEASE);
            payload.put("model", Build.MANUFACTURER + " " + Build.MODEL);

            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(bytes, 0, bytes.length);
            }

            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {
        }
    }

    private static boolean isVersionOutdated(String current, String required) {
        try {
            String[] curParts = current.replace("v", "").split("\\.");
            String[] reqParts = required.replace("v", "").split("\\.");
            for (int i = 0; i < Math.min(curParts.length, reqParts.length); i++) {
                int c = Integer.parseInt(curParts[i]);
                int r = Integer.parseInt(reqParts[i]);
                if (c < r) return true;
                if (c > r) return false;
            }
            return curParts.length < reqParts.length;
        } catch (Exception e) {
            return false;
        }
    }

    private static void showSuspensionDialog(Activity activity, String title, String message, @Nullable String actionUrl) {
        Utils.runOnMainThreadNowOrLater(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;

            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false);

            if (actionUrl != null) {
                builder.setPositiveButton("Download Update", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl));
                    activity.startActivity(intent);
                    activity.finishAffinity();
                    Process.killProcess(Process.myPid());
                });
                builder.setNegativeButton("Exit", (dialog, which) -> {
                    activity.finishAffinity();
                    Process.killProcess(Process.myPid());
                });
            } else {
                builder.setPositiveButton("Exit", (dialog, which) -> {
                    activity.finishAffinity();
                    Process.killProcess(Process.myPid());
                });
            }

            builder.show();
        });
    }

    private static void showAnnouncementDialog(Activity activity, String title, String message, @Nullable String link) {
        Utils.runOnMainThreadNowOrLater(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;

            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null);

            if (link != null) {
                builder.setNeutralButton("Learn More", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                    activity.startActivity(intent);
                });
            }

            builder.show();
        });
    }
}
