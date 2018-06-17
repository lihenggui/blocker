package com.merxury.blocker

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.merxury.blocker.core.shizuku.ShizukuClientWrapper

class BlockerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuClientWrapper.initialize(this)
        context = this
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }
}