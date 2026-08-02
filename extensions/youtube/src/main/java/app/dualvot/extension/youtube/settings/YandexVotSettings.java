/*
 * Copyright (C) 2026 MarcaDian
 *
 * Original add-on settings isolated from the base bundle.
 * Modified for Dual VoT Yandex Add-on.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package app.dualvot.extension.youtube.settings;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static app.morphe.extension.shared.settings.Setting.parent;

import android.content.SharedPreferences;

import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.shared.settings.preference.SeekBarPreference;
import app.morphe.extension.shared.settings.preference.SeekBarPreference.SeekBarConfig;

/**
 * Settings owned by the independent Yandex add-on.
 *
 * <p>The prototype used {@code morphe_yandex_vot_*}.  The canonical
 * {@code dualvot_yandex_*} keys are intentionally distinct from the compatible
 * platform and are copied once only when the canonical key is absent.</p>
 */
public final class YandexVotSettings {
    private static final String LEGACY_PREFIX = "morphe_yandex_vot_";
    private static final String MIGRATION_KEY = "dualvot_yandex_migration_v1";

    public static final BooleanSetting YANDEX_VOT_ENABLED =
            new BooleanSetting("dualvot_yandex_enabled", FALSE);
    public static final StringSetting YANDEX_VOT_TIMER_POSITION =
            new StringSetting("dualvot_yandex_timer_position", "inside", false,
                    parent(YANDEX_VOT_ENABLED));
    public static final BooleanSetting YANDEX_VOT_PROGRESS_RING_ENABLED =
            new BooleanSetting("dualvot_yandex_progress_ring_enabled", TRUE, false,
                    parent(YANDEX_VOT_ENABLED));
    public static final StringSetting YANDEX_VOT_PROGRESS_RING_COLOR =
            new StringSetting("dualvot_yandex_progress_ring_color", "#FFC107", false,
                    parent(YANDEX_VOT_PROGRESS_RING_ENABLED));
    public static final IntegerSetting YANDEX_VOT_PROGRESS_RING_THICKNESS =
            new IntegerSetting("dualvot_yandex_progress_ring_thickness", 2, false,
                    parent(YANDEX_VOT_PROGRESS_RING_ENABLED));
    public static final StringSetting YANDEX_VOT_SOURCE_LANGUAGE =
            new StringSetting("dualvot_yandex_source_language", "auto", false,
                    parent(YANDEX_VOT_ENABLED));
    public static final StringSetting YANDEX_VOT_TARGET_LANGUAGE =
            new StringSetting("dualvot_yandex_target_language", "ru", false,
                    parent(YANDEX_VOT_ENABLED));
    public static final IntegerSetting YANDEX_VOT_TRANSLATION_VOLUME =
            new IntegerSetting("dualvot_yandex_translation_volume", 100, false,
                    parent(YANDEX_VOT_ENABLED));
    public static final IntegerSetting YANDEX_VOT_ORIGINAL_AUDIO_VOLUME =
            new IntegerSetting("dualvot_yandex_original_audio_volume", 30, false,
                    parent(YANDEX_VOT_ENABLED));
    public static final BooleanSetting YANDEX_VOT_AUDIO_PROXY_ENABLED =
            new BooleanSetting("dualvot_yandex_audio_proxy_enabled", TRUE, false,
                    parent(YANDEX_VOT_ENABLED));
    public static final StringSetting YANDEX_VOT_PROXY_URL =
            new StringSetting("dualvot_yandex_proxy_url", "vot-worker.eu.cc", false,
                    parent(YANDEX_VOT_ENABLED));
    public static final BooleanSetting YANDEX_VOT_USE_LIVE_VOICES =
            new BooleanSetting("dualvot_yandex_use_live_voices", TRUE, false,
                    parent(YANDEX_VOT_ENABLED));
    public static final StringSetting YANDEX_VOT_OAUTH_TOKEN =
            new StringSetting("dualvot_yandex_oauth_token", "", false,
                    parent(YANDEX_VOT_ENABLED));

    static {
        migratePrototypeSettingsOnce();
        SeekBarPreference.register(new SeekBarConfig(YANDEX_VOT_ORIGINAL_AUDIO_VOLUME,
                0, 100, 1, "%"));
        SeekBarPreference.register(new SeekBarConfig(YANDEX_VOT_TRANSLATION_VOLUME,
                0, 100, 1, "%"));
        SeekBarPreference.register(new SeekBarConfig(YANDEX_VOT_PROGRESS_RING_THICKNESS,
                1, 6, 1, "dp"));
    }

    /**
     * Imports the old prototype values once.  A canonical value always wins,
     * even if it is equal to a default, so reinstalling/upgrading never
     * overwrites a user's current add-on preference.
     */
    private static void migratePrototypeSettingsOnce() {
        SharedPreferences preferences = Setting.preferences.preferences;
        if (preferences.getBoolean(MIGRATION_KEY, false)) return;

        migrateBoolean(preferences, "enabled", YANDEX_VOT_ENABLED);
        migrateString(preferences, "source_language", YANDEX_VOT_SOURCE_LANGUAGE);
        migrateString(preferences, "target_language", YANDEX_VOT_TARGET_LANGUAGE);
        migrateInteger(preferences, "translation_volume", YANDEX_VOT_TRANSLATION_VOLUME);
        migrateInteger(preferences, "original_audio_volume", YANDEX_VOT_ORIGINAL_AUDIO_VOLUME);
        migrateBoolean(preferences, "audio_proxy_enabled", YANDEX_VOT_AUDIO_PROXY_ENABLED);
        migrateString(preferences, "proxy_url", YANDEX_VOT_PROXY_URL);
        migrateBoolean(preferences, "use_live_voices", YANDEX_VOT_USE_LIVE_VOICES);
        migrateString(preferences, "oauth_token", YANDEX_VOT_OAUTH_TOKEN);

        Setting.preferences.saveBoolean(MIGRATION_KEY, true);
    }

    private static void migrateBoolean(
            SharedPreferences preferences, String suffix, BooleanSetting canonical
    ) {
        String legacyKey = LEGACY_PREFIX + suffix;
        if (!preferences.contains(canonical.key) && preferences.contains(legacyKey)) {
            canonical.save(preferences.getBoolean(legacyKey, canonical.defaultValue));
        }
    }

    private static void migrateInteger(
            SharedPreferences preferences, String suffix, IntegerSetting canonical
    ) {
        String legacyKey = LEGACY_PREFIX + suffix;
        if (!preferences.contains(canonical.key) && preferences.contains(legacyKey)) {
            String value = preferences.getString(legacyKey, canonical.defaultValue.toString());
            try {
                canonical.save(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                // Keep the canonical default if an old malformed value is found.
            }
        }
    }

    private static void migrateString(
            SharedPreferences preferences, String suffix, StringSetting canonical
    ) {
        String legacyKey = LEGACY_PREFIX + suffix;
        if (!preferences.contains(canonical.key) && preferences.contains(legacyKey)) {
            canonical.save(preferences.getString(legacyKey, canonical.defaultValue));
        }
    }

    private YandexVotSettings() {
    }
}
