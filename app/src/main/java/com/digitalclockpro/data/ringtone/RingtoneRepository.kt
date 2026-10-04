package com.digitalclockpro.data.ringtone

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.digitalclockpro.R
import com.digitalclockpro.di.IoDispatcher
import com.digitalclockpro.di.deviceProtectedStorageContextCompat
import com.digitalclockpro.di.isUserUnlockedCompat
import com.digitalclockpro.domain.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A selectable alarm sound. */
data class AlarmSound(
    /** null = "use the system default"; [SILENT_URI] = play nothing. */
    val uri: String?,
    val title: String,
    val source: Source
) {
    enum class Source { SILENT, SYSTEM, IMPORTED }

    companion object {
        /**
         * Explicit sentinel for "no sound". A null Uri already means "fall back to the default
         * alarm tone" (the state of every freshly created alarm), so silence needs its own value.
         */
        const val SILENT_URI = "silent://none"

        /** Title is intentionally empty: the UI renders a localized label for silence. */
        fun silent() = AlarmSound(SILENT_URI, "", Source.SILENT)
    }
}

/**
 * Alarm sound catalogue, custom audio import and **Direct Boot safe playback resolution**.
 *
 * Imported files are copied into `deviceProtectedStorage/files/ringtones/` rather than kept as
 * `content://` Uris, because:
 *  - a SAF grant can be revoked, and the source file can be moved or deleted;
 *  - the alarm database lives in device-protected storage, so an alarm can fire **before the
 *    first unlock**, where credential-protected files and MediaStore providers are unreadable.
 *
 * [resolvePlayableAlarmUri] is the single entry point used by the player: it never throws and
 * always returns something audible.
 */
