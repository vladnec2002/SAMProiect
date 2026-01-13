package com.example.apptracker.data.repository

import android.util.Log
import com.example.apptracker.data.model.AppInfo
import com.example.apptracker.data.network.ApkMirrorScraper
import com.example.apptracker.data.network.FdroidApiService
import com.example.apptracker.data.network.FdroidIndex
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
    // HELPERS
    // =========================
    private fun keyOf(item: AppInfo): String =
        "${item.source}:${if (item.packageName.isNotBlank()) item.packageName else (item.downloadUrl ?: item.appName)}"

    private fun formatDate(ts: Long): String {
        val millis = if (ts < 1_000_000_000_000L) ts * 1000 else ts
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(millis))
    }

    // ========================
    // SEARCH (lite)
    // ========================
    override suspend fun searchLite(term: String, limit: Int): List<AppInfo> =
        withContext(Dispatchers.IO) {

            val query = term.trim()
            if (query.isEmpty()) return@withContext emptyList()

            val lower = query.lowercase()
            val out = mutableListOf<AppInfo>()

            // ---------- F-DROID ----------
            runCatching {
                val index = getIndexCached()
                val results = mutableListOf<AppInfo>()

                for ((pkg, entry) in index.packages) {
                    val meta = entry.metadata ?: continue

                    val appName = meta.name
                        ?.let { m ->
                            m["en-US"]?.takeIf { it.isNotBlank() }
                                ?: m.values.firstOrNull { it.isNotBlank() }
                        }
                        ?: pkg

                    val matches =
                        pkg.contains(lower, true) ||
                                appName.contains(lower, true)

                    if (!matches) continue

                    // icon
                    val iconFile = meta.icon?.values?.firstOrNull()
                    val iconUrl = iconFile?.name?.let { "https://f-droid.org/repo/$it" }

                    // fallback (din index) pentru versiune + release date
                    val latestFromIndex = entry.versions.values
                        .filter { it.versionCode != null }
                        .maxByOrNull { it.versionCode!! }

                    // versiune din API per-app (mai corect): încercăm suggestedVersionCode, apoi max(versionCode)
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
                        // fallback la index dacă API eșuează
                        versionName = latestFromIndex?.versionName
                        versionCode = latestFromIndex?.versionCode?.toInt()
                        Log.w("FDROID", "getApp($pkg) failed, fallback to index")
                    }

                    // release date: preferăm versiunea aleasă de API (suggested), mapată în index-v2 via "added"
                    val releaseDate =
                        chosenApiVersionCode
                            ?.toString()
                            ?.let { key -> entry.versions[key]?.added }
                            ?.let(::formatDate)
                            ?: latestFromIndex?.added?.let(::formatDate)

                    results += AppInfo(
                        source = "F-Droid",
                        packageName = pkg,
                        appName = appName,
                        versionName = versionName,
                        versionCode = versionCode,
                        releaseDate = releaseDate,
                        developer = meta.authorName,
                        downloadUrl = "https://f-droid.org/en/packages/$pkg/",
                        iconUrl = iconUrl
                    )

                    if (results.size >= limit) break
                }

                out += results
            }.onFailure {
                Log.e("FDROID", "Search failed", it)
            }

            // ---------- APKMIRROR ----------
            runCatching {
                val mirror = ApkMirrorScraper.searchByNameLite(query)
                out += mirror.take(limit)
            }.onFailure {
                Log.e("APKMIRROR", "Search failed", it)
            }

            // dedup + limit
            out.distinctBy { keyOf(it) }.take(limit)
        }

    // ========================
    // LOAD DETAILS (heavy)
    // ========================
    override suspend fun loadDetails(item: AppInfo): AppInfo =
        withContext(Dispatchers.IO) {

            when (item.source) {

                "F-Droid" -> {
                    // deja avem toate datele relevante
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
