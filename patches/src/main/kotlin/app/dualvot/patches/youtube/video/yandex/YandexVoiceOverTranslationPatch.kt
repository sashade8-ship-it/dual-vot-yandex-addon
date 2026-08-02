/*
 * Copyright (C) 2026 anddea
 *
 * This file is part of the revanced-patches project:
 * https://github.com/anddea/revanced-patches
 *
 * Original author(s):
 * - Jav1x (https://github.com/Jav1x)
 *
 * Ported to morphe-patches: https://github.com/MorpheApp/morphe-patches
 * Modified by: Jav1x (https://github.com/Jav1x)
 * Modified for Dual VoT Yandex Add-on contributors.
 *
 * Licensed under the GNU General Public License v3.0.
 *
 * ------------------------------------------------------------------------
 * GPLv3 Section 7 – Attribution Notice
 * ------------------------------------------------------------------------
 *
 * This file contains substantial original work by the author(s) listed above.
 *
 * In accordance with Section 7 of the GNU General Public License v3.0,
 * the following additional terms apply to this file:
 *
 * 1. Attribution (Section 7(b)): This specific copyright notice and the
 *    list of original authors above must be preserved in any copy or
 *    derivative work. You may add your own copyright notice below it,
 *    but you may not remove the original one.
 *
 * 2. Origin (Section 7(c)): Modified versions must be clearly marked as
 *    such (e.g., by adding a "Modified by" line or a new copyright notice).
 *    They must not be misrepresented as the original work.
 *
 * ------------------------------------------------------------------------
 * Version Control Acknowledgement (Non-binding Request)
 * ------------------------------------------------------------------------
 *
 * While not a legal requirement of the GPLv3, the original author(s)
 * respectfully request that ports or substantial modifications retain
 * historical authorship credit in version control systems (e.g., Git),
 * listing original author(s) appropriately and modifiers as committers
 * or co-authors.
 */

package app.dualvot.patches.youtube.video.yandex

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue

/**
 * Registration method of this independent add-on.
 */
private const val EXTENSION_ADD_ON_REGISTER_METHOD =
    "Lapp/dualvot/extension/youtube/patches/yandex/YandexVotAddOn;->register()V"

private const val EXTENSION_ORIGINAL_VOLUME_CLASS =
    "Lapp/dualvot/extension/youtube/patches/yandex/YandexVotOriginalVolumePatch;"

private const val ADD_ON_API_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/addon/AddOnApi;"

private const val ADD_ON_MANAGER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/addon/AddOnManager;"

private const val ADD_ON_MANAGER_REGISTER_METHOD_NAME = "registerAddOns"

private const val REQUIRED_ADD_ON_API_VERSION = 1

/**
 * Key of the built-in voice-over translation preference of the compatible
 * platform. This add-on is inserted after it when the host preference exists.
 */
private const val VOICE_OVER_TRANSLATION_SCREEN_KEY = "morphe_vot_screen"

/** Key of this add-on's own settings screen. */
private const val YANDEX_SCREEN_KEY = "dualvot_yandex_screen"

/** Fallback screen when the host voice-over preference is unavailable. */
private const val VIDEO_SCREEN_KEY = "morphe_settings_screen_12_video"

/**
 * No app versions are declared: the API-v1 gate below is the authoritative
 * compatibility check and fails before this add-on mutates resources,
 * extensions, registration, or the AudioTrack hook.
 */
private val COMPATIBILITY_YOUTUBE = Compatibility(
    packageName = "com.google.android.youtube",
    name = "YouTube",
    apkFileType = ApkFileType.APK_REQUIRED,
    appIconColor = 0xFF0033,
    signatures = setOf(
        // Android 13+
        "5aad2bee6db95d17e05a08d7d1e64c10a1511879154483916b6ae6c7fd9cb0c6",
        // Android 7+
        "3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a",
    ),
)

private const val AUDIO_TRACK_CLASS = "Landroid/media/AudioTrack;"

private fun MethodReference.isAudioTrackSetVolume(): Boolean =
    definingClass == AUDIO_TRACK_CLASS &&
        name == "setVolume" &&
        parameterTypes.toList() == listOf("F") &&
        returnType == "I"

