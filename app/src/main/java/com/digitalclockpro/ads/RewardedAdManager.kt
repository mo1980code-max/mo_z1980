package com.digitalclockpro.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.digitalclockpro.clockengine.AdPolicy
import com.digitalclockpro.clockengine.AdSurface
import com.digitalclockpro.clockengine.ChessClockEngine
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The rewarded half of the ad integration — the Android mirror of the narrow door
 * [AdPolicy.allowsRewardedAd] opens: one user-initiated ad, offered only from the chess
 * game-over dialog, only after both clocks are frozen forever.
 *
 * Like [AdsController], this class deliberately decides NOTHING about eligibility: every
 * allow/deny question is delegated to the pure [AdPolicy] in `:clock-engine` (see
 * `AdPolicyTest`). This class only:
 *
 *  1. Preloads one [RewardedAd] as soon as the Mobile Ads SDK is initialized (never before —
 *     loads queued ahead of `MobileAds.initialize` are dropped by the SDK), and keeps exactly
 *     one ready.
 *  2. Shows it when the user explicitly opts in, granting the reward ONLY from
 *     `onUserEarnedReward` — never from a dismissal, never from a failed show.
 *  3. Survives the ugly cases without stranding the user: no fill, offline, an ad that loaded
 *     but failed to render, a double tap. Every failure path ends in a preload retry or a
 *     clean "not available" signal back to the UI — the game itself is never blocked.
 *
 * Threading: every entry point is the main thread (UI taps and all Google Mobile Ads
 * callbacks arrive there). The [Handler] is used only for the bounded load-retry backoff.
 *
 * Error handling contract: a load failure retries on a capped backoff and stops; the next
 * natural event (app start, an ad shown, the user tapping the offer again) re-arms the
 * preload, so an offline device never spins a polling loop, and an offline-to-online
 * transition is picked up by the next user action instead of a timer.
 */
@Singleton
class RewardedAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var isShowing = false

    /** How many load attempts have failed since the last success/user re-arm; indexes RETRY_DELAYS. */
    private var failedLoadAttempts = 0

    /** True when a rewarded ad is loaded and not currently on screen. */
    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    /**
     * Called once from [AdsController] when `MobileAds.initialize` has completed (UMP consent
     * already allowed ads by then). Loading earlier is pointless: the SDK drops it.
     */
    fun onMobileAdsInitialized() {
        preload()
    }

    /**
     * Idempotent: keeps at most one ad preloaded. Safe to call from anywhere, any number of
     * times — the UI calls it when the user taps the offer while no ad happens to be ready,
     * which doubles as the offline-to-online recovery path.
     */
    fun preload() {
        if (rewardedAd != null || isLoading || isShowing) return
        isLoading = true
        RewardedAd.load(
            context,
            AdPolicy.TEST_REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    failedLoadAttempts = 0
                    rewardedAd = ad
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // No fill / offline / SDK error: no ad to show, nothing crashed. Back off.
                    isLoading = false
                    rewardedAd = null
                    _rewardedReady.value = false
                    scheduleLoadRetry()
                }
            }
        )
    }

    /** Synchronous readiness for the moment of the tap — mirrors the UI's "IsLoaded" check. */
    fun isLoaded(): Boolean = rewardedAd != null && !isShowing

    /**
     * THE gate plus the show. Every decision about *whether* an ad may appear here belongs to
     * [AdPolicy.allowsRewardedAd]; this method only enforces it and drives the SDK.
     *
     * @param onUserEarnedReward invoked exactly once, and only, when the SDK reports the user
     *   finished watching (Google's `onUserEarnedReward`). The reward amount is passed through
     *   for callers that verify it; the chess summary unlock ignores it by design.
     * @param onAdUnavailable invoked whenever NO ad is shown — policy said no, nothing was
     *   preloaded, or the loaded ad failed to render. The UI answers with a gentle notice and
     *   the game simply continues; nothing is ever granted here.
     * @return true if a rewarded ad was actually handed to the screen.
     */
    fun showIfEligible(
        activity: Activity,
        surface: AdSurface,
        phase: ChessClockEngine.Phase,
        onUserEarnedReward: (rewardAmount: Int) -> Unit,
        onAdUnavailable: () -> Unit
    ): Boolean {
        // Hard gate: active play (or a non-chess surface) can never see a rewarded ad.
        if (!AdPolicy.allowsRewardedAd(surface, phase)) {
            onAdUnavailable()
            return false
        }

        val ad = rewardedAd
        if (ad == null) {
            // Nothing preloaded (no fill, offline, already consumed): start a fresh load so
            // the NEXT tap may succeed, and let the user down gently.
            preload()
            onAdUnavailable()
            return false
        }
        if (isShowing) return false // already on screen from a double tap

        isShowing = true
        rewardedAd = null // consumed; the next one starts loading on dismissal/failure
        _rewardedReady.value = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowing = false
                clearAndPreloadNext()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Loaded but could not render: nothing was watched, so nothing is granted.
                isShowing = false
                clearAndPreloadNext()
                onAdUnavailable()
            }
        }

        ad.show(activity) { rewardItem -> onUserEarnedReward(rewardItem.amount) }
        return true
    }

    // ------------------------------------------------------------- internals

    /**
     * Capped exponential-ish backoff: 5 s, 15 s, 60 s, then stop until the next external
     * re-arm (app start, a show, a user tap on the offer). An offline device must never turn
     * into a polling loop against the radio.
     */
    private fun scheduleLoadRetry() {
        if (failedLoadAttempts >= RETRY_DELAYS_MILLIS.size) return
        val delayMillis = RETRY_DELAYS_MILLIS[failedLoadAttempts++]
        mainHandler.postDelayed({ preload() }, delayMillis)
    }

    private fun clearAndPreloadNext() {
        rewardedAd = null
        _rewardedReady.value = false
        preload()
    }

    private companion object {
        val RETRY_DELAYS_MILLIS = longArrayOf(5_000L, 15_000L, 60_000L)
    }
}
