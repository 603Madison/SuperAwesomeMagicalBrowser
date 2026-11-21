package app.superawesomemagical.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * "Send to device": hands a page or a link to SuperAwesomeMagicalOrchestrator's app, which shows
 * the person's devices and sends it to the one they pick. The browser has no account of its own,
 * so that's all it does with the orchestrator, and only when asked: browsing isn't told to other
 * devices by itself (nothing is published as an activity).
 */
object Orchestrator {
    const val PACKAGE = "app.superawesomemagical.orchestrator"

    /** Whether the orchestrator's app is on this phone (the manifest's <queries> lets us see it). */
    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Only web pages go to other devices: never about:, file:, data: or another app's links. */
    fun sendable(url: String?): Boolean {
        val lower = url?.trim()?.lowercase() ?: return false
        return (lower.startsWith("http://") && lower.length > 7) || (lower.startsWith("https://") && lower.length > 8)
    }

    /** Whether to offer "Send to device" for [url]. */
    fun offers(installed: Boolean, url: String?): Boolean = installed && sendable(url)

    /**
     * The address and its title for the orchestrator's app, and no other: an explicit share. Null
     * for anything that isn't a web page.
     */
    fun sendIntent(url: String, title: String): Intent? {
        if (!sendable(url)) return null
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(PACKAGE).putExtra(Intent.EXTRA_TEXT, url.trim())
        if (title.isNotBlank()) send.putExtra(Intent.EXTRA_SUBJECT, title.trim())
        return send
    }
}
