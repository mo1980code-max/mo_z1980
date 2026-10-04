package com.digitalclockpro.data.ringtone

import java.io.File
import java.util.Locale

/**
 * Pure (Android-free) validation rules for imported ringtones, kept separate from
 * [RingtoneRepository] so every branch is covered by plain JVM unit tests.
 */
object RingtoneFileRules {

    const val MAX_FILE_BYTES = 25L * 1024 * 1024   // 25 MB
    const val MAX_NAME_CHARS = 80

    val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "opus", "mp4")

    /** Characters that are illegal or dangerous on FAT/ext4/SAF-backed file systems. */
    private val ILLEGAL_CHARS = charArrayOf(
        '/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000'
    )

    sealed interface Rejection {
        data object UnsupportedType : Rejection
        data object Empty : Rejection
        data class TooLarge(val bytes: Long) : Rejection
        data object Unreadable : Rejection
    }

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    /**
     * Accepts a file when the MIME type *or* the extension looks like audio.
     * Content sniffing ([looksLikeAudioContent]) is applied separately on the real bytes.
     */
    fun isSupportedType(mimeType: String?, fileName: String): Boolean {
        val mime = mimeType.orEmpty().lowercase(Locale.ROOT)
        if (mime.startsWith("audio/") || mime == "application/ogg" || mime == "application/x-ogg") {
            return true
        }
        return extensionOf(fileName) in SUPPORTED_EXTENSIONS
    }

    fun isSizeAllowed(bytes: Long): Boolean = bytes in 1..MAX_FILE_BYTES

    /**
     * Magic-number sniffing — a cheap guard against a renamed `.exe`/`.apk` or a provider lying
     * about its MIME type. Unknown headers are **accepted** (many valid codecs have no stable
     * signature); only well-known non-audio containers are rejected outright.
     */
    fun looksLikeAudioContent(header: ByteArray): Boolean {
        if (header.size < 4) return false
        fun ascii(offset: Int, text: String): Boolean =
            header.size >= offset + text.length &&
                String(header, offset, text.length, Charsets.ISO_8859_1) == text

        return when {
            ascii(0, "ID3") -> true                                   // MP3 with ID3 tag
            ascii(0, "RIFF") && ascii(8, "WAVE") -> true              // WAV
            ascii(0, "OggS") -> true                                  // Ogg Vorbis / Opus
            ascii(0, "fLaC") -> true                                  // FLAC
            ascii(4, "ftyp") -> true                                  // M4A / MP4 audio
            ascii(0, "FORM") -> true                                  // AIFF
            // Bare MP3/AAC-ADTS frame sync: 11 set bits.
            header[0].toInt() and 0xFF == 0xFF &&
                header[1].toInt() and 0xE0 == 0xE0 -> true
            // Explicitly reject executables/archives that slipped past the MIME check.
            ascii(0, "PK") || ascii(0, "\u007FELF") || ascii(0, "MZ") || ascii(0, "dex\n") -> false
            else -> true
        }
    }

    /**
     * Unicode-safe file name: keeps letters/digits from any script (Arabic, CJK…), strips path
     * separators, control characters and reserved symbols, collapses whitespace and trims the
     * trailing dots/spaces that Windows-derived file systems reject.
     */
    fun sanitizeFileName(rawName: String, fallbackExtension: String = "mp3"): String {
        // Keep only the final path segment first: a provider may return "../../secret" or a
        // full path, and sanitising in place would leave "_.._secret" instead of "secret".
        val baseName = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')

        val normalized = baseName
            .map { ch ->
                when {
                    ch in ILLEGAL_CHARS -> '_'
                    ch.isISOControl() -> '_'
                    else -> ch
                }
            }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('.')                       // no hidden files, no "../"
            .trimEnd('.', ' ')

        val base = normalized.substringBeforeLast('.', normalized).take(MAX_NAME_CHARS)
        val ext = extensionOf(normalized).takeIf { it.isNotEmpty() && it.length <= 8 }
            ?: fallbackExtension

        val safeBase = base.ifBlank { "ringtone" }
        return "$safeBase.$ext"
    }

    /**
     * Appends " (n)" until the name is free, so a second import of "beep.mp3" never silently
     * overwrites the first.
     */
    fun uniqueName(desiredName: String, exists: (String) -> Boolean): String {
        if (!exists(desiredName)) return desiredName
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = extensionOf(desiredName).ifBlank { "mp3" }
        for (index in 1 until 1000) {
            val candidate = "$base ($index).$ext"
            if (!exists(candidate)) return candidate
        }
        return "$base (${System.currentTimeMillis()}).$ext"
    }

    /**
     * Guards every delete/write: the resolved path must be a direct child of [directory].
     * Blocks `../` traversal, symlinked parents and absolute paths from a crafted Uri.
     */
    fun isInsideDirectory(candidate: File, directory: File): Boolean = runCatching {
        val parent = directory.canonicalFile
        val child = candidate.canonicalFile
        child.parentFile?.canonicalFile == parent && child != parent
    }.getOrDefault(false)
}
