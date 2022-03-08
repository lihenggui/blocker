package com.merxury.blocker.ui.home.advsearch

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.AdvSearchFragmentBinding

class AdvSearchFragment : Fragment() {
    private val logger = XLog.tag("AdvSearchFragment")
    private lateinit var binding: AdvSearchFragmentBinding
    private var viewModel: AdvSearchViewModel? = null
    private var totalCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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
            if (totalCount > 0) {
                val progress = (it * 100 / totalCount)
                binding.progressBar.setProgressCompat(progress, false)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.adv_search_menu, menu)
        initSearch(menu)
    }

    private fun initSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView ?: return
        val searchManager =
            requireActivity().getSystemService(Context.SEARCH_SERVICE) as? SearchManager ?: return
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                logger.i("onQueryTextSubmit: $query")
                viewModel?.filter(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Ignore event
                return false
            }
        })
    }
}