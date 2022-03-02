package com.merxury.blocker.ui.detail.appinfo

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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.data.AndroidCodeName
import com.merxury.blocker.databinding.AppInfoFragmentBinding
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.util.AppIconCache
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.libkit.entity.Application
import java.io.File
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppInfoFragment : Fragment() {
    private var _binding: AppInfoFragmentBinding? = null
    private val binding get() = _binding!!
    private var loadIconJob: Job? = null
    private lateinit var app: Application
    private val logger = XLog.tag("AppInfoFragment")

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
        initQuickActions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.app_info_actions, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_rule -> {
                importRule()
                true
            }
            R.id.action_export_rule -> {
                exportRule()
                true
            }
            R.id.action_import_ifw_rule -> {
                importIfwRule()
                true
            }
            R.id.action_export_ifw_rule -> {
                exportIfwRule()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun importRule() = lifecycleScope.launch {
        try {
            if (checkFolderReadyWithHint()) {
                val uri = RuleBackupHelper.import(requireContext(), app.packageName)
                if (uri == null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_fail_message),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_from_successfully, uri.path),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            logger.e("Can't import rule for ${app.packageName}", e)
            showErrorDialog(e)
        }
    }

    private fun exportRule() = lifecycleScope.launch {
        try {
            if (checkFolderReadyWithHint()) {
                val result = RuleBackupHelper.export(requireContext(), app.packageName)
                if (result) {
                    val folder = PreferenceUtil.getSavedRulePath(requireContext())
                    val fileName = app.packageName + Rule.EXTENSION
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.export_to_dest,
                            folder?.path + File.separator + fileName
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_fail_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            logger.e("Can't export rule for ${app.packageName}", e)
            showErrorDialog(e)
        }
    }

    private fun importIfwRule() = lifecycleScope.launch {
        try {
            if (checkFolderReadyWithHint()) {
                val uri = RuleBackupHelper.importIfwRule(requireContext(), app.packageName)
                if (uri == null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_fail_message),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_from_successfully, uri.path),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            logger.e("Can't import ifw rule for ${app.packageName}", e)
            showErrorDialog(e)
        }
    }

    private fun exportIfwRule() = lifecycleScope.launch {
        try {
            if (checkFolderReadyWithHint()) {
                val uri = RuleBackupHelper.exportIfwRule(requireContext(), app.packageName)
                if (uri == null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_fail_message),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_to_dest, uri),
                        Toast.LENGTH_LONG
                    ).show()
                }

            }
        } catch (e: Exception) {
            logger.e("Can't export ifw rule for ${app.packageName}", e)
            showErrorDialog(e)
        }
    }

    private fun checkFolderReadyWithHint(): Boolean {
        if (PreferenceUtil.getSavedRulePath(requireContext()) == null) {
            Toast.makeText(
                requireContext(),
                R.string.backup_folder_hasnt_been_set_yet,
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        return true
    }

    private fun showErrorDialog(e: Exception) {
        AlertDialog.Builder(requireContext())
            .setTitle(resources.getString(R.string.operation_failed))
            .setMessage(getString(R.string.control_component_error_message, e.message))
            .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
            .show()
    }

    private fun initQuickActions() {
        binding.actionLaunchApp.setOnClickListener {
            logger.i("Launch app ${app.packageName}")
            val intent = requireContext().packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent == null) {
                Toast.makeText(requireContext(), R.string.cannot_launch_this_app, Toast.LENGTH_LONG)
                    .show()
            } else {
                startActivity(intent)
            }
        }
        binding.actionExportRule.setOnClickListener { exportRule() }
        binding.actionImportRule.setOnClickListener { importRule() }
        binding.actionExportIfwRule.setOnClickListener { exportIfwRule() }
        binding.actionImportIfwRule.setOnClickListener { importIfwRule() }
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