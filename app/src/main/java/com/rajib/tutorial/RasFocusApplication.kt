package com.rajib.tutorial

import android.app.Application

class RasFocusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // কোনো init দরকার নেই
        // Firebase auto-init হয় google-services.json দিয়ে
    }
}
