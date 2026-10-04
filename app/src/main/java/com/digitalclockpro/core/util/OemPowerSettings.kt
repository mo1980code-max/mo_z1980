package com.digitalclockpro.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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

    enum class Vendor(val displayName: String) {
        XIAOMI("Xiaomi / Redmi / POCO"),
        HUAWEI("Huawei / Honor"),
        SAMSUNG("Samsung"),
        OPPO("OPPO / realme"),
        ONEPLUS("OnePlus"),
        VIVO("vivo / iQOO"),
        ASUS("ASUS"),
        NOKIA("Nokia / HMD"),
        LETV("LeEco"),
        OTHER("Your device")
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

    val vendor: Vendor by lazy { detectVendor() }

    private fun detectVendor(): Vendor {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        val fingerprint = "$manufacturer $brand"
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
                        title = "Enable Autostart",
                        description = "MIUI blocks the alarm from restarting after the phone " +
                            "reboots or the app is swiped away.",
                        instructions = "Security → Permissions → Autostart → turn ON " +
                            "“Digital Clock Pro”.",
                        candidates = listOf(
                            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartDetailActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "miui_battery_saver",
                        title = "Set battery saver to “No restrictions”",
                        description = "MIUI’s own power keeper is separate from Android’s " +
                            "battery optimization.",
                        instructions = "Find “Digital Clock Pro” → Battery saver → " +
                            "choose “No restrictions”.",
                        candidates = listOf(
                            component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                            component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "miui_lock_recents",
                        title = "Lock the app in Recents",
                        description = "Prevents “clear all” from killing the ringing alarm.",
                        instructions = "Open Recents → swipe down on Digital Clock Pro → " +
                            "tap the padlock icon.",
                        candidates = emptyList()
                    )
                )
            }

            Vendor.HUAWEI -> {
                add(
                    context.action(
                        id = "huawei_startup",
                        title = "Manage app launch manually",
                        description = "EMUI closes apps it manages automatically.",
                        instructions = "App launch → turn OFF “Manage automatically” for " +
                            "Digital Clock Pro → enable all three switches " +
                            "(Auto-launch, Secondary launch, Run in background).",
                        candidates = listOf(
                            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                            component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "huawei_protected",
                        title = "Add to protected apps",
                        description = "Keeps the alarm service alive when the screen is off.",
                        instructions = "Protected apps → enable Digital Clock Pro.",
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
                        title = "Remove from “Sleeping apps”",
                        description = "One UI puts rarely used apps to sleep, which cancels " +
                            "their alarms.",
                        instructions = "Battery → Background usage limits → make sure " +
                            "Digital Clock Pro is NOT in “Sleeping” or “Deep sleeping” apps.",
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
                        title = "Turn off “Put unused apps to sleep”",
                        description = "Device Care re-sleeps apps automatically every few days.",
                        instructions = "Device care → Battery → Background usage limits → " +
                            "turn OFF “Put unused apps to sleep”.",
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
                        title = "Allow auto-startup",
                        description = "ColorOS blocks background start after reboot.",
                        instructions = "Startup manager → enable Digital Clock Pro.",
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
                        title = "Disable battery restrictions",
                        description = "Set the app to “Allow background running”.",
                        instructions = "Power saver → App battery management → " +
                            "Digital Clock Pro → allow background running + auto-launch.",
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
                        title = "Allow auto-launch",
                        description = "OxygenOS “Chain launch” control.",
                        instructions = "Auto-launch → enable Digital Clock Pro.",
                        candidates = listOf(
                            component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                        )
                    )
                )
                add(
                    context.action(
                        id = "oneplus_battery",
                        title = "Set battery to “Don’t optimize”",
                        description = "Advanced optimization also needs to be off.",
                        instructions = "Battery → Battery optimization → Digital Clock Pro → " +
                            "Don’t optimize.",
                        candidates = emptyList()
                    )
                )
            }

            Vendor.VIVO -> {
                add(
                    context.action(
                        id = "vivo_bg_start",
                        title = "Allow background start-up",
                        description = "Funtouch OS blocks background launches by default.",
                        instructions = "Permission manager → Autostart → enable Digital Clock Pro.",
                        candidates = listOf(
                            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
                        )
                    )
                )
                add(
                    context.action(
                        id = "vivo_whitelist",
                        title = "Add to high background power consumption list",
                        description = "Stops the system from freezing the alarm service.",
                        instructions = "Battery → High background power consumption → " +
                            "enable Digital Clock Pro.",
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
                    title = "Enable Auto-start",
                    description = "ZenUI Mobile Manager auto-start list.",
                    instructions = "Mobile Manager → Auto-start manager → allow Digital Clock Pro.",
                    candidates = listOf(
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
                    )
                )
            )

            Vendor.LETV -> add(
                context.action(
                    id = "letv_autoboot",
                    title = "Enable auto-boot",
                    description = "EUI background app protection.",
                    instructions = "Autoboot management → enable Digital Clock Pro.",
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
                title = "Android battery optimization",
                description = "The standard exemption every Android version supports.",
                instructions = "Choose “All apps” → Digital Clock Pro → Don’t optimize.",
                candidates = emptyList(),
                fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        )
        add(
            context.action(
                id = "app_details",
                title = "App info",
                description = "Manual access to notifications, alarms and battery settings.",
                instructions = "Use this if any button above does not open.",
                candidates = emptyList(),
                fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun component(pkg: String, cls: String) = ComponentName(pkg, cls)

    /** Picks the first candidate component that is actually resolvable on this ROM. */
    private fun Context.action(
        id: String,
        title: String,
        description: String,
        instructions: String,
        candidates: List<ComponentName>,
        fallbackIntent: Intent? = null
    ): OemAction {
        val resolved = candidates
            .asSequence()
            .map { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { it.isResolvable(this) }

        val intent = resolved ?: fallbackIntent?.takeIf { it.isResolvable(this) }
        return OemAction(
            id = id,
            title = title,
            description = description,
            instructions = instructions,
            intent = intent,
            available = intent != null
        )
    }

    private fun Intent.isResolvable(context: Context): Boolean = runCatching {
        context.packageManager
            .queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()
    }.getOrDefault(false)
}
