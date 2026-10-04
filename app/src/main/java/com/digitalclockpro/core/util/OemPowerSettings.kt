package com.digitalclockpro.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import com.digitalclockpro.R
import android.provider.Settings
import java.util.Locale

/**
 * OEM "app killer" survival guide.
 *
 * Chinese/Korean ROMs (MIUI/HyperOS, EMUI/HarmonyOS, ColorOS, FuntouchOS, One UI…) ship extra
 * power managers that terminate background processes and silently drop exact alarms, regardless
 * of the standard Android battery-optimization exemption. The only reliable fix is to walk the
 * user into each vendor screen, because none of these settings can be toggled programmatically.
 *
 * This object detects the vendor and exposes the deep links that actually exist on the device.
 * Component names are version-dependent, so every entry lists several historical candidates and
 * we probe them with the PackageManager before showing a button.
 *
 * Requires the matching `<queries>` entries in AndroidManifest.xml (Android 11+ visibility).
 */
object OemPowerSettings {

    enum class Vendor(@StringRes val labelRes: Int) {
        XIAOMI(R.string.vendor_xiaomi),
        HUAWEI(R.string.vendor_huawei),
        SAMSUNG(R.string.vendor_samsung),
        OPPO(R.string.vendor_oppo),
        ONEPLUS(R.string.vendor_oneplus),
        VIVO(R.string.vendor_vivo),
        ASUS(R.string.vendor_asus),
        NOKIA(R.string.vendor_nokia),
        LETV(R.string.vendor_letv),
        OTHER(R.string.vendor_other)
    }

    /** Localized vendor name. Brand names are proper nouns and stay untranslated. */
    fun vendorLabel(context: Context): String = context.getString(vendor.labelRes)

    /**
     * Framework-free description of a vendor Activity, so candidate selection stays unit-testable
     * (`ComponentName` is a stubbed class under plain JUnit and throws on construction).
     */
    data class VendorComponent(val pkg: String, val cls: String) {
        fun toComponentName(): ComponentName = ComponentName(pkg, cls)
    }

    /**
     * A single actionable step shown in the OEM guide.
     *
     * @param available false when the target Activity is absent on this ROM version — the UI
     *        still renders the instructions, just without a (broken) button.
     */
    data class OemAction(
        val id: String,
        val title: String,
        val description: String,
        val instructions: String,
        val intent: Intent?,
        val available: Boolean
    )

    // ------------------------------------------------------------------ vendor detection

    val vendor: Vendor by lazy { detectVendor(Build.MANUFACTURER, Build.BRAND) }

