package com.digitalclockpro.clockengine

/**
 * SINGLE SOURCE OF TRUTH for every ad-eligibility decision in Digital Clock Pro.
 *
 * Why this file lives in `:clock-engine`: this module is a plain `java-library` with **no
 * Android dependency** (see its `build.gradle.kts`). A rule like "the alarm ringing screen
 * must never show an ad" is exactly the kind of logic that deserves the same millisecond JVM
 * unit tests as the timer and stopwatch engines — not a Robolectric test, not a hope. The
 * `:app` module parses Android-side inputs (routes, tab indices, launch intents) and *only*
 * translates them into the plain values this policy consumes; it never decides anything on
 * its own. If a PR adds an `if` about ads outside this file, the PR is wrong.
 *
 * The rules encoded here are product promises, not suggestions:
 *
 *  1. Ads are FORBIDDEN on every surface where they would be dangerous or abusive —
 *     [AdSurface.ALARM_RINGING] (a half-asleep user fumbling to dismiss an alarm must never
 *     mis-tap an ad), [AdSurface.DESK_CLOCK] (a bedside clock that is looked at for hours),
 *     [AdSurface.CHESS_CLOCK] (a running game, two players hammering opposite halves of the
 *     screen), [AdSurface.ALARM_EDITOR] (a mistake here can disable tomorrow's alarm),
 *     [AdSurface.OEM_GUIDE] (a step-by-step battery setup checklist) and [AdSurface.WIDGET]
 *     (home-screen widgets and the studio that configures them).
 *  2. The app-open ad is FORBIDDEN whenever the open *came from an alarm* — the full-screen
 *     intent, the alarm notification, or the status-bar alarm icon. Interposing an ad
 *     between the user and the ringing screen is unacceptable (see [AdLaunchOrigin.ALARM]).
 *
 * Both rules are enforced end-to-end in `AdPolicyTest`.
 */

/**
 * Every screen or surface of the app that can host (or must refuse) an ad decision.
 * The banner host in `:app` maps nav routes, tabs and activities onto these values.
 */
enum class AdSurface {
    /** Main clock dashboard (bottom-nav "Clock" tab). Ads allowed. */
    DASHBOARD,
    /** World clock + meeting planner. Ads allowed. */
    WORLD_CLOCK,
    /** Alarm list. Ads allowed. */
    ALARM_LIST,
    /** Countdown timer tab. Ads allowed. */
    TIMER,
    /** Stopwatch tab. Ads allowed. */
    STOPWATCH,
    /** Settings. Ads allowed. */
    SETTINGS,

    // ---------------------------------------------------------------- ad-free
    /** Full-screen alarm ringing UI. Ad-free FOREVER — see [AdPolicy.AD_FREE_SURFACES]. */
    ALARM_RINGING,
    /** Bedside / desk clock. Ad-free FOREVER. */
    DESK_CLOCK,
    /** Two-player tournament chess clock. Ad-free FOREVER. */
    CHESS_CLOCK,
    /** Alarm editor (create/edit alarm). Ad-free FOREVER. */
    ALARM_EDITOR,
    /** OEM battery-optimization setup guide. Ad-free FOREVER. */
    OEM_GUIDE,
    /** Home-screen widgets AND the widget studio that configures them. Ad-free FOREVER. */
    WIDGET
}

/**
 * How the app process was opened / brought to the foreground. `:app` derives this from the
 * launching `Intent` (see `AdLaunchOrigins`) and hands it to [AdPolicy.allowsAppOpenAd].
 */
enum class AdLaunchOrigin {
    /** Normal launch: launcher icon, recents, in-app navigation. */
    NORMAL,
    /** Opened BY THE ALARM: full-screen intent, alarm notification or status-bar alarm icon. */
    ALARM,
    /** Opened by tapping a home-screen widget. */
    WIDGET
}

object AdPolicy {

    /**
     * Surfaces where ads are banned outright, in any format, forever.
     *
     * Never shrink this list: every entry is a promise to the user. Growing it is allowed
     * only with an accompanying test, because a surface missing from here would silently
     * become monetizable.
     */
    val AD_FREE_SURFACES: Set<AdSurface> = setOf(
        AdSurface.ALARM_RINGING,
        AdSurface.DESK_CLOCK,
        AdSurface.CHESS_CLOCK,
        AdSurface.ALARM_EDITOR,
        AdSurface.OEM_GUIDE,
        AdSurface.WIDGET
    )

    /** True if the surface may ever show an ad of any format. */
    fun allowsAds(surface: AdSurface): Boolean = surface !in AD_FREE_SURFACES

    /**
     * Banner eligibility — the only in-app ad format today. The banner composable calls
     * this on every recomposition and renders *nothing* when it returns false, so a blocked
     * surface never even constructs an `AdView`.
     */
    fun allowsBanner(surface: AdSurface): Boolean = allowsAds(surface)

    /**
     * App-open ad eligibility. Two independent vetoes, either one sufficient:
     *
     *  - [AdLaunchOrigin.ALARM]: the app was opened *by an alarm*. This is the hard product
     *    rule — the user (or the full-screen intent) is here because something is ringing.
     *  - a blocked [surface] on top: an app-open ad is still an ad, so it must never cover
     *    one of the six ad-free surfaces (e.g. returning to the foreground while the alarm
     *    editor or the desk clock is what the user would see).
     */
    fun allowsAppOpenAd(origin: AdLaunchOrigin, surface: AdSurface): Boolean =
        origin != AdLaunchOrigin.ALARM && allowsAds(surface)

    /**
     * Rewarded eligibility — the single, deliberate exception to the ad-free surfaces
     * (class doc, rule 3). True ONLY when BOTH hold:
     *
     *  - the surface is [AdSurface.CHESS_CLOCK] — no other surface has a rewarded door — and
     *  - the engine reports the game [ChessClockEngine.Phase.FINISHED], so every clock is
     *    already frozen and there is no live game left to interrupt.
     *
     * During play (IDLE / RUNNING / PAUSED) this is false for every surface, the chess clock
     * included. Widening this door — another surface, another phase — is a product decision
     * that must arrive with its own tests, like every other rule in this file.
     */
    fun allowsRewardedAd(surface: AdSurface, phase: ChessClockEngine.Phase): Boolean =
        surface == AdSurface.CHESS_CLOCK && phase == ChessClockEngine.Phase.FINISHED

    // --------------------------------------------------------------------- IDs
    //
    // Google's OFFICIAL test identifiers (sample AdMob account used by every Google guide:
    // https://developers.google.com/admob/android/test-ads and
    // https://developers.google.com/admob/android/app-open). They always serve test ads,
    // never charge advertisers and cannot get the account flagged for invalid traffic.
    // The app id mirrors the `com.google.android.gms.ads.APPLICATION_ID` meta-data in
    // AndroidManifest.xml — keep the two in sync. Replace ALL FOUR (plus the manifest
    // entry) with the production AdMob ids before a Play Store release; see docs/ADS.md.

    /** Test AdMob **app** id (note the `~` separator). Mirrors the manifest meta-data. */
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    /** Test ad unit for anchored adaptive banners (the format the app uses bottom-anchored). */
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

    /** Test ad unit for the app-open format. */
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"

    /** Test ad unit for the rewarded format (chess post-game summary unlock). */
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
}
