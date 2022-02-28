package com.merxury.blocker.ui.detail.component

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elvishew.xlog.XLog
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

    private fun initView() {
        adapter.onItemClick = { componentData, checked ->
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
            logger.e("Received error: $it")
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