    /** Pure function so vendor detection is unit-testable without a device. */
    fun detectVendor(manufacturer: String?, brand: String?): Vendor {
        val fingerprint = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            listOf("xiaomi", "redmi", "poco").any { fingerprint.contains(it) } -> Vendor.XIAOMI
            listOf("huawei", "honor").any { fingerprint.contains(it) } -> Vendor.HUAWEI
            fingerprint.contains("samsung") -> Vendor.SAMSUNG
            listOf("oppo", "realme").any { fingerprint.contains(it) } -> Vendor.OPPO
            listOf("oneplus", "op_").any { fingerprint.contains(it) } -> Vendor.ONEPLUS
            listOf("vivo", "iqoo").any { fingerprint.contains(it) } -> Vendor.VIVO
            fingerprint.contains("asus") -> Vendor.ASUS
            listOf("nokia", "hmd").any { fingerprint.contains(it) } -> Vendor.NOKIA
            listOf("letv", "leeco").any { fingerprint.contains(it) } -> Vendor.LETV
            else -> Vendor.OTHER
        }
    }

    /** True on ROMs known to need manual whitelisting beyond the AOSP exemption. */
    val requiresManualWhitelisting: Boolean
        get() = vendor != Vendor.OTHER && vendor != Vendor.NOKIA

    /** Short human-readable ROM label, e.g. "MIUI / HyperOS". */
    val romName: String
        get() = when (vendor) {
            Vendor.XIAOMI -> "MIUI / HyperOS"
            Vendor.HUAWEI -> "EMUI / HarmonyOS"
            Vendor.SAMSUNG -> "One UI"
            Vendor.OPPO -> "ColorOS"
            Vendor.ONEPLUS -> "OxygenOS"
            Vendor.VIVO -> "Funtouch OS / OriginOS"
            Vendor.ASUS -> "ZenUI"
            Vendor.LETV -> "EUI"
            else -> "Android"
        }

    // ------------------------------------------------------------------ actions

    /** Vendor-specific steps, newest-first candidates, filtered to what exists on the device. */
    fun actionsFor(context: Context): List<OemAction> = buildList {
        when (vendor) {
            Vendor.XIAOMI -> {
                add(
                    context.action(
                        id = "miui_autostart",
                        title = context.getString(R.string.oem_miui_autostart_title),
                        description = context.getString(R.string.oem_miui_autostart_desc),
                        instructions = context.getString(R.string.oem_miui_autostart_steps),
                        candidates = listOf(
                            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartDetailActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "miui_battery_saver",
                        title = context.getString(R.string.oem_miui_battery_title),
                        description = context.getString(R.string.oem_miui_battery_desc),
                        instructions = context.getString(R.string.oem_miui_battery_steps),
                        candidates = listOf(
                            component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                            component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "miui_lock_recents",
                        title = context.getString(R.string.oem_miui_lock_title),
                        description = context.getString(R.string.oem_miui_lock_desc),
                        instructions = context.getString(R.string.oem_miui_lock_steps),
                        candidates = emptyList()
                    )
                )
            }

            Vendor.HUAWEI -> {
                add(
                    context.action(
                        id = "huawei_startup",
                        title = context.getString(R.string.oem_huawei_startup_title),
                        description = context.getString(R.string.oem_huawei_startup_desc),
                        instructions = context.getString(R.string.oem_huawei_startup_steps),
                        candidates = listOf(
                            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                            component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "huawei_protected",
                        title = context.getString(R.string.oem_huawei_protected_title),
                        description = context.getString(R.string.oem_huawei_protected_desc),
                        instructions = context.getString(R.string.oem_huawei_protected_steps),
                        candidates = listOf(
                            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                            component("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity")
                        )
                    )
                )
            }

            Vendor.SAMSUNG -> {
                add(
                    context.action(
                        id = "samsung_sleeping",
                        title = context.getString(R.string.oem_samsung_sleeping_title),
                        description = context.getString(R.string.oem_samsung_sleeping_desc),
                        instructions = context.getString(R.string.oem_samsung_sleeping_steps),
                        candidates = listOf(
                            component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                            component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                            component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "samsung_device_care",
                        title = context.getString(R.string.oem_samsung_care_title),
                        description = context.getString(R.string.oem_samsung_care_desc),
                        instructions = context.getString(R.string.oem_samsung_care_steps),
                        candidates = listOf(
                            component("com.samsung.android.lool", "com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity")
                        )
                    )
                )
            }

            Vendor.OPPO -> {
                add(
                    context.action(
                        id = "oppo_startup",
                        title = context.getString(R.string.oem_oppo_startup_title),
                        description = context.getString(R.string.oem_oppo_startup_desc),
                        instructions = context.getString(R.string.oem_oppo_startup_steps),
                        candidates = listOf(
                            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                            component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                            component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "oppo_power",
                        title = context.getString(R.string.oem_oppo_power_title),
                        description = context.getString(R.string.oem_oppo_power_desc),
                        instructions = context.getString(R.string.oem_oppo_power_steps),
                        candidates = listOf(
                            component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                            component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity")
                        )
                    )
                )
            }

            Vendor.ONEPLUS -> {
                add(
                    context.action(
                        id = "oneplus_chain",
                        title = context.getString(R.string.oem_oneplus_chain_title),
                        description = context.getString(R.string.oem_oneplus_chain_desc),
                        instructions = context.getString(R.string.oem_oneplus_chain_steps),
                        candidates = listOf(
                            component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "oneplus_battery",
                        title = context.getString(R.string.oem_oneplus_battery_title),
                        description = context.getString(R.string.oem_oneplus_battery_desc),
                        instructions = context.getString(R.string.oem_oneplus_battery_steps),
                        candidates = emptyList()
                    )
                )
            }

            Vendor.VIVO -> {
                add(
                    context.action(
                        id = "vivo_bg_start",
                        title = context.getString(R.string.oem_vivo_bgstart_title),
                        description = context.getString(R.string.oem_vivo_bgstart_desc),
                        instructions = context.getString(R.string.oem_vivo_bgstart_steps),
                        candidates = listOf(
                            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                        )
                    )
                )
                add(
                    context.action(
                        id = "vivo_whitelist",
                        title = context.getString(R.string.oem_vivo_whitelist_title),
                        description = context.getString(R.string.oem_vivo_whitelist_desc),
                        instructions = context.getString(R.string.oem_vivo_whitelist_steps),
                        candidates = listOf(
                            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                            component("com.vivo.pem", "com.vivo.pem.activity.PemAutoStartActivity")
                        )
                    )
                )
            }

            Vendor.ASUS -> add(
                context.action(
                    id = "asus_autostart",
                    title = context.getString(R.string.oem_asus_autostart_title),
                    description = context.getString(R.string.oem_asus_autostart_desc),
                    instructions = context.getString(R.string.oem_asus_autostart_steps),
                    candidates = listOf(
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
                    )
                )
            )

            Vendor.LETV -> add(
                context.action(
                    id = "letv_autoboot",
                    title = context.getString(R.string.oem_letv_autoboot_title),
                    description = context.getString(R.string.oem_letv_autoboot_desc),
                    instructions = context.getString(R.string.oem_letv_autoboot_steps),
                    candidates = listOf(
                        component("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
                    )
                )
            )

            Vendor.NOKIA, Vendor.OTHER -> Unit
        }

        // Always offered: the two standard AOSP screens.
        add(
            context.action(
                id = "aosp_battery",
                        title = context.getString(R.string.oem_aosp_battery_title),
                        description = context.getString(R.string.oem_aosp_battery_desc),
                        instructions = context.getString(R.string.oem_aosp_battery_steps),
                        candidates = emptyList(),
                fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        )
        add(
            context.action(
                id = "app_details",
                        title = context.getString(R.string.oem_app_details_title),
                        description = context.getString(R.string.oem_app_details_desc),
                        instructions = context.getString(R.string.oem_app_details_steps),
                        candidates = emptyList(),
                fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun component(pkg: String, cls: String) = VendorComponent(pkg, cls)

    /**
     * Pure candidate selection: returns the first component the probe accepts, or null.
     *
     * A null result is **not** an error — the step is still shown with its written instructions,
     * because opening an OEM screen is never a precondition for an alarm to ring.
     */
    fun selectComponent(
        candidates: List<VendorComponent>,
        isResolvable: (VendorComponent) -> Boolean
    ): VendorComponent? = candidates.firstOrNull { candidate ->
        runCatching { isResolvable(candidate) }.getOrDefault(false)
    }

    /** Picks the first candidate component that is actually resolvable on this ROM. */
    private fun Context.action(
        id: String,
        title: String,
        description: String,
        instructions: String,
        candidates: List<VendorComponent>,
        fallbackIntent: Intent? = null
    ): OemAction {
        val selected = selectComponent(candidates) { candidate ->
            candidate.toIntent().isResolvable(this)
        }
        val intent = selected?.toIntent() ?: fallbackIntent?.takeIf { it.isResolvable(this) }
        return OemAction(
            id = id,
            title = title,
            description = description,
            instructions = instructions,
            intent = intent,
            available = intent != null
        )
    }

    private fun VendorComponent.toIntent(): Intent =
        Intent().setComponent(toComponentName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * `queryIntentActivities` rather than `resolveActivity`: the latter returns the "best" match
     * and reports null for components that exist but are not exported as defaults, which made
     * valid vendor screens look unavailable. Even so, the launch itself is still wrapped in a
     * try/catch at the call site — some ROMs advertise a component and then refuse the start.
     */
    private fun Intent.isResolvable(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        pm.queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty() ||
            pm.queryIntentActivities(this, 0).isNotEmpty()
    }.getOrDefault(false)


}
