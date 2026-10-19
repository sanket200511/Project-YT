/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.settings.preference;

import android.app.Dialog;
import android.preference.PreferenceScreen;
import android.widget.Toolbar;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.GmsCoreSupportPatch;
import app.morphe.extension.shared.settings.SharedYouTubeSettings;
import app.morphe.extension.shared.settings.preference.ToolbarPreferenceFragment;
import app.morphe.extension.youtube.patches.HideStatusBarPatch;
import app.morphe.extension.youtube.settings.YouTubeActivityHook;

/**
 * Preference fragment for Morphe settings.
 */
@SuppressWarnings("deprecation")
public class YouTubePreferenceFragment extends ToolbarPreferenceFragment {
    /**
     * The main PreferenceScreen used to display the current set of preferences.
     */
    private PreferenceScreen preferenceScreen;

    /**
     * Initializes the preference fragment.
     */
    @Override
    protected void initialize() {
        super.initialize();

        try {
            preferenceScreen = getPreferenceScreen();
            sortPreferenceGroups(preferenceScreen);
            setPreferenceScreenToolbar(preferenceScreen);

            // Clunky work around until preferences are custom classes that manage themselves.
            // A mount (root) install has its branding applied while patching and cannot change
            // it at runtime. But the preferences must be added during patching because of
            // difficulties detecting during patching if it's a root installation. So instead
            // the non-functional preferences are removed during runtime.
            if (GmsCoreSupportPatch.isPackageNameOriginal()) {
                removePreferences(
                        SharedYouTubeSettings.CUSTOM_BRANDING_ICON.key,
                        SharedYouTubeSettings.CUSTOM_BRANDING_NOTIFICATION_ICON.key,
                        SharedYouTubeSettings.CUSTOM_BRANDING_NAME.key);
            }

            // Filter settings screens based on Advanced Settings Mode toggle
            boolean isAdvancedMode = app.morphe.extension.youtube.settings.Settings.ADVANCED_SETTINGS_MODE.get();
            if (!isAdvancedMode) {
                // Sanket's Preferences Mode: Hide all 12 cluttered advanced screens
                removePreferences(
                        "morphe_settings_screen_01_ads",
                        "morphe_settings_screen_02_dearrow",
                        "morphe_settings_screen_03_feed",
                        "morphe_settings_screen_04_general",
                        "morphe_settings_screen_05_player",
                        "morphe_settings_screen_06_shorts",
                        "morphe_settings_screen_07_seekbar",
                        "morphe_settings_screen_08_swipe_controls",
                        "morphe_settings_screen_09_return_youtube_dislike",
                        "morphe_settings_screen_10_sponsorblock",
                        "morphe_settings_screen_11_misc",
                        "morphe_settings_screen_12_video"
                );
            }

            android.preference.Preference modePref = findPreference("vista_advanced_settings_mode");
            if (modePref != null) {
                modePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (Boolean) newValue;
                    app.morphe.extension.youtube.settings.Settings.ADVANCED_SETTINGS_MODE.save(enabled);
                    android.app.Activity activity = getActivity();
                    if (activity != null) {
                        activity.recreate();
                    }
                    return true;
                });
            }
        } catch (Exception ex) {
            Logger.printException(() -> "initialize failure", ex);
        }
    }

    /**
     * Called when the fragment starts.
     */
    @Override
    public void onStart() {
        super.onStart();
        try {
            // Initialize search controller if needed.
            if (YouTubeActivityHook.searchViewController != null) {
                // Trigger search data collection after fragment is ready.
                YouTubeActivityHook.searchViewController.initializeSearchData();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onStart failure", ex);
        }
    }

    /**
     * Sets toolbar for all nested preference screens.
     */
    @Override
    protected void customizeToolbar(Toolbar toolbar) {
        YouTubeActivityHook.setToolbarLayoutParams(toolbar);
    }

    /**
     * Perform actions after toolbar setup.
     */
    @Override
    protected void onPostToolbarSetup(Toolbar toolbar, Dialog preferenceScreenDialog) {
        HideStatusBarPatch.hideStatusBar(preferenceScreenDialog);

        if (YouTubeActivityHook.searchViewController != null
                && YouTubeActivityHook.searchViewController.isSearchActive()) {
            toolbar.post(() -> YouTubeActivityHook.searchViewController.closeSearch());
        }
    }

    /**
     * Returns the preference screen for external access by SearchViewController.
     */
    public PreferenceScreen getPreferenceScreenForSearch() {
        return preferenceScreen;
    }
}
