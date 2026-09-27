package app.mindcore.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream

/** What arrived from the share menu or the ＋ sheet. */
data class Capture(
    val links: List<String> = emptyList(),
    val text: String? = null, // shared text that isn't just a link
    val images: List<Uri> = emptyList(),
    val chatExport: Uri? = null, // WhatsApp "Export chat" .zip or .txt
    val pdfs: List<Uri> = emptyList(), // books, papers
) {
    val isEmpty get() = links.isEmpty() && text.isNullOrBlank() && images.isEmpty() && chatExport == null && pdfs.isEmpty()

    companion object {
        private val URL = Regex("""https?://[^\s<>"')\]]+""")

        /** Split shared text into links and the words around them (the words become the note). */
        fun fromText(raw: String?): Capture {
            if (raw.isNullOrBlank()) return Capture()
            val links = URL.findAll(raw).map { it.value }.distinct().toList()
            val rest = URL.replace(raw, "").trim()
            return Capture(links = links, text = rest.ifBlank { null })
        }
    }
}

data class CaptureResult(val saved: Int, val duplicates: Int, val skipped: Int, val message: String)

class Capturer(private val context: Context, private val api: Api) {

    suspend fun save(c: Capture, note: String, voice: java.io.File? = null): CaptureResult {
        c.chatExport?.let { return importChat(it) }
        var saved = 0
        var dup = 0
        var skipped = 0
        val saves = JSONArray()
        c.links.forEach { saves.put(JSONObject().put("url", it).put("note", note.ifBlank { c.text ?: "" }).put("source", "share")) }
        // A shared note with no link is saved as a note of its own.
        if (c.links.isEmpty() && !c.text.isNullOrBlank()) saves.put(JSONObject().put("text", listOf(c.text, note).filter { it.isNotBlank() }.joinToString("\n\n")).put("source", "share"))
        // The voice note rides on the first save; on its own it becomes a spoken note.
        val voiceUrl = voice?.let { uploadAudio(it) }
        if (voiceUrl != null) {
            if (saves.length() > 0) saves.getJSONObject(0).put("voice_url", voiceUrl)
            else if (c.images.isEmpty()) saves.put(JSONObject().put("voice_url", voiceUrl).put("source", "voice"))
        }
        c.pdfs.forEach { uri ->
            val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                ?: throw ApiError("Couldn't read the PDF")
            // Cloudinary's free plan takes files up to 10 MB.
            if (bytes.size > 10 * 1024 * 1024) throw ApiError("This PDF is ${bytes.size / (1024 * 1024)} MB; the free storage takes up to 10 MB per file.")
            val sign = api.signUpload()
            val url = upload(bytes, "book.pdf", sign.getString("file_upload_url"), sign)
            saves.put(JSONObject().put("file_url", url).put("note", note).put("source", "share"))
        }
        if (c.images.isNotEmpty()) {
            val sign = api.signUpload() // throws a clear error if Cloudinary isn't set up yet
            c.images.forEachIndexed { i, uri ->
                val url = uploadToCloudinary(uri, sign)
                val save = JSONObject().put("image_url", url).put("note", note).put("source", "share")
                if (i == 0 && voiceUrl != null && saves.length() == 0) save.put("voice_url", voiceUrl)
                saves.put(save)
            }
        }
        if (saves.length() > 0) {
            val counts = api.addSaves(saves)
            saved += counts.optInt("added")
            dup += counts.optInt("duplicate")
            skipped += counts.optInt("skipped")
        }
        val message = when {
            saved > 0 && dup > 0 -> "Saved $saved · $dup already in mind-core"
            saved > 0 -> if (saved == 1) "Saved" else "Saved $saved"
            dup > 0 -> "Already in mind-core"
            skipped > 0 -> "Skipped"
            else -> "Nothing to save"
        }
        return CaptureResult(saved, dup, skipped, message)
    }

    private suspend fun importChat(uri: Uri): CaptureResult {
        val chat = withContext(Dispatchers.IO) { readChatText(uri) } ?: throw ApiError("No chat .txt found in that file")
        val r = api.importWhatsapp(chat)
        val added = r.optInt("added")
        return CaptureResult(added, r.optInt("duplicate"), r.optInt("skipped"),
            "Found ${r.optInt("found")} links · $added new · ${r.optInt("duplicate")} already saved")
    }

    /** WhatsApp shares either the .txt itself or a .zip holding "WhatsApp Chat with ….txt" plus media. */
    private fun readChatText(uri: Uri): String? {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: ""
        val type = resolver.getType(uri) ?: ""
        resolver.openInputStream(uri)?.use { input ->
            if (!name.endsWith(".zip", true) && !type.contains("zip")) return input.bufferedReader().readText()
            ZipInputStream(input).use { zip ->
                var best: String? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(".txt", true)) {
                        val text = zip.bufferedReader().readText()
                        if (best == null || text.length > best.length) best = text
                    }
                }
                return best
            }
        }
        return null
    }

    /** Upload a voice note; returns its Cloudinary URL (audio is filed under "video" there). */
    suspend fun uploadAudio(file: java.io.File): String {
        val sign = api.signUpload()
        return upload(file.readBytes(), "voice.m4a", sign.getString("audio_upload_url"), sign)
    }

    private suspend fun uploadToCloudinary(uri: Uri, sign: JSONObject): String {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            ?: throw ApiError("Couldn't read the image")
        if (bytes.size > 10 * 1024 * 1024) throw ApiError("Image is over Cloudinary's 10 MB free limit")
        return upload(bytes, "shot.jpg", sign.getString("upload_url"), sign)
    }

    private suspend fun upload(bytes: ByteArray, filename: String, uploadUrl: String, sign: JSONObject): String = withContext(Dispatchers.IO) {
        val boundary = "mindcore-" + UUID.randomUUID()
        val body = ByteArrayOutputStream().apply {
            fun field(name: String, value: String) =
                write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            field("api_key", sign.getString("api_key"))
            field("timestamp", sign.getLong("timestamp").toString())
            field("folder", sign.getString("folder"))
            field("signature", sign.getString("signature"))
            write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val conn = URL(uploadUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("content-type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code < 400) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (code >= 400) throw ApiError("Cloudinary upload failed ($code): ${text.take(160)}")
            JSONObject(text).getString("secure_url")
        } finally {
            conn.disconnect()
        }
    }
}
