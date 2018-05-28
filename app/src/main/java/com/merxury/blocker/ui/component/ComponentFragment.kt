package com.merxury.blocker.ui.component

import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.*
import android.view.*
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import com.merxury.blocker.R
import com.merxury.blocker.ui.baseview.ContextMenuRecyclerView
import com.merxury.blocker.ui.strategy.entity.Component
import com.merxury.blocker.ui.strategy.entity.view.AppComponentInfo
import kotlinx.android.synthetic.main.component_item.view.*
import kotlinx.android.synthetic.main.fragment_component.*
import kotlinx.android.synthetic.main.fragment_component.view.*

class ComponentFragment : Fragment(), ComponentContract.View, ComponentContract.ComponentItemListener {

    override lateinit var presenter: ComponentContract.Presenter
    private lateinit var componentOnlineDetailsPresenter: ComponentContract.ComponentOnlineDataPresenter
    private lateinit var componentAdapter: ComponentsRecyclerViewAdapter
    private lateinit var packageName: String
    private lateinit var type: EComponentType

    private lateinit var receiver: OnComponentDetailsLoadedReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        type = args?.getSerializable(Constant.CATEGORY) as EComponentType
        packageName = args.getString(Constant.PACKAGE_NAME)
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
                componentAdapter.setOnClickListener(this@ComponentFragment)
                this.adapter = componentAdapter
                this.itemAnimator = DefaultItemAnimator()
                addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))
                registerForContextMenu(this)
            }
        }
        setHasOptionsMenu(true)
        presenter = ComponentPresenter(context!!, this, packageName)
        return root
    }

    override fun onStart() {
        super.onStart()
        registerReceiver()
        presenter.loadComponents(packageName, type)
    }

    override fun onStop() {
        unregisterReceiver()
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver()
        presenter.destroy()
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
            R.id.menu_block_all -> showDisableAllAlert()
            R.id.menu_enable_all -> {
                Toast.makeText(context, R.string.enabling_hint, Toast.LENGTH_SHORT).show()
                presenter.enableAllComponents(packageName, type)
            }
        }
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        activity?.menuInflater?.inflate(R.menu.component_list_long_click_menu, menu)
        if (type != EComponentType.ACTIVITY) {
            menu?.removeItem(R.id.launch_activity)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (!userVisibleHint) {
            return false
        }
        val position = (item.menuInfo as ContextMenuRecyclerView.RecyclerContextMenuInfo).position
        val component = componentAdapter.getData()[position]
        when (item.itemId) {
            R.id.block_by_ifw -> presenter.addToIFW(component.packageName, component.name, type)
            R.id.enable_by_ifw -> presenter.removeFromIFW(component.packageName, component.name, type)
            R.id.launch_activity -> presenter.launchActivity(component.packageName, component.name)
            R.id.menu_comments -> showAddComment(component.packageName, component.name)
            R.id.view_component_comments -> {
            }
            R.id.menu_upvote_component -> presenter.voteForComponent(component.packageName, component.name, type)
            R.id.menu_downvote_component -> presenter.downVoteForComponent(component.packageName, component.name, type)
        }
        return true
    }


    override fun setLoadingIndicator(active: Boolean) {
        componentListSwipeLayout?.run {
            post { isRefreshing = active }
        }
    }

    override fun showNoComponent() {
        componentListFragmentRecyclerView?.visibility = View.GONE
        noComponentContainer?.visibility = View.VISIBLE
    }

    override fun searchForComponent(name: String) {
        componentAdapter.filter(name)
    }

    override fun showFilteringPopUpMenu() {
        PopupMenu(activity, activity?.findViewById(R.id.menu_filter)).apply {
            menuInflater.inflate(R.menu.filter_component, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.name_asc -> presenter.currentComparator = EComponentComparatorType.SIMPLE_NAME_ASCENDING
                    R.id.name_des -> presenter.currentComparator = EComponentComparatorType.SIMPLE_NAME_DESCENDING
                    R.id.package_name_asc -> presenter.currentComparator = EComponentComparatorType.NAME_ASCENDING
                    R.id.package_name_des -> presenter.currentComparator = EComponentComparatorType.NAME_DESCENDING
                }
                presenter.loadComponents(packageName, type)
                true
            }
            show()
        }

    }

    override fun refreshComponentState(componentName: String) {
        val components = componentAdapter.getData()
        for (i in components.indices) {
            if (componentName == components[i].name) {
                val viewModel = presenter.getComponentViewModel(packageName, componentName)
                components[i] = viewModel
                componentAdapter.notifyItemChanged(i)
                break
            }
        }
    }

    override fun showAlertDialog() {
        context?.apply {
            AlertDialog.Builder(this)
                    .setTitle(resources.getString(R.string.oops))
                    .setMessage(R.string.no_root_error_message)
                    .setPositiveButton(R.string.close, { dialog: DialogInterface, _: Int -> dialog.dismiss() })
                    .show()
        }
    }

    override fun showComponentList(components: MutableList<ComponentItemViewModel>) {
        noComponentContainer?.visibility = View.GONE
        componentListFragmentRecyclerView?.visibility = View.VISIBLE
        componentAdapter.updateData(components)
    }

    override fun onComponentClick(name: String) {
    }

    override fun onComponentLongClick(name: String) {

    }

    override fun onSwitchClick(name: String, isChecked: Boolean) {
        if (isChecked) {
            presenter.enable(packageName, name)
            if (!presenter.checkIFWState(packageName, name)) {
                presenter.removeFromIFW(packageName, name, type)
            }
        } else {
            presenter.disable(packageName, name)
        }
    }

    override fun onUpVoteClick(name: String) {
        // TODO add cancel vote in future version
        if (!presenter.checkComponentIsUpVoted(packageName, name)) {
            presenter.voteForComponent(packageName, name, type)
        }
    }

    override fun onDownVoteClick(name: String) {
        // TODO add cancel vote in future version
        presenter.downVoteForComponent(packageName, name, type)
    }

    override fun showAddComment(packageName: String, componentName: String) {
        context?.apply {
            val view = layoutInflater.inflate(R.layout.add_comment, null)
            val commentInput = view.findViewById<EditText>(R.id.add_comment_input)
            AlertDialog.Builder(this)
                    .setTitle(R.string.add_comment)
                    .setCancelable(true)
                    .setView(view)
                    .setNegativeButton(R.string.cancel, { dialog, _ -> dialog.dismiss() })
                    .setPositiveButton(R.string.send, { dialog, which -> componentOnlineDetailsPresenter.sendDescription(packageName, componentName, type, commentInput.text.toString()) })
                    .create()
                    .show()
        }
    }

    override fun showDisableAllAlert() {
        context?.let {
            AlertDialog.Builder(it)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.warning_disable_all_component)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel, { dialog, _ -> dialog.dismiss() })
                    .setPositiveButton(R.string.ok, { _, _ ->
                        Toast.makeText(it, R.string.disabling_hint, Toast.LENGTH_SHORT).show()
                        presenter.disableAllComponents(packageName, type)
                    })
                    .create()
                    .show()
        }
    }

    override fun showVoteFail() {
        Toast.makeText(context, resources.getText(R.string.vote_fail), Toast.LENGTH_SHORT).show()
    }

    override fun showActionDone() {
        Toast.makeText(context, R.string.done, Toast.LENGTH_SHORT).show()
    }

    override fun showActionFail() {
        Toast.makeText(context, R.string.fail, Toast.LENGTH_SHORT).show()
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter(Constant.DETAIL_LOADED)
        receiver = OnComponentDetailsLoadedReceiver()
        LocalBroadcastManager.getInstance(context!!).registerReceiver(receiver, intentFilter)
    }

    private fun unregisterReceiver() {
        context?.run {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        }
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

    inner class ComponentsRecyclerViewAdapter(private var components: MutableList<ComponentItemViewModel> = ArrayList()) : RecyclerView.Adapter<ComponentsRecyclerViewAdapter.ViewHolder>() {

        private lateinit var pm: PackageManager
        private var listCopy = ArrayList<ComponentItemViewModel>()
        private lateinit var componentData: AppComponentInfo
        private lateinit var listener: ComponentContract.ComponentItemListener

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.component_item, parent, false)
            pm = parent.context.packageManager
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindComponent(this.components[position])
            holder.itemView.isLongClickable = true
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

        fun updateData(components: MutableList<ComponentItemViewModel>) {
            this.components = components
            this.listCopy = ArrayList(components)
            notifyDataSetChanged()
        }

        fun addComponentDetails(componentData: AppComponentInfo) {
            this.componentData = componentData
            notifyDataSetChanged()
        }

        fun getData(): MutableList<ComponentItemViewModel> {
            return components
        }

        fun filter(keyword: String) {
            components = if (keyword.isEmpty()) {
                listCopy
            } else {
                listCopy.filter { it.name.contains(keyword, true) }.toMutableList()
            }
            notifyDataSetChanged()
        }

        fun setOnClickListener(listener: ComponentContract.ComponentItemListener) {
            this.listener = listener
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bindComponent(component: ComponentItemViewModel) {
                with(itemView) {
                    component_name.text = component.simpleName
                    component_package_name.text = component.name
                    component_switch.isChecked = component.state && component.ifwState
                    setOnClickListener {
                        listener.onSwitchClick(component.name, !it.component_switch.isChecked)
                        it.component_switch.isChecked = !it.component_switch.isChecked
                    }
                    component_switch.setOnClickListener {
                        listener.onSwitchClick(component.name, it.component_switch.isChecked)
                    }
                    component_like_button.setOnClickListener {
                        listener.onUpVoteClick(component.name)
                    }
                    component_like_count.setOnClickListener {
                        listener.onUpVoteClick(component.name)
                    }
                    component_dislike_button.setOnClickListener {
                        listener.onDownVoteClick(component.name)
                    }
                    component_dislike_count.setOnClickListener {
                        listener.onDownVoteClick(component.name)
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

        }
    }

    inner class OnComponentDetailsLoadedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val details = componentOnlineDetailsPresenter.getComponentData()
            componentAdapter.addComponentDetails(details)
        }
    }
}