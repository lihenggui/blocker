package com.merxury.blocker.ui.detail

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.elvishew.xlog.XLog
import com.google.android.material.tabs.TabLayoutMediator
import com.merxury.blocker.R
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.databinding.ActivityAppDetailBinding
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.libkit.entity.Application
import rikka.shizuku.Shizuku

class AppDetailActivity : AppCompatActivity() {
    private var _app: Application? = null
    private val app get() = _app!!
    private val logger = XLog.tag("DetailActivity")
    private lateinit var binding: ActivityAppDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fetchAppInfo()
        initToolbar()
        initViewPager()
        initShizuku()
    }

    private fun initShizuku() {
        if (PreferenceUtil.getControllerType(this) != EControllerMethod.SHIZUKU) {
            return
        }
        logger.i("Request Shizuku permission")
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            logger.e("Shizuku does not support Android 5.1 or below")
            return
        }
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            return
        }
        val shizukuPermissionGranted =
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        when {
            shizukuPermissionGranted -> {
                logger.d("Shizuku permission was already granted")
                return
            }
            Shizuku.shouldShowRequestPermissionRationale() -> {
                // Users choose "Deny and don't ask again"
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_required)
                    .setMessage(R.string.shizuku_permission_required_message)
                    .show()
                logger.e("User denied Shizuku permission")
                return
            }
            else -> {
                logger.d("Request Shizuku permission")
                Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
            }
        }
    }

    private fun initToolbar() {
        binding.toolbar.title = app.label
    }

    private fun initViewPager() {
        binding.viewPager.apply {
            adapter = AppDetailAdapter(this@AppDetailActivity, app)
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(AppDetailAdapter.titles[position])
        }.attach()
    }

    private fun fetchAppInfo() {
        _app = intent?.getParcelableExtra(EXTRA_APP)
        if (_app == null) {
            logger.e("app is null")
            finish()
            return
        }
        logger.i("Show app: ${app.packageName}")
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