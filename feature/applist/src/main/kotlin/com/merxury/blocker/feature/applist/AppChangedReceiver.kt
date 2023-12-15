/*
 * Copyright 2023 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.feature.applist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class AppChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.i("AppChangedReceiver onReceive")
        if (Intent.ACTION_PACKAGE_ADDED == intent?.action) {
            val data = intent.data
            val packageName = data?.schemeSpecificPart
            Timber.i("AppChangedReceiver onReceive ACTION_PACKAGE_ADDED $packageName")
        }
        if (Intent.ACTION_PACKAGE_REMOVED == intent?.action) {
            val data = intent.data
            val packageName = data?.schemeSpecificPart
            Timber.i("AppChangedReceiver onReceive ACTION_PACKAGE_REMOVED $packageName")
        }
    }
}
