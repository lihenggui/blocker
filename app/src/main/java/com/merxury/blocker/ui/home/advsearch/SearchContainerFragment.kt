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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchContainerFragment : Fragment(), ILocalSearchHost {
    private lateinit var binding: SearchContainerFragmentBinding
    private lateinit var adapter: SearchPagerAdapter
    private val logger = XLog.tag("SearchContainerFragment")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = SearchContainerFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SearchPagerAdapter(this)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.local)
                1 -> getString(R.string.online)
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