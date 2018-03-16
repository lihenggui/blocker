package com.merxury.ui.home

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.merxury.blocker.R
import com.merxury.entity.Application

class ApplicationListFragment : Fragment(), HomeContract.View {
    override var isActive: Boolean = false
        get() = isAdded

    override lateinit var presenter: HomeContract.Presenter
    private lateinit var noAppView: View
    private lateinit var noAppIcon: ImageView
    private lateinit var noAppMainView: TextView
    private lateinit var appListView: LinearLayout
    private lateinit var sortingFilterView: TextView

    private val listAdapter = AppListRecyclerViewAdapter(context)

    override fun setLoadingIndicator(active: Boolean) {
        val root = view ?: return
        with(root.findViewById<SwipeRefreshLayout>(R.id.app_list_swipe_refresh_layout)) {
            post { isRefreshing = active }
        }

    }

    override fun searchForApplication(name: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showApplicationList(applications: List<Application>) {
        listAdapter.addData(applications)
        appListView.visibility = View.VISIBLE
        noAppView.visibility = View.GONE
    }

    override fun showNoApplication() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showFilteringPopUpMenu() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_app_list, container, false)
        with(root) {
            val appListView = findViewById<RecyclerView>(R.id.app_list_fragment_recyclerview)

        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    interface AppItemListener {
        fun onAppClick(application: Application)
    }

    companion object {
        fun newInstance() = ApplicationListFragment()
    }


}