package com.example.apptracker.data.repository

import android.util.Log
import com.example.apptracker.data.model.AppInfo
import com.example.apptracker.data.network.ApkMirrorScraper
import com.example.apptracker.data.network.FdroidApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface AppRepository {
    suspend fun search(termOrPackage: String): List<AppInfo>
}

@Singleton
class AppRepositoryImpl @Inject constructor(
    private val fdroid: FdroidApiService
) : AppRepository {

    override suspend fun search(termOrPackage: String): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<AppInfo>()
            val query = termOrPackage.trim()

            if (query.isEmpty()) return@withContext emptyList()

            Log.d("SEARCH", "Search started for: $query")

            // 1️⃣ --- F-Droid search (index-v2 + API per-app pentru versiune) ---
            runCatching {
                Log.d("FDROID", "Loading index-v2.json...")
                val index = fdroid.getIndex()
                val lower = query.lowercase()

                val fdroidResults = mutableListOf<AppInfo>()

                // mergem cu for ca să putem apela suspend fdroid.getApp()
                for ((pkgName, pkgEntry) in index.packages.entries) {
                    val meta = pkgEntry.metadata ?: continue

                    // nume localizat: încearcă en-US, apoi orice non-gol
                    val appName = meta.name
                        ?.let { map ->
                            map["en-US"]
                                ?.takeIf { it.isNotBlank() }
                                ?: map.values.firstOrNull { it.isNotBlank() }
                        }
                        ?: pkgName

                    // filtru după nume sau package
                    val matches = pkgName.contains(lower, ignoreCase = true) ||
                            appName.contains(lower, ignoreCase = true)

                    if (!matches) continue

                    // icon: ia primul icon și îl transformă în URL complet
                    val iconFile = meta.icon
                        ?.values
                        ?.firstOrNull()
                    val iconUrl = iconFile?.name?.let { "https://f-droid.org/repo/$it" }

                    // ---------- versiune din index-v2 (dacă există) ----------
                    var versionName: String? = null
                    var versionCode: Int? = null
                    var releaseDate: String? = null

                    val latestFromIndex = pkgEntry.versions
                        .values
                        .filter { it.versionCode != null }
                        .maxByOrNull { it.versionCode!! }

                    if (latestFromIndex != null) {
                        versionName = latestFromIndex.versionName
                        versionCode = latestFromIndex.versionCode?.toInt()

                        // "added" poate fi secunde sau milisecunde
                        releaseDate = latestFromIndex.added?.let { ts ->
                            val millis = if (ts < 1_000_000_000_000L) ts * 1000 else ts
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            fmt.format(Date(millis))
                        }
                    }

                    // ---------- fallback: API oficial /api/v1/packages/{id} ----------
                    if (versionName == null && versionCode == null) {
                        runCatching {
                            val appInfo = fdroid.getApp(pkgName)
                            val latestApi = appInfo.packages
                                .filter { it.versionCode != null }
                                .maxByOrNull { it.versionCode!! }

                            versionName = latestApi?.versionName
                            versionCode = latestApi?.versionCode?.toInt()
                        }.onFailure {
                            Log.e("FDROID", "getApp($pkgName) failed: ${it.message}", it)
                        }
                    }

                    val info = AppInfo(
                        source = "F-Droid",
                        packageName = pkgName,
                        appName = appName,
                        versionName = versionName,
                        versionCode = versionCode,
                        releaseDate = releaseDate,
                        developer = meta.authorName,
                        downloadUrl = "https://f-droid.org/en/packages/$pkgName/",
                        iconUrl = iconUrl
                    )

                    fdroidResults += info
                    if (fdroidResults.size >= 10) break
                }

                out += fdroidResults
                Log.d(
                    "FDROID",
                    "F-Droid results added: ${fdroidResults.size}, total now: ${out.size}"
                )
            }.onFailure {
                Log.e("FDROID", "index-v2.json failed: ${it.message}", it)
            }

            // 2️⃣ --- APKMirror scraper (clean results) ---
            runCatching {
                Log.d("APKMIRROR", "Searching APKMirror for: $query")
                val mirrorResults = ApkMirrorScraper.searchByName(query)
                out += mirrorResults
                Log.d("APKMIRROR", "APKMirror results: ${mirrorResults.size}")
            }.onFailure {
                Log.e("APKMIRROR", "Error scraping: ${it.message}", it)
            }

            // 3️⃣ --- Distinct & log ---
            val finalResults = out.distinctBy {
                // deduplicare după sursă + packageName (sau appName dacă package e gol)
                "${it.source}:${if (it.packageName.isNotBlank()) it.packageName else it.appName.lowercase()}"
            }

            Log.d("SEARCH", "Final result count: ${finalResults.size}")
            finalResults.forEach {
                Log.d("SEARCH", "Result: [${it.source}] ${it.appName} (${it.packageName}) v=${it.versionName}/${it.versionCode} rel=${it.releaseDate}")
            }

            finalResults
        }
}