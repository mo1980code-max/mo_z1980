package com.digitalclockpro.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.digitalclockpro.clockengine.AdLaunchOrigin
import com.digitalclockpro.clockengine.AdPolicy
import com.digitalclockpro.clockengine.AdSurface
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android half of the ad integration. It deliberately decides NOTHING: every allow/deny
 * question is delegated to the pure [AdPolicy] in `:clock-engine` (see `AdPolicyTest`).
 * This class only:
 *
 *  1. Gathers UMP consent and initializes the Mobile Ads SDK — in that order, never the
 *     other way around, per https://developers.google.com/admob/android/privacy.
 *  2. Tracks which [AdSurface] the currently visible activity is showing (activities report
 *     themselves; the map survives backgrounding so a returning user is not mis-classified).
 *  3. Preloads and shows the app-open ad on app foregrounding, but only when
 *     [AdPolicy.allowsAppOpenAd] says so — an alarm-triggered open or an ad-free surface
 *     never sees one.
 *  4. Kicks off the rewarded preload the moment the SDK is initialized — [RewardedAdManager]
 *     owns everything rewarded from there on, under its own [AdPolicy] gate.
 *
 * Threading: every entry point is the main thread (activity callbacks, UMP callbacks and the
 * Mobile Ads callbacks all arrive there).
 */
@Singleton
class AdsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rewardedAds: RewardedAdManager
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Surface of whatever activity is on top, as last reported by that activity. */
    private val _surface = MutableStateFlow(AdSurface.DASHBOARD)
    val surface: StateFlow<AdSurface> = _surface.asStateFlow()

    /** True only once UMP consent allowed ads AND [MobileAds.initialize] has completed. */
    private val _adsReady = MutableStateFlow(false)
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

    /** How this process was opened; consumed by the next foreground transition. */
    private var pendingLaunchOrigin: AdLaunchOrigin? = null

    private val surfacesByActivity = HashMap<Activity, AdSurface>()
    private var currentActivity: Activity? = null

    private lateinit var consentInformation: ConsentInformation
    private var isGatheringConsent = false
    private var mobileAdsInitializeStarted = false

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpenAd = false
    private var isShowingAppOpenAd = false
    private var appOpenAdLoadedAt = 0L

    /** App-open ads expire after 4 hours (https://developers.google.com/admob/android/app-open). */
    private fun appOpenAdIsExpired(): Boolean =
        SystemClock.elapsedRealtime() - appOpenAdLoadedAt > AD_EXPIRY_MILLIS

    /** Called once from [com.digitalclockpro.DigitalClockApp.onCreate]. */
    fun attach(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        // The app-open ad is a process-foreground event, not an activity event.
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ----------------------------------------------------------- UMP consent

    /**
     * Must run BEFORE anything initializes the Mobile Ads SDK. Called from the ad-hosting
     * activity (MainActivity): the UMP form is itself an activity in our process, so it must
     * never be triggered from a ringing alarm, the desk clock or a widget config screen.
     *
     * Flow per https://developers.google.com/admob/android/privacy:
     *  1. `requestConsentInfoUpdate` on every launch (cheap when consent already exists).
     *  2. `loadAndShowConsentFormIfRequired` — presents the GDPR message only when required.
     *  3. `MobileAds.initialize` — only once `canRequestAds()` is true. The trailing call
     *     covers consent obtained in a *previous* session, so startup is never blocked.
     */
    fun gatherConsentAndInitialize(activity: Activity) {
        if (mobileAdsInitializeStarted || isGatheringConsent) return
        isGatheringConsent = true
        consentInformation = UserMessagingPlatform.getConsentInformation(context)
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    isGatheringConsent = false
                    initializeMobileAdsIfConsentAllows()
                }
            },
            {
                // Update failed (offline, SDK error...). Fall back to whatever consent a
                // previous session stored; if there is none, ads simply stay off.
                isGatheringConsent = false
                initializeMobileAdsIfConsentAllows()
            }
        )
        initializeMobileAdsIfConsentAllows()
    }

    private fun initializeMobileAdsIfConsentAllows() {
        if (mobileAdsInitializeStarted) return
        if (!::consentInformation.isInitialized || !consentInformation.canRequestAds()) return
        mobileAdsInitializeStarted = true
        MobileAds.initialize(context) {
            _adsReady.value = true
            loadAppOpenAd()
        }
    }

    // ------------------------------------------------- launch origin & surface

    /**
     * Records how the app was opened (launcher / alarm / widget). Consumed by the next
     * foreground transition; [AdPolicy.allowsAppOpenAd] then vetoes alarm-originated opens.
     */
    fun onLaunched(origin: AdLaunchOrigin) {
        pendingLaunchOrigin = origin
    }

    /**
     * Activities (and, inside MainActivity, the navigation host) report the [AdSurface]
     * currently on screen. Ad-free surfaces report themselves too — that is what keeps the
     * app-open ad from ever covering them.
     */
    fun setSurface(activity: Activity, surface: AdSurface) {
        surfacesByActivity[activity] = surface
        if (activity === currentActivity) _surface.value = surface
    }

    // ----------------------------------------------------- process foreground

    override fun onStart(owner: LifecycleOwner) {
        // Post past the current lifecycle transaction: ProcessLifecycleOwner notifies us
        // from inside the activity's onStart, BEFORE our own onActivityStarted callback has
        // recorded the top activity and its surface.
        mainHandler.post { showAppOpenAdIfEligible() }
    }

    override fun onStop(owner: LifecycleOwner) {
        // Going to background without an eligible show: drop the stale launch origin so a
        // later normal return is not still treated as "opened from an alarm".
        pendingLaunchOrigin = null
    }

    private fun showAppOpenAdIfEligible() {
        val origin = pendingLaunchOrigin ?: AdLaunchOrigin.NORMAL
        pendingLaunchOrigin = null

        // THE gate. Everything about eligibility is decided by the pure policy.
        if (!AdPolicy.allowsAppOpenAd(origin, _surface.value)) {
            loadAppOpenAd() // keep one preloaded for the next eligible open
            return
        }
        if (isShowingAppOpenAd) return

        val ad = appOpenAd
        if (ad == null || appOpenAdIsExpired()) {
            appOpenAd = null
            loadAppOpenAd()
            return
        }
        val activity = currentActivity?.takeIf { !it.isFinishing } ?: return

        appOpenAd = null
        isShowingAppOpenAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingAppOpenAd = false
                loadAppOpenAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAppOpenAd = false
                loadAppOpenAd()
            }
        }
        ad.show(activity)
    }

    private fun loadAppOpenAd() {
        if (appOpenAd != null || isLoadingAppOpenAd || !_adsReady.value) return
        isLoadingAppOpenAd = true
        AppOpenAd.load(
            context,
            AdPolicy.TEST_APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAppOpenAd = false
                    appOpenAd = ad
                    appOpenAdLoadedAt = SystemClock.elapsedRealtime()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // No fill / offline: retry at the next foreground opportunity.
                    isLoadingAppOpenAd = false
                }
            }
        )
    }

    // ------------------------------------------------- activity tracking

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        _surface.value = surfacesByActivity[activity] ?: AdSurface.DASHBOARD
    }

    override fun onActivityDestroyed(activity: Activity) {
        surfacesByActivity.remove(activity)
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit

    private companion object {
        const val AD_EXPIRY_MILLIS = 4 * 60 * 60 * 1000L
    }
}
