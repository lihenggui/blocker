package com.merxury.ui.component

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.merxury.blocker.R
import com.merxury.core.IController
import kotlinx.android.synthetic.main.component_item.view.*
import kotlinx.android.synthetic.main.fragment_component.*
import kotlinx.android.synthetic.main.fragment_component.view.*

class ComponentFragment : Fragment(), ComponentContract.View {

    override lateinit var presenter: ComponentContract.Presenter
    private lateinit var controller: IController
    private lateinit var componentAdapter: ComponentsRecyclerViewAdapter
    private lateinit var packageName: String
    private lateinit var type: EComponentType

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        val controllerView = context as ComponentContract.ControllerAttachedView
        controller = controllerView.getController()
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
                    presenter.loadComponents(context.packageManager, packageName, type)
                }
            }
            component_switch.setOnCheckedChangeListener({ view, isChecked ->
                view
                if (isChecked) {
                    //placeholder
                }
            })
        }
        setHasOptionsMenu(true)
        return root
    }

    override fun onStart() {
        super.onStart()
        val packageManager = context?.packageManager
        if (packageManager != null) {
            presenter.loadComponents(packageManager, packageName, type)
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
            fragment.presenter = ComponentPresenter(pm, fragment)
            return fragment
        }
    }

    interface ComponentItemListener {
        fun onComponentClick()
        fun onComponentLongClick()
        fun swichComponent()
    }
}