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
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.BlockerApplication
import com.merxury.blocker.R
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.utils.AndroidCodeName
import com.merxury.blocker.databinding.AppInfoFragmentBinding
import com.merxury.blocker.util.AppIconCache
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.ToastUtil
import com.merxury.blocker.util.parcelable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class AppInfoFragment : Fragment() {
    private var _binding: AppInfoFragmentBinding? = null
    private val binding get() = _binding!!
    private var loadIconJob: Job? = null
    private lateinit var app: Application
    private val logger = XLog.tag("AppInfoFragment")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = AppInfoFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        app = arguments?.parcelable("app")!!
        showHeader()
        showInfo()
        initMenu()
        initQuickActions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    private fun initMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.app_info_actions, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    when (menuItem.itemId) {
                        R.id.action_import_rule -> importRule()
                        R.id.action_export_rule -> exportRule()
                        R.id.action_import_ifw_rule -> importIfwRule()
                        R.id.action_export_ifw_rule -> exportIfwRule()
                        else -> return false
                    }
                    return true
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun importRule() = lifecycleScope.launch {
        try {
            if (checkFolderReadyWithHint()) {
                val uri = RuleBackupHelper.import(requireContext(), app.packageName)
                if (uri == null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_fail_message),
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_from_successfully, uri.path),
                        Toast.LENGTH_LONG,
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
                    val fileName = app.packageName + com.merxury.blocker.core.rule.Rule.EXTENSION
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.export_to_dest,
                            folder?.path + File.separator + fileName,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_fail_message),
                        Toast.LENGTH_LONG,
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
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_from_successfully, uri.path),
                        Toast.LENGTH_LONG,
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
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.export_to_dest, uri),
                        Toast.LENGTH_LONG,
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
                Toast.LENGTH_LONG,
            ).show()
            return false
        }
        return true
    }

    private fun showErrorDialog(e: Exception) {
        val context = context
        if (context == null) {
            ToastUtil.showToast(
                BlockerApplication.context.getString(
                    R.string.control_component_error_message,
                    e.message,
                ),
            )
        } else {
            AlertDialog.Builder(context)
                .setTitle(resources.getString(R.string.operation_failed))
                .setMessage(getString(R.string.control_component_error_message, e.message))
                .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                }
                .show()
        }
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
        binding.appIcon.setTag(
            R.id.app_item_icon_id,
            app.packageName,
        )
        loadIconJob = AppIconCache.loadIconBitmapAsync(
            requireContext(),
            appInfo,
            appInfo.uid / 100000,
            binding.appIcon,
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
                    targetSdkName,
                ),
            )
            val minSdkVersion = app.minSdkVersion
            val minSdkName = AndroidCodeName.getCodeName(minSdkVersion)
            binding.itemMinSdkVersion.setSummary(
                getString(
                    R.string.sdk_version_with_name_template,
                    minSdkVersion,
                    minSdkName,
                ),
            )
            val lastUpdateTime = app.lastUpdateTime
            binding.itemLastUpdateTime.setSummary(lastUpdateTime.toString())
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
