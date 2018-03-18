package com.merxury.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.merxury.blocker.R
import com.merxury.constant.Constant
import com.merxury.entity.Application

class ApplicationListFragment : Fragment(), HomeContract.View {
    override var isActive: Boolean = false
        get() = isAdded

    override lateinit var presenter: HomeContract.Presenter
    private lateinit var noAppIcon: ImageView
    private lateinit var noAppMainView: TextView
    private lateinit var noAppContainer: LinearLayout
    private lateinit var appListView: RecyclerView
    private lateinit var sortingFilterView: TextView

    private var isSystem: Boolean = false

    /**
     * listener for clicks on items in the RecyclerView
     */
    internal var itemListener: AppItemListener = object : AppItemListener {
        override fun onAppClick(application: Application) {
            presenter.openApplicationDetails(application)
        }
    }

    private lateinit var listAdapter: AppListRecyclerViewAdapter

    override fun setLoadingIndicator(active: Boolean) {
        val root = view ?: return
        with(root.findViewById<SwipeRefreshLayout>(R.id.appListSwipeLayout)) {
            post { isRefreshing = active }
        }
    }

    override fun searchForApplication(name: String) {
        TODO("not implemented")
    }

    override fun showApplicationList(applications: List<Application>) {
        appListView.visibility = View.VISIBLE
        noAppContainer.visibility = View.GONE
        listAdapter.addData(applications)
    }

    override fun showNoApplication() {
        appListView.visibility = View.GONE
        noAppContainer.visibility = View.VISIBLE
    }

    override fun showFilteringPopUpMenu() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showApplicationDetailsUi(application: Application) {
        val intent = Intent()
        intent.putExtra(Constant.APPLICATION, application)
        context?.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            isSystem = savedInstanceState.getBoolean(IS_SYSTEM)
        }
        listAdapter = AppListRecyclerViewAdapter(context?.packageManager, itemListener)
    }
    override fun onResume() {
        super.onResume()
        val context = context
        if (context != null) {
            presenter.start(context)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_app_list, container, false)
        with(root) {
            appListView = findViewById<RecyclerView>(R.id.appListFragmentRecyclerView).apply { adapter = listAdapter }
            findViewById<SwipeRefreshLayout>(R.id.appListSwipeLayout).apply {
                setColorSchemeColors(
                        ContextCompat.getColor(context, R.color.colorPrimary),
                        ContextCompat.getColor(context, R.color.colorAccent),
                        ContextCompat.getColor(context, R.color.colorPrimaryDark)
                )
                setOnRefreshListener { presenter.loadApplicationList(context, isSystem) }
            }

            noAppContainer = findViewById(R.id.noAppContainer)
            noAppIcon = findViewById(R.id.noAppIcon)
            noAppMainView = findViewById(R.id.noAppMain)
        }
        setHasOptionsMenu(true)
        return root
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater) {
        inflater.inflate(R.menu.app_list_fragment_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
        }
        TODO("Implement sorting menu")
    }

    interface AppItemListener {
        fun onAppClick(application: Application)
    }

    companion object {
        const val IS_SYSTEM: String = "IS_SYSTEM"
        fun newInstance(pm: PackageManager, isSystem: Boolean): Fragment {
            val fragment = ApplicationListFragment()
            val bundle = Bundle()
            bundle.putBoolean(IS_SYSTEM, isSystem)
            fragment.arguments = bundle
            fragment.presenter = HomePresenter(pm, fragment)
            return fragment
        }

    }

}