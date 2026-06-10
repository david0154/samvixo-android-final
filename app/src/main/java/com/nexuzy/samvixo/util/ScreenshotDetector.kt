package com.nexuzy.samvixo.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Detects screenshots by observing the MediaStore for new image files.
 * Required for "Secret Chat" notifications in the PID.
 */
class ScreenshotDetector(
    private val context: Context,
    private val onScreenshotDetected: () -> Unit
) {
    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (uri?.toString()?.contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) == true) {
                // Check if it's a screenshot by looking at the path/name
                // In a production app, we'd query the MediaStore for the latest row
                onScreenshotDetected()
            }
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(contentObserver)
    }
}
