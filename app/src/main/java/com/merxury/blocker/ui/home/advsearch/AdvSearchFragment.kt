package com.merxury.blocker.ui.home.advsearch

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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.AdvSearchFragmentBinding
import com.merxury.blocker.util.ToastUtil
import com.merxury.blocker.util.unsafeLazy

class AdvSearchFragment : Fragment() {
    private val logger = XLog.tag("AdvSearchFragment")
    private lateinit var binding: AdvSearchFragmentBinding
    private var viewModel: AdvSearchViewModel? = null
    private var totalCount = 0
    private val adapter by unsafeLazy { ExpandableSearchAdapter(this.lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AdvSearchViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AdvSearchFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListView()
        viewModel?.load(requireContext())
        viewModel?.isLoading?.observe(viewLifecycleOwner) {
            setSearchIconVisibility(!it)
            if (it) {
                binding.searchHintGroup.visibility = View.GONE
                binding.loadingIndicatorGroup.visibility = View.VISIBLE
                binding.list.visibility = View.GONE
            } else {
                binding.list.visibility = View.VISIBLE
                binding.loadingIndicatorGroup.visibility = View.GONE
                binding.searchHintGroup.visibility = View.VISIBLE
            }
        }
        viewModel?.currentProcessApplication?.observe(viewLifecycleOwner) {
            binding.processingName.text = it.packageName
        }
        viewModel?.total?.observe(viewLifecycleOwner) {
            if (it == 0) {
                // Do something that shows no apps was installed
                return@observe
            }
            if (it > 0) {
                logger.i("totalCount: $totalCount")
                totalCount = it
            }
        }
        viewModel?.current?.observe(viewLifecycleOwner) {
            if (totalCount > 0) {
                val progress = (it * 100 / totalCount)
                binding.progressBar.setProgressCompat(progress, false)
            }
        }
        viewModel?.filteredData?.observe(viewLifecycleOwner) {
            binding.searchHintGroup.visibility = View.GONE
            adapter.updateData(it)
        }
        viewModel?.error?.observe(viewLifecycleOwner) {
            val exception = it.getContentIfNotHandled()
            if (exception != null) {
                showErrorDialog(exception)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.adv_search_menu, menu)
        initSearch(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_block_all -> {
                viewModel?.doBatchOperation(false)
                return true
            }
            R.id.action_enable_all -> {
                viewModel?.doBatchOperation(true)
                return true
            }
            else -> false
        }
    }

    override fun onDestroyView() {
        adapter.release()
        super.onDestroyView()
    }

    private fun initListView() {
        adapter.onSwitchClick = { component, checked ->
            logger.i("onSwitchClick: $component, $checked")
            viewModel?.switchComponent(component.packageName, component.name, checked)
        }
        binding.list.apply {
            setAdapter(this@AdvSearchFragment.adapter)
        }
    }

    private fun initSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView ?: return
        val searchManager =
            requireActivity().getSystemService(Context.SEARCH_SERVICE) as? SearchManager ?: return
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                logger.i("onQueryTextChange: $newText")
                try {
                    viewModel?.filter(newText.orEmpty())
                } catch (e: Exception) {
                    logger.e("Invalid regex: $newText", e)
                    ToastUtil.showToast(R.string.invalid_regex, Toast.LENGTH_LONG)
                }
                return true
            }
        })
    }

    private fun showErrorDialog(e: Exception) {
        val context = context
        if (context == null) {
            ToastUtil.showToast(getString(R.string.control_component_error_message, e.message))
        } else {
            AlertDialog.Builder(context)
                .setTitle(resources.getString(R.string.operation_failed))
                .setMessage(getString(R.string.control_component_error_message, e.message))
                .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        }
    }

    private fun setSearchIconVisibility(enabled: Boolean) {
        setHasOptionsMenu(enabled)
        activity?.invalidateOptionsMenu()
    }
}