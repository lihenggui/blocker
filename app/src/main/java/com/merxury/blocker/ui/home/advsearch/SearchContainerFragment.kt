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

package com.merxury.blocker.ui.home.advsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.elvishew.xlog.XLog
import com.google.android.material.tabs.TabLayoutMediator
import com.merxury.blocker.R
import com.merxury.blocker.databinding.SearchContainerFragmentBinding
import com.merxury.blocker.ui.home.advsearch.local.LocalSearchFragment
import com.merxury.blocker.util.reduceDragSensitivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchContainerFragment : Fragment(), ILocalSearchHost {
    private lateinit var binding: SearchContainerFragmentBinding
    private lateinit var adapter: SearchPagerAdapter
    private val logger = XLog.tag("SearchContainerFragment")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = SearchContainerFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SearchPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.reduceDragSensitivity()
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.local_search)
                1 -> getString(R.string.online_rules)
                else -> ""
            }
        }.attach()
    }

    override fun searchLocal(keyword: String) {
        logger.i("Search locally: $keyword")
        binding.viewPager.currentItem = 0
        (adapter.fragments.first() as? LocalSearchFragment)?.search(keyword)
    }
}