private fun getVolumeRegister(i: Instruction): Int? = when (i) {
    is FiveRegisterInstruction -> if (i.registerCount >= 2) i.registerD else null
    is TwoRegisterInstruction -> i.registerB
    is RegisterRangeInstruction -> if (i.registerCount >= 2) i.startRegister + i.registerCount - 1 else null
    else -> null
}

private fun getAudioTrackRegister(i: Instruction): Int? = when (i) {
    is FiveRegisterInstruction -> if (i.registerCount >= 1) i.registerC else null
    is TwoRegisterInstruction -> i.registerA
    is RegisterRangeInstruction -> if (i.registerCount >= 1) i.startRegister else null
    else -> null
}

private object AudioTrackSetVolumeMethodFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(methodCall(
        definingClass = AUDIO_TRACK_CLASS,
        name = "setVolume",
        parameters = listOf("F"),
        returnType = "I",
    )),
)

/**
 * Verifies the complete public surface and registration injection point that
 * the add-on uses. This is a separate, non-mutating patch and every mutating
 * patch depends on it.
 */
private val yandexAddOnApiV1CompatibilityPatch = bytecodePatch {
    execute {
        verifyAddOnApiV1()
    }
}

context(context: BytecodePatchContext)
private fun verifyAddOnApiV1() {
    val apiClass = context.mutableClassDefByOrNull(ADD_ON_API_CLASS_DESCRIPTOR)
        ?: throw PatchException(
            "Dual VoT Yandex Add-on requires Morphe AddOnApi v1, but AddOnApi is missing."
        )

    val apiVersion = (apiClass.fields.firstOrNull {
        it.name == "API_VERSION" && it.type == "I"
    }?.initialValue as? IntEncodedValue)?.value
    if (apiVersion != REQUIRED_ADD_ON_API_VERSION) {
        throw PatchException(
            "Dual VoT Yandex Add-on requires AddOnApi.API_VERSION == " +
                "$REQUIRED_ADD_ON_API_VERSION, found ${apiVersion ?: "missing"}."
        )
    }

    fun requireMethod(name: String, returnType: String, parameterTypes: List<String>) {
        if (apiClass.methods.none {
                it.name == name &&
                    it.returnType == returnType &&
                    it.parameterTypes.toList() == parameterTypes
            }) {
            throw PatchException(
                "Dual VoT Yandex Add-on requires AddOnApi.$name(" +
                    parameterTypes.joinToString() + ")$returnType."
            )
        }
    }

    requireMethod("registerVoiceOverEngine", "Z", listOf("Ljava/lang/String;", "Ljava/lang/Runnable;"))
    requireMethod("activateVoiceOverEngine", "Z", listOf("Ljava/lang/String;"))
    requireMethod("deactivateVoiceOverEngine", "Z", listOf("Ljava/lang/String;"))
    requireMethod("stopActiveVoiceOverEngine", "Z", emptyList())
    requireMethod("getActiveVoiceOverEngineId", "Ljava/lang/String;", emptyList())
    requireMethod("addVoiceOverEngineListener", "V", listOf("Ljava/util/function/Consumer;"))
    requireMethod("removeVoiceOverEngineListener", "V", listOf("Ljava/util/function/Consumer;"))

    val addOnManagerClass = context.mutableClassDefByOrNull(ADD_ON_MANAGER_CLASS_DESCRIPTOR)
        ?: throw PatchException(
            "Dual VoT Yandex Add-on requires Morphe AddOnManager, but it is missing."
        )
    if (addOnManagerClass.methods.none {
            it.name == ADD_ON_MANAGER_REGISTER_METHOD_NAME &&
                it.returnType == "V" &&
                it.parameterTypes.toList().isEmpty()
        }) {
        throw PatchException(
            "Dual VoT Yandex Add-on requires AddOnManager.registerAddOns()V."
        )
    }
}

