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

package com.merxury.blocker.ui.detail.component.info

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.merxury.blocker.R
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import com.merxury.blocker.databinding.ComponentDetailBottomSheetBinding
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.blocker.util.parcelable
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ComponentDetailBottomSheetFragment : BottomSheetDialogFragment() {
    private val viewModel: ComponentDetailViewModel by viewModels()
    private var _binding: ComponentDetailBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var component: ComponentData? = null
    private var isInEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component = arguments?.parcelable(ARG_COMPONENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ComponentDetailBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listenComponentInfoUpdate()
        listenLoadingUpdate()
        loadData()
        initUiComponent()
        showInfo()
    }

    private fun showInfo() {
        binding.name.editText?.setText(component?.name)
    }

    private fun initUiComponent() {
        binding.editAndSaveButton.setOnClickListener {
            if (isInEditMode) {
                saveData()
                quitEditMode()
            } else {
                enterEditMode()
            }
        }
        binding.exitButton.setOnClickListener {
            if (isInEditMode) {
                quitEditMode()
            } else {
                dismiss()
            }
        }
        binding.belongsToSdkCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showSdkName()
            } else {
                hideSdkName()
            }
        }
    }

    private fun loadData() {
        viewModel.getOnlineData(requireContext(), component!!)
    }

    private fun listenLoadingUpdate() {
        lifecycleScope.launchWhenStarted {
            viewModel.loading.collect {
                binding.loadingIndicator.isVisible = it
            }
        }
    }

    private fun saveData() {
        val sdkName = if (binding.belongsToSdkCheckbox.isChecked) {
            binding.sdkName.editText?.text.toString()
        } else {
            ""
        }
        val userComponent = NetworkComponentDetail(
            name = binding.name.editText?.text?.toString().orEmpty(),
            sdkName = sdkName,
            description = binding.description.editText?.text?.toString().orEmpty(),
            disableEffect = binding.disabledEffect.editText?.text?.toString().orEmpty(),
            recommendToBlock = binding.recommendCheckbox.isChecked
        )
        viewModel.saveUserRule(requireContext(), userComponent)
    }

    private fun listenComponentInfoUpdate() {
        lifecycleScope.launchWhenStarted {
            viewModel.onlineData.collect {
                if (it == null) return@collect
                if (!it.sdkName.isNullOrEmpty()) {
                    binding.icon.isVisible = true
                    binding.sdkName.isVisible = true
                    binding.sdkName.editText?.setText(it.sdkName.orEmpty())
                    binding.belongsToSdkCheckbox.isChecked = true
                }
                binding.name.editText?.setText(it.name.orEmpty())
                binding.description.editText?.setText(it.description.orEmpty())
                binding.disabledEffect.editText?.setText(it.disableEffect.orEmpty())
                binding.recommendCheckbox.isChecked = it.recommendToBlock
            }
        }
    }

    private fun enterEditMode() = with(binding) {
        isInEditMode = true
        sdkName.editText?.isEnabled = true
        description.editText?.isEnabled = true
        disabledEffect.editText?.isEnabled = true
        recommendCheckbox.isEnabled = true
        belongsToSdkCheckbox.isEnabled = true
        editAndSaveButton.text = getString(R.string.save)
        editAndSaveButton.setIconResource(R.drawable.ic_save)
        exitButton.setIconResource(R.drawable.ic_back)
    }

    private fun quitEditMode() = with(binding) {
        isInEditMode = false
        sdkName.editText?.isEnabled = false
        description.editText?.isEnabled = false
        disabledEffect.editText?.isEnabled = false
        editAndSaveButton.text = getString(R.string.edit)
        belongsToSdkCheckbox.isEnabled = false
        editAndSaveButton.setIconResource(R.drawable.ic_edit)
        recommendCheckbox.isEnabled = false
        exitButton.setIconResource(R.drawable.ic_close)
    }

    private fun hideSdkName() {
        binding.sdkName.isVisible = false
        binding.icon.isVisible = false
    }

    private fun showSdkName() {
        binding.sdkName.isVisible = true
        binding.icon.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_COMPONENT = "component"
        fun newInstance(data: ComponentData): ComponentDetailBottomSheetFragment {
            return ComponentDetailBottomSheetFragment().apply {
                arguments = bundleOf(ARG_COMPONENT to data)
            }
        }
    }
}
