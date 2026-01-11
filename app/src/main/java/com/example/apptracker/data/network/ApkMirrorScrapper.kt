package com.example.apptracker.data.network

import com.example.apptracker.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

object ApkMirrorScraper {

    private const val BASE = "https://www.apkmirror.com"
    private const val USER_AGENT = "AppTracker/1.0"
    private const val SEARCH_DELAY = 2500L
    private const val PAGE_DELAY = 1200L

    suspend fun searchByName(query: String): List<AppInfo> = withContext(Dispatchers.IO) {

        // mic delay între request-uri de search
        delay(SEARCH_DELAY)

        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE/?s=$q&post_type=app"

        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(15000)
            .get()

        val mainContent = doc.selectFirst("div#content") ?: doc
        val cards = mainContent.select("div.appRow")

        val apps = mutableListOf<AppInfo>()

        // folosim for, nu mapNotNull, ca să putem apela funcții suspend
        for (el in cards) {
            val titleEl = el.selectFirst(".appRowTitle") ?: continue
            val linkEl = el.selectFirst("a[href*=/apk/]") ?: continue
            val iconEl = el.selectFirst("img")

            val appName = titleEl.text().trim()

            // link către pagina aplicației (nu către o versiune anume)
            val appPageUrl = BASE + linkEl.attr("href").substringBefore("?")
            val iconUrl = iconEl?.absUrl("src")

            // luăm detalii pentru cea mai nouă versiune
            val details = runCatching { getLatestVersionDetails(appPageUrl) }.getOrNull()

            val appInfo = AppInfo(
                source = "APKMirror",
                appName = appName,
                packageName = details?.packageName ?: "",
                versionName = details?.versionName,
                versionCode = null,  // am putea obține și aici, dar e un pas în plus
                releaseDate = details?.releaseDate,
                developer = details?.developer,
                downloadUrl = details?.downloadUrl ?: appPageUrl,
                iconUrl = iconUrl,

                // 🔽 info extra luate de pe pagina versiunii
                description = details?.description,
                fileSize = details?.fileSize,
                minAndroidVersion = details?.minAndroidVersion,
                downloads = details?.downloads
            )

            apps += appInfo
        }

        // eliminăm duplicatele după nume de aplicație
        apps.distinctBy { it.appName.lowercase() }
    }

    // ------------------ STEP 2: GET LATEST VERSION FROM APP PAGE ------------------

    private data class VersionDetails(
        val packageName: String?,
        val versionName: String?,
        val releaseDate: String?,
        val developer: String?,
        val downloadUrl: String?,
        val description: String?,
        val fileSize: String?,
        val minAndroidVersion: String?,
        val downloads: String?
    )

    // funcție suspend -> poate folosi delay
    private suspend fun getLatestVersionDetails(appPageUrl: String): VersionDetails {
        delay(PAGE_DELAY)

        val doc = Jsoup.connect(appPageUrl)
            .userAgent(USER_AGENT)
            .timeout(15000)
            .get()

        // prima versiune din listă = cea mai nouă
        val firstRelease = doc.selectFirst("div.release-card")
            ?: return VersionDetails(null, null, null, null, appPageUrl, null, null, null, null)

        val versionLink = firstRelease.selectFirst("a[href]") ?: return VersionDetails(
            null,
            null,
            null,
            null,
            appPageUrl,
            null,
            null,
            null,
            null
        )
        val versionPageUrl = BASE + versionLink.attr("href")

        return scrapeVersionPage(versionPageUrl)
    }

    // ------------------ STEP 3: SCRAPE VERSION PAGE ------------------

    // tot suspend, deci poate folosi delay
    private suspend fun scrapeVersionPage(url: String): VersionDetails {
        delay(PAGE_DELAY)

        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(15000)
            .get()

        // Version: 5.261.0
        val versionName = doc.select("span.info")
            .firstOrNull { it.text().startsWith("Version:") }
            ?.text()
            ?.removePrefix("Version:")
            ?.trim()
            ?.ifBlank { null }

        // Package: com.duckduckgo.mobile.android
        val packageName = doc.select("div.appspec-row")
            .firstOrNull { row -> row.selectFirst(".appspec-label")?.text() == "Package:" }
            ?.selectFirst(".appspec-value")
            ?.text()
            ?.trim()
            ?.ifBlank { null }

        // Uploaded: January 5, 2026 at ...
        val releaseDate = doc.select("div.dates-dl span")
            .firstOrNull { it.text().startsWith("Uploaded:") }
            ?.text()
            ?.removePrefix("Uploaded:")
            ?.trim()
            ?.ifBlank { null }

        // Developer (By <name> / link developer)
        val developer = doc.select("a[href*=/developer/]")
            .firstOrNull()
            ?.text()
            ?.trim()
            ?.ifBlank { null }

        // --------- Description (secțiunea “About <App> <version>”) ----------
        val description = run {
            val aboutHeader = doc.select("h3")
                .firstOrNull { it.text().startsWith("About ") }

            if (aboutHeader != null) {
                val sb = StringBuilder()
                var el = aboutHeader.nextElementSibling()

                // colectăm text până la următorul <h3> (ex: “screenshots”, “Download” etc.)
                while (el != null && el.tagName() != "h3") {
                    val text = el.text().trim()
                    if (text.isNotEmpty()) {
                        if (sb.isNotEmpty()) sb.append("\n\n")
                        sb.append(text)
                    }
                    el = el.nextElementSibling()
                }

                sb.toString().trim().ifBlank { null }
            } else {
                null
            }
        }

        // --------- File size: XX MB ----------
        val fileSize = doc.select("*")
            .firstOrNull { it.ownText().startsWith("File size:") }
            ?.ownText()
            ?.removePrefix("File size:")
            ?.trim()
            ?.ifBlank { null }

        // --------- Downloads: XX ----------
        val downloads = doc.select("*")
            .firstOrNull { it.ownText().startsWith("Downloads:") }
            ?.ownText()
            ?.removePrefix("Downloads:")
            ?.trim()
            ?.ifBlank { null }

        // --------- Min Android version: ex. "Android 5.0+" ----------
        val minAndroidVersion = doc.select("*")
            .mapNotNull { el ->
                val t = el.ownText().trim()
                if (t.matches(Regex("Android\\s+\\d+(\\.\\d+)?\\+"))) t else null
            }
            .firstOrNull()

        return VersionDetails(
            packageName = packageName,
            versionName = versionName,
            releaseDate = releaseDate,
            developer = developer,
            downloadUrl = url,           // pagina versiunii (de aici userul poate alege varianta potrivită)
            description = description,
            fileSize = fileSize,
            minAndroidVersion = minAndroidVersion,
            downloads = downloads
        )
    }
}
