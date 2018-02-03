package com.merxury.adapter;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.merxury.blocker.R;
import com.merxury.core.ApplicationComponents;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.merxury.fragment.ComponentFragment.ACTIVITY;
import static com.merxury.fragment.ComponentFragment.ACTIVITY_NAME;
import static com.merxury.fragment.ComponentFragment.PROVIDER;
import static com.merxury.fragment.ComponentFragment.RECEIVER;
import static com.merxury.fragment.ComponentFragment.RECEIVER_NAME;
import static com.merxury.fragment.ComponentFragment.SERVICE;
import static com.merxury.fragment.ComponentFragment.SERVICE_NAME;

/**
 * Created by Wiki on 2018/1/25.
 */

public class ComponentsRecyclerViewAdapter extends RecyclerView.Adapter<ComponentsRecyclerViewAdapter.ViewHolder> {

    private ActivityInfo[] mActivities;
    private ActivityInfo[] mReceivers;
    private ServiceInfo[] mServices;
    private ProviderInfo[] mProviders;
    private ComponentInfo[] mComponentInfos;
    private int mCategory;
    private PackageManager mPm;

    public ComponentsRecyclerViewAdapter(int category, Parcelable[] parcelables) {
        mCategory = category;
        if (parcelables == null) {
            return;
        }

        mComponentInfos = new ComponentInfo[parcelables.length];
        for (int i = 0; i < parcelables.length; i++) {
            mComponentInfos[i] = (ComponentInfo) parcelables[i];
        }

        switch (mCategory) {
            case RECEIVER:
                mReceivers = new ActivityInfo[parcelables.length];
                for (int i = 0; i < parcelables.length; i++) {
                    mReceivers[i] = (ActivityInfo) parcelables[i];
                }

                break;
            case SERVICE:
                mServices = new ServiceInfo[parcelables.length];
                for (int i = 0; i < parcelables.length; i++) {
                    mServices[i] = (ServiceInfo) parcelables[i];
                }

                break;
            case ACTIVITY:
                mActivities = new ActivityInfo[parcelables.length];
                for (int i = 0; i < parcelables.length; i++) {
                    mActivities[i] = (ActivityInfo) parcelables[i];
                }
                break;
            case PROVIDER:
                mProviders = new ProviderInfo[parcelables.length];
                for (int i = 0; i < parcelables.length; i++) {
                    mProviders[i] = (ProviderInfo) parcelables[i];
                }
                break;
            default:
                break;
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.component_item, parent, false);
        mPm = context.getPackageManager();
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position > mComponentInfos.length) {
            return;
        }
        final ComponentInfo info = mComponentInfos[position];
        String componentName = info.name;
        holder.mComponentName.setText(componentName);
        holder.mSwitch.setChecked(ApplicationComponents.checkComponentIsEnabled(mPm, new ComponentName(info.packageName, info.name)));
    }

    @Override
    public int getItemCount() {
        return mComponentInfos == null ? 0 : mComponentInfos.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View mView;
        @BindView(R.id.component_name)
        TextView mComponentName;
        @BindView(R.id.component_description)
        TextView mComponentDescription;
        @BindView(R.id.component_switch)
        Switch mSwitch;

        ViewHolder(View view) {
            super(view);
            mView = view;
            ButterKnife.bind(this, view);
        }
    }
}
