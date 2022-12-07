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

package com.merxury.blocker.ui.detail

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.elvishew.xlog.XLog
import com.google.android.material.tabs.TabLayoutMediator
import com.merxury.blocker.R
import com.merxury.blocker.core.entity.Application
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.databinding.ActivityAppDetailBinding
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.blocker.util.parcelable
import com.merxury.blocker.util.reduceDragSensitivity
import dagger.hilt.android.AndroidEntryPoint
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderDeadListener
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.sui.Sui

@AndroidEntryPoint
class AppDetailActivity : AppCompatActivity() {
    private var _app: Application? = null
    private val app get() = _app!!
    private val logger = XLog.tag("DetailActivity")
    private lateinit var binding: ActivityAppDetailBinding
    private val binderReceivedListener = OnBinderReceivedListener {
        if (Shizuku.isPreV11()) {
            logger.e("Shizuku pre-v11 is not supported")
        } else {
            logger.i("Shizuku binder received")
            checkPermission()
        }
    }
    private val binderDeadListener = OnBinderDeadListener {
        logger.e("Shizuku binder dead")
    }
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    logger.i("Shizuku permission granted")
                } else {
                    logger.e("Shizuku permission denied")
                    AlertDialog.Builder(this).setTitle(R.string.permission_required)
                        .setMessage(R.string.shizuku_permission_required_message).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        _app = intent?.parcelable(EXTRA_APP)
        if (_app == null) {
            logger.e("app is null")
            finish()
            return
        }
        logger.i("Show app: ${app.packageName}")
        initToolbar()
        initViewPager()
        initEdgeToEdge()
        registerShizuku()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterShizuku()
    }

    private fun registerShizuku() {
        if (PreferenceUtil.getControllerType(this) != EControllerMethod.SHIZUKU) return
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun checkPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            return false
        }
        try {
            return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                if (Sui.isSui()) {
                    Sui.init(packageName)
                }
                true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                logger.e("User denied Shizuku permission.")
                false
            } else {
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
                false
            }
        } catch (e: Throwable) {
            logger.e("Check Shizuku permission failed", e)
        }
        return false
    }

    private fun unregisterShizuku() {
        if (PreferenceUtil.getControllerType(this) != EControllerMethod.SHIZUKU) {
            return
        }
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun initToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = app.label
        }
    }

    private fun initViewPager() {
        binding.viewPager.apply {
            adapter = AppDetailAdapter(this@AppDetailActivity, app)
            reduceDragSensitivity()
            offscreenPageLimit = 2
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(AppDetailAdapter.titles[position])
        }.attach()
    }

    private fun initEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            windowInsets
        }
    }

    companion object {
        private const val EXTRA_APP = "EXTRA_APP"
        private const val REQUEST_CODE_PERMISSION = 101

        fun start(context: Context, app: Application) {
            Intent(context, AppDetailActivity::class.java).apply {
                putExtra(EXTRA_APP, app)
            }.run {
                context.startActivity(this)
            }
        }
    }
}
