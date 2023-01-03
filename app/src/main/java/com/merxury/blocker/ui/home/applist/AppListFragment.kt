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

package com.merxury.blocker.ui.home.applist

import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.databinding.AppListFragmentBinding
import com.merxury.blocker.ui.detail.AppDetailActivity
import com.merxury.blocker.util.AppStateCache
import com.merxury.blocker.util.unsafeLazy

class AppListFragment : Fragment() {
    private var _binding: AppListFragmentBinding? = null
    private val binding get() = _binding!!
    private var viewModel: AppListViewModel? = null
    private val adapter by unsafeLazy { AppListAdapter(this@AppListFragment.lifecycleScope) }
    private val logger = XLog.tag("AppListFragment")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AppListViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AppListFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenu()
        initSwipeLayout()
        initRecyclerView()
        viewModel?.appList?.observe(viewLifecycleOwner) {
            hideLoading()
            if (it.isEmpty()) {
                binding.noAppText.visibility = View.VISIBLE
                binding.appListRecyclerView.visibility = View.GONE
            } else {
                binding.noAppText.visibility = View.GONE
                binding.appListRecyclerView.visibility = View.VISIBLE
                adapter.updateAppList(it)
            }
        }
        viewModel?.sortType?.observe(viewLifecycleOwner) {
            logger.i("SortType changed to ${it?.name}")
            setSortType(requireContext(), it)
        }
        lifecycleScope.launchWhenStarted {
            viewModel?.error?.collect {
                showErrorDialog(it)
            }
        }
        registerForContextMenu(binding.appListRecyclerView)
        loadData()
    }

    override fun onDestroy() {
        adapter.release()
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.clear()
                    menuInflater.inflate(R.menu.app_list_actions, menu)
                    initSearch(menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    super.onPrepareMenu(menu)
                    initMenus(menu)
                }

                override fun onMenuItemSelected(item: MenuItem): Boolean {
                    when (item.itemId) {
                        R.id.action_show_system_apps -> handleShowSystemAppsClicked(item)
                        R.id.action_show_service_info -> handleShowServiceInfoClicked(item)
                        else -> handleSortAction(item)
                    }
                    return true
                }
            },
            viewLifecycleOwner, Lifecycle.State.RESUMED
        )
    }

    private fun initRecyclerView() {
        binding.appListRecyclerView.apply {
            adapter = this@AppListFragment.adapter
            val manager = LinearLayoutManager(context)
            layoutManager = manager
            addItemDecoration(DividerItemDecoration(requireContext(), manager.orientation))
            this@AppListFragment.adapter.apply {
                onItemClick = { app ->
                    AppDetailActivity.start(requireContext(), app)
                }
                onClearCacheClicked = { app ->
                    viewModel?.clearCache(app)
                }
                onClearDataClicked = { app ->
                    viewModel?.clearData(app)
                }
                onForceStopClicked = { app ->
                    viewModel?.forceStop(app)
                }
                onUninstallClicked = { app ->
                    viewModel?.uninstallApp(app)
                }
                onEnableClicked = { app ->
                    viewModel?.enableApp(app)
                }
                onDisableClicked = { app ->
                    viewModel?.disableApp(app)
                }
            }
        }
    }

    private fun initSwipeLayout() {
        binding.appListRefreshLayout.setOnRefreshListener {
            loadData()
            AppStateCache.clear()
        }
    }

    private fun initMenus(menu: Menu) {
        menu.findItem(R.id.action_show_system_apps)?.isChecked =
            PreferenceUtil.getShowSystemApps(requireContext())
        menu.findItem(R.id.action_show_service_info)?.isChecked =
            PreferenceUtil.getShowServiceInfo(requireContext())
    }

    private fun initSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView ?: return
        val searchManager =
            requireActivity().getSystemService(Context.SEARCH_SERVICE) as? SearchManager ?: return
        searchView.setSearchableInfo(
            searchManager.getSearchableInfo(requireActivity().componentName)
        )
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                logger.i("onQueryTextSubmit: $query")
                viewModel?.filter(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel?.filter(newText)
                return false
            }
        })
    }

    private fun hideLoading() {
        if (binding.appListRefreshLayout.isRefreshing) {
            binding.appListRefreshLayout.isRefreshing = false
        }
    }

    private fun handleShowSystemAppsClicked(menuItem: MenuItem) {
        menuItem.isChecked = !menuItem.isChecked
        PreferenceUtil.setShowSystemApps(requireContext(), menuItem.isChecked)
        loadData()
    }

    private fun handleSortAction(menuItem: MenuItem) {
        logger.i("Sort action clicked: $menuItem")
        val sortType = getSortType(menuItem) ?: return
        logger.i("Sort type: $sortType")
        viewModel?.updateSorting(sortType)
    }

    private fun getSortType(menuItem: MenuItem): SortType? {
        return when (menuItem.itemId) {
            R.id.action_sort_name_asc -> SortType.NAME_ASC
            R.id.action_sort_name_desc -> SortType.NAME_DESC
            R.id.action_sort_install_time -> SortType.INSTALL_TIME
            R.id.action_sort_last_update_time -> SortType.LAST_UPDATE_TIME
            else -> null
        }
    }

    private fun handleShowServiceInfoClicked(menuItem: MenuItem) {
        menuItem.isChecked = !menuItem.isChecked
        PreferenceUtil.setShowServiceInfo(requireContext(), menuItem.isChecked)
        loadData()
    }

    private fun loadData() {
        binding.appListRefreshLayout.isRefreshing = true
        val shouldShowSystemApp = PreferenceUtil.getShowSystemApps(requireContext())
        viewModel?.loadData(requireContext(), shouldShowSystemApp)
    }

    private fun showErrorDialog(e: Exception) {
        AlertDialog.Builder(requireContext())
            .setTitle(resources.getString(R.string.operation_failed))
            .setMessage(getString(R.string.error_message_template, e.message))
            .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setSortType(context: Context, value: SortType?) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(
                context.getString(R.string.key_pref_sort_type),
                value?.name
            ).apply()
    }
}
