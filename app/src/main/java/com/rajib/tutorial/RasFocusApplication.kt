package com.rajib.tutorial

import android.app.Application
import com.cloudinary.android.MediaManager

class RasFocusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cloudinary init — video upload এর জন্য
        try {
            val config = mapOf(
                "cloud_name" to "your_cloud_name",   // TODO: replace
                "api_key"    to "your_api_key",       // TODO: replace
                "api_secret" to "your_api_secret"     // TODO: replace
            )
            MediaManager.init(this, config)
        } catch (_: Exception) { }
    }
}
