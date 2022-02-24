package com.merxury.blocker.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elvishew.xlog.XLog
import com.google.android.material.tabs.TabLayoutMediator
import com.merxury.blocker.databinding.ActivityAppDetailBinding
import com.merxury.libkit.entity.Application

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
    }

    private fun initToolbar() {
        binding.toolbar.title = app.label
    }

    private fun initViewPager() {
        binding.viewPager.apply {
            adapter = AppDetailAdapter(this@AppDetailActivity, app)
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = "Tab $position"
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

        fun start(context: Context, app: Application) {
            Intent(context, AppDetailActivity::class.java).apply {
                putExtra(EXTRA_APP, app)
            }.run {
                context.startActivity(this)
            }
        }
    }
}