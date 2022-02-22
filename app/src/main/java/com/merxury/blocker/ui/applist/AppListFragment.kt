package com.merxury.blocker.ui.applist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.AppListFragmentBinding
import com.merxury.blocker.util.PreferenceUtil
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
        initToolbar()
        initSwipeLayout()
        initRecyclerView()
        viewModel?.appList?.observe(viewLifecycleOwner) {
            hideLoading()
            if (it.isEmpty()) {
                binding.noAppsContainer.visibility = View.VISIBLE
                binding.appListRecyclerView.visibility = View.GONE
            } else {
                binding.noAppsContainer.visibility = View.GONE
                binding.appListRecyclerView.visibility = View.VISIBLE
                adapter.updateAppList(it)
            }
        }
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

    private fun initRecyclerView() {
        binding.appListRecyclerView.apply {
            adapter = this@AppListFragment.adapter
            val manager = LinearLayoutManager(context)
            layoutManager = manager
            addItemDecoration(DividerItemDecoration(requireContext(), manager.orientation))
        }
    }

    private fun initSwipeLayout() {
        binding.appListRefreshLayout.setOnRefreshListener {
            loadData()
        }
    }

    private fun initToolbar() {
        val navController = findNavController()
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        binding.toolbar.menu.findItem(R.id.action_show_system_apps).isChecked =
            PreferenceUtil.getShowSystemApps(requireContext())
        binding.toolbar.menu.findItem(R.id.action_show_service_info).isChecked =
            PreferenceUtil.getShowServiceInfo(requireContext())
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            logger.i("Menu item clicked: $menuItem")
            when (menuItem?.itemId) {
                R.id.action_show_system_apps -> {
                    handleShowSystemAppsClicked(menuItem)
                    true
                }
                R.id.action_show_service_info -> {
                    handleShowServiceInfoClicked(menuItem)
                    true
                }
                else -> {
                    handleSortAction(menuItem)
                    true
                }
            }
        }
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
        val shouldShowSystemApp = PreferenceUtil.getShowSystemApps(requireContext())
        viewModel?.loadData(requireContext(), shouldShowSystemApp)
    }
}