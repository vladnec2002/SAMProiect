package com.example.apptracker.data.model

data class AppInfo(
    val source: String,
    val packageName: String,
    val appName: String,
    val versionName: String? = null,
    val versionCode: Int? = null,

    // folosit pe toate sursele (poate rămâne null)
    val releaseDate: String? = null,

    val developer: String? = null,
    val downloadUrl: String? = null,
    val iconUrl: String? = null,

    // ✅ NOU: din APKMirror search list
    val lastUpdated: String? = null,

    // ✅ NOU: din APKMirror details (version page)
    val minAndroid: String? = null,
    val architecture: String? = null
)
