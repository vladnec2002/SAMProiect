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
    val iconUrl: String? = null,

    // 🔽 Nou: info extra, folosite în special pentru APKMirror
    val description: String? = null,          // textul "About ..." + descriere aplicație
    val fileSize: String? = null,             // ex: "23.46 MB"
    val minAndroidVersion: String? = null,    // ex: "Android 5.0+"
    val downloads: String? = null             // ex: "11"
)
