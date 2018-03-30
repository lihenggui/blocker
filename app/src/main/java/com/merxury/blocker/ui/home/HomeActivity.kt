package com.merxury.blocker.ui.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.design.widget.TabLayout
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.merxury.blocker.R
import com.merxury.blocker.ui.adapter.FragmentAdapter
import com.merxury.blocker.ui.base.IActivityView
import com.merxury.util.setupActionBar
import kotlinx.android.synthetic.main.activity_home.*

class HomeActivity : AppCompatActivity(), IActivityView {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // Set up toolbar
        setupActionBar(R.id.toolbar) {
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
            setDisplayHomeAsUpEnabled(true)
        }
        setupDrawerContent(nav_view)
        setupViewPager(app_viewpager)
        findViewById<TabLayout>(R.id.app_kind_tabs).apply {
            setupWithViewPager(app_viewpager)
            setupTab(this)
        }
    }

    private fun setupViewPager(viewPager: ViewPager) {
        val adapter = FragmentAdapter(supportFragmentManager)
        adapter.addFragment(ApplicationListFragment.newInstance(packageManager, false), getString(R.string.third_party_app_tab_text))
        adapter.addFragment(ApplicationListFragment.newInstance(packageManager, true), getString(R.string.system_app_tab_text))
        viewPager.adapter = adapter
    }

    private fun setupDrawerContent(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            //just placeholders
            if (menuItem.itemId == R.id.list_navigation_menu_item) {
                val intent = Intent(this@HomeActivity, HomeActivity::class.java)
                startActivity(intent)
            }
            menuItem.isChecked = true
            drawer_layout.closeDrawers()
            true
        }
    }

    private fun setupTab(tabLayout: TabLayout) {
        changeColor(getBackgroundColor(0))
        tabLayout.setSelectedTabIndicatorColor(resources.getColor(R.color.md_white_1000))
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            drawer_layout.openDrawer(GravityCompat.START)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun changeColor(color: Int) {
        toolbar.setBackgroundColor(color)
        app_kind_tabs.setBackgroundColor(color)
        window.statusBarColor = color
        setTaskDescription(ActivityManager.TaskDescription(null, null, color))
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
            0 -> ContextCompat.getColor(this, R.color.md_blue_700)
            1 -> ContextCompat.getColor(this, R.color.md_red_700)
            else -> ContextCompat.getColor(this, R.color.md_grey_700)
        }
    }
}
