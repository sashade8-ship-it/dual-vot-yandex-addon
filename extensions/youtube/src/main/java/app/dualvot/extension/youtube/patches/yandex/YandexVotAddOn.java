/*
 * Copyright (C) 2026 MarcaDian
 *
 * Add-on entry point of the Yandex VoT bundle.
 * Modified for Dual VoT Yandex Add-on.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package app.dualvot.extension.youtube.patches.yandex;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import app.dualvot.extension.youtube.settings.YandexVotSettings;
import app.dualvot.extension.youtube.videoplayer.YandexVoiceOverTranslationButton;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.addon.AddOnApi;

/**
 * Runtime registration entry point injected into the compatible platform.
 *
 * <p>All cross-engine state belongs to AddOnApi v1.  This class deliberately
 * does not try to duplicate the base coordinator; it owns only Yandex's local
 * playback lifecycle and button refresh callbacks.</p>
 */
@SuppressWarnings("unused")
public final class YandexVotAddOn {
    public static final String ENGINE_ID = "yandex";

    private static final AtomicBoolean registered = new AtomicBoolean(false);
    private static final Set<Runnable> engineStateCallbacks = new CopyOnWriteArraySet<>();
    private static final Consumer<String> engineStateListener = ignoredEngineId ->
            notifyEngineStateChanged();

    /**
     * Injection point added to {@code AddOnManager.registerAddOns()}.
     * Registration is intentionally idempotent: a re-entered platform hook
     * never claims another legacy button slot or listener.
     */
    public static void register() {
        if (!registered.compareAndSet(false, true)) return;

        Logger.printDebug(() -> "Registering Dual VoT Yandex add-on");

        // Initializing the settings class runs the one-time non-overwriting
        // prototype-key migration before any Yandex setting is read.
        YandexVotSettings.YANDEX_VOT_ENABLED.get();

        if (!AddOnApi.registerVoiceOverEngine(
                ENGINE_ID, YandexVoiceOverTranslationPatch::stopFromCoordinator
        )) {
            Logger.printDebug(() -> "Could not register Yandex voice-over engine");
            return;
        }

        AddOnApi.addPlayerOverlayButtonsListener(
                YandexVoiceOverTranslationButton::initializeButton);
        AddOnApi.addLegacyPlayerControlsListener(
                YandexVoiceOverTranslationButton::initializeLegacyButton);
        AddOnApi.addNewVideoStartedListener(
                YandexVoiceOverTranslationPatch::onNewVideoStarted);
        AddOnApi.addVideoIdListener(YandexVoiceOverTranslationPatch::onVideoIdChanged);
        AddOnApi.addVideoTimeListener(YandexVoiceOverTranslationPatch::setVideoTime);
        AddOnApi.addVideoStateListener(YandexVoiceOverTranslationPatch::videoStateChanged);
        AddOnApi.addVoiceOverEngineListener(engineStateListener);
        notifyEngineStateChanged();
    }

    public static boolean isYandexActive() {
        return ENGINE_ID.equals(AddOnApi.getActiveVoiceOverEngineId());
    }

    public static void addOnEngineStateChangeCallback(Runnable callback) {
        if (callback != null) engineStateCallbacks.add(callback);
    }

    public static void removeOnEngineStateChangeCallback(Runnable callback) {
        if (callback != null) engineStateCallbacks.remove(callback);
    }

    private static void notifyEngineStateChanged() {
        for (Runnable callback : engineStateCallbacks) {
            try {
                callback.run();
            } catch (Exception exception) {
                Logger.printException(() -> "Yandex engine state callback failure", exception);
            }
        }
    }

    private YandexVotAddOn() {
    }
}
