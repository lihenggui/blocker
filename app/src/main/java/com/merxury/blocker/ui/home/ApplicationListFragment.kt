package com.merxury.blocker.ui.home

import android.app.Activity
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import com.merxury.blocker.R
import com.merxury.blocker.base.BaseLazyFragment
import com.merxury.blocker.baseview.ContextMenuRecyclerView
import com.merxury.blocker.ui.Constants
import com.merxury.blocker.ui.component.ComponentActivity
import com.merxury.blocker.util.ToastUtil
import com.merxury.ifw.IntentFirewallImpl
import com.merxury.libkit.entity.Application
import com.merxury.libkit.entity.ETrimMemoryLevel
import com.merxury.libkit.utils.ApplicationUtil
import com.merxury.libkit.utils.ServiceHelper
import kotlinx.android.synthetic.main.app_list_item.view.*
import kotlinx.android.synthetic.main.fragment_app_list.*
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

class ApplicationListFragment : BaseLazyFragment(), HomeContract.View, CoroutineScope {

    override val coroutineContext: CoroutineContext = Dispatchers.Main
    override lateinit var presenter: HomeContract.Presenter
    private val servicesStatus = CopyOnWriteArrayList<ApplicationServicesStatus>()
    private lateinit var listAdapter: AppListRecyclerViewAdapter
    private var isSystem: Boolean = false
    private var parentJob: Job? = null
    private var itemListener: AppItemListener = object : AppItemListener {
        override fun onAppClick(application: Application) {
            presenter.openApplicationDetails(application)
        }

        override fun onAppLongClick(application: Application) {
            // Not implemented
        }
    }

    private fun initApplicationServicesStatus(applications: List<Application>) {
        parentJob?.cancel()
        parentJob = launch(Dispatchers.IO) {
            val pm = requireContext().packageManager
            applications.forEachIndexed { index, application ->
                val appStatus = getApplicationServiceStatus(pm, application.packageName)
                withContext(Dispatchers.Main) {
                    servicesStatus.add(appStatus)
                    listAdapter.notifyItemChanged(index)
                }
            }
        }
    }

    @WorkerThread
    private suspend fun getApplicationServiceStatus(packageManager: PackageManager, packageName: String): ApplicationServicesStatus {
        return withContext(Dispatchers.IO) {
            val serviceHelper = ServiceHelper(packageName)
            serviceHelper.refresh()
            val ifwImpl = IntentFirewallImpl(context, packageName)
            val services = ApplicationUtil.getServiceList(packageManager, packageName)
            var run = 0
            var dis = 0
            for (service in services) {
                if (!ifwImpl.getComponentEnableState(packageName, service.name) || !ApplicationUtil.checkComponentIsEnabled(packageManager, ComponentName(packageName, service.name))) {
                    dis++
                }
                if (serviceHelper.isServiceRunning(service.name)) {
                    run++
                }
            }
            ApplicationServicesStatus(packageName, services.size, run, dis)
        }
    }

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
        servicesStatus.clear()
        listAdapter.addData(applications)
        initApplicationServicesStatus(applications)
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
                presenter.loadApplicationList(requireContext(), isSystem)
                true
            }
            show()
        }

    }

    override fun showApplicationDetailsUi(application: Application) {
        val intent = Intent(context, ComponentActivity::class.java)
        intent.putExtra(Constants.APPLICATION, application)
        startActivityForResult(intent, 888)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 888 && resultCode == Activity.RESULT_OK && data != null) {
            data.getStringExtra("package")?.let { packageName ->
                launch {
                    val status = getApplicationServiceStatus(requireContext().packageManager, packageName)
                    servicesStatus.removeAll {
                        it.packageName == packageName
                    }
                    listAdapter.applications.forEachIndexed { index, application ->
                        if (application.packageName == packageName) {
                            servicesStatus.add(status)
                            listAdapter.notifyItemChanged(index)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val argument = arguments
        argument?.run {
            isSystem = this.getBoolean(IS_SYSTEM)
        }
        presenter = HomePresenter(this)
        presenter.start(requireContext())
        listAdapter = AppListRecyclerViewAdapter(itemListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_app_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appListFragmentRecyclerView?.apply {
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            this.layoutManager = layoutManager
            adapter = listAdapter
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
            addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(context, layoutManager.orientation))
            registerForContextMenu(this)
        }
        appListSwipeLayout?.apply {
            setOnRefreshListener { presenter.loadApplicationList(context, isSystem) }
        }
    }

    override fun loadData() {
        presenter.loadApplicationList(requireContext(), isSystem)
    }

    override fun onDestroy() {
        presenter.destroy()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.menu_search)
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

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activity?.menuInflater?.inflate(R.menu.app_list_long_click_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (!userVisibleHint) {
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
        launch {
            val updatedInfo = ApplicationUtil.getApplicationInfo(requireContext(), packageName)
                ?: return@launch
            listAdapter.update(updatedInfo)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter -> showFilteringPopUpMenu()
            R.id.menu_refresh -> presenter.loadApplicationList(requireContext(), isSystem)
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
        fun newInstance(isSystem: Boolean): androidx.fragment.app.Fragment {
            val fragment = ApplicationListFragment()
            val bundle = Bundle()
            bundle.putBoolean(IS_SYSTEM, isSystem)
            fragment.arguments = bundle
            return fragment
        }

    }

    inner class AppListRecyclerViewAdapter(private val listener: ApplicationListFragment.AppItemListener, var applications: MutableList<Application> = mutableListOf()) : androidx.recyclerview.widget.RecyclerView.Adapter<AppListRecyclerViewAdapter.ViewHolder>() {

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

        inner class ViewHolder(view: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            fun bindApplication(application: Application) {
                view?.apply {
                    itemView.app_name.text = application.label
                    itemView.isLongClickable = true
                    itemView.setOnClickListener { listener.onAppClick(application) }
                    if (!application.isEnabled) {
                        itemView.setBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.disabled_app_color
                            )
                        )
                    } else if (application.isBlocked) {
                        itemView.setBackgroundColor(
                            ContextCompat.getColor(
                                context,
                                R.color.blocked_app_color
                            )
                        )
                    } else {
                        itemView.setBackgroundColor(Color.WHITE)
                    }
                    launch {
                        val icon = withContext(Dispatchers.IO) {
                            application.getApplicationIcon(pm)
                        }
                        itemView.app_icon.setImageDrawable(icon)
                        val status = withContext(Dispatchers.Default) {
                            servicesStatus.find { it.packageName == application.packageName }
                        }
                        if (status == null) {
                            itemView.allCount.visibility = View.GONE
                            itemView.runCount.visibility = View.GONE
                            itemView.disableCount.visibility = View.GONE
                        } else {
                            itemView.allCount.visibility = View.VISIBLE
                            itemView.allCount.text = status.allCount.toString()

                            //run
                            itemView.runCount.run {
                                if (status.runCount == 0) visibility = View.GONE
                                else {
                                    visibility = View.VISIBLE
                                    text = status.runCount.toString()
                                }
                            }

                            //dis
                            itemView.disableCount.run {
                                if (status.disCount == 0) visibility = View.GONE
                                else {
                                    visibility = View.VISIBLE
                                    text = status.disCount.toString()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}