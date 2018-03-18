package com.merxury.ui.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.design.widget.TabLayout
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import com.merxury.blocker.R
import com.merxury.ui.adapter.FragmentAdapter
import com.merxury.util.setupActionBar
import kotlinx.android.synthetic.main.activity_home.*

class HomeActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var presenter: HomePresenter
    private lateinit var viewPager: ViewPager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // Set up toolbar
        setupActionBar(R.id.toolbar) {
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
            setDisplayHomeAsUpEnabled(true)
        }
        // Set up navigation drawer layout
        drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout).apply {
            //TODO("Setup color")
        }
        setupDrawerContent(findViewById(R.id.nav_view))
        viewPager = findViewById<ViewPager>(R.id.app_viewpager).apply {
            setupViewPager(this)
        }
        findViewById<TabLayout>(R.id.app_kind_tabs).apply {
            setupWithViewPager(viewPager)
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
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun setupTab(tabLayout: TabLayout) {
        changeColor(getColorForTab(0))
        tabLayout.setSelectedTabIndicatorColor(resources.getColor(R.color.md_white_1000))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                changeTabBackgroundColor(tabLayout, tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {

            }

            override fun onTabReselected(tabr: TabLayout.Tab) {

            }
        })
    }

    private fun changeTabBackgroundColor(tabLayout: TabLayout, tab: TabLayout.Tab) {
        val colorFrom: Int
        if (tabLayout.background != null) {
            colorFrom = (tabLayout.background as ColorDrawable).color
        } else {
            colorFrom = ContextCompat.getColor(this, android.R.color.darker_gray)
        }
        val colorTo = getColorForTab(tab.position)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.addUpdateListener { animation ->
            val color = animation.animatedValue as Int
            changeColor(color)
        }
        colorAnimation.duration = 500
        colorAnimation.start()
    }

    private fun getColorForTab(position: Int): Int {
        return when (position) {
            0 -> ContextCompat.getColor(this, R.color.md_blue_700)
            1 -> ContextCompat.getColor(this, R.color.md_red_700)
            else -> ContextCompat.getColor(this, R.color.md_grey_700)
        }
    }

    private fun changeColor(color: Int) {
        toolbar.setBackgroundColor(color)
        app_kind_tabs.setBackgroundColor(color)
        window.statusBarColor = color
    }

}
