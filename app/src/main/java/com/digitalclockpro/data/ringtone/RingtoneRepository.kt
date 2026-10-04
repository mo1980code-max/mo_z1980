package com.digitalclockpro.data.ringtone

import android.content.Context
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.digitalclockpro.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A selectable alarm sound. */
data class AlarmSound(
    val uri: String?,          // null == silent
    val title: String,
    val source: Source
) {
    enum class Source { SILENT, SYSTEM, IMPORTED }
}

/**
 * Alarm sound catalogue + **custom audio import**.
 *
 * A picked `content://` Uri is NOT stored directly: the grant can be revoked, the provider can
 * disappear (file moved/deleted, SD card unmounted, the app that shared it uninstalled), and a
 * Direct-Boot alarm cannot resolve a credential-protected provider at all. Instead the file is
 * copied once into `filesDir/ringtones/` — the same strategy used for imported fonts — and the
 * alarm stores a stable `file://` Uri that [com.digitalclockpro.alarm.AlarmSoundPlayer] can
 * always open.
 */
@Singleton
class RingtoneRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val ringtonesDir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    // ------------------------------------------------------------------ import

    /**
     * Copies the picked audio file into app storage.
     *
     * @return the imported [AlarmSound], or null when the file is unreadable, empty, too large,
     *         or not an audio type.
     */
    suspend fun importFromUri(source: Uri): AlarmSound? = withContext(io) {
        val displayName = queryDisplayName(source) ?: "ringtone_${System.currentTimeMillis()}"
        val mimeType = context.contentResolver.getType(source).orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        if (!isSupportedAudio(mimeType, extension)) {
            Log.w(TAG, "Rejected import: mime=$mimeType ext=$extension")
            return@withContext null
        }

        val target = File(ringtonesDir, sanitize(displayName)).uniquify()
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching -1L
            target.length()
        }.getOrElse { error ->
            Log.e(TAG, "Failed to copy ringtone", error)
            target.delete()
            return@withContext null
        }

        when {
            copied <= 0L -> {
                target.delete(); null
            }
            copied > MAX_FILE_BYTES -> {
                Log.w(TAG, "Rejected import: ${copied / 1_048_576} MB exceeds limit")
                target.delete(); null
            }
            else -> AlarmSound(
                uri = Uri.fromFile(target).toString(),
                title = displayName.substringBeforeLast('.'),
                source = AlarmSound.Source.IMPORTED
            )
        }
    }

    /** Previously imported sounds, newest first. */
    suspend fun importedSounds(): List<AlarmSound> = withContext(io) {
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

    suspend fun deleteImported(uriString: String): Boolean = withContext(io) {
        val file = runCatching { Uri.parse(uriString).path?.let(::File) }.getOrNull()
        // Never delete outside our own directory.
        if (file == null || file.parentFile?.canonicalPath != ringtonesDir.canonicalPath) {
            return@withContext false
        }
        file.delete()
    }

    // ------------------------------------------------------------------ system sounds

    /** System alarm ringtones, used to populate the in-app picker. */
    suspend fun systemAlarmSounds(): List<AlarmSound> = withContext(io) {
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
        uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString(),
        title = "Default alarm sound",
        source = AlarmSound.Source.SYSTEM
    )

    /** True when the stored Uri still resolves; used to warn the user in the alarm editor. */
    suspend fun isPlayable(uriString: String?): Boolean = withContext(io) {
        if (uriString.isNullOrBlank()) return@withContext true   // silent is valid
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext false
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).canRead() } ?: false
            else -> runCatching {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }.getOrDefault(false)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment
    }.getOrNull()

    private fun isSupportedAudio(mimeType: String, extension: String): Boolean =
        mimeType.startsWith("audio/") ||
            mimeType == "application/ogg" ||
            extension in SUPPORTED_EXTENSIONS

    /** Strips path separators and other characters that could escape our directory. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").take(80).ifBlank { "ringtone.mp3" }

    /** Avoids overwriting an existing import with the same name. */
    private fun File.uniquify(): File {
        if (!exists()) return this
        val base = nameWithoutExtension
        val ext = extension.ifBlank { "mp3" }
        var index = 1
        var candidate: File
        do {
            candidate = File(parentFile, "$base ($index).$ext")
            index++
        } while (candidate.exists() && index < 100)
        return candidate
    }

    private companion object {
        const val TAG = "RingtoneRepository"
        const val DIR_NAME = "ringtones"
        const val MAX_FILE_BYTES = 25L * 1024 * 1024   // 25 MB
        val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "oga")
    }
}
