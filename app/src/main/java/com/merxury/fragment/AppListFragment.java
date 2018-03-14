package com.merxury.fragment;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.merxury.adapter.AppListRecyclerViewAdapter;
import com.merxury.blocker.R;
import com.merxury.core.ApplicationComponents;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.Nullable;
import io.reactivex.schedulers.Schedulers;


public class AppListFragment extends Fragment {
    private static final String IS_SYSTEM = "IS_SYSTEM";
    @BindView(R.id.app_loading_progress_bar)
    ProgressBar mProgressBar;
    @BindView(R.id.app_list_swipe_refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    AppListRecyclerViewAdapter mAppListRecyclerViewAdapter;
    private Unbinder mUnbinder;
    private boolean mSystem;

    public AppListFragment() {
    }

    public static Fragment getInstance(boolean isSystemApp) {
        AppListFragment fragment = new AppListFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean(IS_SYSTEM, isSystemApp);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mSystem = args.getBoolean(IS_SYSTEM);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        loadData();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_list, container, false);
        mUnbinder = ButterKnife.bind(this, view);
        RecyclerView AppListRecyclerView = view.findViewById(R.id.app_list_fragment_recyclerview);
        mAppListRecyclerViewAdapter = new AppListRecyclerViewAdapter(getContext());
        setupRecyclerView(AppListRecyclerView);
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
        recyclerView.setAdapter(mAppListRecyclerViewAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), layoutManager.getOrientation()));
    }

    private void initSwipeRefreshLayout() {
        mSwipeRefreshLayout.setOnRefreshListener(this::loadData);
    }

    private void loadData() {
        Single.create((SingleOnSubscribe<List<PackageInfo>>) emitter -> {
            PackageManager pm = getContext().getPackageManager();
            List<PackageInfo> appList;
            if (mSystem) {
                appList = ApplicationComponents.getSystemApplicationList(pm);
            } else {
                appList = ApplicationComponents.getThirdPartyApplicationList(pm);
            }
            emitter.onSuccess(appList);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(appList -> {
                    mAppListRecyclerViewAdapter.addData(appList);
                    mAppListRecyclerViewAdapter.notifyDataSetChanged();
                    if (mProgressBar.getVisibility() != View.GONE) {
                        mProgressBar.setVisibility(View.GONE);
                    }
                    if (mSwipeRefreshLayout.isRefreshing()) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, throwable -> {
                    //TODO error handling
                });
    }

}
