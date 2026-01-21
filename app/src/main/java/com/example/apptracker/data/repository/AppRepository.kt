package com.example.apptracker.data.repository

import android.util.Log
import com.example.apptracker.data.model.AppInfo
import com.example.apptracker.data.network.ApkMirrorScraper
import com.example.apptracker.data.network.FdroidApiService
import com.example.apptracker.data.network.FdroidFile
import com.example.apptracker.data.network.FdroidIndex
import com.example.apptracker.data.network.FdroidVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface AppRepository {
    suspend fun searchLite(term: String, limit: Int = 20): List<AppInfo>
    suspend fun loadDetails(item: AppInfo): AppInfo
}

@Singleton
class AppRepositoryImpl @Inject constructor(
    private val fdroid: FdroidApiService
) : AppRepository {

    // =========================
    // FDROID INDEX CACHE
    // =========================
    private val indexMutex = Mutex()
    private var cachedIndex: FdroidIndex? = null

    private suspend fun getIndexCached(): FdroidIndex =
        indexMutex.withLock {
            cachedIndex ?: fdroid.getIndex().also { cachedIndex = it }
        }

    // =========================
    // HELPERS (IN THIS FILE)
    // =========================
    private fun keyOf(item: AppInfo): String =
        "${item.source}:${if (item.packageName.isNotBlank()) item.packageName else (item.downloadUrl ?: item.appName)}"

    private fun formatDate(ts: Long): String {
        // normalize seconds vs millis
        val millis = if (ts < 1_000_000_000_000L) ts * 1000L else ts
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
    }

    private fun pickLocalized(map: Map<String, String>?, preferred: String = "en-US"): String? {
        if (map.isNullOrEmpty()) return null
        return map[preferred]
            ?: map["en"]
            ?: map.values.firstOrNull { it.isNotBlank() }
    }

    private fun pickIconFile(iconMap: Map<String, FdroidFile>?, preferred: String = "en-US"): FdroidFile? {
        if (iconMap.isNullOrEmpty()) return null
        return iconMap[preferred]
            ?: iconMap["en"]
            ?: iconMap.values.firstOrNull()
    }

    /**
     * In index-v2.json, icon file names are sometimes:
     * - "org.app.png" (just the filename)
     * - "icons-640/org.app.png" (already includes folder)
     *
     * This handles both safely.
     */
    private fun fdroidIconUrl(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        return if (fileName.contains("/")) {
            "https://f-droid.org/repo/$fileName"
        } else {
            "https://f-droid.org/repo/icons-640/$fileName"
        }
    }

    /**
     * Pick the "latest" version for *release date* using `added`.
     * (This is what you wanted instead of "last updated".)
     */
    private fun latestByAdded(versions: Map<String, FdroidVersion>): Pair<String, FdroidVersion>? {
        if (versions.isEmpty()) return null
        val withAdded = versions.entries.filter { it.value.added != null }
        return if (withAdded.isNotEmpty()) {
            withAdded.maxByOrNull { it.value.added!! }?.let { it.key to it.value }
        } else {
            // fallback if some packages have no "added"
            versions.entries.firstOrNull()?.let { it.key to it.value }
        }
    }

    // ========================
    // SEARCH (lite) 10/10
    // ========================
    override suspend fun searchLite(term: String, limit: Int): List<AppInfo> =
        withContext(Dispatchers.IO) {

            val query = term.trim()
            if (query.isEmpty()) return@withContext emptyList()

            val lower = query.lowercase()

            // ✅ Force 10/10 split (best when limit == 20)
            val targetFdroid = minOf(10, limit)
            val targetMirror = minOf(10, maxOf(0, limit - targetFdroid))

            // Pull extra to survive dedup
            val fetchFdroid = targetFdroid * 3
            val fetchMirror = targetMirror * 3

            val fdroidResults = mutableListOf<AppInfo>()
            val mirrorResults = mutableListOf<AppInfo>()

            // ---------- F-DROID ----------
            runCatching {
                val index = getIndexCached()

                for ((pkg, entry) in index.packages) {
                    val meta = entry.metadata ?: continue

                    val appName = pickLocalized(meta.name)?.takeIf { it.isNotBlank() } ?: pkg

                    val matches =
                        pkg.contains(lower, true) ||
                                appName.contains(lower, true)

                    if (!matches) continue

                    // ✅ icon (prefer localized, then fallback)
                    val iconFile = pickIconFile(meta.icon)
                    val iconUrl = fdroidIconUrl(iconFile?.name)

                    // ✅ release-date source of truth: versions[*].added (pick latest by added)
                    val latestAddedPair = latestByAdded(entry.versions)
                    val latestKey = latestAddedPair?.first
                    val latestFromIndex = latestAddedPair?.second

                    // Optional: use /api/v1/packages/{id} to get the suggested versionName/versionCode
                    // (BUT note: this API does NOT give release dates, so added still comes from index-v2)
                    var chosenApiVersionCode: Long? = null
                    var versionName: String? = null
                    var versionCode: Int? = null

                    runCatching {
                        val app = fdroid.getApp(pkg)

                        val suggested = app.suggestedVersionCode
                        val chosen = app.packages.firstOrNull { it.versionCode != null && it.versionCode == suggested }
                            ?: app.packages
                                .filter { it.versionCode != null }
                                .maxByOrNull { it.versionCode!! }

                        chosenApiVersionCode = chosen?.versionCode
                        versionName = chosen?.versionName
                        versionCode = chosen?.versionCode?.toInt()
                    }.onFailure {
                        // fallback to index-v2 version info
                        versionName = latestFromIndex?.versionName
                        versionCode = latestFromIndex?.versionCode?.toInt()
                        Log.w("FDROID", "getApp($pkg) failed, fallback to index-v2")
                    }

                    // ✅ RELEASE DATE FOR F-DROID:
                    // try to match chosen versionCode -> index-v2 map key; else fallback to latest-by-added
                    val releaseDate =
                        chosenApiVersionCode
                            ?.toString()
                            ?.let { key -> entry.versions[key]?.added }
                            ?.let(::formatDate)
                            ?: latestFromIndex?.added?.let(::formatDate)

                    // If getApp chose a version but index-v2 key isn't versionCode-string, we still have
                    // a good date fallback via latestFromIndex.added (the most recent release).
                    fdroidResults += AppInfo(
                        source = "F-Droid",
                        packageName = pkg,
                        appName = appName,
                        versionName = versionName ?: latestFromIndex?.versionName,
                        versionCode = versionCode ?: latestFromIndex?.versionCode?.toInt(),
                        releaseDate = releaseDate, // ✅ IMPORTANT: from "added"
                        developer = meta.authorName,
                        downloadUrl = "https://f-droid.org/en/packages/$pkg/",
                        iconUrl = iconUrl
                    )

                    if (fdroidResults.size >= fetchFdroid) break
                }
            }.onFailure {
                Log.e("FDROID", "Search failed", it)
            }

            // ---------- APKMIRROR ----------
            runCatching {
                mirrorResults += ApkMirrorScraper.searchByNameLite(query).take(fetchMirror)
            }.onFailure {
                Log.e("APKMIRROR", "Search failed", it)
            }

            // ---------- DEDUP + EXACT PICK ----------
            val seen = HashSet<String>(limit * 4)
            val out = ArrayList<AppInfo>(limit)

            fun addIfNew(it: AppInfo) {
                val k = keyOf(it)
                if (seen.add(k)) out.add(it)
            }

            // Pick EXACTLY targetFdroid from F-Droid (dedup-safe)
            var fdCount = 0
            for (it in fdroidResults) {
                if (fdCount >= targetFdroid) break
                val before = out.size
                addIfNew(it)
                if (out.size > before) fdCount++
            }

            // Pick EXACTLY targetMirror from APKMirror (dedup-safe)
            var apCount = 0
            for (it in mirrorResults) {
                if (apCount >= targetMirror) break
                val before = out.size
                addIfNew(it)
                if (out.size > before) apCount++
            }

            // If one side didn’t have enough, fill remaining slots from the other side
            if (out.size < limit) {
                for (it in fdroidResults) {
                    if (out.size >= limit) break
                    addIfNew(it)
                }
            }
            if (out.size < limit) {
                for (it in mirrorResults) {
                    if (out.size >= limit) break
                    addIfNew(it)
                }
            }

            out.take(limit)
        }

    // ========================
    // LOAD DETAILS (heavy)
    // ========================
    override suspend fun loadDetails(item: AppInfo): AppInfo =
        withContext(Dispatchers.IO) {

            when (item.source) {

                "F-Droid" -> {
                    // already have relevant data (including releaseDate from index-v2 added)
                    item
                }

                "APKMirror" -> {
                    val url = item.downloadUrl ?: return@withContext item

                    val details = runCatching {
                        ApkMirrorScraper.loadDetailsSmart(url)
                    }.getOrNull()

                    if (details == null) return@withContext item

                    item.copy(
                        packageName = details.packageName.ifBlank { item.packageName },
                        versionName = details.versionName ?: item.versionName,
                        releaseDate = details.releaseDate ?: item.releaseDate,
                        developer = details.developer ?: item.developer,
                        downloadUrl = details.downloadUrl ?: item.downloadUrl,
                        minAndroid = details.minAndroid ?: item.minAndroid,
                        architecture = details.architecture ?: item.architecture
                    )
                }

                else -> item
            }
        }
}
