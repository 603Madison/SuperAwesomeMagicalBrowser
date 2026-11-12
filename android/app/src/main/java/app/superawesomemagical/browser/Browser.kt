package app.superawesomemagical.browser

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.HistoryDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse

/** One tab: its Gecko session and what the interface shows of it. */
class BrowserTab(val id: Long, val session: GeckoSession, val private: Boolean, val openerId: Long?) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var secure by mutableStateOf(false)

    /** An address to open when the tab is first shown (restored tabs load when they're picked). */
    var pending: String? = null

    /** A new tab shows the start page until something is opened in it. */
    val isStart: Boolean get() = pending == null && (url.isBlank() || url == "about:blank")

    val label: String
        get() = when {
            isStart -> "New tab"
            title.isNotBlank() -> title
            else -> Address.host(pending ?: url)
        }
}

/** A question from a page (alert, confirm, a <select>...) or a long-press menu, for the interface to ask. */
sealed interface PageDialog {
    data class Alert(val title: String?, val message: String, val ok: () -> Unit) : PageDialog
    data class Confirm(val title: String?, val message: String, val answer: (Boolean) -> Unit) : PageDialog
    data class Text(val title: String?, val message: String, val value: String, val answer: (String?) -> Unit) : PageDialog
    data class Choice(
        val title: String?,
        val options: List<PromptDelegate.ChoicePrompt.Choice>,
        val multiple: Boolean,
        val answer: (List<PromptDelegate.ChoicePrompt.Choice>?) -> Unit,
    ) : PageDialog
    data class LinkMenu(val tab: BrowserTab, val link: String?, val image: String?, val text: String?) : PageDialog
}

/** What the browser needs from the screen it's shown on: pickers, other apps, messages. */
interface BrowserHost {
    fun pickFiles(mimeTypes: List<String>, multiple: Boolean, done: (List<Uri>) -> Unit)
    fun openElsewhere(url: String): Boolean
    fun download(response: WebResponse)
    fun message(text: String)
}

/**
 * The tabs and everything Gecko tells us about them. All of it runs on the main thread, where
 * GeckoView calls its delegates.
 */
class Browser(private val context: Context, private val runtimeProvider: () -> GeckoRuntime, private val history: History) {
    // Made when first needed (and never in the screenshot tests, which draw tabs without Gecko).
    private val runtime: GeckoRuntime get() = runtimeProvider()

    val tabs = mutableStateListOf<BrowserTab>()
    var selected by mutableStateOf<BrowserTab?>(null)
        private set
    var fullScreen by mutableStateOf(false)
        private set
    var dialog by mutableStateOf<PageDialog?>(null)
    var host: BrowserHost? = null

    private var nextId = 1L
    private val saved = context.getSharedPreferences("tabs", Context.MODE_PRIVATE)

    // ---- Tabs ----

    /** Opens a tab (on the start page when [url] is null) and shows it. */
    fun newTab(url: String? = null, private: Boolean = false, openerId: Long? = null, select: Boolean = true): BrowserTab {
        val tab = createTab(private, openerId)
        tab.session.open(runtime)
        if (url != null) tab.session.loadUri(url)
        add(tab, select, openerId)
        return tab
    }

    private fun createTab(private: Boolean, openerId: Long?): BrowserTab {
        val settings = GeckoSessionSettings.Builder().usePrivateMode(private).build()
        val tab = BrowserTab(nextId++, GeckoSession(settings), private, openerId)
        attach(tab)
        return tab
    }

    private fun add(tab: BrowserTab, select: Boolean, openerId: Long?) {
        // A tab opened from a page goes right after it, as on the desktop.
        val opener = tabs.indexOfFirst { it.id == openerId }
        if (opener >= 0) tabs.add(opener + 1, tab) else tabs.add(tab)
        if (select || selected == null) select(tab)
    }

    fun select(tab: BrowserTab) {
        selected = tab
        fullScreen = false
        tab.pending?.let { url ->
            tab.pending = null
            tab.session.loadUri(url)
        }
    }

    fun close(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tabs.removeAt(index)
        tab.session.close()
        if (selected == tab) {
            val next = tabs.firstOrNull { it.id == tab.openerId } ?: tabs.getOrNull(index) ?: tabs.getOrNull(index - 1)
            if (next != null) select(next) else newTab()
        }
    }

    /** Opens what was typed in the address bar or the start page's search box. */
    fun go(input: String, tab: BrowserTab? = selected) {
        val url = Address.toUrl(input) ?: return
        if (tab == null) newTab(url) else {
            tab.pending = null
            tab.session.loadUri(url)
        }
    }

    /** The system back button: leave full screen, go back, or go back to the tab this one came from. */
    fun back(): Boolean {
        val tab = selected ?: return false
        when {
            fullScreen -> tab.session.exitFullScreen()
            tab.canGoBack -> tab.session.goBack()
            tab.openerId != null && tabs.any { it.id == tab.openerId } -> close(tab)
            else -> return false
        }
        return true
    }

    // ---- Keeping tabs across restarts (private tabs aren't kept) ----

    fun save() {
        val kept = tabs.filter { !it.private && !it.isStart }
        val array = JSONArray()
        kept.forEach { array.put(JSONObject().put("url", it.pending ?: it.url).put("title", it.title)) }
        saved.edit()
            .putString("tabs", array.toString())
            .putInt("selected", kept.indexOf(selected).coerceAtLeast(0))
            .apply()
    }

