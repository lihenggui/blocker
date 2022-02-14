package com.merxury.blocker.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.merxury.blocker.R


class HomeActivity : AppCompatActivity() {

    private var appBarConfiguration: AppBarConfiguration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        findViewById<Toolbar>(R.id.toolbar)
            .setupWithNavController(navController, appBarConfiguration!!)
        findViewById<BottomNavigationView>(R.id.bottom_nav)
            .setupWithNavController(navController)
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
