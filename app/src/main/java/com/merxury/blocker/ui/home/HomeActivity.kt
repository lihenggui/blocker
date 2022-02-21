package com.merxury.blocker.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.merxury.blocker.R


class HomeActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
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
