package com.digitalclockpro.ringtone

import com.digitalclockpro.data.ringtone.RingtoneFileRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RingtoneFileRulesTest {

    @get:Rule val tempFolder = TemporaryFolder()

    // ---------------------------------------------------------------- size

    @Test
    fun `rejects a file larger than 25 MB`() {
        val oversize = RingtoneFileRules.MAX_FILE_BYTES + 1
        assertFalse(RingtoneFileRules.isSizeAllowed(oversize))
        assertFalse(RingtoneFileRules.isSizeAllowed(50L * 1024 * 1024))
    }

    @Test
    fun `rejects an empty file and accepts the boundary size`() {
        assertFalse(RingtoneFileRules.isSizeAllowed(0))
        assertFalse(RingtoneFileRules.isSizeAllowed(-1))
        assertTrue(RingtoneFileRules.isSizeAllowed(1))
        assertTrue(RingtoneFileRules.isSizeAllowed(RingtoneFileRules.MAX_FILE_BYTES))
    }

    // ---------------------------------------------------------------- extension / type

    @Test
    fun `rejects an unsupported extension`() {
        assertFalse(RingtoneFileRules.isSupportedType(null, "payload.apk"))
        assertFalse(RingtoneFileRules.isSupportedType("application/pdf", "manual.pdf"))
        assertFalse(RingtoneFileRules.isSupportedType("video/mp4", "clip.mkv"))
    }

    @Test
    fun `accepts supported audio extensions and mime types`() {
        listOf("song.mp3", "tone.wav", "beep.ogg", "clip.m4a", "hifi.flac", "voice.opus")
            .forEach { assertTrue(it, RingtoneFileRules.isSupportedType(null, it)) }
        assertTrue(RingtoneFileRules.isSupportedType("audio/mpeg", "no-extension"))
        assertTrue(RingtoneFileRules.isSupportedType("application/ogg", "stream"))
    }

    @Test
    fun `content sniffing accepts real audio headers and rejects executables`() {
        fun header(text: String) = text.toByteArray(Charsets.ISO_8859_1)
        assertTrue(RingtoneFileRules.looksLikeAudioContent(header("ID3\u0003abcdefghij")))
        assertTrue(RingtoneFileRules.looksLikeAudioContent(header("RIFF****WAVEfmt ")))
        assertTrue(RingtoneFileRules.looksLikeAudioContent(header("OggS\u0000abcdefghijk")))
        assertTrue(RingtoneFileRules.looksLikeAudioContent(header("fLaC\u0000abcdefghijk")))
        assertTrue(RingtoneFileRules.looksLikeAudioContent(header("****ftypM4A abcd")))

        assertFalse(RingtoneFileRules.looksLikeAudioContent(header("PK\u0003\u0004zipdata1234")))
        assertFalse(RingtoneFileRules.looksLikeAudioContent(header("\u007FELF\u0002\u0001abcdefg")))
        assertFalse(RingtoneFileRules.looksLikeAudioContent(ByteArray(2)))
    }

    // ---------------------------------------------------------------- names

    @Test
    fun `sanitizes unsafe characters while keeping unicode letters`() {
        assertEquals("ringtone.mp3", RingtoneFileRules.sanitizeFileName("../../ringtone.mp3"))
        // Only the final path segment is kept — directories in the name are discarded, never
        // flattened into the file name.
        assertEquals("c.mp3", RingtoneFileRules.sanitizeFileName("a/b\\c.mp3"))
        assertEquals("tone.mp3", RingtoneFileRules.sanitizeFileName("/storage/emulated/0/tone.mp3"))
        // Arabic and CJK names must survive intact.
        assertEquals("نغمة المنبه.mp3", RingtoneFileRules.sanitizeFileName("نغمة المنبه.mp3"))
        assertEquals("闹钟.ogg", RingtoneFileRules.sanitizeFileName("闹钟.ogg"))
    }

    @Test
    fun `blocks hidden files trailing dots and control characters`() {
        assertFalse(RingtoneFileRules.sanitizeFileName(".hidden.mp3").startsWith("."))
        assertFalse(RingtoneFileRules.sanitizeFileName("tone.mp3...").endsWith("."))
        assertFalse(RingtoneFileRules.sanitizeFileName("be\u0000ep.mp3").contains('\u0000'))
        assertTrue(RingtoneFileRules.sanitizeFileName("   ").startsWith("ringtone"))
    }

    @Test
    fun `truncates excessively long names but keeps the extension`() {
        val name = "x".repeat(500) + ".mp3"
        val safe = RingtoneFileRules.sanitizeFileName(name)
        assertTrue(safe.endsWith(".mp3"))
        assertTrue(safe.length <= RingtoneFileRules.MAX_NAME_CHARS + 5)
    }

    // ---------------------------------------------------------------- duplicates

    @Test
    fun `duplicate imports are numbered instead of overwriting`() {
        val existing = mutableSetOf("beep.mp3")
        val second = RingtoneFileRules.uniqueName("beep.mp3") { it in existing }
        assertEquals("beep (1).mp3", second)

        existing += second
        val third = RingtoneFileRules.uniqueName("beep.mp3") { it in existing }
        assertEquals("beep (2).mp3", third)
        assertNotEquals(second, third)
    }

    @Test
    fun `unique name is unchanged when free`() {
        assertEquals("fresh.wav", RingtoneFileRules.uniqueName("fresh.wav") { false })
    }

    // ---------------------------------------------------------------- path confinement

    @Test
    fun `only direct children of the ringtones directory are allowed`() {
        val ringtones = tempFolder.newFolder("ringtones")
        val inside = File(ringtones, "ok.mp3")
        assertTrue(RingtoneFileRules.isInsideDirectory(inside, ringtones))
    }

    @Test
    fun `path traversal and foreign directories are rejected`() {
        val root = tempFolder.newFolder("root")
        val ringtones = File(root, "ringtones").apply { mkdirs() }
        val outside = File(root, "secret.db")
        val traversal = File(ringtones, "../secret.db")
        val nested = File(ringtones, "sub/deep.mp3")

        assertFalse(RingtoneFileRules.isInsideDirectory(outside, ringtones))
        assertFalse(RingtoneFileRules.isInsideDirectory(traversal, ringtones))
        assertFalse(RingtoneFileRules.isInsideDirectory(nested, ringtones))
        assertFalse(RingtoneFileRules.isInsideDirectory(ringtones, ringtones))
    }
}
