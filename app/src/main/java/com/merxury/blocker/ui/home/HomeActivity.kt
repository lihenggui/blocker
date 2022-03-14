package com.merxury.blocker.ui.home

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.viewpager2.widget.ViewPager2
import com.merxury.blocker.R
import com.merxury.blocker.databinding.ActivityHomeBinding
import com.merxury.blocker.util.unsafeLazy


class HomeActivity : AppCompatActivity() {
    private val adapter by unsafeLazy { HomeAdapter(this) }
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        initEdgeToEdge()
        initNavigation()
    }

    private fun initNavigation() {
        binding.viewPager.adapter = adapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNav.menu.getItem(position)?.isChecked = true
            }
        })
        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.list -> binding.viewPager.currentItem = 0
                R.id.adv_search -> binding.viewPager.currentItem = 1
                R.id.settings -> binding.viewPager.currentItem = 2
                else -> binding.viewPager.currentItem = 0
            }
            true
        }
    }

    private fun initEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            windowInsets
        }
    }
}
