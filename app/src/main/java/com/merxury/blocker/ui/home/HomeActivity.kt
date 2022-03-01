package com.merxury.blocker.ui.home

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.merxury.blocker.R
import com.merxury.blocker.databinding.ActivityHomeBinding


class HomeActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        initEdgeToEdge()
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        findViewById<BottomNavigationView>(R.id.bottom_nav)
            .setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun initEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                rightMargin = insets.right
            }
            windowInsets
        }
    }

    private fun showReportScreen() {
//        val logFile = filesDir.resolve(BlockerApplication.LOG_FILENAME)
//        val emailIntent = Intent(Intent.ACTION_SEND)
//            .setType("vnd.android.cursor.dir/email")
//            .putExtra(Intent.EXTRA_EMAIL, arrayOf("mercuryleee@gmail.com"))
//            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject_template))
//            .putExtra(Intent.EXTRA_TEXT, getString(R.string.report_content_template))
//        if (logFile.exists()) {
//            val logUri = FileProvider.getUriForFile(
//                this,
//                "com.merxury.blocker.provider",
//                logFile
//            )
//            emailIntent.putExtra(Intent.EXTRA_STREAM, logUri)
//        }
//        startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)));
    }
}
