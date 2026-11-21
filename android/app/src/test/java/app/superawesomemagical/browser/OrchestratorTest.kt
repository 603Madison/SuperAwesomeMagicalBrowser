package app.superawesomemagical.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class OrchestratorTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun offeredOnlyWithTheOrchestratorInstalled() {
        assertFalse(Orchestrator.installed(context))
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = Orchestrator.PACKAGE })
        assertTrue(Orchestrator.installed(context))

        assertTrue(Orchestrator.offers(installed = true, url = "https://example.org/a"))
        assertFalse(Orchestrator.offers(installed = false, url = "https://example.org/a"))
    }

    @Test fun onlyWebPagesAreOffered() {
        assertTrue(Orchestrator.sendable("https://example.org/a?b=c#d"))
        assertTrue(Orchestrator.sendable("HTTP://192.168.1.20/admin"))
        for (url in listOf(
            null, "", "about:blank", "about:config", "file:///sdcard/x.html", "data:text/html,hi", "javascript:alert(1)",
            "view-source:https://example.org", "intent://example.org#Intent;end", "sammusic://track/9", "https://", "example.org",
        )) {
            assertFalse(url, Orchestrator.sendable(url))
            assertFalse(url, Orchestrator.offers(installed = true, url = url))
        }
    }

    @Test fun theIntentCarriesTheAddressAndTitleToTheOrchestratorOnly() {
        val send = Orchestrator.sendIntent("https://example.org/trail", "Trail map: Upper Geyser Basin")!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("app.superawesomemagical.orchestrator", send.`package`)
        assertNull(send.component)
        assertEquals("https://example.org/trail", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Trail map: Upper Geyser Basin", send.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(setOf(Intent.EXTRA_TEXT, Intent.EXTRA_SUBJECT), send.extras!!.keySet())
    }

    @Test fun aLinkWithoutATitleCarriesNoSubject() {
        val send = Orchestrator.sendIntent("https://example.org/", " ")!!
        assertEquals(setOf(Intent.EXTRA_TEXT), send.extras!!.keySet())
    }

    @Test fun nothingButWebPagesMakesAnIntent() {
        assertNull(Orchestrator.sendIntent("about:blank", "New tab"))
        assertNull(Orchestrator.sendIntent("javascript:alert(1)", ""))
        assertNull(Orchestrator.sendIntent("file:///etc/hosts", "hosts"))
    }
}
