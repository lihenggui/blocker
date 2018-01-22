package com.merxury.fragment;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.merxury.adapter.AppListRecyclerViewAdapter;
import com.merxury.blocker.R;
import com.merxury.core.ApplicationComponents;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.annotations.Nullable;


public class AppListFragment extends Fragment {
    private static final String APP_LIST = "APP_LIST";
    private List<PackageInfo> mAppList;

    public AppListFragment() {
    }


    public static Fragment getInstance(Context context, boolean isSystemApp){
        AppListFragment fragment = new AppListFragment();
        Bundle bundle = new Bundle();
        List<PackageInfo> appList;
        if(isSystemApp) {
            appList = ApplicationComponents.getSystemApplicationList(context);
        } else {
            appList = ApplicationComponents.getThirdPartyApplicationList(context);
        }
        //TODO performance optimization
        bundle.putParcelableArrayList(APP_LIST, new ArrayList<>(appList));
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if(args != null) {
            mAppList = args.getParcelableArrayList(APP_LIST);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = (RecyclerView) inflater.inflate(R.layout.fragment_app_list, container, false);
        setupRecyclerView(rv);
        return rv;
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(new AppListRecyclerViewAdapter(getContext(), mAppList));
    }
}
