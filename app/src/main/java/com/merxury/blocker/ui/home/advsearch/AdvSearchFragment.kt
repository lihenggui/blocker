package com.merxury.blocker.ui.home.advsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.elvishew.xlog.XLog
import com.merxury.blocker.databinding.AdvSearchFragmentBinding

class AdvSearchFragment : Fragment() {
    private val logger = XLog.tag("AdvSearchFragment")
    private lateinit var binding: AdvSearchFragmentBinding
    private var viewModel: AdvSearchViewModel? = null
    private var totalCount = 0

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
        viewModel?.load(requireContext())
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
            logger.i("current: $it")
            if (totalCount > 0) {
                val progress = (it * 100 / totalCount)
                binding.progressBar.setProgressCompat(progress, false)
            }
        }
    }
}