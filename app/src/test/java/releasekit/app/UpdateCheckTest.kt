package releasekit.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun release(tag: String, vararg apks: String, draft: Boolean = false, prerelease: Boolean = false) =
        """{"tag_name":"$tag","name":"My App 1.0.${tag.removePrefix("build-")}","draft":$draft,"prerelease":$prerelease,
           "assets":[${apks.joinToString(",") { """{"name":"$it","browser_download_url":"https://example.test/$tag/$it"}""" }}]}"""

    private fun decide(installed: Int, vararg releases: String) =
        UpdateCheck.decide("[${releases.joinToString(",")}]", installed, apkName = "my-app.apk")

    @Test
    fun offersTheHighestBuildNotTheFirstListed() {
        val s = decide(3, release("build-4", "my-app.apk"), release("build-9", "my-app.apk"), release("build-6", "my-app.apk"))
        assertEquals(9, (s as UpdateStatus.Available).release.versionCode)
    }

    @Test
    fun offersTheUniversalApkOverTheSmallerOne() {
        val s = decide(1, release("build-2", "my-app-arm64.apk", "my-app.apk"))
        assertTrue((s as UpdateStatus.Available).release.apkUrl.endsWith("/my-app.apk"))
    }

    @Test
    fun theInstalledBuildIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, decide(9, release("build-9", "my-app.apk")))
    }

    @Test
    fun passesOverReleasesItCannotInstall() {
        // Still building (no APK yet), a draft, a pre-release, a hand-made tag.
        val s = decide(1, release("build-5"), release("build-6", "my-app.apk", draft = true),
            release("build-7", "my-app.apk", prerelease = true), release("v2.0", "my-app.apk"))
        assertEquals(UpdateStatus.UpToDate, s)
    }

    @Test
    fun anUnreadableAnswerIsNeverUpToDate() {
        for (body in listOf("not json", """{"message":"Not Found"}""", "")) {
            assertTrue(body, UpdateCheck.decide(body, 1, "my-app.apk") is UpdateStatus.Unavailable)
        }
    }

    @Test
    fun noReleasesYetIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, UpdateCheck.decide("[]", 1, "my-app.apk"))
    }
}
