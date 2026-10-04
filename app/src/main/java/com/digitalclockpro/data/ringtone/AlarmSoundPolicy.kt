package com.digitalclockpro.data.ringtone

/**
 * Pure decision table for "which audio should actually play right now?".
 *
 * Extracted from [RingtoneRepository] so the Direct Boot behaviour can be unit-tested on the JVM
 * without a device.
 *
 * ### Why this exists
 * The alarm database lives in device-protected storage, so an alarm can fire **before the user
 * has unlocked the phone after a reboot**. In that window:
 *  - `content://media/...` and any `content://` provider backed by credential-protected storage
 *    cannot be opened — including the system default alarm ringtone;
 *  - files under the normal `filesDir` (credential-protected) are unreadable too.
 *
 * Imported ringtones are therefore stored in device-protected storage, and anything we still
 * cannot read degrades to a tone bundled in the APK, which is always readable.
 */
object AlarmSoundPolicy {

    /** Ordered outcome of [resolve], mostly for logging and tests. */
    enum class Source {
        /** The user's chosen sound is readable right now. */
        USER_CHOICE,
        /** User choice unusable, but the system default ringtone is reachable. */
        SYSTEM_DEFAULT,
        /** Nothing else is readable (typically Direct Boot) -> bundled res/raw tone. */
        BUNDLED_FALLBACK
    }

    data class Resolution(val uri: String, val source: Source, val reason: String)

    /**
     * @param storedUri          the Uri saved on the alarm (null = the user picked "Silent";
     *                           callers must not call this function at all in that case).
     * @param userUnlocked       `UserManager.isUserUnlocked` — false during Direct Boot.
     * @param isDirectBootSafe   true when [storedUri] points at device-protected storage or an
     *                           `android.resource://` APK asset.
     * @param isReadable         real readability probe (file exists / provider opens).
     * @param systemDefaultUri   `RingtoneManager.getDefaultUri(TYPE_ALARM)`, may be null.
     * @param bundledUri         `android.resource://<pkg>/raw/fallback_alarm`.
     */
    fun resolve(
        storedUri: String?,
        userUnlocked: Boolean,
        isDirectBootSafe: Boolean,
        isReadable: Boolean,
        systemDefaultUri: String?,
        bundledUri: String
    ): Resolution {
        if (!storedUri.isNullOrBlank()) {
            val usableNow = isReadable && (userUnlocked || isDirectBootSafe)
            if (usableNow) {
                return Resolution(storedUri, Source.USER_CHOICE, "stored sound is readable")
            }
        }

        val reason = when {
            storedUri.isNullOrBlank() -> "no sound stored"
            !userUnlocked -> "device locked after reboot (Direct Boot) and sound is not " +
                "in device-protected storage"
            else -> "stored sound is missing or unreadable"
        }

        // The system default is a MediaStore/content Uri: unavailable before the first unlock.
        if (userUnlocked && !systemDefaultUri.isNullOrBlank()) {
            return Resolution(systemDefaultUri, Source.SYSTEM_DEFAULT, "$reason -> system default")
        }
        return Resolution(bundledUri, Source.BUNDLED_FALLBACK, "$reason -> bundled tone")
    }

    /** `file://` paths inside device-protected storage and APK resources survive Direct Boot. */
    fun isDirectBootSafeUri(uri: String?, deviceProtectedPathPrefix: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        if (uri.startsWith("android.resource://")) return true
        if (!uri.startsWith("file://")) return false
        val prefix = deviceProtectedPathPrefix ?: return false
        val path = uri.removePrefix("file://")
        return path.startsWith(prefix)
    }
}
