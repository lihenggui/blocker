package com.merxury.ui.component

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.merxury.blocker.R
import com.merxury.entity.Application
import kotlinx.android.synthetic.main.application_brief_info_layout.*

class ComponentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_component)
    }

    private fun showApplicationBriefInfo(application: Application) {
        app_info_app_name.text = getString(R.string.application_label, application.label)
        app_info_app_package_name.text = getString(R.string.package_name, application.packageName)
        app_info_target_sdk_version.text = getString(R.string.target_sdk_version, application.targetSdkVersion)
        app_info_min_sdk_version.text = getString(R.string.min_sdk_version, application.minSdkVersion)
        Glide.with(this)
                .load(application.getApplicationIcon(packageManager))
                .transition(DrawableTransitionOptions().crossFade())
                .into(app_info_icon)
    }
}
