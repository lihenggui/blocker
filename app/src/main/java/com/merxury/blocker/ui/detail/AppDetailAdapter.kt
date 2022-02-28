package com.merxury.blocker.ui.detail

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.merxury.blocker.ui.detail.appinfo.AppInfoFragment
import com.merxury.blocker.ui.detail.component.ComponentFragment
import com.merxury.blocker.ui.detail.component.EComponentType
import com.merxury.libkit.entity.Application

class AppDetailAdapter(activity: FragmentActivity, app: Application) :
    FragmentStateAdapter(activity) {
    private val list = listOf(
        AppInfoFragment.newInstance(app),
        ComponentFragment.newInstance(app.packageName, EComponentType.SERVICE),
        ComponentFragment.newInstance(app.packageName, EComponentType.RECEIVER),
        ComponentFragment.newInstance(app.packageName, EComponentType.ACTIVITY),
        ComponentFragment.newInstance(app.packageName, EComponentType.PROVIDER),
    )

    override fun getItemCount(): Int {
        return list.size
    }

    override fun createFragment(position: Int): Fragment {
        return list[position]
    }
}