package app.superawesomemagical.browser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/**
 * The sites visited most, for the start page's tiles: one entry per site (its front page), with
 * how often it was visited. Private tabs don't count. Kept in the app's files as JSON; a site
 * removed from the start page stays off it.
 */
class History(dir: File) {
    /** A site; [name] only for the starter sites (others are named after their address). */
    data class Site(val url: String, val visits: Int, val lastVisit: Long, val name: String? = null)

    private val file = File(dir, "top-sites.json")
    private val sites = LinkedHashMap<String, Site>()
    private val removed = HashSet<String>()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _top = MutableStateFlow<List<Site>>(emptyList())

    /** The sites for the start page, most visited first. */
    val top: StateFlow<List<Site>> = _top

    init {
        runCatching {
            val json = JSONObject(file.readText())
            json.optJSONArray("sites")?.let { array ->
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val site = Site(o.getString("url"), o.getInt("visits"), o.getLong("lastVisit"))
                    sites[keyOf(site.url) ?: continue] = site
                }
            }
            json.optJSONArray("removed")?.let { array -> for (i in 0 until array.length()) removed += array.getString(i) }
        }
        publish()
    }

    @Synchronized
    fun visited(url: String) {
        val key = keyOf(url) ?: return
        val front = frontPage(url) ?: return
        val old = sites[key]
        sites[key] = Site(front, (old?.visits ?: 0) + 1, System.currentTimeMillis())
        publish()
        save()
    }

    @Synchronized
    fun remove(url: String) {
        val key = keyOf(url) ?: return
        removed += key
        publish()
        save()
    }

    private fun publish() {
        _top.value = sites.filterKeys { it !in removed }.values
            .sortedWith(compareByDescending<Site> { it.visits }.thenByDescending { it.lastVisit })
            .take(MAX)
            .ifEmpty { STARTERS.filter { keyOf(it.url) !in removed } }
    }

    private fun save() {
        val json = JSONObject()
            .put("sites", JSONArray().apply {
                sites.values.sortedByDescending { it.visits }.take(KEEP).forEach {
                    put(JSONObject().put("url", it.url).put("visits", it.visits).put("lastVisit", it.lastVisit))
                }
            })
            .put("removed", JSONArray(removed.toList()))
            .toString()
        io.launch {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(file)
        }
    }

    companion object {
        const val MAX = 8
        private const val KEEP = 200

        /** For a new phone, until there is history to go on (as on the desktop). */
        val STARTERS = listOf(
            "https://www.wikipedia.org/" to "Wikipedia", "https://www.youtube.com/" to "YouTube",
            "https://github.com/" to "GitHub", "https://www.reddit.com/" to "Reddit",
        ).map { (url, name) -> Site(url, 0, 0, name) }

        /** A site is its host, without "www.". Only web pages count. */
        fun keyOf(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme != "http" && uri.scheme != "https") return null
            return uri.host?.lowercase()?.removePrefix("www.")
        }

        private fun frontPage(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val port = if (uri.port == -1) "" else ":${uri.port}"
            return "${uri.scheme}://${uri.host ?: return null}$port/"
        }
    }
}
