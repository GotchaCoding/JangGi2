package com.example.janggi2

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

/**
 * Application class for JangGi2.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class JangGi2Application : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize OpenCV
        if (OpenCVLoader.initDebug()) {
            Log.d("JangGi2Application", "OpenCV loaded successfully")
        } else {
            Log.e("JangGi2Application", "OpenCV initialization failed")
        }
    }
}
