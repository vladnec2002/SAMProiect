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
    suspend fun loadDetailsSmart(url: String, desiredVersionName: String? = null): AppInfo? =
        withContext(Dispatchers.IO) {

            // 1) dacă e deja release page / version-ish, încearcă direct
            val direct = runCatching { scrapeReleasePage(url) }.getOrNull()
            if (direct != null) return@withContext direct

            // 2) fallback: app page → alege release-card care conține versiunea din card
            val releaseUrl = runCatching { pickReleaseUrlFromAppPage(url, desiredVersionName) }.getOrNull()
                ?: return@withContext null

            scrapeReleasePage(releaseUrl)
        }


    private suspend fun pickReleaseUrlFromAppPage(appPageUrl: String, desiredVersionName: String?): String? {
        delay(PAGE_DELAY)

        val doc = Jsoup.connect(appPageUrl)
            .userAgent(USER_AGENT)
            .header("Accept-Language", ACCEPT_LANG)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .timeout(20_000)
            .followRedirects(true)
            .get()

        val releases = doc.select("div.release-card")
        if (releases.isEmpty()) return null

        val picked =
            desiredVersionName
                ?.takeIf { it.isNotBlank() }
                ?.let { ver ->
                    releases.firstOrNull { it.text().contains(ver, ignoreCase = true) }
                }
                ?: releases.first()

        val href = picked.selectFirst("a[href]")?.attr("href")?.substringBefore("?").orEmpty()
        if (href.isBlank()) return null
        return if (href.startsWith("http")) href else BASE + href
    }



    private suspend fun scrapeReleasePage(url: String): AppInfo? {
        delay(PAGE_DELAY)

        val doc = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .header("Accept-Language", ACCEPT_LANG)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .timeout(20_000)
            .followRedirects(true)
            .get()

        // =========
        // 1) INFO SLIDE (Version / Uploaded / File size / Downloads)
        // =========
        val infoMap = parseInfoSlide(doc)

        val version = infoMap["Version"]
        val fileSize = infoMap["File size"]
        val downloads = infoMap["Downloads"]

        // Uploaded: prefer data-utcdate dacă există
        val uploadedUtc = doc.selectFirst(".infoSlide span.datetime_utc[data-utcdate]")?.attr("data-utcdate")
        val uploadedText = infoMap["Uploaded"]

        // =========
        // 2) VARIANTS TABLE
        // =========
        val variants = parseVariants(doc)

        // best-effort: din prima variantă
        val bestArch = variants.firstOrNull()?.architecture
        val bestMinAndroid = variants.firstOrNull()?.minAndroid
        val bestDpi = variants.firstOrNull()?.dpi
        val bestBadges = variants.firstOrNull()?.badges ?: emptyList()

        // =========
        // 3) Package name (nu e mereu în release page; dacă există în pagină îl luăm)
        // =========
        val pkg = extractPackageName(doc).orEmpty()

        // Developer / AppName (best-effort: din breadcrumbs / header)
        val appName = extractAppName(doc).orEmpty()
        val developer = extractDeveloper(doc)

        return AppInfo(
            source = "APKMirror",
            packageName = pkg,
            appName = if (appName.isBlank()) "APKMirror" else appName,
            versionName = version,
            releaseDate = uploadedText,  // text friendly
            uploadedUtc = uploadedUtc,   // data stabilă dacă vrei
            developer = developer,
            downloadUrl = url, // release page url
            fileSize = fileSize,
            downloads = downloads,
            minAndroid = bestMinAndroid,
            architecture = bestArch,
            dpi = bestDpi,
            badges = bestBadges,
            variants = variants
        )
    }

    private fun parseInfoSlide(doc: Document): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val slide = doc.selectFirst("div.infoSlide") ?: return map

        val names = slide.select("div.infoSlide-name")
        val values = slide.select("div.infoSlide-value")

        val n = minOf(names.size, values.size)
        for (i in 0 until n) {
            val k = names[i].text().trim()
            val v = values[i].text().trim()
            if (k.isNotBlank() && v.isNotBlank()) map[k] = v
        }
        return map
    }

    private fun parseVariants(doc: Document): List<com.example.apptracker.data.model.ApkMirrorVariant> {
        val out = mutableListOf<com.example.apptracker.data.model.ApkMirrorVariant>()

        val table = doc.selectFirst("div.variants-table") ?: return out
        val rows = table.select("div.table-row")
        for (row in rows) {
            val linkEl = row.selectFirst("a.accent_color[href]") ?: continue
            val href = linkEl.attr("href").substringBefore("?")
            val variantPageUrl = if (href.startsWith("http")) href else BASE + href

            val variantName = linkEl.text().trim().ifBlank { null }
            val badges = row.select("span.apkm-badge").mapNotNull { it.text()?.trim() }.filter { it.isNotBlank() }

            val cells = row.select("div.table-cell")
            // în HTML-ul tău: [0]=link+badges, [1]=arch, [2]=minAndroid, [3]=dpi
            val architecture = cells.getOrNull(1)?.text()?.trim()?.ifBlank { null }
            val minAndroid = cells.getOrNull(2)?.text()?.trim()?.ifBlank { null }
            val dpi = cells.getOrNull(3)?.text()?.trim()?.ifBlank { null }

            out += com.example.apptracker.data.model.ApkMirrorVariant(
                variantName = variantName,
                badges = badges,
                architecture = architecture,
                minAndroid = minAndroid,
                dpi = dpi,
                variantPageUrl = variantPageUrl
            )
        }
        return out
    }

    private fun extractAppName(doc: Document): String? {
        // încearcă un header comun
        return doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
    }

    private fun extractDeveloper(doc: Document): String? {
        // uneori apare ca “by X”
        val by = doc.select("a, span, div").firstOrNull { it.text().trim().startsWith("by ", true) }?.text()?.trim()
        return by?.removePrefix("by ")?.trim()
    }

    private fun extractPackageName(doc: Document): String? {
        // APKMirror are uneori “Package Name” în diverse zone; încercăm generic
        val txt = doc.text()
        val m = Regex("""Package Name\s+([a-zA-Z0-9_\.]+)""").find(txt) ?: return null
        return m.groupValues.getOrNull(1)
    }




    // =========================
// INTERNAL SCRAPING
// =========================
    private suspend fun getVersionDetailsFromAppPage(
        appPageUrl: String,
        desiredVersionName: String?
    ): VersionDetails {
        delay(PAGE_DELAY)

        val doc = Jsoup.connect(appPageUrl)
            .userAgent(USER_AGENT)
            .header("Accept-Language", ACCEPT_LANG)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .timeout(20_000)
            .followRedirects(true)
            .get()

        val releases = doc.select("div.release-card")
        if (releases.isEmpty()) {
            return VersionDetails(null, null, null, null, appPageUrl, null, null)
        }

        // ✅ dacă avem versiune din card, alegem release-card-ul care o conține
        val pickedRelease =
            desiredVersionName
                ?.takeIf { it.isNotBlank() }
                ?.let { ver ->
                    releases.firstOrNull { card ->
                        card.text().contains(ver, ignoreCase = true)
                    }
                }
                ?: releases.first()

        val link = pickedRelease.selectFirst("a[href]")
            ?: return VersionDetails(null, null, null, null, appPageUrl, null, null)

        val href = link.attr("href")
        val versionPageUrl = if (href.startsWith("http")) href else BASE + href

        // ✅ de aici încolo scrape-uim version page (minAndroid, arch, package, uploaded etc.)
        return scrapeVersionPage(versionPageUrl)
    }

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
