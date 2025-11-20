package com.example.mylibrary

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CameraActivity : ComponentActivity() {
    @OptIn(ExperimentalGetImage::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        ContextProvider.init(this)
        setContent {
            VirtualBackgroundApp(this)
        }
    }

    fun finishWithResult(videoUri: Uri) {
        val resultIntent = Intent().apply {
            data = videoUri
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    fun finishWithCancel(reason: String) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra("reason", reason))
        finish()
    }
}


