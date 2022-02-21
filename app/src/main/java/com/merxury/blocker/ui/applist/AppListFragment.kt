package com.merxury.blocker.ui.applist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.databinding.AppListFragmentBinding
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
        initSwipeLayout()
        initRecyclerView()
        viewModel?.appList?.observe(viewLifecycleOwner) {
            hideLoading()
            if (it.isEmpty()) {
                showNoAppView()
            } else {
                showAppView()
                adapter.updateAppList(it)
            }
        }
        viewModel?.loadData(requireContext(), true)
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
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun initSwipeLayout() {
        binding.appListRefreshLayout.setOnRefreshListener {
            viewModel?.loadData(requireContext(), true)
        }
    }

    private fun showNoAppView() {
        binding.noAppsContainer.visibility = View.VISIBLE
    }

    private fun showAppView() {
        binding.noAppsContainer.visibility = View.GONE
    }

    private fun hideLoading() {
        if (binding.appListRefreshLayout.isRefreshing) {
            binding.appListRefreshLayout.isRefreshing = false
        }
    }
}