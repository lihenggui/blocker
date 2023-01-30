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

package com.merxury.blocker.feature.appdetail.summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class SummaryViewModel @Inject constructor(
    app: Application,
) : AndroidViewModel(app) {
    fun exportRule(packageName: String) {
        Timber.d("Export Blocker rule for $packageName")
    }

    fun importRule(packageName: String) {
        Timber.d("Import Blocker rule for $packageName")
    }

    fun exportIfwRule(packageName: String) {
        Timber.d("Export IFW rule for $packageName")
    }

    fun importIfwRule(packageName: String) {
        Timber.d("Import IFW rule for $packageName")
    }

    fun resetIfw(packageName: String) {
        Timber.d("Reset IFW rule for $packageName")
    }
}
