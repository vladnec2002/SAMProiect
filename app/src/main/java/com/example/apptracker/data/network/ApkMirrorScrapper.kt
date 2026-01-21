package com.example.apptracker.data.network

import com.example.apptracker.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

object ApkMirrorScraper {

    private const val BASE = "https://www.apkmirror.com"

    // ✅ Use a real browser UA (APKMirror often serves different HTML for bot-like UAs)
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // ✅ Optional but helpful
    private const val ACCEPT_LANG = "en-US,en;q=0.9"

    private const val SEARCH_DELAY = 1200L
    private const val PAGE_DELAY = 900L

    // prinde versiuni gen: 1.2 / 1.2.3 / 10.0.1 etc.
    private val VERSION_REGEX = Regex("""(\d+(?:\.\d+)+)\s*$""")

    // =========================
    // SEARCH LITE (RAPID)
    // =========================
    suspend fun searchByNameLite(query: String): List<AppInfo> =
        withContext(Dispatchers.IO) {

            delay(SEARCH_DELAY)

            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE/?s=$q&post_type=app"

            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", ACCEPT_LANG)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Connection", "keep-alive")
                .timeout(20_000)
                .followRedirects(true)
                .get()

            val mainContent = doc.selectFirst("div#content") ?: doc

            // ✅ Robust: catches appRow/appRowMini and other appRow variants
            val rows = mainContent.select("div[class*=appRow]")

            val apps = mutableListOf<AppInfo>()

            for (row in rows) {

                // ✅ Title fallback
                val titleEl =
                    row.selectFirst(".appRowTitle")
                        ?: row.selectFirst("h5, h4, h3")
                        ?: row.selectFirst("a")

                val titleText = titleEl?.text()?.trim().orEmpty()
                if (titleText.isBlank()) continue

                // ✅ Link fallback (prefer /apk/)
                val linkEl =
                    row.selectFirst("a[href*=/apk/]")
                        ?: row.selectFirst("a[href]")

                val href = linkEl?.attr("href")?.substringBefore("?").orEmpty()
                if (href.isBlank()) continue

                val pageUrl = if (href.startsWith("http")) href else BASE + href

                // ✅ Icon fallback: data-src is common
                val iconEl = row.selectFirst("img")
                val iconUrl = when {
                    iconEl == null -> null
                    iconEl.hasAttr("data-src") -> iconEl.absUrl("data-src").ifBlank { null }
                    else -> iconEl.absUrl("src").ifBlank { null }
                }

                // 🔹 developer este PE ALT RÂND: "by SYBO Games"
                val developer = row
                    .select("span, small, div, a")
                    .firstOrNull { it.text().trim().startsWith("by ", ignoreCase = true) }
                    ?.text()
                    ?.removePrefix("by")
                    ?.trim()
                    ?.ifBlank { null }

                // 🔹 versiunea din finalul titlului
                val versionMatch = VERSION_REGEX.find(titleText)
                val versionName = versionMatch?.groupValues?.getOrNull(1)

                val appName = if (versionName != null) {
                    titleText.removeSuffix(versionName).trim()
                } else {
                    titleText
                }

                val lastUpdated = normalizeLastUpdated(extractLastUpdated(row))

                apps += AppInfo(
                    source = "APKMirror",
                    packageName = "",
                    appName = appName,
                    versionName = versionName,
                    developer = developer,
                    downloadUrl = pageUrl,
                    iconUrl = iconUrl,
                    lastUpdated = lastUpdated
                )
            }

