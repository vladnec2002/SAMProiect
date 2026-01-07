package com.example.apptracker.data.model

data class AppInfo(
    val source: String,
    val packageName: String,
    val appName: String,
    val versionName: String? = null,
    val versionCode: Int? = null,
    val releaseDate: String? = null,
    val developer: String? = null,
    val downloadUrl: String? = null,
    val iconUrl: String? = null
)
