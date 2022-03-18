package com.merxury.blocker.ui.home

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.merxury.blocker.ui.home.advsearch.SearchContainerFragment
import com.merxury.blocker.ui.home.applist.AppListFragment
import com.merxury.blocker.ui.home.settings.SettingsFragment

class HomeAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    private val list = listOf(
        AppListFragment(),
        SearchContainerFragment(),
        SettingsFragment()
    )

    override fun getItemCount(): Int {
        return list.size
    }

    override fun createFragment(position: Int): Fragment {
        return list[position]
    }
}