            apps.distinctBy {
                "${it.appName.lowercase()}:${it.versionName ?: ""}:${it.developer ?: ""}"
            }
        }

    // =========================
    // LOAD DETAILS (CLICK)
    // =========================
    suspend fun loadDetailsSmart(url: String): AppInfo? =
        withContext(Dispatchers.IO) {

            // 1️⃣ încearcă direct ca version page
            val direct = runCatching { scrapeVersionPage(url) }.getOrNull()
            if (direct != null && (
                        direct.packageName != null ||
                                direct.versionName != null ||
                                direct.minAndroid != null
                        )
            ) {
                return@withContext AppInfo(
                    source = "APKMirror",
                    packageName = direct.packageName ?: "",
                    appName = "",
                    versionName = direct.versionName,
                    releaseDate = direct.releaseDate,
                    developer = direct.developer,
                    downloadUrl = url,
                    minAndroid = direct.minAndroid,
                    architecture = direct.architecture
                )
            }

            // 2️⃣ fallback: app page → latest version
            val latest = runCatching { getLatestVersionDetails(url) }.getOrNull()
                ?: return@withContext null

            AppInfo(
                source = "APKMirror",
                packageName = latest.packageName ?: "",
                appName = "",
                versionName = latest.versionName,
                releaseDate = latest.releaseDate,
                developer = latest.developer,
                downloadUrl = latest.downloadUrl ?: url,
                minAndroid = latest.minAndroid,
                architecture = latest.architecture
            )
        }

    // =========================
    // INTERNAL SCRAPING
    // =========================
    private data class VersionDetails(
        val packageName: String?,
        val versionName: String?,
        val releaseDate: String?,
        val developer: String?,
        val downloadUrl: String?,
        val minAndroid: String?,
        val architecture: String?
    )

    private suspend fun getLatestVersionDetails(appPageUrl: String): VersionDetails {
        delay(PAGE_DELAY)

        val doc = Jsoup.connect(appPageUrl)
            .userAgent(USER_AGENT)
            .header("Accept-Language", ACCEPT_LANG)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .timeout(20_000)
            .followRedirects(true)
            .get()

        val firstRelease = doc.selectFirst("div.release-card")
            ?: return VersionDetails(null, null, null, null, appPageUrl, null, null)

        val link = firstRelease.selectFirst("a[href]")
            ?: return VersionDetails(null, null, null, null, appPageUrl, null, null)

        val versionPageUrl = BASE + link.attr("href")
        return scrapeVersionPage(versionPageUrl)
    }

    private suspend fun scrapeVersionPage(url: String): VersionDetails {
        delay(PAGE_DELAY)

        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .header("Accept-Language", ACCEPT_LANG)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .timeout(20_000)
            .followRedirects(true)
            .get()

        val versionName = doc.select("span.info")
            .firstOrNull { it.text().startsWith("Version:") }
            ?.text()
            ?.removePrefix("Version:")
            ?.trim()

        val packageName = findSpec(doc, "Package:")
        val developer = doc.select("a[href*=/developer/]").firstOrNull()?.text()?.trim()

        val uploaded = doc.select("div.dates-dl span")
            .firstOrNull { it.text().startsWith("Uploaded:") }
            ?.text()
            ?.removePrefix("Uploaded:")
            ?.trim()

        val minAndroid = findSpec(doc, "Requires Android:", "Min Android:")
        val architecture = findSpec(doc, "Architecture:", "Arch:")

        return VersionDetails(
            packageName = packageName,
            versionName = versionName,
            releaseDate = uploaded,
            developer = developer,
            downloadUrl = url,
            minAndroid = minAndroid,
            architecture = architecture
        )
    }

    // =========================
    // HELPERS
    // =========================
    private fun extractLastUpdated(row: Element): String? {
        // încearcă întâi selectorii “corecți”
        val direct = row.selectFirst(".appRowDate, .appRowUpdated")
            ?.text()
            ?.trim()
        if (!direct.isNullOrBlank()) return direct

        // fallback: ia textul întregului row (normalizeLastUpdated va scoate doar data)
        return row.text().trim()
    }

    private val DATE_REGEX = Regex(
        """\b(January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}\b""",
        RegexOption.IGNORE_CASE
    )

    private fun normalizeLastUpdated(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        // ia EXACT "January 12, 2026" din orice text mai lung
        val m = DATE_REGEX.find(raw)
        return m?.value
    }

    private fun findSpec(doc: Document, vararg labels: String): String? {
        val rows = doc.select("div.appspec-row")
        for (label in labels) {
            val row = rows.firstOrNull {
                it.selectFirst(".appspec-label")?.text() == label
            }
            val value = row?.selectFirst(".appspec-value")?.text()?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
}
