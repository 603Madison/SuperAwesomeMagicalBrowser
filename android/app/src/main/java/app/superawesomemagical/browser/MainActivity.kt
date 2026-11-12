package app.superawesomemagical.browser

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.superawesomemagical.browser.ui.BrowserScreen
import app.superawesomemagical.browser.ui.BrowserTheme
import app.superawesomemagical.browser.ui.ScreenActions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

class MainActivity : ComponentActivity(), BrowserHost {
    private val app get() = application as BrowserApplication
    private val browser get() = app.browser
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private var filesPicked: ((List<Uri>) -> Unit)? = null
    private val pickOne = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        filesPicked?.invoke(listOfNotNull(uri)); filesPicked = null
    }
    private val pickMany = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        filesPicked?.invoke(uris); filesPicked = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        browser.host = this
        if (browser.tabs.isEmpty()) {
            browser.restore()
            if (savedInstanceState == null) open(intent)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!browser.back()) moveTaskToBack(true)
            }
        })

        // A page's video in full screen: the system bars go too.
        lifecycleScope.launch {
            snapshotFlow { browser.fullScreen }.collect { full ->
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                if (full) {
                    bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    bars.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        val actions = ScreenActions(share = ::share, copy = ::copy)
        setContent {
            BrowserTheme {
                BrowserScreen(browser, app.history, messages, actions) { modifier ->
                    val tab = browser.selected
                    AndroidView(
                        factory = { context -> GeckoView(context) },
                        update = { view ->
                            if (tab != null && view.session != tab.session) {
                                view.releaseSession()
                                view.setSession(tab.session)
                            }
                        },
                        modifier = modifier,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    override fun onStop() {
        super.onStop()
        browser.save()
    }

    /** Links from other apps, searches, and text shared to the browser. */
    private fun open(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let { browser.newTab(it) }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let { browser.newTab(Address.search(it)) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Address.toUrl(text)?.let { browser.newTab(it) }
            }
        }
    }

    // ---- BrowserHost ----

    override fun pickFiles(mimeTypes: List<String>, multiple: Boolean, done: (List<Uri>) -> Unit) {
        filesPicked?.invoke(emptyList())
        filesPicked = done
        val types = mimeTypes.filter { '/' in it }.ifEmpty { listOf("*/*") }.toTypedArray()
        try {
            if (multiple) pickMany.launch(types) else pickOne.launch(types)
        } catch (e: ActivityNotFoundException) {
            filesPicked = null
            done(emptyList())
        }
    }

    override fun openElsewhere(url: String): Boolean {
        val intent = if (url.startsWith("intent:")) {
            runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()?.apply {
                // Only to apps that take links, never to a named component of another app.
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return try {
            startActivity(intent ?: return false)
            true
        } catch (e: ActivityNotFoundException) {
            val fallback = intent?.getStringExtra("browser_fallback_url")
            if (fallback != null && Address.isWeb(fallback)) browser.go(fallback) else message("No app here can open that link")
            false
        }
    }

    override fun download(response: WebResponse) {
        lifecycleScope.launch {
            val saved = Downloads.save(applicationContext, response)
            message(if (saved != null) "Saved ${saved.name} to Downloads" else "That download didn't work")
        }
    }

    override fun message(text: String) {
        messages.tryEmit(text)
    }

    private fun share(title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        if (title.isNotBlank()) send.putExtra(Intent.EXTRA_SUBJECT, title)
        startActivity(Intent.createChooser(send, null))
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", text))
    }
}
