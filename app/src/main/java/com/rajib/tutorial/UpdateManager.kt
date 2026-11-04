package com.rajib.tutorial

// ============================================================
//  Auto-Update System — Rajib Tutorial
//
//  কীভাবে কাজ করে:
//    1. App খুললে GitHub Releases API চেক করে
//    2. Latest release এর versionCode > installed versionCode হলে
//       update notification দেখায়
//    3. User "Update" বাটন চাপলে GitHub Release page খোলে
//       যেখান থেকে সঠিক split APK নামিয়ে install করতে পারবে
//
//  GitHub repo: raseledutools/Rajib-tutarial
//  API: https://api.github.com/repos/raseledutools/Rajib-tutarial/releases/latest
// ============================================================

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int,       // tag থেকে বের করা: v1.0.42 → 42
    val releaseUrl: String,     // GitHub Release page URL
    val releaseNotes: String,
    val apkAssets: List<ApkAsset>
)

data class ApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
)

object UpdateManager {

    private const val REPO        = "raseledutools/Rajib-tutarial"
    private const val API_URL     = "https://api.github.com/repos/$REPO/releases/latest"
    private const val PREFS_NAME  = "update_prefs"
    private const val KEY_SKIPPED = "skipped_version"

    // GitHub Release API থেকে latest release info আনো
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 8000
                readTimeout    = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            if (conn.responseCode != 200) return@withContext null

            val json    = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag     = json.optString("tag_name", "")          // "v1.0.42"
            val htmlUrl = json.optString("html_url", "")
            val body    = json.optString("body", "")

            // versionCode বের করো tag থেকে: v1.0.42 → 42
            val code = tag.substringAfterLast(".").toIntOrNull() ?: 0

            // APK assets list
            val assetsArr = json.optJSONArray("assets") ?: return@withContext null
            val assets = (0 until assetsArr.length())
                .map { assetsArr.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk") }
                .map { a -> ApkAsset(
                    name        = a.optString("name"),
                    downloadUrl = a.optString("browser_download_url"),
                    sizeBytes   = a.optLong("size", 0)
                )}

            if (tag.isEmpty() || assets.isEmpty()) return@withContext null

            ReleaseInfo(
                versionName  = tag,
                versionCode  = code,
                releaseUrl   = htmlUrl,
                releaseNotes = body.take(300),
                apkAssets    = assets
            )
        } catch (_: Exception) {
            null
        }
    }

    // Installed versionCode
    fun getInstalledVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info.longVersionCode.toInt()
            else
                @Suppress("DEPRECATION") info.versionCode
        } catch (_: PackageManager.NameNotFoundException) { 0 }
    }

    fun getInstalledVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }

    // Update আছে কিনা চেক করো
    fun hasUpdate(context: Context, release: ReleaseInfo): Boolean {
        val installed = getInstalledVersionCode(context)
        return release.versionCode > installed
    }

    // "এই version skip করো" preference
    fun skipVersion(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SKIPPED, versionCode).apply()
    }

    fun isVersionSkipped(context: Context, versionCode: Int): Boolean {
        val skipped = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SKIPPED, 0)
        return skipped >= versionCode
    }

    // GitHub Release page browser এ খোলো
    fun openReleasePage(context: Context, url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // Device ABI অনুযায়ী সঠিক APK suggest করো
    fun suggestApk(assets: List<ApkAsset>): ApkAsset? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return assets.firstOrNull()
        // arm64-v8a, armeabi-v7a, x86_64 match
        val abiMatch = assets.firstOrNull { it.name.contains(abi.replace("-", "_")) }
        // universal fallback
        return abiMatch
            ?: assets.firstOrNull { it.name.contains("universal") }
            ?: assets.firstOrNull()
    }

    // File size human-readable
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000     -> "%.0f KB".format(bytes / 1_000.0)
            else               -> "$bytes B"
        }
    }
}
