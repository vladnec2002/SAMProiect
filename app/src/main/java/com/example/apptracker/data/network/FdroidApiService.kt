package com.example.apptracker.data.network

import retrofit2.http.GET
import retrofit2.http.Path

// index-v2.json root
data class FdroidIndex(
    val packages: Map<String, FdroidPackageEntry>
)

data class FdroidPackageEntry(
    val metadata: FdroidMetadata? = null,
    val versions: Map<String, FdroidVersion> = emptyMap()
)

data class FdroidMetadata(
    val name: Map<String, String>? = null,
    val summary: Map<String, String>? = null,
    val authorName: String? = null,
    val license: String? = null,
    val sourceCode: String? = null,
    val icon: Map<String, FdroidFile>? = null
)

data class FdroidFile(
    val name: String,
    val sha256: String? = null,
    val size: Long? = null
)

data class FdroidVersion(
    val versionName: String? = null,
    val versionCode: Long? = null,
    val added: Long? = null
)

// /api/v1/packages/{id}
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

    @GET("index-v2.json")
    suspend fun getIndex(): FdroidIndex

    @GET("/api/v1/packages/{id}")
    suspend fun getApp(@Path("id") packageId: String): FdroidAppPackageResponse
}
