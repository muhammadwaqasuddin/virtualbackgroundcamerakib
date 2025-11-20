package com.example.virtualbackgroundcamerakib

// MyApplication.kt
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
// Initialize in Application class:
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
//        ContextProvider.init(this)
    }
}
