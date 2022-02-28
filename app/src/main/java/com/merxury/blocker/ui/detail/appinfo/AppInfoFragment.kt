package com.merxury.blocker.ui.detail.appinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.merxury.blocker.R
import com.merxury.blocker.data.AndroidCodeName
import com.merxury.blocker.databinding.AppInfoFragmentBinding
import com.merxury.blocker.util.AppIconCache
import com.merxury.libkit.entity.Application
import java.text.DateFormat
import java.util.*

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppInfoFragment : Fragment() {
    private var _binding: AppInfoFragmentBinding? = null
    private val binding get() = _binding!!
    private var loadIconJob: Job? = null
    private lateinit var app: Application

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

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
        showInfo()
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

    private fun showInfo() {
        lifecycleScope.launch {
            val targetSdkVersion = app.packageInfo?.applicationInfo?.targetSdkVersion ?: 0
            val targetSdkName = AndroidCodeName.getCodeName(targetSdkVersion)
            binding.itemTargetSdkVersion.setSummary(
                getString(
                    R.string.sdk_version_with_name_template,
                    targetSdkVersion,
                    targetSdkName
                )
            )
            val minSdkVersion = app.getMinSdkVersion()
            val minSdkName = AndroidCodeName.getCodeName(minSdkVersion)
            binding.itemMinSdkVersion.setSummary(
                getString(
                    R.string.sdk_version_with_name_template,
                    minSdkVersion,
                    minSdkName
                )
            )
            val lastUpdateTime = app.lastUpdateTime ?: Date(0)
            val formatter = DateFormat.getDateTimeInstance()
            binding.itemLastUpdateTime.setSummary(formatter.format(lastUpdateTime))
            val dataDir = app.packageInfo?.applicationInfo?.dataDir
            binding.itemDataDir.setSummary(dataDir)
        }
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