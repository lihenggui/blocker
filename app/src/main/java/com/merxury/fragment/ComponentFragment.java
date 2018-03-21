package com.merxury.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.merxury.blocker.R;
import com.merxury.core.ApplicationComponents;
import com.merxury.ui.component.ComponentsRecyclerViewAdapter;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class ComponentFragment extends Fragment {
    public static final int RECEIVER = 1;
    public static final int SERVICE = 2;
    public static final int ACTIVITY = 3;
    public static final int PROVIDER = 4;

    public static final String CATEGORY = "category";
    public static final String PACKAGE_NAME = "package_name";

    @BindView(R.id.componentListSwipeLayout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    ComponentsRecyclerViewAdapter mComponentsRecyclerViewAdapter;
    private int mCategory;
    private String mPackageName;
    private Unbinder mUnbinder;

    public ComponentFragment() {
    }

    public static Fragment getInstance(PackageManager pm, String packageName, int category) {
        ComponentFragment fragment = new ComponentFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(CATEGORY, category);
        bundle.putString(PACKAGE_NAME, packageName);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mCategory = args.getInt(CATEGORY);
            mPackageName = args.getString(PACKAGE_NAME);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        loadData();
    }

    @SuppressLint("CheckResult")
    private void loadData() {
        Single.create((SingleOnSubscribe<List<ComponentInfo>>) emitter -> {
            PackageManager pm = getContext().getPackageManager();
            List<ComponentInfo> componentInfo;
            switch (mCategory) {
                case RECEIVER:
                    componentInfo = ApplicationComponents.getReceiverList(pm, mPackageName);
                    break;
                case SERVICE:
                    componentInfo = ApplicationComponents.getServiceList(pm, mPackageName);
                    break;
                case ACTIVITY:
                    componentInfo = ApplicationComponents.getActivityList(pm, mPackageName);
                    break;
                case PROVIDER:
                    componentInfo = ApplicationComponents.getProviderList(pm, mPackageName);
                    break;
                default:
                    //FLAG: It should not happen
                    componentInfo = new ArrayList<>();
                    break;
            }
            emitter.onSuccess(componentInfo);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(appList -> {
                    hideProgressBar();
                    if (mSwipeRefreshLayout.isRefreshing()) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                    mComponentsRecyclerViewAdapter.addData(appList);
                    mComponentsRecyclerViewAdapter.notifyDataSetChanged();
                }, throwable -> {
                    hideProgressBar();
                    //TODO error handling
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_component, container, false);
        mUnbinder = ButterKnife.bind(this, view);
        RecyclerView rv = view.findViewById(R.id.componentListFragmentRecyclerView);
        setupRecyclerView(rv);
        initSwipeRefreshLayout();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUnbinder.unbind();
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        Context context = recyclerView.getContext();
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);
        mComponentsRecyclerViewAdapter = new ComponentsRecyclerViewAdapter();
        recyclerView.setAdapter(mComponentsRecyclerViewAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), layoutManager.getOrientation()));
    }

    private void initSwipeRefreshLayout() {
        mSwipeRefreshLayout.setOnRefreshListener(this::loadData);
    }

    private void hideProgressBar() {

    }
}
