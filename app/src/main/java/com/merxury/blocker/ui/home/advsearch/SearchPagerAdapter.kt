package com.merxury.blocker.ui.home.advsearch

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.merxury.blocker.ui.home.advsearch.local.LocalSearchFragment
import com.merxury.blocker.ui.home.advsearch.online.GeneralRulesFragment

class SearchPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    val fragments = listOf(LocalSearchFragment(), GeneralRulesFragment())

    override fun getItemCount(): Int {
        return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
}
