package com.merxury.blocker.ui.component

import android.content.*
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.*
import android.view.*
import android.widget.PopupMenu
import com.merxury.blocker.R
import com.merxury.blocker.core.ApplicationComponents
import com.merxury.blocker.ui.strategy.entity.Component
import com.merxury.blocker.ui.strategy.entity.view.AppComponentInfo
import kotlinx.android.synthetic.main.component_item.view.*
import kotlinx.android.synthetic.main.fragment_component.*
import kotlinx.android.synthetic.main.fragment_component.view.*

class ComponentFragment : Fragment(), ComponentContract.View, ComponentContract.ComponentMainView, ComponentContract.ComponentItemListener {

    override lateinit var presenter: ComponentContract.Presenter
    private lateinit var componentDetailsPresenter: ComponentContract.ComponentDataPresenter
    private lateinit var componentAdapter: ComponentsRecyclerViewAdapter
    private lateinit var packageName: String
    private lateinit var type: EComponentType

    private lateinit var receiver: OnComponentDetailsLoadedReceiver


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        type = args?.getSerializable(Constant.CATEGORY) as EComponentType
        packageName = args.getString(Constant.PACKAGE_NAME)
        presenter = ComponentPresenter(context!!.packageManager, this)
        componentDetailsPresenter = (activity as ComponentContract.ComponentMainView).getComponentDataPresenter()
        registerReceiver()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_component, container, false)
        with(root) {
            componentListSwipeLayout.apply {
                setColorSchemeColors(
                        ContextCompat.getColor(context, R.color.colorPrimary),
                        ContextCompat.getColor(context, R.color.colorAccent),
                        ContextCompat.getColor(context, R.color.colorPrimaryDark)
                )
                setOnRefreshListener {
                    presenter.loadComponents(packageName, type)
                }
            }

            componentListFragmentRecyclerView.apply {
                val layoutManager = LinearLayoutManager(context)
                this.layoutManager = layoutManager
                componentAdapter = ComponentsRecyclerViewAdapter()
                this.adapter = componentAdapter
                this.itemAnimator = DefaultItemAnimator()
                addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))
            }
        }
        setHasOptionsMenu(true)
        return root
    }

    override fun onResume() {
        super.onResume()
        registerReceiver()
        presenter.loadComponents(packageName, type)
    }

    override fun onStop() {
        unregisterReceiver()
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater) {
        inflater.inflate(R.menu.list_fragment_menu, menu)
        val searchItem = menu?.findItem(R.id.menu_search)
        val searchView = searchItem?.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                searchForComponent(newText)
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                searchForComponent(query)
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter -> showFilteringPopUpMenu()
            R.id.menu_refresh -> presenter.loadComponents(packageName, type)
        }
        return true
    }


    override fun setLoadingIndicator(active: Boolean) {
        with(componentListSwipeLayout) {
            post { isRefreshing = active }
        }
    }

    override fun showNoComponent() {
        componentListFragmentRecyclerView.visibility = View.GONE
        noComponentContainer.visibility = View.VISIBLE
    }

    override fun searchForComponent(name: String) {
        componentAdapter.filter(name)
    }

    override fun showFilteringPopUpMenu() {
        PopupMenu(activity, activity?.findViewById(R.id.menu_filter)).apply {
            menuInflater.inflate(R.menu.filter_component, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.name_asc -> presenter.currentComparator = EComponentComparatorType.NAME_ASCENDING
                    R.id.name_des -> presenter.currentComparator = EComponentComparatorType.NAME_DESCENDING
                    R.id.package_name_asc -> presenter.currentComparator = EComponentComparatorType.PACKAGE_NAME_ASCENDING
                    R.id.package_name_des -> presenter.currentComparator = EComponentComparatorType.PACKAGE_NAME_DESCENDING
                }
                presenter.loadComponents(packageName, type)
                true
            }
            show()
        }

    }

    override fun refreshComponentSwitchState(componentName: String) {
        val components = componentAdapter.getData()
        for (i in components.indices) {
            if (componentName == components[i].name) {
                componentAdapter.notifyItemChanged(i)
            }
        }
    }

    override fun showAlertDialog() {
        context?.apply {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(resources.getString(R.string.alert_dialog_title_error))
                    .setMessage(R.string.alert_dialog_message_error)
                    .setPositiveButton(R.string.close, { dialog: DialogInterface, _: Int -> dialog.dismiss() })
                    .show()
        }

    }

    override fun showComponentList(components: List<ComponentInfo>) {
        noComponentContainer.visibility = View.GONE
        componentListFragmentRecyclerView.visibility = View.VISIBLE
        componentAdapter.addData(components)
    }

    override fun onComponentClick(component: ComponentInfo) {
    }

    override fun onComponentLongClick(component: ComponentInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSwitchClick(component: ComponentInfo, isChecked: Boolean) {
        if (isChecked) {
            presenter.enableComponent(component)
        } else {
            presenter.disableComponent(component)
        }
    }

    override fun onUpVoteClick(component: ComponentInfo) {
    }

    override fun onDownVoteClick(component: ComponentInfo) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onComponentLoaded(appComponentInfo: AppComponentInfo) {

    }

    override fun getComponentDataPresenter(): ComponentContract.ComponentDataPresenter {
        TODO("won't implemented")
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter(Constant.DETAIL_LOADED)
        receiver = OnComponentDetailsLoadedReceiver()
        LocalBroadcastManager.getInstance(context!!).registerReceiver(receiver, intentFilter)
    }

    private fun unregisterReceiver() {
        LocalBroadcastManager.getInstance(context!!).unregisterReceiver(receiver)
    }

    companion object {
        const val TAG = "ComponentFragment"
        fun newInstance(packageName: String, type: EComponentType): Fragment {
            val fragment = ComponentFragment()
            val bundle = Bundle()
            bundle.putSerializable(Constant.CATEGORY, type)
            bundle.putString(Constant.PACKAGE_NAME, packageName)
            fragment.arguments = bundle
            return fragment
        }
    }


    inner class ComponentsRecyclerViewAdapter(private var components: List<ComponentInfo> = ArrayList()) : RecyclerView.Adapter<ComponentsRecyclerViewAdapter.ViewHolder>() {

        lateinit var pm: PackageManager
        private var listCopy = ArrayList<ComponentInfo>()
        private lateinit var componentData: AppComponentInfo
        private lateinit var listener: ComponentContract.ComponentItemListener

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.component_item, parent, false)
            pm = parent.context.packageManager
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindComponent(this.components[position])
            updateComponentDetails(position, holder)
        }

        private fun updateComponentDetails(position: Int, holder: ViewHolder) {
            if (::componentData.isInitialized) {
                val components =
                        when (type) {
                            EComponentType.ACTIVITY -> componentData.activity
                            EComponentType.RECEIVER -> componentData.receiver
                            EComponentType.SERVICE -> componentData.service
                            EComponentType.PROVIDER -> componentData.provider
                            EComponentType.UNKNOWN -> ArrayList()
                        }
                val name = this.components[position].name
                run outside@{
                    components?.forEach inside@{
                        if (it.name == name) {
                            holder.updateComponentDetails(it)
                            return@outside
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return this.components.size
        }

        fun addData(components: List<ComponentInfo>) {
            this.components = components
            this.listCopy = ArrayList(components)
            notifyDataSetChanged()
        }

        fun addComponentDetails(componentData: AppComponentInfo) {
            this.componentData = componentData
            notifyDataSetChanged()
        }

        fun getData(): List<ComponentInfo> {
            return components
        }

        fun filter(keyword: String) {
            components = if (keyword.isEmpty()) {
                listCopy
            } else {
                listCopy.filter { it.name.contains(keyword, true) }
            }
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bindComponent(component: ComponentInfo) {
                val componentShortName = component.name.split(".").last()
                with(itemView) {
                    component_name.text = componentShortName
                    component_package_name.text = component.name
                    component_switch.isChecked = ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(component.packageName, component.name))
                    setOnClickListener {
                        listener.onSwitchClick(component, !it.component_switch.isChecked)
                        it.component_switch.isChecked = !it.component_switch.isChecked
                    }
                    setOnLongClickListener {
                        listener.onComponentLongClick(component)
                        true
                    }
                    component_switch.setOnClickListener {
                        listener.onSwitchClick(component, it.component_switch.isChecked)
                    }
                    component_like_button.setOnClickListener {
                        listener.onUpVoteClick(component)
                    }
                    component_like_count.setOnClickListener {
                        listener.onUpVoteClick(component)
                    }
                    component_dislike_button.setOnClickListener {
                        listener.onDownVoteClick(component)
                    }
                    component_dislike_count.setOnClickListener {
                        listener.onDownVoteClick(component)
                    }
                }
            }

            fun updateComponentDetails(component: Component) {
                with(component) {
                    itemView.component_description.text = component.bestComment?.description
                    itemView.component_like_count.text = component.upVoteCount.toString()
                    itemView.component_dislike_count.text = component.downVoteCount.toString()
                }
            }

            private fun switchComponent(component: ComponentInfo, isChecked: Boolean) {

            }
        }
    }

    inner class OnComponentDetailsLoadedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val details = componentDetailsPresenter.getComponentData(packageName)
            componentAdapter.addComponentDetails(details)
        }
    }
}