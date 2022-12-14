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

package com.merxury.blocker.ui.home.advsearch.local

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.elvishew.xlog.XLog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.merxury.blocker.R
import com.merxury.blocker.core.database.app.AppComponentEntity
import com.merxury.blocker.core.database.app.InstalledAppEntity
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.utils.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ExpandableSearchAdapter(private val lifecycleScope: LifecycleCoroutineScope) :
    BaseExpandableListAdapter() {
    private val logger = XLog.tag("ExpandableSearchAdapter")
    private var appList = listOf<InstalledAppEntity?>()
    private var data = mapOf<InstalledAppEntity?, List<AppComponentEntity>>()
    private var loadIconJob: Job? = null
    var onSwitchClick: ((AppComponentEntity, Boolean) -> Unit)? = null

    fun updateData(newData: Map<InstalledAppEntity?, List<AppComponentEntity>>) {
        appList = newData.keys.toList()
        data = newData
        notifyDataSetChanged()
    }

    fun release() {
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    override fun getGroup(groupPosition: Int): Any? {
        return appList[groupPosition]
    }

    override fun getGroupCount(): Int {
        return appList.size
    }

    override fun getGroupId(groupPosition: Int): Long {
        return appList[groupPosition]?.packageName.hashCode().toLong()
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        val app = appList.getOrNull(groupPosition)
        return data[app]?.getOrNull(childPosition)
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        val app = appList.getOrNull(groupPosition)
        val child = data[app]?.getOrNull(childPosition)
        return child?.componentName?.hashCode()?.toLong() ?: 0
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        val app = appList.getOrNull(groupPosition)
        val child = data[app]
        return child?.size ?: 0
    }

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val app = appList[groupPosition]
        val components = data[app] ?: listOf()
        val context = parent?.context ?: throw IllegalStateException("Context is null")
        val view = LayoutInflater.from(context)
            .inflate(R.layout.search_app_header, parent, false)
        view.findViewById<TextView>(R.id.app_name).text = app?.label
        view.findViewById<ImageView>(R.id.icon).apply {
            if (getTag(com.merxury.blocker.core.common.R.id.app_item_icon_id) != app?.packageName) {
                setTag(com.merxury.blocker.core.common.R.id.app_item_icon_id, app?.packageName)
                loadIcon(context, app, this)
            }
        }
        view.findViewById<TextView>(R.id.search_count).text = components.size.toString()
        return view
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val app = appList.getOrNull(groupPosition)
        val child = data[app]?.getOrNull(childPosition) ?: throw RuntimeException("Child is null")
        val context = parent?.context
        val view = LayoutInflater.from(context)
            .inflate(R.layout.search_app_component, parent, false)
        view.findViewById<TextView>(R.id.component_name).text = child.componentName
        val switch = view.findViewById<SwitchMaterial>(R.id.component_switch)
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = !(child.pmBlocked || child.ifwBlocked)
        switch.setOnCheckedChangeListener { _, isChecked ->
            onSwitchClick?.invoke(child, isChecked)
        }
        return view
    }

    private fun loadIcon(context: Context, app: InstalledAppEntity?, view: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val packageInfo =
                    context.packageManager.getPackageInfoCompat(app?.packageName ?: "", 0)
                val appInfo = packageInfo.applicationInfo ?: run {
                    logger.e("Application info is null, packageName: ${app?.packageName}")
                    return@launch
                }
                loadIconJob = AppIconCache.loadIconBitmapAsync(
                    context,
                    appInfo,
                    appInfo.uid / 100000,
                    view
                )
            } catch (e: Exception) {
                logger.e("Failed to load icon, packageName: ${app?.packageName}", e)
            }
        }
    }
}
