/*
 * Copyright 2023 Blocker
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
import com.merxury.blocker.util.reduceDragSensitivity
import com.merxury.blocker.util.unsafeLazy
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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
        binding.viewPager.reduceDragSensitivity()
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
