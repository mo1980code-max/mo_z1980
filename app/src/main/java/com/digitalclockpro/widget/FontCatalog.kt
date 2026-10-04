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
    val fallbackFamily: String = "monospace",
    /**
     * Style applied to [fallbackFamily]. Without this, two thirds of the catalogue collapsed
     * onto plain monospace and every chip previewed identically — the picker looked broken.
     */
    val fallbackStyle: Int = Typeface.NORMAL
)

object FontCatalog {

    /**
     * The 33 faces the app is designed around.
     *
     * **None of these `.ttf` files are in the repository yet** — see `docs/FONTS.md` for the
     * exact file names and licences. Until one is dropped into `res/font/` (file name ==
     * [FontSpec.resName]) the catalogue resolves it with `getIdentifier`, gets 0, and falls
     * back to [fallbackFamily] + [fallbackStyle]. That degrades without breaking the build,
     * and [isInstalled] lets the UI admit which entries are substitutes.
     */
    val fonts: List<FontSpec> = listOf(
        FontSpec("dseg7_classic", "DSEG7 Classic", "dseg7_classic", "7-Segment"),
        FontSpec("dseg7_modern", "DSEG7 Modern", "dseg7_modern", "7-Segment"),
        FontSpec("dseg7_italic", "DSEG7 Italic", "dseg7_italic", "7-Segment", "monospace", Typeface.ITALIC),
        FontSpec("dseg14_classic", "DSEG14 Classic", "dseg14_classic", "14-Segment", "monospace", Typeface.BOLD),
        FontSpec("dseg14_modern", "DSEG14 Modern", "dseg14_modern", "14-Segment", "monospace", Typeface.BOLD),
        FontSpec("digital_7", "Digital-7", "digital_7", "7-Segment"),
        FontSpec("digital_7_mono", "Digital-7 Mono", "digital_7_mono", "7-Segment", "monospace", Typeface.BOLD),
        FontSpec("led_counter", "LED Counter", "led_counter", "LED", "monospace", Typeface.BOLD),
        FontSpec("led_dot_matrix", "LED Dot Matrix", "led_dot_matrix", "LED"),
        FontSpec("dot_matrix", "Dot Matrix", "dot_matrix", "LED"),
        FontSpec("five_by_seven", "5x7 Pixel", "five_by_seven", "LED"),
        FontSpec("vcr_osd_mono", "VCR OSD Mono", "vcr_osd_mono", "Retro", "serif-monospace", Typeface.NORMAL),
        FontSpec("flip_clock", "Flip Clock", "flip_clock", "Retro", "sans-serif-medium", Typeface.NORMAL),
        FontSpec("nixie_tube", "Nixie Tube", "nixie_tube", "Retro", "serif", Typeface.NORMAL),
        FontSpec("neon_glow", "Neon Glow", "neon_glow", "Neon"),
        FontSpec("neon_tubes", "Neon Tubes", "neon_tubes", "Neon", "sans-serif-light", Typeface.NORMAL),
        FontSpec("cyberpunk", "Cyberpunk", "cyberpunk", "Neon", "sans-serif-black", Typeface.NORMAL),
        FontSpec("orbitron", "Orbitron", "orbitron", "Futuristic", "sans-serif"),
        FontSpec("rajdhani", "Rajdhani", "rajdhani", "Futuristic", "sans-serif"),
        FontSpec("audiowide", "Audiowide", "audiowide", "Futuristic", "sans-serif-medium", Typeface.NORMAL),
        FontSpec("exo_two", "Exo 2", "exo_two", "Futuristic", "sans-serif"),
        FontSpec("michroma", "Michroma", "michroma", "Futuristic", "sans-serif-black", Typeface.NORMAL),
        FontSpec("share_tech_mono", "Share Tech Mono", "share_tech_mono", "Mono"),
        FontSpec("jetbrains_mono", "JetBrains Mono", "jetbrains_mono", "Mono"),
        FontSpec("roboto_mono", "Roboto Mono", "roboto_mono", "Mono"),
        FontSpec("space_mono", "Space Mono", "space_mono", "Mono"),
        FontSpec("ibm_plex_mono", "IBM Plex Mono", "ibm_plex_mono", "Mono"),
        FontSpec("inter_tight", "Inter Tight", "inter_tight", "Minimal", "sans-serif-light", Typeface.NORMAL),
        FontSpec("montserrat", "Montserrat", "montserrat", "Minimal", "sans-serif"),
        FontSpec("poppins", "Poppins", "poppins", "Minimal", "sans-serif-medium", Typeface.NORMAL),
        FontSpec("bebas_neue", "Bebas Neue", "bebas_neue", "Minimal", "sans-serif-condensed"),
        FontSpec("oswald", "Oswald", "oswald", "Minimal", "sans-serif-condensed"),
        FontSpec("lexend_deca", "Lexend Deca", "lexend_deca", "Minimal", "sans-serif-thin", Typeface.NORMAL)
    )

    private val cache = ConcurrentHashMap<String, Typeface>()
    private val installed = ConcurrentHashMap<String, Boolean>()

    fun byKey(key: String): FontSpec = fonts.firstOrNull { it.key == key } ?: fonts.first()

    /**
     * Whether the real `.ttf` for [spec] is actually present in `res/font/`.
     *
     * None of them are, at the time of writing: the catalogue lists the faces the app intends
     * to ship, and [typeface] silently substitutes a system family for each. Silent is the
     * problem — the Studio advertised "Bundled fonts (33)" while every chip rendered the same.
     * Exposing the truth lets the picker label a substituted face instead of lying about it.
     */
    fun isInstalled(context: Context, spec: FontSpec): Boolean =
        installed.getOrPut(spec.key) {
            context.resources.getIdentifier(spec.resName, "font", context.packageName) != 0
        }

    /** How many catalogue entries resolve to a real bundled file. */
    fun installedCount(context: Context): Int = fonts.count { isInstalled(context, it) }

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
            bundled
                ?: Typeface.create(spec.fallbackFamily, spec.fallbackStyle)
                ?: Typeface.MONOSPACE
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
