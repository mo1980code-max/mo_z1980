package com.digitalclockpro.oem

import com.digitalclockpro.core.util.OemPowerSettings
import com.digitalclockpro.core.util.OemPowerSettings.Vendor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vendor detection and deep-link selection are pure functions over [OemPowerSettings.VendorComponent],
 * so these run as plain JVM tests — no Robolectric, no stubbed `Intent`/`ComponentName`.
 */
class OemPowerSettingsTest {

    // ---------------------------------------------------------------- vendor detection

    @Test
    fun `detects xiaomi family`() {
        assertEquals(Vendor.XIAOMI, OemPowerSettings.detectVendor("Xiaomi", "Redmi"))
        assertEquals(Vendor.XIAOMI, OemPowerSettings.detectVendor("xiaomi", "POCO"))
        assertEquals(Vendor.XIAOMI, OemPowerSettings.detectVendor("Redmi", "Redmi"))
    }

    @Test
    fun `detects the other supported vendors`() {
        assertEquals(Vendor.HUAWEI, OemPowerSettings.detectVendor("HUAWEI", "Honor"))
        assertEquals(Vendor.SAMSUNG, OemPowerSettings.detectVendor("samsung", "Samsung"))
        assertEquals(Vendor.OPPO, OemPowerSettings.detectVendor("OPPO", "realme"))
        assertEquals(Vendor.ONEPLUS, OemPowerSettings.detectVendor("OnePlus", "OnePlus"))
        assertEquals(Vendor.VIVO, OemPowerSettings.detectVendor("vivo", "iQOO"))
        assertEquals(Vendor.ASUS, OemPowerSettings.detectVendor("asus", "ASUS"))
        assertEquals(Vendor.LETV, OemPowerSettings.detectVendor("LeEco", "LeEco"))
    }

    @Test
    fun `unknown and blank manufacturers degrade to OTHER`() {
        assertEquals(Vendor.OTHER, OemPowerSettings.detectVendor("Google", "google"))
        assertEquals(Vendor.OTHER, OemPowerSettings.detectVendor("", ""))
        assertEquals(Vendor.OTHER, OemPowerSettings.detectVendor(null, null))
    }

    // ---------------------------------------------------------------- deep-link fallback

    private val oldAutostart = OemPowerSettings.VendorComponent(
        "com.vendor.old", "com.vendor.old.AutoStart"
    )
    private val newAutostart = OemPowerSettings.VendorComponent(
        "com.vendor.new", "com.vendor.new.AutoStart"
    )

    @Test
    fun `picks the first resolvable candidate`() {
        val selected = OemPowerSettings.selectComponent(listOf(oldAutostart, newAutostart)) {
            it.pkg == "com.vendor.new"
        }
        assertEquals(newAutostart, selected)
    }

    @Test
    fun `candidate order is respected when several resolve`() {
        val selected = OemPowerSettings.selectComponent(listOf(oldAutostart, newAutostart)) { true }
        assertEquals(oldAutostart, selected)
    }

    @Test
    fun `returns null when no vendor screen exists so only instructions are shown`() {
        val selected = OemPowerSettings.selectComponent(listOf(oldAutostart, newAutostart)) { false }
        assertNull(selected)
    }

    @Test
    fun `empty candidate list is handled`() {
        assertNull(OemPowerSettings.selectComponent(emptyList()) { true })
    }

    @Test
    fun `a throwing probe is treated as not resolvable instead of crashing`() {
        val selected = OemPowerSettings.selectComponent(listOf(oldAutostart, newAutostart)) {
            if (it == oldAutostart) throw SecurityException("package visibility") else true
        }
        assertEquals(newAutostart, selected)
    }

    @Test
    fun `every vendor has a localizable label`() {
        // Labels are string resources so the guide can be translated; brand names stay as-is.
        Vendor.entries.forEach { assertTrue(it.name, it.labelRes != 0) }
        assertEquals(
            Vendor.entries.size,
            Vendor.entries.map { it.labelRes }.distinct().size
        )
    }
}