private val yandexVoiceOverTranslationBytecodePatch = bytecodePatch {
    dependsOn(yandexAddOnApiV1CompatibilityPatch)
    extendWith("extensions/youtube.mpe")

    execute {
        // Duck original audio: route AudioTrack.setVolume through the extension multiplier.
        val method = AudioTrackSetVolumeMethodFingerprint.method
        val index = method.indexOfFirstInstructionOrThrow {
            (opcode == Opcode.INVOKE_VIRTUAL || opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
                (getReference<MethodReference>()?.isAudioTrackSetVolume() == true)
        }
        val instruction = method.implementation!!.instructions.elementAt(index)
        val audioTrackReg = getAudioTrackRegister(instruction)
            ?: throw PatchException("YandexVoT: cannot get AudioTrack register")
        val volReg = getVolumeRegister(instruction)
            ?: throw PatchException("YandexVoT: cannot get volume register")
        method.addInstructions(index, """
            invoke-static { v$audioTrackReg, v$volReg }, $EXTENSION_ORIGINAL_VOLUME_CLASS->applyVolumeMultiplier(Landroid/media/AudioTrack;F)F
            move-result v$volReg
            """.trimIndent())
    }

    finalize {
        // The host extension is merged during its patches; registration belongs
        // in finalize, after the API-v1 gate and the AudioTrack injection.
        registerAddOn(EXTENSION_ADD_ON_REGISTER_METHOD)
    }
}

private val yandexVoiceOverTranslationResourcePatch = resourcePatch {
    dependsOn(yandexAddOnApiV1CompatibilityPatch)

    execute {
        copyResources(
            "yandexvoiceovertranslationbutton",
            ResourceGroup(
                resourceDirectoryName = "drawable",
                "dualvot_yt_yandex_vot.xml",
                "dualvot_yt_yandex_vot_bold.xml",
            ),
        )

        addBundledResources()

        addAddOnPreferences(
            preferenceScreen(
                key = YANDEX_SCREEN_KEY,
                titleKey = "dualvot_yandex_screen_title",
                summaryKey = "dualvot_yandex_screen_summary",
                sorting = Sorting.UNSORTED,
                preferences = listOf(
                    noTitlePreferenceCategory(
                        key = "dualvot_yandex_general_category",
                        preferences = listOf(
                            switchPreference("dualvot_yandex_enabled"),
                            listPreference("dualvot_yandex_timer_position"),
                            switchPreference("dualvot_yandex_progress_ring_enabled"),
                            nonInteractivePreference(
                                key = "dualvot_yandex_progress_ring_color",
                                tag = "app.morphe.extension.shared.settings.preference.ColorPickerPreference",
                                selectable = true,
                            ),
                            nonInteractivePreference(
                                key = "dualvot_yandex_progress_ring_thickness",
                                tag = "app.morphe.extension.shared.settings.preference.SeekBarPreference",
                                selectable = true,
                            ),
                            listPreference("dualvot_yandex_source_language"),
                            listPreference("dualvot_yandex_target_language"),
                            nonInteractivePreference(
                                key = "dualvot_yandex_translation_volume",
                                tag = "app.morphe.extension.shared.settings.preference.SeekBarPreference",
                                selectable = true,
                            ),
                            nonInteractivePreference(
                                key = "dualvot_yandex_original_audio_volume",
                                tag = "app.morphe.extension.shared.settings.preference.SeekBarPreference",
                                selectable = true,
                            ),
                            switchPreference("dualvot_yandex_use_live_voices"),
                            nonInteractivePreference(
                                key = "dualvot_yandex_oauth_token",
                                tag = "app.dualvot.extension.youtube.settings.preference.YandexVotOAuthPreference",
                                selectable = true,
                            ),
                        ),
                    ),
                    noTitlePreferenceCategory(
                        key = "dualvot_yandex_proxy_category",
                        preferences = listOf(
                            switchPreference(
                                key = "dualvot_yandex_audio_proxy_enabled",
                                titleKey = "dualvot_yandex_audio_proxy_title",
                                summary = true,
                            ),
                            textPreference("dualvot_yandex_proxy_url"),
                            nonInteractivePreference("dualvot_yandex_credits"),
                        ),
                    ),
                ),
            ),
            afterKey = VOICE_OVER_TRANSLATION_SCREEN_KEY,
            screenKey = VIDEO_SCREEN_KEY,
        )
    }
}

@Suppress("unused")
val yandexVoiceOverTranslationPatch = bytecodePatch(
    name = "Voice Over Translation (Yandex)",
    description = "Development-only Voice Over Translation (Yandex) add-on requiring Morphe AddOnApi v1.",
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)
    dependsOn(
        yandexAddOnApiV1CompatibilityPatch,
        yandexVoiceOverTranslationResourcePatch,
        yandexVoiceOverTranslationBytecodePatch,
    )
    execute { }
}
