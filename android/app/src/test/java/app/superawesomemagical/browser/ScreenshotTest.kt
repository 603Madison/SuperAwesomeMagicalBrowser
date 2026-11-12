package app.superawesomemagical.browser

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superawesomemagical.browser.ui.BrowserScreen
import app.superawesomemagical.browser.ui.BrowserTheme
import app.superawesomemagical.browser.ui.ScreenActions
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the browser's screens to PNGs in android/app/build/outputs/screenshots, for a look (and to
 * check they come up). The page itself is a stand-in: Gecko doesn't run in these tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temp = TemporaryFolder()

    private fun browser(start: Boolean): Pair<Browser, History> {
        val history = History(temp.newFolder())
        val browser = Browser(compose.activity, { error("No Gecko in screenshot tests") }, history)
        val pages = listOf(
            "https://www.ecosia.org/search?q=northern+lights" to "northern lights - Ecosia",
            "https://en.wikipedia.org/wiki/Aurora" to "Aurora - Wikipedia",
            "https://www.nps.gov/yell/index.htm" to "Yellowstone National Park",
            "https://github.com/603Madison" to "603Madison · GitHub",
        )
        pages.forEachIndexed { i, (url, title) ->
            browser.tabs.add(BrowserTab(i + 1L, GeckoSession(), private = false, openerId = null).also { it.url = url; it.title = title })
        }
        browser.tabs.add(BrowserTab(9, GeckoSession(), private = true, openerId = null).also { it.url = "https://example.org/"; it.title = "A private page" })
        val shown = if (start) BrowserTab(10, GeckoSession(), false, null).also { browser.tabs.add(it) } else browser.tabs[1]
        shown.secure = true
        browser.select(shown)
        return browser to history
    }

    private fun render(start: Boolean, drawer: Boolean = false) {
        val (browser, history) = browser(start)
        compose.setContent {
            BrowserTheme {
                BrowserScreen(browser, history, MutableSharedFlow(), ScreenActions({ _, _ -> }, {}), drawerOpen = drawer) { modifier ->
                    FakePage(modifier)
                }
            }
        }
    }

    /** Stands in for the web page (GeckoView). */
    @Composable
    private fun FakePage(modifier: Modifier) {
        Column(modifier.background(Color.White).padding(20.dp)) {
            Text("Aurora", fontSize = 30.sp, fontFamily = FontFamily.Serif, color = Color(0xFF202122))
            Spacer(Modifier.height(4.dp))
            Text("From Wikipedia, the free encyclopedia", fontSize = 13.sp, color = Color(0xFF54595D))
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF243B55)))
            Spacer(Modifier.height(16.dp))
            Text(
                "An aurora is a natural light display in Earth's sky, predominantly observed in high-latitude regions " +
                    "around the Arctic and Antarctic. Auroras display dynamic patterns of brilliant lights that appear as " +
                    "curtains, rays, spirals or dynamic flickers covering the entire sky.",
                fontSize = 16.sp, lineHeight = 24.sp, fontFamily = FontFamily.Serif, color = Color(0xFF202122),
            )
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun startLight() { render(start = true); save("start-light") }

    @Config(qualifiers = "+night")
    @Test fun startDark() { render(start = true); save("start-dark") }

    @Test fun pageLight() { render(start = false); save("page-light") }

    @Config(qualifiers = "+night")
    @Test fun tabsDark() { render(start = false, drawer = true); save("tabs-dark") }

    @Test fun tabsLight() { render(start = false, drawer = true); save("tabs-light") }

    @Config(qualifiers = "w1280dp-h800dp-night-mdpi")
    @Test fun tabletDark() { render(start = true); save("tablet-dark") }
}
