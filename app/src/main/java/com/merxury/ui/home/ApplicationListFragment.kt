package com.merxury.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import com.merxury.blocker.R
import com.merxury.entity.Application
import kotlinx.android.synthetic.main.fragment_app_list.*
import kotlinx.android.synthetic.main.fragment_app_list.view.*

class ApplicationListFragment : Fragment(), HomeContract.View {
    override var isActive: Boolean = false
        get() = isAdded

    override lateinit var presenter: HomeContract.Presenter
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
        with(appListSwipeLayout) {
            post { isRefreshing = active }
        }
    }

    override fun searchForApplication(name: String) {
        TODO("not implemented")
    }

    override fun showApplicationList(applications: List<Application>) {
        appListFragmentRecyclerView.visibility = View.VISIBLE
        noAppContainer.visibility = View.GONE
        listAdapter.addData(applications)
    }

    override fun showNoApplication() {
        appListFragmentRecyclerView.visibility = View.GONE
        noAppContainer.visibility = View.VISIBLE
    }

    override fun showFilteringPopUpMenu() {
        PopupMenu(activity, activity?.findViewById(R.id.menu_filter)).apply {
            menuInflater.inflate(R.menu.filter_application, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.name_asc -> presenter.currentComparator = ApplicationComparatorType.ASCENDING_BY_LABEL
                    R.id.name_des -> presenter.currentComparator = ApplicationComparatorType.DESCENDING_BY_LABEL
                    R.id.installation_date -> presenter.currentComparator = ApplicationComparatorType.BY_INSTALLATION_DATE
                    else -> presenter.currentComparator = ApplicationComparatorType.BY_INSTALLATION_DATE
                }
                presenter.loadApplicationList(context!!, isSystem)
                true
            }
            show()
        }

    }

    override fun showApplicationDetailsUi(application: Application) {
        val intent = Intent(context, com.merxury.ui.component.ComponentActivity::class.java)
        intent.putExtra(Constant.APPLICATION, application)
        context?.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val argument = arguments
        argument?.let {
            isSystem = it.getBoolean(IS_SYSTEM)
        }
        presenter = HomePresenter(context!!.packageManager, this)
        listAdapter = AppListRecyclerViewAdapter(context?.packageManager, itemListener)
    }

    override fun onResume() {
        super.onResume()
        context?.let {
            presenter.start(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_app_list, container, false)
        with(root) {
            appListFragmentRecyclerView.apply {
                val layoutManager = LinearLayoutManager(context)
                this.layoutManager = layoutManager
                adapter = listAdapter
                itemAnimator = DefaultItemAnimator()
                addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))
            }
            appListSwipeLayout.apply {
                setColorSchemeColors(
                        ContextCompat.getColor(context, R.color.colorPrimary),
                        ContextCompat.getColor(context, R.color.colorAccent),
                        ContextCompat.getColor(context, R.color.colorPrimaryDark)
                )
                setOnRefreshListener { presenter.loadApplicationList(context, isSystem) }
            }

        }
        setHasOptionsMenu(true)
        return root
    }

    override fun onStart() {
        super.onStart()
        val fragmentContext = context
        fragmentContext?.let {
            presenter.loadApplicationList(it, isSystem)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater) {
        inflater.inflate(R.menu.app_list_fragment_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter -> showFilteringPopUpMenu()
            R.id.menu_refresh -> presenter.loadApplicationList(context!!, isSystem)
        }
        return true
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
            return fragment
        }

    }

}