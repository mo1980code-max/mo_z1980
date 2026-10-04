package com.digitalclockpro.widget

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Bundled digital/modern font library + user-imported `.ttf` / `.otf` support.
 *
 * Bundled faces live in `res/font/`; add a `.ttf` there and one [FontSpec] line to publish it in
 * the Widget Studio. Imported fonts are copied into `filesDir/fonts/` on import so widgets keep
 * working after the source Uri permission is revoked.
 */
data class FontSpec(
    val key: String,
    val displayName: String,
    /** File name (without extension) of the font in `res/font/`. */
    val resName: String,
    val category: String,
    /** Fallback system family used until the .ttf is dropped into res/font. */
    val fallbackFamily: String = "monospace"
)

object FontCatalog {

    /**
     * 30+ curated digital / modern faces. Drop the matching `.ttf` into `res/font/`
     * (file name == [FontSpec.resName]); the catalogue resolves it at runtime with
     * `getIdentifier`, so a missing file degrades gracefully to [FontSpec.fallbackFamily]
     * instead of breaking the build.
     */
    val fonts: List<FontSpec> = listOf(
        FontSpec("dseg7_classic", "DSEG7 Classic", "dseg7_classic", "7-Segment"),
        FontSpec("dseg7_modern", "DSEG7 Modern", "dseg7_modern", "7-Segment"),
        FontSpec("dseg7_italic", "DSEG7 Italic", "dseg7_italic", "7-Segment"),
        FontSpec("dseg14_classic", "DSEG14 Classic", "dseg14_classic", "14-Segment"),
        FontSpec("dseg14_modern", "DSEG14 Modern", "dseg14_modern", "14-Segment"),
        FontSpec("digital_7", "Digital-7", "digital_7", "7-Segment"),
        FontSpec("digital_7_mono", "Digital-7 Mono", "digital_7_mono", "7-Segment"),
        FontSpec("led_counter", "LED Counter", "led_counter", "LED"),
        FontSpec("led_dot_matrix", "LED Dot Matrix", "led_dot_matrix", "LED"),
        FontSpec("dot_matrix", "Dot Matrix", "dot_matrix", "LED"),
        FontSpec("five_by_seven", "5x7 Pixel", "five_by_seven", "LED"),
        FontSpec("vcr_osd_mono", "VCR OSD Mono", "vcr_osd_mono", "Retro"),
        FontSpec("flip_clock", "Flip Clock", "flip_clock", "Retro"),
        FontSpec("nixie_tube", "Nixie Tube", "nixie_tube", "Retro"),
        FontSpec("neon_glow", "Neon Glow", "neon_glow", "Neon"),
        FontSpec("neon_tubes", "Neon Tubes", "neon_tubes", "Neon"),
        FontSpec("cyberpunk", "Cyberpunk", "cyberpunk", "Neon"),
        FontSpec("orbitron", "Orbitron", "orbitron", "Futuristic", "sans-serif"),
        FontSpec("rajdhani", "Rajdhani", "rajdhani", "Futuristic", "sans-serif"),
        FontSpec("audiowide", "Audiowide", "audiowide", "Futuristic", "sans-serif"),
        FontSpec("exo_two", "Exo 2", "exo_two", "Futuristic", "sans-serif"),
        FontSpec("michroma", "Michroma", "michroma", "Futuristic", "sans-serif"),
        FontSpec("share_tech_mono", "Share Tech Mono", "share_tech_mono", "Mono"),
        FontSpec("jetbrains_mono", "JetBrains Mono", "jetbrains_mono", "Mono"),
        FontSpec("roboto_mono", "Roboto Mono", "roboto_mono", "Mono"),
        FontSpec("space_mono", "Space Mono", "space_mono", "Mono"),
        FontSpec("ibm_plex_mono", "IBM Plex Mono", "ibm_plex_mono", "Mono"),
        FontSpec("inter_tight", "Inter Tight", "inter_tight", "Minimal", "sans-serif"),
        FontSpec("montserrat", "Montserrat", "montserrat", "Minimal", "sans-serif"),
        FontSpec("poppins", "Poppins", "poppins", "Minimal", "sans-serif"),
        FontSpec("bebas_neue", "Bebas Neue", "bebas_neue", "Minimal", "sans-serif-condensed"),
        FontSpec("oswald", "Oswald", "oswald", "Minimal", "sans-serif-condensed"),
        FontSpec("lexend_deca", "Lexend Deca", "lexend_deca", "Minimal", "sans-serif")
    )

    private val cache = ConcurrentHashMap<String, Typeface>()

    fun byKey(key: String): FontSpec = fonts.firstOrNull { it.key == key } ?: fonts.first()

    fun typeface(context: Context, key: String, customUri: String?): Typeface {
        customUri?.let { uri ->
            cache["custom:$uri"]?.let { return it }
            loadCustom(context, uri)?.let { cache["custom:$uri"] = it; return it }
        }
        return cache.getOrPut(key) {
            val spec = byKey(key)
            val resId = context.resources.getIdentifier(spec.resName, "font", context.packageName)
            val bundled = if (resId != 0) {
                runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
            } else null
            bundled ?: Typeface.create(spec.fallbackFamily, Typeface.NORMAL) ?: Typeface.MONOSPACE
        }
    }

    /** Copies a picked document into app storage and returns the stable file:// Uri. */
    fun importFont(context: Context, source: Uri, fileName: String): String? = runCatching {
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        val target = File(dir, fileName.substringAfterLast('/'))
        context.contentResolver.openInputStream(source)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        Uri.fromFile(target).toString()
    }.getOrNull()

    private fun loadCustom(context: Context, uriString: String): Typeface? = runCatching {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") Typeface.createFromFile(uri.path!!)
        else context.contentResolver.openFileDescriptor(uri, "r")!!.use {
            Typeface.Builder(it.fileDescriptor).build()
        }
    }.getOrNull()
}
