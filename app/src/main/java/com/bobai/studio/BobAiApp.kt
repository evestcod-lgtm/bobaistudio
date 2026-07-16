package com.bobai.studio

import android.app.Application

class BobAiApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // JavaCPP (used to extract and run the FFmpeg binary — see
        // WatermarkRemover/FragmentReplacer) defaults to a cache directory
        // that can end up mounted without exec permission on Android 10+,
        // causing "Permission denied" the first time the ffmpeg binary is
        // run. Pointing it at filesDir avoids that known issue.
        System.setProperty("org.bytedeco.javacpp.cachedir", filesDir.absolutePath)
    }
}
