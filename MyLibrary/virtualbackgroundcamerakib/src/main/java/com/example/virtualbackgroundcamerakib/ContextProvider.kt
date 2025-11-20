package com.example.virtualbackgroundcamerakib

import android.content.Context

object ContextProvider {
    private lateinit var context: Context
    lateinit var contextActivity: CameraActivity

    @JvmStatic
    fun init(context: Context) {
        this.context = context.applicationContext
        contextActivity = context as CameraActivity
    }

    @JvmStatic
    fun get(): Context {
        return context
    }
}
