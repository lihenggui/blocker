package com.merxury.fragment;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
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

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.Nullable;
import io.reactivex.schedulers.Schedulers;


public class AppListFragment extends Fragment {
    private static final String IS_SYSTEM = "IS_SYSTEM";
    private ProgressBar mProgressBar;
    private AppListRecyclerViewAdapter mAppListRecyclerViewAdapter;
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
                    mProgressBar.setVisibility(View.GONE);
                }, throwable -> {
                    //TODO error handling
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_list, container, false);
        mProgressBar = view.findViewById(R.id.app_loading_progress_bar);
        RecyclerView AppListRecyclerView = view.findViewById(R.id.app_list_fragment_recyclerview);
        mAppListRecyclerViewAdapter = new AppListRecyclerViewAdapter(getContext());
        setupRecyclerView(AppListRecyclerView);
        return view;
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(mAppListRecyclerViewAdapter);
    }

}
