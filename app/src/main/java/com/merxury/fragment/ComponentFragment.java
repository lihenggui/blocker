package com.merxury.fragment;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.merxury.adapter.ComponentsRecyclerViewAdapter;
import com.merxury.blocker.R;
import com.merxury.core.ApplicationComponents;


public class ComponentFragment extends Fragment {
    public static final int RECEIVER = 1;
    public static final int SERVICE = 2;
    public static final int ACTIVITY = 3;
    public static final int PROVIDER = 4;

    public static final String CATEGORY = "category";
    public static final String RECEIVER_NAME = "receiver";
    public static final String SERVICE_NAME = "service";
    public static final String ACTIVITY_NAME = "activity";
    public static final String PROVIDER_NAME = "provider";

    private int mCategory;

    private Parcelable[] mParcelables;

    public ComponentFragment() {
    }

    public static Fragment getInstance(PackageManager pm, String packageName, int category) {
        ComponentFragment fragment = new ComponentFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(CATEGORY, category);
        switch (category) {
            case RECEIVER:
                ActivityInfo[] receivers = ApplicationComponents.getReceiverList(pm, packageName);
                bundle.putParcelableArray(RECEIVER_NAME, receivers);
                break;
            case SERVICE:
                ServiceInfo[] services = ApplicationComponents.getServiceList(pm, packageName);
                bundle.putParcelableArray(SERVICE_NAME, services);
                break;
            case ACTIVITY:
                ActivityInfo[] activities = ApplicationComponents.getActivitiyList(pm, packageName);
                bundle.putParcelableArray(ACTIVITY_NAME, activities);
                break;
            case PROVIDER:
                ProviderInfo[] providers = ApplicationComponents.getProviderList(pm, packageName);
                bundle.putParcelableArray(PROVIDER_NAME, providers);
                break;
            default:
                break;
        }
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mCategory = args.getInt(CATEGORY);
            switch (mCategory) {
                case RECEIVER:
                    mParcelables = args.getParcelableArray(RECEIVER_NAME);
                    break;
                case SERVICE:
                    mParcelables = args.getParcelableArray(SERVICE_NAME);
                    break;
                case ACTIVITY:
                    mParcelables = args.getParcelableArray(ACTIVITY_NAME);
                    break;
                case PROVIDER:
                    mParcelables = args.getParcelableArray(PROVIDER_NAME);
                    break;
                default:
                    break;
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_component, container, false);
        RecyclerView rv = view.findViewById(R.id.component_fragment_recyclerview);
        setupRecyclerView(rv);
        return view;
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(new ComponentsRecyclerViewAdapter(mCategory, mParcelables));
    }

}
