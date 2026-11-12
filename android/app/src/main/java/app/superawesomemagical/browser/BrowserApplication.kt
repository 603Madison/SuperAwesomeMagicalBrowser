package app.superawesomemagical.browser

import android.app.Application
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Holds Firefox's engine (one GeckoRuntime for the app, as GeckoView requires) and the tabs.
 * Gecko's own processes start this class too, so nothing is made until the activity asks.
 */
class BrowserApplication : Application() {
    val runtime: GeckoRuntime by lazy {
        val blocking = ContentBlocking.Settings.Builder()
            // Firefox's standard Enhanced Tracking Protection, with Total Cookie Protection.
            .antiTracking(ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .contentBlocking(blocking)
            .globalPrivacyControlEnabled(true)
            .consoleOutput(BuildConfig.DEBUG)
            .build()
        GeckoRuntime.create(this, settings)
    }

    val history: History by lazy { History(filesDir) }

    val browser: Browser by lazy { Browser(this, { runtime }, history) }
}