@Singleton
class RingtoneRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /** Device-protected so the file is readable during Direct Boot. */
    private val deviceContext: Context get() = context.deviceProtectedStorageContextCompat()

    private val ringtonesDir: File
        get() = File(deviceContext.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /** Legacy location used before Direct Boot support; migrated on first access. */
    private val legacyDir: File get() = File(context.filesDir, DIR_NAME)

    /** `android.resource://` Uri of the tone bundled in the APK — always readable. */
    val bundledFallbackUri: String
        get() = "android.resource://${context.packageName}/${R.raw.fallback_alarm}"

    // ------------------------------------------------------------------ playback resolution

    /**
     * Resolves the Uri that should actually be handed to ExoPlayer for [alarm].
     *
     * Order: the user's sound (when readable *and* reachable in the current boot state) ->
     * the system default alarm tone (only after unlock) -> the bundled tone.
     * Returns null only for an explicitly silent alarm (vibration-only).
     * Every downgrade is logged with its reason; the call never fails.
     */
    suspend fun resolvePlayableAlarmUri(alarm: Alarm): Uri? = withContext(io) {
        val stored = alarm.soundUri
        if (stored == AlarmSound.SILENT_URI) {
            Log.d(TAG, "Alarm ${alarm.id} is silent: vibration only")
            return@withContext null
        }
        val unlocked = context.isUserUnlockedCompat()
        val resolution = AlarmSoundPolicy.resolve(
            storedUri = stored,
            userUnlocked = unlocked,
            isDirectBootSafe = AlarmSoundPolicy.isDirectBootSafeUri(
                uri = stored,
                deviceProtectedPathPrefix = runCatching { deviceContext.filesDir.canonicalPath }
                    .getOrNull()
            ),
            isReadable = isPlayable(stored),
            systemDefaultUri = systemDefaultAlarmUri()?.toString(),
            bundledUri = bundledFallbackUri
        )

        if (resolution.source != AlarmSoundPolicy.Source.USER_CHOICE) {
            Log.w(
                TAG,
                "Alarm ${alarm.id}: falling back to ${resolution.source} (${resolution.reason})"
            )
        }
        Uri.parse(resolution.uri)
    }

    private fun systemDefaultAlarmUri(): Uri? = runCatching {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }.getOrNull()

    // ------------------------------------------------------------------ import

    /**
     * Copies a picked audio document into device-protected app storage.
     *
     * @param flags the `Intent` flags returned by the picker, used to persist the read grant
     *        while we stream the bytes (the copy itself makes the grant disposable afterwards).
     * @return the imported [AlarmSound], or null when rejected (type, size, unreadable).
     */
    suspend fun importFromUri(source: Uri, flags: Int = 0): AlarmSound? = withContext(io) {
        takePersistableReadPermission(source, flags)

        val displayName = queryDisplayName(source) ?: "ringtone_${System.currentTimeMillis()}.mp3"
        val mimeType = runCatching { context.contentResolver.getType(source) }.getOrNull()

        if (!RingtoneFileRules.isSupportedType(mimeType, displayName)) {
            Log.w(TAG, "Rejected import: unsupported type mime=$mimeType name=$displayName")
            return@withContext null
        }
        if (!hasAudioSignature(source)) {
            Log.w(TAG, "Rejected import: content does not look like audio ($displayName)")
            return@withContext null
        }

        val directory = ringtonesDir
        val safeName = RingtoneFileRules.uniqueName(
            desiredName = RingtoneFileRules.sanitizeFileName(displayName),
            exists = { name -> File(directory, name).exists() }
        )
        val target = File(directory, safeName)

        // Defence in depth: never write outside our own folder, whatever the provider returned.
        if (!RingtoneFileRules.isInsideDirectory(target, directory)) {
            Log.e(TAG, "Rejected import: resolved path escapes the ringtones directory")
            return@withContext null
        }

        val copiedBytes = runCatching {
            context.contentResolver.openInputStream(source).use { input ->
                if (input == null) return@runCatching -1L
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.length()
        }.getOrElse { error ->
            Log.e(TAG, "Failed to copy ringtone", error)
            target.delete()
            return@withContext null
        }

        if (!RingtoneFileRules.isSizeAllowed(copiedBytes)) {
            Log.w(TAG, "Rejected import: size $copiedBytes bytes outside allowed range")
            target.delete()
            return@withContext null
        }

        releasePersistableReadPermission(source, flags)

        AlarmSound(
            uri = Uri.fromFile(target).toString(),
            title = target.nameWithoutExtension,
            source = AlarmSound.Source.IMPORTED
        )
    }

    suspend fun importedSounds(): List<AlarmSound> = withContext(io) {
        migrateLegacyImports()
        ringtonesDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                AlarmSound(
                    uri = Uri.fromFile(it).toString(),
                    title = it.nameWithoutExtension,
                    source = AlarmSound.Source.IMPORTED
                )
            }
            .orEmpty()
    }

    /** Deletes an imported sound. Refuses any path that is not a direct child of our folder. */
    suspend fun deleteImported(uriString: String): Boolean = withContext(io) {
        val file = runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme != "file") null else uri.path?.let(::File)
        }.getOrNull() ?: return@withContext false

        if (!RingtoneFileRules.isInsideDirectory(file, ringtonesDir)) {
            Log.w(TAG, "Refused to delete a file outside the ringtones directory")
            return@withContext false
        }
        file.delete()
    }

    // ------------------------------------------------------------------ system sounds

    suspend fun systemAlarmSounds(): List<AlarmSound> = withContext(io) {
        if (!context.isUserUnlockedCompat()) return@withContext emptyList()
        val result = mutableListOf<AlarmSound>()
        runCatching {
            RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
                .cursor
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                        val id = cursor.getString(RingtoneManager.ID_COLUMN_INDEX)
                        val base = cursor.getString(RingtoneManager.URI_COLUMN_INDEX)
                        result += AlarmSound("$base/$id", title, AlarmSound.Source.SYSTEM)
                    }
                }
        }.onFailure { Log.w(TAG, "Unable to list system alarms", it) }
        result
    }

    fun defaultAlarmSound(): AlarmSound = AlarmSound(
        uri = systemDefaultAlarmUri()?.toString(),
        title = context.getString(R.string.sound_default),
        source = AlarmSound.Source.SYSTEM
    )

    fun bundledFallbackSound(): AlarmSound = AlarmSound(
        uri = bundledFallbackUri,
        title = context.getString(R.string.sound_bundled_fallback),
        source = AlarmSound.Source.SYSTEM
    )

    /** True when the stored Uri still resolves; used to warn the user in the alarm editor. */
    suspend fun isPlayable(uriString: String?): Boolean = withContext(io) {
        // null = system default, sentinel = silent: both are always "valid".
        if (uriString.isNullOrBlank() || uriString == AlarmSound.SILENT_URI) {
            return@withContext true
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext false
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).canRead() } ?: false
            "android.resource" -> true
            else -> runCatching {
                context.contentResolver.openInputStream(uri).use { it != null }
            }.getOrDefault(false)
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Keeps the read grant alive across process death while we stream the file. */
    private fun takePersistableReadPermission(uri: Uri, flags: Int) {
        if (uri.scheme != "content") return
        if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure { Log.d(TAG, "Uri grant is not persistable: ${it.message}") }
    }

    /** The bytes are ours now; releasing keeps us under the system's persisted-grant limit. */
    private fun releasePersistableReadPermission(uri: Uri, flags: Int) {
        if (uri.scheme != "content") return
        if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun hasAudioSignature(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return false
            val header = ByteArray(HEADER_BYTES)
            val read = input.read(header)
            if (read <= 0) return false
            RingtoneFileRules.looksLikeAudioContent(header.copyOf(read))
        }
    }.getOrDefault(false)

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            } ?: uri.lastPathSegment
    }.getOrNull()

    /** Moves pre-Direct-Boot imports into device-protected storage, once. */
    private fun migrateLegacyImports() {
        val legacy = legacyDir
        if (!legacy.isDirectory) return
        val target = ringtonesDir
        if (legacy.canonicalPath == target.canonicalPath) return

        legacy.listFiles()?.forEach { file ->
            runCatching {
                val destination = File(
                    target,
                    RingtoneFileRules.uniqueName(file.name) { File(target, it).exists() }
                )
                if (file.renameTo(destination)) return@runCatching
                file.inputStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                file.delete()
            }.onFailure { Log.w(TAG, "Could not migrate ${file.name}", it) }
        }
        legacy.delete()
    }

    private companion object {
        const val TAG = "RingtoneRepository"
        const val DIR_NAME = "ringtones"
        const val HEADER_BYTES = 16
    }
}
