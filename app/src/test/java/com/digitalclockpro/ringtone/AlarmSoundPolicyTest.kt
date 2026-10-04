package com.digitalclockpro.ringtone

import com.digitalclockpro.data.ringtone.AlarmSoundPolicy
import com.digitalclockpro.data.ringtone.AlarmSoundPolicy.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSoundPolicyTest {

    private val bundled = "android.resource://com.digitalclockpro/2131230001"
    private val systemDefault = "content://media/internal/audio/media/42"
    private val dpsPrefix = "/data/user_de/0/com.digitalclockpro/files"
    private val importedTone = "file://$dpsPrefix/ringtones/wake.mp3"

    @Test
    fun `uses the user sound when readable and unlocked`() {
        val result = AlarmSoundPolicy.resolve(
            storedUri = importedTone,
            userUnlocked = true,
            isDirectBootSafe = true,
            isReadable = true,
            systemDefaultUri = systemDefault,
            bundledUri = bundled
        )
        assertEquals(Source.USER_CHOICE, result.source)
        assertEquals(importedTone, result.uri)
    }

    // ---------------------------------------------------------------- Direct Boot

    @Test
    fun `direct boot plays an imported tone stored in device protected storage`() {
        val result = AlarmSoundPolicy.resolve(
            storedUri = importedTone,
            userUnlocked = false,          // before the first unlock
            isDirectBootSafe = true,
            isReadable = true,
            systemDefaultUri = systemDefault,
            bundledUri = bundled
        )
        assertEquals(Source.USER_CHOICE, result.source)
    }

    @Test
    fun `direct boot falls back to the bundled tone for a MediaStore sound`() {
        val result = AlarmSoundPolicy.resolve(
            storedUri = "content://media/external/audio/media/7",
            userUnlocked = false,
            isDirectBootSafe = false,
            isReadable = false,            // provider unavailable before unlock
            systemDefaultUri = systemDefault,
            bundledUri = bundled
        )
        assertEquals(Source.BUNDLED_FALLBACK, result.source)
        assertEquals(bundled, result.uri)
        assertTrue(result.reason.contains("Direct Boot"))
    }

    @Test
    fun `direct boot never selects the system default because it is credential protected`() {
        val result = AlarmSoundPolicy.resolve(
            storedUri = null,
            userUnlocked = false,
            isDirectBootSafe = false,
            isReadable = false,
            systemDefaultUri = systemDefault,
            bundledUri = bundled
        )
        assertEquals(Source.BUNDLED_FALLBACK, result.source)
    }

    // ---------------------------------------------------------------- unlocked fallbacks

    @Test
    fun `missing custom file falls back to the system default after unlock`() {
        val result = AlarmSoundPolicy.resolve(
            storedUri = importedTone,
            userUnlocked = true,
            isDirectBootSafe = true,
            isReadable = false,            // user deleted the file
            systemDefaultUri = systemDefault,
            bundledUri = bundled
        )
        assertEquals(Source.SYSTEM_DEFAULT, result.source)
        assertEquals(systemDefault, result.uri)
    }

    @Test
    fun `falls back to the bundled tone when the device has no default ringtone`() {
        val result = AlarmSoundPolicy.resolve(
            storedUri = null,
            userUnlocked = true,
            isDirectBootSafe = false,
            isReadable = false,
            systemDefaultUri = null,
            bundledUri = bundled
        )
        assertEquals(Source.BUNDLED_FALLBACK, result.source)
    }

    // ---------------------------------------------------------------- uri classification

    @Test
    fun `direct boot safety is detected from the storage path`() {
        assertTrue(AlarmSoundPolicy.isDirectBootSafeUri(importedTone, dpsPrefix))
        assertTrue(AlarmSoundPolicy.isDirectBootSafeUri(bundled, dpsPrefix))
        assertFalse(
            AlarmSoundPolicy.isDirectBootSafeUri(
                "file:///data/data/com.digitalclockpro/files/ringtones/x.mp3", dpsPrefix
            )
        )
        assertFalse(AlarmSoundPolicy.isDirectBootSafeUri(systemDefault, dpsPrefix))
        assertFalse(AlarmSoundPolicy.isDirectBootSafeUri(null, dpsPrefix))
        assertFalse(AlarmSoundPolicy.isDirectBootSafeUri(importedTone, null))
    }
}
