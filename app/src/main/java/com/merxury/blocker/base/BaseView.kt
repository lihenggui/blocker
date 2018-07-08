package com.merxury.blocker.base

import android.app.Activity

interface BaseView<T> {
    var presenter: T
    fun getViewActivity(): Activity
}