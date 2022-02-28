package com.merxury.blocker.ui.detail.component

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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.ComponentFragmentBinding

class ComponentFragment : Fragment() {
    private lateinit var binding: ComponentFragmentBinding
    private lateinit var viewModel: ComponentViewModel
    private val adapter = ComponentAdapter()
    private var packageName: String = ""
    private var type: EComponentType = EComponentType.RECEIVER
    private val logger = XLog.tag("ComponentFragment")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = arguments?.getString(KEY_PACKAGE_NAME).orEmpty()
        type = arguments?.getSerializable(KEY_TYPE) as? EComponentType ?: EComponentType.RECEIVER
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ComponentFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            ComponentViewModel.ComponentViewModelFactory(requireContext().packageManager)
        )[ComponentViewModel::class.java]
        viewModel.load(requireContext(), packageName, type)
        initView()
        observeData()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.component_fragment_menu, menu)
        initSearch(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_block_all -> {
                viewModel.disableAll(requireContext(), packageName, type)
                true
            }
            R.id.action_enable_all -> {
                viewModel.enableAll(requireContext(), packageName, type)
                true
            }
            R.id.action_refresh -> {
                viewModel.load(requireContext(), packageName, type)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView ?: return
        val searchManager =
            requireActivity().getSystemService(Context.SEARCH_SERVICE) as? SearchManager ?: return
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                logger.i("onQueryTextSubmit: $query")
                viewModel.filter(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                logger.i("onQueryTextChange: $newText")
                viewModel.filter(newText)
                return false
            }
        })
    }

    private fun initView() {
        adapter.onSwitchClick = { componentData, checked ->
            viewModel.controlComponent(requireContext(), componentData, checked)
        }
        binding.recyclerView.apply {
            adapter = this@ComponentFragment.adapter
            val manager = LinearLayoutManager(context)
            layoutManager = manager
            addItemDecoration(DividerItemDecoration(requireContext(), manager.orientation))
        }
        binding.swipeLayout.setOnRefreshListener {
            viewModel.load(requireContext(), packageName, type)
        }
    }

    private fun observeData() {
        viewModel.data.observe(viewLifecycleOwner) {
            logger.i("Received component info: ${it.count()}, type = ${type.name}")
            if (binding.swipeLayout.isRefreshing) {
                binding.swipeLayout.isRefreshing = false
            }
            if (it.isEmpty()) {
                binding.noComponentHint.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.noComponentHint.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                adapter.submitList(it)
            }
        }
        viewModel.updatedItemData.observe(viewLifecycleOwner) {
            logger.i("Received updated component info: ${it}, type = ${type.name}")
            adapter.updateItem(it)
        }
        viewModel.error.observe(viewLifecycleOwner) {
            AlertDialog.Builder(requireContext())
                .setTitle(resources.getString(R.string.oops))
                .setMessage(getString(R.string.control_component_error_message, it.message))
                .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                .show()
        }
    }

    companion object {
        private const val KEY_PACKAGE_NAME = "package_name"
        private const val KEY_TYPE = "type"

        fun newInstance(packageName: String, type: EComponentType): Fragment {
            val fragment = ComponentFragment()
            val args = Bundle().apply {
                putString(KEY_PACKAGE_NAME, packageName)
                putSerializable(KEY_TYPE, type)
            }
            fragment.arguments = args
            return fragment
        }
    }
}