    fun restore() {
        if (tabs.isNotEmpty()) return
        val array = runCatching { JSONArray(saved.getString("tabs", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val tab = createTab(private = false, openerId = null)
            tab.session.open(runtime)
            tab.pending = o.getString("url")
            tab.title = o.optString("title")
            tabs.add(tab)
        }
        val chosen = tabs.getOrNull(saved.getInt("selected", 0))
        if (chosen != null) select(chosen) else newTab()
    }

    // ---- What Gecko tells us ----

    private fun attach(tab: BrowserTab) {
        val session = tab.session
        session.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.loading = true
                tab.progress = 5
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
                tab.progress = 100
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: ProgressDelegate.SecurityInformation) {
                tab.secure = securityInfo.isSecure
            }
        }

        session.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.url = url ?: ""
                if (tab.url.isBlank() || tab.url == "about:blank") tab.title = ""
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            // Links for other apps (tel:, mailto:, intent:, app stores...) go to them.
            override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                if (Address.isWeb(request.uri)) return null
                host?.openElsewhere(request.uri)
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            // target=_blank and window.open(): a new tab right after this one.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val opened = createTab(tab.private, tab.id)
                add(opened, select = true, openerId = tab.id)
                return GeckoResult.fromValue(opened.session)
            }
        }

        session.contentDelegate = object : ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title ?: ""
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (selected == tab) this@Browser.fullScreen = fullScreen
            }

            override fun onCloseRequest(session: GeckoSession) {
                close(tab)
            }

            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContentDelegate.ContextElement) {
                val image = element.srcUri.takeIf { element.type == ContentDelegate.ContextElement.TYPE_IMAGE }
                if (element.linkUri == null && image == null) return
                dialog = PageDialog.LinkMenu(tab, element.linkUri, image, element.linkText ?: element.title ?: element.altText)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                host?.download(response)
            }

            // The page's process stopped (crashed, or Android needed the memory): start it again.
            override fun onCrash(session: GeckoSession) = recover()

            override fun onKill(session: GeckoSession) = recover()

            private fun recover() {
                val url = tab.url
                if (!tab.session.isOpen) tab.session.open(runtime)
                if (url.isNotBlank() && url != "about:blank") {
                    if (selected == tab) tab.session.loadUri(url) else tab.pending = url
                }
            }
        }

        session.historyDelegate = object : HistoryDelegate {
            override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
                val topLevel = flags and HistoryDelegate.VISIT_TOP_LEVEL != 0
                val redirect = flags and (HistoryDelegate.VISIT_REDIRECT_SOURCE or HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT) != 0
                val failed = flags and HistoryDelegate.VISIT_UNRECOVERABLE_ERROR != 0
                if (!tab.private && topLevel && !redirect && !failed) history.visited(url)
                return GeckoResult.fromValue(true)
            }
        }

        session.promptDelegate = Prompts(tab)
    }

    /** A page's questions, asked with our own dialogs. */
    private inner class Prompts(private val tab: BrowserTab) : PromptDelegate {
        /** Shows [shown], and closes it again if the page drops the question (it navigated away). */
        private fun ask(prompt: PromptDelegate.BasePrompt, shown: (GeckoResult<PromptResponse>) -> PageDialog): GeckoResult<PromptResponse> {
            val result = GeckoResult<PromptResponse>()
            val view = shown(result)
            prompt.setDelegate(object : PromptDelegate.PromptInstanceDelegate {
                override fun onPromptDismiss(prompt: PromptDelegate.BasePrompt) {
                    if (dialog === view) dialog = null
                }
            })
            if (selected == tab) dialog = view else if (!prompt.isComplete) result.complete(prompt.dismiss())
            return result
        }

        private fun finish(result: GeckoResult<PromptResponse>, prompt: PromptDelegate.BasePrompt, response: () -> PromptResponse) {
            dialog = null
            if (!prompt.isComplete) result.complete(response())
        }

        override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) =
            ask(prompt) { result -> PageDialog.Alert(prompt.title, prompt.message.orEmpty()) { finish(result, prompt) { prompt.dismiss() } } }

        override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) =
            ask(prompt) { result ->
                PageDialog.Confirm(prompt.title, prompt.message.orEmpty()) { yes ->
                    finish(result, prompt) {
                        prompt.confirm(if (yes) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE)
                    }
                }
            }

        override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) =
            ask(prompt) { result ->
                PageDialog.Text(prompt.title, prompt.message.orEmpty(), prompt.defaultValue.orEmpty()) { text ->
                    finish(result, prompt) { if (text == null) prompt.dismiss() else prompt.confirm(text) }
                }
            }

        override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) =
            ask(prompt) { result ->
                val flat = prompt.choices.flatMap { choice -> if (choice.items != null) choice.items!!.toList() else listOf(choice) }
                    .filter { !it.separator }
                PageDialog.Choice(prompt.title ?: prompt.message, flat, prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) { chosen ->
                    finish(result, prompt) {
                        when {
                            chosen == null -> prompt.dismiss()
                            prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE -> prompt.confirm(chosen.toTypedArray())
                            chosen.isEmpty() -> prompt.dismiss()
                            else -> prompt.confirm(chosen.first())
                        }
                    }
                }
            }

        override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
            val result = GeckoResult<PromptResponse>()
            val picker = host
            if (picker == null) {
                result.complete(prompt.dismiss())
                return result
            }
            val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
            picker.pickFiles(prompt.mimeTypes?.toList().orEmpty(), multiple) { uris ->
                if (prompt.isComplete) return@pickFiles
                result.complete(
                    when {
                        uris.isEmpty() -> prompt.dismiss()
                        multiple -> prompt.confirm(context, uris.toTypedArray())
                        else -> prompt.confirm(context, uris.first())
                    },
                )
            }
            return result
        }
    }
}
