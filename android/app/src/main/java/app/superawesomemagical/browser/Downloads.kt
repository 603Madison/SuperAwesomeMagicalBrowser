package app.superawesomemagical.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse

/** Saves what a page hands over as a download into the phone's Downloads folder. */
object Downloads {
    data class Saved(val name: String, val uri: Uri, val mimeType: String)

    suspend fun save(context: Context, response: WebResponse): Saved? = withContext(Dispatchers.IO) {
        val body = response.body ?: return@withContext null
        val headers = response.headers.mapKeys { it.key.lowercase() }
        val type = headers["content-type"]?.substringBefore(';')?.trim()?.ifEmpty { null }
        val name = URLUtil.guessFileName(response.uri, headers["content-disposition"], type)
        val mimeType = type ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        try {
            body.use { input -> resolver.openOutputStream(uri)!!.use { output -> input.copyTo(output) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            Saved(name, uri, mimeType)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
