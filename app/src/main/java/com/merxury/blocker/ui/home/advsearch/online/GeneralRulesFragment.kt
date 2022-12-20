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

package com.merxury.blocker.ui.home.advsearch.online

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.GeneralRulesFragmentBinding
import com.merxury.blocker.ui.home.advsearch.ILocalSearchHost
import com.merxury.blocker.util.BrowserUtil
import com.merxury.blocker.util.unsafeLazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GeneralRulesFragment : Fragment() {
    private val logger = XLog.tag("GeneralRulesFragment")
    private val viewModel: GeneralRulesViewModel by viewModels()
    private lateinit var binding: GeneralRulesFragmentBinding
    private val adapter by unsafeLazy { GeneralRulesAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = GeneralRulesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        initMenu()
        binding.swipeLayout.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.generalRuleUiState.collect { uiState ->
                        when (uiState) {
                            is GeneralRuleUiState.Loading -> {
                                binding.swipeLayout.isRefreshing = true
                            }
                            is GeneralRuleUiState.Error -> {
                                showErrorDialog(uiState.message)
                                binding.swipeLayout.isRefreshing = false
                            }
                            is GeneralRuleUiState.Success -> {
                                binding.swipeLayout.isRefreshing = false
                                adapter.updateData(uiState.generalRules)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.online_search_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.action_submit_ideas -> {
                            BrowserUtil.openUrl(
                                requireContext(),
                                "https://github.com/lihenggui/blocker-general-rules/issues"
                            )
                        }
                    }
                    return true
                }
            },
            viewLifecycleOwner, Lifecycle.State.RESUMED
        )
    }

    private fun showErrorDialog(message: String?) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.oops))
            .setMessage(getString(R.string.error_occurred_with_message, message))
            .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    private fun initRecyclerView() {
        binding.recyclerView.adapter = adapter
        adapter.onSearchClickListener = { rule ->
            logger.d("rule is clicked: $rule")
            val keyword = rule.searchKeyword.joinToString()
            (parentFragment as? ILocalSearchHost)?.searchLocal(keyword)
        }
    }
}
