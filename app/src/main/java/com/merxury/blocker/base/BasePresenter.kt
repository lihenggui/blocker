package com.merxury.blocker.base

import android.content.Context

interface BasePresenter {
    fun start(context: Context)
    fun destroy()
}