package com.merxury.blocker.ui.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.merxury.blocker.BlockerApplication
import com.merxury.blocker.R
import com.merxury.blocker.adapter.FragmentAdapter
import com.merxury.blocker.base.IActivityView
import com.merxury.blocker.ui.settings.SettingsActivity
import com.merxury.blocker.util.setupActionBar
import com.merxury.libkit.utils.StatusBarUtil
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.withIcon
import com.mikepenz.materialdrawer.model.interfaces.withIdentifier
import com.mikepenz.materialdrawer.model.interfaces.withName
import kotlinx.android.synthetic.main.activity_home.*


class HomeActivity : AppCompatActivity(), IActivityView {
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setupActionBar(R.id.toolbar) {
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
            setDisplayHomeAsUpEnabled(true)
        }
        setupDrawerContent(savedInstanceState)
        setupViewPager(app_viewpager)
        findViewById<TabLayout>(R.id.app_kind_tabs).apply {
            setupWithViewPager(app_viewpager)
            setupTab(this)
        }
    }

    private fun setupViewPager(viewPager: ViewPager) {
        val adapter = FragmentAdapter(supportFragmentManager)
        adapter.addFragment(ApplicationListFragment.newInstance(false), getString(R.string.third_party_app_tab_text))
        adapter.addFragment(ApplicationListFragment.newInstance(true), getString(R.string.system_app_tab_text))
        viewPager.adapter = adapter
    }

    private fun setupDrawerContent(savedInstanceState: Bundle?) {
        val listItem = PrimaryDrawerItem()
            .withIdentifier(1)
            .withName(R.string.app_list_title)
            .withIcon(R.drawable.ic_list)
        val settingItem = SecondaryDrawerItem()
            .withIdentifier(2)
            .withName(R.string.action_settings)
            .withIcon(R.drawable.ic_settings)
        val emailItem = SecondaryDrawerItem()
            .withIdentifier(3)
            .withName(R.string.report)
            .withIcon(R.drawable.ic_email)
        slider.itemAdapter.add(
            listItem,
            settingItem,
            DividerDrawerItem(),
            emailItem
        )
        slider.onDrawerItemClickListener = { v, drawerItem, position ->
            when (drawerItem.identifier) {
                1L -> startActivity(Intent(this@HomeActivity, HomeActivity::class.java))
                2L -> startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
                3L -> showReportScreen()
            }
            false
        }
    }

    private fun setupTab(tabLayout: TabLayout) {
        changeColor(getBackgroundColor(0))
        tabLayout.setSelectedTabIndicatorColor(ContextCompat.getColor(this, R.color.component_item_background_color))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                changeBackgroundColor(tabLayout, tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {

            }

            override fun onTabReselected(tab: TabLayout.Tab) {

            }
        })
    }


    private fun changeColor(color: Int) {
        toolbar.setBackgroundColor(color)
        app_kind_tabs.setBackgroundColor(color)
        StatusBarUtil.setColorForDrawerLayout(this, drawerLayout, color, com.merxury.blocker.constant.Constant.STATUS_BAR_ALPHA)
        findViewById<View>(R.id.statusbarutil_translucent_view).setBackgroundColor(color)
    }


    private fun changeBackgroundColor(tabLayout: TabLayout, tab: TabLayout.Tab) {
        val colorFrom: Int
        if (tabLayout.background != null) {
            colorFrom = (tabLayout.background as ColorDrawable).color
        } else {
            colorFrom = ContextCompat.getColor(this, android.R.color.darker_gray)
        }
        val colorTo = getBackgroundColor(tab.position)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.addUpdateListener { animation ->
            val color = animation.animatedValue as Int
            changeColor(color)
        }
        colorAnimation.duration = 500
        colorAnimation.start()
    }

    override fun getBackgroundColor(tabPosition: Int): Int {
        return when (tabPosition) {
            0 -> ContextCompat.getColor(this, R.color.primary)
            1 -> ContextCompat.getColor(this, R.color.google_red)
            else -> ContextCompat.getColor(this, R.color.google_blue)
        }
    }

    private fun showReportScreen() {
        val logFile = filesDir.resolve(BlockerApplication.LOG_FILENAME)
        val emailIntent = Intent(Intent.ACTION_SEND)
            .setType("vnd.android.cursor.dir/email")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("mercuryleee@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject_template))
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.report_content_template))
        if (logFile.exists()) {
            val logUri = FileProvider.getUriForFile(
                this,
                "com.merxury.blocker.provider", //(use your app signature + ".provider" )
                logFile);
            emailIntent.putExtra(Intent.EXTRA_STREAM, logUri)
        }
        startActivity(Intent.createChooser(emailIntent , getString(R.string.send_email)));
    }
}
