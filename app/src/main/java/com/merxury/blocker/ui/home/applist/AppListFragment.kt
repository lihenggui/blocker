package com.merxury.blocker.ui.home.applist

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.AppListFragmentBinding
import com.merxury.blocker.ui.detail.AppDetailActivity
import com.merxury.blocker.util.AppStateCache
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
        setHasOptionsMenu(true)
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
        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.app_list_actions, menu)
        initSearch(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        initMenus(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        logger.i("Menu item clicked: $item")
        return when (item.itemId) {
            R.id.action_show_system_apps -> {
                handleShowSystemAppsClicked(item)
                true
            }
            R.id.action_show_service_info -> {
                handleShowServiceInfoClicked(item)
                true
            }
            else -> {
                handleSortAction(item)
                true
            }
        }
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
            this@AppListFragment.adapter.onItemClick = { app ->
                AppDetailActivity.start(requireContext(), app)
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
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
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
        val shouldShowSystemApp = PreferenceUtil.getShowSystemApps(requireContext())
        viewModel?.loadData(requireContext(), shouldShowSystemApp)
    }
}