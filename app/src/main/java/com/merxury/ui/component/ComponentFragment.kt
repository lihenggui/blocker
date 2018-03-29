package com.merxury.ui.component

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.merxury.blocker.R
import com.merxury.core.ApplicationComponents
import kotlinx.android.synthetic.main.component_item.view.*
import kotlinx.android.synthetic.main.fragment_component.*
import kotlinx.android.synthetic.main.fragment_component.view.*

class ComponentFragment : Fragment(), ComponentContract.View {


    override fun setSwitchEnableState(view: ComponentContract.View, enabled: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override lateinit var presenter: ComponentContract.Presenter
    private lateinit var componentAdapter: ComponentsRecyclerViewAdapter
    private lateinit var packageName: String
    private lateinit var type: EComponentType

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context != null) {
            presenter = ComponentPresenter(context.packageManager, this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        type = args?.getSerializable(Constant.CATEGORY) as EComponentType
        packageName = args.getString(Constant.PACKAGE_NAME)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_component, container, false)
        with(root) {
            componentListFragmentRecyclerView.apply {
                val layoutManager = LinearLayoutManager(context)
                this.layoutManager = layoutManager
                componentAdapter = ComponentsRecyclerViewAdapter()
                this.adapter = componentAdapter
                this.itemAnimator = DefaultItemAnimator()
                addItemDecoration(DividerItemDecoration(context, layoutManager.orientation))
            }
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
        }
        setHasOptionsMenu(true)
        return root
    }

    override fun onStart() {
        super.onStart()
        val packageManager = context?.packageManager
        if (packageManager != null) {
            presenter.loadComponents(packageName, type)
        }
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

    override fun searchForComponent() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun showFilteringPopUpMenu() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

    companion object {
        const val TAG = "ComponentFragment"
        fun newInstance(pm: PackageManager, packageName: String, type: EComponentType): Fragment {
            val fragment = ComponentFragment()
            val bundle = Bundle()
            bundle.putSerializable(Constant.CATEGORY, type)
            bundle.putString(Constant.PACKAGE_NAME, packageName)
            fragment.arguments = bundle
            return fragment
        }
    }

    interface ComponentItemListener {
        fun onComponentClick()
        fun onComponentLongClick()
        fun switchComponent()
    }

    inner class ComponentsRecyclerViewAdapter(private var components: List<ComponentInfo> = ArrayList()) : RecyclerView.Adapter<ComponentsRecyclerViewAdapter.ViewHolder>() {

        lateinit var pm: PackageManager

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.component_item, parent, false)
            pm = parent.context.packageManager
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindComponent(this.components[position])
        }


        override fun getItemCount(): Int {
            return this.components.size
        }

        fun addData(components: List<ComponentInfo>) {
            this.components = components
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            fun bindComponent(component: ComponentInfo) {
                val componentShortName = component.name.split(".").last()
                with(component) {
                    itemView.component_name.text = componentShortName
                    itemView.component_description.text = component.name
                    itemView.component_switch.isChecked = ApplicationComponents.checkComponentIsEnabled(pm, ComponentName(component.packageName, component.name))
                    itemView.component_switch.setOnCheckedChangeListener { buttonView, isChecked ->
                        if (isChecked) presenter.enableComponent(component) else presenter.disableComponent(component)
                    }
                }
            }
        }
    }
}