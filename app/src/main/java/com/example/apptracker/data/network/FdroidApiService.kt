package com.example.apptracker.data.network

import retrofit2.http.GET
import retrofit2.http.Path

// index-v2.json root
data class FdroidIndex(
    val packages: Map<String, FdroidPackageEntry>
)

// corespunde lui PackageV2
data class FdroidPackageEntry(
    val metadata: FdroidMetadata? = null,
    val versions: Map<String, FdroidVersion> = emptyMap()
)

// corespunde lui MetadataV2
data class FdroidMetadata(
    // name/summary sunt localizate: { "en-US": "Name", "de-DE": "Name DE", ... }
    val name: Map<String, String>? = null,
    val summary: Map<String, String>? = null,
    val authorName: String? = null,
    val license: String? = null,
    val sourceCode: String? = null,
    // icon este Map<locale, FileV2>
    val icon: Map<String, FdroidFile>? = null
)

// corespunde lui FileV2
data class FdroidFile(
    val name: String,
    val sha256: String? = null,
    val size: Long? = null
)

// corespunde lui PackageVersionV2 (folosim doar ce ne trebuie)
data class FdroidVersion(
    val versionName: String? = null,
    val versionCode: Long? = null,
    val added: Long? = null      // timestamp (sec sau ms)
)

/**
 * Răspunsul de la API-ul per-app:
 * GET https://f-droid.org/api/v1/packages/{id}
 *
 * {
 *   "packageName": "org.fdroid.fdroid",
 *   "suggestedVersionCode": 1009000,
 *   "packages": [
 *     { "versionName": "1.10-alpha0", "versionCode": 1010000 },
 *     { "versionName": "1.9", "versionCode": 1009000 }
 *   ]
 * }
 */
data class FdroidAppPackageVersion(
    val versionName: String? = null,
    val versionCode: Long? = null
)

data class FdroidAppPackageResponse(
    val packageName: String,
    val suggestedVersionCode: Long? = null,
    val packages: List<FdroidAppPackageVersion> = emptyList()
)

interface FdroidApiService {

    // https://f-droid.org/repo/index-v2.json (baseUrl include deja /repo/)
    @GET("index-v2.json")
    suspend fun getIndex(): FdroidIndex

    // https://f-droid.org/api/v1/packages/{id}
    // observă "/" la început -> Retrofit ignoră /repo/ din baseUrl
    @GET("/api/v1/packages/{id}")
    suspend fun getApp(@Path("id") packageId: String): FdroidAppPackageResponse
}
