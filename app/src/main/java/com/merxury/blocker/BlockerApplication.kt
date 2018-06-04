package com.merxury.blocker

import android.app.Application
import com.merxury.blocker.core.shizuku.ShizukuClientWrapper

class BlockerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuClientWrapper.initialize(this)
    }
}