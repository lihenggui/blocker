/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.ui.home.advsearch.online

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.model.data.GeneralRule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GeneralRulesViewModel @Inject constructor() :
    ViewModel() {
    private val logger = XLog.tag("GeneralRulesViewModel")
    private val reloadTrigger = MutableLiveData<Boolean>()
    val rules = listOf<GeneralRule>()

    init {
        reloadTrigger.value = true
    }

    fun refresh() {
        logger.i("Refresh data")
        reloadTrigger.value = true
    }
}
