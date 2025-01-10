/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.ui.detail.component

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.databinding.ComponentFragmentBinding
import com.merxury.blocker.ui.detail.component.info.ComponentDetailBottomSheetFragment
import com.merxury.blocker.util.BrowserUtil
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ShareUtil
import com.merxury.blocker.util.serializable
import com.merxury.blocker.util.unsafeLazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class ComponentFragment : Fragment() {
    private lateinit var binding: ComponentFragmentBinding
    private val viewModel: ComponentViewModel by viewModels()
    private val adapter by unsafeLazy { ComponentAdapter(lifecycleScope) }
    private var packageName: String = ""
    private var type: ComponentType = ComponentType.RECEIVER
    private val logger = XLog.tag("ComponentFragment")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageName = arguments?.getString(KEY_PACKAGE_NAME).orEmpty()
        type = arguments?.serializable(KEY_TYPE) as? ComponentType ?: ComponentType.RECEIVER
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ComponentFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initMenu()
        observeData()
        load()
    }

    private fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.component_fragment_menu, menu)
                    initSearch(menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_block_all -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.disabling_components_please_wait,
                                Toast.LENGTH_SHORT,
                            ).show()
                            viewModel.disableAll(requireContext(), packageName, type)
                            true
                        }

                        R.id.action_enable_all -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.enabling_components_please_wait,
                                Toast.LENGTH_SHORT,
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

                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            State.RESUMED,
        )
    }

    private fun shareRules() = viewModel.shareRule(requireContext())

    private fun openRepository() {
        BrowserUtil.openUrl(
            requireContext(),
            "https://github.com/lihenggui/blocker-general-rules",
        )
    }

    private fun initSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView ?: return
        val searchManager =
            requireActivity().getSystemService(Context.SEARCH_SERVICE) as? SearchManager ?: return
        searchView.setSearchableInfo(
            searchManager.getSearchableInfo(requireActivity().componentName),
        )
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
                componentData.name,
            )
            clipboardManager?.setPrimaryClip(clipData)
            Toast.makeText(
                requireContext(),
                getString(R.string.copied_to_clipboard_template, componentData.name),
                Toast.LENGTH_SHORT,
            ).show()
        }
        adapter.onDetailClick = { componentData ->
            val fragment = ComponentDetailBottomSheetFragment.newInstance(componentData)
            fragment.show(
                requireActivity().supportFragmentManager,
                "ComponentDetailDialogFragment",
            )
        }
        adapter.onComponentBind = { fullName ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(State.STARTED) {
                    viewModel.loadComponentDetail(fullName)
                        .collect {
                            adapter.updateItemDetail(it)
                        }
                }
            }
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
            logger.i("Received updated component info: $it, type = ${type.name}")
            adapter.updateItem(it)
        }
        viewModel.error.observe(viewLifecycleOwner) {
            AlertDialog.Builder(requireContext())
                .setTitle(resources.getString(R.string.oops))
                .setMessage(getString(R.string.control_component_error_message, it.message))
                .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                }
                .show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(State.STARTED) {
                viewModel.zippedRules.collect {
                    if (it == null) return@collect
                    showShareFile(it)
                }
            }
        }
    }

    private fun showShareFile(file: File) {
        ShareUtil.shareFileToEmail(requireContext(), file)
    }

    companion object {
        private const val KEY_PACKAGE_NAME = "package_name"
        private const val KEY_TYPE = "type"

        fun newInstance(packageName: String, type: ComponentType): Fragment {
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
