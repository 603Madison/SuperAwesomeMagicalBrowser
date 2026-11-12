package app.superawesomemagical.browser

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** What's typed in the address bar, and how an address is shown there. */
object Address {
    /** Ecosia, the browser's search engine (as on the desktop). */
    private const val SEARCH = "https://www.ecosia.org/search?q="

    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d{1,5})?$")
    private val domain = Regex("^([\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?\\.)+\\p{L}{2,}(:\\d{1,5})?$")

    /** The search address for [terms]. */
    fun search(terms: String): String = SEARCH + URLEncoder.encode(terms.trim(), "UTF-8")

    /**
     * Turns what was typed into the address to open: an address as it is (adding https:// to a
     * bare domain, http:// for this computer and local network addresses), anything else as a
     * search. Returns null for nothing.
     */
    fun toUrl(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.contains(' ')) return search(text)
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("about:") || lower.startsWith("view-source:") || lower.startsWith("data:")
        ) return text
        val host = text.substringBefore('/').substringBefore('?').substringBefore('#')
        val local = host.equals("localhost", true) || host.startsWith("localhost:", true) || ipv4.matches(host)
        if (local) return "http://$text"
        if (domain.matches(host)) return "https://$text"
        // "mailto:", "tel:" and other schemes are left to the page and the system; the rest is a search.
        if (scheme.containsMatchIn(text)) return text
        return search(text)
    }

    /** The search terms if [url] is a search on our engine, to show them instead of the address. */
    fun searchTerms(url: String): String? {
        if (!url.startsWith(SEARCH)) return null
        val query = url.removePrefix(SEARCH).substringBefore('&').substringBefore('#')
        return runCatching { URLDecoder.decode(query, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** The site's name for the address bar and tabs: its host without "www.". */
    fun host(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull() ?: return url
        return host.removePrefix("www.")
    }

    /** Whether [url] is a page the browser shows (rather than one for another app, like tel:). */
    fun isWeb(url: String): Boolean {
        val lower = url.lowercase()
        return listOf("http:", "https:", "about:", "data:", "blob:", "file:", "view-source:", "moz-extension:", "resource:")
            .any { lower.startsWith(it) }
    }

    /** "github.com" → "Github": a short name for a site's tile. */
    fun siteName(url: String): String {
        val label = host(url).substringBefore('.')
        return label.replaceFirstChar { it.uppercase() }
    }
}
