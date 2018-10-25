package com.merxury.blocker.ui.home

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.merxury.blocker.R
import com.merxury.blocker.baseview.ContextMenuRecyclerView
import com.merxury.blocker.ui.Constants
import com.merxury.blocker.ui.component.ComponentActivity
import com.merxury.blocker.util.ToastUtil
import com.merxury.libkit.entity.Application
import com.merxury.libkit.entity.ETrimMemoryLevel
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.android.synthetic.main.app_list_item.view.*
import kotlinx.android.synthetic.main.fragment_app_list.*


class ApplicationListFragment : Fragment(), HomeContract.View {
    override lateinit var presenter: HomeContract.Presenter

    private var isSystem: Boolean = false

    /**
     * listener for clicks on items in the RecyclerView
     */
    private var itemListener: AppItemListener = object : AppItemListener {
        override fun onAppClick(application: Application) {
            presenter.openApplicationDetails(application)
        }

        override fun onAppLongClick(application: Application) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    private lateinit var listAdapter: AppListRecyclerViewAdapter

    override fun setLoadingIndicator(active: Boolean) {
        appListSwipeLayout?.run {
            post { isRefreshing = active }
        }
    }

    override fun searchForApplication(name: String) {
        listAdapter.filter(name)
    }

    override fun showApplicationList(applications: MutableList<Application>) {
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
                    R.id.installation_time -> presenter.currentComparator = ApplicationComparatorType.INSTALLATION_TIME
                    R.id.last_update_time -> presenter.currentComparator = ApplicationComparatorType.LAST_UPDATE_TIME
                    else -> presenter.currentComparator = ApplicationComparatorType.DESCENDING_BY_LABEL
                }
                presenter.loadApplicationList(context!!, isSystem)
                true
            }
            show()
        }

    }

    override fun showApplicationDetailsUi(application: Application) {
        val intent = Intent(context, ComponentActivity::class.java)
        intent.putExtra(Constants.APPLICATION, application)
        context?.startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val argument = arguments
        argument?.run {
            isSystem = this.getBoolean(IS_SYSTEM)

        }
        presenter = HomePresenter(this)
        presenter.start(context!!)
        listAdapter = AppListRecyclerViewAdapter(itemListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_app_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appListFragmentRecyclerView?.apply {
            val layoutManager = android.support.v7.widget.LinearLayoutManager(context)
            this.layoutManager = layoutManager
            adapter = listAdapter
            itemAnimator = DefaultItemAnimator()
            addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))
            registerForContextMenu(this)
        }
        appListSwipeLayout?.apply {
            setColorSchemeColors(
                    ContextCompat.getColor(context, com.merxury.blocker.R.color.colorPrimary),
                    ContextCompat.getColor(context, com.merxury.blocker.R.color.colorAccent),
                    ContextCompat.getColor(context, com.merxury.blocker.R.color.colorPrimaryDark)
            )
            setOnRefreshListener { presenter.loadApplicationList(context, isSystem) }
        }
        presenter.loadApplicationList(context!!, isSystem)
    }

    override fun onDestroy() {
        presenter.destroy()
        super.onDestroy()
    }

    override fun getViewActivity(): Activity {
        return activity as Activity
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu?.findItem(R.id.menu_search)
        val searchView = searchItem?.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                searchForApplication(newText)
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                searchForApplication(query)
                return true
            }
        })
        searchView.setOnSearchClickListener {
            setItemsVisibility(menu, searchItem, false)
        }
        searchView.setOnCloseListener {
            setItemsVisibility(menu, searchItem, true)
            false
        }

    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activity?.menuInflater?.inflate(R.menu.app_list_long_click_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        if (!userVisibleHint || item == null) {
            return false
        }
        val position = (item.menuInfo as ContextMenuRecyclerView.RecyclerContextMenuInfo).position
        val application = listAdapter.getDataAt(position)
        val packageName = application.packageName
        when (item.itemId) {
            R.id.block_application -> presenter.blockApplication(packageName)
            R.id.unblock_application -> presenter.unblockApplication(packageName)
            R.id.launch_application -> presenter.launchApplication(packageName)
            R.id.force_stop -> presenter.forceStop(packageName)
            R.id.enable_application -> presenter.enableApplication(packageName)
            R.id.disable_application -> presenter.disableApplication(packageName)
            R.id.clear_data -> presenter.clearData(packageName)
            R.id.trim_memory -> presenter.trimMemory(packageName, ETrimMemoryLevel.COMPLETE)
            R.id.details -> presenter.showDetails(packageName)
        }
        return true
    }

    override fun showAlert(alertMessage: Int, confirmAction: () -> Unit) {
        context?.let {
            AlertDialog.Builder(it)
                    .setTitle(R.string.alert)
                    .setMessage(alertMessage)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel) { dialog: DialogInterface?, _: Int -> dialog?.dismiss() }
                    .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int -> confirmAction() }
                    .show()
        }
    }

    override fun showError(errorMessage: Int) {
        context?.let {
            AlertDialog.Builder(it)
                    .setTitle(R.string.oops)
                    .setMessage(errorMessage)
                    .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .show()
        }
    }

    override fun showToastMessage(message: String?, length: Int) {
        ToastUtil.showToast(message ?: "", length)
    }

    override fun showDataCleared() {
        Toast.makeText(context, R.string.data_cleared, Toast.LENGTH_SHORT).show()
    }

    override fun showForceStopped() {
        Toast.makeText(context, R.string.force_stopped, Toast.LENGTH_SHORT).show()
    }

    override fun updateState(packageName: String) {
        val updatedInfo = ApplicationUtil.getApplicationInfo(context!!, packageName) ?: return
        listAdapter.update(updatedInfo)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter -> showFilteringPopUpMenu()
            R.id.menu_refresh -> presenter.loadApplicationList(context!!, isSystem)
        }
        return true
    }

    private fun setItemsVisibility(menu: Menu, exception: MenuItem, visible: Boolean) {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item !== exception)
                item.isVisible = visible
        }
    }

    interface AppItemListener {
        fun onAppClick(application: Application)
        fun onAppLongClick(application: Application)
    }

    companion object {
        const val IS_SYSTEM: String = "IS_SYSTEM"
        fun newInstance(isSystem: Boolean): Fragment {
            val fragment = ApplicationListFragment()
            val bundle = Bundle()
            bundle.putBoolean(IS_SYSTEM, isSystem)
            fragment.arguments = bundle
            return fragment
        }

    }

    inner class AppListRecyclerViewAdapter(private val listener: ApplicationListFragment.AppItemListener, private var applications: MutableList<Application> = mutableListOf()) : RecyclerView.Adapter<AppListRecyclerViewAdapter.ViewHolder>() {

        private lateinit var pm: PackageManager
        private var listCopy = ArrayList<Application>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.app_list_item, parent, false)
            pm = parent.context.packageManager
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return this.applications.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindApplication(this.applications[position])
        }

        fun addData(applications: MutableList<Application>) {
            this.applications = applications
            this.listCopy = ArrayList(applications)
            notifyDataSetChanged()
        }

        fun getDataAt(position: Int): Application {
            return applications[position]
        }

        fun update(application: Application) {
            val position = getPositionByPackageName(application.packageName)
            if (position == -1) return
            applications[position] = application
            notifyItemChanged(position)

        }

        fun getPositionByPackageName(packageName: String): Int {
            applications.forEachIndexed { index, application ->
                if (application.packageName == packageName) {
                    return index
                }
            }
            return -1
        }

        fun filter(keyword: String) {
            applications = if (keyword.isEmpty()) {
                listCopy
            } else {
                listCopy.asSequence()
                        .filter { it.label.contains(keyword, true) || it.packageName.contains(keyword, true) }
                        .toMutableList()
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bindApplication(application: Application) {
                view?.apply {
                    itemView.app_name.text = application.label
                    itemView.app_icon.setImageDrawable(application.getApplicationIcon(pm))
                    itemView.isLongClickable = true
                    itemView.setOnClickListener { listener.onAppClick(application) }
                    if (!application.isEnabled) {
                        itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_300))
                    } else if (application.isBlocked) {
                        itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.md_red_50))
                    } else {
                        itemView.setBackgroundColor(Color.WHITE)
                    }
                    val options = RequestOptions()
                            .fitCenter()
                            .error(R.drawable.ic_error_red_24dp)
                    Glide.with(this)
                            .load(application.getApplicationIcon(pm))
                            .apply(options)
                            .transition(withCrossFade(DrawableCrossFadeFactory.Builder(100).setCrossFadeEnabled(true).build()))
                            .into(itemView.app_icon)
                }
            }

        }
    }
}