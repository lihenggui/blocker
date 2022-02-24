package com.merxury.blocker.ui.detail.appinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.merxury.blocker.R
import com.merxury.blocker.databinding.AppInfoFragmentBinding
import com.merxury.blocker.util.AppIconCache
import com.merxury.libkit.entity.Application
import kotlinx.coroutines.Job

class AppInfoFragment : Fragment() {
    private var _binding: AppInfoFragmentBinding? = null
    private val binding get() = _binding!!
    private var loadIconJob: Job? = null
    private lateinit var app: Application

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AppInfoFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = arguments?.getParcelable("app")!!
        showHeader()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    private fun showHeader() {
        binding.appName.text = app.label
        binding.packageName.text = app.packageName
        binding.versionName.text = app.versionName
        val appInfo = app.packageInfo?.applicationInfo!!
        binding.appIcon.setTag(R.id.app_item_icon_id, app.packageName)
        loadIconJob = AppIconCache.loadIconBitmapAsync(
            requireContext(),
            appInfo,
            appInfo.uid / 100000,
            binding.appIcon
        )
    }

    companion object {
        fun newInstance(app: Application): AppInfoFragment {
            val fragment = AppInfoFragment()
            val bundle = Bundle()
            bundle.putParcelable("app", app)
            fragment.arguments = bundle
            return fragment
        }
    }
}