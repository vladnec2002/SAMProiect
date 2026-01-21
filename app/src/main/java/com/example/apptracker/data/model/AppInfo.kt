package com.example.apptracker.data.model

data class ApkMirrorVariant(
    val variantName: String? = null,     // ex: "1.461.750107"
    val badges: List<String> = emptyList(), // ex: ["BUNDLE"]
    val architecture: String? = null,    // ex: "arm64-v8a"
    val minAndroid: String? = null,      // ex: "Android 7.0+"
    val dpi: String? = null,             // ex: "nodpi"
    val variantPageUrl: String? = null   // ex: "...android-apk-download/"
)

data class AppInfo(
    val source: String,
    val packageName: String,
    val appName: String,
    val versionName: String? = null,
    val versionCode: Int? = null,

    // used for all sources
    val releaseDate: String? = null,

    val developer: String? = null,
    val downloadUrl: String? = null, // pt APKMirror: app page sau release page
    val iconUrl: String? = null,

    // APKMirror list
    val lastUpdated: String? = null,

    // APKMirror details (release page)
    val fileSize: String? = null,
    val downloads: String? = null,
    val uploadedUtc: String? = null, // dacă vrei data stabilă din data-utcdate
    val minAndroid: String? = null,
    val architecture: String? = null,
    val dpi: String? = null,
    val badges: List<String> = emptyList(),
    val variants: List<ApkMirrorVariant> = emptyList(),

    // F-Droid nice metadata
    val summary: String? = null,
    val license: String? = null,
    val sourceCodeUrl: String? = null,

    // (UI-ul tău deja referă astea în AppCard; le pun ca să nu crape)
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val categories: List<String>? = null,
    val antiFeatures: List<String>? = null,
    val permissions: List<String>? = null
)
