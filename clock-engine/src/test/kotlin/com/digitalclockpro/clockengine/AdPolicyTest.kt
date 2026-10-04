package com.digitalclockpro.clockengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ad policy is a set of product promises; these tests are the contract that keeps them
 * true. Each ad-free surface gets its own named test so a failure message says exactly
 * which promise was broken.
 */
class AdPolicyTest {

    // -------------------------------------------------------- ad-free surfaces

    @Test
    fun `the ad-free set is exactly the six surfaces the product promised`() {
        assertEquals(
            setOf(
                AdSurface.ALARM_RINGING,
                AdSurface.DESK_CLOCK,
                AdSurface.CHESS_CLOCK,
                AdSurface.ALARM_EDITOR,
                AdSurface.OEM_GUIDE,
                AdSurface.WIDGET
            ),
            AdPolicy.AD_FREE_SURFACES
        )
    }

    @Test
    fun `the alarm ringing screen never shows any ad`() {
        assertFalse(AdPolicy.allowsAds(AdSurface.ALARM_RINGING))
        assertFalse(AdPolicy.allowsBanner(AdSurface.ALARM_RINGING))
    }

    @Test
    fun `the desk clock never shows any ad`() {
        assertFalse(AdPolicy.allowsAds(AdSurface.DESK_CLOCK))
        assertFalse(AdPolicy.allowsBanner(AdSurface.DESK_CLOCK))
    }

    @Test
    fun `the chess clock never shows any ad`() {
        assertFalse(AdPolicy.allowsAds(AdSurface.CHESS_CLOCK))
        assertFalse(AdPolicy.allowsBanner(AdSurface.CHESS_CLOCK))
    }

    @Test
    fun `the alarm editor never shows any ad`() {
        assertFalse(AdPolicy.allowsAds(AdSurface.ALARM_EDITOR))
        assertFalse(AdPolicy.allowsBanner(AdSurface.ALARM_EDITOR))
    }

    @Test
    fun `the OEM guide never shows any ad`() {
        assertFalse(AdPolicy.allowsAds(AdSurface.OEM_GUIDE))
        assertFalse(AdPolicy.allowsBanner(AdSurface.OEM_GUIDE))
    }

    @Test
    fun `widgets never show any ad`() {
        assertFalse(AdPolicy.allowsAds(AdSurface.WIDGET))
        assertFalse(AdPolicy.allowsBanner(AdSurface.WIDGET))
    }

    @Test
    fun `the ad-free set only references real surfaces`() {
        assertTrue(AdSurface.entries.containsAll(AdPolicy.AD_FREE_SURFACES))
    }

    @Test
    fun `no ad-free surface can ever show a banner`() {
        AdPolicy.AD_FREE_SURFACES.forEach { surface ->
            assertFalse("banner leaked into $surface", AdPolicy.allowsBanner(surface))
        }
    }

    @Test
    fun `every surface that is not ad-free may show a banner`() {
        AdSurface.entries.filter { it !in AdPolicy.AD_FREE_SURFACES }.forEach { surface ->
            assertTrue("expected a banner to be allowed on $surface", AdPolicy.allowsBanner(surface))
        }
    }

    @Test
    fun `banner eligibility is consistent with the ad-free set for every surface`() {
        AdSurface.entries.forEach { surface ->
            assertEquals(
                "surface $surface is classified inconsistently",
                surface !in AdPolicy.AD_FREE_SURFACES,
                AdPolicy.allowsBanner(surface)
            )
        }
    }

    // ------------------------------------------------------------- app-open ad

    @Test
    fun `a normal launch of an ad-friendly surface may show the app open ad`() {
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.NORMAL, AdSurface.DASHBOARD))
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.NORMAL, AdSurface.WORLD_CLOCK))
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.NORMAL, AdSurface.ALARM_LIST))
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.NORMAL, AdSurface.TIMER))
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.NORMAL, AdSurface.STOPWATCH))
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.NORMAL, AdSurface.SETTINGS))
    }

    @Test
    fun `an open that came from an alarm never shows the app open ad - on any surface`() {
        AdSurface.entries.forEach { surface ->
            assertFalse(
                "app-open ad leaked through an ALARM launch onto $surface",
                AdPolicy.allowsAppOpenAd(AdLaunchOrigin.ALARM, surface)
            )
        }
    }

    @Test
    fun `the app open ad is blocked on ad-free surfaces even on a normal launch`() {
        AdPolicy.AD_FREE_SURFACES.forEach { surface ->
            assertFalse(
                "app-open ad covered the ad-free surface $surface",
                AdPolicy.allowsAppOpenAd(AdLaunchOrigin.NORMAL, surface)
            )
        }
    }

    @Test
    fun `a widget tap may show the app open ad - but only on ad-friendly surfaces`() {
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.WIDGET, AdSurface.DASHBOARD))
        assertTrue(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.WIDGET, AdSurface.ALARM_LIST))
        assertFalse(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.WIDGET, AdSurface.CHESS_CLOCK))
        assertFalse(AdPolicy.allowsAppOpenAd(AdLaunchOrigin.WIDGET, AdSurface.DESK_CLOCK))
    }

    // ------------------------------------------------------------- test ids

    @Test
    fun `the ids are google's official test ids from the admob docs`() {
        // App id:      https://developers.google.com/admob/android/quick-start
        // Banner:      https://developers.google.com/admob/android/test-ads (anchored adaptive)
        // App open:    https://developers.google.com/admob/android/app-open
        assertEquals("ca-app-pub-3940256099942544~3347511713", AdPolicy.TEST_APP_ID)
        assertEquals("ca-app-pub-3940256099942544/9214589741", AdPolicy.TEST_BANNER_AD_UNIT_ID)
        assertEquals("ca-app-pub-3940256099942544/9257395921", AdPolicy.TEST_APP_OPEN_AD_UNIT_ID)
    }

    @Test
    fun `every id belongs to google's official test publisher`() {
        val officialTestPublisher = "ca-app-pub-3940256099942544"
        listOf(
            AdPolicy.TEST_APP_ID,
            AdPolicy.TEST_BANNER_AD_UNIT_ID,
            AdPolicy.TEST_APP_OPEN_AD_UNIT_ID
        ).forEach { id ->
            assertTrue("$id is not a Google test id", id.startsWith(officialTestPublisher))
        }
    }

    @Test
    fun `the app id uses the app separator and ad units use the ad-unit separator`() {
        // AdMob app ids contain '~'; ad unit ids contain '/'. Swapping them is the classic
        // integration bug and the SDK crashes on start, so guard the shapes here.
        assertTrue(AdPolicy.TEST_APP_ID.contains('~'))
        assertFalse(AdPolicy.TEST_APP_ID.contains('/'))
        assertTrue(AdPolicy.TEST_BANNER_AD_UNIT_ID.contains('/'))
        assertFalse(AdPolicy.TEST_BANNER_AD_UNIT_ID.contains('~'))
        assertTrue(AdPolicy.TEST_APP_OPEN_AD_UNIT_ID.contains('/'))
        assertFalse(AdPolicy.TEST_APP_OPEN_AD_UNIT_ID.contains('~'))
    }
}
