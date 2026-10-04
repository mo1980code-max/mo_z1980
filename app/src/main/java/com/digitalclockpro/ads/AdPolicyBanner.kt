package com.digitalclockpro.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.digitalclockpro.clockengine.AdPolicy
import com.digitalclockpro.clockengine.AdSurface
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * The ONE banner host of the app, bottom-anchored above the navigation bar.
 *
 * [AdPolicy.allowsBanner] is the only gate: on the six ad-free surfaces (alarm ringing, desk
 * clock, chess clock, alarm editor, OEM guide, widgets) this composable returns before an
 * [AdView] is even constructed, so there is nothing to render and nothing to leak. Nothing
 * else in the UI layer is allowed to make that call.
 *
 * Uses an anchored adaptive banner: full width, height chosen by the SDK per device
 * (https://developers.google.com/admob/android/banner/anchored-adaptive).
 */
@Composable
fun AdPolicyBanner(
    surface: AdSurface,
    adsReady: Boolean,
    modifier: Modifier = Modifier
) {
    // Hard gate #1: ad-free surfaces never host an ad, in any state, ever.
    if (!AdPolicy.allowsBanner(surface)) return

    // Hard gate #2: no request until UMP consent allowed ads and MobileAds initialized.
    if (!adsReady) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    if (widthDp <= 0) return // transient value during initial layout / previews

    // Keyed on width so a rotation builds a correctly sized view (and destroys the old one).
    val adView = remember(context, widthDp) {
        AdView(context).apply {
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            )
            adUnitId = AdPolicy.TEST_BANNER_AD_UNIT_ID
        }
    }

    // Load once per size; destroy when the banner leaves composition (navigating to an
    // ad-free surface destroys it — that is intended, not a leak of impressions).
    DisposableEffect(adView) {
        adView.loadAd(AdRequest.Builder().build())
        onDispose { adView.destroy() }
    }

    // Follow the host lifecycle: pause requests while covered, resume when visible again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(factory = { adView }, modifier = modifier)
}
