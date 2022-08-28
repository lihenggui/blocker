package com.merxury.blocker.ui.detail.component

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.ComponentFragmentBinding
import com.merxury.blocker.ui.detail.component.info.ComponentDetailBottomSheetFragment
import com.merxury.blocker.util.BrowserUtil
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ShareUtil
import com.merxury.blocker.util.unsafeLazy
import com.merxury.libkit.entity.EComponentType
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class ComponentFragment : Fragment() {
    private lateinit var binding: ComponentFragmentBinding
    private val viewModel: ComponentViewModel by viewModels()
    private val adapter by unsafeLazy { ComponentAdapter(lifecycleScope) }
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
        initView()
        observeData()
        load()
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
                Toast.makeText(
                    requireContext(),
                    R.string.disabling_components_please_wait,
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.disableAll(requireContext(), packageName, type)
                true
            }
            R.id.action_enable_all -> {
                Toast.makeText(
                    requireContext(),
                    R.string.enabling_components_please_wait,
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.enableAll(requireContext(), packageName, type)
                true
            }
            R.id.action_refresh -> {
                load()
                true
            }
            R.id.action_show_enabled_components_first -> {
                PreferenceUtil.setShowEnabledComponentShowFirst(requireContext(), true)
                load()
                true
            }
            R.id.action_show_disabled_components_first -> {
                PreferenceUtil.setShowEnabledComponentShowFirst(requireContext(), false)
                load()
                true
            }
            R.id.open_repo -> {
                openRepository()
                true
            }
            R.id.share_rules -> {
                shareRules()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareRules() = viewModel.shareRule(requireContext())

    private fun openRepository() {
        BrowserUtil.openUrl(requireContext(), "https://github.com/lihenggui/blocker-general-rules")
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
        adapter.onLaunchClick = { componentData ->
            try {
                viewModel.launchActivity(componentData)
            } catch (e: Exception) {
                logger.e("Can't launch activity", e)
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.root_required)
                    .setMessage(R.string.cannot_launch_this_activity)
                    .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
        adapter.onCopyClick = { componentData ->
            val clipboardManager =
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = ClipData.newPlainText(
                requireContext().getString(R.string.component_name),
                componentData.name
            )
            clipboardManager?.setPrimaryClip(clipData)
            Toast.makeText(
                requireContext(),
                getString(R.string.copied_to_clipboard_template, componentData.name),
                Toast.LENGTH_SHORT
            ).show()
        }
        adapter.onDetailClick = { componentData ->
            val fragment = ComponentDetailBottomSheetFragment.newInstance(componentData)
            fragment.show(requireActivity().supportFragmentManager, "ComponentDetailDialogFragment")
        }
        binding.recyclerView.apply {
            adapter = this@ComponentFragment.adapter
            val manager = LinearLayoutManager(context)
            layoutManager = manager
            addItemDecoration(DividerItemDecoration(requireContext(), manager.orientation))
        }
        registerForContextMenu(binding.recyclerView)
        binding.swipeLayout.setOnRefreshListener {
            load()
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun load() {
        binding.swipeLayout.isRefreshing = true
        viewModel.load(requireContext(), packageName, type)
    }

    @SuppressLint("NotifyDataSetChanged")
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
                adapter.notifyDataSetChanged()
            }
        }
        viewModel.updatedItem.observe(viewLifecycleOwner) {
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
        lifecycleScope.launchWhenStarted {
            viewModel.zippedRules.collect {
                if (it == null) return@collect
                showShareFile(it)
            }
        }
    }

    private fun showShareFile(file: File) {
        ShareUtil.shareFileToEmail(requireContext(), file)
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