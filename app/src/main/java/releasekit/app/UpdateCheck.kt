package releasekit.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A published build a phone can actually install. */
data class Release(
    /** The number from the release tag ("build-12" is 12), which is also its versionCode. */
    val versionCode: Int,
    val versionName: String,
    /** Direct link to the APK. A release without one is never offered. */
    val apkUrl: String,
)

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(val release: Release) : UpdateStatus
    data class Unavailable(val reason: String) : UpdateStatus
}

/**
 * A sideloaded app has no update channel: Android never checks anywhere, so without this nothing
 * would ever say a new version exists.
 *
 * It reads GitHub's public releases API, which needs no login, so there is no token inside the
 * APK (anyone could pull one back out). The catch: it only works while the repository is public.
 * A private repository answers 404, and the check says so rather than claiming "up to date".
 */
object UpdateCheck {

    suspend fun check(
        api: String = BuildConfig.RELEASES_API,
        installed: Int = BuildConfig.VERSION_CODE,
    ): UpdateStatus = withContext(Dispatchers.IO) {
        if (api.isEmpty()) return@withContext UpdateStatus.Unavailable("Built on a computer, not by GitHub, so there is nowhere to check.")
        try {
            val connection = (URL(api).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            try {
                when (val code = connection.responseCode) {
                    404 -> UpdateStatus.Unavailable("GitHub didn't show the releases. Update checks work once the repository is public.")
                    in 200..299 -> decide(connection.inputStream.bufferedReader().use { it.readText() }, installed)
                    else -> UpdateStatus.Unavailable("GitHub answered HTTP $code. Try again later.")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            UpdateStatus.Unavailable("Couldn't reach GitHub: ${e.message ?: "no connection"}")
        }
    }

    /** What to tell the user, given GitHub's answer and the installed versionCode. */
    fun decide(body: String, installed: Int, apkName: String = BuildConfig.APK_NAME): UpdateStatus {
        // An answer that can't be read says nothing about whether there is an update.
        val releases = installableReleases(body, apkName)
            ?: return UpdateStatus.Unavailable("GitHub's answer couldn't be read. Try again later.")
        val newest = releases.maxByOrNull { it.versionCode }
        return if (newest != null && newest.versionCode > installed) UpdateStatus.Available(newest) else UpdateStatus.UpToDate
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Every release with an APK attached, passing over drafts, pre-releases and tags that aren't
     * `build-<number>`; null when the answer isn't a list of releases.
     *
     * The whole list, not /releases/latest: a release has no APK while its build is still
     * running, and offering it then would open a page with nothing to install.
     */
    fun installableReleases(body: String, apkName: String): List<Release>? {
        val list = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
        return list.mapNotNull { it as? JsonObject }
            .filterNot { it.bool("draft") || it.bool("prerelease") }
            .mapNotNull { installable(it, apkName) }
    }

    private fun installable(o: JsonObject, apkName: String): Release? {
        val code = o.string("tag_name")?.removePrefix("build-")?.toIntOrNull() ?: return null
        val apks = o["assets"]?.jsonArray.orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
        // The universal APK, so the download works whatever the phone's processor.
        val apk = (apks.firstOrNull { it.string("name") == apkName } ?: apks.firstOrNull())
            ?.string("browser_download_url")
            ?: return null
        return Release(code, o.string("name") ?: "build $code", apk)
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean =
        runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true
}
