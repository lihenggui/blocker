/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.ui.detail

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.merxury.blocker.R
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.ui.detail.appinfo.AppInfoFragment
import com.merxury.blocker.ui.detail.component.ComponentFragment

class AppDetailAdapter(activity: FragmentActivity, app: Application) :
    FragmentStateAdapter(activity) {
    private val list = listOf(
        AppInfoFragment.newInstance(app),
        ComponentFragment.newInstance(app.packageName, ComponentType.SERVICE),
        ComponentFragment.newInstance(app.packageName, ComponentType.RECEIVER),
        ComponentFragment.newInstance(app.packageName, ComponentType.ACTIVITY),
        ComponentFragment.newInstance(app.packageName, ComponentType.PROVIDER),
    )

    override fun getItemCount(): Int {
        return list.size
    }

    override fun createFragment(position: Int): Fragment {
        return list[position]
    }

    companion object {
        val titles = listOf(
            R.string.app_info,
            R.string.service,
            R.string.receiver,
            R.string.activity,
            R.string.content_provider,
        )
    }
}
