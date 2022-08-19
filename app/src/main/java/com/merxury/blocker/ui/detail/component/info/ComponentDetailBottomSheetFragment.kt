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
import com.merxury.blocker.databinding.ComponentDetailBottomSheetBinding
import com.merxury.blocker.ui.detail.component.ComponentData
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
        component = arguments?.getParcelable(ARG_COMPONENT)
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
        listenControllerStateUpdate()
        listenLoadingUpdate()
        loadData()
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
    }

    private fun loadData() {
        val packageName = component?.packageName.orEmpty()
        val componentName = component?.name.orEmpty()
        viewModel.getOnlineData(requireContext(), component!!)
        viewModel.loadIfwState(requireContext(), packageName, componentName)
        viewModel.loadPmState(requireContext(), packageName, componentName)
    }

    private fun listenLoadingUpdate() {
        lifecycleScope.launchWhenStarted {
            viewModel.loading.collect {
                binding.loadingIndicator.isVisible = it
            }
        }
    }

    private fun saveData() {

    }

    private fun listenComponentInfoUpdate() {
        lifecycleScope.launchWhenStarted {
            viewModel.onlineData.collect {
                binding.sdkName.editText?.setText(it?.sdkName.orEmpty())
                binding.name.editText?.setText(it?.name.orEmpty())
                binding.description.editText?.setText(it?.description.orEmpty())
                binding.disabledEffect.editText?.setText(it?.disableEffect.orEmpty())
            }
        }
    }

    private fun listenControllerStateUpdate() {
        lifecycleScope.launchWhenStarted {
            viewModel.ifwState.collect {
                binding.ifwSwitch.isChecked = it
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.pmState.collect {
                binding.pmSwitch.isChecked = it
            }
        }
    }

    private fun enterEditMode() = with(binding) {
        isInEditMode = true
        sdkName.editText?.isEnabled = true
        name.editText?.isEnabled = true
        description.editText?.isEnabled = true
        disabledEffect.editText?.isEnabled = true
        editAndSaveButton.text = getString(R.string.save)
        editAndSaveButton.setIconResource(R.drawable.ic_save)
        exitButton.setIconResource(R.drawable.ic_back)
    }

    private fun quitEditMode() = with(binding) {
        isInEditMode = false
        sdkName.editText?.isEnabled = false
        name.editText?.isEnabled = false
        description.editText?.isEnabled = false
        disabledEffect.editText?.isEnabled = false
        editAndSaveButton.text = getString(R.string.edit)
        editAndSaveButton.setIconResource(R.drawable.ic_edit)
        exitButton.setIconResource(R.drawable.ic_close)
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